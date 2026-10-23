param(
    [switch]$InstallApk,
    [switch]$NoTokenPrompt,
    [switch]$CheckOnly,
    [string]$Sdk = "$env:LOCALAPPDATA\Android\Sdk"
)
$ErrorActionPreference = 'Stop'
$root = Split-Path $PSScriptRoot -Parent
$adb = Join-Path $Sdk 'platform-tools\adb.exe'
$emulator = Join-Path $Sdk 'emulator\emulator.exe'
$serial = $null
$phase = 'prerequisites'
function Invoke-Adb([string[]]$Arguments) {
    $result = & $adb @Arguments 2>&1
    if ($LASTEXITCODE -ne 0) { throw 'ADB operation failed; no application data was removed.' }
    return ($result -join "`n")
}
function Find-MediumPhone {
    $devices = Invoke-Adb @('devices')
    foreach ($line in ($devices -split "`n")) {
        if ($line -match '^(emulator-\d+)\s+device\s*$') {
            $candidate = $Matches[1]
            $name = Invoke-Adb @('-s',$candidate,'emu','avd','name')
            if (($name -split "`n")[0].Trim() -eq 'medium_phone') { return $candidate }
        }
    }
    return $null
}
try {
    foreach ($required in @($adb,$emulator,(Join-Path $root 'backend\build\install\backend\lib'),
        (Join-Path $root '.runtime\live-budget.json'))) {
        if (!(Test-Path -LiteralPath $required)) { throw 'Required SDK, backend distribution or preserved budget ledger is missing. See docs/EMULATOR_QUICKSTART.md.' }
    }
    # Never reset or create a replacement ledger on launch.
    $budget = [IO.File]::ReadAllText((Join-Path $root '.runtime\live-budget.json')) | ConvertFrom-Json
    if ($budget.version -ne 1 -or $null -eq $budget.attempts) { throw 'Budget ledger cannot be verified.' }
    $spent = ($budget.attempts | Measure-Object chargedUpperUsd -Sum).Sum
    Write-Output ("Budget: {0}/100 attempts; conservative USD {1:F7}. No evaluation is sent by this launcher." -f @($budget.attempts).Count,$spent)
    if (@($budget.attempts).Count -ge 23 -or ($spent + 0.01568) -ge 3) {
        Write-Output 'LIVE test allowance exhausted. Local speech and typed input remain available; backend will reject paid evaluation.'
    }
    if ($CheckOnly) { Write-Output 'Prerequisites and persistent ledger verified. No process or application was started.'; exit 0 }
    $phase = 'backend'
    $listeners = @(Get-NetTCPConnection -LocalPort 8787 -State Listen -ErrorAction SilentlyContinue)
    if ($listeners.Count -gt 0) {
        # Health alone is insufficient: verify tracked local process before reusing the port.
        $pidFile = Join-Path $root '.runtime\backend.pid'
        if (!(Test-Path $pidFile)) { throw 'Port 8787 belongs to an untracked process. Nothing was stopped.' }
        $tracked = 0
        if (![int]::TryParse([IO.File]::ReadAllText($pidFile).Trim(),[ref]$tracked)) { throw 'Invalid tracked backend PID.' }
        $process = Get-CimInstance Win32_Process -Filter "ProcessId=$tracked"
        if (!$process -or $process.Name -ne 'java.exe' -or
            $process.CommandLine -notlike '*com.vitaltriage.backend.ServerKt*' -or
            !$process.CommandLine.Contains((Join-Path $root 'backend')) -or
            @($listeners | Where-Object { $_.OwningProcess -ne $tracked -or $_.LocalAddress -ne '127.0.0.1' }).Count -gt 0) {
            throw 'Port identity/loopback check failed. No unrelated process was stopped.'
        }
    } else {
        & (Join-Path $PSScriptRoot 'Start-DetachedLiveBackend.ps1')
        if ($LASTEXITCODE -ne 0) { throw 'LIVE backend startup failed. Existing credentials and budget were preserved.' }
    }
    $health = Invoke-RestMethod 'http://127.0.0.1:8787/health' -TimeoutSec 5
    if ($health.status -ne 'ok' -or $health.mode -ne 'LIVE') { throw 'Backend is not healthy LIVE. No Demo fallback is used.' }
    Write-Output 'Tracked loopback backend: ok / LIVE (health only; not a new API proof).'
    $phase = 'emulator'
    $serial = Find-MediumPhone
    if (!$serial) {
        $avds = & $emulator -list-avds
        if ($avds -notcontains 'medium_phone') { throw 'medium_phone is not installed. No SDK or AVD download was started.' }
        # The measured Medium -> NLLB flow needs 4 GB guest RAM; keep host headroom.
        $hostMemory = Get-CimInstance Win32_OperatingSystem
        if ($hostMemory.FreePhysicalMemory -lt 6291456) {
            throw 'At least 6 GB free host RAM is required before starting the 4 GB emulator.'
        }
        # Detached from this console. Do not disable emulator authentication or wipe its data.
        $null = Start-Process -FilePath $emulator -ArgumentList @('-avd','medium_phone','-memory','4096','-cores','4','-no-snapshot-load','-no-snapshot-save','-gpu','swiftshader_indirect') -PassThru
        $deadline = [DateTime]::UtcNow.AddMinutes(3)
        do {
            Start-Sleep -Seconds 2
            $serial = Find-MediumPhone
        } until ($serial -or [DateTime]::UtcNow -ge $deadline)
        if (!$serial) { throw 'medium_phone did not appear within three minutes. Inspect Device Manager; no data was reset.' }
    }
    $deadline = [DateTime]::UtcNow.AddMinutes(3)
    do {
        $booted = (Invoke-Adb @('-s',$serial,'shell','getprop','sys.boot_completed')).Trim()
        if ($booted -eq '1') { break }
        Start-Sleep -Seconds 2
    } while ([DateTime]::UtcNow -lt $deadline)
    if ($booted -ne '1') { throw 'Emulator boot is incomplete.' }
    $guestMemory = Invoke-Adb @('-s',$serial,'shell','dumpsys','meminfo','--oom')
    if ($guestMemory -match 'Total RAM:\s*([0-9,]+)K') {
        $guestKb = [long]($Matches[1].Replace(',',''))
        Write-Output ('Emulator RAM: approximately {0:N0} MB.' -f ($guestKb / 1024))
        if ($guestKb -lt 3500000) {
            Write-Output 'Existing emulator has limited RAM for the measured Medium plus NLLB flow. Close it normally and rerun this launcher for 3072 MB; do not wipe data. Typed input remains available.'
        }
    }
    $phase = 'normal application'
    # pm path returns 1 with empty output when the package is absent/unavailable.
  # Preserve any retained data by repairing only with the same signed APK and -r.
  $installed = (& $adb -s $serial shell pm path com.vitaltriage.app 2>&1) -join "`n"
  if ($LASTEXITCODE -ne 0 -and ![string]::IsNullOrWhiteSpace($installed)) {
    throw 'Package availability check failed; no application data was removed.'
  }
    if ($InstallApk -or !$installed.Contains('package:')) {
        $apk = Join-Path $root 'app\build\outputs\apk\debug\app-debug.apk'
        if (!(Test-Path $apk)) { throw 'Debug APK missing. Build :app:assembleDebug first.' }
        Write-Output 'Installing normal debug APK with -r. No uninstall or data clear is permitted.'
        $null = Invoke-Adb @('-s',$serial,'install','-r',$apk)
    }
    $null = Invoke-Adb @('-s',$serial,'reverse','tcp:8787','tcp:8787')
    $null = Invoke-Adb @('-s',$serial,'shell','am','start','-a','android.intent.action.MAIN','-c','android.intent.category.LAUNCHER','-f','0x10200000','-n','com.vitaltriage.app/.LegacyLoadingActivity')
    $phase = 'application focus'
  $focusDeadline = [DateTime]::UtcNow.AddSeconds(15)
  $normalFocused = $false
  do {
    $windows = Invoke-Adb @('-s',$serial,'shell','dumpsys','window')
    if ($windows -match 'mCurrentFocus=.*Application Not Responding') {
      Write-Output 'Android is showing a not-responding dialog. Resolve it on the emulator; no data reset or automatic dismissal was performed.'
      throw 'Android window focus is blocked.'
    }
    $normalFocused = $windows -match 'mCurrentFocus=.*\bcom\.vitaltriage\.app/'
    if ($normalFocused) { break }
    Start-Sleep -Seconds 1
  } while ([DateTime]::UtcNow -lt $focusDeadline)
  if (!$normalFocused) {
    Write-Output 'Normal application foreground was not confirmed. Check the emulator screen or unlock it manually.'
    throw 'Normal application is not focused.'
  }
  Write-Output ("Opened and verified normal com.vitaltriage.app foreground on " + $serial + '. Preferences preserved.')
    Write-Output 'Use Settings > Developer/Test > Demo/backend: Offline Demo OFF; http://127.0.0.1:8787.'
    Write-Output 'The backend token is separate from the OpenAI key. Unless explicitly saved with encrypted Remember access, it remains in Android process memory.'
    if (!$NoTokenPrompt) {
        Write-Output 'If the token is missing, focus the EMPTY masked backend-token field in the normal app.'
        $answer = Read-Host 'Press Enter to enter the local backend token securely, or type s to skip'
        if ([string]::IsNullOrEmpty($answer)) {
            & (Join-Path $PSScriptRoot 'Enter-BackendToken.ps1') -Adb $adb -Serial $serial
            if ($LASTEXITCODE -ne 0) { throw 'Token entry refused. Focus the empty masked field and run Enter-BackendToken.ps1 again.' }
        }
    }
    Write-Output 'Ready for local use. Review text and explicitly approve each evaluation. No API request was made by startup.'
    Write-Output 'Backend is detached but is not a Windows service or reboot auto-start. Re-run this launcher after reboot.'
} catch {
    # Do not echo arbitrary process/HTTP exceptions or command lines containing secrets.
    Write-Output ("Startup stopped at: " + $phase)
    Write-Output 'Check docs/EMULATOR_QUICKSTART.md. No data reset, automatic API retry, or secret output was performed.'
    exit 1
}
