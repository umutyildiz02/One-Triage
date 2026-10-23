$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.Speech
$root = Split-Path $PSScriptRoot -Parent
$directory = Join-Path $root '.runtime/synthetic-audio'
[IO.Directory]::CreateDirectory($directory) | Out-Null
$path = Join-Path $directory 'synthetic-triage-en.wav'
$speech = New-Object System.Speech.Synthesis.SpeechSynthesizer
try {
    $voice = $speech.GetInstalledVoices() | Where-Object { $_.Enabled -and $_.VoiceInfo.Culture.Name -eq 'en-US' } | Select-Object -First 1
    if ($null -eq $voice) { throw 'Installed offline English voice missing' }
    $speech.SelectVoice($voice.VoiceInfo.Name)
    $format = New-Object System.Speech.AudioFormat.SpeechAudioFormatInfo(16000,[System.Speech.AudioFormat.AudioBitsPerSample]::Sixteen,[System.Speech.AudioFormat.AudioChannel]::Mono)
    $speech.SetOutputToWaveFile($path,$format)
    $speech.Speak('This is a synthetic case. An adult has severe chest pain and trouble breathing, starting twenty minutes ago.')
    $speech.SetOutputToNull()
    Write-Output ('Synthetic offline WAV prepared; bytes=' + (Get-Item $path).Length + '; rate=16000; mono PCM16; OpenAI requests=0.')
} finally { $speech.Dispose() }
