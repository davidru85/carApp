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

Create convention plugins for KMP libraries, features, Android application, Compose, Room and SKIE.

Acceptance criteria:

- Creating a new module requires no more than five lines in its `build.gradle.kts`.
- SKIE is applied only to `:shared`.
- Test and Kotlin toolchain configuration is centralized.
- Plugins make future feature splitting possible without redesign.

### E0-03 - Base Core Modules - M

Create `:core:model`, `:core:common`, `:core:crash` and `:core:testing`, implementing the Phase 0 canonical types of `docs/CONTRACTS.md §20`.

Acceptance criteria:

- `Outcome`, the full `AppError` hierarchy with stable codes, `Confirmation`, `AppClock`, `UuidGenerator`, `DispatcherProvider`, `Logger`, `LocaleProvider`, `ConnectivityObserver`, `OwnerContext`, `SyncTrigger` and `MinorUnits` exist and match `docs/CONTRACTS.md §20` exactly.
- `:core:crash` exposes `CrashReporter` and a no-op implementation matching `docs/CONTRACTS.md §20.3.1`, with no Firebase, GitLive, Android or iOS type.
- `EntityId`, `OwnerId`, `CurrencyCode`, `Money`, `FuelVolume`, `PricePerLiter`, `ConsumptionL100Km` and `LOCAL_OWNER` match `docs/CONTRACTS.md §20.0` exactly, including the canonical property names `value` and `scaled`, and every scaled value is a `Long`.
- None of those types validates on construction: a test proves that wrapping a malformed UUID and an unsupported currency code succeeds, because the pull path of `docs/CONTRACTS.md §5` may not fail on a domain constraint.
- The named constants of `docs/CONTRACTS.md §20.0.1` exist in `:core:common`, including `SUPPORTED_CURRENCY_CODES`, and no story writes their literals inline.
- The three canonical monetary formulas and the two consumption formulas are implemented as exact integer arithmetic and pass every golden value in `docs/CONTRACTS.md §2`.
- A test proves no monetary or consumption path uses `Float` or `Double`.
- `:core:testing` exposes `testAppGraphDependencies(...)` with every parameter defaulted to a fake, including a no-op `CrashReporter`.
- Kover thresholds pass for `:core:model` and `:core:common`.

### E0-04 - Architecture Guards - M

Implement module-level and package-level dependency checks per `docs/TECHNICAL_PLAN.md §4`.

Acceptance criteria:

- Feature `domain` dependency on Room, Firebase, Koin, Android, Ktor, own `data` or own `presentation` fails the build with a rule-specific message.
- Feature `data` dependency on `:core:auth` or `:integration:*` fails the build.
- Feature-to-feature dependency fails the build.
- A `:core:model` dependency on `:core:common` fails the build; the reverse is allowed (`docs/TECHNICAL_PLAN.md §4`).
- Moving `ConsumptionInvalidReason` or `SegmentResult` out of `:core:model` into `:core:common` fails the build.
- `:core:sync` or `:shared` dependency on `:integration:*` fails the build.
- Feature `presentation` dependency on feature `data` fails the build.
- `:core:crash` dependency on Firebase, GitLive, Koin, Ktor, platform APIs, integrations or features fails the build.
- An `expect`/`actual` declaration inside `:core:crash` fails the build.
- An `:integration:*` reference to `createAppGraph` fails the build; a Koin `Module` declaration inside `:integration:*` is allowed.
- The Phase 0 module set above is enforced: `:core:auth`, `:core:database` and `:core:sync` are not created by Phase 0 stories.
- `Float` and `Double` usage in `:core:*`, `:feature:*` or `:shared` arithmetic paths fails unless it is an explicitly allowlisted platform display conversion.
- `Logger.log` calls with string field values outside stable code, enum-name or `cycleId` patterns fail the source rule.
- `Logger.log` calls from `:core:database` are rejected except for local-database-only failures.
- Reads of `outbox.lastError` outside logging, debug UI and mapper projections fail; sync logic may read only `lastErrorCode`.
- Image-loading dependencies in `gradle/libs.versions.toml` fail unless a story reference and the Coil decision path of `docs/CONTRACTS.md §15.5` are present.
- Writes to `currentOdometerKm` or `odometerInconsistent` outside `:core:database` fail the build.
- **Every rule has a failing fixture proving the check actually fires.**
- The check configuration is generated from the `docs/TECHNICAL_PLAN.md §4` table.
- The check runs on every PR.

