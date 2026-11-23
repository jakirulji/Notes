# Architecture

## Current implementation status
The repository currently contains the initial Android/Compose application shell and a temporary in-memory notes interaction. This is a foundation, not a completed offline notes engine.

## Target boundaries
- **app**: application entry point, navigation host and composition root.
- **core:model**: immutable domain models and identifiers.
- **core:database**: Room entities, DAOs, migrations and database configuration.
- **core:security**: credential verification, Keystore access and biometric orchestration.
- **core:crypto**: authenticated encryption and key hierarchy.
- **data:repository**: repository implementations and transaction boundaries.
- **feature**: UI and presentation logic for notes, editor, folders, search, vaults and settings.
- **sync:contract**: future provider-neutral sync interfaces only; no Drive implementation in this scope.

## Data isolation
Normal notes, each private vault, and the decoy dataset must have separate repository/query boundaries. Private data must not be exposed through normal search, exports, notifications, widgets, recents or share surfaces.

## Persistence
Room is the planned source of truth. UI state must be derived from repositories and survive process death. DataStore is reserved for non-secret preferences; secrets and raw keys must never be stored there.

## Background work
WorkManager may be used for local maintenance and reminders. Cloud sync workers are explicitly out of scope.
