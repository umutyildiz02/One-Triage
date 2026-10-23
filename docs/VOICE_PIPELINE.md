# Voice pipeline audit — 2026-10-20

## Latest measured integration — 2026-10-21

Native6 and installed-TTS5 checks passed610 offline. The combined Turkish waveform→Whisper→NLLB→review→explicit mock assessment passed639 on4GB/4cores in50.796s. On3GB the low-memory guard safely cancels optional translation; a tested fallback retains already recognized text without restarting capture or submitting assessment. Final55/55 UI regressions726 pass. Exact final Studio-built normal device APK smoke742 passes offline/no adb reverse: real installed-TTS waveform→Medium36.632s, NLLB EN→TR5.629s, preferences unchanged. This is file/waveform inference, not a physical microphone or ARM64 claim. See EMULATOR_PERFORMANCE.md for timings and retained failures.

A bounded host-microphone probe614 captured132096 real samples but failed expected-word recognition. Host routing was turnedOFF; this is not a successful human-microphone test. Existing four-language WAV/PCM success and ARM64 compilation remain separate evidence.

## Legacy parity correction

The original languagesNNQualityLow switch selects language catalogs, not model weights: 31/57 source intersection entries are now filtered in the native language dialog; only the four contract languages are selectable. languagesQualityLow now changes actual Android Voice quality/legacy eligibility while keeping installed/offline privacy restrictions. Neither needs a new NLLB/TTS engine. Earlier unavailable explanations below are historical. Exact source chains and limits (local audit, excluded from this source backup).

## Current capability map (supersedes historical pending-native sections)

| Stage | Actual implementation/evidence | Limit |
|---|---|---|
| Capture/VAD | RTranslator-derived threshold, pre-roll and silence segmentation; bounded AudioRecord; real capture/cancel/restart and real PCM segmenter tests | No intelligible hardware-microphone speech captured in this session |
| Local STT | Pinned whisper.cpp1.8.0 JNI, greedy, up to4 threads on x86_64 (measured), up to2 on ARM64 (unmeasured); runtime CPU dispatch and both ABI builds | Physical ARM64 execution unverified; target-only CPU abort adapter now interrupts manual/auto inference (728/290ms measured), initial model preparation has a separate bound |
| Small190MB | Actual EN synthetic/TR/FR public recordings pass manual+auto | AR WER0.40 fails unchanged0.35 threshold |
| Medium539MB | Actual EN/TR/FR/AR manual+auto pass; TR/FR/AR WER0.0/0.25/0.20 | Final 3GB guest: manual29–36s, auto53–66s on short fixtures; higher RAM and remaining FR/AR errors. See EMULATOR_PERFORMANCE.md. |
| Native speech translation | Actual French audio translated | Meaning loss; beam5 LOW_MEMORY; not production translation |
| Optional text translation | RTranslator2.0.0 ONNX/NLLB cache/greedy decoder + SentencePiece, or earlier ML Kit adapter; independent EN/TR/FR/AR targets; real offline Turkish Whisper→NLLB→review/consent passed639 | NLLB severity omission and ML Kit meaning failures remain; default-off, original-only assessment. NLLB research/license limits apply. |
| TTS | Actual EN/TR/FR/AR offline playback/stop/restart passed shell-250 after explicit official voice setup | Perceived pronunciation/clinical suitability not human-validated |
| LIVE | Actual WAV→Whisper→consent→OpenAI→normal-app result; four independently selected output languages | Not microphone proof, not general language/clinical validation |

Settings select Small/Medium; existing source sensitivity, silence and pre-roll rows control the actual capture adapter. Compact debug/device/release APKs use explicit HTTPS preparation into private no-backup storage, with pinned length/hash and resumable bounded downloads. Older optional fat debug builds can still use ignored local assets. Whisper releases its context before NLLB allocation; leaving the Activity cancels/releases work. Actual preparation progress is shown. Clean normal release model delivery was verified542/563. Source-derived Apache2 notices and pinned Whisper MIT notices remain.

See WHISPER_QUALITY.md, VOICE_MEMORY.md, LOCAL_TRANSLATION.md and HANDOFF.md for current evidence and limitations. Exact new Whisper downloads803677074 bytes; SDK-model allowance tracked separately in the same persistent5GB ledger. Earlier no-download/pending-approval text below describes prior stages.


## Native Whisper continuation (supersedes pending-native statements below)

