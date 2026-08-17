# Technical Plan - carApp MVP

> Derived document. It plans and explains; it does not create rules. Behaviour is normative in `docs/SPECIFICATION.md`, representation in `docs/CONTRACTS.md`, allowed technologies in `docs/DECISION_BOARD.md`. See `AGENTS.md` for authority and normative language.

## 1. Context

The project is greenfield. This plan closes the technical decisions needed to start implementation with multiple AI agents while keeping module boundaries, sync behavior, and quality gates explicit.

The selected architecture is Kotlin Multiplatform for shared logic and native UI per platform. The app is local-first. Cloud Firestore is a remote replica, not the UI source of truth.

## 2. Closed Decisions

Decision IDs are owned by `docs/DECISION_BOARD.md`. This table mirrors it and MUST stay identical; `contract-check` asserts that.

| ID | Decision | Choice | Status | Rationale |
|----|----------|--------|--------|-----------|
| D-0 | Backend | Cloud Firestore | Accepted | Fits the data model, avoids fixed Cloud SQL cost, provides client-ID idempotent writes and server timestamps. |
| D-1 | Local database | Room 3.0 KMP with `androidx.sqlite:sqlite-bundled` | Accepted | Same SQLite version across Android and iOS, supports modern UPSERT syntax with `minSdk 26`. |
| D-2 | Swift interop | SKIE only in `:shared` | Accepted | Better Swift ergonomics for Flow and sealed-like models than raw KMP export. |
| D-3 | DI | Koin KMP | Accepted | Owner-selected DI. Runtime wiring is acceptable if Koin is constrained to composition and wiring. |
| D-4 | `fuelType` | Stored on `Vehicle` from day one | Accepted | Schema evolution is easier before users exist; selector is not part of MVP UI. |
| D-5 | Firestore access | Firebase Firestore integration behind `RemoteSyncSource` | Accepted | Firebase is the initial database backend, fully decoupled so a future Ktor/API implementation can replace it. |
| D-6 | Firebase Auth | GitLive Auth 2.6.x behind `AuthClient` | Accepted | Consistent with the Firestore wrapper. Native UI obtains Google and Apple credentials. |
| D-7 | Navigation | Native per platform | Accepted | Compose Navigation and SwiftUI `NavigationStack`; no shared destination model. |
| D-8 | Presentation | Shared KMP state holders | Accepted | High KMP return with minimal native duplication. |
| D-9 | Firestore offline cache | Disabled | Accepted | The custom outbox is the offline strategy; two caches would create invalidation bugs. |
| D-10 | Metrics | Firebase Analytics behind `AnalyticsTracker` | Accepted | Aligns with the Firebase stack while keeping analytics replaceable. |
| D-11 | HTTP/API client | Ktor deferred | Deferred | Reserved for a future API-based remote implementation. |
| D-12 | Image loading | Coil if ever needed | Deferred | Prevents agents from choosing competing loaders. |
| D-13 | Firestore location | `eur3` | Proposed | Spanish user base; the location is immutable after database creation. |
| D-14 | Firebase project topology | `dev` and `prod` projects plus emulator | Proposed | Keeps test data out of production and makes rules deploys reversible. |
| D-15 | Logging implementation | Kermit behind `Logger` | Proposed | `Logger` is needed from Phase 0; the abstraction stays mandatory either way. |
| D-16 | Architecture checks | Konsist for package rules, custom Gradle check for module rules | Proposed | Gradle cannot express intra-module package rules. |
| D-17 | Flow testing helper | Turbine | Proposed | Confirm compatibility during version pinning. |
| D-18 | Coverage | Kover with thresholds | Proposed | Makes "high coverage" a pass/fail criterion. |
| D-19 | Result type | `Outcome<T, E>` in `:core:common` | Proposed | `kotlin.Result` has one type parameter; Arrow is out of scope. |
| D-20 | Localization | Native resources, no user-facing text in `UiState` | Proposed | UI is native; shared code has no resource bundle. |
| D-21 | Crash reporting | Crashlytics in Phase 4 | Pending | Not needed before release hardening. |
| D-22 | Application identifiers | `docs/identifiers.md` | Proposed | Store identifiers are effectively irreversible. |

