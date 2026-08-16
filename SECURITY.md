# Security Policy

## Supported Versions

The project is in pre-MVP development. Security fixes apply to the active `main` branch until release branches exist.

## Reporting a Vulnerability

Do not open a public issue for vulnerabilities involving authentication, authorization, data isolation, Firestore rules, account deletion, synchronization data loss, or exposed secrets.

Report privately to the repository owner.

Include:

- Affected area.
- Reproduction steps.
- Expected impact.
- Any logs, screenshots, or proof of concept that can be shared safely.

## Security-Sensitive Areas

- Firebase Authentication.
- Firestore security rules.
- User data isolation under `users/{uid}`.
- Account deletion.
- Offline synchronization and tombstone handling.
- Analytics event payloads and user properties.
- Secrets, tokens, signing credentials, and CI configuration.

Changes in these areas require explicit human review.