### E0-05 - Quality Tooling and CI - M

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

Validate all decision records and pin the toolchain.

Acceptance criteria:

- One ADR exists per `Accepted` or `Deferred` decision, with the ADR `Status` equal to the board status.
- `docs/adr/README.md` maps every decision ID to its ADR file.
- The decision ID set and status values are identical across `docs/DECISION_BOARD.md`, `docs/SPECIFICATION.md §12`, `docs/TECHNICAL_PLAN.md §2` and `docs/adr/README.md`.
- `docs/versions-matrix.md` pins JDK, Gradle, AGP, Kotlin, KSP, Compose, Room, `androidx.sqlite`, SKIE, Xcode, Firebase BOM, GitLive, coroutines, serialization, datetime, Koin, Kermit, Turbine and Kover, with the compatibility relation and the exact `Instant` package.
- Every `TBD` cell is replaced by a concrete pinned value with a citation from the corresponding ADR.
- A unit test imports the pinned datetime type package at build time.
- `docs/versions-matrix.md` fixes the reference devices and the measurement method for every performance target.
- Version choices are reflected in `gradle/libs.versions.toml` and nowhere else.

Blocks: E0-07.

### E0-08 - `:core:analytics` Abstraction - S

Create `:core:analytics` with the `AnalyticsTracker` interface and the closed `AnalyticsEvent` hierarchy of `docs/CONTRACTS.md §16.1` and `§20.9`. Abstraction only: no Firebase dependency, no provider SDK.

Acceptance criteria:

- `AnalyticsTracker`, `AnalyticsEvent`, `AnalyticsUserProperties`, `SyncStatusCategory`, `ConversionFailureReason`, `DeletionFailureReason` and `CountBucket` match `docs/CONTRACTS.md §20.9` exactly.
- `AnalyticsEvent` is a closed sealed hierarchy and no leaf carries a free-text `String`, proven by an exhaustive `when` over every leaf.
- A no-op `AnalyticsTracker` lives in `:core:testing` and is the default in `testAppGraphDependencies(...)`, so `AppGraphDependencies` is complete without any Firebase module.
- `setEnabled(false)` makes `track` and `setUserProperties` no-ops that buffer nothing.
- No Firebase, GitLive or Android type appears in this module.

Why Phase 0: `AnalyticsTracker` is a mandatory member of `AppGraphDependencies` (`docs/CONTRACTS.md §11.6`), so the graph cannot be constructed or tested without it. The split mirrors `:core:auth` and `:integration:firebase-auth`.

### E0-07 - Walking Skeleton - L

Build a single screen crossing native UI, shared state holder, Room, Firestore and real anonymous auth.

Acceptance criteria:

- A value written on one platform can be backed up remotely and restored on a clean second device.
- `iosSimulatorArm64` runs in CI.
- iOS consumes the shared framework through direct SPM integration, not CocoaPods.
- Firestore offline persistence is disabled.
- The Firestore database exists in the location fixed by `D-13`, in the development Firebase project fixed by `D-22` and governed by `D-14`.
- The Swift-facing surface constraints of `docs/CONTRACTS.md §15.3` are validated: no value class, project-owned type parameter, default argument, `CoroutineScope`, `Outcome`, `AppError`, repository, use case, command model or `AppGraphDependencies` appears in the exported API, and the generated Objective-C header is committed as `shared/build/generated/objc-header/Shared.h.golden`.
- The Objective-C header contains the exported allowlist of `docs/CONTRACTS.md §20.10`, including `createSwiftAppGraph(isDebugBuild)`, `SwiftAppGraph`, concrete state holders and `UiState` classes, and omits the Kotlin-facing `createAppGraph(AppGraphDependencies)`, `AppGraph` and `SyncController`.
- The `objc-header-golden-check` CI step compares the generated header with the committed golden file.
- `shared/README.md` documents every Swift-facing scale suffix from `docs/CONTRACTS.md §20.10` for iOS consumers.

Decision gate:

- If Room KMP/KSP blocks iOS progress, switch to SQLDelight the same day. The switch MUST land with a superseding ADR, a `docs/DECISION_BOARD.md` status change on `D-1` and an update to `E1-01`, in the same PR.

Human review required.

## Phase 1 - Local Persistence

Goal: the app stores and displays vehicles and fuel entries locally. It is useful without remote backup, and works from a first launch with no connectivity.

