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

  Since `D-34` the repository is **public**, so that proviso is a precondition rather than good
  practice: anyone can read those keys the moment the files are committed. E0-07 restricted the
  development keys in the Google Cloud console before committing either file; the completed
  evidence is in `docs/handoffs/E0-07.md`. Every future app registration MUST repeat that ordering.

  Under `D-41`, the Android development key is restricted to the debug application ID and the
  owner's current local debug signing certificate. The iOS development key is restricted to the
  debug bundle identifier. Moving Android development to another machine requires an explicit
  additional fingerprint; restrictions MUST NOT be widened automatically.

  Under `D-238` (superseding `D-54`), each build type carries only its own restricted
  configuration. Debug uses `androidApp/src/debug/google-services.json` and
  `iosApp/Config/Debug/GoogleService-Info-Debug.plist` for `davidruiz-carapp-dev`; Release uses
  `androidApp/src/release/google-services.json` and `iosApp/Config/Release/GoogleService-Info.plist`
  for the production project of `docs/identifiers.md`. A configuration MUST NOT reach an artifact of
  the other build type, and `FirebaseConfigurationTest` enforces it. The production keys must be
  restricted to the release package, signing certificates and bundle identifier before either file is
  committed (`docs/runbooks/production-firebase-release.md` O4–O5); until they exist, release builds
  fail closed.

Never committed:

- Keystores and `*.jks`, `*.keystore`.
- Apple `*.p8`, `*.p12`, provisioning profiles.
- Service-account JSON of any kind.
- `local.properties`.
- Any token, password or private key.

Requirements:

- `.gitignore` MUST cover the entries above from the first commit.
- Secret scanning MUST be enabled on the repository. It is enabled, together with push protection,
  since 2026-10-27 (`D-260`).
- Product tests in CI run against the Firestore emulator and MUST NOT hold Firebase client or
  administrator credentials or write to a real Firebase project. D-66 permits one short-lived
  GitHub OIDC identity whose custom role contains only `cloudfunctions.functions.get`; it can read
  the deployed runtime and cannot read application data or mutate the project.
- If a secret is committed, treat it as compromised: rotate it first, then rewrite history.
- Crash reports MUST NOT contain UID, tokens, notes, exact odometer values, exact costs, raw Firestore payloads or free-text user content.

## Accepted Residual Risks

- **A sync cycle that ignores cancellation can outlive `AppGraph.close()` by up to five seconds**
  (`D-172`, `E3-17`). `close()` cancels `graphScope` and releases the `DatabaseHandle` from a single
  bounded waiter on a scope that outlives the graph: it joins the cancelled scope and releases
  afterwards, or gives up at a 5-second deadline and releases anyway. A cooperative cycle - every
  cycle the MVP actually runs - unwinds during cancellation and the handle is released as soon as it
  finishes, so the deadline is not reached. The residual is the case of work that does not observe
  cancellation at all, which is also the case that would otherwise hold the driver for the life of
  the process. Inside that window the graph reports itself closed while its driver is still live, so
  a call could still reach a driver that is about to be released; the reverse - releasing the driver
  underneath live work - is the defect this decision removes. The window is bounded, needs no owner
  action, and cannot lose or corrupt data: it is a lifetime question, not a durability one. No
  document may describe the close path as race-free for non-cooperative work until that work is
  bounded at its source.

- **Three low-probability departure concurrency and lifecycle windows remain in the MVP**
  (`D-164`). First, delayed provider work and retained retries are not atomically bound to the owner
  that created the request, so a session switch in the remaining check-to-use window can make work
  act on a different active owner; `E5-02` owns the fix. Second, `NonCancellable` keeps the coroutine
  tail alive but a host graph close can dispose the auth client or database before the tail finishes,
  leaving provider-session cleanup or local clearing incomplete; `E5-03` owns graph-lifecycle
  completion. Third, an auth transition consumed while the outbox count suppresses presentation
  updates may not be reconciled afterwards, leaving stale session state or `isBusy`; `E5-04` owns
  reconciliation. These windows are outside the normal single-owner foreground flow and do not
  block PR #65 or MVP completion. No document may describe any of the three guarantees as complete
  until its executable follow-up closes it.
