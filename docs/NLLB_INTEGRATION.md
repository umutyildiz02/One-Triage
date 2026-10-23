# NLLB integration — in progress

This is an optional research translation pipeline. It is not a clinical equivalence claim or a production model clearance.

## Latest integrated checks — 2026-10-21

- Normal UI review found a complete second-sentence omission. Shell682 unchanged-engine diagnostic reproduced it in EN/TR/AR; FR passed. Both fixed input/output reports are retained in ui-comparison/product-release/nllb-sentences-before.json and nllb-sentences-after.json.
- Adapted the legacy Translator.java453–460 locale sentence boundary loop. Unlike its subsequent under200-token rejoining, short sentences now stay separate: that merge loses the negated second sentence with the pinned greedy model. Same tokenizer/models/ONNX sessions; decoder/cache reset per segment, original untouched. No original content logging or partial-success publication.
- Total input still4000characters/256tokens; at most16 locale sentences, each decoder at most256steps; cancellation/memory checks between/in segments. This does not claim sentence-boundary or clinical semantic perfection. Invalid/overlong input fails rather than truncating.
- Shell683 app unit tests (89, previously86 plus3 segmentation cases), debug/test builds passed1m53s. Shell684 normal install-r and settled-offline four-language real inference diagnostic PASS1/1 in15.248s, preferences unchanged, zero paid calls/downloads. EN→TR now includes “Ateş yok.”; TR/FR/AR→EN all retain fever negation. Warm inference before→after ms: EN829→864, TR645→832, FR904→1021, AR654→932. These short fixture timings exclude model load and are not general performance guarantees.
- Existing Turkish severity omission and raw-tokenizer apostrophe failures remain; translation stays defaultOFF.

- Shell631 passed real TR/FR/AR corpus, four-target review, cancellation/invalid input, memory checks and both Conversation/TTS cases. Its Turkish combined flow failed under3GB memory pressure; this failure is retained.
- Shell635 passed genuine English Whisper recognition followed by controlled translation-memory cancellation: the completed original remains editable, with no automatic restart or assessment.
- Shell639 passed the unchanged complete Turkish WAV→Whisper→NLLB→explicit consent→mock assessment offline on4GB/4vCPU in50.796s. Original61characters, translated74,0low-memory signals, sampledPSS1,602,610KB, minimumavailable1,189,008KB. Shell636 on3GB safely stopped NLLB but preserved the original. See EMULATOR_PERFORMANCE.md.
- Conversation opted-in automatic reading is now tied to real installed-TTS completion before the next queued translated turn; two actual NLLB/TTS cases passed626 and631. No recognition/share/assessment automatically follows peer playback.
- NllbInput replaces only a U+2019 apostrophe between letters with ASCII in the temporary tokenizer input. Original text is never modified. Real French no-fever translation and identical adapted token IDs passed631; unassigned vocabulary characters still fail closed. The raw exact-roundtrip diagnostic remains failing and is not presented as repaired vocabulary.
- The Turkish severity-omission diagnostic still fails. Automatic translation remainsOFF by default. No semantic correction, quality-threshold reduction, substitute transcript or paid API call was used.

## Verified source

Pinned upstream baseline: tag2.0.0 commit43cd934889302325d6071c9f35e69eaa5bd23df7 (GitHub git tree/ls-remote checked2026-10-21). Do not use default v3 or the later v2.00branch head. Local Tokenizer.java blob7da2d2099acf0575a33eb7c17b47f865e607a091 exactly matches this tag. Local Translator.java blobae6d92453c2765eefd49344de2a09a7989b7586e differs from upstream5a7cc218f71f1d0f2853676fd4a3a7352b2df1ef; the adapted local cache/greedy chain remains the technical source. WalkieTalkie and Recorder also have local changes, preserved. Evidence:shell493/495/496, no reference source edited.


Read-only RTranslator-2.00:
- `voice_translation/neural_networks/translation/Translator.java`: constructor loads encoder, decoder, cache initializer and shared embedding/LM-head; performTextTranslation tokenizes source and invokes executeCacheDecoderGreedy. The source explicitly says beam-search is outdated and crashes; it is not an eligible working implementation.
- `Tokenizer.java`: SentencePiece IDs are mapped to the fairseq vocabulary, source language prefixed, EOS appended; target language forced after the initial decoder step.
- `SentencePieceProcessorJava.java` and native interface: legacy native handle has no deletion and Load ignores error status. The adaptation adds deterministic close, checked load and UTF-8 byte arrays.
- Original translation logs source/output text. VitalTriage does not copy those logs or automatically send translated text to assessment.

Implementation: NllbEngine, NllbTokenizer, NllbModels, VerifiedModelDownload. ORT1.23.0; SentencePiece v0.2.0 commit17d7580d6407802f85855d2cc9190634e2c95624. x86_64 executes on the emulator; arm64-v8a is compiled, not physically executed.

