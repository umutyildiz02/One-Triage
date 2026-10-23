param([Parameter(Mandatory=$true)][string]$Vocabulary)
$ErrorActionPreference='Stop'
$root=Split-Path $PSScriptRoot -Parent
$folder=Join-Path $root '.runtime/nllb'
$ledgerPath=Join-Path $root '.runtime/model-downloads.json'
$ledger=Get-Content -LiteralPath $ledgerPath -Raw | ConvertFrom-Json
$items=@(
    @{name='NLLB_cache_initializer.onnx';size=25368443},
    @{name='NLLB_decoder.onnx';size=179109694},
    @{name='NLLB_embed_and_lm_head.onnx';size=524712277},
    @{name='NLLB_encoder.onnx';size=266487014}
)
if ((Get-PSDrive C).Free -lt 4000000000) { throw 'At least 4 GB free disk required.' }
[IO.Directory]::CreateDirectory($folder) | Out-Null
$evidence=@()
foreach($item in $items) {
    $reservation=@($ledger.attempts | Where-Object name -eq ('nllb-2.0.0/'+$item.name))
    if($reservation.Count -eq 0) {
        $reserved=($ledger.attempts | Measure-Object reservedBytes -Sum).Sum
        if($reserved+$item.size -gt 5000000000) { throw 'Aggregate authorized model download limit reached.' }
        $ledger.attempts+= [pscustomobject]@{name=('nllb-2.0.0/'+$item.name);time=[DateTime]::UtcNow.ToString('o');reservedBytes=$item.size;status='RESERVED';license='CC-BY-NC-4.0';source='RTranslator GitHub release2.0.0';note='Publisher has no release digest; local SHA256 is integrity pinning, not independent provenance proof.'}
        [IO.File]::WriteAllText($ledgerPath,($ledger | ConvertTo-Json -Depth 8))
    }
    $file=Join-Path $folder $item.name
    $part=$file+'.part'
    if(!(Test-Path -LiteralPath $file)) {
        & curl.exe --fail --location --proto '=https' --proto-redir '=https' --connect-timeout 20 --max-time 1800 --continue-at - --output $part ('https://github.com/niedev/RTranslator/releases/download/2.0.0/'+$item.name)
        if($LASTEXITCODE -ne 0) { throw 'Download interrupted; partial file retained for explicit resume.' }
        if((Get-Item -LiteralPath $part).Length -ne $item.size) { throw 'Publisher asset length mismatch.' }
        [IO.File]::Move($part,$file)
    }
    if((Get-Item -LiteralPath $file).Length -ne $item.size) { throw 'Existing model length mismatch; preserved for inspection.' }
    $hash=(Get-FileHash -LiteralPath $file -Algorithm SHA256).Hash.ToLowerInvariant()
    $evidence+= [pscustomobject]@{name=$item.name;bytes=$item.size;sha256=$hash;source=('https://github.com/niedev/RTranslator/releases/download/2.0.0/'+$item.name)}
    Write-Output ($item.name+' complete; SHA256 '+$hash)
}
if(!(Test-Path -LiteralPath $Vocabulary)) { throw 'Original vocabulary not found.' }
$vocabTarget=Join-Path $folder 'sentencepiece_bpe.model'
$sourceHash=(Get-FileHash -LiteralPath $Vocabulary -Algorithm SHA256).Hash
if(Test-Path -LiteralPath $vocabTarget) {
    if((Get-FileHash -LiteralPath $vocabTarget -Algorithm SHA256).Hash -ne $sourceHash) { throw 'Vocabulary mismatch; not overwritten.' }
} else { [IO.File]::Copy($Vocabulary,$vocabTarget,$false) }
$evidence+=[pscustomobject]@{name='sentencepiece_bpe.model';bytes=(Get-Item -LiteralPath $vocabTarget).Length;sha256=$sourceHash.ToLowerInvariant();source='Read-only original RTranslator asset'}
[IO.File]::WriteAllText((Join-Path $folder 'integrity.json'),($evidence | ConvertTo-Json -Depth 4))
Write-Output 'Research-only NLLB models prepared. No inference or medical quality claim. No API call.'
