param(
  [switch]$IncludeBundle,
    [string]$Sdk="$env:LOCALAPPDATA/Android/Sdk",
    [string]$JavaHome="$env:USERPROFILE/.jdks/ms-17.0.16",
    [string]$BuildTools='35.0.0'
)
$ErrorActionPreference='Stop'
$root=Split-Path $PSScriptRoot -Parent
$apk=Join-Path $root 'app/build/outputs/apk/release/app-release.apk'
$aab=Join-Path $root 'app/build/outputs/bundle/release/app-release.aab'
foreach($path in @($apk)){if(-not(Test-Path -LiteralPath $path)){throw 'Signed APK/AAB output is missing.'}}
$env:JAVA_HOME=$JavaHome
$signer=Join-Path $Sdk "build-tools/$BuildTools/apksigner.bat"
$align=Join-Path $Sdk "build-tools/$BuildTools/zipalign.exe"
$reader=Join-Path $Sdk 'ndk/29.0.14033849/toolchains/llvm/prebuilt/windows-x86_64/bin/llvm-readelf.exe'
$signature=(& $signer verify --verbose --print-certs $apk 2>&1)-join "`n"
if($LASTEXITCODE -ne 0 -or $signature -match 'CN=Android Debug'){throw 'APK is unsigned, invalid or debug-signed.'}
$alignment=(& $align -c -P 16 -v 4 $apk 2>&1)-join "`n"
if($LASTEXITCODE -ne 0){throw 'APK16KB ZIP alignment verification failed.'}
if($IncludeBundle){
$jarResult=(& "$JavaHome/bin/jarsigner.exe" -verify $aab 2>&1)-join "`n"
if($LASTEXITCODE -ne 0 -or $jarResult -notmatch 'jar verified'){throw 'AAB signature verification failed.'}
}
Add-Type -AssemblyName System.IO.Compression.FileSystem
$folder=Join-Path $root '.runtime/release-verification'
[IO.Directory]::CreateDirectory($folder)|Out-Null
$zip=[IO.Compression.ZipFile]::OpenRead($apk)
$native=@()
try{
    foreach($entry in $zip.Entries){
        if($entry.FullName -match '^lib/(arm64-v8a|x86_64)/([^/]+[.]so)$'){
            $abi=$Matches[1];$name=$Matches[2]
            $dest=Join-Path $folder ($abi+'-'+$name)
            [IO.Compression.ZipFileExtensions]::ExtractToFile($entry,$dest,$true)
            $headers=(& $reader -lW $dest 2>&1)
            if($LASTEXITCODE -ne 0){throw 'ELF inspection failed.'}
            $loads=@($headers|Where-Object {$_ -match '^\s*LOAD\s'})
            if($loads.Count -eq 0){throw 'No ELF load segments.'}
            foreach($line in $loads){
                $last=($line.Trim() -split '\s+')[-1]
                if([Convert]::ToInt64($last.Replace('0x',''),16) -lt 16384){throw "ELF16KB alignment failed: $abi/$name"}
            }
            $native+=@{abi=$abi;name=$name;loadSegments=$loads.Count;alignmentAtLeast16384=$true}
        }
        if($entry.FullName -match '[.](dex|xml|json|properties|txt)$'){
            $stream=$entry.Open()
            $memory=New-Object IO.MemoryStream
            try{
                $stream.CopyTo($memory)
                $text=[Text.Encoding]::UTF8.GetString($memory.ToArray())
                if($text -match 'sk-(proj-)?[A-Za-z0-9_-]{25,}'){throw 'Potential OpenAI credential found in package; value suppressed.'}
            }finally{$stream.Dispose();$memory.Dispose()}
        }
    }
}finally{$zip.Dispose()}
foreach($abi in @('arm64-v8a','x86_64')){
    if(-not($native|Where-Object{$_.abi -eq $abi -and $_.name -eq 'libvt-whisper.so'})){throw "Whisper library absent for $abi"}
    if(-not($native|Where-Object{$_.abi -eq $abi -and $_.name -eq 'libvt-sentencepiece.so'})){throw "Tokenizer library absent for $abi"}
}
$delivery=Join-Path $root 'release-delivery/artifacts'
[IO.Directory]::CreateDirectory($delivery)|Out-Null
Copy-Item -LiteralPath $apk -Destination (Join-Path $delivery 'VitalTriage.apk')
if($IncludeBundle){Copy-Item -LiteralPath $aab -Destination (Join-Path $delivery 'VitalTriage-0.2.0-research.aab')
}
$report=@{
    generatedUtc=[DateTime]::UtcNow.ToString('o')
    apk=@{bytes=(Get-Item $apk).Length;sha256=(Get-FileHash $apk -Algorithm SHA256).Hash.ToLowerInvariant();signatureVerified=$true;debugSignature=$false;zipAlignment16KB=$true}
    aab=if($IncludeBundle){@{bytes=(Get-Item $aab).Length;sha256=(Get-FileHash $aab -Algorithm SHA256).Hash.ToLowerInvariant();jarSignatureVerified=$true}
    }else{$null}
  native=$native
    credentialPatternScan='No literal OpenAI-key pattern found; not a proof against every obfuscation or external misconfiguration.'
    physicalDeviceVerified=$false
    publicHttpsEndpointVerified=$false
}
[IO.File]::WriteAllText((Join-Path $delivery 'verification.json'),($report|ConvertTo-Json -Depth 8))
[IO.File]::WriteAllText((Join-Path $delivery 'certificate.txt'),$signature)
Write-Output "Verified signed research artifacts: $delivery"
Write-Output ('APK bytes='+$report.apk.bytes+'; SHA256='+$report.apk.sha256)
Write-Output ('Native libraries checked='+$native.Count+'; no physical-device or clinical certification implied.')