Owner authorization now covers native integration and controlled resources. Commit 9b3cb4c builds pinned official whisper.cpp v1.8.0 for x86_64 and arm64-v8a. The existing Eren model is actual multilingual GGML despite its .gguf extension; its 190085487 bytes and SHA-256 match the publisher artifact. See WHISPER_PROVENANCE.md. No new model download was needed. Model weights/build outputs are ignored; MIT notices are packaged.

WhisperEngine serializes model preparation and inference off the UI thread. JNI accepts bounded real 16 kHz PCM16, uses two CPU threads, suppresses logs, rejects silence, honors cancellation and a 120-second work deadline. Automatic mode uses language detection plus transcription, with detect_language=false (fixing Eren's detect-only early return). No fixed confidence is manufactured. SourceAudioCapture and VoiceSegmenter feed this engine when Offline Whisper is enabled. Returned text remains editable and requires explicit analysis consent.

Verified on medium_phone: real English offline-TTS waveform → Whisper in manual en and auto modes, expected words and detected en. Load 1752 ms, manual 67970 ms, auto 116864 ms. Actual PCM → VAD → production SpeechController → editor test also passed, with zero repository/API calls before approval. These are waveform tests, not physical microphone evidence. High x86_64 latency is a current limitation; ARM64 compilation is not a physical-device run.

The authenticated emulator gRPC injection attempt failed with permission-denied because injectAudio is not authorized for this test client. No allowlist, security setting or client identity was altered to bypass it. Manual empty capture showed a no-match error and retained typed entry. Physical/emulated microphone speech recognition remains a separate blocked verification item.

Turkish/French/Arabic public non-clinical FLEURS fixtures (CC-BY-4.0) were obtained individually, approximately 2 MB total, for separate actual-engine tests. Their results are pending; English success does not imply four-language success. No paid OpenAI request is part of these diagnostics.

## Historical source audit and previous provider stage


## Continuation: source capture and VAD port

The earlier inventory below is retained as historical context. The following parts are now implemented in VitalTriage:

- VoiceSegmenter adapts RTranslator Recorder/Global amplitude detection (15 above-threshold samples), original sensitivity curve (0→15000, 50→2000, 100→400), silence range 100–5000 ms and pre-roll range 100–1800 ms. Milliseconds are converted to samples before division, correcting the source truncation bug.
- SourceAudioCapture uses actual AudioRecord at 16 kHz mono PCM float, converted to bounded PCM16. A single worker owns hardware creation/read/stop/release; nonblocking reads, negative-read checks, cancellation suppression and bounded 29-second utterance / 30-second session avoid the original release race and Eren's unbounded Short list.
- Manual mode collects until user stop or safety cap. Automatic mode waits for threshold activity, keeps pre-roll and completes one utterance after the selected silence. This is amplitude VAD, not a trained noise classifier or automatic language detection.
- The optional **Source recorder / VAD (experimental)** switch in the existing Developer/Test section enables this capture adapter on Android 13+. It is off by default. Existing microphone controls and animations are reused. Captured audio is offered to the configured SpeechRecognizer through a PCM descriptor; that provider may reject/ignore external audio and may use cloud processing. This does not claim successful STT on medium_phone.
- With this switch enabled, the original three settings rows become functional using the adapted original preference_seekbar XML, reset vector and slider dimensions. Settings persist as bounded scalar preferences and are read at the next capture start. With it disabled, explanatory rows remain; no slider pretends to control the system provider.
- Audio/transcript content is not persisted or logged. A transcript, if actually returned by the provider, enters the existing editor and explicit analysis approval pipeline. No automatic OpenAI request was added.
- No native model, JNI library, Whisper, NLLB or translation engine was copied or downloaded. Native resource approval remains pending.

Additional confirmed Eren defect: native-lib.cpp sets detect_language=true in automatic mode. Its bundled whisper.cpp/src/whisper.cpp, around line 6818, returns immediately after language detection when this flag is true, before transcribing. Reuse requires language=null/auto with detect-only disabled, plus actual tests; the old branch is not a working automatic dictation implementation as written.

The source license is retained in code/layout headers and docs/ui-comparison/VITALVOICE-LICENSE.txt. SourceAudioCapture is a new lifecycle adapter; the source-derived algorithms are in VoiceSegmenter and CapturePreference. No clinical validation is implied.


This is a source/runtime evidence inventory, not a claim that every reference project works. Reference folders were read only. No model was downloaded or copied into VitalTriage during this stage.

## Actual sources found

| Root | Inspected implementation | Evidence and missing pieces |
|---|---|---|
| <local-reference-root>/RTranslator-2.00 | app/src/main/java/nie/translator/rtranslator/voice_translation/neural_networks/voice/Recorder.java, Recognizer.java; translation/Translator.java; access/DownloadFragment.java, Downloader.java; NeuralNetworkApi.java | Custom capture/VAD and ONNX Whisper/NLLB code present. No .onnx model found in the searched Traslator tree. Original runtime unavailable. |
| <local-reference-root>/Eren_Project | app/src/main/java/com/example/eren_project/audio/AudioRecorder.kt; ml/WhisperBridge.kt, ModelPath.kt; app/src/main/cpp/native-lib.cpp, CMakeLists.txt | Actual model app/src/main/assets/ggml-small-q5_1.gguf: 190,085,487 bytes. Built libnative-lib.so found only for arm64-v8a. x86_64 ML Kit libraries are not the Whisper JNI library. No successful transcription evidence established. |
| <local-reference-root>/WhisperCppAndroidDemo-main | app/src/main/java/com/whispercppdemo/whisper/LibWhisper.kt | Float PCM to whisper.cpp, single-thread coroutine dispatcher. No built .so found. No full model identified in inspected .bin search. MIT, Kevin Brothaler 2022. This is the actual located sample; no literal WhisperCppDemo root was assumed. |
| <local-reference-root>/speechtranslator | app/src/main/java/com/example/speechtranslator/MainActivity.kt | Actual code uses SpeechRecognizer plus ML Kit Translation and system TTS. Vosk/Argos asset folders exist but are not wired into this Activity; searches found no final.mdl or .bin there. Source existence does not prove offline Vosk functionality. |
| <local-reference-root>/speechtranslator | Folder discovered | Alternate copy not treated as independently verified. |
| <local-reference-root>/Android_ChatGPT-main | app/src/main/java/com/example/easychatgpt/MainActivity.java, selected non-secret request lines | Legacy /v1/completions with gpt-3.5-turbo-instruct and question as prompt. Not reused: no benefit over existing backend Responses contract. |
| <local-reference-root>/as | Previously documented alternate source | Not used for logo/assets or credentials. |

## Capture and VAD comparison

RTranslator Recorder actually captures **16 kHz mono PCM float**, despite an obsolete PCM16 comment. It has a 30-second ring buffer, a 29-second maximum utterance, amplitude-based voice detection, adjustable silence duration, pre-roll and manual mode. Detection requires at least 15 samples over threshold; this is amplitude VAD, not a trained noise classifier. Defaults: threshold 2,000 on a 32,767 scale, silence 1,300 ms, pre-roll 1,300 ms. Source ranges: threshold 400–15,000, silence 100–5,000 ms, pre-roll 100–1,800 ms. Level callbacks drive microphone animation. Float ring storage alone is about 1.92 MB.

Direct copying would retain defects: pre-roll computes integer seconds before multiplication (losing milliseconds), negative AudioRecord read results are not handled, interrupted worker is not joined before release, and Bluetooth/global-service coupling complicates lifecycle. These must be fixed if ported. Bluetooth routing is not verified.

Eren AudioRecorder uses 16 kHz mono PCM16 / VOICE_RECOGNITION. Its boxed Short list grows without a duration bound and stop joins the worker synchronously. It has no equivalent pre-roll/VAD settings. JNI converts to float, applies capped gain, uses up to four threads, hardcodes Turkish in manual mode, enables language detection in automatic mode, and returns a fixed confidence of 1.0. That fixed value is not usable confidence evidence. Its native configuration is ARM-oriented and needs an actual x86_64 build. Model suffix .gguf alone does not establish format compatibility or integrity.

Current VitalTriage uses the configured Android recognition provider, explicitly requests the selected language, accepts partial/final text, bounds the session at 30 seconds, and waits five seconds after stop. Language changes/settings/backgrounding close the recognizer. No transcript automatically triggers analysis. Provider audio may be processed online; disclosure is shown. A provider being installed does not prove its offline model is installed: the earlier unconditional preference for an available on-device engine was removed. Permission, unsupported/unavailable language, busy, audio, network and no-match errors preserve typed input. Detection callbacks are displayed only for a selected language at confident-or-better provider confidence. Automatic switching remains **provider dependent, unverified**.

The original amplitude threshold, exact manual capture semantics, pre-roll and silence end-point controls are **not implemented by SpeechRecognizer itself**. They now operate in the optional SourceAudioCapture adapter described above. With that option off, Settings explain the limitation instead of showing fake sliders.

## STT candidates and resource decision

- RTranslator ONNX: float[1,N] audio, multiple initializer/encoder/cache/decoder/detokenizer sessions, explicit one/two-language decoding. Uses ONNX Runtime plus extensions/custom ops. Text language identification is ML Kit (0.5 confidence, or forced 0.01), not a proof of acoustic language identification. Original NLLB produces translations from tokenized text. Startup latency, inference latency and peak RAM were not measured; no runtime equivalence claimed.
- Eren whisper.cpp: reusable native candidate with a real 190 MB local quantized model; offline multilingual inference is a capability of the engine, not yet proven for this file/build. Integration needs ABI rebuild, real language parameterization, bounded recorder/VAD, cancellation/serialization, model integrity/provenance and actual PCM tests. User approval for this memory/disk-intensive step is pending. No copying/building heavy native infrastructure while that decision is pending.
- WhisperCppAndroidDemo: simpler float-array wrapper, but native/model setup unverified and executor cleanup needs correction. Lower evidence than Eren's existing binary/model, still not a proven working alternative.
- System provider: no new app model/NDK dependency, smallest integration cost; device controls support/privacy. Real engine test below failed; it is a fallback interface, not verified dictation.

Upstream whisper.cpp documents roughly 852 MB memory for the unquantized small model; quantization reduces requirements, but actual RAM/latency for the local 190 MB file must be measured. This is why native integration was submitted for a resource decision. [Upstream memory and licensing](https://github.com/ggml-org/whisper.cpp).

## Original model package

DownloadFragment lists ten HTTPS GitHub release assets under https://github.com/niedev/RTranslator/releases/download/2.0.0/ :

- NLLB cache initializer 24,000 KB, decoder 171,000 KB, embed/lm-head 500,000 KB, encoder 254,000 KB.
- Whisper cache initializer 14,000 KB, batched initializer 14,000 KB, decoder 173,000 KB, detokenizer 461 KB, encoder 88,000 KB, initializer 69 KB.
- Source approximate total: **1,238,530 KB**. Whisper subset alone is approximately 289,530 KB. No such download was started.
- The source's integrity check attempts to load an ONNX session; it is not a cryptographic checksum verification. Trusted checksums and exact model provenance still need establishing before reuse.
- RTranslator source is Apache-2.0; model licenses are separate. NLLB-200-distilled-600M is CC-BY-NC-4.0, research oriented, and its model card excludes medical-domain use. Do not treat translation as clinically validated. [Publisher model card](https://huggingface.co/facebook/nllb-200-distilled-600M).
- No unnecessary English translation layer was added: the backend receives the original reviewed text with independent response_language. The original bilingual bubble remains a documented missing feature, not a fabricated translation.

## Actual device audio evidence

medium_phone API36 x86_64, emulator-5554:
- Configured provider: com.google.android.tts/...GoogleTTSRecognitionService. AiAi on-device service also listed.
- TTS engine: com.google.android.tts/...GoogleTtsService.
- Isolated SpeechEngineProbeTest generated **167,834 PCM bytes, 24 kHz, mono PCM16** from an explicitly synthetic English sentence using the real installed offline TTS engine.
- Installed offline voices among the requested languages: **English only**. Turkish/French/Arabic offline TTS are not ready on this AVD; no voice was downloaded.
- The PCM was passed using RecognizerIntent.EXTRA_AUDIO_SOURCE to the actual configured STT engine through the production SpeechController. Result: **speechNoMatch, zero recognized characters**. Test failed; this is not transcript injection, and it is not successful speech recognition. Provider/model/audio-source compatibility remains unresolved.
- See ui-comparison/live/speech-probe.json. A second attempt normalized actual PCM from 24 kHz to 16 kHz (111,888 bytes), matching the original recorder format; it also returned speechNoMatch. See speech-probe-16khz.json and both instrumentation logs in that directory. This rules out 24 kHz alone as a sufficient explanation, not every provider/audio compatibility issue. No OpenAI call was made by either attempt.
- Actual physical microphone-to-transcript, automatic language detection/switching, noisy-room accuracy, RAM and latency measurements remain unverified. Physical-device testing and/or the approved local native pipeline is required.

## TTS and UI

The existing original vv_sound_icon is restored in the header. Write a message remains as a secondary control in the white area. Explicit speaker activation reads the validated assessment with its prototype disclaimer, or the draft in its selected speech language. Only installed offline voices are selected, highest available quality; no automatic playback, model download or cloud speech synthesis. Microphone activation/backgrounding stops playback; destruction releases the engine. Missing voices produce a four-language error.

The original low-quality TTS catalog switch remains explanatory, because selecting the highest installed local voice does not reproduce that catalog. Native onboarding/settings labels remain mostly English. Original runtime pixel comparisons remain unavailable.
