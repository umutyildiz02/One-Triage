# Launch the existing credential-safe script through the local Windows process provider.
# No secret is supplied to WMI, command-line arguments, a scheduled task, or a log file.
$ErrorActionPreference = 'Stop'
$root = Split-Path $PSScriptRoot -Parent
$launcher = Join-Path $PSScriptRoot 'Start-LiveBackend.ps1'
$powerShell = Join-Path $env:SystemRoot 'System32\WindowsPowerShell\v1.0\powershell.exe'
$phase = 'port-check'
try {
  if (Get-NetTCPConnection -LocalPort 8787 -State Listen -ErrorAction SilentlyContinue) {
    throw 'Port already occupied; use Stop-LiveBackend.ps1 for the tracked server first.'
  }
  $phase = 'process-startup-options'
  $startup = New-CimInstance -ClassName Win32_ProcessStartup -ClientOnly -Property @{
    ShowWindow = [uint16]0
  }
  $commandLine = '"' + $powerShell + '" -NoLogo -NoProfile -NonInteractive -File "' + $launcher + '" -UseReferenceKey'
  $phase = 'windows-create-process'
  $created = Invoke-CimMethod -ClassName Win32_Process -MethodName Create -Arguments @{
    CommandLine = $commandLine
    CurrentDirectory = $root
    ProcessStartupInformation = $startup
  }
  Write-Output ('Windows process creation status=' + $created.ReturnValue)
  if ($created.ReturnValue -ne 0) { throw 'Windows process provider refused startup.' }
  $phase = 'local-health'
  $ready = $false
  for ($attempt = 0; $attempt -lt 20; $attempt++) {
    try {
      $health = Invoke-RestMethod 'http://127.0.0.1:8787/health' -TimeoutSec 1
      if ($health.status -eq 'ok' -and $health.mode -eq 'LIVE') { $ready = $true; break }
    } catch { }
    Start-Sleep -Milliseconds 500
  }
  if (!$ready) { throw 'Detached launcher did not become healthy.' }
  Write-Output ('Independent LIVE launcher PID=' + $created.ProcessId + '; local health ok/LIVE. No API request sent.')
  Write-Output 'Stop with scripts/Stop-LiveBackend.ps1. Budget and credentials remain unchanged.'
    exit 0
} catch {
  Write-Output (('Independent startup failed at ' + $phase + ': type=') + $_.Exception.GetType().Name + '; hresult=' + $_.Exception.HResult)
  Write-Output 'No credential or exception contents printed. Check the local port and Windows WMI permission.'
  exit 1
}
