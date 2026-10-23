# One Triage — third-party attribution

One Triage is an independent product identity. Its original application, backend,
consent, and safety additions coexist with the third-party work identified below.
These acknowledgments describe provenance; they do not imply upstream endorsement.
This documentation revision reorganizes notices without changing upstream license texts.

## Adapted application components

**RTranslator — Copyright 2016 Luca Martino — Apache License 2.0.**
The source baseline is version 2.0.0, commit
`43cd934889302325d6071c9f35e69eaa5bd23df7`, with adaptations from the owner's
customized RTranslator-2.00 / VitalVoice source.

Adapted portions include Pairing/Conversation/Peers layouts and message framing;
WalkieTalkie, onboarding, settings, language dialogs, preference ordering,
language catalogs and TTS quality filtering; NLLB tokenization, greedy/cache
translation and locale sentence segmentation; and recording/voice segmentation.
Recording adaptations also retain **Copyright 2016 Google Inc. and RTranslator
contributors**, as stated in their source header.

The `vv_*` layouts, colors, theme variants, splash dimensions, `user_icon`,
`sound_icon`, and `reload_icon` resources derive from the source layouts and
drawables. Layout measurements and animation timing follow the original XML and
CustomAnimator/ButtonMic sources. Resource names, image tint handling, readiness
controls, and data-processing notices were adapted. Transport uses a distinct
secured service with explicit approval rather than compatibility with the
original unsecured service.

Imported resources include `drawable/app_icon.png` retained as
`drawable-nodpi/vitalvoice_logo.png`; `mic_icon`, `settings_icon`,
`keyboard_icon`, `edit_icon`, `send_icon`, `check_icon`, `back_arrow_icon`,
and `cancel_icon` vectors; and the Nunito Sans fonts listed below.
Retaining an unused imported asset does not remove its provenance obligations.

Original copyright headers and the [Apache-2.0 text](release-delivery/licenses/Apache-2.0.txt)
remain. The earlier duplicate license copy also remains at
[docs/ui-comparison/VITALVOICE-LICENSE.txt](docs/ui-comparison/VITALVOICE-LICENSE.txt).
The catalog and TTS adaptations are recorded in `LegacyLanguageCatalog` and
`LegacyTtsQuality`. No model binary or credential is included.

## Native engines, runtime, and fonts

- **Whisper.cpp / ggml:** Copyright (c) 2023–2024 The ggml authors; MIT.
  Pinned revision `41fc9dea6a4fe056424be86f61164413903fcff4`.
  [Bundled MIT notices](release-delivery/licenses/Whisper-LICENSES.txt).
- **OpenAI Whisper weights:** Copyright (c) 2022 OpenAI; MIT.
  Weights are optional separate downloads, not repository contents.
  The PCM-to-Whisper JNI flow was adapted from the owner's Eren_Project reference;
  see the provenance qualification in [the component inventory](docs/THIRD_PARTY.md).
- **SentencePiece v0.2.0:** Copyright Google Inc.; Apache-2.0.
  [License and bundled dependency terms](release-delivery/licenses/SentencePiece-LICENSE.txt).
  The packaged notice bundle additionally preserves darts-clone (Susumu Yata),
  esaxx (Daisuke Okanohara), and Protocol Buffers (Google Inc.) notices.
- **ONNX Runtime 1.23.0:** Copyright (c) Microsoft Corporation; MIT.
  [License](release-delivery/licenses/ONNX-Runtime-MIT.txt).
- **Nunito Sans:** Copyright 2016 The Nunito Sans Project Authors
  (https://github.com/Fonthausen/NunitoSans); SIL Open Font License 1.1.
  Applies to `nunito_sans.ttf`, `nunito_sans_light.ttf`, and
  `nunito_sans_semibold.ttf`. [Full license](release-delivery/licenses/Nunito-Sans-OFL.txt).

## Translation models and provider attribution

Optional NLLB-200 distilled 600M weights derive from Meta / Facebook AI under
[CC BY-NC 4.0](https://creativecommons.org/licenses/by-nc/4.0/).
The [model card](https://huggingface.co/facebook/nllb-200-distilled-600M) describes
research intent and excludes medical-domain and production use from its intended
scope. These weights are not relicensed by One Triage. Their ONNX conversion
source and integrity records are documented in [the component inventory](docs/THIRD_PARTY.md).

Google ML Kit is governed by its provider terms. The Google translation graphic
is provider attribution, not One Triage branding. Preserve applicable attribution
when that provider is used.

## Other dependencies and distribution

AndroidX / Compose, Kotlin, Gson, and OkHttp retain their Apache-2.0 terms.
JUnit uses EPL-1.0. The Gradle wrapper retains its own copyright/license header.
This summary is not a resolved inventory of every transitive binary dependency.

Keep [LICENSE.md](LICENSE.md), these notices, applicable source headers, and the
[packaged notice bundle](app/src/commonAssets/THIRD_PARTY_NOTICES.txt) with relevant
redistributions. Consult [docs/THIRD_PARTY.md](docs/THIRD_PARTY.md) for remaining
distribution review items and what a fully independent replacement would require.
