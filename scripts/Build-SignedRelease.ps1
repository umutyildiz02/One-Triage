param(
    [switch]$InitializeSigning,
  [switch]$IncludeBundle,
    [string]$ProductionEndpoint = '',
    [string]$JavaHome = "$env:USERPROFILE/.jdks/ms-17.0.16"
)
$ErrorActionPreference='Stop'
$root=Split-Path $PSScriptRoot -Parent
$signing=Join-Path $root '.runtime/signing'
$store=Join-Path $signing 'vitaltriage-release.p12'
$protected=Join-Path $signing 'password.dpapi'
$keytool=Join-Path $JavaHome 'bin/keytool.exe'
if(-not(Test-Path -LiteralPath $keytool)){throw 'JDK keytool is missing. Supply -JavaHome.'}
if($ProductionEndpoint){
    $uri=[Uri]$ProductionEndpoint
    if($uri.Scheme -ne 'https' -or -not $uri.Host -or $uri.UserInfo -or $uri.Query -or $uri.Fragment){throw 'Use a credential-free HTTPS endpoint.'}
}
if($InitializeSigning){
    if((Test-Path -LiteralPath $store) -or (Test-Path -LiteralPath $protected)){throw 'Signing material already exists. It will not be replaced.'}
    [IO.Directory]::CreateDirectory($signing)|Out-Null
    $acl=New-Object Security.AccessControl.DirectorySecurity
    $sid=[Security.Principal.WindowsIdentity]::GetCurrent().User
    $acl.SetAccessRuleProtection($true,$false)
    $rule=New-Object Security.AccessControl.FileSystemAccessRule($sid,'FullControl','ContainerInherit,ObjectInherit','None','Allow')
    $acl.AddAccessRule($rule)
    Set-Acl -LiteralPath $signing -AclObject $acl
    $bytes=New-Object byte[] 32
    $rng=[Security.Cryptography.RandomNumberGenerator]::Create()
    try{$rng.GetBytes($bytes)}finally{$rng.Dispose()}
    $password=[Convert]::ToBase64String($bytes)
    [Array]::Clear($bytes,0,$bytes.Length)
    $secure=ConvertTo-SecureString $password -AsPlainText -Force
    # DPAPI is tied to this Windows user. Neither this file nor the key belongs in Git/delivery.
    [IO.File]::WriteAllText($protected,(ConvertFrom-SecureString $secure))
    $env:VT_SIGNING_STORE_PASSWORD=$password
    try{
        & $keytool -genkeypair -keystore $store -storetype PKCS12 -alias vitaltriage-release -keyalg RSA -keysize 3072 -validity 10000 -dname 'CN=VitalTriage Local Release' -storepass:env VT_SIGNING_STORE_PASSWORD -keypass:env VT_SIGNING_STORE_PASSWORD
        if($LASTEXITCODE -ne 0){throw 'Key generation failed. Existing files were retained for inspection.'}
    }finally{
        Remove-Item Env:VT_SIGNING_STORE_PASSWORD -ErrorAction SilentlyContinue
        $password=$null
        $secure.Dispose()
    }
}
if(-not(Test-Path -LiteralPath $store) -or -not(Test-Path -LiteralPath $protected)){throw 'No local release signing material. Run once with -InitializeSigning.'}
$secure=ConvertTo-SecureString ([IO.File]::ReadAllText($protected))
$ptr=[Runtime.InteropServices.Marshal]::SecureStringToBSTR($secure)
try{
    $env:VT_SIGNING_STORE_PASSWORD=[Runtime.InteropServices.Marshal]::PtrToStringBSTR($ptr)
    $env:VT_SIGNING_KEY_PASSWORD=$env:VT_SIGNING_STORE_PASSWORD
    $env:VT_SIGNING_STORE=$store
    $env:VT_SIGNING_ALIAS='vitaltriage-release'
    $env:JAVA_HOME=$JavaHome
    Push-Location $root
    try{
        $tasks=@(':app:assembleRelease',':app:lintRelease')
  if($IncludeBundle){$tasks+= ':app:bundleRelease'}
  & ./gradlew.bat @tasks "-PproductionEndpoint=$ProductionEndpoint" --max-workers=2 --no-daemon
        if($LASTEXITCODE -ne 0){throw 'Release build or lint failed.'}
    }finally{Pop-Location}
}finally{
    [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($ptr)
    $secure.Dispose()
    foreach($name in @('VT_SIGNING_STORE_PASSWORD','VT_SIGNING_KEY_PASSWORD','VT_SIGNING_STORE','VT_SIGNING_ALIAS')){
        Remove-Item "Env:$name" -ErrorAction SilentlyContinue
    }
}
Write-Output 'Signed release build completed. Verify the APK signature before direct-install delivery. AAB is optional.'
Write-Output 'The key is local and DPAPI-protected. Owner-controlled secure backup/custody is still required.'
Write-Output 'A signed artifact does not establish a working HTTPS backend, Play acceptance or clinical suitability.'
