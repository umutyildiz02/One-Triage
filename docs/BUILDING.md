# Build and credentials

## Reproducible source setup

Use Git with submodules, JDK 17, SDK 36, NDK 29.0.14033849, and CMake 3.22.1.
The Gradle wrapper and dependency versions are committed.
Run `git submodule update --init --recursive` after cloning.
Keep the pinned submodule commits; do not update them to an arbitrary latest version.

- Whisper.cpp: `41fc9dea6a4fe056424be86f61164413903fcff4`
- SentencePiece: `17d7580d6407802f85855d2cc9190634e2c95624`

Android Studio creates local SDK settings in ignored `local.properties`.
Do not copy the owner's machine paths or signing files.

## Safe development without a key

Run `gradlew.bat :app:assembleUiTest -PcompactModels=true`.
On POSIX systems use `./gradlew`.
This builds the separate `com.vitaltriage.app.uitest` development application.
The output is `app/build/outputs/apk/uiTest/app-uiTest.apk`.
Its development/mock controls are test infrastructure, not live medical results.

The `debug` variant supports the optional local backend.
Backend sources and unit tests are included, but no server is required for offline features.
For an explicitly configured live backend, supply your own `OPENAI_API_KEY` and
separate `VT_ACCESS_TOKEN` to the backend process environment. Do not put them in source.
See `backend/src/main/kotlin/com/vitaltriage/backend/Server.kt` for its configuration.
Keep the durable budget file; never delete it to retry requests.

## Owner-only direct device build

The unchanged `device` variant uses `com.vitaltriage.app`.
It requires private `.runtime/device-secrets.properties` and the owner's existing
`.runtime/live-budget.json`. Neither is published.
The example in `config/device-secrets.properties.example` is intentionally nonfunctional.

The original build refuses a missing or incomplete owner ledger.
Do not invent historical attempts or replace the existing ledger with an empty template.
Another developer can use the keyless variant or their own backend configuration;
this snapshot does not grant access to the owner's OpenAI account.

The device task creates credential-bearing files under `app/build/generated/personalDeviceAssets`.
They and all APK/AAB outputs are ignored. Never attach a personal device APK to a release.
Use your own credentials only in ignored local configuration.
Embedded client keys remain extractable even with obfuscation.

The source preserves the owner's signing configuration, but private signing material
is intentionally excluded. A clone's debug signing identity may differ.
Do not uninstall an existing app or erase its data to work around signature mismatch.

## Models and tests

Model preparation runs on the phone with explicit download approval and integrity checks.
Models and speech fixture downloads are not part of the Git repository.
Some instrumentation tests require separately prepared models and permitted speech fixtures.
They are not expected to pass on an empty device without preparation.

See [Publication review](PUBLICATION.md) for the scope and limits of source checks.
This documentation pass does not rebuild Android binaries or rerun model inference.
