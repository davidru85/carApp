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
| D-1 | Local database | Room 3.0 KMP with `androidx.sqlite:sqlite-bundled` | Accepted | Same SQLite version across Android and iOS, supports modern UPSERT syntax with `minSdk 26`. |
| D-2 | Swift interop | SKIE only in `:shared` | Accepted | Better Swift ergonomics for Flow and sealed-like models than raw KMP export. |
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
| D-13 | Firestore location | `europe-west1` | Accepted | Firestore is a backup and recovery replica only; Room is the source of truth. The location is immutable after database creation. |
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
:core:database                  Room entities, DAOs, migrations, platform builders, read-model invariants
:core:auth                      AuthClient, TokenProvider, AuthState
:core:sync                      Outbox, cursor, backup/recovery engine, SyncController, RemoteSyncSource
:core:analytics                 AnalyticsTracker and the closed AnalyticsEvent hierarchy
:core:crash                     CrashReporter abstraction and no-op implementation
:core:testing                   fakes, builders, in-memory remote, deterministic simulator,
                                testAppGraphDependencies factory

:integration:firebase-auth      Firebase Auth implementation
:integration:firebase-firestore Firestore RemoteSyncSource implementation
:integration:firebase-analytics Firebase Analytics implementation
:integration:firebase-crashlytics Firebase Crashlytics implementation, Phase 4

:feature:vehicle                domain/data/presentation packages
:feature:fuel                   domain/data/presentation packages
:feature:session                onboarding, auth, settings packages

