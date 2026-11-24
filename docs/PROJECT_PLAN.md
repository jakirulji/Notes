# Notes — Project Plan

## Product vision
Build an offline-first Android notes app inspired by the interaction patterns of Xiaomi Mi Notes, with a premium interface and a genuinely private, encrypted hidden-vault system. The app is independently branded as **Notes** and does not use Xiaomi branding or proprietary assets.

## Product principles
- Android first; native Kotlin and Jetpack Compose.
- Offline-first: core note-taking must work without an account or network.
- Privacy by default: private-vault content must never leak into normal search, recents, widgets, notifications, sharing suggestions, or ordinary exports.
- Open source under Apache-2.0.
- No cloud account should be able to decrypt backups by itself.
- Never claim forensic-proof deniability for hidden or decoy vaults.

## Core features

### Normal notes
- Rich-text notes, checklists, folders, tags, pinning, colors, search, reminders, autosave and trash.
- Image, file and voice attachments.
- The editor uses a WYSIWYG Compose rich-text component; content is serialized as Markdown for local persistence. The available toolbar formatting is bold, italic, ordered/unordered lists and inline code; explicit heading controls are not currently provided. Note color selection, independent Room-backed checklist items (add, check/uncheck, remove), and in-app voice recording to app-private M4A attachments are implemented. M1 still requires successful CI and physical-device acceptance testing.
- Picked document attachments are copied into app-private storage, persisted by relative path, and exposed to other apps only through a non-exported FileProvider URI grant. Removing an attachment or permanently deleting its note removes the stored copy. Attachments remain unencrypted until the M4 encryption milestone; reinstalling the app or clearing app data removes these local copies.
- Reliable local persistence and migration between app versions.

### Hidden Vault
- A pull-down gesture from the top of the normal notes list reveals the vault entry only when the list is already at its top boundary.
- Distinct haptic feedback and transition; authentication is required before opening.
- Multiple independent vaults, each with isolated data and keys.
- Vault content excluded from all normal-app discovery and export surfaces.

### Decoy Vault
- A separate credential opens a separate decoy dataset.
- No shared note IDs, search index, attachment paths or encryption keys with the primary vault.
- Clearly document that device artifacts, backups, app usage and external observations may still reveal that private data exists; this is not a forensic-deniability guarantee.

### Security and recovery
- AES-256-GCM authenticated encryption for note and attachment content.
- Argon2id from a maintained, reviewed implementation for password-based key derivation; benchmark parameters on supported devices.
- Android Keystore protects/wraps key-encryption keys; use random per-vault data-encryption keys.
- Enforce nonce uniqueness; never log or persist raw keys in ordinary preferences.
- Biometric unlock through AndroidX Biometric, with a secure credential fallback.
- Encrypted export/import and a high-entropy recovery key.
- Losing both the unlock credentials and recovery material may make encrypted data unrecoverable.

### Cloud sync
- Google Drive backup and sync are explicitly out of scope for this release.
- No OAuth flow, Drive API client, cloud upload/download, sync worker or cloud conflict resolver will be added.
- Keep local export/import and provider-neutral boundaries separate so a future cloud provider can be evaluated without coupling the notes engine to it.

## Proposed architecture
- Language/UI: Kotlin, Jetpack Compose, Material 3.
- Architecture: MVVM with clean boundaries and repository interfaces.
- Persistence: Room/SQLite; DataStore only for non-secret preferences.
- Dependency injection: Hilt.
- Background work: WorkManager.
- Security: Android Keystore, AndroidX Biometric, maintained Argon2id implementation, AES-GCM.
- Cloud sync: excluded from current scope; local encrypted export/import only.
- Tests: JUnit, AndroidX, Compose UI and instrumentation tests.
- CI: GitHub Actions.

## Proposed modules
```
app/
core/common/
core/design/
core/database/
core/security/
core/crypto/
core/model/
feature/home/
feature/editor/
feature/folders/
feature/search/
feature/vault/
feature/decoy/
feature/authentication/
feature/backup/
feature/settings/
data/local/
data/repository/
data/migration/
sync/drive/
sync/workers/
sync/conflict/
```

## Milestones
- **M0 — Foundation:** Gradle/Kotlin/Compose setup, design system, navigation, dependency injection, CI, documentation.
- **M1 — Notes engine:** local CRUD, editor, folders, tags, search, attachments, autosave and trash.
- **M2 — Hidden Vault:** top-boundary pull-down gesture, authentication, isolated vault storage and leakage tests.
- **M3 — Decoy Vault:** separate credential and dataset, routing tests and limitations documentation.
- **M4 — Encryption and recovery:** encrypted database/content strategy, attachment encryption, key hierarchy, recovery and tamper tests.
- **M5 — Local backup:** encrypted export/import, integrity validation and transactional restore; no cloud sync.
- **M6 — Release hardening:** accessibility, performance, migration, security review, test coverage, release signing and documentation.

## Quality and security test plan
- Note CRUD, autosave, process death, database migration through v4, checklist CRUD and attachment integrity.
- Gesture threshold and scroll-boundary tests; ensure ordinary scrolling cannot accidentally expose the vault.
- Verify vault isolation across IDs, database queries, search, notifications, widgets, exports and logs.
- Verify decoy routing and independent key/data separation.
- Test ciphertext tampering, nonce uniqueness, wrong credentials and recovery on a clean installation.
- Verify local exports contain ciphertext only for protected datasets; test interrupted import, integrity failures and transactional restore.
- Do not release based only on a successful compile: require automated tests and a documented device test pass.

## Repository workflow
- `main`: stable, release-ready code.
- `dev`: integration branch.
- Feature branches merge into `dev` through reviewed pull requests.
- Promote `dev` to `main` only at an approved milestone/release.
- Avoid uploading routine CI artifacts; publish a test APK only at agreed checkpoints.

## Documentation to maintain
- `README.md`
- `LICENSE` (Apache-2.0)
- `SECURITY.md`
- `CONTRIBUTING.md`
- `docs/PROJECT_PLAN.md`
- `docs/ARCHITECTURE.md`
- `docs/SECURITY_MODEL.md`
- `docs/DATA_AND_BACKUP_FORMAT.md`
- `docs/ROADMAP.md`

## Decisions still required
- Final Android application ID/package name.
- Minimum supported Android API level.
- Final visual design tokens and icon set.
- Security review and threat-model sign-off before enabling production vault backup.
