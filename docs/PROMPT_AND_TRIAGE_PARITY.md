# Prompt and assessment parity

The original medical prompt was found in the owner's customized RTranslator source. It was not identical to the earlier target prompt. Its short-assessment intent and four urgency categories were carried over. Structured JSON, independent result language, explicit approval, and safe uncertainty are intentional differences. Neither identical clinical decisions nor word-for-word model outputs are claimed.

## Proven source and active call chain

Root: `<local-reference-root>/RTranslator-2.00/app/src/main/java/nie/translator/rtranslator`.

- `tools/OpenAiClient.java:54–66`: system prompt. Line 51 selects gpt-5.1; line 30 uses `/v1/chat/completions`; lines 72–80 build system and user messages. The symptom parameter is placed directly in user content. No temperature/top_p/max_tokens override appears in that request block.
- `WalkieTalkieFragment.onMessage:781–875`: final GuiMessage → getMessage().getText() → trim/empty check → askChatGpt. It updates the waiting text, raw answer, and color. Automatic paid submission was deliberately not copied.
- `WalkieTalkieService` translation listeners at 423–493 create Message with translated text and separate original textToTranslate. `Message.getText:230` returns the translation; `getTextToTranslate:323` returns the original. This old chain therefore assesses translated text.
- `OpenAiClient.onResponse:100–149` reads choices[0].message.content without the new schema/score checks. Old sensitive request/response logging was not copied.
- `WalkieTalkieFragment.extractUrgency:637–656` expects the English Urgency: prefix and checks critical/high/medium/low in order. updateTriageAppearance maps the category to a color. Its black-background/black-text unknown-state defect was not copied.

The alternative `as` folder has the same prompt/request in its 149-line OpenAiClient. Android_ChatGPT-main is a generic completions example (gpt-3.5-turbo-instruct, max_tokens 256, temperature 0.7), not the medical prompt source. No other active medical prompt was found in the inspected Eren/speechtranslator flows. These reference folders had no usable Git history; no unobserved historical version is claimed.

## Exact text and adaptations

- [Safe exact original prompt](../backend/src/test/resources/legacy-vitalvoice-prompt.txt): prompt only; no credential-bearing source header.
- [Shared current prompt](../backend/src/main/resources/triage-system.txt): also copied exactly into the personal device assets during build.
- Historical word-by-word comparison (local evidence, excluded from this source backup): authentic comparison at the time of the legacy port, not a claim that later language-screening additions did not occur.

| Original instruction | Current behavior |
|---|---|
| You are an AI that performs a triage-style risk assessment. | Preserved opening intent. |
| EXACTLY two lines / no additional text | Network output is strict JSON. Main UI keeps short assessment and urgency; extra fields are in details. |
| Possible diagnosis: short diagnosis | Short possible assessment, not a definitive diagnosis. |
| Low Urgency / Medium Urgency / High Urgency / Critical | Explicit LOW/MEDIUM/HIGH/CRITICAL mapping. |
| Do NOT add disclaimers | Not adopted as an instruction to remove safety. Essential limitations are concise in information/onboarding; emergency behavior remains. |
| Do NOT add explanations | Main result remains compact; requested rationale and follow-up questions live in secondary details. |
| Keep diagnosis very short, 1–3 words | Short assessment where the chosen language permits it. |
| Urgency must be one of the four categories | Four categories retained; UNDETERMINED is a safe extra state when assessment cannot be made reliably. |
| Same language as user message | The independently selected response language takes precedence. Translation display does not silently choose the assessment language. |

The original does not specify numerical clinical thresholds, a probability model, or a calibrated scoring formula. There is no evidence of an identical clinical algorithm across model versions.

## End-to-end flow comparison

| Stage | Original | Current personal device |
|---|---|---|
| Input | Recorder → ONNX Recognizer | Typed original or local Whisper/SpeechController output |
| Translation | NLLB; translated Message.text | Actual optional NLLB/ML Kit display translation; original retained separately |
| Approval | Final callback submits automatically | Review/edit → explicit approval; edits invalidate stale approval/result |
| Request | Direct Chat Completions, system+user | Direct HTTPS Responses, shared instructions + structured input; optional backend remains development infrastructure |
| Submitted text | Trimmed translated getText | Reviewed original symptoms, never silently replaced by display translation |
| Model | gpt-5.1 in old source | Existing budgeted gpt-4.1-mini; not represented as the same model |
| Limits | No explicit output/sampling limit in request block | max_output_tokens 1800, store=false, bounded timeout, no uncontrolled paid retry |
| Parsing | Free text and English label matching | Completed response/output_text checks, strict fields/category/language/score/color validation |
| Safety | Four colors; unknown styling defect | Safety.enforce and explicit errors/UNDETERMINED, no simulated fallback |
| Display | Two-line TextView intent, maxLines 4 | Compact assessment/urgency, full details accessible separately |

The model and pricing basis were checked in the earlier verification; this polish pass does not change either or assert future provider availability. See the recorded usage ledger for actual response model and token evidence.

## Score and colors

There is **no original 0–100 criticality formula**. The present score is an experimental model field, not a calibrated measurement or recovered original formula. Simulated scores belong only to explicitly isolated test fixtures.

| Category | Source-derived display color | Validated contract riskColor |
|---|---|---|
| LOW | #4CAF50 | #176B47 |
| MEDIUM | #FFC107 | #8A5700 |
| HIGH | #FF5722 | #B34700 |
| CRITICAL | #D50000 | #B42335 |
| UNDETERMINED | Not defined in original | #526277 |

The UI selects a palette from a validated enum, not arbitrary model color text. LegacyParityTest checks the four source colors.

## Safety and evidence limits

- Translated labels could break the original English Urgency parser. JSON enums now remain stable while human-readable fields use the selected result language.
- Missing data, wrong language/color/score, refusal, malformed output, and safety conflicts do not become LOW.
- Local emergency-word screening is not a clinical protocol. The expanded registry explicitly distinguishes unverified language coverage from a warning match or lack of a match. Lack of a local match never means all-clear.
- LegacyPromptParityTest and mock HTTP tests check instructions, original text, categories, languages, negation, uncertainty, and response handling. Mock decisions are fixtures, not evidence that two models make the same clinical decision.
- LiveGatewayTest and BoundedResponseTest retain malformed JSON, missing response, authentication, credit, model, budget, and timeout checks. Translation-quality failures remain recorded.
- Historical actual attempt 16 showed the short prompt and independent Turkish result language in the normal application. Its follow-up questions redundantly asked for already-provided information; that quality defect was retained in the report. Later actual German attempt 21 verified the direct personal path. See local verification and local handoff (excluded from this source backup) for dates and counts.

UI wording cleanup in the final polish does not modify classification instructions, model selection, schema validation, or Safety rules.