:shared                         iOS framework and shared graph factory
:wiring:firebase                composition root that names Firebase integrations
:androidApp                     Android host app
iosApp/                         SwiftUI host app
firestore/                      rules and indexes
```

Each feature is one Gradle module. Layer separation is enforced by package-level source analysis, not by three Gradle modules per feature.

`:core:database` is a **shared-write module**: it owns the Room schema, entities, DAOs and migrations for every feature, and it owns the read-model invariants of `docs/CONTRACTS.md §3.1`. A story that adds an entity MUST also bump the database version, add a `Migration` and add a migration test in the same PR. Only one story at a time may modify it; the handoff MUST declare it. If two implementation stories need this module concurrently, the optional `database-lock` check of `docs/CONTRACTS.md §18` uses `core/database/.story-lock` to make ownership explicit.

## 4. Dependency Rules

| Area | Allowed | Forbidden |
|------|---------|-----------|
| `:core:model` | Kotlin stdlib, coroutines, `kotlinx-datetime`, `kotlinx.serialization` | platform APIs, Firebase, Room, Koin, Ktor, **`:core:common`** |
| `:core:common` | `:core:model`, plus the same libraries as `:core:model` | platform APIs, Firebase, Room, Koin, Ktor |
| feature `domain` | `:core:model`, `:core:common` | Android, iOS, Firebase, GitLive, Koin, Room, Ktor, own `data`, own `presentation` |
| feature `data` | own `domain`, `:core:model`, `:core:common`, `:core:database`, `:core:sync` | `:integration:*`, `:core:auth`, other features |
| feature `presentation` | own `domain`, `:core:model`, `:core:common` | own `data`, other features |
| `:core:sync` | `:core:model`, `:core:common`, `:core:database` | `:integration:*`, `:core:auth`, features |
| `:core:database` | `:core:model`, `:core:common`, Room | `:integration:*`, features, `:core:sync` |
| `:core:auth` | `:core:model`, `:core:common`, coroutines, `kotlinx.serialization`, `kotlinx-datetime` | platform APIs, Firebase, GitLive, Room, Koin, Ktor, `:integration:*`, features |
| `:core:analytics` | `:core:model`, `:core:common` | platform APIs, Firebase, GitLive, Room, Koin, Ktor, `:integration:*`, features |
| `:core:testing` | every `:core:*` module plus test libraries (Turbine, `kotlin.test`) | `:integration:*`, `:wiring:*`, `:feature:*`, platform APIs in `commonMain` public API (platform APIs are permitted only in `expect`/`actual` test doubles, per `docs/CONTRACTS.md §15.1`) |
| `:core:crash` | `:core:common` | platform APIs, Firebase, GitLive, Koin, Ktor, integrations, features |
| `:integration:*` | `:core:*` interfaces, provider SDKs | features, `:shared` |
| `:shared` | `:core:*`, `:feature:*` | `:integration:*` |
| `:wiring:firebase` | integrations, `:shared` graph, Koin | product logic |

`:core:model` is the vocabulary and `:core:common` is the plumbing that speaks it, so the dependency runs `:core:common` -> `:core:model` and never the reverse. The direction is load-bearing rather than stylistic: `OwnerContext`, `LocaleInfo` and `MinorUnits` live in `:core:common` (`docs/CONTRACTS.md §20.3`) and refer to `OwnerId` and `CurrencyCode`, which live in `:core:model` (`§20.0`). Because the architecture check is generated from this table, leaving the edge undeclared would either fail the build on a legal dependency or leave the rule unenforced.

Feature `data` cannot depend on `:core:auth`, so the current owner reaches repositories through `OwnerContext` (`:core:common`), implemented by `:core:auth` and bound in wiring. An architecture rule asserts that no feature module references `AuthClient`.

"Platform API" in this table means direct references to Android packages (`android.*`, `androidx.*`), Android-only `java.util.concurrent` types, Apple/native packages (`platform.Foundation`, `platform.UIKit`, `platform.darwin`, `kotlinx.cinterop.*`) or any direct `expect`/`actual` boundary not allowed by `docs/CONTRACTS.md §15.1`. The architecture fixtures MUST include at least one rejected platform API reference for `:core:crash` and one for `:core:testing` (a platform API used in the `commonMain` public surface, not in a permitted `expect`/`actual` test double).

The three rows added for `:core:auth`, `:core:analytics` and `:core:testing` close the previous gap: every module in the canonical inventory of `docs/CONTRACTS.md §1.1` now has an enforceable dependency rule. `:core:auth` and `:core:analytics` are provider-free abstractions, so they forbid the same set of integrations and platform APIs as `:core:crash`; `:core:auth` additionally forbids Room because auth owns no persistence. `:core:testing` is the only `:core:*` module allowed to depend on every other `:core:*` module, because it must be able to construct fakes for `AppGraphDependencies` (`docs/CONTRACTS.md §11.6`); it remains forbidden from reaching integrations, wiring or features, and its platform-API permission is restricted to `expect`/`actual` test doubles so its `commonMain` public surface stays Kotlin-pure.

"Product logic" in `:wiring:firebase` is defined checkably: every top-level declaration there MUST be a Koin `Module`, a factory returning an abstraction, or a platform initialiser. No use cases, repositories, mappers, validation or business `expect`/`actual`. `:integration:firebase-*` modules MAY declare Koin `Module` declarations for their own bindings, but MUST NOT reference `createAppGraph`; only `:wiring:firebase` may aggregate those bindings into the final graph.

The architecture check MUST fail the build with a rule-specific message, and the check configuration is generated from this table so the two cannot drift.

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

`:shared` exposes a Kotlin-facing graph factory that receives a platform dependency container:

```kotlin
fun createAppGraph(dependencies: AppGraphDependencies): AppGraph
```

`AppGraphDependencies` and the Kotlin-facing `AppGraph` are defined in `docs/CONTRACTS.md §11.6` and `§20.10`; they are hidden from the Swift-facing Objective-C header. Swift calls `createSwiftAppGraph(isDebugBuild)` and consumes `SwiftAppGraph` plus the concrete state holders defined in `docs/CONTRACTS.md §20.10`. Only `:wiring:firebase` creates Firebase implementations. The executable decoupling check is:

```text
Exclude :integration:* and :wiring:firebase from settings.
Compile and test all :core:* and :feature:* modules using :core:testing fakes.
```

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

Room configuration: `exportSchema = true`, schema JSON committed under `core/database/schemas/`. `fallbackToDestructiveMigration` is FORBIDDEN in every build type. Every version bump ships an explicit `Migration` plus a test that migrates a populated previous-version database and asserts row preservation.

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
       first page: startAt(overlapSince, "")
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
- Pull overlap prevents silent cursor loss; `startAt(overlapSince, "")` gives the first overlapped page a legal concrete anchor; `startAfter` on the previous page cursor prevents re-reading the same page forever.
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

KMP bootstrap, Gradle convention plugins, core modules, quality tools, CI, architecture checks, contract check, ADRs, version matrix, identifiers.

Entry condition: every `Proposed` decision in `docs/DECISION_BOARD.md` that a Phase 0 story depends on has been confirmed by the owner.

### Phase 0.5 - Walking Skeleton

One end-to-end vertical slice: native UI, shared state holder, Room, Firestore, anonymous auth, Android-to-iOS sync, plus validation of the Swift-facing surface constraints.

Decision rule: if Room KMP and KSP block iOS progress during this phase, switch to SQLDelight immediately. The switch is pre-authorised only inside this story, and MUST produce a superseding ADR, a `docs/DECISION_BOARD.md` status change and an update to `E1-01` in the same PR.

### Phase 1 - Local Persistence

Local database, vehicle and fuel domains, repositories, consumption calculation, settings persistence, Android UI, iOS UI.

### Phase 2 - Authentication

Auth abstractions, Firebase Auth integration, onboarding, local owner adoption, conversion, sign-out, account deletion.

### Phase 3 - Backend Backup and Recovery

Firestore rules and emulator tests, Firestore integration for the development project, backup and recovery engine, app graph wiring, repository wiring, backup status UI, tombstone purge, account deletion server operation, provider decoupling proof.

### Phase 4 - MVP Hardening

Settings UI, accessibility, localization, performance, release builds, Crashlytics integration, store requirements.

## 11. Risks and Mitigations

| Risk | Probability / Impact | Mitigation |
|------|----------------------|------------|
| iOS toolchain friction | High / High | Walking skeleton in the first week, macOS CI from the first PR, SPM integration, pinned Kotlin/SKIE/Xcode versions. |
| Swift-facing API shape rejected by the Obj-C export | High / Medium | `docs/CONTRACTS.md §15.3` constraints validated in `E0-07`, plus a committed header golden file. |
| Backup and recovery bugs | High / Critical | Common engine, in-memory remote, deterministic simulation, required tests, debug screen for outbox, cursors and backup state. |
| Room KMP iOS friction | Medium / Medium | Validate before features. Keep the database behind repositories. Switch to SQLDelight if blocked. |
| Firestore rule mistake | Medium / Critical | Emulator tests for owner isolation, anonymous access, server timestamp enforcement, hard-delete rejection and range validation. |
| Data loss at the `LOCAL_OWNER` boundary | Medium / Critical | Outbox suppressed before a real UID exists; adoption story with an idempotency test. |
| Scope creep | Medium / Medium | Explicit out-of-scope list and review gate. |

## 12. Verification Strategy

Automated on every PR:

- Gradle build for Android and shared KMP modules.
- iOS simulator target and `:shared` framework build on macOS.
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
- Anonymous conversion preserves data.
- Credential collision is clear and non-destructive by default.
- Device clock skew does not corrupt sync.
- TalkBack and VoiceOver for critical flows.

## 13. Out of Plan

Maintenance expenses, advanced analytics, export, receipt images, odometer images, local or on-device AI text recognition, OCR, reminders, shared vehicles, widgets, wearables, web, App Check, Cloud Functions-mediated remote read/write validation beyond the `D-23` account deletion server operation, automatic account merging, simultaneous multi-device use, active multi-device synchronization, remote-database-as-source-of-truth operation, real-time Firestore listeners, remote settings synchronization, platform settings sync or backup through Google Play services / Android backup / iCloud, and electric or hybrid energy modelling.
