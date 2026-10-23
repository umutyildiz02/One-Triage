# One Triage — license scope

This repository contains original project material and separately licensed
third-party components. It does **not** grant a new blanket license for all of
One Triage's original code, documentation, branding, or artwork. Repository access
and publication preparation are not permission for unrestricted redistribution.

The One Triage / OneVoice identity is owner-supplied. No trademark or artwork
rights are granted by this document. Existing third-party permissions continue
to apply to their respective materials; this statement does not revoke or narrow
those permissions.

## Component licenses

| Material | Applicable terms |
|---|---|
| Adapted application components identified in NOTICE.md | Apache-2.0 |
| Whisper.cpp / ggml and optional OpenAI Whisper weights | MIT |
| SentencePiece | Apache-2.0 and bundled dependency notices |
| ONNX Runtime | MIT |
| Nunito Sans fonts | SIL Open Font License 1.1 |
| Optional NLLB weights | CC BY-NC 4.0; separate from application code |
| Google ML Kit and its translation attribution | Provider terms and attribution requirements |

See [NOTICE.md](NOTICE.md), [the component inventory](docs/THIRD_PARTY.md),
[full license texts](release-delivery/licenses/), and
[the packaged notices](app/src/commonAssets/THIRD_PARTY_NOTICES.txt).
Existing copyright, license, attribution, and modification notices must remain
with the material to which they apply. Upstream license text is authoritative.

## Models and distribution limits

No Whisper or NLLB weights are committed. Downloading them does not make their
terms part of the application's code license. The
[NLLB model card](https://huggingface.co/facebook/nllb-200-distilled-600M) describes
research intent, says the model is not released for production deployment, and
places medical-domain translation outside its intended scope. Its
[CC BY-NC 4.0 license](https://creativecommons.org/licenses/by-nc/4.0/) additionally
limits use to noncommercial purposes under its conditions. Intended-use guidance
and copyright license terms are distinct; neither establishes clinical safety.

Before broader distribution, the maintainer must choose an explicit license for
original contributions, establish rights to owner-supplied and adapted assets,
review unresolved provenance and resolved dependency terms, and decide whether
to replace models unsuitable for the intended use. This private documentation
review does not resolve those decisions or certify medical/regulatory compliance.
