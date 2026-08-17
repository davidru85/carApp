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

- Firebase Authentication and the local owner adoption boundary.
- Firestore security rules, including per-field range validation.
- User data isolation under `users/{uid}`.
- Account deletion ordering.
- Offline synchronization and tombstone handling.
- Analytics event payloads and user properties.
- Logging redaction in release builds.
- Secrets, tokens, signing credentials, and CI configuration.

Changes in these areas require explicit human review (`AGENTS.md`).

## Secrets in the Repository

Allowed in the repository:

- `google-services.json` and `GoogleService-Info.plist`. These are client configuration, not secrets, **provided** the corresponding API keys are restricted in the Google Cloud console by package name, bundle id and signing certificate.

Never committed:

- Keystores and `*.jks`, `*.keystore`.
- Apple `*.p8`, `*.p12`, provisioning profiles.
- Service-account JSON of any kind.
- `local.properties`.
- Any token, password or private key.

Requirements:

- `.gitignore` MUST cover the entries above from the first commit.
- Secret scanning MUST be enabled on the repository.
- CI holds credentials only as repository secrets, and CI runs against the Firestore emulator, never against the production project.
- If a secret is committed, treat it as compromised: rotate it first, then rewrite history.

## Accepted Residual Risks

- **No Firebase App Check in the MVP** (`docs/SPECIFICATION.md §3.2`). Anyone holding a valid UID can write to their own subtree with a non-official client. This is mitigated, not removed, by per-field range validation in the Firestore rules and by client-side quarantine of unsupported documents. Revisit before any public launch beyond the MVP.
- **Last-write-wins conflict resolution** can lose one whole-document update when two devices edit different fields concurrently. Documented in `docs/SPECIFICATION.md §9.5`.
- **Anonymous data loss** if the user uninstalls before converting the account. Documented in `docs/SPECIFICATION.md §4`.

## Privacy

- Analytics collection is disabled by default and requires an explicit opt-in.
- Analytics events carry no odometer, volume, cost, notes, entity IDs or UID.
- Release logs never contain the Firebase UID, notes, exact odometer values or costs.
- In-app account deletion is available and deletes remote data before the auth account.