### E1-01 - `:core:database` - M

Implement Room 3.0 KMP with bundled SQLite, schema v1, DAOs, transactions and migration strategy.

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
- `exportSchema = true`, schema JSON committed, `fallbackToDestructiveMigration` absent, and a v1 migration test exists.
- Observable queries return `Flow`; one-shot queries are `suspend`.

### E1-02 - Vehicle Domain - S

Implement the `:feature:vehicle` domain package: entity, repository interface and use cases.

Acceptance criteria:

- Domain is Kotlin pure.
- Normalisation runs before validation per `docs/CONTRACTS.md §5`.
- Name validation and `nameFold` uniqueness use `canonicalVehicleName(name).lowercase()` exactly as defined in `docs/CONTRACTS.md §5`.
- `initialOdometerKm` range and its edit restriction are implemented.
- `FuelType` exists with exactly the MVP values of `docs/CONTRACTS.md §20.4` and default `GASOLINE`; `ELECTRIC` and `HYBRID` are absent until a future energy-model story expands the enum.
- Commands match `docs/CONTRACTS.md §20.5`; no command carries `ownerId`, `id` or timestamps.
- Use cases have unit tests for success and for every error they declare.

### E1-03 - Vehicle Data, Local Only - M

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
- The repository filter step is covered: `observeConsumption` passes only non-deleted entries for one vehicle to `CalculateConsumption`.
- 1,000 entries processed within the target of `docs/SPECIFICATION.md §11`, measured as defined in `docs/versions-matrix.md`.

Human review required.

### E1-06 - Fuel Entry Data, Local Only - M

Implement the local data source, mappers, projections and repository implementation for fuel entries.

Acceptance criteria:

- Queries support both orderings of `docs/CONTRACTS.md §4`.
- `observeFuelEntries` returns the `FuelEntryListItem` projection in chronological order and excludes orphan entries.
- `FuelEntryListItem` maps partial rows to `consumption = null` and `invalidReason = EndEntryNotFullTank`, while still allowing those rows to contribute litres to the next full segment.
- `observeConsumption` is backed by a dedicated projection query, not by the UI list.
- Created and edited rows become `PENDING`; no outbox row while `LOCAL_OWNER`; `LOCAL_OWNER + PENDING + no outbox` is a valid stored state.
- Created, edited and tombstoned rows receive a fresh `localMutationSeq` from the shared local sequence.
- Logical delete works and triggers the `docs/CONTRACTS.md §3.1` recompute set.
- Mappers have round-trip tests.

### E1-07 - Android UI: Vehicles - M

Implement the vehicle list, create/edit form and detail shell with a shared presentation state holder.

Acceptance criteria:

- The state holder lives in `commonMain`, takes a `CoroutineScope`, exposes `close()`, and emits on `dispatchers.main`.
- `UiState` contains no user-facing text.
- Loading, empty and error states exist and map typed errors to platform strings.
- Spanish and English strings exist; no hardcoded user-facing strings.
- Vehicle creation UI test exists.

### E1-08 - Android UI: Fuel Entries - L

Implement the fuel entry list, create/edit form, segment consumption display and average consumption display.

Acceptance criteria:

- Form defaults follow F-3, including the `hasMissedEntries` secondary toggle.
- The derived R-2 value recalculates while typing, using `MoneyInput`.
- The odometer warning dialog implements the two-step confirmation.
- Entries with no consumption show an accessible explanation derived from `ConsumptionInvalidReason`, including `EndEntryNotFullTank` for partial refuels.
- Empty consumption state follows the specification.

### E1-09 - iOS UI: Vehicles and Fuel Entries - L

Implement SwiftUI screens consuming the shared state holders through `AppGraph`.

Acceptance criteria:

- No business logic is duplicated in Swift.
- State holder scopes are created in `init` and cancelled in `deinit`.
- Functional parity with Android for F-2 and F-3.
- Fuel-entry consumption and no-consumption explanations match Android, including `EndEntryNotFullTank` for partial refuels.
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
- Cancelled system dialogs produce `AuthError.Cancelled`.
- A provider flow that would change the UID produces `AuthError.UidWouldChange` and aborts.
- `AuthClient.deleteAccount()` calls the `D-23` server/Admin operation and never calls the mobile Firebase Auth account deletion API directly.
- Account deletion verifies token freshness using `FRESH_LOGIN_THRESHOLD_MS` before calling the server operation, and triggers re-authentication if the token is stale.
- Native UI obtains credentials; common code exchanges them.
- No GitLive or Firebase type crosses the module boundary.

