# Implementation Backlog - Vehicle Expense Tracking MVP

> Derived document. Each story is a deliverable unit for one AI agent: closed scope, explicit dependencies, verifiable acceptance criteria. Behaviour is normative in `docs/SPECIFICATION.md`, representation in `docs/CONTRACTS.md`, allowed technologies in `docs/DECISION_BOARD.md`. See `AGENTS.md` for authority, normative language and human review gates.

Size guide: **S** up to half a day, **M** 1 to 2 days, **L** 3 to 5 days.

## Agent Conventions

- Read `AGENTS.md` first. It defines document authority, normative language, gates and the document map.
- No story is done without relevant tests, clean lint, acceptance criteria evidence, a handoff filled from `docs/templates/agent-handoff.md`, and an entry appended to `docs/PROJECT_LOG.md`.
- Do not implement anything listed as out of scope in `docs/SPECIFICATION.md §3.2`.
- Do not violate the dependency rules in `docs/SPECIFICATION.md §8.3` / `docs/TECHNICAL_PLAN.md §4`.
- Data model changes require migrations and migration tests.
- Monetary values never use `Float` or `Double`.
- A story whose decisions are still `Proposed` or `Pending` in `docs/DECISION_BOARD.md` is NOT Ready.
- Each handoff includes the ready check and acceptance evidence required by `AGENTS.md`.

## Phase 0 - Foundations

Goal: a project skeleton that compiles on both platforms and has enforceable architecture boundaries.

**Entry condition:** every decision needed by a Phase 0 story is resolved before that story starts. No Phase 0 story is currently blocked by an unresolved owner decision.

Phase 0 ships only `:core:model`, `:core:common`, `:core:analytics`, `:core:crash` and `:core:testing` from the core layer. `:core:auth`, `:core:database` and `:core:sync` are introduced by their later stories and MUST NOT be pulled into Phase 0 early.

### E0-00 - Owner Decision Closure - S

Status: completed on 2026-08-17 by the decision commits for `D-13` through `D-22`. Future implementation work starts at `E0-01` unless a new unresolved owner decision is introduced.

Turn the `Proposed` and Phase-0 `Pending` rows of `docs/DECISION_BOARD.md` into `Accepted` (or a different choice), and materialise the supporting documents.

Acceptance criteria:

- Every Phase 0 row in "Decisions Awaiting Owner Confirmation" is `Accepted`, `Rejected` or explicitly re-`Deferred`.
- `docs/identifiers.md` contains the final applicationId, bundle identifier, Kotlin namespace, display name, development Firebase project ID and Firestore location; the separate production Firebase project ID is explicitly deferred by `D-14`.
- One ADR exists per newly accepted decision.
- `docs/SPECIFICATION.md §12`, `docs/TECHNICAL_PLAN.md §2` and `docs/adr/README.md` mirror `docs/DECISION_BOARD.md` exactly.

Blocks: E0-01.

Human review required.

### E0-01 - KMP Project Bootstrap - M

Status: completed on 2026-08-19, PR #9. See `docs/handoff-E0-01.md`.

Create the KMP project with Android and iOS targets, Android host app, SwiftUI iOS host app, and `:shared` framework.

Acceptance criteria:

- Android debug app builds.
- iOS simulator app builds and shows text coming from `commonMain`.
- iOS imports the shared framework as `import Shared`; this is the canonical SPM module name from `docs/identifiers.md`.
- Build scripts use Kotlin DSL only.
- `gradle/libs.versions.toml` is the single source of dependency versions.
- Identifiers exactly match `docs/identifiers.md`; nothing is invented.
- Android `AndroidManifest.xml` and iOS entitlements contain no platform backup or settings-sync API surface.

Blocks: all other stories.

### E0-02 - Gradle Convention Plugins - M

Status: completed on 2026-08-21, PR #14. See `docs/handoff-E0-02.md`.

Create convention plugins for KMP libraries, features, Android application, Compose, local persistence and SKIE.

Acceptance criteria:

- Creating a new module requires no more than five lines in its `build.gradle.kts`.
- SKIE is applied only to `:composition:ios`, the framework producer selected by D-58.
- Test and Kotlin toolchain configuration is centralized.
- Plugins make future feature splitting possible without redesign.

### E0-03 - Base Core Modules - M

Status: completed. PR #15 delivered the Phase 0 scope; E0-05 later closed the Kover criterion and
E0-07 closed the test-factory criterion in `:shared:testing` under D-27 and D-56. See
`docs/handoff-E0-03.md`.

Create `:core:model`, `:core:common`, `:core:crash` and `:core:testing`, implementing the Phase 0 canonical types of `docs/CONTRACTS.md §20`.

Acceptance criteria:

- `Outcome`, the full `AppError` hierarchy with stable codes — including `ValidationError`, `ValidationWarning`, `AuthError`, `PersistenceError`, `SyncError`, `RemoteError` and `SecurityError` — plus `Confirmation`, `AppClock`, `UuidGenerator`, `DispatcherProvider`, `Logger`, `LocaleProvider`, `ConnectivityObserver`, `OwnerContext`, `SyncTrigger`, `SyncTriggerAdapter` and `MinorUnits` exist and match `docs/CONTRACTS.md §20` exactly.
- `:core:crash` exposes `CrashReporter` and a no-op implementation matching `docs/CONTRACTS.md §20.3.1`, with no Firebase, GitLive, Android or iOS type.
- `EntityId`, `OwnerId`, `CurrencyCode`, `Money`, `FuelVolume`, `PricePerLiter`, `ConsumptionL100Km` and `LOCAL_OWNER` match `docs/CONTRACTS.md §20.0` exactly, including the canonical property names `value` and `scaled`, and every scaled value is a `Long`.
- None of those types validates on construction: a test proves that wrapping a malformed UUID and an unsupported currency code succeeds, because the pull path of `docs/CONTRACTS.md §5` may not fail on a domain constraint.
- The named constants of `docs/CONTRACTS.md §20.0.1` exist in `:core:common`, including `SUPPORTED_CURRENCY_CODES`, and no story writes their literals inline.
- The three canonical monetary formulas and the two consumption formulas are implemented as exact integer arithmetic and pass every golden value in `docs/CONTRACTS.md §2`.
- A test proves no monetary or consumption path uses `Float` or `Double`.
- Kover thresholds pass for `:core:model` and `:core:common`.

### E0-04 - Architecture Guards - M

Status: completed on 2026-08-21, PR #17, except the feature-layer package rules (`DEC-3`). See `docs/handoff-E0-04.md`.

Implement module-level and package-level dependency checks per `docs/TECHNICAL_PLAN.md §4`.

Acceptance criteria:

