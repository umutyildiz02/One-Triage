param([string]$Adb = 'adb',
    [ValidatePattern('^emulator-[0-9]+$')][string]$Serial = 'emulator-5554')
$ErrorActionPreference = 'Stop'
# Focus the EMPTY, masked backend-token field in the normal app first.
# Only the separate backend token is typed. The OpenAI key is never read here.
try {
    # Fail closed before reading the credential unless the normal app's empty
    # password field is actually focused. UI XML is inspected in memory only.
    $probeInfo = New-Object Diagnostics.ProcessStartInfo
    $probeInfo.FileName = $Adb
    $probeInfo.Arguments = '-s ' + $Serial + ' exec-out uiautomator dump /dev/tty'
    $probeInfo.UseShellExecute = $false
    $probeInfo.CreateNoWindow = $true
    $probeInfo.RedirectStandardOutput = $true
    $probeInfo.RedirectStandardError = $true
    $probe = [Diagnostics.Process]::Start($probeInfo)
    $ui = $probe.StandardOutput.ReadToEnd()
    $null = $probe.StandardError.ReadToEnd()
    $probe.WaitForExit()
    $start = $ui.IndexOf('<?xml')
    $end = $ui.IndexOf('</hierarchy>')
    if ($probe.ExitCode -ne 0 -or $start -lt 0 -or $end -lt $start) { throw 'Cannot verify credential field' }
    [xml]$tree = $ui.Substring($start,$end-$start+12)
    $field = $tree.SelectSingleNode('//node[@package="com.vitaltriage.app" and @password="true" and @focused="true" and @text=""]')
    $ui = $null; $tree = $null
    if ($null -eq $field) { throw 'Focus the empty masked field in the normal app first' }
    $field = $null
    Add-Type -AssemblyName System.Security
    $root = Split-Path $PSScriptRoot -Parent
    $bytes = [Security.Cryptography.ProtectedData]::Unprotect([IO.File]::ReadAllBytes((Join-Path $root '.runtime\backend-token.dpapi')), $null, [Security.Cryptography.DataProtectionScope]::CurrentUser)
    $token = [Text.Encoding]::UTF8.GetString($bytes)
    if ($token -notmatch '^[a-f0-9]{64}$') { throw 'Invalid local token format' }
    $info = New-Object Diagnostics.ProcessStartInfo
    $info.FileName = $Adb
    $info.Arguments = '-s ' + $Serial + ' shell input text ' + $token
    $info.UseShellExecute = $false
    $info.CreateNoWindow = $true
    $info.RedirectStandardOutput = $true
    $info.RedirectStandardError = $true
    $process = [Diagnostics.Process]::Start($info)
    $null = $process.StandardOutput.ReadToEnd()
    $null = $process.StandardError.ReadToEnd()
    $process.WaitForExit()
    $info.Arguments = ''; $token = $null
    if ($process.ExitCode -ne 0) { throw 'Input failed' }
    Write-Output 'Backend credential entered into focused field; value not printed.'
exit 0
} catch { Write-Output 'Backend credential entry failed; details suppressed.'; exit 1 }
finally { $token = $null }