- **Local data can outlive a deleted account if the process dies while the server operation is
  returning** (`D-165`, narrowing `D-163`). The F-5 departure now persists a durable marker before
  its first destructive step and records each step as it succeeds, and the app graph finishes an
  interrupted departure at the next launch, so a process death at any point the marker covers is
  recovered. One instant is not covered: between the `D-23` server operation returning success and
  that success being written to the local database. Firebase Auth and Cloud Firestore share no
  transaction with SQLite, so the two writes cannot be made atomic — the same class of limit as
  `D-150`. A process death inside that instant leaves the vehicles, refuels and settings of an
  account that no longer exists remotely on the device, and the next launch drops the marker rather
  than resuming, because it cannot tell whether the server call ran and MUST NOT repeat it. The data
  is device-local, is no longer reachable from any account, and is removed by the next successful
  departure or by uninstalling the app; no remote copy survives. The window is now a single
  round-trip completion rather than a whole tail of network and database work, but it is not zero,
  and no document may describe the departure as fully recoverable across process death.
- **A confirmed account replacement that fails before the session switch blocks the anonymous
  owner's backup, and its retry can lose rows** (`D-228`, accepted as option A on
  2026-10-16). Confirming the credential collision of `docs/CONTRACTS.md §11.3` persists the
  replacement marker first. If issuing the cleanup ticket or signing into the existing permanent
  account then fails, or the process dies in that interval, the marker stays in
  `SNAPSHOT_CAPTURED` while the session is still anonymous. Every sync cycle is then refused,
  because the replacement can resume only from a permanent session, so the owner's later changes
  are not backed up until the owner links again, and the manual backup retry cannot clear it. The
  new link reuses the snapshot captured at the first attempt, as `§11.3` requires, and the local
  rebuild clears every local row before restoring that snapshot, so vehicles and refuels written
  between the failure and the retry are lost. A local-data deletion also clears the marker. The
  window opens only after the owner confirmed a destructive replacement and a network or provider
  call failed. The correctness follow-up `E2-10` owns the fix; until it merges no document
  may describe the account replacement as lossless across a failure before the session switch.
- **A failed resume after the session switch can offer first-vehicle creation over data the
  replacement will rebuild** (`D-228`, accepted as option A on 2026-10-16). After the switch
  the local rows still belong to the anonymous UID until the local rebuild. If the resume fails,
  for example on an offline relaunch, the permanent owner's vehicle list resolves as empty and
  F-1 presents mandatory first-vehicle creation. A vehicle created there is deleted when the
  resumed replacement rebuilds the local rows from the snapshot; the device's own vehicles and
  refuels are not lost, because the resume restores them. The correctness follow-up `E2-10`
  owns the fix.
- **A write made while a confirmed account replacement runs can be lost** (`D-233`, accepted as option A
  on 2026-10-23). Confirming the credential collision captures the local snapshot before the
  orphan-cleanup ticket, the sign-in to the existing permanent account and the remote replacement; the
  local rebuild then clears every local row and restores that snapshot. A vehicle or refuel written in
  that interval is lost even though the replacement succeeds, and outside Settings no host shows that
  the replacement is running. The correctness follow-up `E2-10` owns the fix.
- **One orphan-cleanup authorization can outlive a successful account deletion** (`D-149`, accepted
  as option D on 2026-10-15). `deleteAccount` deletes the remote data, purges the
  `orphanCleanupTickets` records bound to the UID and then deletes the Firebase Auth user. If a
  ticket issuance for the same UID passes its Admin eligibility read before that purge and lands its
  write after it, the authorization survives the deletion that returned success. No mechanism closes
  this: Firebase Auth and Cloud Firestore share no transaction, and the only candidate with a
  serialization point — a marker read inside the issuance transaction — would retain every deleted
  UID indefinitely, the retention `D-143` was accepted to remove. The owner accepted the window
  because only the account owner can start either operation, the app issues a ticket only from an
  anonymous session and calls `deleteAccount` only from a permanent one, so it does not run the two
  operations against one account at the same time, and the surviving record holds only the
  pseudonymous UID of an account that no longer exists, a ticket hash, a status and an expiry — no
  product data and no capability against anyone else. Its only cleanup is the Firestore TTL on
  `expiresAt`: provider-managed eventual deletion after the 30-day expiration horizon, with no
  proven maximum. No document may describe the account deletion as erasing every UID-bound
  authorization, or cite that TTL as a retention bound. `functions/test/accountDeletionIssuanceRace.test.mjs`
  pins the interleaving.
