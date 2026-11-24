# Notes

An offline-first Android notes app with a calm, premium interface and privacy-focused vault features.

**Current status:** M1 is in progress. The app now stores normal notes in a local Room database and includes create/edit, search, pin/unpin, trash, restore and permanent deletion. Folder creation/filtering, moving notes between folders, comma-separated note tags, and a 700 ms debounced editor autosave are now available. Draft IDs remain stable across repeated autosaves, preventing duplicate notes. Room schema export is configured for checked-in migration snapshots. The editor uses Compose Rich Editor for WYSIWYG formatting (bold, italic, ordered/unordered lists and inline code), with Markdown serialization for local persistence. Note colors and independent persistent checklists are available. Voice notes can be recorded with microphone permission and saved as app-private M4A attachments. Picked documents and recordings are stored privately and opened through a non-exported FileProvider; removing attachments or permanently deleting notes cleans up stored copies. Local reminders are scheduled through WorkManager. M1 implementation is in CI validation; a physical-device acceptance pass is still required. Hidden vault, decoy vault, encryption and encrypted backup are not yet complete.

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
