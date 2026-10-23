param([string]$Language = 'en')
$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.Speech
$sentences = @{
    en = 'This is a synthetic test. My throat hurts today.'
    tr = 'Bu sentetik bir testtir. Bugün boğazım ağrıyor.'
    fr = 'Ceci est un test fictif. Je souffre de la gorge depuis ce matin.'
    ar = 'هذا اختبار اصطناعي. أشعر بألم في الحلق منذ الصباح.'
}
if (-not $sentences.ContainsKey($Language)) { throw 'Unsupported fixture language' }
$synth = [System.Speech.Synthesis.SpeechSynthesizer]::new()
$stream = [IO.MemoryStream]::new()
try {
    $voices = @($synth.GetInstalledVoices() | Where-Object { $_.Enabled -and $_.VoiceInfo.Culture.TwoLetterISOLanguageName -eq $Language })
    if ($voices.Count -eq 0) { throw ('No installed offline Windows voice for ' + $Language) }
    $synth.SelectVoice($voices[0].VoiceInfo.Name)
    $format = [System.Speech.AudioFormat.SpeechAudioFormatInfo]::new(16000,[System.Speech.AudioFormat.AudioBitsPerSample]::Sixteen,[System.Speech.AudioFormat.AudioChannel]::Mono)
    $synth.SetOutputToAudioStream($stream,$format)
    $synth.Speak($sentences[$Language])
    $bytes = $stream.ToArray()
    if ($bytes.Length -lt 3200 -or $bytes.Length -gt 928000) { throw 'Unexpected synthetic waveform duration' }
    $root = Split-Path $PSScriptRoot -Parent
    $folder = Join-Path $root '.runtime/synthetic-audio'
    [IO.Directory]::CreateDirectory($folder) | Out-Null
    [IO.File]::WriteAllBytes((Join-Path $folder ($Language + '.pcm')),$bytes)
    Write-Output ('Synthetic offline waveform prepared: language=' + $Language + '; rate=16000; channels=1; PCM16 bytes=' + $bytes.Length)
    [Array]::Clear($bytes,0,$bytes.Length)
} finally { $synth.Dispose();$stream.Dispose() }
