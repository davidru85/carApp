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

## Phase 0 - Foundations

Goal: a project skeleton that compiles on both platforms and has enforceable architecture boundaries.

**Entry condition:** the owner has confirmed or changed every `Proposed` decision listed as "needed before Phase 0" in `docs/DECISION_BOARD.md` — at minimum `D-13`, `D-14`, `D-15`, `D-16`, `D-19`, `D-22`. `E0-01` MUST NOT start before that.

### E0-00 - Owner Decision Closure - S

Turn the `Proposed` and Phase-0 `Pending` rows of `docs/DECISION_BOARD.md` into `Accepted` (or a different choice), and materialise the supporting documents.

Acceptance criteria:

- Every Phase 0 row in "Decisions Awaiting Owner Confirmation" is `Accepted`, `Rejected` or explicitly re-`Deferred`.
- `docs/identifiers.md` contains the final applicationId, bundle identifier, Kotlin namespace, display name, Firebase project IDs and Firestore location.
- One ADR exists per newly accepted decision.
- `docs/SPECIFICATION.md §12`, `docs/TECHNICAL_PLAN.md §2` and `docs/adr/README.md` mirror `docs/DECISION_BOARD.md` exactly.

Blocks: E0-01.

Human review required.

### E0-01 - KMP Project Bootstrap - M

Create the KMP project with Android and iOS targets, Android host app, SwiftUI iOS host app, and `:shared` framework.

Acceptance criteria:

- Android debug app builds.
- iOS simulator app builds and shows text coming from `commonMain`.
- Build scripts use Kotlin DSL only.
- `gradle/libs.versions.toml` is the single source of dependency versions.
- Identifiers exactly match `docs/identifiers.md`; nothing is invented.

Blocks: all other stories.

### E0-02 - Gradle Convention Plugins - M

Create convention plugins for KMP libraries, features, Android application, Compose, Room and SKIE.

Acceptance criteria:

- Creating a new module requires no more than five lines in its `build.gradle.kts`.
- SKIE is applied only to `:shared`.
- Test and Kotlin toolchain configuration is centralized.
- Plugins make future feature splitting possible without redesign.

### E0-03 - Base Core Modules - M

Create `:core:model`, `:core:common` and `:core:testing`, implementing the canonical types of `docs/CONTRACTS.md §20`.

Acceptance criteria:

- `Outcome`, the full `AppError` hierarchy with stable codes, `Confirmation`, `AppClock`, `UuidGenerator`, `DispatcherProvider`, `Logger`, `LocaleProvider`, `ConnectivityObserver`, `OwnerContext`, `SyncTrigger` and `MinorUnits` exist and match `docs/CONTRACTS.md §20` exactly.
- `EntityId`, `OwnerId`, `CurrencyCode`, `Money`, `FuelVolume`, `PricePerLiter`, `ConsumptionL100Km` and `LOCAL_OWNER` match `docs/CONTRACTS.md §20.0` exactly, including the canonical property names `value` and `scaled`, and every scaled value is a `Long`.
- None of those types validates on construction: a test proves that wrapping a malformed UUID and an unsupported currency code succeeds, because the pull path of `docs/CONTRACTS.md §5` may not fail on a domain constraint.
- The named constants of `docs/CONTRACTS.md §20.0.1` exist in `:core:common`, and no story writes their literals inline.
- The three canonical monetary formulas and the two consumption formulas are implemented as exact integer arithmetic and pass every golden value in `docs/CONTRACTS.md §2`.
- A test proves no monetary or consumption path uses `Float` or `Double`.
- `:core:testing` exposes `testAppGraphDependencies(...)` with every parameter defaulted to a fake.
- Kover thresholds pass for `:core:model` and `:core:common`.

### E0-04 - Architecture Guards - M

Implement module-level and package-level dependency checks per `docs/TECHNICAL_PLAN.md §4`.

Acceptance criteria:

