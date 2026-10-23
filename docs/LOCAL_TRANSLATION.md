# Optional local translation

Translation is off by default. Select NLLB or Google ML Kit, choose the independent
target language, explicitly prepare the necessary models, and then enable the
feature. Equal source/target pairs skip inference. The main screen displays the
completed target-language turn while retaining the editable original.

Only reviewed original text is eligible for assessment after explicit approval.
Missing models, unsupported pairs, cancellation, or failed translation do not
discard it or fabricate a replacement translation. Optional reading uses an
installed offline TTS voice. A user-started capture loop waits for playback;
received peer playback never sends or assesses a message automatically.

## Providers

NLLB uses local ONNX Runtime and SentencePiece with a fixed verified model set.
[NLLB integration](NLLB_INTEGRATION.md) describes bounds and resource behavior.
Its noncommercial model license and research/medical-domain limitations are
separate from the application code.

ML Kit uses its runtime language catalog intersected with the application's
registry. Unsupported pairs need another supported selection. Explicit preparation
uses the selected input and target languages, approximately 30 MB each, over Wi-Fi.
The SDK manages downloads; they may continue after the screen closes. Ordinary
translation checks readiness without silently downloading. Failed preparation
requires an explicit retry and reuses completed models.

Google's terms, data handling, and translation attribution apply; the SDK should
not be described as having zero network activity. See [provider terms and source
provenance](THIRD_PARTY.md). The Google attribution graphic is retained.

## Known quality limitations

Earlier synthetic/licensed diagnostic examples recorded:

- NLLB Turkish severity omission.
- Japanese fever translated with heat-related meaning.
- Korean unsupported vocabulary and throat/fever meaning failures.
- German-to-Turkish introduction of female gender and a change from ongoing
  symptoms to past tense.
- A raw curly-apostrophe tokenizer round-trip failure; the production adapter
  normalizes an apostrophe between letters only in its temporary input.
- ML Kit throat, negation, and severity failures in Turkish, French, or Arabic
  examples.
- Native Whisper translation loss of a negative clause; that experimental path
  is not the production text-translation provider.

Keeping locale sentences separate repaired some earlier second-sentence omissions,
but does not establish semantic accuracy. Successful pipeline tests only establish
the exercised wiring and original preservation. They are not medical validation,
and failed meaning checks must not be weakened into passes.

Historical reports are not bundled or newly reproduced here. Review original
wording, negation, timing, severity, and identity before any decision. Disable
translation when unreliable. See [Language support](LANGUAGE_SUPPORT.md) and
[Publication review](PUBLICATION.md).