- Feature `domain` dependency on SQLDelight, SQLite, Firebase, Koin, Android, Ktor, own `data` or own `presentation` fails the build with a rule-specific message.
- Feature `data` dependency on `:core:auth` or `:integration:*` fails the build.
- Feature-to-feature dependency fails the build.
- A `:core:model` dependency on `:core:common` fails the build; the reverse is allowed (`docs/TECHNICAL_PLAN.md §4`).
- Moving `ConsumptionInvalidReason` or `SegmentResult` out of `:core:model` into `:core:common` fails the build.
- `:core:sync` or `:shared` dependency on `:integration:*` fails the build.
- Feature `presentation` dependency on feature `data` fails the build.
- `:core:crash` dependency on Firebase, GitLive, Koin, Ktor, platform APIs, integrations or features fails the build.
- `:core:auth` dependency on platform APIs, Firebase, GitLive, SQLDelight, SQLite, Koin, Ktor, `:integration:*` or features fails the build with a rule-specific message.
- `:core:analytics` dependency on platform APIs, Firebase, GitLive, SQLDelight, SQLite, Koin, Ktor, `:integration:*` or features fails the build.
- `:core:testing` dependency on `:integration:*`, `:wiring:*` or `:feature:*` fails the build; a platform API reference in the `:core:testing` `commonMain` public surface fails the build, while the same platform API inside a permitted `expect`/`actual` test double (per `docs/CONTRACTS.md §15.1`) is allowed.
- `:core:sync` dependency on `:core:auth`, `:integration:*` or features fails the build; the sync engine does not reference `AuthClient` or `TokenProvider` (token handling lives in `RemoteSyncSource`, per `docs/CONTRACTS.md §10`).
- A reference to `AppDatabase` or `DatabaseFactory` from `:core:common` fails the build; both types are owned by `:core:database` (`docs/CONTRACTS.md §20.3.2`) and may appear only in `:core:database`, `:core:testing` fakes and the `AppGraphDependencies` field of `:shared`.
- `contract-check` assertion 1 does not flag SQLDelight-generated `AppDatabase`, query or row types as undeclared when they appear in a `:core:database` signature or in `DatabaseFactory`; the same types appearing in `:core:common`, `:core:sync`, feature `domain` or the `:shared` public API fail the check.
- An `expect`/`actual` declaration inside `:core:crash` fails the build.
- An `:integration:*` reference to `createAppGraph` fails the build; a Koin `Module` declaration inside `:integration:*` is allowed.
- The Phase 0 module set above is enforced: `:core:auth`, `:core:database` and `:core:sync` are not created by Phase 0 stories.
- `Float` and `Double` usage in `:core:*`, `:feature:*` or `:shared` arithmetic paths fails unless it is an explicitly allowlisted platform display conversion.
- `Logger.log` calls with string field values outside stable code, enum-name or `cycleId` patterns fail the source rule.
- `Logger.log` calls from `:core:database` are rejected except for local-database-only failures.
- Reads of `outbox.lastError` outside logging, debug UI and mapper projections fail; sync logic may read only `lastErrorCode`.
- Image-loading dependencies in `gradle/libs.versions.toml` fail unless a story reference and the Coil decision path of `docs/CONTRACTS.md §15.5` are present.
- Writes to `currentOdometerKm` or `odometerInconsistent` outside `:core:database` fail the build.
- The three feature-layer rows of `docs/TECHNICAL_PLAN.md §4` — feature `domain`, `data` and `presentation` — are implemented with Konsist in `E1-07`, not here (`D-28`).
- **Every rule has a failing fixture proving the check actually fires.**
- The check configuration is generated from the `docs/TECHNICAL_PLAN.md §4` table.
- The check runs on every PR.

### E0-05 - Quality Tooling and CI - M

Status: completed. PR #18 delivered the quality and CI implementation; D-34 activated branch
protection on 2026-08-21. See `docs/handoff-E0-05.md`.

Configure ktlint, detekt, Kover, the contract check and CI.

Acceptance criteria:

- `.editorconfig` sets `ktlint_code_style = ktlint_official`; `detekt.yml` is committed; baseline suppression files are absent.
- Style violations, failing tests and coverage below threshold fail CI.
- Android and iOS simulator / shared framework verification run on macOS CI.
- CI check names match `docs/CONTRACTS.md §18` exactly.
- `contract-check` implements the assertions of `docs/CONTRACTS.md §18` and fails when any is violated.
- The first `contract-check` invocation uses the Phase 0 type set (`Outcome`, `AppError`, `Confirmation`, `AppClock`, `UuidGenerator`, `DispatcherProvider`, `Logger`, `LocaleProvider`, `ConnectivityObserver`, `OwnerContext`, `SyncTrigger`, `MinorUnits`, `AnalyticsTracker`, `AnalyticsEvent` and `CrashReporter`) as the source of truth. Walking-skeleton types are added in `E0-07` and the check is re-run there to verify the Swift-facing ABI.
- `contract-check` applies the external identifier allowlist of `docs/CONTRACTS.md §18`, so primitives, standard collections, coroutine types and the pinned `Instant` type do not require duplicate declarations in §20.
- Until `E0-06` replaces the datetime `TBD`, `contract-check` reports `Instant` as a known `E0-06` blocker; after `E0-06`, it reads the exact fully-qualified `Instant` package from `docs/versions-matrix.md` and fails on any different package.
- Branch protection for `main` requires those checks.

CI duration under 20 minutes is an objective, monitored and reported, not a pass/fail criterion.

### E0-06 - ADRs, Version Matrix and Decision Board Validation - S

Status: completed on 2026-08-21, PR #13. See `docs/handoff-E0-06.md`.

Validate all decision records and pin the toolchain.

Acceptance criteria:

- One ADR exists per `Accepted` or `Deferred` decision, with the ADR `Status` equal to the board status.
- `docs/adr/README.md` maps every decision ID to its ADR file.
- The decision ID set and status values are identical across `docs/DECISION_BOARD.md`, `docs/SPECIFICATION.md §12`, `docs/TECHNICAL_PLAN.md §2` and `docs/adr/README.md`.
- `docs/versions-matrix.md` pins JDK, Gradle, AGP, Kotlin, KSP, Compose, SQLDelight, the SQLDelight AndroidX driver, `androidx.sqlite`, SKIE, Xcode, Firebase BOM, GitLive, coroutines, serialization, datetime, Koin, Kermit, Turbine and Kover, with the compatibility relation and the exact `Instant` package.
- Every `TBD` cell is replaced by a concrete pinned value with a citation from the corresponding ADR.
- A unit test imports the pinned datetime type package at build time.
- `docs/versions-matrix.md` fixes the reference devices and the measurement method for every performance target.
- Version choices are reflected in `gradle/libs.versions.toml` and nowhere else.

Blocks: E0-07.

### E0-08 - `:core:analytics` Abstraction - S

Status: completed on 2026-08-21, PR #16. See `docs/handoff-E0-08.md`.

Create `:core:analytics` with the `AnalyticsTracker` interface and the closed `AnalyticsEvent` hierarchy of `docs/CONTRACTS.md §16.1` and `§20.9`. Abstraction only: no Firebase dependency, no provider SDK.

Acceptance criteria:

- `AnalyticsTracker`, `AnalyticsEvent`, `AnalyticsUserProperties`, `SyncStatusCategory`, `ConversionFailureReason`, `DeletionFailureReason` and `CountBucket` match `docs/CONTRACTS.md §20.9` exactly.
- `AnalyticsEvent` is a closed sealed hierarchy and no leaf carries a free-text `String`, proven by an exhaustive `when` over every leaf.
- A no-op `AnalyticsTracker` lives in `:core:testing` and is the default in `testAppGraphDependencies(...)`, so `AppGraphDependencies` is complete without any Firebase module.
- `setEnabled(false)` makes `track` and `setUserProperties` no-ops that buffer nothing.
- No Firebase, GitLive or Android type appears in this module.

Why Phase 0: `AnalyticsTracker` is a mandatory member of `AppGraphDependencies` (`docs/CONTRACTS.md §11.6`), so the graph cannot be constructed or tested without it. The split mirrors `:core:auth` and `:integration:firebase-auth`.

## Phase 1 - Local Persistence

Goal: the app stores and displays vehicles and fuel entries locally. It is useful without remote backup, and works from a first launch with no connectivity.

### E1-01 - `:core:database` - M

Status: completed on 2026-08-24. See `docs/handoff-E1-01.md`.

