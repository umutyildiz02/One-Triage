param([string]$Device='emulator-5556',[string]$JavaHome="$env:USERPROFILE/.jdks/ms-17.0.16",[switch]$Install)
$ErrorActionPreference='Stop'
$root=Split-Path $PSScriptRoot -Parent
$tools=Join-Path $root '.runtime/tools'
[IO.Directory]::CreateDirectory($tools)|Out-Null
$jar=Join-Path $tools 'bundletool-all-1.18.3.jar'
$expected='a099cfa1543f55593bc2ed16a70a7c67fe54b1747bb7301f37fdfd6d91028e29'
if(-not(Test-Path -LiteralPath $jar)){
    Invoke-WebRequest -UseBasicParsing -Uri 'https://github.com/google/bundletool/releases/download/1.18.3/bundletool-all-1.18.3.jar' -OutFile $jar
}
if((Get-FileHash $jar -Algorithm SHA256).Hash.ToLowerInvariant() -ne $expected){throw 'Official bundletool checksum mismatch'}
$java=Join-Path $JavaHome 'bin/java.exe'
$aab=Join-Path $root 'app/build/outputs/bundle/release/app-release.aab'
& $java -jar $jar validate --bundle=$aab
if($LASTEXITCODE -ne 0){throw 'AAB structural validation failed'}
$signing=Join-Path $root '.runtime/signing'
$secure=ConvertTo-SecureString ([IO.File]::ReadAllText((Join-Path $signing 'password.dpapi')))
$ptr=[Runtime.InteropServices.Marshal]::SecureStringToBSTR($secure)
# Only a short-lived file under the owner-only signing ACL; never password process arguments/logs.
$temp=Join-Path $signing ([Guid]::NewGuid().ToString()+'.password')
$apks=Join-Path $root '.runtime/release-verification/release-device.apks'
try{
    [IO.File]::WriteAllText($temp,[Runtime.InteropServices.Marshal]::PtrToStringBSTR($ptr))
    $store=Join-Path $signing 'vitaltriage-release.p12'
    & $java -jar $jar build-apks --bundle=$aab --output=$apks --overwrite --connected-device --device-id=$Device --ks=$store --ks-key-alias=vitaltriage-release --ks-pass="file:$temp"
    if($LASTEXITCODE -ne 0){throw 'Signed device APK-set generation failed'}
}finally{
    if(Test-Path -LiteralPath $temp){Remove-Item -LiteralPath $temp -Force}
    [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($ptr);$secure.Dispose()
}
& $java -jar $jar get-size total --apks=$apks --dimensions=ABI
if($LASTEXITCODE -ne 0){throw 'APK-set size inspection failed'}
if($Install){
    if($Device -eq 'emulator-5554'){throw 'Refusing release install onto protected normal debug installation'}
    & $java -jar $jar install-apks --apks=$apks --device-id=$Device
    if($LASTEXITCODE -ne 0){throw 'APK-set install failed; no uninstall or data deletion attempted'}
}
Write-Output 'Bundle structurally validated and signed device APK set generated. Physical hardware/public HTTPS/Play approval are not established.'
