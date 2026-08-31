# Technical Plan - carApp MVP

> Derived document. It plans and explains; it does not create rules. Behaviour is normative in `docs/SPECIFICATION.md`, representation in `docs/CONTRACTS.md`, allowed technologies in `docs/DECISION_BOARD.md`. See `AGENTS.md` for authority and normative language.

## 1. Context

The project is greenfield. This plan closes the technical decisions needed to start implementation with multiple AI agents while keeping module boundaries, remote backup behavior, and quality gates explicit.

The selected architecture is Kotlin Multiplatform for shared logic and native UI per platform. The app is local-first, supports one active device per account in the MVP, and uses Cloud Firestore only as a backup and recovery replica, not the UI source of truth.

## 2. Closed Decisions

Decision IDs are owned by `docs/DECISION_BOARD.md`. This table mirrors its decision IDs and statuses and MUST stay identical; `contract-check` asserts that.

| ID | Decision | Choice | Status | Rationale |
|----|----------|--------|--------|-----------|
| D-0 | Backend | Cloud Firestore | Accepted | Fits the data model, avoids fixed Cloud SQL cost, provides client-ID idempotent writes and server timestamps. |
| D-1 | Local database | Room 3.0 KMP with `androidx.sqlite:sqlite-bundled` | Superseded | Replaced by `D-36` because the mandatory SQLite `CHECK` constraints could not be represented as one Room-generated schema. |
| D-2 | Swift interop | SKIE only in `:shared` | Superseded | D-58 retains SKIE and moves its application to the module that owns the exported framework. |
| D-3 | DI | Koin KMP | Accepted | Owner-selected DI. Runtime wiring is acceptable if Koin is constrained to composition and wiring. |
| D-4 | `fuelType` | Stored on `Vehicle` from day one, without electric/hybrid values in MVP | Accepted | Schema evolution is easier before users exist; selector is not part of MVP UI; electric/hybrid needs a future energy model. |
| D-5 | Firestore access | Firebase Firestore integration behind `RemoteSyncSource` | Accepted | Firebase is the initial database backend, fully decoupled so a future Ktor/API implementation can replace it. |
| D-6 | Firebase Auth | GitLive Auth 2.6.x behind `AuthClient` | Accepted | Consistent with the Firestore wrapper. Native UI obtains Google and Apple credentials. |
| D-7 | Navigation | Native per platform | Accepted | Compose Navigation and SwiftUI `NavigationStack`; no shared destination model. |
| D-8 | Presentation | Shared KMP state holders | Accepted | High KMP return with minimal native duplication. |
| D-9 | Firestore offline cache | Disabled | Accepted | The custom outbox is the offline strategy; two caches would create invalidation bugs. |
| D-10 | Metrics | Firebase Analytics behind `AnalyticsTracker` | Accepted | Aligns with the Firebase stack while keeping analytics replaceable. |
| D-11 | HTTP/API client | Ktor deferred | Deferred | Reserved for a future API-based remote implementation. |
| D-12 | Image loading | Coil if ever needed | Deferred | Prevents agents from choosing competing loaders. |
| D-13 | Firestore location | `europe-west1` | Accepted | Firestore is a backup and recovery replica only; the local database is the source of truth. The location is immutable after database creation. |
| D-14 | Firebase project topology | one development project plus emulator now; separate production project before release | Accepted | Keeps development setup small while retaining emulator-only CI. Production project creation and its ID are deferred until release preparation. |
| D-15 | Logging implementation | Kermit behind `Logger` | Accepted | `Logger` is needed from Phase 0; the abstraction stays mandatory either way. |
| D-16 | Architecture checks | Konsist for package rules, custom Gradle check for module rules | Accepted | Gradle cannot express intra-module package rules. |
| D-17 | Flow testing helper | Turbine | Accepted | Confirm compatibility during version pinning. |
| D-18 | Coverage | Kover with thresholds | Accepted | Makes "high coverage" a pass/fail criterion. |
| D-19 | Result type | `Outcome<T, E>` in `:core:common` | Accepted | `kotlin.Result` has one type parameter; Arrow is out of scope. |
| D-20 | Localization | Native resources, no user-facing text in `UiState` | Accepted | UI is native; shared code has no resource bundle. |
| D-21 | Crash reporting | Firebase Crashlytics behind `CrashReporter` in Phase 4 | Accepted | Not needed before release hardening. |
| D-22 | Application identifiers | `docs/identifiers.md` | Accepted | Store identifiers are effectively irreversible; the production Firebase project ID is deferred by `D-14`. |
| D-23 | Account deletion execution | Firebase Admin server operation | Accepted | Store deletion compliance requires physical remote purge, while mobile clients must keep `allow delete: if false`. |
| D-24 | Module Android namespaces | Derived from the Gradle module path | Accepted | AGP 9 requires a unique namespace per module and 17 modules remain to be created; a derivable rule removes 17 identifier decisions and lets a convention plugin compute the value. |
| D-25 | `targetSdk` policy | Independent of `compileSdk` | Accepted | `compileSdk` is forced by the Compose BOM and only decides which APIs compile; `targetSdk` is the runtime contract the app opts into and is a behavioural decision. |
| D-26 | Monetary golden values | Correct the contradictory golden row and add a real HALF_UP round-up row | Accepted | The formula is authoritative and the row was arithmetically wrong; a bare correction would have removed the round-up coverage. |
| D-27 | `testAppGraphDependencies` ownership | Built by `E0-07`, not `E0-03` | Accepted | Keeps the Phase 0 module set absolute and writes the factory once against the complete member list. |
| D-28 | Feature-layer package rules | Implemented with Konsist in `E1-07` | Accepted | Konsist arrives when it has something to check; a dedicated module would not be in the canonical inventory. |
| D-29 | Contract type declarations | A type may be declared in `§20` or inline in the section that owns it | Accepted | Nothing was ambiguous for an implementer, and moving nine declarations would separate each from the rules that constrain it. |
| D-30 | Walking skeleton position | `E0-07` is the second story of Phase 1, after `E1-01` | Accepted | Avoids an exception in the Phase 0 module set, and gives `E0-07` a real local-database implementation to exercise. |
| D-31 | Branch protection | `main` requires all nine CI checks of `§18` | Accepted | A later edit to add the two placeholder checks is easy to forget, and they are the two nobody watches. |
| D-32 | Development Firebase project ID | `davidruiz-carapp-dev` | Accepted | Google Cloud project IDs are globally unique and `carapp-dev` was taken; the owner chose the replacement. |
| D-33 | Repository visibility and branch protection | Stay private; apply the `D-31` protection in the same change that makes the repository public or moves it to a plan that allows it | Superseded | No cost and no premature publication, at the price of an advisory-only CI; the decision carries an explicit trigger rather than a follow-up note. |
| D-34 | Repository visibility and branch protection | Repository is public; the `D-31` branch protection is active | Accepted | Resolves two blockers at once: `§18` branch protection was impossible on a private Free-plan repository, and metered Actions minutes made the macOS jobs unaffordable. |
| D-35 | CI job topology | `shared-tests` and `ios-simulator-build` stay separate jobs | Accepted | Merging them would worsen wall-clock, hide the native tests behind a check named for the iOS build, and stop the tests running whenever `xcodebuild` fails. |
| D-36 | Local database implementation | SQLDelight 2.3.2 with AndroidX bundled SQLite 2.7.0 through `sqldelight-androidx-driver` 0.2.1 | Accepted | Preserves exact SQLite constraints and UPSERT semantics on Android and iOS while retaining `minSdk 26`. |
| D-37 | Kotlin/Native iOS targets | Support `iosArm64` and `iosSimulatorArm64`; remove `iosX64` | Accepted | The shipped device target and the Apple Silicon simulator retain the bundled SQLite stack; the already-unlinked Intel simulator target would force a divergent driver. |
| D-38 | Database-owned mutation strategy | Kotlin/SQLDelight `DatabaseMutations` transaction facade | Accepted | Captures pre-write state for the exact recompute set, keeps writes atomic and emits reliable SQLDelight invalidations; direct generated entity mutations outside `:core:database` are rejected. |
| D-39 | Walking-skeleton data slice | Minimal valid `Vehicle` slice; edit the vehicle name | Accepted | Uses the final closed schema without pulling the complete vehicle domain and data stories into E0-07. |
| D-40 | Firestore-rule sequencing | E3-01 before E0-07 | Accepted | Real client traffic starts only after the complete reviewed Firestore rule set and emulator suite exist. |
| D-41 | Development Android Firebase certificate | Current local debug certificate plus debug application ID | Accepted | Restricts the public client key without creating another development keystore secret. |
| D-42 | Provider-proof sequencing | E3-06 before E3-01 before E0-07 | Accepted | Makes the required decoupling check executable before the first provider module appears. |
| D-43 | Provider-exclusion control | Canonical settings selected by `carapp.excludeFirebaseProviders=true` | Accepted | Keeps the local and CI proof on one Gradle graph definition. |
| D-44 | Provider-module registry | Explicit canonical paths, conditionally included when directories exist | Accepted | Supports a non-vacuous fixture before provider modules exist and rejects silent filesystem discovery. |
| D-45 | Provider-decoupling platforms | Android host plus `iosSimulatorArm64` on macOS | Accepted | Proves provider-free compilation and tests on JVM and Kotlin/Native without renaming the protected check. |
| D-46 | Firestore rules test stack | Exact Node 22 and official Firebase emulator packages with `node:test` | Accepted | Provides supported auth mocking and a production-safe emulator harness without another test framework. |
| D-47 | Firestore rules CI placement | Named emulator step inside `contract-check` | Accepted | Keeps rule evidence mandatory under the existing protected topology. |
| D-48 | Firestore client-cache configuration ownership | E0-07 owns the first executable client configuration | Accepted | Keeps E3-01 focused on rules and avoids premature provider/native linking. |
| D-49 | MVP Firestore schema-version rule | Exact remote `schemaVersion == 1` | Accepted | Preserves the closed MVP schema and defers rollout sequencing to the story that introduces a new schema. |
| D-50 | Firestore first-page cursor | Timestamp-only `startAt(overlapSince)` | Accepted | Works with the pinned Firebase SDK and includes every document at the overlap boundary; later pages retain the full timestamp/document-ID cursor. |
| D-51 | npm install-script policy | Repository-wide `ignore-scripts=true` | Accepted | Makes clean installs deterministic and prevents unnecessary transitive lifecycle execution. |
| D-52 | Firebase CLI audit residual | Retain 15.28.1 with a documented moderate dev-only residual | Accepted | The affected paths do not execute in the emulator harness or ship in the app; forced alternatives violate the selected stack or upstream compatibility. |
| D-53 | Development Firebase app provisioning | Separate Android and iOS debug apps with restricted Firebase-provisioned keys | Accepted | One application-restriction type is allowed per key, so platform isolation is required before public configuration files are committed. |
| D-54 | Development Firebase configuration isolation | Debug-only platform configuration; release fails closed without production configuration | Accepted | Prevents accidental release traffic to the development backend while retaining reproducible restricted debug setup. |
| D-55 | Walking-skeleton staged ownership | Final modules and public contracts now; real Vehicle slice only | Accepted | Keeps the complete Swift ABI gate early while later stories retain ownership of full feature, auth and sync behavior. |
| D-56 | Test app graph factory module | Factory in `:shared:testing`; graph contract in `:shared`; generic fakes in `:core:testing` | Accepted | Preserves the application-to-core dependency direction and exposes reusable KMP test support from `commonMain`. |
| D-57 | Android Firebase configuration plugin | Google Services Gradle plugin 4.5.0 | Accepted | Uses the current stable processor for the debug-only Firebase configuration and keeps its version centralized. |
| D-58 | iOS framework composition ownership | `:composition:ios` owns the single `Shared` framework and composes `:shared` with `:wiring:firebase` | Accepted | Avoids a `:shared` to wiring cycle, preserves provider-free graph tests and keeps one Kotlin/Native runtime. |
| D-59 | `AppProviders` port shape | Explicit typed properties except `isDebugBuild` | Accepted | Keeps construction compile-time checked and makes `buildAppGraph` the sole owner of the build-mode flag. |
| D-60 | Anonymous identity retention and portability | Device-bound until linked; native 30-day Firebase cleanup | Accepted | Avoids an unsupported anonymous recovery promise and bounds abandoned-account retention. |
| D-61 | Account-linking collision precedence | Current anonymous-session data wins after destructive confirmation | Accepted | Preserves the current device snapshot through an idempotent, resumable replacement flow. |
| D-62 | Anonymous sign-in benefit reminders | Fixed days 1, 3, 8 and 18 with highest-due collapse | Accepted | Informs before cleanup without replaying a backlog of prompts. |
| D-63 | User-data cleanup implementation | Owned idempotent service plus one temporary 1st gen Auth deletion trigger | Accepted | Avoids the externally dated Firebase Extensions management sunset; the Admin collision path calls the service directly. |
| D-64 | Anonymous lifecycle delivery | Split work across E0-07, E2-02, E2-04, E2-07, E3-10, E3-11 and E3-12 | Accepted | Keeps story PRs reviewable and runs recovery evidence only after permanent auth and sync exist. |
| D-65 | Firebase Apple SDK compatibility pin | Firebase Apple 11.8.0 exactly with GitLive 2.6.0 | Accepted | Keeps the native Apple SDK aligned with the exact version used to generate GitLive's cinterop bindings; both pins move together. |
| D-66 | Development cloud cost containment | EUR 10 alerts-only budget plus project-local 2nd gen cutoff at 100% actual cost | Accepted | Bounds accidental development spend with an intentionally destructive response; production uses alerts and manual action. |
| D-67 | Firebase App Check enforcement | App Attest, Play Integrity and local/CI-only debug providers; enforce Auth and Firestore | Accepted | Billing turns anonymous-authentication abuse into a direct cost vector, so caller integrity enters MVP scope. |
| D-68 | Cloud Functions moderate advisory residual | Retain the official SDK graph while the affected UUID variants remain dynamically unreachable | Accepted | The finding is a production transitive, so CI exposes moderate reports, blocks high/critical and the acceptance expires into TD-01 review. |
| D-69 | Billing cutoff account privilege | Dedicated keyless Billing Admin identity plus billing-administration alert | Accepted | The personal billing account cannot host a custom role; visibility compensates for the necessary broad standard role. |
| D-70 | Billing cutoff delivery policy | No Pub/Sub retry; execution-error alert plus measured recurring budget notification | Accepted | Prevents a persistent failure from producing seven days of the recurring billable work the cutoff exists to stop. |
| D-71 | iOS simulator signing | Normal Xcode simulator signing with no committed developer team or account-specific credential | Accepted | Restores Keychain-backed Auth persistence while keeping simulator acceptance reproducible and release signing fail-closed. |
| D-72 | Billing cutoff acceptance trigger | Two controlled threshold events on the real topic, expected-versus-actual evidence and timed recovery | Accepted | Verifies the deployed destructive path and the already-disabled no-op without manufacturing billable usage. |
| D-73 | Cloud Functions artifact retention | One-day cleanup on the exact Functions Artifact Registry repository with observed deletion | Accepted | Keeps a short inspection window while bounding development storage cost and treating Git as the source of truth. |
| D-74 | iOS Keychain persistence acceptance | Test-only XCUITest bundle drives UI across a terminated and relaunched real app process | Accepted | Proves signed-app Keychain persistence without shared access groups, host hooks or manual interaction. |
| D-75 | Firebase standalone Kotlin/Native test exception | Derive exemptions from the transitive Native-test project graph rooted at the Firebase integration modules; current resolution is four modules | Accepted | Avoids a second Firebase dependency manager or experimental toolchain while a graph-derived exact-set guard and real-host acceptance keep the changing coverage loss explicit. |
| D-76 | Vehicle validation boundary | Pure validators consume immutable pre-write facts; E1-03 loads facts, validates and mutates in one transaction | Accepted | Extends the contractual `CalculateConsumption` functional-core pattern to writes; the transaction is the only local guarantee because remote duplicates must remain ingestible without a unique index. |
| D-77 | Fuel Entry validation boundary | Pure validators consume immutable pre-write facts and return canonical values; E1-06 loads facts, validates and mutates in one transaction | Accepted | Preserves atomic R-1 validation while keeping R-2 derivation and warning behavior deterministic and independently testable. |
| D-78 | Consumption invalidation precedence | Apply the explicit structural-first single-reason precedence | Accepted | Keeps the unconditional non-positive-distance guarantee reachable and preserves the canonical result and Swift projection shapes. |
| D-79 | Consumption repository-filter evidence | E1-06 proves production filtering; E1-05 proves the pure calculator does not filter | Accepted | Places the acceptance evidence with the implementation that owns it without substituting a fake or pulling database work forward. |
| D-80 | Consumption performance evidence | Standalone uninstrumented JVM benchmark plus optimized iOS device-test binary over shared data | Accepted | Preserves meaningful wall-clock evidence, keeps normal tests fast and leaves unavailable real-device evidence visibly pending. |
| D-81 | Fuel Entry earliest-date calendar | Subtract 20 calendar years from `vehicle.createdAt` with `Instant.minus(20, DateTimeUnit.YEAR, TimeZone.UTC)`, then clamp the result to the Unix epoch | Accepted | Implements literal calendar years with the accepted commonMain library and keeps the persisted fact at or after the Unix epoch. |
| D-82 | Fuel Entry odometer inconsistency derivation | Database-owned neighbour-or-initial-odometer predicate with a missing-Vehicle fallback | Accepted | Aligns persistence with validation and keeps confirmed warnings in local and outbox snapshots without weakening the sole-writer boundary. |
| D-83 | Fuel Entry bounded projection window | Descending inner limit over each canonical ordering, followed by the canonical ascending outer order | Accepted | Retains the newest/highest rows under the 5,000-row memory ceiling while preserving deterministic list and calculation order. |
| D-84 | Android Vehicle UI support stack | Compose Navigation 2.9.8, instrumented Compose UI tests and a SHA-pinned emulator CI job | Accepted | Runs the real Android Vehicle creation flow with stable AndroidX tooling. |
| D-85 | Vehicle presentation ownership | Vehicle presentation in `:feature:vehicle`; shared UI primitives in `:core:common` | Accepted | Enforces the feature boundary without allowing presentation to depend on `:core:sync`. |
| D-86 | Kotlin and Swift graph separation | Hidden Kotlin `AppGraph`, wrapped by `SwiftAppGraph` | Accepted | Separates caller-owned Kotlin scopes from cached Swift-owned child scopes without leaking Kotlin-only APIs. |
| D-87 | Vehicle editability projection | Reactive `VehicleEditFacts` projection | Accepted | Re-emits after Vehicle or active Fuel Entry changes while write validation stays authoritative. |
| D-88 | Pre-E3-03 sync-status staging | Direct restore plus constant `Idle` until E3-03 | Accepted | Avoids a provisional second source of sync truth. |
| D-89 | Local database lifetime ownership | `DatabaseFactory` returns an idempotently closeable `DatabaseHandle` | Accepted | Keeps the SQL driver in `:core:database` and gives each application graph explicit ownership of one connection. |
| D-90 | Swift holder release and Vehicle creation completion | Keyed Swift cache release plus separate `savedVehicleId` | Accepted | Bounds Swift-owned scopes and prevents a successful creation form from silently becoming an editor. |
| D-91 | Stable Swift names for exported common enums | Exact Kotlin-matching `@ObjCName` annotations | Accepted | Prevents framework export configuration from renaming three already exported common enums. |

