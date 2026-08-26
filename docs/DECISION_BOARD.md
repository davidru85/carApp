# Decision Board - carApp MVP

> **Sole registry of decision IDs (`D-n`)** and authoritative for which libraries, services and technical options are allowed. Accepted decisions MUST be reflected in an ADR and mirrored in `docs/SPECIFICATION.md §12` and `docs/TECHNICAL_PLAN.md §2`; `contract-check` asserts that mirrored decision IDs and statuses stay aligned. See `AGENTS.md` for authority and normative language.

## Scope of this registry

The registry is not limited to decisions taken during the definition phase. **Any decision taken
while implementing a story — a build-model choice, an identifier convention, a policy such as how
`targetSdk` is pinned — MUST be added here with a new decision ID and MUST get its own ADR**, in the
same pull request that makes the decision. A decision recorded only in a handoff, a commit message
or `docs/PROJECT_LOG.md` is not recorded: those are history, not authority, and the next agent has
no obligation to follow them.

## Decision Status

One vocabulary, shared with the ADR `Status` field. An ADR recording a deferral has `Status: Deferred`, not `Accepted`.

- `Proposed`: a recommendation is on the table and requires owner confirmation before the first story that depends on it starts.
- `Accepted`: selected for the MVP.
- `Deferred`: intentionally not part of the MVP but reserved for future use.
- `Pending`: no recommendation yet; requires an owner decision.
- `Rejected`: explicitly not allowed for the MVP.
- `Superseded`: replaced by a later decision, which MUST be named.

**Blocking rule:** a `Proposed` or `Pending` decision that any Ready story depends on is itself a blocking backlog item. A story that needs it is not Ready (`AGENTS.md`, Definition of Ready).

## Core Stack Decisions

