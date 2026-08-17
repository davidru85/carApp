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
- CI runs against the Firestore emulator and MUST NOT hold Firebase project credentials or write to a real Firebase project.
- If a secret is committed, treat it as compromised: rotate it first, then rewrite history.
- Crash reports MUST NOT contain UID, tokens, notes, exact odometer values, exact costs, raw Firestore payloads or free-text user content.

## Accepted Residual Risks

- **No Firebase App Check in the MVP** (`docs/SPECIFICATION.md §3.2`). Anyone holding a valid UID can write to their own subtree with a non-official client. This is mitigated, not removed, by per-field range validation in the Firestore rules and by client-side quarantine of unsupported or malformed documents. Revisit before any public launch beyond the MVP.
- **No post-MVP Cloud Functions-mediated database access in the MVP** (`docs/SPECIFICATION.md §3.3`). The only MVP server/Admin operation is `D-23` account deletion. Server-side validation before remote writes, authenticated identity and authorization checks before remote reads, app integrity checks, rate limiting, abuse monitoring and broader privileged server-side product operations require a future story or ADR before implementation.
- **No receipt, odometer image or OCR processing in the MVP** (`docs/SPECIFICATION.md §3.3`). Future local AI text recognition must keep receipt images, odometer images, recognized raw text and extracted fields local unless a later explicit owner decision changes the privacy model.
- **Last-write-wins backup collision handling** can lose one whole-document update if the same account is actively edited on multiple devices. Active multi-device editing is not a supported MVP workflow. Documented in `docs/SPECIFICATION.md §9.5`.
- **Anonymous data loss** if the user uninstalls before converting the account. Documented in `docs/SPECIFICATION.md §4`.

## Privacy

- Analytics collection is disabled by default and requires an explicit opt-in.
- Analytics events carry no odometer, volume, cost, notes, entity IDs or UID.
- Release logs never contain the Firebase UID, notes, exact odometer values or costs.
- In-app account deletion is available and uses the `D-23` Firebase Admin server operation to delete remote data before the auth account. Mobile clients never receive a Firestore hard-delete permission.
