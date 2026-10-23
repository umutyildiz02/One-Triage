# One Triage

One Triage is an experimental Android application for describing symptoms, reviewing speech transcripts, translating conversations, and requesting an optional AI assessment. Its workflow keeps the original message available for review and separates translation, Bluetooth sharing, and assessment approval.

One Triage uses OneVoice visual assets. This private source repository excludes credentials, private APKs, and model weights; it does not provide clinical certification.

> **Safety:** One Triage is not a medical device, diagnosis service, or emergency service. Speech recognition, translation, and AI assessments can be wrong. Do not delay emergency assistance while waiting for the app.

## Features

- Type a message or capture speech, then review and edit the original text.
- Run offline speech recognition with prepared Whisper Small or Medium models.
- Choose optional local translation using NLLB or Google ML Kit.
- Keep interface, input, translation target, and assessment languages separate.
- Display a translated turn while retaining its original for review.
- Read text with an available installed Android text-to-speech voice.
- Exchange explicitly approved text with one nearby Bluetooth Conversation peer.
- Request an OpenAI assessment only after reviewing and approving the original.
- Show compact assessment and urgency information, with additional details available.
- Report missing models, invalid responses, and processing errors without substituting a fabricated live result.

## Requirements and installation

Android 8.0 / API 26 or newer is required. Native libraries target ARM64 and x86_64. Some capture options need newer Android versions, and available speech services, voices, memory, and Bluetooth support vary by device.

For development, install Android Studio, JDK 17, Android SDK 36, NDK 29.0.14033849, and CMake 3.22.1. Clone the repository using an account with access:

```sh
git clone --recurse-submodules https://github.com/umutyildiz02/One-Triage.git
```

Open the cloned folder in Android Studio and allow Gradle to sync. If submodules were omitted, run `git submodule update --init --recursive` from that folder. Keep the committed submodule revisions.

Select the **app** module and **uiTest** build variant for keyless development. On Windows:

```powershell
.\gradlew.bat :app:assembleUiTest -PcompactModels=true
```

On macOS or Linux, use `./gradlew :app:assembleUiTest -PcompactModels=true`. The output is `app/build/outputs/apk/uiTest/app-uiTest.apk`. Install that APK on your own test device, or run the selected variant through Android Studio.

The isolated package is `com.vitaltriage.app.uitest`; its technical identifier is retained for compatibility. Its demo controls use synthetic fixtures, not medical predictions. Other variants can share the original application's package: read [Build and credentials](docs/BUILDING.md) before using them. Do not uninstall an existing personal application or erase its data to resolve installation conflicts. No Android build or device installation was performed for this documentation review.

## First launch and model downloads

Read the onboarding notice, choose an interface language, and enter a non-sensitive nickname. An optional profile photo stays on the device. Do not use patient information in the profile.

Open **Settings**, select a Whisper model, and choose its preparation action. Review the download information and approve it explicitly. Small requires about **190 MB**; Medium about **539 MB**. Medium generally needs more processing time and memory. The current unset model preference resolves to Medium; choosing Small is available when resources are limited.

For NLLB, select that translation engine and prepare its models separately. The complete configured set is approximately **1.00 GB**, plus temporary download and working space. ML Kit instead prepares the selected supported language models, approximately **30 MB per language**. Use Wi-Fi and leave storage headroom.

Whisper and NLLB preparation verify expected file sizes and SHA-256 hashes, reuse valid completed files, and install verified artifacts. A failed integrity check is an error, not permission to bypass verification. Ordinary recognition or translation does not silently prepare missing models. ML Kit manages its own model delivery, which may continue after its preparation screen closes.

Once prepared, Whisper and local translation can work offline. Reading aloud also needs a suitable installed offline voice. OpenAI assessments still require internet. See [model provenance and terms](docs/THIRD_PARTY.md) before downloading or redistributing models.

## Selecting languages

Four independent choices control different parts of the experience:

| Choice | Purpose |
|---|---|
| Interface | Menus and messages: English, Turkish, French, or Arabic |
| Input / speech | The language of the original message |
| Translation target | The language displayed for an optional translation |
| Assessment result | The requested language of the AI response |

Select relevant input languages in Settings and choose the active speech language on the main screen. Automatic detection is available, but a manual choice can help with short or ambiguous speech.

The processing registry has 71 language entries; this is a mapping, not a promise of speech, voice, or translation quality in all of them. Some languages support typed input without Whisper recognition. The expanded-language and lower-quality-voice options broaden selection; they do not improve model accuracy. See [Language support](docs/LANGUAGE_SUPPORT.md) for the capability matrix.

## Microphone and reading aloud

Prepare the selected model, enable **Offline Whisper**, then tap the microphone and grant recording permission when requested. Speak a short, clear sentence in a quiet environment. Finish the recording using the screen control, or let the enabled silence-based capture finish the utterance. Review names, numbers, timing, and negative statements before proceeding.

When the app owns capture, Settings exposes microphone sensitivity, silence timeout, and audio anticipation. These controls do not apply equally to an external Android speech provider. The alternative system recognizer depends on the device and may send audio to its provider; selecting it does not guarantee offline processing.

