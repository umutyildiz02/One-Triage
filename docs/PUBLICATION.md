# Private source snapshot

This repository is a clean snapshot of local source commit
`87677a5654c7f636792924ca66c29d15f908587a`.
It starts a separate `main` history. Original branches, tags, reflogs, and Git objects
are not published or rewritten. The original personal installation is untouched.

## Included

Application, core, backend, native sources, tests, Gradle wrapper and build files,
approved app graphics and fonts, reusable scripts, technical notes, and license notices.
Whisper.cpp and SentencePiece are pinned submodules using official HTTPS URLs.

## Excluded

Private configuration, API keys, access tokens, signing material, usage/download ledgers,
models, personal APK/AAB files, generated assets, build/IDE caches, private recordings,
screenshots, device logs, local delivery reports, and credential-inspection tooling.
Local launch shortcuts are omitted; documented Gradle commands are portable.
The optional deploy/ directory was blocked by the IDE's AI access policy and could not
be reviewed. It is intentionally excluded rather than published without inspection.
Android, core, backend source, and Gradle builds do not depend on that directory.
Model and audio-fixture preparation remains explicit and external to Git.

Selected technical notes retain historical results and known failures for context.
Their references to private local evidence are not new verification claims.
The private evidence is not part of this source backup.

## Publication-only adjustments

- Added comprehensive ignore rules, this note, a short README, and build instructions.
- Replaced private workstation paths in copied scripts with parameters or environment settings.
- Removed private path prefixes from copied technical notes.
- Added a deliberately invalid credential example.
- Retained runtime behavior, strict validation, consent, 100-attempt/USD guards,
  and the owner's fail-closed private device ledger requirement.
- Kept third-party notices. No model weights were copied or relicensed.

## Verification scope

The original tracked snapshot and all reachable/reflog file objects were scanned
for credential patterns and the exact locally configured OpenAI key without printing values.
An independent Gitleaks scan was also run with full redaction.
Synthetic credential fixtures remain in tests; they are not usable account credentials.
Final staged content is separately checked before commit and push.

These checks reduce exposure risk; they are not a mathematical guarantee that arbitrary
future changes contain no secrets. Review future diffs and never upload personal APKs.

No paid OpenAI request, Android build, phone operation, or model download was performed
for this publication task. A keyless build path is documented, not newly APK-tested here.
