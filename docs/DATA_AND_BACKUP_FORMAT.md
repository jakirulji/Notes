# Data and Local Backup Format

## Scope
Google Drive backup and synchronization are excluded. This specification covers the future local encrypted export/import format and internal persistence boundaries.

## Design requirements
- Version every export envelope and database schema.
- Encrypt note bodies and attachment bytes before writing an export.
- Include authenticated metadata required to restore note relationships, folders, tags and attachment references.
- Never include plaintext vault content in normal exports.
- Keep primary and decoy vault exports explicitly separated and opt-in.
- Validate sizes, schema versions, authentication tags and attachment hashes before committing an import.
- Restore transactionally: invalid or incomplete imports must not partially overwrite existing data.
- Avoid duplicate attachment materialization and preserve stable logical identifiers.

## Recovery
A high-entropy recovery secret must be shown/exported only through an explicit user action. The application must explain that losing both the unlock credential and recovery material can make data unrecoverable. Recovery must be tested on a clean installation.
