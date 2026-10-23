# NLLB integration

NLLB is an optional research translation pipeline. It is off by default and has
separate noncommercial model terms and medical-domain suitability limitations.
See [model provenance, hashes, and licensing](THIRD_PARTY.md).

## Implementation

`NllbEngine`, `NllbTokenizer`, `NllbModels`, and `VerifiedModelDownload`
coordinate ONNX Runtime 1.23.0 and pinned SentencePiece v0.2.0. The model uses an
encoder, decoder, cache initializer, shared embedding/LM head, and tokenizer.
Source language is prefixed, EOS appended, and target language forced during
greedy decoding. Native handles have checked initialization and deterministic close.

Input is bounded to 4,000 characters / 256 tokens, at most 16 locale sentences,
and 256 decoder steps per sentence. Sentences remain separate to reduce observed
second-sentence omissions. Limits fail explicitly rather than silently truncating.
No partial successful translation is published if another sentence fails.

The temporary tokenizer input normalizes U+2019 only between letters; the original
remains unchanged. Unknown vocabulary is rejected. Cancellation and memory checks
apply during processing, and ONNX run options allow active inference cancellation.

## Preparation and resources

The configured model set is 1,000,529,482 bytes including its tokenizer.
Download approval is explicit. Expected lengths and SHA-256 hashes, storage
headroom, partial-download handling, and atomic installation protect preparation.
Ordinary translation does not fetch missing models.

Whisper followed by NLLB can place substantial pressure on limited-memory devices.
Earlier integration notes recorded a completed Turkish synthetic speech flow on
a 4 GB emulator and safe translation cancellation on a 3 GB configuration while
preserving the original. These are bounded observations, not minimum-memory
guarantees or physical-device measurements.

## Accuracy and evidence boundaries

Historical x86_64 emulator inference exercised multiple language directions,
original preservation, cancellation, and Conversation/TTS ordering. ARM64 build
support does not establish physical execution. None of these checks was rerun in
the publication pass, and private reports are excluded.

Known Turkish severity loss, Japanese/Korean translation failures, German
gender/tense changes, and raw tokenizer limitations remain documented in
[Local translation](LOCAL_TRANSLATION.md). Successful short no-fever fixtures do
not prove clinical fidelity. The original alone is submitted for an assessment,
and only after separate user approval.