Implement SQLDelight 2.3.2 with AndroidX bundled SQLite, schema v1, typed queries, transactions and migration strategy (`D-36`).

Acceptance criteria:

- Database instantiates and persists on Android and iOS.
- Bundled SQLite is used.
- Tables: `vehicle`, `fuel_entry`, `user_settings`, `local_sequence`, `outbox`, `sync_cursor`, `quarantine`.
- Synchronized entities include every control column of `docs/TECHNICAL_PLAN.md §6`, with `deleted INTEGER NOT NULL CHECK(deleted IN (0, 1))`, nullable `serverUpdatedAt INTEGER NULL`, and `CHECK((deleted = 0 AND deletedAt IS NULL) OR (deleted = 1 AND deletedAt IS NOT NULL))`.
- Migration tests prove the `deleted` checks reject values outside `0`/`1` and invalid `deleted`/`deletedAt` pairs, and prove `serverUpdatedAt = NULL` round-trips.
- `local_sequence` assigns monotonic `localMutationSeq` values shared by `vehicle` and `fuel_entry`; pull-applied remote writes and local-owner adoption do not consume it.
- There is **no** foreign key from `fuel_entry` to `vehicle`, and **no** unique index on the vehicle name.
- Outbox matches the committed DDL, including `lastErrorCode` and `idx_outbox_due`, and preserves the original `seq` when coalescing.
- `currentOdometerKm` and `odometerInconsistent` are recomputed inside `:core:database` for every fuel-entry write, and tests cover the exact recompute set of `docs/CONTRACTS.md §3.1`, including create, update that moves an entry in chronological order, update that changes only odometer comparison, single delete and vehicle cascade delete.
- Recompute tests include no recompute on `notes` / `currency` edits, coincident pre/post successors on update, single tombstone pre-delete successor, and the 3-row vehicle cascade case.
- Committed `.sq` files are the schema source, `verifyMigrations = true`, destructive schema recreation is absent, and schema-v1 create/reopen tests exist; every future version requires a committed `.sqm` migration and a populated previous-version migration test.
- Observable queries return `Flow`; one-shot queries are `suspend`.

### E0-07 - Walking Skeleton - L

Status: completed on 2026-08-27 through implementation PR #27 and D-73 evidence PR #28. See
`docs/handoff-E0-07.md`.

Build a single screen crossing native UI, shared state holder, SQLDelight, Firestore and real anonymous auth.

Moved here from Phase 0 by `D-30`: it needs the local database, which lives in `:core:database`, and the Phase 0 module set forbade that module. It is the Phase 1 gate — no other Phase 1 story starts until it passes.

Acceptance criteria:

- On both native application paths, a value written through the shared state holder is persisted in
  SQLDelight, backed up to Firestore under a real anonymous UID and fetched back after clearing the
  local product rows while that same Firebase Auth session remains available. This criterion does
  not transfer or recreate an anonymous credential on another device (`D-60`, `D-64`).
- The proof value is the name of a complete contract-valid `Vehicle` document (`D-39`); no
  walking-skeleton-only collection is introduced, and the slice does not claim E1-02 or E1-03.
- `iosSimulatorArm64` runs in CI.
- iOS consumes the single `Shared` framework produced by `:composition:ios` through direct
  integration, not CocoaPods. `:shared` exports no framework binary.
- Firestore offline persistence is disabled.
- Before billing is linked, the development project receives the D-66 EUR 10 monthly alerts-only
  budget, actual-cost alerts at 50%, 90% and 100%, and the project-local `stopBilling` 2nd gen
  Pub/Sub function. A temporary trivial budget fires the function for real; billing is relinked,
  observed Authentication/Firestore/data behavior and exact recovery steps are recorded, and the
  final budget is restored to EUR 10. Production is documented as alerts plus manual intervention
  and MUST NOT inherit the cutoff.
- D-69/D-70 controls are active: the broad billing role is confined to a keyless dedicated
  identity, billing administration and function errors alert the owner, retry is disabled, and the
  actual recurring budget-publication interval is recorded.
- The Functions package pins Node.js 22 in its manifest and `firebase.json`, with the runtime debt
  coupled to D-63 under TD-01. CI reads the normative value from `docs/versions-matrix.md`, fails on
  local pin disagreement and uses repository/ref-restricted OIDC plus a one-permission custom role
  to fail when the deployed runtime differs.
- App Check is enforced for Firebase Authentication and Cloud Firestore before native acceptance.
  Android uses Play Integrity, iOS uses App Attest and debug providers are confined to local or
  CI-specific builds. Real local acceptance uses registered debug tokens, and an unverified request
  is rejected (`D-67`).
- The API keys of the development Firebase project are restricted in the Google Cloud console by package name, bundle identifier and signing certificate **before** `google-services.json` or `GoogleService-Info.plist` is committed. The repository is public (`D-34`), so those keys are readable by anyone the moment the files land (`docs/SECURITY.md`).
- The Firestore database exists in the location fixed by `D-13`, in the development Firebase project fixed by `D-22` and governed by `D-14`.
- The Swift-facing surface constraints of `docs/CONTRACTS.md §15.3` are validated: no value class,
  project-owned type parameter, default argument, `CoroutineScope`, `Outcome`, `AppError`,
  repository, use case, command model, `AppProviders` or `AppGraphDependencies` appears in the
  exported API. The header is generated from `:composition:ios` and its golden remains committed
  as `shared/build/generated/objc-header/Shared.h.golden`.
- The Objective-C header contains the exported allowlist of `docs/CONTRACTS.md §20.10`, including
  the single `createSwiftAppGraph(isDebugBuild)` declaration owned by `:composition:ios`,
  `SwiftAppGraph`, concrete state holders and `UiState` classes, and omits the Kotlin-facing
  `buildAppGraph(isDebugBuild, providers)`, `AppProviders`, `AppGraphDependencies`, `AppGraph` and
  `SyncController`.
- The `objc-header-golden-check` CI step compares the generated header with the committed golden file, and the job is moved back to `macos-latest`: `E0-05` put it on `ubuntu-latest` because it had nothing to compare, and generating the header needs the Apple toolchain.
- `shared/README.md` documents every Swift-facing scale suffix from `docs/CONTRACTS.md §20.10` for iOS consumers.
- `:shared:testing` exposes `testAppGraphDependencies(...)` from `commonMain` with every parameter
  defaulted to a fake, including a no-op `CrashReporter` and a no-op `AnalyticsTracker`, with the
  same parameter count and order as `AppGraphDependencies` (`D-27`, `D-56`). Consumers depend on
  `:shared:testing` only from `commonTest`.
- `:shared` exposes `buildAppGraph(isDebugBuild, providers)` and tests it provider-free through an
  explicit `AppProviders` fake. `:composition:ios` declares `api(project(":shared"))`, exports
  `:shared`, depends on `:wiring:firebase`, owns SKIE and produces the only framework with
  `baseName = "Shared"` (`D-58`, `D-59`).

Database gate resolved by `D-36`: `E0-07` MUST exercise the accepted SQLDelight AndroidX bundled driver on the real Android and iOS application paths.

Staged ownership is fixed by `D-55`: E0-07 creates the final modules and public contract shapes
needed by the complete Swift golden header, but implements product behavior only for its minimal
Vehicle slice. Non-slice exported state holders are deterministic, unwired contract shells. Later
feature, auth, sync and graph stories complete those same modules and public types in place. E0-07
MAY use removable internal adapters, but MUST NOT introduce a temporary module, remote collection
or public walking-skeleton-only API.

Human review required.

