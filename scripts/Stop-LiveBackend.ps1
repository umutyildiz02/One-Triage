$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot
$pidPath = Join-Path $root '.runtime/backend.pid'
if (-not [IO.File]::Exists($pidPath)) { throw 'No tracked backend process.' }
$backendPid = 0
if (-not [int]::TryParse([IO.File]::ReadAllText($pidPath).Trim(), [ref]$backendPid)) { throw 'Invalid tracked process ID.' }
$process = Get-CimInstance Win32_Process -Filter "ProcessId=$backendPid"
if ($null -eq $process) { Write-Output 'Tracked backend is already stopped.'; exit 0 }
if ($process.Name -ne 'java.exe' -or $process.CommandLine -notlike '*com.vitaltriage.backend.ServerKt*' -or $process.CommandLine -notlike "*$root*") {
    throw 'Process identity does not match VitalTriage; nothing stopped.'
}
Stop-Process -Id $backendPid
Write-Output 'Tracked VitalTriage backend stopped. Budget and credentials retained.'
exit 0
