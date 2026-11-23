# Architecture

## Current implementation status
- Android application shell and Compose Material 3 interface are in place.
- Normal notes are stored in a local Room database through `NoteDao` and `NoteRepository`.
- The current UI supports note create/edit, local search, pin/unpin, trash, restore and permanent deletion.
- Folder creation/filtering, moving notes between folders, folder rename/delete operations and comma-separated note tags are implemented through Room-backed repositories.
- The note-card UI can reassign an existing note to any folder or remove its folder assignment.
- Room schema export is configured under `app/schemas` for migration review.
- Database v1-to-v2 migration creates the folders table without destructive migration.
- Repository unit tests cover title normalization, update identity/pin preservation, trash lifecycle and folder lifecycle.
- Rich text, attachments, reminders, reliable autosave, vault isolation and cryptographic storage remain planned work, not implemented features.

## Target boundaries
- **app**: application entry point, navigation host and composition root.
- **core:model**: immutable domain models and identifiers.
- **core:database**: Room entities, DAOs, migrations and database configuration.
- **core:security**: credential verification, Keystore access and biometric orchestration.
- **core:crypto**: authenticated encryption and key hierarchy.
- **data:repository**: repository implementations and transaction boundaries.
- **feature**: UI and presentation logic for notes, editor, folders, search, vaults and settings.
- **backup:local**: future local encrypted export/import; cloud sync is outside the current scope.

## Data isolation
Normal notes, each private vault, and the decoy dataset must have separate repository/query boundaries. Private data must not be exposed through normal search, exports, notifications, widgets, recents or share surfaces.

## Persistence
Room is the source of truth for local notes. UI state is derived from repository Flows and should survive process death. Schema changes must use explicit migrations; destructive migration is not enabled. DataStore is reserved for non-secret preferences; secrets and raw keys must never be stored there.

## Background work
WorkManager may be used for local maintenance and reminders. Cloud sync workers are explicitly out of scope.