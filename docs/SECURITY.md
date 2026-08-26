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

  Since `D-34` the repository is **public**, so that proviso is a precondition rather than good practice: anyone can read those keys the moment the files are committed. `E0-07` MUST restrict the keys in the Google Cloud console before committing either file, and the handoff MUST state that it did.

  Under `D-41`, the Android development key is restricted to the debug application ID and the
  owner's current local debug signing certificate. The iOS development key is restricted to the
  debug bundle identifier. Moving Android development to another machine requires an explicit
  additional fingerprint; restrictions MUST NOT be widened automatically.

  Under `D-54`, the restricted development configurations are build-specific:
  `androidApp/src/debug/google-services.json` and
  `iosApp/Config/Debug/GoogleService-Info-Debug.plist`. They MUST NOT be included in release
  artifacts. Release builds fail closed until E4-04 supplies separately reviewed production
  configuration.

Never committed:

- Keystores and `*.jks`, `*.keystore`.
- Apple `*.p8`, `*.p12`, provisioning profiles.
- Service-account JSON of any kind.
- `local.properties`.
- Any token, password or private key.

Requirements:

- `.gitignore` MUST cover the entries above from the first commit.
- Secret scanning MUST be enabled on the repository.
- Product tests in CI run against the Firestore emulator and MUST NOT hold Firebase client or
  administrator credentials or write to a real Firebase project. D-66 permits one short-lived
  GitHub OIDC identity whose custom role contains only `cloudfunctions.functions.get`; it can read
  the deployed runtime and cannot read application data or mutate the project.
- If a secret is committed, treat it as compromised: rotate it first, then rewrite history.
- Crash reports MUST NOT contain UID, tokens, notes, exact odometer values, exact costs, raw Firestore payloads or free-text user content.

## Accepted Residual Risks

- **Moderate Firebase CLI transitive advisories in the E3-01 test harness** (`D-52`). At acceptance,
  `npm audit` reports five moderate dependency entries representing two advisories below the pinned
  Firebase CLI 15.28.1: OpenTelemetry W3C baggage allocation and old UUID buffer APIs. The CLI runs
  only the local Firestore emulator against repository-owned fixtures; those paths are unused and
  none of the npm packages ships in the Android or iOS app. Re-evaluate on any high or critical
  advisory, affected-path expansion or reviewed Firebase CLI update.
- **App Check is an abuse control, not authorization** (`D-67`). Authentication and Firestore
  enforce App Check because billing converts frictionless anonymous-authentication abuse into a
  direct cost vector. Firestore Rules remain load-bearing: App Check does not prove owner identity,
  validate schema or authorize a document path.
- **No general Cloud Functions-mediated database access in the MVP** (`docs/SPECIFICATION.md
  §3.3`). The only MVP server/Admin operations are the `D-23` user-requested account deletion and
  the `D-63` anonymous identity/data-cleanup paths. Server-side validation before remote writes,
  authenticated identity and authorization checks before remote reads, rate limiting, abuse
  monitoring and broader privileged server-side product operations require a
  future story or ADR before implementation.
- **No receipt, odometer image or OCR processing in the MVP** (`docs/SPECIFICATION.md §3.3`). Future local AI text recognition must keep receipt images, odometer images, recognized raw text and extracted fields local unless a later explicit owner decision changes the privacy model.
- **Last-write-wins backup collision handling** can lose one whole-document update if the same account is actively edited on multiple devices. Active multi-device editing is not a supported MVP workflow. Documented in `docs/SPECIFICATION.md §9.5`.
- **Anonymous data loss** if the user uninstalls, clears the retained Firebase Auth session or
  reaches native automatic cleanup eligibility before linking a permanent provider. Anonymous
  identity is device-bound, and the risk is disclosed through `D-62` foreground notices. Documented
  in `docs/SPECIFICATION.md §4` and `docs/CONTRACTS.md §11.2`.
- **One temporary Cloud Functions 1st gen dependency.** `onAnonymousUserDeleted` is the only
  permitted exception because Authentication user-deletion events have no 2nd gen equivalent.
  The exact migration surface, quarterly owner review and prohibition on additional 1st gen
  functions are tracked in `docs/TECHNICAL_PLAN.md §13` (`TD-01`).
- **Cloud Billing controls are delayed best effort, not a hard cap.** The D-66 EUR 10 budget sends
  notifications and the project-local function removes billing after reported actual cost reaches
  100%. Cost reporting can arrive late, so charges can exceed the budget. The intended development
  response is a complete project outage followed by owner-led manual recovery. Production MUST NOT
  inherit this automatic cutoff.

## Privacy

- Analytics collection is disabled by default and requires an explicit opt-in.
- Analytics events carry no odometer, volume, cost, notes, entity IDs or UID.
- Release logs never contain the Firebase UID, notes, exact odometer values or costs.
- In-app account deletion is available and uses the `D-23` Firebase Admin server operation to delete remote data before the auth account. Mobile clients never receive a Firestore hard-delete permission.
- App Check debug tokens are secrets. They MUST NOT be committed, printed by CI or embedded in a
  distributed build. Debug-provider dependencies and factories are forbidden in release variants.
- The GitHub OIDC provider admits only the immutable `davidru85/carApp` repository identity, the
  protected runtime-verification environment and approved main/PR contexts. Its service account
  has a custom role containing exactly `cloudfunctions.functions.get` and no broader project role.