Do not use GitLive 3.0 alpha during the MVP. Do not add Ktor during the MVP unless a new ADR introduces an HTTP API implementation. Account deletion hard deletes use the `D-23` Firebase Admin server operation, not a client Firestore exception.

## 3. Module Architecture

This inventory mirrors the canonical module list in `docs/CONTRACTS.md §1.1`; the descriptions are explanatory only.

```text
build-logic/                    convention plugins
gradle/libs.versions.toml       single source of dependency versions

:core:model                     pure models, Money, scaled value classes
:core:common                    AppClock, UuidGenerator, DispatcherProvider, Outcome, AppError,
                                OwnerContext, Logger, LocaleProvider, ConnectivityObserver, backoff,
                                named constants (docs/CONTRACTS.md §20.0.1). Depends on :core:model.
:core:database                  SQLDelight schema, queries, migrations, platform builders, read-model invariants
:core:auth                      AuthClient, TokenProvider, AuthState
:core:sync                      Outbox, cursor, backup/recovery engine, SyncController, RemoteSyncSource
:core:analytics                 AnalyticsTracker and the closed AnalyticsEvent hierarchy
:core:crash                     CrashReporter abstraction and no-op implementation
:core:testing                   generic fakes, builders, in-memory remote, deterministic simulator

:integration:firebase-auth      Firebase Auth implementation
:integration:firebase-firestore Firestore RemoteSyncSource implementation
:integration:firebase-analytics Firebase Analytics implementation
:integration:firebase-crashlytics Firebase Crashlytics implementation, Phase 4

:feature:vehicle                domain/data/presentation packages
:feature:fuel                   domain/data/presentation packages
:feature:session                onboarding, auth, settings packages

:shared                         provider-free shared graph, Swift facade, state holders and models
:shared:testing                 KMP testAppGraphDependencies factory, consumed from commonTest only
:wiring:firebase                composition root that names Firebase integrations
:composition:ios                sole Shared framework producer and iOS composition root
:androidApp                     Android host app
iosApp/                         SwiftUI host app
firestore/                      rules and indexes
```

