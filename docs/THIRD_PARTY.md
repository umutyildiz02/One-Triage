# Third-party components and independent replacement

This inventory separates One Triage's product identity from source provenance.
[NOTICE.md](../NOTICE.md) is the attribution summary; [LICENSE.md](../LICENSE.md)
sets out the mixed license scope. This is a source review, not a complete binary
distribution audit or a grant of additional rights.

## Application adaptations

RTranslator 2.0.0, Copyright 2016 Luca Martino, supplies Apache-2.0-derived UI,
settings, catalogs, TTS filtering, Bluetooth message framing, NLLB integration,
and recording-related material. Recording source also credits Google Inc. and
RTranslator contributors. The historical baseline is
[commit 43cd934889302325d6071c9f35e69eaa5bd23df7](https://github.com/niedev/RTranslator/tree/43cd934889302325d6071c9f35e69eaa5bd23df7).
Some adaptations came from an owner-customized copy; that copy is not represented
as byte-identical to upstream.

Preserve existing file headers and modification notices. Apache-2.0 section 4
requires the applicable license, notices, attribution, and modification marking
when redistributing covered work. Branding can be independent while truthful
source acknowledgments remain. See the [authoritative license](https://www.apache.org/licenses/LICENSE-2.0).

The PCM-to-Whisper JNI flow originated in the owner's Eren_Project reference.
The native engine itself is pinned official MIT Whisper.cpp. A local reference
project name alone does not establish a standalone license for every adaptation;
confirm provenance and permission for that wrapper and owner-customized additions
before unrestricted redistribution. Retaining the recorded origin avoids implying
that all wrapper code has been independently authored.

## Native dependencies and font assets

| Component | Pin / source | License record |
|---|---|---|
| Whisper.cpp / ggml | `41fc9dea6a4fe056424be86f61164413903fcff4` | [MIT notices](../release-delivery/licenses/Whisper-LICENSES.txt) |
| SentencePiece | `17d7580d6407802f85855d2cc9190634e2c95624` | [License](../release-delivery/licenses/SentencePiece-LICENSE.txt) |
| ONNX Runtime Android | `1.23.0` | [MIT](../release-delivery/licenses/ONNX-Runtime-MIT.txt) |
| Nunito Sans | Three imported font files | [SIL OFL 1.1](../release-delivery/licenses/Nunito-Sans-OFL.txt) |

SentencePiece's bundled dependencies have additional notices. The
[app notice bundle](../app/src/commonAssets/THIRD_PARTY_NOTICES.txt) includes
darts-clone, esaxx, and Protocol Buffers notices. Submodules retain their own
licenses, authorship, and nested notices. Do not replace these with this summary.

## Optional model provenance

Whisper Small and Medium are converted OpenAI Whisper weights distributed by
ggerganov/whisper.cpp under MIT terms. Sizes, SHA-256 values, and publisher
references are in [Whisper provenance](WHISPER_PROVENANCE.md).
Hash agreement identifies an artifact; it does not audit training data or accuracy.

NLLB-200 distilled 600M originates with Meta / Facebook AI.
Its [model card](https://huggingface.co/facebook/nllb-200-distilled-600M) and
[CC BY-NC 4.0 terms](https://creativecommons.org/licenses/by-nc/4.0/) are separate
from Apache/MIT software licenses. Noncommercial copyright conditions and the
model card's research/production/medical-domain limitations need separate review.

The four ONNX conversions are fetched from
[RTranslator release 2.0.0](https://github.com/niedev/RTranslator/releases/tag/2.0.0);
the tokenizer is fetched from Meta's model repository. They are not included here.
The existing integration records these fixed integrity values:

| File | Bytes | SHA-256 |
|---|---:|---|
| NLLB_cache_initializer.onnx | 25368443 | `4150b3cba7db9487a94e3b693f79d0f89772e75d80bffd0e60b06de1b574e3d9` |
| NLLB_decoder.onnx | 179109694 | `86028825495fc34085c7ebb80fb5f0eaa18ac6600f9d4dbc55bc731cc6981159` |
| NLLB_embed_and_lm_head.onnx | 524712277 | `2745a6f932237b8bb0c35ded54987ea3fc455b696215d48d32271c28c785dd50` |
| NLLB_encoder.onnx | 266487014 | `9b25d62f4875e5087ffde53c2794308c07f60f4c77670fc5bbfe308a1ef88cd9` |
| sentencepiece_bpe.model | 4852054 | `14bb8dfb35c0ffdea7bc01e56cea38b9e3d5efcdcb9c251d6b40538e1aab555a` |

The four ONNX files total 995,677,428 bytes; including the tokenizer,
1,000,529,482 bytes. The earlier integration recorded locally calculated ONNX
hashes, not independent publisher signatures. Tokenizer identity was matched
to the publisher's LFS metadata. This documentation pass did not download weights.

## Provider and diagnostic material

Google ML Kit translation is subject to
[ML Kit terms](https://developers.google.com/ml-kit/terms) and
[translation attribution requirements](https://developers.google.com/ml-kit/language/translation/translation-terms).
Its unchanged Google translation graphic is provider attribution, not product
branding. Do not describe the SDK as Apache/MIT merely because other dependencies are.
Review resolved dependencies and provider terms before distributing a binary.

Optional public speech fixtures are Google FLEURS, attributed to Google and
dataset contributors under CC BY 4.0. [Fixture provenance](FLEURS_FIXTURES.md)
records transformations and source identifiers. No fixture recordings are committed.

## If zero original-project references are required

Deleting names from notices while retaining derived material does not achieve
independent provenance. A separate replacement project would need to:

1. Inventory and independently replace the derived onboarding/settings/
   conversation layouts, visual resources, icons, unused imported logo,
   catalog definitions, TTS filters, and recording adaptations.
2. Replace derived Bluetooth framing/session adaptations and translation/tokenizer/
   sentence-segmentation implementations where covered material remains.
3. Replace or independently produce and verify the ONNX conversions, download
   sources, and associated provenance records; independently assess model terms.
4. Review owner-customized wrappers, copied prompts/fixtures, fonts, artwork,
   and other imported assets for separate rights and attribution obligations.
5. Update technical names only after data compatibility and behavior review;
   renaming a package, file, or preference does not remove derivation.
6. Keep notices for any material still included. Archived commits retain their
   historical source and notices; this task does not rewrite Git history.

Independent replacements and asset-rights review are outside this documentation
change. A literal zero-reference requirement across existing history is incompatible
with preserving that history. The present PR retains legally relevant source names
and functional upstream download URLs.

## Remaining release decisions

No blanket license has been chosen for original project material. Owner-supplied
OneVoice artwork and reference-derived wrappers need a documented rights basis.
The unused imported logo remains tracked and attributed. NLLB's noncommercial
terms and medical-domain suitability are unresolved for a production product.
A binary release also needs an inventory of resolved transitive dependencies,
their notices, and packaging checks. These are publication/distribution limits,
not reasons to erase attribution.
