param(
    [string]$Serial = 'emulator-5554',
    [string]$Adb = "$env:LOCALAPPDATA/Android/Sdk/platform-tools/adb.exe",
    [switch]$RemoveVerifiedIsolatedCopies
)
$ErrorActionPreference = 'Stop'
# Local development only. Production/phone installation uses the in-app HTTPS model preparer.
# Never removes normal application data. Only exact, backed-up isolated test fixtures are optional cleanup.
$root = Split-Path $PSScriptRoot -Parent
$folder = Join-Path $root '.runtime/nllb'
$items = @(
    @{name='NLLB_cache_initializer.onnx';bytes=25368443;sha='4150b3cba7db9487a94e3b693f79d0f89772e75d80bffd0e60b06de1b574e3d9'},
    @{name='NLLB_decoder.onnx';bytes=179109694;sha='86028825495fc34085c7ebb80fb5f0eaa18ac6600f9d4dbc55bc731cc6981159'},
    @{name='NLLB_embed_and_lm_head.onnx';bytes=524712277;sha='2745a6f932237b8bb0c35ded54987ea3fc455b696215d48d32271c28c785dd50'},
    @{name='NLLB_encoder.onnx';bytes=266487014;sha='9b25d62f4875e5087ffde53c2794308c07f60f4c77670fc5bbfe308a1ef88cd9'},
    @{name='sentencepiece_bpe.model';bytes=4852054;sha='14bb8dfb35c0ffdea7bc01e56cea38b9e3d5efcdcb9c251d6b40538e1aab555a'}
)
function Invoke-AdbChecked([string[]]$Arguments) {
    # adb push writes successful progress to stderr. Check its exit status, not the stream.
    $previousPreference = $ErrorActionPreference
    try {
        $ErrorActionPreference = 'Continue'
        $result = & $Adb -s $Serial @Arguments 2>&1
        $exitCode = $LASTEXITCODE
    } finally { $ErrorActionPreference = $previousPreference }
    if ($exitCode -ne 0) { throw ('ADB operation failed: ' + ($result -join ' ')) }
    return $result
}
foreach ($item in $items) {
    $path = Join-Path $folder $item.name
    if (!(Test-Path -LiteralPath $path) -or (Get-Item -LiteralPath $path).Length -ne $item.bytes) {
        throw ('Verified local model missing/wrong length: ' + $item.name)
    }
    if ((Get-FileHash -LiteralPath $path -Algorithm SHA256).Hash.ToLowerInvariant() -ne $item.sha) {
        throw ('Local integrity mismatch; no device changes performed: ' + $item.name)
    }
}
Invoke-AdbChecked @('shell','run-as','com.vitaltriage.app','id') | Out-Null
if ($RemoveVerifiedIsolatedCopies) {
    foreach ($item in $items) {
        $fixture = '/sdcard/Android/data/com.vitaltriage.app.uitest/files/nllb/' + $item.name
        & $Adb -s $Serial shell test -f $fixture
        if ($LASTEXITCODE -ne 0) { continue }
        $hash = (Invoke-AdbChecked @('shell','sha256sum',$fixture)) -join ''
        if (!$hash.StartsWith($item.sha, [StringComparison]::OrdinalIgnoreCase)) {
            throw ('Isolated fixture hash differs; retained: ' + $item.name)
        }
        Invoke-AdbChecked @('shell','rm',$fixture) | Out-Null
        Write-Output ('Removed only reproducible verified isolated fixture: ' + $item.name)
    }
}
Invoke-AdbChecked @('shell','run-as','com.vitaltriage.app','mkdir','-p','no_backup/nllb') | Out-Null
$stage = '/data/local/tmp/vitaltriage-model-' + [Guid]::NewGuid().ToString('N')
Invoke-AdbChecked @('shell','mkdir',$stage) | Out-Null
Invoke-AdbChecked @('shell','chmod','755',$stage) | Out-Null
$evidence = @()
try {
    foreach ($item in $items) {
        $dest = 'no_backup/nllb/' + $item.name
        & $Adb -s $Serial shell run-as com.vitaltriage.app test -f $dest
        if ($LASTEXITCODE -eq 0) {
            $hash = (Invoke-AdbChecked @('shell','run-as','com.vitaltriage.app','sha256sum',$dest)) -join ''
            if (!$hash.StartsWith($item.sha,[StringComparison]::OrdinalIgnoreCase)) {
                throw ('Normal model differs; preserved without overwrite: ' + $item.name)
            }
            $evidence += @{name=$item.name;sha256=$item.sha;action='reused'}
            continue
        }
        $disk = (Invoke-AdbChecked @('shell','df','-k','/data'))[-1] -split '\s+'
        $available = [long]$disk[3] * 1024
        if ($available -lt (2L * $item.bytes + 67108864)) {
            throw ('Insufficient temporary storage; existing data retained: ' + $item.name)
        }
        $source = $stage + '/' + $item.name
        $partial = $dest + '.' + [Guid]::NewGuid().ToString('N') + '.part'
        try {
            Invoke-AdbChecked @('push',(Join-Path $folder $item.name),$source) | Out-Null
            Invoke-AdbChecked @('shell','chmod','644',$source) | Out-Null
            Invoke-AdbChecked @('shell','run-as','com.vitaltriage.app','cp',$source,$partial) | Out-Null
            $hash = (Invoke-AdbChecked @('shell','run-as','com.vitaltriage.app','sha256sum',$partial)) -join ''
            if (!$hash.StartsWith($item.sha,[StringComparison]::OrdinalIgnoreCase)) { throw 'Device copy integrity mismatch.' }
            Invoke-AdbChecked @('shell','run-as','com.vitaltriage.app','mv','-n',$partial,$dest) | Out-Null
            $finalHash = (Invoke-AdbChecked @('shell','run-as','com.vitaltriage.app','sha256sum',$dest)) -join ''
            if (!$finalHash.StartsWith($item.sha,[StringComparison]::OrdinalIgnoreCase)) { throw 'Final model integrity mismatch.' }
            $evidence += @{name=$item.name;sha256=$item.sha;action='local-copy-verified'}
            Write-Output ('Prepared verified local model: ' + $item.name)
        } finally {
            & $Adb -s $Serial shell rm -f $source | Out-Null
            & $Adb -s $Serial shell run-as com.vitaltriage.app rm -f $partial | Out-Null
        }
    }
} finally {
    & $Adb -s $Serial shell rmdir $stage | Out-Null
}
$report = @{utc=[DateTime]::UtcNow.ToString('o');serial=$Serial;package='com.vitaltriage.app';networkBytes=0;models=$evidence}
[IO.File]::WriteAllText((Join-Path $root '.runtime/normal-nllb-local-copy.json'),($report | ConvertTo-Json -Depth 6))
Write-Output 'Normal-app NLLB files verified. No network download or inference claim; translation stays opt-in.'
