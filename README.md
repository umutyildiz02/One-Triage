# One Triage

One Triage is a personal Android app for speaking, translating, and reviewing symptoms.
The interface uses the OneVoice logo and a mint/green design.

## What it does

- Accepts typed messages and speech.
- Uses prepared Whisper models for offline speech recognition.
- Uses prepared NLLB models for offline translation.
- Shows one translated bubble for each turn.
- Reads messages with an installed Android text-to-speech voice.
- Supports Bluetooth Conversation with explicit message sharing.
- Sends the reviewed original text to OpenAI only after approval.
- Shows a compact, colored urgency result. Errors are not replaced with simulated results.

AI and translation can be wrong. Do not wait for this app in an emergency.
This software is not clinically validated.

## Open and build

1. Clone this repository with its submodules:
   `git clone --recurse-submodules <this-repository-url>`
2. Open the cloned folder in Android Studio.
3. Use JDK 17. Install Android SDK 36, NDK 29.0.14033849, and CMake 3.22.1.
4. Let Gradle sync.
5. For a safe, keyless development build, select the **app** module and **uiTest** build variant.
6. Build with `gradlew.bat :app:assembleUiTest -PcompactModels=true` on Windows,
   or `./gradlew :app:assembleUiTest -PcompactModels=true` on macOS/Linux.

This isolated development package is `com.vitaltriage.app.uitest`.
It does not include an OpenAI key. Development fixtures are not real assessments.
It does not replace the owner's installed personal application.

Android 8.0 (API 26) or later is required. The native targets are ARM64 and x86_64.
Not every phone, microphone, Bluetooth radio, or language has been verified.

See [Build and credentials](docs/BUILDING.md) for the other variants and private configuration.

## Models and languages

Model weights are **not stored in GitHub**.
Use the app's model preparation screen. Review the size and approve the download.
Use Wi-Fi and keep enough free space. NLLB needs about 1 GB, plus working space.
Whisper has separate model choices and storage needs.

The app verifies model files and reuses completed downloads.
Once ready, local speech recognition and translation can work offline.
A suitable installed TTS voice is needed for offline reading.
OpenAI assessments require internet.

Menu, spoken, translation, and assessment languages are separate settings.
A language mapping is not proof of translation accuracy.
Japanese and Korean have known translation-quality limitations.
Always review important wording, including negation, time, and severity.

## Credentials and personal builds

No OpenAI key, access token, signing key, owner usage ledger, or personal APK is included.

The owner's **device** build connects directly from the phone to OpenAI.
It requires private local configuration that is deliberately absent here.
The computer does not need to remain on after installing that configured build.

A key embedded in an APK can be extracted. Such APKs must not be publicly redistributed.
Never commit private configuration or reset an existing usage ledger.
The 100-attempt cap and separate USD safeguards remain in the source.

## Source and licensing

- [Build and credentials](docs/BUILDING.md)
- [Source publication checks and exclusions](docs/PUBLICATION.md)
- [Attribution](NOTICE.md)
- [License scope](LICENSE.md)
- [Third-party license texts](release-delivery/licenses/)
- [Privacy and safety](app/src/commonAssets/PRIVACY_AND_SAFETY.txt)

RTranslator-derived code keeps its Apache-2.0 notices.
Whisper/ggml uses MIT terms; SentencePiece uses Apache-2.0.
NLLB weights have separate CC-BY-NC-4.0 terms and research/medical-domain limitations.
They are not covered by the code licenses and are not included in this repository.
See the [NLLB model card](https://huggingface.co/facebook/nllb-200-distilled-600M).