| ID | Area | Choice | Alternatives Reviewed | Status | Guardrail |
|----|------|--------|-----------------------|--------|-----------|
| D-0 | Remote backend | Cloud Firestore | Supabase, AWS backend, custom API, local-only MVP | Accepted | Firestore is a backup and recovery replica behind `RemoteSyncSource`, never UI source of truth. |
| D-1 | Local database | Room 3 KMP | SQLDelight, Realm, DataStore-only | Superseded | Superseded by `D-36` after Room could not express the mandatory SQLite `CHECK` constraints as one generated schema. |
| D-2 | iOS interop | SKIE | Raw KMP export, manual wrappers | Superseded | D-58 retains SKIE but moves framework generation and SKIE processing to `:composition:ios`. |
| D-3 | Dependency injection | Koin KMP | Manual DI, Metro, Kotlin Inject | Accepted | Koin is wiring only. No service locator access in domain, use cases or repositories. |
| D-4 | `fuelType` | Store MVP combustion/fuel-like values on `Vehicle` from day one | Add later, store per fuel entry, include electric/hybrid in MVP | Accepted | Persist default `GASOLINE`; no MVP UI selector; metadata only; `ELECTRIC` and `HYBRID` are deferred to a future energy model. |
| D-5 | Firestore access | Firebase Firestore behind `RemoteSyncSource` | Ktor + Firestore REST, native SDK wrappers, custom API | Accepted | Firebase used directly behind integration boundaries. |
| D-6 | Authentication | Firebase Auth behind `AuthClient` | Custom auth, native-only wrappers | Accepted | Auth provider types never cross `:integration:firebase-auth`. |
| D-7 | Navigation | Native per platform | Shared router, Compose Multiplatform navigation | Accepted | No shared destination sealed class. |
| D-8 | Presentation | Shared KMP state holders | Native-only ViewModels, shared UI | Accepted | State holders expose `StateFlow<UiState>` and intents; `UiState` carries no user-facing text. |
| D-9 | Firestore offline persistence | Disabled | Enabled Firestore SDK cache | Accepted | The SQLDelight local database and the outbox are the only offline model. |
| D-10 | Metrics | Firebase Analytics behind `AnalyticsTracker` | PostHog, custom metrics, none | Accepted | Closed `AnalyticsEvent` hierarchy; no analytics calls from domain or data; off by default. |
| D-11 | HTTP/API client | Ktor, for future API-based remote implementations | Retrofit, native URLSession/OkHttp wrappers, no HTTP abstraction | Deferred | Do not add Ktor to the MVP. |
| D-12 | Image loading | Coil, if image loading ever becomes necessary | SDWebImage, platform-native loaders, none | Deferred | No image loading dependency until a story requires it; Coil is then the only approved library. |
| D-13 | Firestore location | `europe-west1` single region | `eur3` European multi-region, `nam5` United States multi-region | Accepted | Firestore is a backup and recovery replica only; the local database is the source of truth. The location is immutable after database creation and must be verified before `E0-07` creates the database. |
| D-14 | Firebase project topology | One development Firebase project plus the local emulator now; add a separate production Firebase project before release | Two projects from day one, single project through production, three projects with staging | Accepted | Development uses one real Firebase project for manual testing, CI uses the emulator only, and no public release build may point at the development project. |
| D-15 | Logging implementation | Kermit behind `Logger` | Napier, custom sinks, no implementation | Accepted | The `Logger` abstraction is mandatory regardless; Kermit never appears outside the sink implementation. |
| D-16 | Architecture checks | Konsist for package-level rules, custom Gradle check for module-level rules | Custom checks only, dependency-analysis plugin | Accepted | Gradle cannot express intra-module package rules, and features are one module each. Every rule needs a failing fixture test. |
| D-17 | Flow testing helper | Turbine | Manual collection | Accepted | Confirm compatibility during version pinning in `E0-06`. |
| D-18 | Coverage measurement | Kover with per-module thresholds | No measurement, JaCoCo | Accepted | `:core:model` and `:core:common` at least 90%, feature `domain` 85%, `:core:sync` 80%, enforced in CI. |
| D-19 | Result type | Custom `Outcome<T, E>` in `:core:common` | `kotlin.Result`, Arrow `Either`, exceptions | Accepted | `kotlin.Result` has a single type parameter. Arrow is rejected for MVP dependency surface. Declared in `docs/CONTRACTS.md §20.1`. |
| D-20 | Localization implementation | Native Android and iOS resources | Shared resource library (moko-resources) | Accepted | UI is native. `UiState` carries typed values only, so shared code never needs a string bundle. |
| D-21 | Crash reporting | Firebase Crashlytics behind `CrashReporter` | Sentry, none | Accepted | Added in Phase 4. Crashlytics types never leave `:integration:firebase-crashlytics` or `:wiring:firebase`. |
| D-22 | Application identifiers | Fixed in `docs/identifiers.md` | — | Accepted | Agents MUST NOT invent an applicationId, bundle id, namespace, project name or region. The production Firebase project ID is deferred by `D-14`. |
| D-23 | Account deletion execution | Firebase Admin server operation | Client hard-delete exception, tombstone-only purge, manual support deletion | Accepted | Client Firestore rules keep `allow delete: if false`. Account deletion hard deletes run only in an authenticated server/Admin environment that verifies the caller UID, deletes `fuelEntries`, then `vehicles`, then the Firebase Auth user, and returns success before the app clears local data. |
| D-24 | Module Android namespaces | Derived from the Gradle module path | One concrete value per module, per-module free choice | Accepted | Namespace = shared package root + module path, `:`→`.`, `-` removed. Not the Kotlin package root. `:androidApp` keeps `com.ruizurraca.carapp`. |
| D-25 | `targetSdk` policy | Pinned independently of `compileSdk`; `compileSdk 37`, `targetSdk 36` | Keep both at the same level | Accepted | `compileSdk` is forced by the Compose BOM; `targetSdk` is a runtime opt-in and a behavioural change. `E4-04` owns raising it before release. |
| D-26 | Monetary golden values | Correct the contradictory golden row and add a real HALF_UP round-up row | Keep the row and add a minimum-one-minor-unit rule; correct the row only | Accepted | Golden row 3 contradicted the formula it illustrates. The formula is authoritative; both rounding directions stay covered. |
| D-27 | `testAppGraphDependencies` ownership | Built by `E0-07`, not `E0-03` | Create stub `:core:*` modules in Phase 0; build a partial factory | Accepted | Four of the 15 `AppGraphDependencies` members have types owned by modules Phase 0 forbids creating. |
| D-28 | Feature-layer package rules | Implemented with Konsist in `E1-07` | A dedicated architecture-test module now; extend the custom checker and drop Konsist | Accepted | Konsist rules need a module to live in and no `:feature:*` module exists. The 14 module-level rows are enforced by `E0-04`. |
| D-29 | Contract type declarations | A type may be declared in `§20` or inline in the section that owns it | Move all declarations into `§20`; leave the assertion unenforceable | Accepted | Nine types are declared inline beside the prose that constrains them. `§18` assertion 1 is worded to accept either location. |
| D-30 | Walking skeleton position | `E0-07` is the second story of Phase 1, after `E1-01` | Allow `E0-07` to create `:core:database`; drop local persistence from `E0-07` | Accepted | `E0-07` needs the local database, which lives in `:core:database`, a module Phase 0 forbade and `E0-04` enforced. |
| D-31 | Branch protection | `main` requires all nine CI checks of `§18` | Require only the seven checks that verify something today | Accepted | Administrator enforcement is off so the owner can land pull requests that predate the workflow. |
| D-32 | Development Firebase project ID | `davidruiz-carapp-dev` | `carapp-dev` (unavailable) | Accepted | `carapp-dev` is held by another Google Cloud customer: `409 ALREADY_EXISTS` on create and `403 PERMISSION_DENIED` on addFirebase. Application identifiers are unchanged. |
| D-33 | Repository visibility and branch protection | Stay private; apply the `D-31` protection in the same change that makes the repository public or moves it to a plan that allows it | Upgrade to GitHub Pro now; make the repository public now | Superseded | Branch protection is gated for a private repository on the Free plan. Until the trigger fires, CI reports but does not gate, and a merge on red is possible. |
| D-34 | Repository visibility and branch protection | Repository is public; the `D-31` branch protection is active | Upgrade to GitHub Pro; stay private and reduce CI | Accepted | Supersedes `D-33`. Public repositories get free standard runners and can use branch protection; the macOS jobs cost ten times wall-clock and the account had exhausted its minutes. |
| D-35 | CI job topology | `shared-tests` and `ios-simulator-build` stay separate jobs | Merge them into one macOS job | Accepted | They are complementary diagnostics — shared-logic behaviour versus framework linking — and a single job cannot report both independently. They also run in parallel, so separating them costs no wall-clock. |
| D-36 | Local database implementation | SQLDelight 2.3.2 with AndroidX bundled SQLite 2.7.0 through `sqldelight-androidx-driver` 0.2.1 | Room 3 KMP with callback-owned DDL; SQLDelight official platform drivers; raise `minSdk` to 30 | Accepted | `.sq` files are the canonical schema, generated operations are asynchronous, the SQLite 3.24 dialect preserves the exact outbox UPSERT, and the bundled engine keeps identical SQLite behaviour on Android and iOS with `minSdk 26`. |
| D-37 | Kotlin/Native iOS targets | Support `iosArm64` and `iosSimulatorArm64`; remove `iosX64` | Use the official SQLDelight Native driver only for `iosX64`; use it for every iOS target; maintain private `iosX64` forks | Accepted | The application and CI already build ARM64 only, while the `D-36` AndroidX bundled SQLite stack publishes no `iosX64` variants. Reintroducing Intel-simulator support requires a compatible complete dependency set and a superseding decision. |
| D-38 | Database-owned mutation strategy | Use a Kotlin/SQLDelight `DatabaseMutations` transaction facade; forbid direct generated entity-mutation calls outside `:core:database` | SQLite triggers; split Kotlin/trigger implementation | Accepted | The facade can capture pre-write state, apply the exact de-duplicated recompute set, notify SQLDelight observers and keep every change in one transaction. Triggers obscure recomputation and do not reliably expose indirect table changes to SQLDelight `Flow`. |
| D-39 | Walking-skeleton data slice | Use a minimal valid `Vehicle` slice; the edited proof value is the vehicle name | Full vehicle form and repository; fuel entry with a seeded vehicle | Accepted | The closed Firestore schema permits no temporary collection. E0-07 proves a real vehicle round trip without absorbing E1-02 and E1-03. |
| D-40 | Firestore-rule sequencing | Complete `E3-01` before `E0-07` | Fold E3-01 into E0-07; deploy temporary cloud-only rules | Accepted | The first real client write must use the complete reviewed rule set and emulator evidence, never temporary permissive rules. |
| D-41 | Development Android Firebase certificate | Restrict the debug Firebase key to `com.ruizurraca.carapp.debug` and the owner's current local debug signing certificate | Dedicated development keystore; defer restriction | Accepted | The repository is public. A new development machine requires an explicitly registered fingerprint; keystores and fingerprints stay out of repository documentation. |
| D-42 | Provider-proof sequencing | Complete `E3-06` before `E3-01`, which remains before `E0-07` | Fold E3-06 into E3-01; relax the placeholder check | Accepted | The required provider-decoupling check becomes executable before the first integration module appears. |
| D-43 | Provider-exclusion control | Use `carapp.excludeFirebaseProviders=true` in the canonical Gradle settings | Separate settings file; generated CI-only settings | Accepted | Local and CI verification use the same module graph definition; a second provider-free settings file is forbidden. |
| D-44 | Provider-module registry | Explicit canonical provider paths, included only when their directories exist and exclusion is off | Filesystem discovery; per-story ad-hoc conditions | Accepted | The proof is testable before provider modules exist, planned paths do not create empty projects and unknown modules are not silently admitted. |
| D-45 | Provider-decoupling platforms | Run Android host and `iosSimulatorArm64` provider-free tests in one macOS job | Android host only on Ubuntu; separate platform jobs | Accepted | Both JVM and Kotlin/Native dependency surfaces are checked while the required branch-protection name stays `provider-decoupling`. |
| D-46 | Firestore rules test stack | Node 22.22.3, Firebase CLI 15.28.1, Firebase JS 12.18.0, `@firebase/rules-unit-testing` 5.0.1 and built-in `node:test`, pinned by npm manifests | Direct REST harness; Kotlin/GitLive integration tests; third-party JS test runner | Accepted | Use the official auth-mocking emulator library without adding Jest, Mocha or Vitest. Gradle versions remain in the version catalog; Node-only versions live in `package.json` and `package-lock.json`. |
| D-47 | Firestore rules CI placement | Run emulator tests as a named step of the protected `contract-check` job | Add a tenth protected job; run under provider decoupling | Accepted | The rules are executable contract evidence and remain mandatory without changing the nine protected check names. |
| D-48 | Firestore client-cache configuration ownership | E0-07 owns the executable disabled-persistence configuration; E3-01 owns only rules, indexes and emulator tests | Create `:integration:firebase-firestore` in E3-01 | Accepted | E0-07 already configures the first real client; E3-01 must not introduce a premature provider module or native Firebase linking. |
| D-49 | MVP Firestore schema-version rule | Accept exactly `schemaVersion == 1` during the MVP | Accept every version `>= 1`; accept a predeclared version range | Accepted | Matches the closed remote schema, `CLIENT_MAX_SCHEMA_VERSION` and E3-01 acceptance criteria; a future schema rollout requires an explicit sequencing decision. |
| D-50 | Firestore first-page cursor | Use `startAt(overlapSince)` for the first page and the full timestamp/document-ID cursor for later pages | Rely only on the range filter; use a minimum UUID sentinel | Accepted | Firebase JS 12.18.0 rejects an empty document-ID cursor; a timestamp-only first boundary includes every document at the overlap timestamp without excluding malformed IDs that must be quarantined. |
| D-51 | npm install-script policy | Disable dependency install scripts repository-wide with `.npmrc` | Approve pinned scripts and pin npm 12; leave behavior to the installed npm version | Accepted | The emulator suite passes without the four blocked transitive scripts; a repository policy is deterministic across npm versions and reduces supply-chain execution. |
| D-52 | Firebase CLI audit residual | Retain Firebase CLI 15.28.1 and document its moderate dev-tool-only transitive advisories | Force-downgrade the CLI; override unsupported transitive majors | Accepted | The affected Pub/Sub baggage and UUID buffer paths are not used by the emulator harness and no package ships in the app; forced remediation would contradict D-46 or exceed upstream compatibility. |
| D-53 | Development Firebase app provisioning | Register separate Android and iOS debug apps, use their Firebase-provisioned platform keys, restrict each key before committing its configuration | Pre-create custom restricted keys; share one client key | Accepted | Google permits only one application-restriction type per key; separate platform keys keep the public debug configurations isolated and follow the official Firebase provisioning path. |
| D-54 | Development Firebase configuration isolation | Keep Firebase configuration debug-only on both platforms and fail release builds closed until production configuration exists | Share development configuration with release builds; inject untracked local files | Accepted | Build-time isolation prevents a release from reaching the development backend, while committed restricted debug configuration keeps local and CI setup reproducible. |
| D-55 | Walking-skeleton staged ownership | E0-07 creates final modules and public contracts, implements only the real Vehicle slice and leaves deterministic unwired shells for the remaining exported state holders | Narrow the E0-07 ABI temporarily; move E0-07 after the owning feature/auth/sync stories | Accepted | Preserves the full Swift golden-header gate and final module topology without absorbing later product behaviors or introducing temporary public API. |
| D-56 | Test app graph factory module | Place `testAppGraphDependencies(...)` in `:shared:testing`; keep `AppGraphDependencies` in `:shared` and `:core:testing` generic | Allow `:core:testing` to depend on `:shared`; move `AppGraphDependencies` to a new central module | Accepted | A dedicated KMP test-support module preserves the application-to-core dependency direction while making the factory reusable from `commonTest`. |
| D-57 | Android Firebase configuration plugin | Pin Google Services Gradle plugin 4.5.0 | Retain the previous stable 4.4.4 | Accepted | Use the current stable version documented by Firebase to process the debug-only `google-services.json`; the version lives only in the Gradle version catalog. |
| D-58 | iOS framework composition ownership | `:composition:ios` produces the single `Shared` framework, exports `:shared` and composes `:wiring:firebase` | Let `:shared` depend on wiring; use two frameworks with prior registration | Accepted | A dedicated composition root avoids the Gradle cycle, preserves provider-free `:shared` tests and prevents duplicated Kotlin/Native runtimes and global registration. Supersedes D-2's module-ownership constraint while retaining SKIE. |
| D-59 | `AppProviders` port shape | Explicit typed properties for every `AppGraphDependencies` dependency except `isDebugBuild` | Return the complete dependency container from one method; expose only Firebase providers | Accepted | The explicit port keeps graph construction compile-time checked, provider-free and free of service-location semantics; `buildAppGraph` remains responsible for applying `isDebugBuild`. |
| D-60 | Anonymous identity retention and portability | Treat an unlinked anonymous identity as device-bound and enable Firebase's native 30-day automatic cleanup | Promise cross-device anonymous recovery; retain anonymous accounts indefinitely | Accepted | Only a linked permanent provider makes the account recoverable on another device; associated data cleanup is owned by D-63. |
| D-61 | Account-linking collision precedence | Keep the current anonymous-session snapshot and replace the pre-existing permanent-account data after explicit confirmation | Keep the permanent account; automatically merge both accounts | Accepted | The destructive flow is resumable, captures proof of anonymous ownership before switching and leaves the anonymous session untouched on cancellation. |
| D-62 | Anonymous sign-in benefit reminders | Fixed day-1, day-3, day-8 and day-18 timeline with highest-due reminder collapse | Recalculate from display time; show every missed reminder | Accepted | The schedule is anchored to Firebase account creation, is non-blocking and completes permanently after reminder 4 or permanent sign-in. |
| D-63 | User-data cleanup implementation | Repository-owned idempotent service, direct collision-path invocation and one temporary 1st gen Auth deletion trigger | Firebase Delete User Data extension; rely only on the trigger | Accepted | Firebase Extensions management sunsets on 2027-03-31; `onAnonymousUserDeleted` is the only permitted 1st gen function and its migration is tracked as TD-01. |
| D-64 | Anonymous lifecycle delivery | Split retention, conversion, reminders, backend cleanup and cross-device proof across their owning stories | Implement the complete lifecycle in E0-07 | Accepted | Keeps E0-07 reviewable and moves cross-device evidence to the first point where permanent auth and complete sync coexist. |
| D-65 | Firebase Apple SDK compatibility pin | Pin Firebase Apple SDK 11.8.0 exactly with GitLive 2.6.0 | Use Firebase Apple 12.18.0; use Firebase Apple 11.15.0 | Accepted | GitLive 2.6.0 Apple cinterop bindings are generated against 11.8.0; using a different native SDK can compile but fail at runtime. The two pins move together when GitLive publishes bindings for a supported newer Firebase Apple SDK. |
| D-66 | Development cloud cost containment | EUR 10 monthly alerts-only budget, actual-cost alerts at 50/90/100%, project-local 2nd gen cutoff at 100%, Node.js 22 and read-only GitHub OIDC verification | Cut off at 90%; forecast cutoff; separate FinOps project; Python; Node.js 24 split or downgrade | Accepted | The budget is notification-only and delayed; the development project deliberately stops when the cutoff fires. Production uses aggressive alerts and manual intervention, never automatic billing shutdown. Node.js 22 is coupled to D-63 and moves under TD-01. |
| D-67 | Firebase App Check enforcement | Enforce Authentication and Firestore with App Attest, Play Integrity and build-restricted debug providers | Keep App Check out of MVP; integrate without enforcement | Accepted | Billing turns anonymous-authentication abuse from console noise into a direct cost vector. App Check supplements, never replaces, Authentication and Firestore Rules. |