The speaker control uses an installed voice. Optional reading of translated turns must be enabled separately. A user-started automatic capture session waits for playback before its next turn. Stop that session when finished; listening does not approve a Bluetooth send or an assessment.

## Translation

Translation is optional and off by default. Choose an engine and target language, prepare the necessary models, then enable translation. Equal source and target languages skip translation. Missing or unsupported output produces a visible status while the original remains available.

Inspect translations carefully, particularly negation, symptom severity, duration, and who is being described. Recorded limitations include Japanese fever/heat confusion, Korean vocabulary and meaning failures, Turkish severity loss, and German-to-Turkish gender and tense changes. Passing a short example does not establish medical accuracy.

NLLB is a research model with separate noncommercial licensing and medical-domain suitability limitations. ML Kit has its own terms and attribution requirements. Neither option is presented as clinically validated. [Translation details](docs/LOCAL_TRANSLATION.md) explain these boundaries.

## Bluetooth Conversation

Open **Conversation** from Settings on both devices. Enable Bluetooth, grant the requested nearby-device permissions, discover the peer, and complete Android pairing and the app's connection acceptance. Compare pairing information when Android presents it.

Review the selected text and recipient before explicitly sharing. The connection exchanges the chosen nickname; profile photos are not shared. Messages contain text and language information, not a continuous raw-audio stream. The receiving side can optionally translate and read a message.

Conversation supports one foreground peer, requires an explicit reconnect, and does not automatically replay missed messages. Receiving or reading a message never submits it to OpenAI. Bluetooth sharing permission and assessment consent are separate. A recipient can retain received text outside this app, so obtain permission before sharing sensitive information.

## OpenAI assessment and settings

Review the **original** symptoms, choose the result language, and explicitly approve the assessment. Display translation is not silently substituted for the original. Editing the input invalidates stale approval or results. Invalid, uncertain, refused, or failed responses are not converted into low urgency or a simulated answer.

The keyless development variant supports demo fixtures and an optional configured backend. A private personal device variant connects directly to OpenAI over HTTPS and needs excluded local credentials and an existing usage ledger. A configured backend instead holds its provider key on the server. The computer need not stay running for the direct device variant.

The preserved personal safeguards include a 100-attempt cap and separate spending controls. Failed or uncertain requests can consume an allowance. Do not reset usage records to bypass a stop. These safeguards are local controls, not account-wide billing protection.

Settings also covers profile information, language catalogs, speech models, translation preparation, voice selection, privacy, and third-party notices. Leave developer/demo settings for development.

## Troubleshooting

| Problem | What to check |
|---|---|
| Model unavailable | Select the intended engine/model and explicitly complete preparation. |
| Download fails | Check Wi-Fi and free storage; retry explicitly without bypassing hashes. |
| Slow processing or memory error | Use shorter input, consider Small, and use typed input when needed. |
| No microphone input | Check Android permission, the active language, capture mode, and microphone availability. |
| No spoken output | Install an offline voice for the selected language in Android speech settings. |
| Peer missing | Keep both apps foreground; check Bluetooth, permissions, pairing, and acceptance. |
| Assessment unavailable | Check the selected build's configuration, internet, authentication, and remaining allowance. |
| Wrong translation | Retain and review the original; disable translation rather than trusting incorrect wording. |

Do not clear app data as routine troubleshooting: it can remove settings, models, and usage records.

## Privacy, safety, and licensing

Local capture does not intentionally save recordings; conversation text and results are not a patient-record system. Settings, profile information, models, and usage records persist. Model downloads contact their hosts. System speech and ML Kit have provider-specific data handling. An approved assessment leaves the device; requesting `store=false` does not promise zero provider retention.

A personal APK containing an API key is extractable and must stay private. Never commit credentials, signing files, patient data, recordings, or private APKs. See [Privacy and safety](app/src/commonAssets/PRIVACY_AND_SAFETY.txt).

The urgency categories and experimental score are not a validated clinical protocol. Source availability does not establish regulatory approval. [LICENSE.md](LICENSE.md) describes the mixed license scope; [NOTICE.md](NOTICE.md) preserves attribution. No blanket open-source license is granted for all original code or branding.

## Project structure and contributions

| Directory | Contents |
|---|---|
| `app/` | Android UI, capture, translation, Bluetooth, native bridges, tests |
| `core/` | Shared language, assessment, validation, and budget logic |
| `backend/` | Optional development assessment service and tests |
| `third_party/` | Pinned Whisper.cpp and SentencePiece submodules |
| `docs/` | Build, architecture, feature, provenance, and review documentation |
| `release-delivery/licenses/` | Redistributable license texts |
| `scripts/` | Explicit development and verification utilities |
| `config/` | Nonfunctional configuration example |

Contributors with repository access should open a focused issue or pull request against `main`, describe the behavior and validation, and preserve consent, original-text handling, and license notices. Use synthetic or properly licensed examples; redact diagnostics. Discuss rights to new assets and contributions with the maintainer before broader redistribution.

Read [Architecture](docs/ARCHITECTURE.md) and [Publication review](docs/PUBLICATION.md). Documentation readiness is separate from public release, clinical validation, and model licensing clearance.
