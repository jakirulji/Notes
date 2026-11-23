# Security Model

## Status
This document describes required controls. It does not claim that the current scaffold already implements them.

## Assets
- Normal note content and attachments.
- Primary vault content and per-vault keys.
- Decoy dataset and its independent credential.
- Recovery material and encrypted exports.

## Required controls
- AES-256-GCM for authenticated content encryption.
- Random per-vault data-encryption keys and a reviewed key-wrapping hierarchy.
- Android Keystore protection for key-encryption keys.
- Argon2id using a maintained implementation with device-appropriate, benchmarked parameters.
- Unique nonces per key; authenticated metadata and versioned ciphertext envelopes.
- Biometric unlock through AndroidX Biometric with a secure credential fallback.
- No raw key or plaintext secret in logs, analytics, ordinary preferences or crash reports.

## Vault isolation
Vault identifiers, storage, attachments, indexes and keys must be isolated. The normal app must not query private datasets. Decoy vaults are a separate dataset, not a forensic-deniability feature.

## Limitations
A decoy vault cannot guarantee that private data existence is undetectable. Device artifacts, backups, app usage, memory inspection and external observations may reveal it. Losing both unlock credentials and recovery material may make encrypted data permanently unrecoverable.

## Release gate
Do not describe encryption, recovery or vault privacy as production-ready until automated tests, adversarial leakage checks and a documented device review pass.