## Library Review Matrix

| Area | Library / Tool | Alternatives | Status | Notes |
|------|----------------|--------------|--------|-------|
| Coroutines and streams | `kotlinx.coroutines` + Flow | callbacks, Rx | Accepted | Required for KMP async and state streams. |
| Serialization | `kotlinx.serialization-json` | Moshi, manual JSON | Accepted | Required for outbox payloads and remote DTOs. |
| Date/time | `kotlinx-datetime` | platform date APIs only | Accepted | UTC instants only in persistence. The exact `Instant` package is pinned in `docs/versions-matrix.md`. |
| Logging | Kermit behind `Logger` | Napier, custom sinks | Accepted (D-15) | Abstraction mandatory; implementation swappable. Does not replace `AnalyticsTracker` or `CrashReporter`. |
| Crash reporting | Firebase Crashlytics behind `CrashReporter` | Sentry, none | Accepted (D-21) | Phase 4. |
| Flow testing | Turbine | manual collection | Accepted (D-17) | Validate against the pinned coroutines version. |
| Test assertions | `kotlin.test` | Kotest | Accepted | Keep tests simple for agent predictability. |
| Test doubles | Hand-written fakes | MockK, Mockative | Accepted | Fakes are preferred for domain and sync. |
| Firestore rules tests | Node 22.22.3, Firebase CLI 15.28.1, Firebase JS 12.18.0, `@firebase/rules-unit-testing` 5.0.1, `node:test` | Direct REST harness, Kotlin/GitLive integration tests, Jest/Mocha/Vitest | Accepted (D-46) | Official emulator auth mocking; exact Node-only dependency versions live in npm manifests. |
| Firebase Apple SDK | 11.8.0, matched exactly to GitLive 2.6.0 Apple bindings | Firebase Apple 12.18.0; Firebase Apple 11.15.0 | Accepted (D-65) | Direct iOS integration must not mix the GitLive cinterop bindings with a different native Firebase Apple release. |
| Cloud billing cutoff | Node.js 22, Firebase Functions 7.3.2, Firebase Admin 14.3.0, `@google-cloud/billing` 6.0.0, TypeScript 7.0.2 | Python; Node.js 24 in a second codebase; temporary Node.js 24 downgrade | Accepted (D-66) | One Functions package supports the D-66 2nd gen Pub/Sub trigger and the D-63 temporary 1st gen Auth trigger. Runtime debt is tracked only by TD-01. |
| Cloud runtime CI identity | GitHub OIDC, Workload Identity Federation, one-permission custom role, SHA-pinned Google actions | Service-account key; local-config-only check | Accepted (D-66) | Provider admission and the service-account IAM binding both restrict the immutable repository identity; CI can only read the deployed function runtime. |
| Firebase App Check | App Attest on iOS, Play Integrity on Android, debug providers in local/CI builds only | No App Check; monitoring-only integration | Accepted (D-67) | Authentication and Firestore enforcement is required before any build leaves local development. |
| Coverage | Kover | JaCoCo, none | Accepted (D-18) | Thresholds enforced in CI. |
| Android background work | WorkManager | foreground-only sync | Accepted for Phase 3 | Trigger only: it calls `SyncController.requestSync(reason)` and carries no scheduling policy. |
| iOS background work | BGTaskScheduler | foreground-only sync | Accepted for Phase 3 | Same constraint; a single task identifier. |
| Connectivity | `ConnectivityObserver` behind a common interface | Ktor-only detection, platform-only direct usage | Accepted | Injected, not `expect`/`actual` in public API. |
| Localization | Native Android/iOS resources | shared resource library | Accepted (D-20) | Native resources because UI is native. |
| Architecture checks | Konsist + custom Gradle check | custom only, dependency-analysis plugin | Accepted (D-16) | See D-16. |
| Charts | None for MVP | Vico, Swift Charts | Rejected for MVP | Advanced charts are out of scope. |
| Image loading | Coil | SDWebImage, platform-native | Deferred (D-12) | Only if a story requires it. |
| HTTP client | Ktor | Retrofit, URLSession | Deferred (D-11) | Only with an approved API implementation story. |
| Secrets/config | Platform Firebase config files and CI secrets | committed secrets | Accepted | See `docs/SECURITY.md` for the allowlist and denylist. |
| Static analysis | ktlint + detekt + Android lint | none, Spotless-only | Accepted | Config files committed in Phase 0. Baseline suppression files are FORBIDDEN. |
| Local database | SQLDelight 2.3.2 | Room 3 KMP, Realm | Accepted (D-36) | SQL is the schema source; generated database types stay owned by `:core:database`. |
| SQLDelight AndroidX driver | `com.eygraber:sqldelight-androidx-driver` 0.2.1 | Official platform drivers, repository-owned adapter | Accepted (D-36) | Wraps `androidx.sqlite:sqlite-bundled`; restricted to `:core:database`, with async generation and multiplatform execution tests. |

## Ktor Decision

Ktor is not part of the initial MVP dependency set while Firebase is the selected database backend.

The architecture must still support a future migration path:

```text
RemoteSyncSource
  -> FirebaseRemoteSyncSource        MVP implementation
  -> HttpApiRemoteSyncSource         Future Ktor implementation
  -> SupabaseRemoteSyncSource        Future option
  -> AwsRemoteSyncSource             Future option
```

Adding Ktor requires a new implementation story and an ADR update. Until then, agents MUST NOT add Ktor dependencies.

## Firebase Decoupling Rule

Firebase is an implementation detail. These abstractions are mandatory:

- `AuthClient`
- `TokenProvider`
- `RemoteSyncSource`
- `AnalyticsTracker`
- `CrashReporter`

Firebase SDK, GitLive and native Firebase types are allowed only inside integration and wiring modules.

## Decisions Awaiting Owner Confirmation

No `Proposed` or `Pending` decisions are currently awaiting owner confirmation.

When a future decision is added with status `Proposed` or `Pending`, this section MUST contain a Markdown table with columns `ID`, `Area`, `Recommendation`, `Needed by` and `Consequence if unresolved`. A story named in `Needed by` MUST NOT start until its row is resolved.