- **An orphan-cleanup authorization can be created for an identity that changed during issuance**
  (`D-150`, accepted as option A on 2026-10-15). The ticket issuer reads the caller's Firebase Auth
  record, checks that it exists, is enabled and is still anonymous (`D-148`), and then writes the
  authorization to Cloud Firestore. The two services share no transaction, so the account can be
  linked, disabled or deleted between the read and the write, and the authorization is created
  anyway. Consumption rejects a bound account observed to be linked (`D-142`) before deletion.
  If the bound Auth user is missing, consumption skips Auth deletion but still deletes any remaining
  registered remote data for that UID, then marks the authorization `COMPLETED`; it is not a no-op.
  An account that remains anonymous but becomes disabled can still be cleaned up by its ticket,
  and that case requires an administrator to disable the account inside the lookup-to-write window.
  Linking and the app's own deletion paths can be started only by the account owner. Successful
  consumption changes only the authorization's status to `COMPLETED`; it does not erase the
  document or its bound UID, and it does not change `expiresAt`. Expiration rejects subsequent
  consumption but does not itself erase the record. Physical erasure is provided by a `D-143`
  account-deletion purge that observes the record, or by asynchronous Firestore TTL cleanup on
  `expiresAt` after the 30-day expiration horizon, with no proven maximum. No document may describe
  `D-148` as guaranteeing eligibility when the authorization is written. The three `D-150` tests in
  `functions/test/orphanedAnonymousAccount.test.mjs` pin the interleavings.
- **Broad billing-account role on one isolated identity** (`D-69`). A personal Cloud Billing
  account cannot host a custom IAM role, and `billing.resourceAssociations.delete` is available to
  the cutoff through the broad standard `roles/billing.admin` role. The role belongs only to the
  keyless `development-billing-cutoff` identity. Its project role contains only project read,
  billing-assignment deletion and service use; its `roles/run.invoker` binding is scoped only to
  the `stopbilling` Cloud Run service so Eventarc can deliver the CloudEvent. It has no Auth,
  Firestore, Storage or product-data role. A Cloud Monitoring alert sends every billing-account
  administrative change to the owner, and the runbook records every deliberate billing-state
  transition.
- **Production Cloud Functions transitive advisory GHSA-w5hq-g745-h8pq** (`D-68`). The official
  Functions graph contains `uuid@9.0.1` through Firebase Admin's Cloud Storage dependency, and a
  production-only audit reports seven moderate entries. `firebase-admin` cannot be removed because
  Firebase Functions 7.3.2 declares it as a mandatory peer. The executable full-trigger test proves
  that `stopBilling` reaches the Billing disable operation without loading Cloud Storage or `uuid`,
  so the affected UUID v3/v5/v6 buffer path is unreachable. CI displays moderate findings and fails
  on high or critical. The dated, expiring acceptance and current-state history live in
  `docs/SECURITY_ADVISORY_REGISTER.md` and are reviewed through TD-01.
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
- **The release Firebase configuration is not yet supplied, so release builds fail closed**
  (`D-238`, `D-54`). `./gradlew :androidApp:assembleRelease` stops at
  `processReleaseGoogleServices` until the owner provides the restricted production
  `google-services.json`; no release artifact can be built without it.
- **Production Firebase client configuration will be public** (`D-238`). The release
  `google-services.json` and `GoogleService-Info.plist` are to be committed to the public repository.
  Their API keys must be restricted to the release package and signing certificates and to the release
  bundle identifier; App Check (`D-67`, `D-249`) and the Firestore rules remain the abuse and
  authorization controls. Widening a key restriction requires the owner.
- **Production has no automatic billing shutdown** (`D-66`, `D-237`, `D-247`). The production budget
  only notifies, and reporting is delayed, so a cost spike continues until the owner acts on an alert.
