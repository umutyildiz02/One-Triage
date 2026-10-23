$ErrorActionPreference = 'Stop'
$ProgressPreference = 'SilentlyContinue'
$root = Split-Path $PSScriptRoot -Parent
$folder = Join-Path $root '.runtime/tools'
[IO.Directory]::CreateDirectory($folder) | Out-Null
$listing = (Invoke-WebRequest -UseBasicParsing -Uri 'https://nodejs.org/dist/latest-v22.x/SHASUMS256.txt').Content
$entry = [regex]::Match($listing,'(?m)^([a-f0-9]{64})\s+(node-v22\.[0-9]+\.[0-9]+-win-x64\.zip)\s*$')
if (-not $entry.Success) { throw 'Official Node archive metadata not found' }
$name = $entry.Groups[2].Value
$archive = Join-Path $folder $name
$destination = Join-Path $folder ([IO.Path]::GetFileNameWithoutExtension($name))
if (-not (Test-Path -LiteralPath $archive)) {
    Invoke-WebRequest -UseBasicParsing -Uri ('https://nodejs.org/dist/latest-v22.x/' + $name) -OutFile $archive
}
if ((Get-FileHash -Algorithm SHA256 -LiteralPath $archive).Hash.ToLowerInvariant() -ne $entry.Groups[1].Value) { throw 'Official archive checksum mismatch' }
if (-not (Test-Path -LiteralPath $destination)) { Expand-Archive -LiteralPath $archive -DestinationPath $folder }
Write-Output ('Verified official portable Node: ' + (Join-Path $destination 'node.exe'))
Write-Output ('Archive bytes=' + (Get-Item -LiteralPath $archive).Length + '; no installer, PATH change or admin operation.')
