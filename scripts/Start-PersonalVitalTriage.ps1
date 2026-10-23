param([string]$Serial='', [string]$Sdk="$env:LOCALAPPDATA/Android/Sdk")
$ErrorActionPreference='Stop'
$root=Split-Path $PSScriptRoot -Parent
$adb=Join-Path $Sdk 'platform-tools/adb.exe'
$emulator=Join-Path $Sdk 'emulator/emulator.exe'
$apk=Join-Path $root 'app/build/outputs/apk/device/app-device.apk'
function Invoke-Adb([string[]]$Arguments) {
    $old=$ErrorActionPreference
    $ErrorActionPreference='Continue'
    try { $out=(& $adb @Arguments 2>&1) -join "`n"; $code=$LASTEXITCODE } finally { $ErrorActionPreference=$old }
    if($code -ne 0) { throw 'ADB operation failed; no data was deleted. Inspect device availability or signing compatibility.' }
    return $out
}
function Find-Medium {
    $devices=Invoke-Adb @('devices')
    foreach($line in ($devices -split "`n")) {
        if($line -match '^(emulator-\d+)\s+device\s*$') {
            $candidate=$Matches[1]
            $name=Invoke-Adb @('-s',$candidate,'emu','avd','name')
            if(($name -split "`n")[0].Trim() -eq 'medium_phone') { return $candidate }
        }
    }
    return ''
}
try {
    if(!(Test-Path $adb) -or !(Test-Path $apk)) { throw 'Build app/device in Android Studio first; Android SDK and app-device.apk are required.' }
    if(!$Serial) { $Serial=Find-Medium }
    if(!$Serial) {
        if(!(Test-Path $emulator)) { throw 'Android emulator is not installed.' }
        if((Get-CimInstance Win32_OperatingSystem).FreePhysicalMemory -lt 6291456) { throw 'Free at least 6 GB of host RAM before starting the 4 GB emulator.' }
        if((& $emulator -list-avds) -notcontains 'medium_phone') { throw 'Existing medium_phone AVD is required. Nothing is downloaded or recreated.' }
        $null=Start-Process $emulator -ArgumentList @('-avd','medium_phone','-memory','4096','-cores','4','-no-snapshot-load')
        $deadline=[DateTime]::UtcNow.AddMinutes(3)
        do { Start-Sleep -Seconds 2; $Serial=Find-Medium } while(!$Serial -and [DateTime]::UtcNow -lt $deadline)
        if(!$Serial) { throw 'Emulator did not become available.' }
    }
    $deadline=[DateTime]::UtcNow.AddMinutes(3)
    do {
        $boot=(Invoke-Adb @('-s',$Serial,'shell','getprop','sys.boot_completed')).Trim()
        if($boot -eq '1') { break }
        Start-Sleep -Seconds 2
    } while([DateTime]::UtcNow -lt $deadline)
    if($boot -ne '1') { throw 'Android boot is incomplete.' }
    Write-Output 'Installing personal device APK with -r; existing application data is retained.'
    $result=Invoke-Adb @('-s',$Serial,'install','-r',$apk)
    if($result -notmatch 'Success') { throw 'Install did not succeed. No uninstall/data reset is performed.' }
    $null=Invoke-Adb @('-s',$Serial,'shell','am','start','-n','com.vitaltriage.app/.LegacyLoadingActivity')
    Write-Output 'Personal VitalTriage opened. No Windows backend or reverse tunnel was started.'
    Write-Output 'Review the original text and explicitly approve each OpenAI request. Internet is required for assessment.'
    Write-Output 'This owner-authorized APK contains an extractable personal API key; it is not a credential-free sharing build.'
    exit 0
} catch {
    Write-Output $_.Exception.Message
    exit 1
}