- Feature `domain` dependency on Room, Firebase, Koin, Android, Ktor, own `data` or own `presentation` fails the build with a rule-specific message.
- Feature `data` dependency on `:core:auth` or `:integration:*` fails the build.
- Feature-to-feature dependency fails the build.
- A `:core:model` dependency on `:core:common` fails the build; the reverse is allowed (`docs/TECHNICAL_PLAN.md §4`).
- `:core:sync` or `:shared` dependency on `:integration:*` fails the build.
- Feature `presentation` dependency on feature `data` fails the build.
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
- `contract-check` implements the four assertions of `docs/CONTRACTS.md §18` and fails when any is violated.
- Branch protection for `main` requires those checks.

CI duration under 20 minutes is an objective, monitored and reported, not a pass/fail criterion.

### E0-06 - ADRs, Version Matrix and Decision Board Validation - S

Validate all decision records and pin the toolchain.

Acceptance criteria:

- One ADR exists per `Accepted` or `Deferred` decision, with the ADR `Status` equal to the board status.
- `docs/adr/README.md` maps every decision ID to its ADR file.
- The decision ID set is identical across `docs/DECISION_BOARD.md`, `docs/SPECIFICATION.md §12`, `docs/TECHNICAL_PLAN.md §2` and `docs/adr/README.md`.
- `docs/versions-matrix.md` pins JDK, Gradle, AGP, Kotlin, KSP, Compose, Room, `androidx.sqlite`, SKIE, Xcode, Firebase BOM, GitLive, coroutines, serialization, datetime, Koin, Kermit, Turbine and Kover, with the compatibility relation and the exact `Instant` package.
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

- A value written on Android appears on iOS after sync, and the reverse.
- `iosSimulatorArm64` runs in CI.
- iOS consumes the shared framework through direct SPM integration, not CocoaPods.
- Firestore offline persistence is disabled.
- The Firestore database exists in the location fixed by `D-13`, in the `dev` project of `D-14`.
- The Swift-facing surface constraints of `docs/CONTRACTS.md §15.3` are validated: no value class, type parameter or default argument in the exported API, and the generated Objective-C header is committed as a golden file.

Decision gate:

- If Room KMP/KSP blocks iOS progress, switch to SQLDelight the same day. The switch MUST land with a superseding ADR, a `docs/DECISION_BOARD.md` status change on `D-1` and an update to `E1-01`, in the same PR.

Human review required.

## Phase 1 - Local Persistence

Goal: the app stores and displays vehicles and fuel entries locally. It is useful without remote sync, and works from a first launch with no connectivity.

### E1-01 - `:core:database` - M

Implement Room 3.0 KMP with bundled SQLite, schema v1, DAOs, transactions and migration strategy.

Acceptance criteria:

- Database instantiates and persists on Android and iOS.
- Bundled SQLite is used.
- Tables: `vehicle`, `fuel_entry`, `user_settings`, `outbox`, `sync_cursor`, `quarantine`.
- Synchronized entities include every control column of `docs/TECHNICAL_PLAN.md §6`, with `CHECK(deleted = (deletedAt IS NOT NULL))`.
- There is **no** foreign key from `fuel_entry` to `vehicle`, and **no** unique index on the vehicle name.
- Outbox matches the committed DDL, including `lastErrorCode` and `idx_outbox_due`, and preserves the original `seq` when coalescing.
- `currentOdometerKm` and `odometerInconsistent` are recomputed inside `:core:database` for every fuel-entry write, and a test proves they stay correct after an edit and after a delete of a neighbouring entry.
- `exportSchema = true`, schema JSON committed, `fallbackToDestructiveMigration` absent, and a v1 migration test exists.
- Observable queries return `Flow`; one-shot queries are `suspend`.

### E1-02 - Vehicle Domain - S

Implement the `:feature:vehicle` domain package: entity, repository interface and use cases.

Acceptance criteria:

