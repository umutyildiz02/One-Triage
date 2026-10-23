param(
    [string]$Serial = 'emulator-5554',
    [string]$Adb = "$env:LOCALAPPDATA/Android/Sdk/platform-tools/adb.exe"
)
$ErrorActionPreference = 'Stop'
# Explicit free regression group. No normal-app reset, model download or paid API request.
$classes = @(
    'com.vitaltriage.app.LegacyFlowTest',
    'com.vitaltriage.app.LivePreferencesTest',
    'com.vitaltriage.app.MainFlowTest',
    'com.vitaltriage.app.MultilingualUiTest',
    'com.vitaltriage.app.ResultAndErrorUiTest',
    'com.vitaltriage.app.SourceCaptureTest',
    'com.vitaltriage.app.SpeechOutputTest',
    'com.vitaltriage.app.WhisperFlowTest',
'com.vitaltriage.app.EmulatorLifecycleTest',
'com.vitaltriage.app.BackendErrorFlowTest',
'com.vitaltriage.app.LegacyParityTest',
 'com.vitaltriage.app.ModelDeliveryUiTest',
 'com.vitaltriage.app.ConversationUiTest',
 'com.vitaltriage.app.BackendAccessStoreTest',
 'com.vitaltriage.app.WalkieHistoryUiTest',
 'com.vitaltriage.app.TranslationReviewUiTest',
 'com.vitaltriage.app.OneVoiceBrandingTest',
 'com.vitaltriage.app.ExpandedLanguageDeviceTest',
 'com.vitaltriage.app.ExpandedLanguageUiTest'
) -join ','
$runner = 'com.vitaltriage.app.uitest.test/androidx.test.runner.AndroidJUnitRunner'
$focus = (& $Adb -s $Serial shell dumpsys window 2>&1) -join "`n"
if ($focus -match 'mCurrentFocus=.*Application Not Responding') {
    throw 'An Android ANR dialog obscures the test device. Inspect and resolve it before running; no dialog is dismissed automatically.'
}
$started = [DateTime]::UtcNow
$output = & $Adb -s $Serial shell am instrument -w -r -e class $classes $runner 2>&1 | ForEach-Object { Write-Host $_; $_ }
$exitCode = $LASTEXITCODE
$text = $output -join "`n"
$cases = @(); $testClass = ''; $testName = ''
foreach ($line in $output) {
    if ($line -match '^INSTRUMENTATION_STATUS: class=(.+)$') { $testClass=$Matches[1].Trim() }
    elseif ($line -match '^INSTRUMENTATION_STATUS: test=(.+)$') { $testName=$Matches[1].Trim() }
    elseif ($line -match '^INSTRUMENTATION_STATUS_CODE: (-?\d+)$' -and [int]$Matches[1] -ne 1) {
        $cases += @{class=$testClass;test=$testName;statusCode=[int]$Matches[1]}
    }
}
$evidence = Join-Path (Split-Path $PSScriptRoot -Parent) '.runtime/test-evidence'
[IO.Directory]::CreateDirectory($evidence) | Out-Null
$report = @{startedUtc=$started.ToString('o');endedUtc=[DateTime]::UtcNow.ToString('o');serial=$Serial;
    package='com.vitaltriage.app.uitest';paidCalls=0;adbExitCode=$exitCode;cases=$cases;
    junitSuccess=($text -match 'OK \(\d+ tests?\)' -and $text -notmatch 'FAILURES!!!|INSTRUMENTATION_FAILED|Process crashed')}
[IO.File]::WriteAllText((Join-Path $evidence ('device-'+$started.ToString('yyyyMMdd-HHmmss')+'.json')),($report | ConvertTo-Json -Depth 5))
# adb may return zero even when JUnit fails.
if ($exitCode -ne 0 -or $text -match 'FAILURES!!!|INSTRUMENTATION_FAILED|Process crashed|INSTRUMENTATION_ABORTED' -or $text -notmatch 'OK \([0-9]+ tests?\)') {
    throw 'Device regression did not report a successful JUnit completion. Inspect the output.'
}