### E1-02 - Vehicle Domain - S

Status: completed on 2026-08-28. See `docs/handoff-E1-02.md`.

Implement the `:feature:vehicle` domain package: entity, repository interface and use cases.

Acceptance criteria:

- Domain is Kotlin pure.
- Normalisation runs before validation per `docs/CONTRACTS.md §5`.
- Name validation and `nameFold` uniqueness use `canonicalVehicleName(name).lowercase()` exactly as defined in `docs/CONTRACTS.md §5`.
- `initialOdometerKm` range and its edit restriction are implemented.
- `FuelType` exists with exactly the MVP values of `docs/CONTRACTS.md §20.4` and default `GASOLINE`; `ELECTRIC` and `HYBRID` are absent until a future energy-model story expands the enum.
- Commands match `docs/CONTRACTS.md §20.5`; no command carries `ownerId` or timestamps, and only
  `UpdateVehicleCommand` carries the target `id` required by that canonical shape.
- Use cases have unit tests for success and for every error they declare.

### E1-03 - Vehicle Data, Local Only - M

Status: completed on 2026-08-28. See `docs/handoff-E1-03.md`.

Implements `VehicleRepository` (`docs/CONTRACTS.md §12`).

Implement the local data source, mappers and repository implementation for vehicles.

Acceptance criteria:

- Mappers have round-trip tests.
- `ownerId` is stamped from `OwnerContext`; the module does not reference `AuthClient`.
- Created and edited rows become `PENDING`.
- Created, edited and tombstoned rows receive a fresh `localMutationSeq` from the shared local sequence.
- **No outbox row is created while the owner is `LOCAL_OWNER`; `LOCAL_OWNER + PENDING + no outbox` is a valid stored state.**
- Vehicle deletion is logical and cascades to fuel entries in one transaction.
- No Firebase or GitLive type is referenced.

### E1-04 - Fuel Entry Domain - M

Status: completed on 2026-08-28. See `docs/handoff-E1-04.md`.

Implement the `:feature:fuel` domain package, CRUD use cases and rules R-1 and R-2.

Acceptance criteria:

- `MoneyInput` is the only way to supply monetary values, and all three derivations pass the golden values.
- Persistence stores the canonical monetary triple only (`litersScaled`, `pricePerLiterScaled`, `totalCostMinor`); no local, remote or outbox schema contains `moneyInputKind` or any other supplied-pair marker.
- `totalCostMinor` is integer minor units; no monetary path uses `Float` or `Double`.
- The two-step warning protocol is implemented: an unconfirmed inconsistent odometer returns `ValidationWarning.OdometerInconsistent` and mutates nothing; the same command with the confirmation succeeds.
- R-1 is enforced on edit as well as on create.
- Every bound in the `docs/CONTRACTS.md §5` validation table is enforced at both ends.
- A currency outside `SUPPORTED_CURRENCY_CODES` returns `ValidationError.InvalidUnit`; supported codes all use factor `100`.
- Platform currency tests verify every `SUPPORTED_CURRENCY_CODES` entry reports two minor digits through Android/JVM and iOS/native locale APIs, or falls back to `EUR` if the runtime reports a different factor.
- Boundary monetary tests include `litersScaled = 500_000`, `pricePerLiterScaled = 999_999`, `minorUnitFactor = 100`, proving every intermediate arithmetic step is `Long`.

### E1-05 - Consumption Calculation R-3 - M

Status: implementation completed on 2026-08-28. The optimized real-iPhone measurement remains an
explicit D-80 item in E4-03; E1-05 does not claim that device evidence.

Implement the pure `CalculateConsumption` use case.

Acceptance criteria:

- Happy path with two full tanks.
- First full tank produces `NoPreviousFullTank`.
- Partial intermediate refuel contributes to the next full segment.
- Partial entries do not produce `SegmentResult`; `EndEntryNotFullTank` is not emitted by `CalculateConsumption`.
- An entry sharing `odometerKm` with `P` is counted in the segment litres and yields `DuplicateOdometerInSegment`.
- `hasMissedEntries = true` on entry `E` invalidates the segment ending at `E` and any segment containing `E`, and leaves earlier segments valid — per `docs/CONTRACTS.md §3`. A partial entry flagged `hasMissedEntries` therefore invalidates the segment ending at the *next* full tank.
- `odometerInconsistent` invalidates the containing segment.
- `distanceKm <= 0` yields `NonPositiveDistance` and never divides by zero.
- Calculation order is `odometerKm, date, id`, and a back-dated entry does not change the result.
- Segment and average values are produced by the canonical consumption arithmetic of `docs/CONTRACTS.md §2`, and all four golden values in that section pass.
- Average consumption is distance-weighted, not an arithmetic mean; the golden case where the two differ (`774` versus `776`) is covered by a test.
- The function is total: no input throws.
- `CalculateConsumption` does not filter its input; entries from another vehicle or with a non-null `deletedAt` still participate when supplied directly.
- 1,000 entries processed within the target of `docs/SPECIFICATION.md §11`, measured as defined in `docs/versions-matrix.md`.

Human review required.

### E1-06 - Fuel Entry Data, Local Only - M

Status: implementation completed on 2026-08-29.

Implements `FuelEntryRepository` (`docs/CONTRACTS.md §12`).

Implement the local data source, mappers, projections and repository implementation for fuel entries.

Acceptance criteria:

- Queries support both orderings of `docs/CONTRACTS.md §4`.
- `observeFuelEntries` returns the `FuelEntryListItem` projection in chronological order and excludes orphan entries.
- `FuelEntryListItem` maps partial rows to `consumption = null` and `invalidReason = EndEntryNotFullTank`, while still allowing those rows to contribute litres to the next full segment.
- `observeConsumption` is backed by a dedicated projection query, not by the UI list.
- The repository filter step is covered: `observeConsumption` passes only non-deleted entries for one vehicle to `CalculateConsumption`.
- Created and edited rows become `PENDING`; no outbox row while `LOCAL_OWNER`; `LOCAL_OWNER + PENDING + no outbox` is a valid stored state.
- Created, edited and tombstoned rows receive a fresh `localMutationSeq` from the shared local sequence.
- Logical delete works and triggers the `docs/CONTRACTS.md §3.1` recompute set.
- Mappers have round-trip tests.

### E1-07 - Android UI: Vehicles - M

**Status:** Completed on 2026-08-30.

Implement the vehicle list, create/edit form and detail shell with a shared presentation state holder.

Design reference (non-normative): `docs/DESIGN.md §4`, Android screens 02 home, 03 vehicle form and 04 vehicle detail.

Acceptance criteria:

- The state holder lives in `commonMain`, takes a `CoroutineScope`, exposes `close()`, and emits on `dispatchers.main`.
- `UiState` contains no user-facing text.
- Loading, empty and error states exist and map typed errors to platform strings.
- Spanish and English strings exist; no hardcoded user-facing strings.
- Vehicle creation UI test exists.
- The Vehicle presentation types live in the `:feature:vehicle` `presentation` package; their
  existing Objective-C and Swift names remain byte-exact in the generated framework header.
- The create/edit form never renders a `fuelType` selector, while the stored value round-trips.
- The production list calls `observeVehicles(includeDeleted = false)` and never emits a deleted row.
- Edit forms observe `VehicleEditFacts` reactively, and a stale editable UI flag cannot bypass the
  repository's authoritative initial-odometer write validation.
- `buildAppGraph` returns the Kotlin-facing `AppGraph`; Android passes `viewModelScope`, while
  `SwiftAppGraph` wraps the graph and retains its caching and close semantics.