### E2-03 - Onboarding Flow F-1 - M

Implement the welcome screen and provider selection, with an offline-capable local start.

Acceptance criteria:

- "Continue without account" succeeds with no connectivity and creates a `LOCAL_OWNER` session.
- With connectivity, "Continue without account" creates a Firebase anonymous user automatically and does not enter `LOCAL_OWNER`.
- Routing never happens while `AuthState.Unknown`.
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
- Collision offers an explicit destructive choice showing the data-loss count, gated by `Confirmation.AdoptExistingAccount`.
- Cancelling leaves the anonymous session and local data untouched.
- Automatic merge is not implemented.

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

## Phase 3 - Backend and Synchronization

### E3-01 - Firestore Structure and Security Rules - M

Create the Firestore rules, indexes and emulator tests.

Acceptance criteria:

- Rules match `docs/CONTRACTS.md §16`, split by operation, with `allow delete: if false`.
- `validPayload()` enforces the closed remote `Vehicle` and `FuelEntry` schemas from `docs/CONTRACTS.md §16`, including required keys, extra-key rejection, unknown-collection rejection, local-only-key rejection, primitive type, enum, nullability, range checks and `deleted == (deletedAt != null)`.
- Nullable-field emulator tests prove `brand: null` is accepted and missing `brand` is rejected.
- Emulator tests prove `CLIENT_MAX_SCHEMA_VERSION` matches the accepted remote `schemaVersion`, orphan fuel entries are accepted, and literal `updatedAt` values that differ from `request.time` are rejected.
- `firestore/rules/main.rules` contains the `validPayload()` helpers required by `docs/CONTRACTS.md §16`.
- Every emulator test listed in `docs/CONTRACTS.md §16` passes.
- `firestore/firestore.indexes.json` exists and the pull query runs without an index error.
- Firestore offline persistence is disabled in client configuration.

Human review required.

### E3-10 - Account Deletion Server Operation - M

Implement the Firebase Admin account deletion operation selected by `D-23`.

Acceptance criteria:

- The operation accepts only an authenticated caller and verifies that the caller UID is the target UID.
- The operation deletes documents under `users/{uid}` in the normative order: `fuelEntries`, then `vehicles`.
- A server-side test proves the operation does not delete vehicles before fuel entries.
- The operation deletes the Firebase Auth user only after remote document deletion succeeds.
- The operation is idempotent for already-deleted documents and missing auth users, but never deletes outside `users/{uid}`.
- Failures before Firebase Auth deletion return a typed failure and are not reported as success to the app.
- Logs are redacted according to `docs/CONTRACTS.md §17` and `docs/SECURITY.md`.
- Emulator or server-side tests prove the happy path, retry/idempotency behavior, caller UID rejection and failure-before-auth-deletion behavior.
- Firestore emulator tests still prove that mobile client hard deletes are rejected by `allow delete: if false`.

Human review required.

### E3-02 - Firestore RemoteSyncSource - M

Implement the Firestore remote sync integration.

Acceptance criteria:

- Writes use `serverTimestamp()` and the client document ID.
- Delta pull applies the overlap once per cycle, uses `startAt(overlapSince, "")` for the first page and `startAfter(lastServerUpdatedAt, lastDocumentId)` for later pages.
- A resumed-cycle emulator test proves `startAt(overlapSince, "")` works after the first non-empty pull.
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

Human review required.

### E3-08 - App Graph and Firebase Wiring - M

Implement `createAppGraph`, the Kotlin-facing `AppGraph`, `createSwiftAppGraph(isDebugBuild)`, the Swift-facing `SwiftAppGraph` and `:wiring:firebase`.

Acceptance criteria:

- `AppGraphDependencies`, the Kotlin-facing `AppGraph`, `createSwiftAppGraph(isDebugBuild)`, `SwiftAppGraph`, exported state holders and exported `UiState` classes match `docs/CONTRACTS.md §11.6` and `§20.10`.
- `testAppGraphDependencies(...)` mirrors the exact `AppGraphDependencies` parameter order and defaults every parameter.
- Only `:wiring:firebase` constructs Firebase implementations.
- Every top-level declaration in `:wiring:firebase` is a Koin module, an abstraction factory or a platform initialiser.
- Tests build the graph from `testAppGraphDependencies(...)` without starting Koin.
- The Swift facade exposes a sync state holder, not `SyncController`, and it owns/cancels the scopes for state holders it creates.
- `SwiftAppGraph` state-holder factories are cached/idempotent for identical arguments, and throw after `SwiftAppGraph.close()`.