2026-10-21: typed review supports independent EN/TR/FR/AR translation targets and keeps original-only assessment behind explicit consent. Shell585 passed the corrected four-target oracle, including valid Arabic لستُ with an affirmative negative-control. Shell597 repeated this with Wi-Fi and mobile data disabled and Active default network:none: EN→TR26.730s,TR→FR10.503s,FR→AR11.806s,AR→EN10.708s, all fixed no-fever sentinels passed. No paid API call; assessment was explicitly approved mock input and used the untouched original.

Shell597 also passed the real TR/FR/AR→EN throat/fever/negation/two-day corpus and French public legal double-negative example offline. NLLB load5.065s; sampled process PSS1,455,376KB after load and1,435,829–1,444,801KB after translation (not true peak). The two unchanged diagnostic failures persist: Turkish severity omitted and raw curly-apostrophe tokenizer roundtrip. Automatic translation therefore remainsOFF.

ConversationTranslationTest passed real ordered NLLB translation with peer-message boundaries; its transport input is simulated, separate from bonded two-AVD BLE evidence505/506. Shell585 also passed actual Turkish synthetic waveform→Whisper→NLLB→editable original/translation→explicit mock assessment95.265s. Neither result proves physical microphone/radio operation or clinical equivalence.

Clean normal owner-signed release on small_phone API36.1 downloaded and verified all NLLB assets over HTTPS (563/564), without host-side model injection. This verifies delivery/readiness, not normal-release inference or clinical translation quality.

## Model provenance and license

Four NLLB ONNX assets total995,677,428 bytes, from the original project's HTTPS GitHub release2.0.0. The release API provides asset lengths but no digest. Local SHA256 values pin subsequent installation integrity; they are not independent publisher signatures or proof of training provenance. The vocabulary4852054bytes was reused read-only and its SHA256 matches Meta's Hugging Face LFS object14bb8dfb35c0ffdea7bc01e56cea38b9e3d5efcdcb9c251d6b40538e1aab555a.

- Original assets: https://github.com/niedev/RTranslator/releases/tag/2.0.0
- Model card/license/use limits: https://huggingface.co/facebook/nllb-200-distilled-600M
- SentencePiece: https://github.com/google/sentencepiece/tree/v0.2.0
- ORT Android build/keep rules: https://onnxruntime.ai/docs/build/android.html

The NLLB model card uses CC-BY-NC-4.0, describes research rather than production release, and excludes medical/legal domain intent. This is a concrete production-model suitability/license review blocker. Apache/MIT code licenses do not override the weights' terms. Keep optional translation off by default and do not distribute it as clinically reliable medical translation.

## Safety and resource changes

- Original reviewed text remains the only assessment input.
- No fallback fabricated translation, no automatic OpenAI request.
- Bounded256 total input tokens/4000characters, at most16 locale sentences and256 decoder steps per sentence; overlong input fails explicitly instead of silent truncation. No partial result is published if any sentence fails.
- Per-session resources close on failure/success; ONNX RunOptions cancellation stops active inference.
- No global application/reference-project dependencies and no content logging.
- Model preparation is explicit; HTTPS, expected length, SHA256, partial resume, disk headroom and atomic installation.
- No download during ordinary translation. No fake progress.

## Actual emulator evidence (2026-10-20)

Shell441/446 executed the original ONNX models on medium_phone x86_64. Model load5701ms; first TR→EN4254ms, FR→EN1163ms and AR→EN794ms. The small throat/fever/negation/two-day corpus and French double-negative example passed. This is real inference, not a fixture response. ARM64 is compiled only.

Shell446:6 diagnostics,4 passed/2 failed. NllbFlowTest passed actual Turkish TTS waveform→Whisper Medium→NLLB→editable original/translation→explicit consent→mock assessment in63500ms. The mock assessment was free; neither physical microphone nor new OpenAI use is implied.

The expanded fixed eight-case corpus found a genuine severity omission: Turkish `Sol kolum iki saattir çok ağrıyor.` became `My left arm has been in pain for two hours.` The severity assertion remains failing. The EN→TR case produced a correct Turkish sentence but the test incorrectly treated dotted İ with English casing; the oracle now uses the target locale without changing its semantic assertions. Rerun pending.

The tokenizer round-trip test still exposes unsupported U+2019 curly apostrophe in this model vocabulary. Decode now preserves the unknown representation instead of silently dropping it. Production translation rejects unknown source/output token3 explicitly; original user text remains intact. The diagnostic is retained, not converted into a fake success.

Reports: ignored .runtime/nllb-quality.json, nllb-synthetic-safety.json and nllb-flow.json contain only fixed synthetic test cases/timing. No patient audio/text is logged by the production pipeline. Optional translation stays OFF by default because quality is not uniformly reliable.