- Domain is Kotlin pure.
- Normalisation runs before validation per `docs/CONTRACTS.md §5`.
- Name validation and `nameFold` uniqueness are implemented as a local pre-write check.
- `initialOdometerKm` range and its edit restriction are implemented.
- `fuelType` exists with default `GASOLINE`.
- Commands match `docs/CONTRACTS.md §20.5`; no command carries `ownerId`, `id` or timestamps.
- Use cases have unit tests for success and for every error they declare.

### E1-03 - Vehicle Data, Local Only - M

Implement the local data source, mappers and repository implementation for vehicles.

Acceptance criteria:

- Mappers have round-trip tests.
- `ownerId` is stamped from `OwnerContext`; the module does not reference `AuthClient`.
- Created and edited rows become `PENDING`.
- **No outbox row is created while the owner is `LOCAL_OWNER`.**
- Vehicle deletion is logical and cascades to fuel entries in one transaction.
- No Firebase or GitLive type is referenced.

### E1-04 - Fuel Entry Domain - M

Implement the `:feature:fuel` domain package, CRUD use cases and rules R-1 and R-2.

Acceptance criteria:

- `MoneyInput` is the only way to supply monetary values, and all three derivations pass the golden values.
- `totalCostMinor` is integer minor units; no monetary path uses `Float` or `Double`.
- The two-step warning protocol is implemented: an unconfirmed inconsistent odometer returns `ValidationWarning.OdometerInconsistent` and mutates nothing; the same command with the confirmation succeeds.
- R-1 is enforced on edit as well as on create.
- Every bound in the `docs/CONTRACTS.md §5` validation table is enforced at both ends.
- A currency without a supported minor-unit factor returns `ValidationError.InvalidUnit`.

### E1-05 - Consumption Calculation R-3 - M

Implement the pure `CalculateConsumption` use case.

Acceptance criteria:

- Happy path with two full tanks.
- First full tank produces `NoPreviousFullTank`.
- Partial intermediate refuel contributes to the next full segment.
- An entry sharing `odometerKm` with `P` is counted in the segment litres and yields `DuplicateOdometerInSegment`.
- `hasMissedEntries = true` on entry `E` invalidates the segment ending at `E` and any segment containing `E`, and leaves earlier segments valid — per `docs/CONTRACTS.md §3`. A partial entry flagged `hasMissedEntries` therefore invalidates the segment ending at the *next* full tank.
- `odometerInconsistent` invalidates the containing segment.
- `distanceKm <= 0` yields `NonPositiveDistance` and never divides by zero.
- Calculation order is `odometerKm, date, id`, and a back-dated entry does not change the result.
- Segment and average values are produced by the canonical consumption arithmetic of `docs/CONTRACTS.md §2`, and all four golden values in that section pass.
- Average consumption is distance-weighted, not an arithmetic mean; the golden case where the two differ (`774` versus `776`) is covered by a test.
- The function is total: no input throws.
- 1,000 entries processed within the target of `docs/SPECIFICATION.md §11`, measured as defined in `docs/versions-matrix.md`.

Human review required.

### E1-06 - Fuel Entry Data, Local Only - M

Implement the local data source, mappers, projections and repository implementation for fuel entries.

Acceptance criteria:

- Queries support both orderings of `docs/CONTRACTS.md §4`.
- `observeFuelEntries` returns the `FuelEntryListItem` projection and excludes orphan entries.
- `observeConsumption` is backed by a dedicated projection query, not by the UI list.
- Created and edited rows become `PENDING`; no outbox row while `LOCAL_OWNER`.
- Logical delete works and triggers read-model recomputation.
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
- Entries with no consumption show an accessible explanation derived from `ConsumptionInvalidReason`.
- Empty consumption state follows the specification.

### E1-09 - iOS UI: Vehicles and Fuel Entries - L

Implement SwiftUI screens consuming the shared state holders through `AppGraph`.

Acceptance criteria:

- No business logic is duplicated in Swift.
- State holder scopes are created in `init` and cancelled in `deinit`.
- Functional parity with Android for F-2 and F-3.
- Dynamic Type is usable for critical flows.

### E1-10 - Settings Persistence - S

Implement the local `user_settings` table, `SettingsRepository` and `UpdateSettingsCommand`.

Acceptance criteria:

- A single row is created on first launch with the locale-derived currency, `EUR` fallback, and `analyticsEnabled = false`.
- Only supported 2-decimal currencies are accepted.
- Changing the currency does not rewrite existing fuel entries.
- Settings are device-local: nothing is enqueued and there is no remote document.

## Phase 2 - Authentication

### E2-01 - `:core:auth` - S

Implement the auth interfaces and models.

Acceptance criteria:

- `AuthClient`, `TokenProvider`, `AuthSession`, `AuthState`, `NativeAuthCredential`, `AuthToken` and the typed `AuthError` match `docs/CONTRACTS.md §20.8`.
- `AuthState.Unknown` is distinct from `SignedOut`.
- `OwnerContext` is implemented here and bound in wiring; feature modules do not see `AuthClient`.
- No Firebase type appears in this module.

### E2-02 - Firebase Auth Integration - L

Implement anonymous, Google, Apple, credential linking, re-authentication, sign-out, account deletion and token refresh.

Acceptance criteria:

- Anonymous login works on both platforms; Google works on both; Apple works on iOS.
- Cancelled system dialogs produce `AuthError.Cancelled`.
- A provider flow that would change the UID produces `AuthError.UidWouldChange` and aborts.
- Native UI obtains credentials; common code exchanges them.
- No GitLive or Firebase type crosses the module boundary.

### E2-03 - Onboarding Flow F-1 - M

Implement the welcome screen and provider selection, with an offline-capable local start.

Acceptance criteria:

- "Continue without account" succeeds with no connectivity and creates a `LOCAL_OWNER` session.
- Routing never happens while `AuthState.Unknown`.
- iOS offers Apple whenever Google is offered.
- Retry after network failure does not leave the UI stuck.
- Routing after authentication depends on whether vehicles exist.

### E2-06 - Local Owner Adoption - M

Adopt `LOCAL_OWNER` data into the first real UID.

Acceptance criteria:

- On the first successful authentication, all `LOCAL_OWNER` rows are rewritten to the new UID in one transaction, `localRevision` is bumped and an outbox snapshot is enqueued for every non-synced row, preserving `seq` causality.
- The operation is idempotent: running it twice enqueues each row once.
- A test starts from a populated `LOCAL_OWNER` database with vehicles and fuel entries and asserts nothing is lost and everything syncs.
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
- Sign-out warns about pending sync and offers to wait, cancel or discard.
- Account deletion follows the order of `docs/CONTRACTS.md §11.5`: re-authenticate if required, delete remote data in batches, then the auth account, then local data.
- A failure during remote deletion aborts with a typed error and does NOT delete the account.
- Account deletion is accessible from settings.

## Phase 3 - Backend and Synchronization

### E3-01 - Firestore Structure and Security Rules - M

Create the Firestore rules, indexes and emulator tests.

Acceptance criteria:

- Rules match `docs/CONTRACTS.md §16`, split by operation, with `allow delete: if false`.
- `validPayload()` enforces presence, type and range for every field.
- Every emulator test listed in `docs/CONTRACTS.md §16` passes.
- `firestore/firestore.indexes.json` exists and the pull query runs without an index error.
- Firestore offline persistence is disabled in client configuration.

Human review required.

### E3-02 - Firestore RemoteSyncSource - M

Implement the Firestore remote sync integration.

Acceptance criteria:

