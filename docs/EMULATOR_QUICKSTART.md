# Development launcher scope

The optional launcher scripts reference this page for setup failures. They are
development utilities, not prerequisites for reading or contributing to One Triage.

Use the [keyless build instructions](BUILDING.md) first. Do not run a personal/live
launcher unless you intentionally configured its required local SDK tools,
backend distribution, credentials, and preserved usage ledger. These private
dependencies are deliberately absent from the clean clone.

A missing ledger must not be replaced with an empty file or fabricated history.
Do not uninstall the existing personal app, reset device data, or change its
package/signature to work around setup failures.

Emulator installation, microphone/radio tests, model downloads, and paid
assessment requests are separate opt-in development work. None was performed
for the documentation/publication review. See [PUBLICATION.md](PUBLICATION.md)
for its actual verification scope.
