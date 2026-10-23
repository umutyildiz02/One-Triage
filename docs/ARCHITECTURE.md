# Architecture and reference decisions

## Reference review
Vital_Voice's WalkieTalkieFragment.extractUrgency parsed English text and defaulted to a black-on-black display on unknown values. The new core uses strict typed JSON and UNDETERMINED with a visible neutral color.
Vital_Voice's language selection and urgency-color presentation informed the UX; source was not copied.
Eren's MainActivity had empty navigation callbacks and its Whisper JNI enabled detect_language-only mode. Those implementations were not carried over.
The old OpenAI client logged prompts/responses and relied on client-side keys. No old key or client code was reused.

## Data flow
App UiState -> review + explicit confirmation -> local Demo fixture OR BackendRepository -> loopback backend -> OpenAI Responses structured output -> shared validation and safety checks -> Evaluation -> result UI.
Core is shared by Android and backend, with no Android dependencies.
App uses ViewModel/StateFlow and lifecycle-aware collection. Patient text is memory-only.
Navigation cancels outstanding assessment jobs; a canceled job cannot publish a late result.
SpeechRecognizer is activity-owned, main-thread controlled and destroyed on stop/destroy; permission is requested on user action.

## Demo semantics
Scenario selection is an explicit synthetic fixture, not a prediction based on text.
The same demo generator runs on-device and behind the HTTP backend.
Insufficient text and emergency/LOW contradictions are suppressed. Errors do not fall back to a fabricated demo assessment in backend mode.

## Threat boundary
The development backend binds 127.0.0.1 only. Live mode requires a distinct access token; the OpenAI key stays in its process environment.
Only debug permits loopback cleartext. Release requires HTTPS and blocks screenshots.
No public deployment or claims of production/clinical readiness are made.
A real deployment requires identity, authorization, TLS, audit design without patient-data leakage, stronger abuse controls and clinical oversight.

## Known research limitations
Emergency phrase matching is a limited, unvalidated tripwire, not comprehensive NLP. Negation may over-trigger and paraphrases may be missed.
An explicit immediate-danger checkbox and persistent general warning do not depend on keyword coverage.
A validated clinical protocol, clinical dataset evaluation, translation evaluation and professional review remain necessary.
Speech support depends on installed recognition services and language models. Provider/offline behavior varies.