Each feature is one Gradle module. Layer separation is enforced by package-level source analysis, not by three Gradle modules per feature.

`:core:database` is a **shared-write module**: it owns the SQLDelight schema, typed queries and migrations for every feature, and it owns the read-model invariants of `docs/CONTRACTS.md §3.1`. A story that changes the schema MUST bump the database version, add a committed `.sqm` migration and add a populated previous-version migration test in the same PR. Only one story at a time may modify it; the handoff MUST declare it. If two implementation stories need this module concurrently, the optional `database-lock` check of `docs/CONTRACTS.md §18` uses `core/database/.story-lock` to make ownership explicit.

## 4. Dependency Rules

| Area | Allowed | Forbidden |
|------|---------|-----------|
| `:core:model` | Kotlin stdlib, coroutines, `kotlinx-datetime`, `kotlinx.serialization` | platform APIs, Firebase, SQLDelight, SQLite, Koin, Ktor, **`:core:common`** |
| `:core:common` | `:core:model`, plus the same libraries as `:core:model` | platform APIs, Firebase, SQLDelight, SQLite, Koin, Ktor |
| feature `domain` | `:core:model`, `:core:common` | Android, iOS, Firebase, GitLive, Koin, SQLDelight, SQLite, Ktor, own `data`, own `presentation` |
| feature `data` | own `domain`, `:core:model`, `:core:common`, `:core:database`, `:core:sync` | `:integration:*`, `:core:auth`, other features |
| feature `presentation` | own `domain`, `:core:model`, `:core:common` | own `data`, other features |
| `:core:sync` | `:core:model`, `:core:common`, `:core:database` | `:integration:*`, `:core:auth`, features |
| `:core:database` | `:core:model`, `:core:common`, SQLDelight, SQLite | `:integration:*`, features, `:core:sync` |
| `:core:auth` | `:core:model`, `:core:common`, coroutines, `kotlinx.serialization`, `kotlinx-datetime` | platform APIs, Firebase, GitLive, SQLDelight, SQLite, Koin, Ktor, `:integration:*`, features |
| `:core:analytics` | `:core:model`, `:core:common` | platform APIs, Firebase, GitLive, SQLDelight, SQLite, Koin, Ktor, `:integration:*`, features |
| `:core:testing` | every `:core:*` module plus test libraries (Turbine, `kotlin.test`) | `:integration:*`, `:wiring:*`, `:feature:*`, platform APIs in `commonMain` public API (platform APIs are permitted only in `expect`/`actual` test doubles, per `docs/CONTRACTS.md §15.1`) |
| `:core:crash` | `:core:common` | platform APIs, Firebase, GitLive, Koin, Ktor, integrations, features |
| `:integration:*` | `:core:*` interfaces, provider SDKs | features, `:shared` |
| `:shared` | `:core:*`, `:feature:*`, `:shared:testing` in test-support and SPM metadata configurations only | `:integration:*` |
| `:shared:testing` | `:shared`, `:core:testing`, test libraries | `:integration:*`, `:wiring:*`, `:feature:*`, platform APIs in `commonMain` public API |
| `:wiring:firebase` | `:integration:*`, `:shared` graph, Koin | product logic |
| `:composition:ios` | `:shared`, `:wiring:firebase`, `:feature:vehicle`, `:core:common` | product logic, direct `:integration:*`, a second framework runtime, any other feature or core module |

