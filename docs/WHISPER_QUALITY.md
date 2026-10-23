# Whisper quality and diagnostic limits

These notes summarize earlier synthetic and licensed public waveform experiments.
They are not new results from the publication pass. Private logs and device
artifacts are excluded, so the observations are not independently reproduced here.

## Retained observations

- Small multilingual recognition passed some English, Turkish, and French
  manual/automatic fixtures. Arabic produced text and detected its language but
  recorded word error rate 0.40 against the unchanged 0.35 diagnostic threshold.
- A less-quantized Small q8 candidate did not resolve the quality problem:
  Arabic remained 0.40 and French regressed to approximately 0.42. It is not a
  configured production-model upgrade.
- Medium passed the bounded Turkish/French/Arabic manual/automatic fixture
  threshold in a later run. This does not establish accuracy across accents,
  noise, vocabulary, or devices.
- Experimental native speech translation lost a second negative clause across
  model comparisons. Medium beam search also caused low-memory process exits.
  The production optional translation path uses separate NLLB or ML Kit text
  translation; its own failures remain in [Local translation](LOCAL_TRANSLATION.md).

Small is selectable and lighter; Medium is the current unset preference.
Neither is clinically validated. Failed fixtures remain failures and must not
be hidden by alternate wording or reduced thresholds.

## Reproduction boundaries

[Licensed fixture definitions](FLEURS_FIXTURES.md) describe source identifiers,
transformation, and checksums. Optional preparation downloads are explicit and
outside this documentation task. Test model selections are verified rather than
arbitrary file paths.

Word error rate and keyword sentinels are limited diagnostics. A passing sentinel
can miss a lost negative clause or altered meaning. A failing keyword can also
miss a valid paraphrase; inspect the full licensed/synthetic output when evaluating
quality without logging patient text.

Waveform tests do not establish physical microphone capture or playback.
Successful x86_64 emulator inference is not physical ARM64 performance evidence.
Model bytes and pins are in [Whisper provenance](WHISPER_PROVENANCE.md).