### E3-04 - Repository Sync Wiring - M

Replace no-op remote sources with real sync wiring and platform triggers.

Acceptance criteria:

- The UI still observes only local database flows.
- The five triggers of `docs/CONTRACTS.md §9.8` exist with the stated constants.
- Platform workers only call `SyncController.requestSync(reason)`.
- No state holder change is required for sync correctness.

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

Acceptance criteria:

- Excluding `:integration:*` and `:wiring:firebase` leaves `:core:*` and `:feature:*` compiling and testing with fakes.
- The check runs in CI under the name `provider-decoupling`.

## Phase 4 - MVP Hardening

### E4-01 - Settings UI - S

Implement the settings screen over the Phase 1 persistence.

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
- No memory leaks in critical flows; state holder scopes are cancelled.

### E4-04 - Release Preparation - M

Prepare release assets and store requirements.

Acceptance criteria:

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
      -> E0-02, E0-03, E0-04, E0-05, E0-06
          -> E0-08 :core:analytics
          -> E0-07 walking skeleton gate
              -> Phase 1 local persistence
              -> Phase 2 auth can overlap with late Phase 1; E2-06 must precede E3-04
              -> Phase 3 can start E3-01 early, but sync wiring depends on Phases 1 and 2
              -> Phase 4
```

`E0-08` is a hard prerequisite for `E0-07` because `AppGraphDependencies` requires `AnalyticsTracker`.

## Story Index

| Story | Phase | Size | Human gate |
|-------|-------|------|------------|
| E0-00 Owner decision closure (completed) | 0 | S | Yes |
| E0-01 KMP bootstrap | 0 | M | — |
| E0-02 Convention plugins | 0 | M | — |
| E0-03 Base core modules | 0 | M | — |
| E0-04 Architecture guards | 0 | M | — |
| E0-05 Quality tooling and CI | 0 | M | — |
| E0-06 ADRs and version matrix | 0 | S | — |
| E0-08 `:core:analytics` abstraction | 0 | S | — |
| E0-07 Walking skeleton | 0.5 | L | Yes |
| E1-01 `:core:database` | 1 | M | — |
| E1-02 Vehicle domain | 1 | S | — |
| E1-03 Vehicle data | 1 | M | — |
| E1-04 Fuel entry domain | 1 | M | — |
| E1-05 Consumption calculation | 1 | M | Yes |
| E1-06 Fuel entry data | 1 | M | — |
| E1-07 Android UI vehicles | 1 | M | — |
| E1-08 Android UI fuel entries | 1 | L | — |
| E1-09 iOS UI | 1 | L | — |
| E1-10 Settings persistence | 1 | S | — |
| E2-01 `:core:auth` | 2 | S | — |
| E2-02 Firebase Auth integration | 2 | L | — |
| E2-03 Onboarding F-1 | 2 | M | — |
| E2-06 Local owner adoption | 2 | M | Yes |
| E2-04 Account conversion F-4 | 2 | M | — |
| E2-05 Sign-out and deletion F-5 | 2 | M | — |
| E3-01 Firestore rules | 3 | M | Yes |
| E3-10 Account deletion server operation | 3 | M | Yes |
| E3-02 Firestore RemoteSyncSource | 3 | M | — |
| E3-03 `:core:sync` engine | 3 | L | Yes |
| E3-08 App graph and wiring | 3 | M | — |
| E3-04 Repository sync wiring | 3 | M | — |
| E3-05 Backup status UI | 3 | S | — |
| E3-07 Tombstone purge | 3 | S | — |
| E3-09 Firebase Analytics integration | 3 | S | — |
| E3-06 Provider decoupling proof | 3 | S | — |
| E4-01 Settings UI | 4 | S | — |
| E4-02 Accessibility and localization | 4 | M | — |
| E4-03 Performance hardening | 4 | M | — |
| E4-04 Release preparation | 4 | M | — |

Human review gates are defined canonically in `AGENTS.md`. The column above is a convenience index, not a second source.