Do not use GitLive 3.0 alpha during the MVP. Do not add Ktor during the MVP unless a new ADR introduces an HTTP API implementation.

## 3. Module Architecture

```text
build-logic/                    convention plugins
gradle/libs.versions.toml       single source of dependency versions

:core:model                     pure models, Money, scaled value classes
:core:common                    AppClock, UuidGenerator, DispatcherProvider, Outcome, AppError,
                                OwnerContext, Logger, LocaleProvider, ConnectivityObserver, backoff
:core:database                  Room entities, DAOs, migrations, platform builders, read-model invariants
:core:auth                      AuthClient, TokenProvider, AuthState
:core:sync                      Outbox, cursor, sync engine, SyncController, RemoteSyncSource
:core:analytics                 AnalyticsTracker and the closed AnalyticsEvent hierarchy
:core:testing                   fakes, builders, in-memory remote, deterministic simulator,
                                testAppGraphDependencies factory

:integration:firebase-auth      Firebase Auth implementation
:integration:firebase-firestore Firestore RemoteSyncSource implementation
:integration:firebase-analytics Firebase Analytics implementation

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

`:core:database` is a **shared-write module**: it owns the Room schema, entities, DAOs and migrations for every feature, and it owns the read-model invariants of `docs/CONTRACTS.md §3.1`. A story that adds an entity MUST also bump the database version, add a `Migration` and add a migration test in the same PR. Only one story at a time may modify it; the handoff MUST declare it.

## 4. Dependency Rules

| Area | Allowed | Forbidden |
|------|---------|-----------|
| `:core:model`, `:core:common` | Kotlin stdlib, coroutines, `kotlinx-datetime`, `kotlinx.serialization` | platform APIs, Firebase, Room, Koin, Ktor |
| feature `domain` | `:core:model`, `:core:common` | Android, iOS, Firebase, GitLive, Koin, Room, Ktor, own `data`, own `presentation` |
| feature `data` | own `domain`, `:core:model`, `:core:common`, `:core:database`, `:core:sync` | `:integration:*`, `:core:auth`, other features |
| feature `presentation` | own `domain`, `:core:model`, `:core:common` | own `data`, other features |
| `:core:sync` | `:core:model`, `:core:common`, `:core:database`, `:core:auth` | `:integration:*`, features |
| `:core:database` | `:core:model`, `:core:common`, Room | `:integration:*`, features, `:core:sync` |
| `:integration:*` | `:core:*` interfaces, provider SDKs | features, `:shared` |
| `:shared` | `:core:*`, `:feature:*` | `:integration:*` |
| `:wiring:firebase` | integrations, `:shared` graph, Koin | product logic |

Feature `data` cannot depend on `:core:auth`, so the current owner reaches repositories through `OwnerContext` (`:core:common`), implemented by `:core:auth` and bound in wiring. An architecture rule asserts that no feature module references `AuthClient`.

"Product logic" in `:wiring:firebase` is defined checkably: every top-level declaration there MUST be a Koin `Module`, a factory returning an abstraction, or a platform initialiser. No use cases, repositories, mappers, validation or business `expect`/`actual`.

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

`:shared` exposes a graph factory that receives a platform dependency container:

```kotlin
fun createAppGraph(dependencies: AppGraphDependencies): AppGraph
```

`AppGraphDependencies` and `AppGraph` are defined in `docs/CONTRACTS.md §11.6` and `§20.10`. Only `:wiring:firebase` creates Firebase implementations. The executable decoupling check is:

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
| `serverUpdatedAt` | Authoritative remote timestamp, null if never synced. |
| `deleted` | Tombstone flag, with `CHECK(deleted = (deletedAt IS NOT NULL))`. |
| `deletedAt` | Tombstone timestamp. |
| `syncState` | Local state. Canonical values in `docs/CONTRACTS.md §7`. |
| `localRevision` | Incremented on each local edit to detect in-flight edits. |
| `schemaVersion` | Payload schema version. |

Tables: `vehicle`, `fuel_entry`, `user_settings`, `outbox`, `sync_cursor`, `quarantine`.

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
  UNIQUE(entityType, entityId)
);

CREATE INDEX idx_outbox_due ON outbox(nextAttemptAt, seq);
```