- **Release Android builds are neither shrunk nor obfuscated** (`D-245`). The APK is larger and its code
  is readable; it contains no secret.
- **Release builds are verified locally, not in CI** (`D-248`). A release-only build regression is found
  by the next local or owner build, not by a required check.
- **The client could be steered only in Debug through the launch environment** (`E4-04`). Every UI-test
  hook now compiles only under `#if DEBUG`, and `SwiftDebugOnlyHookRule` keeps it that way, so a Release
  build cannot be steered by whoever launches it.
- **Store-only privacy obligations are deferred while the app is not published** (`D-236`, accepted
  on 2026-10-24). Release builds run against the production project, but deleting an Apple-linked
  account does not revoke its Sign in with Apple token, production App Check on Android accepts
  versions that Google Play does not recognise, and no iOS privacy manifest, store privacy label or
  public privacy policy exists. A user who deletes an Apple-linked account keeps the app listed under
  Sign in with Apple in their Apple Account settings until they remove it there. `E4-09`
  closes all of them and MUST land before any App Store or Google Play submission.

- **An opt-out reaches the provider's fatal-crash handler at the next launch** (`D-241`, `E4-04`).
  Both Crashlytics SDKs persist the collection command and apply a disable from the next app launch.
  When the owner of a device turns the Settings opt-in off, the crash reporter drops every later
  non-fatal report at once and deletes the reports the provider stored while collection was off,
  but the provider's fatal-crash handler stays active until the app restarts, so a fatal crash in
  that same session may still be uploaded on Android. Such a report carries only the `§20.3.1`
  allowlisted keys, the stable error code and a stack trace. The window is one app session.

- **Nothing technical stops an agent merge** (`D-260`, accepted 2026-10-27). Branch protection on
  `main` requires the fifteen checks but no approving or code-owner review, and administrator
  enforcement is off, because the sole collaborator is also the sole author and GitHub forbids
  self-approval. Agents act with the owner's GitHub credentials, so only the process rule that agents
  never merge stands between an agent and `main`. A machine account that could author agent pull
  requests for the owner to approve was considered and declined for now.
- **Two acknowledged development API keys remain in the commit history** (`D-260`, 2026-10-27).
  Secret scanning is enabled and raised exactly two `google_api_key` alerts from commit `0ddef323`, for
  `androidApp/src/debug/google-services.json` and
  `iosApp/Config/Debug/GoogleService-Info-Debug.plist`. Both are resolved `wont_fix`: Firebase client
  configuration is not a secret under this policy, and both keys were verified restricted by
  application and by API target on 2026-10-27. GitHub does not apply push protection to that pattern, so
  committing restricted Firebase client configuration is unaffected.

## Privacy

- Analytics collection is disabled by default and requires an explicit opt-in.
- Application-defined analytics events carry no odometer, volume, cost, notes, entity IDs or UID.
- After opt-in, standard technical Firebase SDK events and its app instance ID are accepted for
  pseudonymous session measurement (`D-203`). AAID, IDFA, IDFV, personalized ad signals and automatic
  screen reporting are disabled; Android advertising permissions are removed and iOS links the
  no-ad-ID product. The app never sets an analytics user ID or links analytics to the Firebase UID.
- Crash collection is disabled by default and follows the same Settings opt-in as analytics
  (`D-241`). Crash reports carry only the `docs/CONTRACTS.md §20.3.1` allowlisted keys, the
  recorded error's stable code and stack traces; the app never sets a Crashlytics user identifier.
- Release logs never contain the Firebase UID, notes, exact odometer values or costs. Every build
  binds a logger that emits nothing until `E4-08` (`D-242`).
- In-app account deletion is available and uses the `D-23` Firebase Admin server operation to delete remote data before the auth account. Mobile clients never receive a Firestore hard-delete permission.
- App Check debug tokens are secrets. They MUST NOT be committed, printed by CI or embedded in a
  distributed build. Debug-provider dependencies and factories are forbidden in release variants.
- The GitHub OIDC provider admits only the immutable `davidru85/carApp` repository identity, the
  protected runtime-verification environment and approved main/PR contexts. Its service account
  has a custom role containing exactly `cloudfunctions.functions.get` and no broader project role.