- `AppGraph.close()` idempotently releases its graph-owned `DatabaseHandle`, and
  `SwiftAppGraph.close()` releases that database connection transitively.
- D-90 keeps a creation holder's route identity `null`, reports completion through
  `savedVehicleId` and resets its inputs after success so it cannot overwrite the created Vehicle.
- `SwiftAppGraph` exposes keyed release for Vehicle forms, Fuel Entry lists and Fuel Entry forms;
  releasing closes the holder and cancels its child scope, and the next request returns a fresh
  instance.
- D-91 pins the exported common enums to `Confirmation`, `AuthProvider` and `SyncTrigger` in Swift
  with the matching `SharedConfirmation`, `SharedAuthProvider` and `SharedSyncTrigger` Objective-C
  names; the rename from the pre-E1-07 module-derived names is intentional.
- Compose Navigation and the instrumented UI stack are pinned by D-84, and the creation test runs
  in the protected `android-instrumented-tests` emulator job.

### E1-08 - Android UI: Fuel Entries - L (completed)

Implement the fuel entry list, create/edit form, segment consumption display and average consumption display.

Design reference (non-normative): `docs/DESIGN.md §4`, Android screens 04 vehicle detail and 05 refuelling form.

Acceptance criteria:

- Form defaults follow F-3, including the `hasMissedEntries` secondary toggle.
- The derived R-2 value recalculates while typing, using `MoneyInput`.
- The odometer warning dialog implements the two-step confirmation.
- Entries with no consumption show an accessible explanation derived from `ConsumptionInvalidReason`, including `EndEntryNotFullTank` for partial refuels.
- `hasMissedEntries` and `odometerInconsistent` flags are rendered on every row, including partial refuels where `invalidReason = EndEntryNotFullTank`.
- Empty consumption state follows the specification.
- Move the Fuel Entry state holders and their `UiState` and row types from the D-55 `:shared`
  shells into the `:feature:fuel` `presentation` package, preserving the Swift ABI.

### E1-09 - iOS UI: Vehicles and Fuel Entries - L

Implement SwiftUI screens consuming the shared state holders through `AppGraph`.

Design reference (non-normative): `docs/DESIGN.md §4`, iOS screens 02 home, 03 vehicle form, 04 vehicle detail and 05 refuelling form.

Acceptance criteria:

- No business logic is duplicated in Swift.
- State holder scopes are created in `init` and cancelled in `deinit`.
- Functional parity with Android for F-2 and F-3.
- Fuel-entry consumption and no-consumption explanations match Android, including `EndEntryNotFullTank` for partial refuels.
- `hasMissedEntries` and `odometerInconsistent` flags are rendered on every row, including partial refuels.
- Dynamic Type is usable for critical flows.

### E1-10 - Settings Persistence - S

Implement the local `user_settings` table, `SettingsRepository` and `UpdateSettingsCommand`.

Acceptance criteria:

- A single row is created on first launch with the locale-derived currency, `EUR` fallback, and `analyticsEnabled = false`.
- Only supported 2-decimal currencies are accepted.
- Locale-derived defaults use platform currency APIs; any supported code whose runtime minor-unit factor is not `100` falls back to `EUR`.
- Changing the currency does not rewrite existing fuel entries.
- Settings are device-local: nothing is enqueued and there is no remote document.
- An update with both fields `null` returns `ValidationError.NoOp` and mutates nothing.
- Settings are deleted by destructive local-data flows and recreated from locale defaults with `analyticsEnabled = false`.

## Phase 2 - Authentication

### E2-01 - `:core:auth` - S

Implement the auth interfaces and models.

Acceptance criteria:

- `AuthClient`, `TokenProvider`, `AuthSession`, `AuthState`, `NativeAuthCredential`, `AuthToken` and the typed `AuthError` match `docs/CONTRACTS.md §6`, `§11.1` and `§20.8`.
- `AuthState.Unknown` is distinct from `SignedOut`.
- `OwnerContext` is implemented here and bound in wiring; feature modules do not see `AuthClient`.
- No Firebase type appears in this module.

### E2-02 - Firebase Auth Integration - L

Implement anonymous, Google, Apple, credential linking, re-authentication, sign-out, account deletion and token refresh.

Acceptance criteria:

- Anonymous login works on both platforms; Google works on both; Apple works on iOS.
- The Firebase projects use Authentication with Identity Platform and native automatic anonymous
  cleanup with the provider-owned 30-day eligibility threshold.
- Anonymous account creation metadata is available to common auth behavior so the D-62 schedule is
  anchored to Firebase's canonical user-creation timestamp rather than a device clock first-seen
  value.
- Cancelled system dialogs produce `AuthError.Cancelled`.
- A non-collision provider flow that would change the UID produces `AuthError.UidWouldChange` and
  aborts. `CredentialAlreadyInUse` remains available to the E2-04 collision flow.
- `AuthClient.deleteAccount()` calls the `D-23` server/Admin operation and never calls the mobile Firebase Auth account deletion API directly.
- Account deletion verifies token freshness using `FRESH_LOGIN_THRESHOLD_MS` before calling the server operation, and triggers re-authentication if the token is stale.
- Native UI obtains credentials; common code exchanges them.
- No GitLive or Firebase type crosses the module boundary.

### E2-03 - Onboarding Flow F-1 - M

Implement the welcome screen, with an offline-capable local start. Provider selection happens on
the welcome screen itself; there is no separate provider-selection screen.

Acceptance criteria:

- "Continue without account" succeeds with no connectivity and creates a `LOCAL_OWNER` session.
- With connectivity, "Continue without account" creates a Firebase anonymous user automatically and does not enter `LOCAL_OWNER`.
- Routing never happens while `AuthState.Unknown`.
- The welcome screen presents the platform providers directly: exactly two actions on Android (Google, continue without account) and exactly three on iOS (Apple, Google, continue without account).
- No control on the welcome screen starts a sign-in without naming its provider. Every sign-in affordance maps to `startPermanentSignIn(provider)` with a concrete `AuthProvider`, or to `startAnonymousSignIn()`.
- iOS offers Apple whenever Google is offered.
- Retry after network failure does not leave the UI stuck.
- Routing after authentication depends on whether vehicles exist.

### E2-06 - Local Owner Adoption - M

Adopt `LOCAL_OWNER` data into the first real UID.

Acceptance criteria:

- On the first successful authentication, all `LOCAL_OWNER` rows are rewritten to the new UID in one transaction, `localRevision` is bumped and an outbox snapshot is enqueued for every non-synced row.
- Adoption preserves existing `localMutationSeq` values and inserts outbox rows in the push dependency order of `docs/CONTRACTS.md §8`, then `localMutationSeq ASC, id ASC`.
- Adoption resets non-`SYNCED` rows, including `FAILED_POISONED`, to `PENDING`, clears error context and enqueues snapshots.
- Outbox `seq` values assigned during adoption are monotonically increasing and the first adoption row receives `(max pre-existing seq) + 1`.
- The operation is idempotent: running it twice enqueues each row once.
- A test starts from a populated `LOCAL_OWNER` database with vehicles and fuel entries, including interleaved edits, and asserts nothing is lost, outbox order is deterministic and everything syncs.
- Adoption is triggered automatically when connectivity returns, not only from a UI action.

Blocks: E3-04.

Human review required.

### E2-04 - Anonymous Account Conversion F-4 - M

Implement account linking and credential collision handling.

Acceptance criteria:

