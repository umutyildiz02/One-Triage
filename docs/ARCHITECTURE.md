# One Triage architecture

## Modules and state

`app` contains Android activities, XML/Compose presentation, model preparation,
speech, translation, Bluetooth, and assessment repositories. `core` contains
shared language definitions, response validation, safety rules, and budget logic
without Android dependencies. `backend` is an optional development service.

The UI uses ViewModel/StateFlow and lifecycle-aware state. Transcripts and results
are not intended as persistent patient records. Preferences, model files,
profile information, and usage accounting have separate persistence paths.

## Processing flow

Typed text or speech recognition -> editable original -> optional display
translation -> review and explicit assessment approval -> configured repository
-> strict response validation -> result or explicit error.

The `device` variant uses a direct HTTPS OpenAI repository. Development variants
can use isolated synthetic demo fixtures or the optional backend. A release build
needs its own configured HTTPS endpoint. See [Build and credentials](BUILDING.md).

Only reviewed original text is submitted. Translation, TTS, Bluetooth sharing,
and assessment are separate actions. Changes invalidate stale approval/results.
Cancellation and revision checks suppress late callbacks. Live errors do not
fall back to a demo assessment.

## Local work and network boundaries

Whisper and prepared NLLB/ML Kit translation perform local inference. Model
preparation is explicit and contacts external hosts. Android system recognition
has provider-dependent behavior. TTS depends on installed offline voices.

Bluetooth uses a bonded, accepted, single-peer foreground session. Sharing needs
approval for the exact text/language/recipient. Receiving does not trigger assessment.
See [Bluetooth integration](BLUETOOTH_INTEGRATION.md).

The development backend binds loopback and live mode requires a separate access
token. Provider credentials belong in private configuration, never source.
The private direct-device build embeds a key and is unsuitable for public APK
distribution. Endpoint and signing configuration remain unchanged by this PR.

## Safety and verification boundaries

Strict JSON fields and category/language/score/color validation protect the
response contract. The experimental score is not calibrated clinical probability.
Limited emergency-word matching is not comprehensive language understanding;
lack of a match never establishes safety. Unknown or invalid results do not
become LOW.

No production deployment, clinical validation, or current device test is claimed.
The publication review is documented in [PUBLICATION.md](PUBLICATION.md).
Derived-component provenance is recorded separately in [THIRD_PARTY.md](THIRD_PARTY.md).
