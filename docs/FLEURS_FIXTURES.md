# Licensed speech diagnostics

Source: Google FLEURS, https://huggingface.co/datasets/google/fleurs
License: Creative Commons Attribution 4.0, https://creativecommons.org/licenses/by/4.0/
Attribution: FLEURS, Google and the dataset contributors, paper https://arxiv.org/abs/2205.12446.

Only three individual public, non-clinical recordings were downloaded. They are not patient recordings and are never sent to OpenAI. They test local speech recognition separately from microphone capture and medical assessment.

| Language | Configuration/split/row | Dataset ID | Audio duration | WAV bytes |
|---|---|---:|---:|---:|
| Turkish | tr_tr / validation / 0 | 1522 | 16.8 s | 1075258 |
| French | fr_fr / validation / 1 | 1570 | 6.0 s | 384058 |
| Arabic | ar_eg / test / 0 | 1993 | 8.16 s | 522298 |

Total download: 1981614 bytes. No model download. Original mono 16 kHz float WAV was decoded to PCM16; content was not edited. These fixtures cover one voice/utterance per language, not accents, clinical vocabulary, noisy speech or broad accuracy.

Preparation: run scripts/Prepare-FleursFixtures.mjs with the locally verified Node runtime (scripts/Prepare-LocalNode.ps1). The script retrieves metadata and one audio URL per selected row, bounds downloads to 2 MB each, validates WAV format, writes PCM and a manifest with source IDs, reference text, transformations and SHA-256. Generated fixtures are ignored under app/src/androidTest/assets/speech-fixtures and are packaged only in the isolated test APK, never the normal app. No account/API credential is used.

WhisperMultilingualTest compares actual engine output with the reference using word edit distance after punctuation/case normalization and Arabic diacritic removal. It requires the actual language and word error rate ≤0.35 for manual and automatic recognition. The threshold is a diagnostic assertion, not a clinical criterion. Failed assertions remain failures. Logs retain only counts, timing and error rate, not captured audio or user content.

Publisher WAV SHA-256 values computed after download (integrity records, not independent origin certification):
- tr: dd59d619f546fcfa8eaca0e1ed06ab98dcc978e28bc657577c814772ead16e67
- fr: a9f2f1630034aae5bbe5273a25166d9ed1e92bedf4d613c7398fbda92b65ed12
- ar: 40d979a6239fc08951f387c671fb5fc46fffdc5df823ed1dbdb809bc75e26620

English uses an independently generated offline synthetic TTS waveform. None of these tests proves physical microphone capture, TTS playback in the other languages, or clinical fidelity.
