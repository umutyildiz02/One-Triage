# Whisper quality experiments — 2026-10-20

These are bounded, local waveform experiments. No patient recording or paid API request is used. A successful short fixture is not general speech or clinical validation.

## Current emulator finalization

The historical comparison below used2threads/2GB. Current code checkpoint ef1b3d5 preserves the same models, decoder and WER threshold, uses up to4 x86_64 threads and adds a target-only native abort adapter. The safe launcher requests3GB for the same medium_phone data image. Controlled English quality/performance and cancellation evidence is in EMULATOR_PERFORMANCE.md. Current full four-language rerun results are listed in VERIFICATION.md; historical failures below remain failures, not erased by later passes.

## Historical production baseline

The existing Eren multilingual small-q5_1 GGML model is 190,085,487 bytes. Its size and SHA-256 match the publisher artifact; see WHISPER_PROVENANCE.md. The production recognizer uses greedy decoding, two CPU threads and runtime-checked x86_64 CPU dispatch. Both x86_64 and arm64-v8a compile. Physical ARM64 execution is pending.

English synthetic speech and Turkish/French licensed FLEURS recordings passed manual-language and auto-detection tests. Arabic produced Arabic text and detected ar but had word error rate 0.40, above the unchanged 0.35 fixture threshold. Beam search with width5 did not fix it. The error is not just punctuation or diacritics.

The first French→English test checked fiscal/customs keywords and passed. Inspection of the actual public output showed loss of the second negative clause. That keyword check was insufficient; an additional negative-coordination assertion now guards this fixture. The original failure and outputs are retained. Optional English translation must not be presented as validated medical translation; the original reviewed transcript remains the sole backend input.

## Less-quantized candidate

- Publisher: ggerganov/whisper.cpp, MIT model distribution.
- File: ggml-small-q8_0.bin; 264,464,607 bytes.
- Pinned revision: 0b364b566045a405be7225ee1e415a073e04da77.
- SHA-256: 49c8fb02b65e6049d5fa6c04f81f53b867b5ec9540406812c643f177317f779f.
- Published LFS metadata: https://huggingface.co/ggerganov/whisper.cpp/commit/0b364b566045a405be7225ee1e415a073e04da77
- Local helper: scripts/Prepare-WhisperQualityModel.mjs. It checks resources, records a download reservation before fetching, bounds bytes, verifies pinned publisher size/hash, and does not retry automatically.
- Verified download is ignored under .runtime/quality-models. Aggregate new-model download reservation/history lives in .runtime/model-downloads.json and must not be reset. At that first candidate stage the total was264,464,607 bytes of the owner's5GB authorization; later Medium/SDK reservations are listed below and in the persistent ledger. Reusing the pre-existing Eren model is not a new download.

Publisher checksum agreement establishes artifact identity, not an independent audit of its training data or safety. Test-only model selection verifies the candidate again on device; it never accepts arbitrary paths and does not change the production model. The exact same pinned FLEURS audio and accuracy threshold are used for comparison. Shell-220: Turkish passed (WER0.08696 manual/auto); Arabic remained0.40 and French regressed to0.41667. French translation still lost negative coordination. Three of four comparison cases failed; q8 is not a production upgrade.

## Larger candidate

A second bounded candidate is ggml-medium-q5_0.bin,539,212,467 bytes, publisher revision f281eb45af861ab5e5297d23694b7d46e090c02c and SHA-25619fea4b380c3a618ec4723c3eef2eb785ffba0d0538cf43f8f235e7b3b34220f. Published metadata: https://huggingface.co/ggerganov/whisper.cpp/commit/f281eb45af861ab5e5297d23694b7d46e090c02c . Same MIT distribution and provenance limits apply. Resource check:~77.6GB free disk/~3.48GB free host RAM. Download verified in shell-221. Aggregate reserved/downloaded model bytes803,677,074; no counter reset. Shell-223 passed all three actual manual+auto recognition cases: Turkish WER0.0 (61.5/75.5s), Arabic0.20 (60.3/77.4s), French0.25 (49.7/80.8s). The threshold stayed0.35. Medium is now a selectable bundled option, Small remains the default; both model assets are ignored by Git. English Medium manual/auto verification passed separately in shell-245: load7161ms, manual52695ms, auto78528ms, expected words preserved and en detected. No paid API request was used for these motor diagnostics.

Medium beam5 translation died twice with confirmed LOW_MEMORY; greedy completed but lost the negative-clause meaning. This native translation diagnostic remains failing. A separate ML Kit translation pipeline now passes the French audio-to-bilingual-review wiring test, but the translated public sentence still contains double negation, and a Turkish synthetic fixture loses throat meaning. Wiring success is not translation-accuracy success. Original text is the sole backend payload; optional translation stays default-off and review-labeled. See LOCAL_TRANSLATION.md.

After comparison, only the two task-created external device copies of q8/Medium were deleted following exact hash verification to reclaim803MB. Verified host artifacts, app-private prepared models, evidence and user preferences were preserved. The test helper uses the bundled verified Medium asset if its comparison copy is absent.