- Successful linking preserves vehicles and fuel entries and keeps the UID.
- `SessionStateHolder.startAccountConversion(provider)` calls `AuthClient.linkCredential` (not `signInWithCredential`).
- `SessionStateHolder.confirmAccountConversion(confirmation)` handles the collision confirmation.
- Collision offers an explicit destructive choice stating that existing permanent-account data
  will be replaced by the current anonymous-session snapshot, gated by
  `Confirmation.AdoptExistingAccount`.
- Cancelling leaves the anonymous session and local data untouched.
- Confirmation persists a complete local snapshot and captures a fresh anonymous ID token before
  switching sessions, replaces the permanent account's remote data idempotently, and keeps a
  durable operation marker until replacement and orphan cleanup both succeed.
- Interruption tests cover every boundary after confirmation, including after the permanent-account
  session switch; retry resumes the captured replacement instead of pulling over it.
- After replacement, the flow calls the E3-11 2nd gen callable to delete the orphaned anonymous
  identity. It does not depend on an Auth deletion trigger.
- Automatic merge is not implemented.

Depends on: E2-02, E3-11.

Human review required.

### E2-07 - Anonymous Sign-In Benefit Reminders - S

Implement the foreground-only anonymous-account retention notices selected by `D-62`.

Acceptance criteria:

- One configuration constant contains exactly the elapsed-day thresholds `1, 3, 8, 18`, anchored
  to the Firebase anonymous user-creation timestamp.
- Evaluation runs on launch and foreground return only; no operating-system notification,
  scheduler or background alarm is introduced.
- Only the highest unseen due reminder is emitted, and persisting its index consumes every lower
  pending reminder.
- The last-shown index survives process and app restarts, clears after permanent sign-in or
  successful linking, and index 3 completes the sequence.
- Reminders are dismissible, never gate product functionality and explain permanent-sign-in
  recovery plus the device-bound 30-day cleanup risk.
- Deterministic tests cover 12 hours and elapsed days 1, 2, 4, 9, 20 and 31. The day-20 case emits
  only reminder 4 and consumes reminders 1 through 3.

Depends on: E2-02.

Human review required.

### E2-05 - Sign-Out and Account Deletion F-5 - M

Implement sign-out, local-data deletion and account deletion.

Acceptance criteria:

- Sign-out is offered only to permanently authenticated users; anonymous sessions get "delete local data" with two-step confirmation.
- Sign-out returns `ValidationWarning.PendingSyncBeforeSignOut(pendingCount)` when the outbox is non-empty, then offers to wait, cancel or discard through `Confirmation.DiscardPendingChanges`.
- Account deletion follows the order of `docs/CONTRACTS.md §11.5`: verify token freshness and re-authenticate if stale, call the `D-23` server/Admin operation, wait for remote data and auth account deletion, then clear local data.
- Account deletion drops any pending outbox rows only after the server operation succeeds and local data is cleared.
- A failure in the server/Admin operation maps to `AuthError.AccountDeletionRemoteFailed`, preserves local data and does NOT report the account as deleted.
- Sign-out, anonymous "delete local data" and account deletion delete `user_settings`; the next settings read recreates defaults.
- Account deletion is accessible from settings.
- Move Session state holders and their `UiState` types from the D-55 `:shared` shells into the
  `:feature:session` `presentation` package, preserving the Swift ABI. `SyncStateHolder` remains
  the app-level state holder in `:shared`.

## Phase 3 - Backend and Synchronization

### E3-01 - Firestore Structure and Security Rules - M

Status: completed on 2026-08-24, PR #26. See `docs/handoff-E3-01.md`.

Create the Firestore rules, indexes and emulator tests.

Prerequisite order: follows `E3-06` and precedes `E0-07` (`D-40`, `D-42`).

Acceptance criteria:

- Rules match `docs/CONTRACTS.md §16`, split by operation, with `allow delete: if false`.
- `validPayload()` enforces the closed remote `Vehicle` and `FuelEntry` schemas from `docs/CONTRACTS.md §16`, including required keys, extra-key rejection, unknown-collection rejection, local-only-key rejection, primitive type, enum, nullability, range checks and `deleted == (deletedAt != null)`.
- Nullable-field emulator tests prove `brand: null` is accepted and missing `brand` is rejected.
- Emulator tests prove `CLIENT_MAX_SCHEMA_VERSION` matches the accepted remote `schemaVersion`, orphan fuel entries are accepted, and literal `updatedAt` values that differ from `request.time` are rejected.
- `firestore/rules/main.rules` contains the `validPayload()` helpers required by `docs/CONTRACTS.md §16`.
- Every emulator test listed in `docs/CONTRACTS.md §16` passes.
- `firestore/firestore.indexes.json` exists and the pull query runs without an index error.
- Rules tests use the exact official Node/Firebase stack of `D-46` and run as a named step inside
  the protected `contract-check` job (`D-47`).
- Rules accept exactly `schemaVersion == 1` and reject lower or higher values (`D-49`).
- No Firestore client or provider module is created. E0-07 owns the executable disabled-persistence
  configuration on the first real Android and iOS client paths (`D-48`).

Human review required.

### E3-10 - Account Deletion Server Operation - M

Implement the Firebase Admin account deletion operation selected by `D-23`.

Acceptance criteria:

- The operation accepts only an authenticated caller and verifies that the caller UID is the target UID.
- The operation deletes documents under `users/{uid}` in the normative order: `fuelEntries`, then `vehicles`.
- The deletion implementation is the reusable idempotent `deleteUserData` service selected by
  `D-63`, not logic embedded only in the user-requested account-deletion handler.
- An explicit registry lists every cleared Firestore collection and Cloud Storage prefix. It lists
  `fuelEntries` and `vehicles`, and its current Storage-prefix list is explicitly empty.
- A contract test compares the registry with the closed data-location schema and fails if either
  declares a location absent from the other.
- A server-side test proves the operation does not delete vehicles before fuel entries.
- The operation deletes the Firebase Auth user only after remote document deletion succeeds.
- The operation is idempotent for already-deleted documents and missing auth users, but never deletes outside `users/{uid}`.
- Failures before Firebase Auth deletion return a typed failure and are not reported as success to the app.
- Logs are redacted according to `docs/CONTRACTS.md §17` and `docs/SECURITY.md`.
- Emulator or server-side tests prove the happy path, retry/idempotency behavior, caller UID rejection and failure-before-auth-deletion behavior.
- Firestore emulator tests still prove that mobile client hard deletes are rejected by `allow delete: if false`.

Human review required.

### E3-11 - Anonymous Identity Cleanup Entry Points - M

Add the owned cleanup callers selected by `D-63` for native anonymous cleanup and destructive
account-linking collisions.

Acceptance criteria:

- `onAnonymousUserDeleted` is the only Cloud Functions 1st gen function in the project. The
  application relies on it only for Firebase native automatic anonymous cleanup, and it delegates
  eligible deleted anonymous UIDs to E3-10 `deleteUserData`; delivery from another deletion path is
  harmless overlap.
- `deleteOrphanedAnonymousAccount` is a 2nd gen callable. It verifies the captured anonymous ID
  token and the permanent caller context, rejects deletion of the current permanent UID, deletes
  the orphaned anonymous Auth account through the Admin SDK, then invokes `deleteUserData`
  directly.
- The callable never relies on `onAnonymousUserDeleted` firing. An integration test deletes through
  the Admin SDK path with trigger delivery suppressed or disregarded and still proves
  `users/{uid}` is removed.
- Both paths are idempotent and concurrent or delayed overlap is harmless.
- Logs contain no UID, token, raw payload or other forbidden value.
- The functions, exports and deployment configuration match the exact `TD-01` migration surface;
  a contract check rejects any additional 1st gen function.