The outbox stores full snapshots; re-applying the same snapshot is idempotent. Retry decisions are made on `lastErrorCode`, never on `lastError` text.

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
2. Partition into dependency groups, preserving seq within each group:
   vehicle upserts -> fuel entry upserts -> fuel entry tombstones -> vehicle tombstones.
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
  2. anchor = (max(0, cursor.lastServerUpdatedAt - 30s), null)   // overlap applied once per cycle
  3. Query where updatedAt >= anchor.time
       orderBy updatedAt ASC, documentId ASC
       startAfter(anchor)
       limit 200
  4. Apply the page in one local transaction.
     - quarantine documents whose schemaVersion is unsupported
     - skip entities that have an outbox row
     - otherwise apply if remote.updatedAt > local.serverUpdatedAt, or local was never synced
  5. anchor = (lastApplied.updatedAt, lastApplied.documentId); advance the cursor.
  6. If the page was full and the anchor did not strictly advance, fail with ConflictUnresolved.
  7. Repeat while the page is full.
```

### Convergence

- Local mutations eventually reach the outbox, except while the owner is `LOCAL_OWNER`.
- Push is idempotent by client-generated document ID.
- Server `updatedAt` creates authoritative ordering; the local `updatedAt` never arbitrates.
- `(updatedAt, documentId)` provides a deterministic total order over the pull stream.
- Pull overlap prevents silent cursor loss; `startAfter` prevents the overlap from re-reading the same page forever.
- Tombstones are regular LWW documents.

## 9. Sync Tests

Required tests for `:core:sync`:

1. Offline write syncs after connectivity returns.
2. Ambiguous response retry does not duplicate records.
3. Two-device edit conflict converges.
4. Exact `updatedAt` tie converges deterministically in the pull stream order.
5. Tombstone wins over older update.
6. Local edit during in-flight push is not lost, and the state machine follows `SYNCING -> SYNCING -> PENDING`.
7. Pull overlap prevents missing a document with a timestamp before the cursor.
8. Device clock one hour ahead does not win all conflicts.
9. First sync of 1,000 records is paginated correctly.
10. After `MAX_RETRYABLE_ATTEMPTS` consecutive retryable failures the row becomes `FAILED_POISONED` and manual retry is available.
11. More than 200 documents sharing one timestamp inside the overlap window paginate to completion instead of looping.
12. A fuel entry arriving before its vehicle is persisted, hidden from the UI, and converges without stalling.
13. Two devices creating the same vehicle name converge into two vehicles without a constraint failure.
14. Local owner adoption on a populated `LOCAL_OWNER` database enqueues every row exactly once and is idempotent.
15. A document with an unsupported higher `schemaVersion` is quarantined and does not block cursor advance.
16. Backoff with an injected jitter source produces deterministic, capped delays.

Add a deterministic simulation with a fixed seed that interleaves local edits, push, pull, network failure, duplicate delivery and lost responses, asserting convergence between two clients.

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

### Phase 3 - Backend and Synchronization

Firestore rules and emulator tests, Firestore integration, sync engine, app graph wiring, repository wiring, sync status UI, tombstone purge, provider decoupling proof.

### Phase 4 - MVP Hardening

Settings UI, accessibility, localization, performance, release builds, store requirements.

## 11. Risks and Mitigations

| Risk | Probability / Impact | Mitigation |
|------|----------------------|------------|
| iOS toolchain friction | High / High | Walking skeleton in the first week, macOS CI from the first PR, SPM integration, pinned Kotlin/SKIE/Xcode versions. |
| Swift-facing API shape rejected by the Obj-C export | High / Medium | `docs/CONTRACTS.md §15.3` constraints validated in `E0-07`, plus a committed header golden file. |
| Sync convergence bugs | High / Critical | Common engine, in-memory remote, deterministic simulation, 16 required tests, debug screen for outbox, cursors and sync state. |
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
- Sync convergence tests when sync exists.
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

Maintenance expenses, advanced analytics, export, receipt images, OCR, reminders, shared vehicles, widgets, wearables, web, App Check, automatic account merging, real-time Firestore listeners, and remote settings synchronization.
