# Whisper provenance

Whisper.cpp / ggml uses the official MIT-licensed source pinned at
`41fc9dea6a4fe056424be86f61164413903fcff4` in the
[Whisper.cpp submodule](../third_party/whisper.cpp).
OpenAI Whisper weights are optional separate downloads; no model is committed.

## Configured artifacts

| Model | File | Bytes | SHA-256 |
|---|---|---:|---|
| Small | ggml-small-q5_1.bin | 190085487 | `ae85e4a935d7a567bd102fe55afc16bb595bdb618e11b2fc7591bc08120411bb` |
| Medium | ggml-medium-q5_0.bin | 539212467 | `19fea4b380c3a618ec4723c3eef2eb785ffba0d0538cf43f8f235e7b3b34220f` |

Publisher records:
- [Small pinned metadata](https://huggingface.co/ggerganov/whisper.cpp/blob/c521a4b02f422512d734391fdf08bb08c0862f68/ggml-small-q5_1.bin)
- [Medium publisher revision](https://huggingface.co/ggerganov/whisper.cpp/commit/f281eb45af861ab5e5297d23694b7d46e090c02c)
- [OpenAI Whisper license](https://github.com/openai/whisper/blob/main/LICENSE)

Runtime URLs use the publisher's main branch with fixed byte/hash validation.
An upstream change must fail verification rather than silently updating weights.
The original Small reference had a misleading .gguf extension but a GGML header;
the configured .bin artifact and hash establish the expected content.

Preparation is explicit, reuses verified artifacts, and maintains a private
no-backup model copy. Publisher hash agreement is integrity evidence, not an
independent audit of training data, medical suitability, or language accuracy.

## Native adaptation

The JNI adapter converts PCM16 to floats and invokes recognition for text and
language. Its reference-source qualification is retained in
[THIRD_PARTY.md](THIRD_PARTY.md). The application uses official pinned native
source rather than an unversioned reference project's engine files.

`WhisperAbort.cmake` creates a derived native source only in the build directory,
checks exact source anchors, and connects the cancellation/deadline callback to
CPU graph backends. It preserves upstream license headers and fails configuration
on unexpected source layout. The submodule itself stays unchanged.

x86_64 uses up to four threads; ARM64 uses two. Dynamic CPU variants require
packaged native libraries to be extracted. Source/build support does not prove
physical ARM64 execution. See [quality](WHISPER_QUALITY.md) and
[memory](VOICE_MEMORY.md) limits.

[Packaged MIT notices](../app/src/commonAssets/WHISPER_LICENSES.txt) preserve ggml
and OpenAI authorship. This documentation review downloaded no weights and ran
no inference.