Depends on: E3-10.

Human review required.

### E3-02 - Firestore RemoteSyncSource - M

Implement the Firestore remote sync integration.

Acceptance criteria:

- Writes use `serverTimestamp()` and the client document ID.
- Delta pull applies the overlap once per cycle, uses `startAt(overlapSince)` for the first page and `startAfter(lastServerUpdatedAt, lastDocumentId)` for later pages (`D-50`).
- A resumed-cycle emulator test proves `startAt(overlapSince)` works after the first non-empty pull.
- Firestore `Timestamp` values are converted to epoch milliseconds at the integration boundary before conflict comparison.
- Firestore errors map to `RemoteError` exactly as in `docs/CONTRACTS.md §6`.
- An `Unauthenticated` response forces a token refresh and retries once inside this module.
- On an empty page, `nextCursor` equals the input cursor and `hasMore` is false.
- If `items` is non-empty, `nextCursor` equals the last returned item, even when the input cursor and first item share timestamp values.
- No Firestore or GitLive type crosses the module boundary.

### E3-03 - `:core:sync` Engine - L

Implement the outbox, cursor, push, pull, LWW, overlap window, backoff, quarantine, aggregate backup status and debug support according to `docs/CONTRACTS.md §7`–`§9`.

Acceptance criteria:

- All 18 backup and recovery tests in `docs/TECHNICAL_PLAN.md §9` pass.
- A cycle is not started while `ConnectivityObserver.isOnline` is false, and connectivity failures never poison a row (`docs/CONTRACTS.md §9.2`, `§9.7`).
- The deterministic backup and recovery simulation exists with a fixed seed and an injected jitter source.
- The state machine matches `docs/CONTRACTS.md §7`, including `SYNCING -> SYNCING` on a local edit during an in-flight push.
- Only one cycle runs at a time per owner, enforced by a mutex in `SyncController`.
- Concurrent triggers set the single pending flag and cause exactly one follow-up cycle after the active cycle completes.
- Trigger constants match `docs/CONTRACTS.md §9.8`.
- `ConnectivityRecovered` moves connectivity-only `FAILED_RETRYABLE` rows due immediately while preserving `attemptCount`.
- `retryFailed()` resets every `FAILED_RETRYABLE` and `FAILED_POISONED` row to `PENDING` with cleared error context.
- Cold-start sync pulls before pushing only when `vehicle` and `fuel_entry` are empty for the owner and the outbox is empty.
- Debug screen exposes the outbox, cursors, quarantine and row sync state.
- Sync retry and poison decisions read `lastErrorCode` only; `lastError` is debug/UI context and is never read by sync logic.
- Quarantine records include `QuarantineReason`, raw payload JSON, `schemaVersion`, `serverUpdatedAt` and redacted logging for unsupported schema and malformed supported-version payloads.
- Wire every state holder that exposes `SyncStatus` to the single `SyncController.status` flow and
  add the `docs/CONTRACTS.md §14` unit test proving that two holders converge. This closes the D-88
  E1-07 exception for constant `Idle` and direct Vehicle restoration.

Human review required.

### E3-08 - App Graph and Firebase Wiring - M

Complete the Kotlin-facing `AppGraph`, the Swift-facing `SwiftAppGraph` and
`:wiring:firebase` in place. E0-07 already owns the provider-free `buildAppGraph`, the sole
`createSwiftAppGraph(isDebugBuild)` declaration in `:composition:ios` and the framework topology
under D-58/D-59.

Acceptance criteria:

- `AppProviders`, `AppGraphDependencies`, `buildAppGraph(isDebugBuild, providers)`, the
  Kotlin-facing `AppGraph`, `createSwiftAppGraph(isDebugBuild)`, `SwiftAppGraph`, exported state
  holders and exported `UiState` classes match `docs/CONTRACTS.md §11.6` and `§20.10`.
- `:shared:testing` exposes `testAppGraphDependencies(...)` from `commonMain`, mirroring the exact
  `AppGraphDependencies` parameter order and defaulting every parameter (`D-56`).
- Only `:wiring:firebase` constructs Firebase implementations.
- Every top-level declaration in `:wiring:firebase` is a Koin module, an abstraction factory or a platform initialiser.
- Tests build the graph through `buildAppGraph` and `testAppProviders(...)` without starting Koin.
- The Swift facade exposes a sync state holder, not `SyncController`, and it owns/cancels the scopes for state holders it creates.
- `SwiftAppGraph` state-holder factories are cached/idempotent for identical arguments, and throw after `SwiftAppGraph.close()`.

### E3-04 - Repository Sync Wiring - M

Replace no-op remote sources with real sync wiring and platform triggers.

Acceptance criteria:

- The UI still observes only local database flows.
- The five triggers of `docs/CONTRACTS.md §9.8` exist with the stated constants.
- Platform workers only call `SyncController.requestSync(reason)`.
- No state holder change is required for sync correctness.

### E3-12 - Permanent-Account Cross-Device Recovery Proof - S

Prove recovery at the first point where permanent authentication and complete sync coexist.

Acceptance criteria:

- A contract-valid Vehicle written and backed up on Android under a permanent provider identity is
  restored from Firestore on iOS after starting from clean local product data and signing into the
  same permanent identity.
- The reverse iOS-to-Android direction is covered or the handoff records why the same shared path
  makes it redundant and supplies equivalent provider-boundary evidence.
- The proof never transfers an anonymous Firebase Auth session or describes anonymous identity as
  cross-device recoverable.
- Recovery remains pull-based, uses no real-time listener and does not introduce simultaneous
  multi-device use.

Depends on: E2-02, E3-04.

Human review required.

### E3-05 - Backup Status UI - S

Add a non-intrusive backup status indicator.

Acceptance criteria:

- `SyncStatus` is rendered with the precedence `Failed > Syncing > Pending > Idle`.
- Being offline with pending rows, or with only connectivity-code retryable failures, renders as `Pending`, never as an error.
- The failed state offers manual retry through `SyncController.retryFailed()`.

### E3-07 - Tombstone Purge - S

Implement the local 90-day tombstone purge.

Acceptance criteria:

- A tombstone is purged only when `SYNCED`, older than 90 days by `serverUpdatedAt`, and with no outbox row.
- Purge runs at most once per app start, in one transaction.
- A test proves a pending tombstone is never purged.
- A fresh device pulling a tombstone for an entity it has never seen inserts it as a tombstone instead of failing.

### E3-09 - Firebase Analytics Integration - S

Implement `:integration:firebase-analytics`, the Firebase-backed `AnalyticsTracker` from `E0-08`, and bind it in `:wiring:firebase`.

Acceptance criteria:

- Every `AnalyticsEvent` leaf maps to a Firebase event name and parameter set, exhaustively and with no `else` branch.
- Collection is disabled at startup and enabled only after an explicit opt-in, including on a fresh install; a test proves nothing is buffered while disabled.
- No forbidden payload of `docs/CONTRACTS.md §16.1` can be sent: a test asserts no parameter value derives from odometer, volume, cost, notes, entity IDs or the UID.
- Only `:wiring:firebase` constructs the implementation; no Firebase type crosses the module boundary.
- Excluding this module leaves the app building and testing on the `:core:analytics` no-op, per `E3-06`.

### E3-06 - Provider Decoupling Proof - S

Make P4 executable.

Status: completed on 2026-08-24. See `docs/handoff-E3-06.md`.

Acceptance criteria:

- Excluding `:integration:*` and `:wiring:firebase` leaves `:core:*` and `:feature:*` compiling and testing with fakes.
- `settings.gradle.kts` uses `carapp.excludeFirebaseProviders=true` and the explicit conditional
  provider registry of `D-43` / `D-44`; missing provider directories create no empty projects.
- The proof also compiles and tests `:shared`, excludes `:composition:ios`, and runs Android host plus `iosSimulatorArm64` on
  macOS (`D-45`).
- The check runs in CI under the name `provider-decoupling`.

## Phase 4 - MVP Hardening

### E4-01 - Settings UI - S

Implement the settings screen over the Phase 1 persistence.

Design reference (non-normative): `docs/DESIGN.md §4`, screen 06 settings on both platforms.

Acceptance criteria:

- The screen shows exactly the surface listed in `docs/SPECIFICATION.md §3.1`.
- Units are visible and read-only.
- The analytics opt-in is off by default and takes effect immediately.
- Sign-out or local-data deletion, account deletion and app version are reachable.

### E4-02 - Accessibility and Localization - M

Harden accessibility and ES/EN localization.

Acceptance criteria:

- TalkBack and VoiceOver audits pass for F-1 through F-3.
- UI is usable at 200% font size.
- Spanish and English strings are complete, including every `AppError` and `ConsumptionInvalidReason` mapping.
- No hardcoded user-facing strings remain, and no user-facing text appears in `UiState`.

### E4-03 - Performance and Reliability Hardening - M

Measure and fix MVP performance.

Acceptance criteria:

- Cold start, list smoothness and consumption targets are met, measured exactly as defined in `docs/versions-matrix.md` on the reference devices.
- Run and record the D-80 optimized consumption benchmark on a real iPhone, filling the device and date left open by E1-05.
- No memory leaks in critical flows; state holder scopes are cancelled.

### E4-04 - Release Preparation - M

Prepare release assets and store requirements.

Acceptance criteria:

- Branch protection for `main` is active with the ten `docs/CONTRACTS.md §18` checks, or the repository is still private and `D-33` still holds. A public repository without branch protection fails this story.

- App icons and splash are present.
- Privacy policy and store privacy labels are prepared and cover analytics, which is off by default.
- A separate production Firebase project exists, its project identifier has been decided by the owner, and no public release build points to the development Firebase project.
- `:integration:firebase-crashlytics` implements `CrashReporter` with Firebase Crashlytics; `:wiring:firebase` binds it without leaking provider types.
- Release builds are installable on both platforms.
- Account deletion and Apple sign-in requirements are satisfied.
- Crash-reporting redaction is verified: no UID, tokens, notes, exact odometer values, exact costs or raw Firestore payloads in crash reports.
- Release logging redaction is verified: no UID, notes, odometer or cost values in release logs.

## Execution Order

```text
E0-00 owner decisions (completed)
  -> E0-01 KMP bootstrap
      -> E0-06 toolchain pinning
          -> E0-02 convention plugins
              -> E0-03 base core modules -> E0-08 :core:analytics
                  -> E0-04 architecture guards -> E0-05 quality tooling and CI
                      -> Phase 0 closes
  -> Phase 1 local persistence
      -> E1-01 :core:database
          -> E3-06 provider decoupling proof (pulled forward by D-42)
              -> E3-01 Firestore rules (pulled forward by D-40)
                  -> E0-07 walking skeleton gate (moved here by D-30)
                      -> the rest of Phase 1
                      -> Phase 2 auth can overlap with late Phase 1; E2-06 must precede E3-04
                      -> Phase 3 sync wiring depends on Phases 1 and 2
                      -> Phase 4

E2-02 Firebase Auth integration -> E2-07 anonymous retention notices
E3-10 deletion service -> E3-11 anonymous cleanup entry points -> E2-04 collision conversion
E2-02 Firebase Auth integration + E3-04 repository sync wiring -> E3-12 cross-device recovery proof
```

`E0-08` is a hard prerequisite for `E0-07` because `AppGraphDependencies` requires
`AnalyticsTracker`. `E0-07` is a Phase 1 story since `D-30`, because it needs the local database
from `:core:database` (`E1-01`). `D-42` and `D-40` add the security prerequisite chain
`E3-06 -> E3-01 -> E0-07` without moving E0-07 out of Phase 1.

`D-64` keeps the anonymous lifecycle split across reviewable owners: E0-07 proves the real
anonymous local/remote Vehicle path only; E2-02 provides permanent providers and creation
metadata; E2-07 owns notices; E3-10 owns the reusable deletion service; E3-11 owns cleanup entry
points and unblocks E2-04 collision handling; E3-12 supplies the permanent-account cross-device
proof after E3-04.

## Story Index

| Story | Phase | Size | Human gate |
|-------|-------|------|------------|
| E0-00 Owner decision closure (completed) | 0 | S | Yes |
| E0-01 KMP bootstrap (completed) | 0 | M | — |
| E0-02 Convention plugins (completed) | 0 | M | — |
| E0-03 Base core modules (completed) | 0 | M | — |
| E0-04 Architecture guards (completed, feature rules open) | 0 | M | — |
| E0-05 Quality tooling and CI (completed) | 0 | M | — |
| E0-06 ADRs and version matrix (completed) | 0 | S | — |
| E0-08 `:core:analytics` abstraction (completed) | 0 | S | — |

| E1-01 `:core:database` (completed) | 1 | M | — |
| E0-07 Walking skeleton (completed) | 1 | L | Yes |
| E1-02 Vehicle domain (completed) | 1 | S | — |
| E1-03 Vehicle data | 1 | M | — |
| E1-04 Fuel entry domain (completed) | 1 | M | — |
| E1-05 Consumption calculation (completed; D-80 device evidence open in E4-03) | 1 | M | Yes |
| E1-06 Fuel entry data (completed) | 1 | M | — |
| E1-07 Android UI vehicles (completed) | 1 | M | — |
| E1-08 Android UI fuel entries (completed) | 1 | L | — |
| E1-09 iOS UI | 1 | L | — |
| E1-10 Settings persistence | 1 | S | — |
| E2-01 `:core:auth` | 2 | S | — |
| E2-02 Firebase Auth integration | 2 | L | Yes |
| E2-03 Onboarding F-1 | 2 | M | — |
| E2-06 Local owner adoption | 2 | M | Yes |
| E2-04 Account conversion F-4 | 2 | M | Yes |
| E2-07 Anonymous sign-in benefit reminders | 2 | S | Yes |
| E2-05 Sign-out and deletion F-5 | 2 | M | — |
| E3-01 Firestore rules (completed) | 3 | M | Yes |
| E3-10 Account deletion server operation | 3 | M | Yes |
| E3-11 Anonymous identity cleanup entry points | 3 | M | Yes |
| E3-02 Firestore RemoteSyncSource | 3 | M | — |
| E3-03 `:core:sync` engine | 3 | L | Yes |
| E3-08 App graph and wiring | 3 | M | — |
| E3-04 Repository sync wiring | 3 | M | — |
| E3-12 Permanent-account cross-device recovery proof | 3 | S | Yes |
| E3-05 Backup status UI | 3 | S | — |
| E3-07 Tombstone purge | 3 | S | — |
| E3-09 Firebase Analytics integration | 3 | S | — |
| E3-06 Provider decoupling proof (completed) | 3 | S | — |
| E4-01 Settings UI | 4 | S | — |
| E4-02 Accessibility and localization | 4 | M | — |
| E4-03 Performance hardening | 4 | M | — |
| E4-04 Release preparation | 4 | M | — |

Human review gates are defined canonically in `AGENTS.md`. The column above is a convenience index, not a second source.