`:core:model` is the vocabulary and `:core:common` is the plumbing that speaks it, so the dependency runs `:core:common` -> `:core:model` and never the reverse. The direction is load-bearing rather than stylistic: `OwnerContext`, `LocaleInfo` and `MinorUnits` live in `:core:common` (`docs/CONTRACTS.md §20.3`) and refer to `OwnerId` and `CurrencyCode`, which live in `:core:model` (`§20.0`). Because the architecture check is generated from this table, leaving the edge undeclared would either fail the build on a legal dependency or leave the rule unenforced.

Feature `data` cannot depend on `:core:auth`, so the current owner reaches repositories through `OwnerContext` (`:core:common`), implemented by `:core:auth` and bound in wiring. An architecture rule asserts that no feature module references `AuthClient`.

"Platform API" in this table means direct references to Android packages (`android.*`, `androidx.*`), Android-only `java.util.concurrent` types, Apple/native packages (`platform.Foundation`, `platform.UIKit`, `platform.darwin`, `kotlinx.cinterop.*`) or any direct `expect`/`actual` boundary not allowed by `docs/CONTRACTS.md §15.1`. The architecture fixtures MUST include at least one rejected platform API reference for `:core:crash` and one for `:core:testing` (a platform API used in the `commonMain` public surface, not in a permitted `expect`/`actual` test double).

