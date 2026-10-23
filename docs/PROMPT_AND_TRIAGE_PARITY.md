# Assessment contract and retained comparison fixtures

One Triage requests a short possible assessment and an urgency category, not a
definitive diagnosis. The active [shared prompt](../backend/src/main/resources/triage-system.txt)
is used by the optional backend and copied into privately configured device assets.

An earlier [prompt-only comparison fixture](../backend/src/test/resources/legacy-vitalvoice-prompt.txt)
is retained for regression checks. It contains no credentials. Its owner-customized
reference-source provenance is recorded in [THIRD_PARTY.md](THIRD_PARTY.md).
Matching prompt intent does not establish identical model decisions or a validated
clinical algorithm.

## Current request and response

The direct repository sends reviewed original symptoms through HTTPS Responses
with structured output, independently selected result language, bounded time/output,
and explicit approval. Display translation never silently replaces the original.
The configured source uses gpt-4.1-mini with max_output_tokens 1800 and store=false;
these describe source configuration, not a guarantee of future provider availability,
pricing, or zero retention.

Validation checks completion, response fields, language, score, urgency, and
risk-color consistency. Authentication, budget, refusal, malformed output,
timeout, and safety conflicts produce explicit failure/uncertainty, never a
fabricated live result or automatic LOW fallback. Input changes invalidate stale
approval/results. No uncontrolled paid retry is authorized by a failed response.

## Categories and display

| Category | Contract risk color |
|---|---|
| LOW | #176B47 |
| MEDIUM | #8A5700 |
| HIGH | #B34700 |
| CRITICAL | #B42335 |
| UNDETERMINED | #526277 |

The UI maps validated categories to presentation colors. The 0–100 score is an
experimental model field, not calibrated probability or a recovered clinical
formula. Fixture scores only belong to explicitly selected development scenarios.

## Limits and regression intent

Local emergency-word checks have limited language coverage. The absence of a match is never
an all-clear, and unverified languages remain distinguishable. Explicit danger
input and general safety messaging do not depend on a keyword match.

Prompt and mock HTTP tests exercise original-text handling, negation, language
selection, parsing, errors, and uncertainty. They do not validate clinical
decisions or compare actual models' accuracy. This PR changes documentation and
notices only; prompts, classification rules, budget handling, and application code
remain unchanged. No paid request or test rerun was performed.
