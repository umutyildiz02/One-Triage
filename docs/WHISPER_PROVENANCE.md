# Offline Whisper provenance

Inspected 2026-10-20. Reference projects remain unchanged.

Current emulator finalization also uses the publisher-pinned Medium model documented in WHISPER_QUALITY.md. The original Small audit below is retained as provenance history, not the current four-language quality summary. Native x86_64 uses up to4 measured threads; ARM64 stays at2.

app/src/main/cpp/WhisperAbort.cmake now generates a derived whisper-vt-abort.cpp only in the build directory. The pinned upstream submodule itself remains unchanged. Four exact single-occurrence anchors and the target-source replacement are checked; a mismatched upstream layout fails configuration. A thread-local RAII full-call scope forwards the existing cancellation/deadline callback to CPU scheduler graph backends, including automatic-language encoding, and clears backend callback pointers after synchronous computation. No weights, decoder policy, language detection result or accuracy threshold is changed. Upstream MIT notices remain in the generated source and packaged license bundle. Verification status and measured before/after cancellation are in EMULATOR_PERFORMANCE.md; a successful compilation alone is not proof of runtime cancellation.

- Local model: Eren_Project/app/src/main/assets/ggml-small-q5_1.gguf, 190085487 bytes.
- Actual header: GGML magic 0x67676d6c, vocabulary 51865; the original .gguf extension is misleading.
- SHA-256: ae85e4a935d7a567bd102fe55afc16bb595bdb618e11b2fc7591bc08120411bb.
- Matches [publisher SHA-256](https://huggingface.co/ggerganov/whisper.cpp/blob/c521a4b02f422512d734391fdf08bb08c0862f68/ggml-small-q5_1.bin). This establishes byte equivalence to that published file, not an independent audit of training data or clinical accuracy.
- Publisher identifies converted OpenAI Whisper weights and MIT license: https://huggingface.co/ggerganov/whisper.cpp ; original license https://github.com/openai/whisper/blob/main/LICENSE.
- No new model download. Prepare-WhisperModel.ps1 validates length/header/published hash, copies binary into ignored APK assets, and refuses an unexpected existing destination. Model weights are never committed.
- Runtime prepares a private no-backup copy on a worker, checks SHA-256, uses atomic replacement and no network download.
- Eren JNI flow is adapted (PCM16 → float → whisper_full → text/language); hardcoded Turkish, detect-only early return, manufactured confidence and content/progress logging are not retained.
- Eren's source claims 1.8.0 but is not a Git checkout and core file hash differs from official release. For reproducible Android ABI builds, VitalTriage uses upstream MIT whisper.cpp v1.8.0 as a pinned submodule at 41fc9dea6a4fe056424be86f61164413903fcff4. Upstream license notices retained in submodule. CMake uses the upstream CPU target instead of Eren's broad ARM-oriented source glob.
- Both x86_64 and arm64-v8a built successfully (shell-190). ARM64 physical-device execution remains unverified. x86_64 runtime uses upstream feature-checked dynamic CPU backends; this emulator selected AVX2/FMA support, with portable fallback variants included. Native libraries are extracted by Android for that loader.
- Actual waveform tests passed English, Turkish and French manual/automatic recognition. Arabic produced text and detected ar but failed the unchanged word-error-rate threshold (0.40 > 0.35); it is not a passing accuracy result. Detailed timings and reports: docs/ui-comparison/whisper-native/dispatch-*.json. No microphone or clinical accuracy claim follows from these small fixtures.
- Packaged WHISPER_LICENSES.txt preserves MIT notices for whisper.cpp and OpenAI weights. Native submodule remains unchanged.
- Initial resources: ~83.7 GB free disk, ~3.3 GB free physical RAM / ~33.1 GB total. Native inference uses two CPU threads; compilation constrained to avoid resource exhaustion.
- OpenAI pricing rechecked on https://developers.openai.com/api/docs/models/gpt-4.1-mini: $0.40 input / $1.60 output per million standard text tokens. Current authorization is 23 total attempts and a conservative $3 stop including reservation, within the $5 authorized maximum. Historical ledger is preserved.
