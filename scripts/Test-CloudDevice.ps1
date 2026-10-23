param(
    [Parameter(Mandatory=$true)][ValidatePattern('^[a-z][a-z0-9-]{4,62}$')][string]$Project,
    [string]$Model,
    [string]$AndroidVersion,
    [switch]$RunConfirmedFreeQuota
)
$ErrorActionPreference='Stop'
$root=Split-Path $PSScriptRoot -Parent
$gcloud=Get-Command gcloud -ErrorAction SilentlyContinue
if(-not $gcloud){throw 'Install the official Google Cloud CLI and authenticate locally. No account or billing will be created by this script.'}
$accounts=& $gcloud.Source auth list --filter=status:ACTIVE --format='value(account)'
if($LASTEXITCODE -ne 0 -or -not $accounts){throw 'No authenticated Google Cloud account. Run gcloud auth login in your own terminal.'}
# No account identifier/token is printed by this script.
& $gcloud.Source firebase test android models list --project=$Project
if($LASTEXITCODE -ne 0){throw 'Device catalog unavailable. Check existing project authorization.'}
if(-not $RunConfirmedFreeQuota){
    Write-Output 'Catalog only. No test submitted. Confirm existing free quota, physical ARM64 model and version before using -RunConfirmedFreeQuota.'
    exit 0
}
if(-not $Model -or -not $AndroidVersion){throw 'Select one current physical ARM64 model/version from the catalog.'}
$apk=Join-Path $root 'app/build/outputs/apk/uiTest/app-uiTest.apk'
$test=Join-Path $root 'app/build/outputs/apk/androidTest/uiTest/app-uiTest-androidTest.apk'
foreach($file in @($apk,$test)){if(-not(Test-Path -LiteralPath $file)){throw 'Build isolated app and runner first.'}}
# Exactly one bounded, free-quota-authorized run. No retry, OpenAI key, backend token or model upload.
& $gcloud.Source firebase test android run --project=$Project --type=instrumentation --app=$apk --test=$test --device="model=$Model,version=$AndroidVersion,locale=en,orientation=portrait" --timeout=5m --test-targets='class com.vitaltriage.app.LegacyParityTest,class com.vitaltriage.app.ConversationUiTest' --client-details='matrixLabel=VitalTriage-0.2.0-free-smoke'
if($LASTEXITCODE -ne 0){throw 'Cloud run failed or tests failed. Inspect the returned matrix; no automatic retry.'}
Write-Output 'Inspect device kind/ABI, matrix results and skipped cases before claiming physical verification. This smoke run does not prove microphone intelligibility or two-phone Bluetooth.'