- Writes use `serverTimestamp()` and the client document ID.
- Delta pull uses `startAfter` on `(updatedAt, documentId)` and is paginated.
- Firestore errors map to `RemoteError` exactly as in `docs/CONTRACTS.md §6`.
- An `Unauthenticated` response forces a token refresh and retries once inside this module.
- On an empty page, `nextCursor` equals the input cursor and `hasMore` is false.
- No Firestore or GitLive type crosses the module boundary.

### E3-03 - `:core:sync` Engine - L

Implement the outbox, cursor, push, pull, LWW, overlap window, backoff, quarantine, aggregate status and debug support according to `docs/CONTRACTS.md §7`–`§9`.

Acceptance criteria:

- All 17 sync tests in `docs/TECHNICAL_PLAN.md §9` pass.
- A cycle is not started while `ConnectivityObserver.isOnline` is false, and connectivity failures never poison a row (`docs/CONTRACTS.md §9.2`, `§9.7`).
- The deterministic convergence simulation exists with a fixed seed and an injected jitter source.
- The state machine matches `docs/CONTRACTS.md §7`, including `SYNCING -> SYNCING` on a local edit during an in-flight push.
- Only one cycle runs at a time per owner, enforced by a mutex in `SyncController`.
- Trigger constants match `docs/CONTRACTS.md §9.8`.
- Debug screen exposes the outbox, cursors, quarantine and row sync state.

Human review required.

### E3-08 - App Graph and Firebase Wiring - M

Implement `createAppGraph`, `AppGraph` and `:wiring:firebase`.

Acceptance criteria:

- `AppGraphDependencies` and `AppGraph` match `docs/CONTRACTS.md §11.6` and `§20.10`.
- Only `:wiring:firebase` constructs Firebase implementations.
- Every top-level declaration in `:wiring:firebase` is a Koin module, an abstraction factory or a platform initialiser.
- Tests build the graph from `testAppGraphDependencies(...)` without starting Koin.

### E3-04 - Repository Sync Wiring - M

Replace no-op remote sources with real sync wiring and platform triggers.

Acceptance criteria:

- The UI still observes only local database flows.
- The five triggers of `docs/CONTRACTS.md §9.8` exist with the stated constants.
- Platform workers only call `SyncController.requestSync(reason)`.
- No state holder change is required for sync correctness.

### E3-05 - Sync Status UI - S

Add a non-intrusive sync status indicator.

Acceptance criteria:

- `SyncStatus` is rendered with the precedence `Failed > Syncing > Pending > Idle`.
- Being offline with pending rows renders as `Pending`, never as an error.
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
- Release builds are installable on both platforms.
- Account deletion and Apple sign-in requirements are satisfied.
- Release logging redaction is verified: no UID, notes, odometer or cost values in release logs.

## Execution Order

```text
E0-00 owner decisions
  -> Phase 0
      -> E0-07 walking skeleton gate
          -> Phase 1 local persistence
          -> Phase 2 auth can overlap with late Phase 1; E2-06 must precede E3-04
          -> Phase 3 can start E3-01 early, but sync wiring depends on Phases 1 and 2
          -> Phase 4
```

## Story Index

| Story | Phase | Size | Human gate |
|-------|-------|------|------------|
| E0-00 Owner decision closure | 0 | S | Yes |
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
| E3-02 Firestore RemoteSyncSource | 3 | M | — |
| E3-03 `:core:sync` engine | 3 | L | Yes |
| E3-08 App graph and wiring | 3 | M | — |
| E3-04 Repository sync wiring | 3 | M | — |
| E3-05 Sync status UI | 3 | S | — |
| E3-07 Tombstone purge | 3 | S | — |
| E3-09 Firebase Analytics integration | 3 | S | — |
| E3-06 Provider decoupling proof | 3 | S | — |
| E4-01 Settings UI | 4 | S | — |
| E4-02 Accessibility and localization | 4 | M | — |
| E4-03 Performance hardening | 4 | M | — |
| E4-04 Release preparation | 4 | M | — |

Human review gates are defined canonically in `AGENTS.md`. The column above is a convenience index, not a second source.
