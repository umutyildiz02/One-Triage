param([Parameter(Mandatory=$true)][string]$Source)
$ErrorActionPreference = 'Stop'
$expected = 'ae85e4a935d7a567bd102fe55afc16bb595bdb618e11b2fc7591bc08120411bb'
$file = Get-Item -LiteralPath $Source
if ($file.Length -ne 190085487) { throw 'Unexpected model length' }
$hash = (Get-FileHash -LiteralPath $Source -Algorithm SHA256).Hash.ToLowerInvariant()
if ($hash -ne $expected) { throw 'Model differs from published ggerganov/whisper.cpp small-q5_1 SHA256' }
$reader = [IO.BinaryReader]::new([IO.File]::OpenRead($Source))
try {
    $magic = $reader.ReadUInt32()
    $vocabulary = $reader.ReadInt32()
    if ($magic -ne 0x67676d6c -or $vocabulary -ne 51865) { throw 'Unexpected GGML multilingual model header' }
} finally { $reader.Dispose() }
$root = Split-Path $PSScriptRoot -Parent
$folder = Join-Path $root 'app/src/main/assets/models'
[IO.Directory]::CreateDirectory($folder) | Out-Null
$destination = Join-Path $folder 'ggml-small-q5_1.bin'
if (Test-Path -LiteralPath $destination) {
    if ((Get-FileHash -LiteralPath $destination -Algorithm SHA256).Hash.ToLowerInvariant() -ne $expected) {
        throw 'Existing destination differs; refusing overwrite'
    }
} else { [IO.File]::Copy($Source, $destination, $false) }
Write-Output ('Model verified: GGML, multilingual vocabulary=' + $vocabulary + '; bytes=' + $file.Length + '; SHA256=' + $hash)
Write-Output 'Matched publisher file metadata; no independent audit of training data or clinical accuracy. No download performed.'
