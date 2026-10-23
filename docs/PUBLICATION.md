# Publication preparation review

This review works only in the clean One Triage clone. The repository remains
**private**. It prepares a documentation pull request against main; it does not
publish the repository, merge changes, rewrite history, or grant a blanket license.

## Scope

The English README explains setup, models, languages, capture, translation,
Bluetooth, assessment, settings, privacy, safety, troubleshooting, and contributions.
Technical notes now describe the current source and explicitly bounded earlier
observations. Broken references to excluded private reports and personal usage
counts were removed. Known quality failures remain documented.

One Triage is the product identity. Derived-source authorship, license text,
model provenance, and functional upstream URLs remain where applicable.
[THIRD_PARTY.md](THIRD_PARTY.md) explains independent replacement requirements.

Changes are documentation and notice edits plus one build-error documentation
link. Application logic, identifiers, prompts, models, budget safeguards,
submodule revisions, original projects, personal credentials, installed apps,
and private APKs are outside the change.

## Excluded material

Ignore rules cover local credentials, signing files, usage/download ledgers,
models, APK/AAB outputs, build/IDE caches, private recordings, screenshots,
logs, and local evidence. The configuration example is deliberately invalid.
Ignoring a path alone does not remove files already committed.

Optional deployment templates are absent from the current tree. This clean
repository has its own existing history; removing a file from a later snapshot
does not erase earlier objects. Review history as well as the proposed tree.

## Verification scope

Publication checks inspect the clean clone's reachable/reflog Git blobs and
staged content using redacted credential-format, private-key, credential-URL,
and personal-path patterns. Exact known synthetic test keys are distinguished
from usable credentials. Tracked artifact names, relative Markdown links,
README length, whitespace, Git integrity, and submodule pins are also reviewed.

These are bounded source checks, not a guarantee that arbitrary secrets are absent.
No personal API key is opened, compared, rotated, or used. No provider credential
is tested against a paid service. A fresh Gitleaks/entropy scan is not claimed
when that tool is unavailable.

Submodules are pinned official dependencies; pin/working-tree checks are not
a full upstream security audit. External documentation and license pages are
checked selectively without downloading model assets. Private reports from
earlier development are not part of this review or evidence of fresh tests.

## Review results — 2026-10-23

- The source snapshot check covered 305 unique reachable/reflog and staged blobs.
  Two exact synthetic key fixtures were identified. Three credential-URL matches
  were manually reviewed in LivePreferencesTest, BackendConnectionPolicyTest, and
  VerifiedModelDownloadTest; all are rejection-test inputs using example domains,
  not account credentials. No real credential or personal-path finding remained.
- No tracked private APK, signing key, recording, runtime ledger, or model artifact
  matched the reviewed private-file patterns.
- The staged README has approximately 1,783 whitespace-delimited words.
  All 65 relative Markdown links checked resolved.
- Git integrity and whitespace checks passed; the two submodule pins remained
  unchanged.
- The packaged Apache, SentencePiece, ONNX, darts-clone, esaxx, Protocol Buffers,
  and font license sections were compared with their originals and are unchanged.
  The Whisper MIT body is unchanged; only project-specific prose was updated.
- Primary Apache, Creative Commons, NLLB, and Google ML Kit documentation pages
  were reachable. External model binary downloads were not attempted.

The pattern scanner initially returned a review status for the three dummy URLs;
manual classification resolved them without deleting security regression fixtures.
Gitleaks was not available on PATH, so these results must not be described as
a fresh independent Gitleaks or entropy scan. The local review script is an ignored
task artifact and is not part of the publication commit.

## Release decisions still open

- Select a license for original project material before calling the entire
  repository open source.
- Confirm rights/provenance for owner-supplied artwork and reference-derived
  wrappers or customized additions.
- Resolve NLLB's separate noncommercial license and production/medical-domain
  suitability before any production distribution.
- Review the resolved binary dependency inventory, complete notices, and
  packaging before distributing an APK.
- Independently validate clinical, language, physical-device, and deployment
  behavior for any intended real-world use.

No Android build, emulator test, phone operation, model download, or paid API call
was performed in this documentation pass. The build path is documented, not
newly executed. Documentation PR readiness does not imply public or medical
release readiness.