The three rows added for `:core:auth`, `:core:analytics` and `:core:testing` close the previous gap: every module in the canonical inventory of `docs/CONTRACTS.md §1.1` now has an enforceable dependency rule. `:core:auth` and `:core:analytics` are provider-free abstractions, so they forbid the same set of integrations and platform APIs as `:core:crash`; `:core:auth` additionally forbids SQLDelight and SQLite because auth owns no persistence. `:core:testing` is the only `:core:*` module allowed to depend on every other `:core:*` module, because it must provide generic fakes for every `AppGraphDependencies` abstraction; it remains forbidden from reaching `:shared`, integrations, wiring or features, and its platform-API permission is restricted to `expect`/`actual` test doubles so its `commonMain` public surface stays Kotlin-pure. `:shared:testing` performs the application-specific composition and exposes it to consumer `commonTest` source sets (`D-56`).

"Product logic" in `:wiring:firebase` is defined checkably: every top-level declaration there MUST be a Koin `Module`, a factory returning an abstraction, or a platform initialiser. No use cases, repositories, mappers, validation or business `expect`/`actual`. `:integration:firebase-*` modules MAY declare Koin `Module` declarations for their own bindings, but MUST NOT reference `createAppGraph`; only `:wiring:firebase` may aggregate those bindings into the final graph.

The architecture check MUST fail the build with a rule-specific message, and the check configuration is generated from this table so the two cannot drift.

`D-38` makes `DatabaseMutations` the only synchronized-entity write boundary consumed by
`:feature:*`, `:core:sync` and wiring. Generated SQLDelight entity-mutation functions remain an
implementation detail of `:core:database`; an executable source rule rejects direct calls from
every other module. Read queries and outbox/sync-engine control operations are not entity
mutations and remain available to their owning stories.

## 4.1 Contractual Guardrails

`docs/CONTRACTS.md` defines implementation contracts that agents MUST NOT reinterpret:

- Canonical types (§20), scaled integer formats and exact monetary arithmetic (§2).
- Local row and remote document schemas, and database-owned invariants (§3).
- Ordering rules for validation and for consumption (§4).
- Validation, normalisation and warning semantics (§5).
- Error taxonomy, error codes, and the `RemoteError` to `SyncError` mapping (§6).
- Sync state machine, outbox payload, cursor, backoff, trigger constants and cycle ordering (§7–§9).
- `RemoteSyncSource`, auth, app graph, repository, use case and presentation contracts (§10–§14).
- Allowed and forbidden `expect`/`actual` boundaries and the Swift-facing surface (§15).
- Firestore rule and query contract (§16), analytics (§16.1), logging and privacy (§17).

Any change to those contracts is a human review gate and MUST update `docs/CONTRACTS.md` in the same change.

## 5. Provider Decoupling

`:shared` exposes a provider-free graph factory through an explicit port:

```kotlin
fun buildAppGraph(isDebugBuild: Boolean, providers: AppProviders): SwiftAppGraph
```

`AppProviders`, `AppGraphDependencies` and `buildAppGraph` are defined in
`docs/CONTRACTS.md §11.6`; they are hidden from the Swift-facing Objective-C header. The sole
exported `createSwiftAppGraph(isDebugBuild)` declaration lives in `:composition:ios`, constructs
providers through `:wiring:firebase` and delegates to `buildAppGraph`. `:composition:ios` produces
the only framework, keeps `baseName = "Shared"` and exports the state holders and models owned by
`:shared`. Only `:wiring:firebase` creates Firebase implementations. The executable decoupling
check is:

```text
Set carapp.excludeFirebaseProviders=true in the canonical Gradle settings.
Exclude every registered :integration:* module, :wiring:firebase and :composition:ios whose
directory exists.
Compile and test :core:*, :feature:* and :shared on Android host and iosSimulatorArm64 using
:shared:testing and :core:testing fakes through buildAppGraph.
```

The provider registry is explicit and closed (`D-44`); provider directories are never discovered
by scanning the filesystem. Registered modules whose directories do not exist create no Gradle
project. The required `provider-decoupling` CI job runs this proof on macOS (`D-45`).

## 6. Local Data Model

Synchronized entity control columns:

| Column | Meaning |
|--------|---------|
| `id` | Client-generated UUID primary key. |
| `ownerId` | Owner ID, or `LOCAL_OWNER` before authentication. |
| `updatedAt` | Local provisional timestamp. Never used for remote conflict arbitration. |
| `serverUpdatedAt` | `INTEGER NULL`, authoritative remote timestamp; `NULL` means never synced. |
| `deleted` | `INTEGER NOT NULL CHECK(deleted IN (0, 1))`, with `CHECK((deleted = 0 AND deletedAt IS NULL) OR (deleted = 1 AND deletedAt IS NOT NULL))`. |
| `deletedAt` | Tombstone timestamp. |
| `syncState` | Local state. Canonical values in `docs/CONTRACTS.md §7`. |
| `localRevision` | Incremented on each local edit to detect in-flight edits. |
| `localMutationSeq` | Monotonic database-local mutation order, shared across synchronized entity tables. |
| `schemaVersion` | Payload schema version. |

Tables: `vehicle`, `fuel_entry`, `user_settings`, `local_sequence`, `outbox`, `sync_cursor`, `quarantine`.

There is **no enforced foreign key** from `fuel_entry` to `vehicle`: sync can legitimately deliver an entry before its vehicle, and a constraint failure inside a pull transaction would stall the cursor permanently.

Outbox schema:

```sql
CREATE TABLE outbox (
  seq INTEGER PRIMARY KEY AUTOINCREMENT,
  entityType TEXT NOT NULL CHECK (entityType IN ('VEHICLE','FUEL_ENTRY')),
  entityId TEXT NOT NULL,
  payload TEXT NOT NULL,
  localRevision INTEGER NOT NULL,
  attemptCount INTEGER NOT NULL DEFAULT 0,
  nextAttemptAt INTEGER NOT NULL DEFAULT 0,   -- 0 means "due now"
  lastError TEXT,
  lastErrorCode TEXT,
  cycleId TEXT,
  UNIQUE(entityType, entityId)
);

CREATE INDEX idx_outbox_due ON outbox(nextAttemptAt, seq);
```

The `cycleId TEXT` column stores the `CycleId` (`§20.7`) of the sync cycle that last attempted the row, populated on every failed attempt. The sync engine reads it only for log correlation; it MUST NOT use it for retry or poison decisions (which read `lastErrorCode` only, per `§9.7`). An `E3-03` migration test MUST verify the column is populated on failure and NULL on success.

