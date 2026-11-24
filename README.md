# Notes

An offline-first Android notes app with a calm, premium interface and privacy-focused vault features.

**Current status:** M1 is in progress. The app now stores normal notes in a local Room database and includes create/edit, search, pin/unpin, trash, restore and permanent deletion. Folder creation/filtering, moving notes between folders, comma-separated note tags, and a 700 ms debounced editor autosave are now available. Draft IDs remain stable across repeated autosaves, preventing duplicate notes. Room schema export is configured for checked-in migration snapshots. Rich text, attachments, reminders, hidden vault, decoy vault, encryption and encrypted backup are not yet complete.

## Development

- Active branch: `dev`
- Stable branch: `main` (kept separate; do not merge without an explicit release decision)
- Android: Kotlin, Jetpack Compose, Material 3, Room
- Minimum Android version: API 26
- JDK 17 and Gradle 8.9

```bash
gradle testDebugUnitTest
gradle assembleDebug
```

## Scope

Google Drive Sync is excluded. The current product is offline-first; future backup work is limited to local encrypted export/import unless the scope is explicitly changed.

## License

Apache-2.0.
