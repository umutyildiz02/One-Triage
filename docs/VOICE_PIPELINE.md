# Speech and turn processing

One Triage accepts typed input or speech, retains an editable original, and keeps
translation, reading aloud, sharing, and assessment approval separate.

## Capture and recognition

`SourceAudioCapture` owns AudioRecord on a worker and converts bounded microphone
frames for recognition. It does not write audio to disk. `VoiceSegmenter` applies
sensitivity, silence timeout, and pre-roll preferences when the app owns capture.
An external Android speech service may not honor those controls.

Prepared Whisper models run locally through JNI and pinned Whisper.cpp.
Select Small or Medium in Settings, prepare it explicitly, then enable Offline
Whisper. The unset model choice resolves to Medium in the current source.
[Whisper provenance](WHISPER_PROVENANCE.md) records artifact identities.

Android system recognition is an alternative whose service, language support,
and network behavior depend on the device. The source-capture option for system
recognition requires Android 13 or later. Typed input remains available when
recording permission, hardware, models, or a language are unavailable.

## Turn ordering

A user starts capture; completed text may be translated using the selected
provider and target. The optional translated-turn reader uses installed TTS.
`WalkieTurnLoop` stops capture during playback and uses completion/revision checks
before scheduling the next turn in a user-started automatic session.

Stopping, leaving the relevant lifecycle, or cancellation invalidates pending
callbacks. Automatic capture is not automatic assessment or Bluetooth consent.
Incoming peer playback never authorizes another send or an assessment.

The original and displayed translation remain distinct. Input edits invalidate
stale review/assessment state. No recognition confidence or translation is
manufactured to hide an error.

## Resource limits and evidence

Audio and inference are bounded and cancellable; native work can still be slow
or fail under memory pressure. [Memory notes](VOICE_MEMORY.md) describe these
limits. Smaller models and short input can help but are not reliability guarantees.

Earlier waveform diagnostics exercised recognition independently of physical
microphone capture. Synthetic TTS or licensed FLEURS input is not evidence of
real-world microphone, accent, noise, or clinical performance. [Quality notes](WHISPER_QUALITY.md)
retain failures and uncertainty. No build, model download, microphone test, or
paid request was performed in this publication pass.

Source derivation and required acknowledgments are in [THIRD_PARTY.md](THIRD_PARTY.md).