The outbox stores full snapshots; re-applying the same snapshot is idempotent. Retry decisions are made on `lastErrorCode`, never on `lastError` text. `lastError` is debug/UI context only and MUST NOT be read by the sync engine.

Outbox coalescing uses the statement defined in `docs/CONTRACTS.md §8`; the existing `seq` is preserved on conflict.

`local_sequence` is a single-row local control table used only to assign `localMutationSeq`. Local creates, updates and tombstone writes consume it; pull-applied remote writes and local-owner adoption do not. The value never leaves the local database.

```sql
CREATE TABLE local_sequence (
  id INTEGER PRIMARY KEY CHECK (id = 0),
  next INTEGER NOT NULL DEFAULT 1
);
```

The next value is assigned by incrementing the single row inside the caller's write transaction, for example with `UPDATE local_sequence SET next = next + 1 RETURNING next`. An equivalent `INTEGER PRIMARY KEY AUTOINCREMENT` helper table is allowed only if it provides the same no-reuse guarantee.

Quarantine schema:

```sql
CREATE TABLE quarantine (
  entityType TEXT NOT NULL CHECK (entityType IN ('VEHICLE','FUEL_ENTRY')),
  entityId TEXT NOT NULL,
  reason TEXT NOT NULL CHECK (reason IN ('UnsupportedSchemaVersion','MalformedPayload')),
  schemaVersion INTEGER NOT NULL,
  serverUpdatedAt INTEGER NOT NULL,
  rawJson TEXT NOT NULL,
  createdAt INTEGER NOT NULL,
  UNIQUE(entityType, entityId)
);
```

`sync_cursor` schema:

```sql
CREATE TABLE sync_cursor (
  entityType TEXT NOT NULL CHECK (entityType IN ('VEHICLE','FUEL_ENTRY')),
  lastServerUpdatedAt INTEGER NOT NULL,
  lastDocumentId TEXT NOT NULL,
  PRIMARY KEY (entityType)
);
```

`lastDocumentId` is `TEXT NOT NULL` because `docs/CONTRACTS.md §9.4` forbids `null` as a cursor component; the `RemoteCursor.INITIAL` sentinel is never stored as a row. An `E1-01` migration test MUST verify the constraint rejects an unknown `entityType`.

Future columns MUST NOT store provider credentials, auth tokens or unredacted SDK error objects.

SQLDelight configuration: committed `.sq` files are the canonical schema and query source, asynchronous generation and `verifyMigrations` are enabled, and system-SQLite linking is disabled for the bundled Native driver. Destructive schema recreation is FORBIDDEN. Every version bump ships a committed `.sqm` migration plus a test that migrates a populated previous-version database and asserts row preservation. Schema v1 is covered by create, constraint and close/reopen persistence tests on Android and iOS.

## 7. Firestore Design

Structure:

```text
users/{uid}/vehicles/{vehicleId}
users/{uid}/fuelEntries/{entryId}
```

Settings are device-local and have no remote document.

Rationale:

- User-scoped subcollections make authorization straightforward.
- Reads are naturally owner-scoped.
- Delta pull uses the automatic single-field `updatedAt` index; `firestore/firestore.indexes.json` exists and stays empty until a composite index is required.

The normative security rule shape, including per-field range validation and the `allow delete: if false` tombstone policy, is in `docs/CONTRACTS.md §16`. It MUST NOT be restated here in a weaker form.

## 8. Sync Engine

The engine lives fully in `commonMain`. Platform APIs only trigger it; they are not used inside the core engine, and they carry no scheduling policy of their own.

### Push

```text
1. SELECT outbox rows WHERE nextAttemptAt <= now ORDER BY seq LIMIT 50.
2. Partition into the canonical dependency groups of `docs/CONTRACTS.md §8`, preserving `seq` within each group.
3. For each row, write doc(users/{uid}/{entityType.collection}/{id}) with serverTimestamp().
4. Take serverUpdatedAt from the write result, or re-read the document.
5. In a local transaction:
   - if outbox.localRevision == entity.localRevision:
       delete outbox row, set syncState = SYNCED, set serverUpdatedAt
   - else:
       keep outbox row, update only serverUpdatedAt
6. Retry network and token failures with backoff, up to MAX_RETRYABLE_ATTEMPTS.
7. Mark validation and permission failures as poisoned.
```

### Pull

```text
For entityType in [VEHICLE, FUEL_ENTRY]:
  1. cursor = sync_cursor[entityType] (created lazily as RemoteCursor.INITIAL)
  2. overlapSince = max(0, cursor.lastServerUpdatedAt - 30s)   // overlap applied once per cycle
  3. Query where updatedAt >= overlapSince
       orderBy updatedAt ASC, documentId ASC
       first page: startAt(overlapSince)
       later pages: startAfter(pageCursor.lastServerUpdatedAt, pageCursor.lastDocumentId)
       limit 200
  4. Apply the page in one local transaction.
     - quarantine documents whose schemaVersion is unsupported or whose supported-version payload is malformed
     - skip entities that have an outbox row
     - otherwise apply if remote.updatedAt > local.serverUpdatedAt, or local was never synced
  5. pageCursor = (lastApplied.updatedAt, lastApplied.documentId); advance the cursor.
  6. If the page was full and the anchor did not strictly advance, fail with ConflictUnresolved.
  7. Repeat while the page is full.
```

### Recovery Guarantees

- Local mutations eventually reach the outbox, except while the owner is `LOCAL_OWNER`.
- Push is idempotent by client-generated document ID.
- Server `updatedAt` creates authoritative ordering; the local `updatedAt` never arbitrates.
- `(updatedAt, documentId)` provides a deterministic total order over the pull stream.
- Pull overlap prevents silent cursor loss; `startAt(overlapSince)` includes every document at the first-page boundary; `startAfter` on the previous page's timestamp/document-ID cursor prevents re-reading the same page forever.
- Tombstones are regular LWW documents.

## 9. Backup and Recovery Tests

Required tests for `:core:sync`:

