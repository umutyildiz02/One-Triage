param(
    [switch]$UseReferenceKey,
    [string]$ReferenceFile = '',
    [string]$JavaHome = $env:JAVA_HOME
)
$ErrorActionPreference = 'Stop'
$root = Split-Path $PSScriptRoot -Parent
$runtime = Join-Path $root '.runtime'
$child = $null
try {
    if (Get-NetTCPConnection -LocalPort 8787 -State Listen -ErrorAction SilentlyContinue) {
        Write-Output 'Port 8787 is occupied. Identify and stop only the VitalTriage backend before starting.'; exit 1
    }
    if (!(Test-Path (Join-Path $root 'backend\build\install\backend\lib'))) {
        Write-Output 'Build :backend:installDist with JDK 17 first.'; exit 1
    }
    if ($UseReferenceKey) {
        $content = [IO.File]::ReadAllText($ReferenceFile)
        $candidates = @([regex]::Matches($content, 'sk-(?:proj-|svcacct-)?[A-Za-z0-9_-]{20,}') |
            ForEach-Object { $_.Value } | Select-Object -Unique)
        $content = $null
        if ($candidates.Count -ne 1) { Write-Output 'Expected exactly one credential in the selected file. No request sent.'; exit 1 }
        $apiKey = $candidates[0]
    } else {
        $secure = Read-Host 'OpenAI API key (masked, not saved)' -AsSecureString
        $ptr = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($secure)
        try { $apiKey = [Runtime.InteropServices.Marshal]::PtrToStringBSTR($ptr) }
        finally { [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($ptr) }
    }
    if ([string]::IsNullOrWhiteSpace($apiKey)) { Write-Output 'No key configured.'; exit 1 }
    [IO.Directory]::CreateDirectory($runtime) | Out-Null
    Add-Type -AssemblyName System.Security
    $tokenFile = Join-Path $runtime 'backend-token.dpapi'
    if (Test-Path $tokenFile) {
        $bytes = [Security.Cryptography.ProtectedData]::Unprotect([IO.File]::ReadAllBytes($tokenFile), $null, [Security.Cryptography.DataProtectionScope]::CurrentUser)
        $token = [Text.Encoding]::UTF8.GetString($bytes)
    } else {
        $bytes = New-Object byte[] 32
        $rng = [Security.Cryptography.RandomNumberGenerator]::Create()
        $rng.GetBytes($bytes); $rng.Dispose()
        $token = ([BitConverter]::ToString($bytes)).Replace('-', '').ToLowerInvariant()
        $protected = [Security.Cryptography.ProtectedData]::Protect([Text.Encoding]::UTF8.GetBytes($token), $null, [Security.Cryptography.DataProtectionScope]::CurrentUser)
        [IO.File]::WriteAllBytes($tokenFile, $protected)
    }
    $info = New-Object Diagnostics.ProcessStartInfo
    $info.FileName = Join-Path $JavaHome 'bin\java.exe'
    $info.Arguments = '-Xmx128m -Djdk.httpclient.disableRetryConnect=true -cp "' + (Join-Path $root 'backend\build\install\backend\lib\*') + '" com.vitaltriage.backend.ServerKt'
    $info.WorkingDirectory = $root
    $info.UseShellExecute = $false
    $info.EnvironmentVariables['VT_MODE'] = 'LIVE'
    $info.EnvironmentVariables['OPENAI_API_KEY'] = $apiKey
    $info.EnvironmentVariables['OPENAI_MODEL'] = 'gpt-4.1-mini'
    $info.EnvironmentVariables['VT_ACCESS_TOKEN'] = $token
    $info.EnvironmentVariables['VT_BUDGET_FILE'] = Join-Path $runtime 'live-budget.json'
    $info.EnvironmentVariables['PORT'] = '8787'
    # Prevent inherited JVM option hooks from printing or forwarding environment values.
    [void]$info.EnvironmentVariables.Remove('JAVA_TOOL_OPTIONS')
    [void]$info.EnvironmentVariables.Remove('_JAVA_OPTIONS')
    [void]$info.EnvironmentVariables.Remove('JDK_JAVA_OPTIONS')
    [void]$info.EnvironmentVariables.Remove('TRIAGE_PROMPT_FILE')
    $child = [Diagnostics.Process]::Start($info)
    [IO.File]::WriteAllText((Join-Path $runtime 'backend.pid'), [string]$child.Id)
    $info.EnvironmentVariables['OPENAI_API_KEY'] = ''
    $info.EnvironmentVariables['VT_ACCESS_TOKEN'] = ''
    $apiKey = $null; $token = $null; $candidates = $null
    Write-Output ('LIVE backend PID=' + $child.Id + '; model=gpt-4.1-mini; keys configured; max calls=100; conservative USD stop=3.00 (including reservations); authorized total USD=5.00; durable cutoff=$3.00.')
    Write-Output 'No API call is sent on startup. Budget and DPAPI backend token survive restarts. Never delete the budget to retry.'
    $child.WaitForExit()
} catch {
    Write-Output 'Startup failed. Secret values and exception details suppressed.'
    exit 1
} finally { $apiKey = $null; $token = $null; $candidates = $null }
