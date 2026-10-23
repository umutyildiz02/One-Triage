# Attribution

The domain, backend, consent and safety implementation belongs to VitalTriage. Speech/translation adaptations retain the upstream notices below.

Product additions: the source Pairing/Conversation/Peers layouts and framing derive from RTranslator (Copyright 2016 Luca Martino, Apache-2.0); the new secured transport intentionally does not interoperate with the old insecure service. SentencePiece v0.2.0 is Copyright Google Inc., Apache-2.0 (third_party/sentencepiece/LICENSE). ONNX Runtime1.23.0 is Copyright Microsoft Corporation, MIT (https://github.com/microsoft/onnxruntime/blob/v1.23.0/LICENSE). Whisper.cpp/ggml and OpenAI Whisper weights retain MIT notices in app/src/commonAssets/WHISPER_LICENSES.txt.

Optional NLLB-200 distilled600M model artifacts derive from Meta/Facebook AI under CC-BY-NC-4.0 (https://creativecommons.org/licenses/by-nc/4.0/; https://huggingface.co/facebook/nllb-200-distilled-600M). Original RTranslator ONNX conversion provenance/hashes are in docs/NLLB_INTEGRATION.md. Research/noncommercial and model-card medical-domain restrictions remain; model files are not relicensed by VitalTriage and are not embedded in release artifacts. Their use is not clinical validation.
The WalkieTalkie, onboarding and settings presentations follow the user's RTranslator-2.00 / VitalVoice source (Apache-2.0), Copyright 2016 Luca Martino.

The `vv_*` layouts, colors, theme variants and preference ordering are adapted from activity_loading/access/settings, fragment_notice/user_data/download/settings, preference_user_image/user_name/seekbar, dialog_edit_name/languages and component_row_language. `user_icon`, `sound_icon`, `reload_icon` vectors and the splash layer dimensions are also reused. Resource names, AppCompat image tint handling, readiness controls and data-processing notices were adapted for the current implementation. Original copyright headers are retained where present. These adaptations do not include model binaries, API credentials or automatic analysis.
LegacyLanguageCatalog additionally derives the original Global/Recognizer/Translator catalog intersection from the normal/all language XML lists. LegacyTtsQuality adapts the tools/TTS.java Voice quality and legacy-voice filter. Both retain Luca Martino's Apache-2.0 attribution; the full license is in docs/ui-comparison/VITALVOICE-LICENSE.txt. Catalog availability is not a claim of model support or clinical validation.

Imported, without modification, from that project's app/src/main/res:
- drawable/app_icon.png → drawable-nodpi/vitalvoice_logo.png
- font/nunito_sans.ttf, nunito_sans_light.ttf, nunito_sans_semibold.ttf
- drawable/mic_icon.xml, settings_icon.xml, keyboard_icon.xml, edit_icon.xml, send_icon.xml, check_icon.xml, back_arrow_icon.xml, cancel_icon.xml

Layout measurements, colors and animation timing follow the original XML and CustomAnimator/ButtonMic sources. No original credentials, models or automatic OpenAI request behavior were imported. The alternative "as" logo is not used.

Nunito Sans is distributed under the SIL Open Font License; RTranslator under Apache-2.0. Dependencies retain their upstream licenses (AndroidX/Compose, Kotlin, Gson, OkHttp: Apache-2.0; JUnit: EPL-1.0). The Gradle wrapper retains its copyright/license header.