1. Offline write is backed up after connectivity returns.
2. Ambiguous response retry does not duplicate records.
3. A clean recovery device restores backed-up vehicles and fuel entries for the authenticated owner.
4. Exact `updatedAt` tie paginates deterministically in the pull stream order.
5. Tombstone wins over older update.
6. Local edit during in-flight push is not lost, and the state machine follows `SYNCING -> SYNCING -> PENDING`.
7. Pull overlap prevents missing a document with a timestamp before the cursor.
8. Device clock one hour ahead does not win all conflicts.
9. First sync of 1,000 records is paginated correctly.
10. After `MAX_RETRYABLE_ATTEMPTS` consecutive **non-connectivity** retryable failures the row becomes `FAILED_POISONED`, and `SyncController.retryFailed()` resets `attemptCount` and revives it.
11. More than 200 documents sharing one timestamp inside the overlap window paginate to completion instead of looping.
12. A fuel entry arriving before its vehicle during recovery is persisted, hidden from the UI, and later becomes visible without stalling.
13. Recovery data containing two vehicle documents with the same name restores both vehicles without a local uniqueness constraint failure.
14. Local owner adoption on a populated `LOCAL_OWNER` database enqueues every row exactly once, is idempotent, and inserts outbox rows in dependency-group order and then by `localMutationSeq ASC, id ASC`.
15. A document with an unsupported higher `schemaVersion` is quarantined and does not block cursor advance.
16. A supported-version document with malformed payload is quarantined with `MalformedPayload`, is not applied to product tables and does not block cursor advance after quarantine is committed.
17. Backoff with an injected jitter source produces deterministic, capped delays.
18. A device offline for longer than the full backoff series keeps every row in a retryable state, poisons nothing, reports `Pending` rather than `Failed`, and backs up once connectivity returns. This is the regression test for `docs/CONTRACTS.md §9.7`: with the ceiling and the backoff constants alone, rows would poison after roughly 17 minutes offline.

Add a deterministic simulation with a fixed seed that interleaves local edits, push, recovery pull, network failure, duplicate delivery and lost responses, asserting that a clean recovery client can restore the source client's backed-up data.

## 10. Implementation Phases

### Phase 0 - Foundations

Status: complete.

KMP bootstrap, Gradle convention plugins, core modules, quality tools, CI, architecture checks, contract check, ADRs, version matrix, identifiers.

Entry condition: every `Proposed` decision in `docs/DECISION_BOARD.md` that a Phase 0 story depends on has been confirmed by the owner.

### Phase 1 Opening Gate - Walking Skeleton

Status: complete through E1-01 and E0-07, including D-73 acceptance evidence.

One end-to-end vertical slice on both native application paths: native UI, shared state holder,
SQLDelight, Firestore and real anonymous auth under the same retained Firebase Auth session, plus
validation of the Swift-facing surface constraints. The gate does not transfer an anonymous
credential between devices; permanent-account Android-to-iOS recovery is owned by E3-12 (`D-64`).

`D-36` resolved the database fallback before this gate: the walking skeleton exercises the accepted SQLDelight AndroidX bundled driver on both application paths.

### Phase 1 - Local Persistence

Status: active. E1-07 is complete; E1-08 is next and E1-08 through E1-10 remain. D-80 leaves the
manual optimized real-iPhone consumption measurement explicit for E4-03.

Local database, vehicle and fuel domains, repositories, consumption calculation, settings persistence, Android UI, iOS UI.

### Phase 2 - Authentication

Status: planned; all Phase 2 stories remain.

Auth abstractions, Firebase Auth integration, onboarding, local owner adoption, conversion,
anonymous retention notices, sign-out and account deletion.

### Phase 3 - Backend Backup and Recovery

Status: partially complete. E3-06 and E3-01 are complete; every other Phase 3 story remains.

Firestore rules and emulator tests, Firestore integration for the development project, backup and
recovery engine, app graph wiring, repository wiring, backup status UI, tombstone purge, account
deletion and anonymous cleanup operations, permanent-account cross-device recovery proof, and
provider decoupling proof.

### Phase 4 - MVP Hardening

Status: planned; E4-01 through E4-04 remain.

Settings UI, accessibility, localization, performance, release builds, Crashlytics integration, store requirements.

## 11. Risks and Mitigations

| Risk | Probability / Impact | Mitigation |
|------|----------------------|------------|
| iOS toolchain friction | High / High | Walking skeleton in the first week, macOS CI from the first PR, SPM integration, pinned Kotlin/SKIE/Xcode versions. |
| Swift-facing API shape rejected by the Obj-C export | High / Medium | `docs/CONTRACTS.md §15.3` constraints validated in `E0-07`, plus a committed header golden file. |
| Backup and recovery bugs | High / Critical | Common engine, in-memory remote, deterministic simulation, required tests, debug screen for outbox, cursors and backup state. |
| SQLDelight AndroidX adapter maintenance or Native-link friction | Medium / Medium | Pin all three database components, keep the adapter confined to `:core:database`, compile and execute tests on Android and Kotlin/Native, and retain the official-driver alternatives in ADR-0037. |
| Firestore rule mistake | Medium / Critical | Emulator tests for owner isolation, anonymous access, server timestamp enforcement, hard-delete rejection and range validation. |
| Data loss at the `LOCAL_OWNER` boundary | Medium / Critical | Outbox suppressed before a real UID exists; adoption story with an idempotency test. |
| Orphaned anonymous data | Medium / High | D-63 idempotent deletion service, explicit data-location registry, native cleanup trigger and a direct Admin collision path. |
| Runaway development cloud cost | Low / High | D-66 EUR 10 alerts-only budget, actual-cost notifications, tested project-local cutoff and manual recovery runbook. Reporting delay means overshoot remains possible. |
| Billed anonymous-client abuse | Medium / High | D-67 App Check enforcement for Authentication and Firestore, while retaining Auth and closed Firestore Rules. |
| Moderate UUID advisory in the deployed Functions graph | Low / Medium | D-68 dynamic full-trigger reachability test, explicit advisory register, high/critical CI gate and expiring TD-01 review. |
| Broad billing-account role on the cutoff identity | Low / High | D-69 dedicated keyless identity with no product-data role, minimal project role and an alert on billing administrative changes. |
| Silent dropped cutoff event | Low / High | D-70 disables retries but alerts on every execution error and records the observed recurring budget publication cadence as the second-attempt bound. |
| Temporary 1st gen Auth trigger becomes inherited infrastructure | Medium / Medium | `TD-01` names the sole exception, exact migration surface, quarterly owner review and a contract allowlist. |
| Scope creep | Medium / Medium | Explicit out-of-scope list and review gate. |

## 12. Verification Strategy

Automated on every PR:

- Gradle build for Android and shared KMP modules.
- iOS simulator target and the `Shared` framework from `:composition:ios` build on macOS.
- ktlint, detekt.
- Unit tests with Kover thresholds.
- Architecture rule checks, each with a failing fixture test.
- Contract check (`docs/CONTRACTS.md §18`).
- Backup and recovery tests when remote backup exists.
- Firestore emulator tests when rules exist.
- Provider decoupling check once integrations exist.

Manual at phase gates:

- Offline first launch, create vehicle and fuel entries with no connectivity at any point, then connect and verify adoption and remote data.
- Two-device edit conflict converges.
- Normal anonymous linking preserves the UID and data.
- Credential collision cancellation is non-destructive; confirmed replacement makes the current
  anonymous snapshot win and resumes safely after interruption.
- Permanent-account recovery succeeds across Android and iOS; no anonymous cross-device recovery
  promise appears.
- Device clock skew does not corrupt sync.
- TalkBack and VoiceOver for critical flows.

## 13. Tracked Technical Debt

### TD-01 - Authentication deletion trigger pinned to Cloud Functions 1st gen

`onAnonymousUserDeleted` is pinned to Cloud Functions 1st gen **solely** because
`auth.user().onDelete` has no Cloud Functions 2nd gen equivalent, and for no other reason. The
callable collision path and every other function use 2nd gen. No new 1st gen function may be added
to this project; `onAnonymousUserDeleted` is the only permitted exception.

The single `functions/` package is pinned to Node.js 22 only because it must host this temporary
1st gen exception and the project's 2nd gen functions without a second runtime or deployment path.
This is not separate technical debt: the runtime and Auth trigger move together in the same TD-01
migration and recurring review. Node.js 22 deprecates on **2027-04-30** and is decommissioned on
**2027-10-31**. Decommission is a hard deadline. If generally available 2nd gen Authentication
deletion triggers do not exist in time, the owner MUST escalate rather than continue waiting:
either isolate only the Auth function on a supported runtime and formally accept the split, or
select a different cleanup mechanism through a superseding decision.

Exact migration surface once E3-10 and E3-11 create it:

| File or configuration | Affected declaration | Migration responsibility |
|-----------------------|----------------------|--------------------------|
| `functions/src/auth/onAnonymousUserDeleted.ts` | `onAnonymousUserDeleted` | Replace the `firebase-functions/v1` Auth deletion builder with the generally available 2nd gen Authentication deletion trigger. |
| `functions/src/index.ts` | `onAnonymousUserDeleted` export | Retain the public deployed function name while switching its implementation export. |
| `functions/test/contract/functionGenerationPolicy.test.ts` | sole-1st-gen allowlist | Remove the D-63 exception and require every exported function to use 2nd gen. |
| `functions/test/integration/anonymousCleanup.test.ts` | automatic-cleanup trigger coverage | Run the same deletion, idempotency and overlap assertions against the 2nd gen trigger. |
| `firebase.json` | Functions source/codebase deployment entry | Verify the existing deployment target deploys the migrated function; there is no permitted second codebase or hidden 1st gen deployment entry. |
| `functions/package.json` | `firebase-functions` dependency and Functions test scripts | Raise the pinned SDK only if the first GA 2nd gen Auth trigger requires it, then update `docs/versions-matrix.md` under the normal library-review gate. |
| `functions/package.json`, `firebase.json` | Node.js 22 runtime | Move the complete Functions package to a current supported runtime after the Auth trigger is 2nd gen; do not create a separate runtime migration story. |

`functions/src/deletion/dataLocationRegistry.ts`,
`functions/src/deletion/userDeletionService.ts` (`deleteUserData`) and
`functions/src/callable/deleteOrphanedAnonymousAccount.ts`
(`deleteOrphanedAnonymousAccount`) are intentionally generation-neutral or 2nd gen and MUST NOT be
rewritten as part of this migration. That boundary keeps the migration surface narrow and known in
advance.

The owner watches both the [Firebase release notes](https://firebase.google.com/support/releases)
and the [Cloud Functions Authentication trigger documentation](https://firebase.google.com/docs/functions/1st-gen/auth-events).
The concrete availability signal is a Firebase announcement and SDK documentation for generally
available Authentication user-deletion event triggers in Cloud Functions 2nd gen. Preview or
private-preview availability does not trigger migration.

Review owner: **David Ruiz**. The first recurring review is **2026-12-01** and repeats quarterly on
March 1, June 1, September 1 and December 1 until migration completes or the owner formally
re-accepts the constraint. Every completed review appends one dated row below; the next due date is
never left implicit.

The same review also owns the expiring D-68 acceptance for GHSA-w5hq-g745-h8pq; it does not create
a separate cycle. Each review MUST rerun the production dependency audit, the deployed-trigger
reachability test and the current-state assessment in `docs/SECURITY_ADVISORY_REGISTER.md`. An
immediate review is triggered by any high or critical finding, any expansion of this function into
Storage, or the availability of a compatible official update.

The same review also owns D-75; no separate review cycle is created. Each review MUST check both
[GitLive issue #499](https://github.com/GitLiveApp/firebase-kotlin-sdk/issues/499) for supported
transitive Apple linking and the status of
[Kotlin SwiftPM import](https://kotlinlang.org/docs/multiplatform/multiplatform-spm-import.html).
D-75 expires when either GitLive supports the standalone Kotlin/Native test link or SwiftPM import
is stable in a Kotlin release compatible with the pinned project stack. A GitLive release that
targets Firebase Apple 12.x or another successor also triggers a joint D-65/D-75 evaluation; the
native SDK pin and the test exception MUST NOT be reviewed as unrelated migrations.

| Review date | Owner | Outcome | Next review |
|-------------|-------|---------|-------------|
| 2026-08-25 (baseline) | David Ruiz | D-63 accepted; no generally available 2nd gen Authentication user-deletion trigger exists. TD-01 opened with one permitted 1st gen function. | 2026-12-01 |

Once 2nd gen Authentication user-deletion triggers are generally available, migration is scheduled
as its own backlog story. It MUST NOT be folded into an unrelated story. Closing TD-01 requires the
new story to migrate the trigger, remove the allowlist exception, pass the automatic-cleanup and
overlap tests, move the complete Functions package to a current supported runtime, deploy under the
retained function name, and append the final review outcome here. The deliberately temporary
Node.js 24 -> 22 -> current-runtime sequence is rejected; Node.js 22 is selected once and leaves
only when TD-01 closes.

## 14. Out of Plan

Maintenance expenses, advanced analytics, export, receipt images, odometer images, local or
on-device AI text recognition, OCR, fuel and maintenance reminders, operating-system notifications,
shared vehicles, widgets, wearables, web, Cloud Functions-mediated product read/write validation
beyond the `D-23` and `D-63` account identity and data-deletion operations, automatic
account merging, simultaneous multi-device use, active multi-device synchronization,
remote-database-as-source-of-truth operation, real-time Firestore listeners, remote settings
synchronization, platform settings sync or backup through Google Play services / Android backup /
iCloud, and electric or hybrid energy modelling. The foreground-only anonymous-account retention
notices selected by `D-62`, D-66 development billing containment and D-67 App Check enforcement
are in plan.
