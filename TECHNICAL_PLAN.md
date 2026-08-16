# Technical Plan - carApp MVP

## 1. Context

The project is greenfield. This plan closes the technical decisions needed to start implementation with multiple AI agents while keeping module boundaries, sync behavior, and quality gates explicit.

The selected architecture is Kotlin Multiplatform for shared logic and native UI per platform. The app is local-first. Cloud Firestore is a remote replica, not the UI source of truth.

## 2. Closed Decisions

| ID | Decision | Choice | Rationale |
|----|----------|--------|-----------|
| D-0 | Backend | Cloud Firestore | Fits the data model, avoids fixed Cloud SQL cost, provides client-ID idempotent writes and server timestamps. |
| D-1 | Local database | Room 3.0 KMP with `androidx.sqlite:sqlite-bundled` | Same SQLite version across Android and iOS, supports modern UPSERT syntax with `minSdk 26`. |
| D-2 | Swift interop | SKIE only in `:shared` | Better Swift ergonomics for Flow and sealed-like models than raw KMP export. |
| D-3 | DI | Koin KMP | Owner-selected DI. Runtime wiring is acceptable if Koin is constrained to composition/wiring and prohibited from domain logic. |
| D-4 | `fuelType` | Stored on `Vehicle` from day one | Schema evolution is easier before users exist; selector is not part of MVP UI. |
| D-5 | Firestore access | Firebase Firestore integration behind `RemoteSyncSource` | Firebase is the initial database backend. The integration must be fully decoupled so a future Ktor/API implementation can replace it. |
| D-6 | Firebase Auth | GitLive Auth 2.6.x behind `AuthClient` | Consistent with Firestore wrapper. Native UI obtains Google/Apple credentials. |
| D-7 | Navigation | Native per platform | Compose Navigation and SwiftUI `NavigationStack`; no shared destination model. |
| D-8 | Presentation | Shared KMP state holders | High KMP return with minimal native duplication. |
| D-9 | Firestore offline cache | Disabled | The custom outbox is the offline strategy; two caches would create invalidation bugs. |
| D-10 | Metrics | Firebase Analytics behind `AnalyticsTracker` | Aligns with Firebase stack while keeping analytics provider replaceable. |
| D-11 | HTTP/API client | Ktor deferred | Ktor is reserved for future API-based remote implementations; it is not an MVP dependency while Firestore is used directly behind an interface. |

Do not use GitLive 3.0 alpha during the MVP. Do not add Ktor during the MVP unless a new ADR introduces an HTTP API implementation.

## 3. Module Architecture

```text
build-logic/                    convention plugins
gradle/libs.versions.toml       single source of dependency versions

:core:model                     pure models, Money, value objects
:core:common                    Clock, UUID, dispatchers, Result, AppError, backoff
:core:database                  Room entities, DAOs, migrations, platform builders
:core:auth                      AuthClient, TokenProvider, AuthState
:core:sync                      Outbox, cursor, sync engine, RemoteSyncSource
:core:analytics                 AnalyticsTracker and metrics event contracts
:core:testing                   fakes, builders, in-memory remote, deterministic simulator

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

Each feature is one Gradle module. Layer separation is enforced by package-level architecture checks, not by three Gradle modules per feature.

## 4. Dependency Rules

| Area | Allowed | Forbidden |
|------|---------|-----------|
| `:core:model`, `:core:common` | Kotlin stdlib and approved primitives | platform APIs, Firebase, Room |
| feature `domain` | `:core:model`, `:core:common` | Android, iOS, Firebase, GitLive, Koin, Room, Ktor, data, presentation |
| feature `data` | own domain, `:core:database`, `:core:sync` | `:integration:*`, other features |
| feature `presentation` | own domain, `:core:common` | own data package, other features |
| `:core:sync` | `:core:database`, `:core:auth`, `:core:common` | `:integration:*` |
| `:integration:*` | `:core:*` interfaces | features |
| `:shared` | `:core:*`, `:feature:*` | `:integration:*` |
| `:wiring:firebase` | integrations, shared graph | product logic |

The architecture check must fail the build with a rule-specific message.

## 4.1 Contractual Guardrails

`CONTRACTS.md` defines implementation contracts that agents must not reinterpret:

- Canonical data types and scaled integer formats.
- Local row and remote document schemas.
- Validation and warning semantics.
- Error taxonomy and expected failure handling.
- Sync state machine, outbox payload, cursor, backoff, and cycle ordering.
- `RemoteSyncSource`, auth, repository, and use case interface shapes.
- Presentation state holder lifecycle rules.
- Allowed and forbidden `expect`/`actual` boundaries.
- Firestore query/rule contract.
- Logging and privacy rules.
- Metrics and analytics event boundaries.

Any change to those contracts is a human review gate and must update `CONTRACTS.md` in the same change.

## 5. Provider Decoupling

`:shared` exposes a graph factory that receives a platform dependency container:

```kotlin
fun createAppGraph(dependencies: AppGraphDependencies): AppGraph
```

`AppGraphDependencies` is defined contractually in `CONTRACTS.md` and includes auth, remote sync, analytics, database factory, clock, dispatchers, UUID generation, logger, locale provider, connectivity, and sync trigger abstractions. Only `:wiring:firebase` creates Firebase implementations. The executable decoupling check is:

```text
Exclude :integration:* and :wiring:firebase from settings.
Compile and test all :core:* and :feature:* modules using :core:testing fakes.
```

## 6. Local Data Model

Synchronized entity control columns:

| Column | Meaning |
|--------|---------|
| `id` | Client-generated UUID primary key. |
| `ownerId` | Authenticated user ID. |
| `updatedAt` | Local provisional timestamp. |
| `serverUpdatedAt` | Authoritative remote timestamp, null if never synced. |
| `deleted` | Tombstone flag. |
| `syncState` | Local state. Canonical values are defined in `CONTRACTS.md`. |
| `localRevision` | Incremented on each local edit to detect in-flight edits. |

Tables:

- `vehicle`
- `fuel_entry`
- `outbox`
- `sync_cursor`

Outbox schema:

```sql
CREATE TABLE outbox (
  seq INTEGER PRIMARY KEY AUTOINCREMENT,
  entityType TEXT NOT NULL,
  entityId TEXT NOT NULL,
  payload TEXT NOT NULL,
  localRevision INTEGER NOT NULL,
  attemptCount INTEGER NOT NULL DEFAULT 0,
  nextAttemptAt INTEGER NOT NULL DEFAULT 0,
  lastError TEXT,
  UNIQUE(entityType, entityId)
);
```

The outbox stores full snapshots. Re-applying the same snapshot is idempotent. Payload format, sync states, and poison/retry semantics are defined in `CONTRACTS.md`.

## 7. Firestore Design

Structure:

```text
users/{uid}/vehicles/{vehicleId}
users/{uid}/fuelEntries/{entryId}
users/{uid}/meta/settings
```

Rationale:

- User-scoped subcollections make authorization straightforward.
- Reads are naturally owner-scoped.
- Delta pull can use single-field `updatedAt` indexes.

Security rule shape:

```javascript
rules_version = '2';
service cloud.firestore {
  match /databases/{db}/documents {
    match /users/{uid}/{document=**} {
      allow read: if request.auth != null && request.auth.uid == uid;
      allow write: if request.auth != null
                   && request.auth.uid == uid
                   && request.resource.data.updatedAt == request.time;
    }
  }
}
```

Anonymous Firebase users must be allowed.

## 8. Sync Engine

The engine lives fully in `commonMain`. Platform APIs only trigger it; they are not used inside the core engine.

### Push

```text
1. SELECT due outbox rows ordered by seq, limit 50.
2. Reorder by dependency: vehicles before fuel entries.
3. For each row, write doc(users/{uid}/{collection}/{id}) with serverTimestamp().
4. Re-read the document to get authoritative updatedAt.
5. In a local transaction:
   - if outbox.localRevision == entity.localRevision:
       delete outbox row, set syncState = SYNCED, set serverUpdatedAt
   - else:
       keep outbox row, update only serverUpdatedAt
6. Retry network and token failures with backoff.
7. Mark validation failures as poisoned.
```

### Pull

```text
1. cursor = sync_cursor[entityType]
2. since = max(0, cursor.lastServerUpdatedAt - 30 seconds)
3. Query collection where updatedAt >= since ordered by updatedAt and document ID, limit 200.
4. Apply the page in one local transaction.
5. If local outbox exists for entity ID, do not overwrite local data.
6. Otherwise apply remote row if (updatedAt, id) is newer.
7. Advance cursor to the last applied `(updatedAt, documentId)` pair.
8. Repeat while the page is full.
```

### Convergence

- Local mutations eventually reach the outbox.
- Push is idempotent by client-generated document ID.
- Server `updatedAt` creates authoritative ordering.
- `(updatedAt, id)` provides deterministic total order.
- Pull overlap prevents silent timestamp cursor loss.
- Tombstones are regular LWW documents.

## 9. Sync Tests

Required tests for `:core:sync`:

1. Offline write syncs after connectivity returns.
2. Ambiguous response retry does not duplicate records.
3. Two-device edit conflict converges.
4. Exact `updatedAt` tie converges deterministically by `id`.
5. Tombstone wins over older update.
6. Local edit during in-flight push is not lost.
7. Pull overlap prevents missing a document with timestamp before cursor.
8. Device clock one hour ahead does not win all conflicts.
9. First sync of 1,000 records is paginated correctly.
10. Persistent failure becomes `FAILED` with manual retry available.

Add a deterministic simulation with a fixed seed that interleaves local edits, push, pull, network failure, duplicate delivery, and lost responses, asserting convergence between two clients.

## 10. Implementation Phases

### Phase 0 - Foundations

KMP bootstrap, Gradle convention plugins, core modules, quality tools, CI, architecture checks, ADRs.

### Phase 0.5 - Walking Skeleton

One end-to-end vertical slice: native UI, shared state holder, Room, Firestore, anonymous auth, Android-to-iOS sync.

Decision rule: if Room KMP and KSP block iOS progress during this phase, switch to SQLDelight immediately.

### Phase 1 - Local Persistence

Local database, vehicle and fuel domains, repositories, consumption calculation, Android UI, iOS UI.

### Phase 2 - Authentication

Auth abstractions, Firebase Auth integration, onboarding, conversion, sign-out, account deletion.

### Phase 3 - Backend and Synchronization

Firestore rules and emulator tests, Firestore integration, sync engine, repository wiring, sync status UI, provider decoupling proof.

### Phase 4 - MVP Hardening

Settings, accessibility, localization, performance, release builds, store requirements.

## 11. Risks and Mitigations

| Risk | Probability / Impact | Mitigation |
|------|----------------------|------------|
| iOS toolchain friction | High / High | Walking skeleton in first week, macOS CI from first PR, SPM integration, pinned Kotlin/SKIE/Xcode versions. |
| Sync convergence bugs | High / Critical | Common engine, in-memory remote, deterministic simulation, debug screen for outbox/cursors/syncState. |
| Room KMP iOS friction | Medium / Medium | Validate before features. Keep DB behind repositories. Switch to SQLDelight if blocked. |
| Firestore rule mistake | Medium / Critical | Emulator tests for owner isolation, anonymous access, and server timestamp enforcement. |
| Scope creep | Medium / Medium | Explicit out-of-scope list and review gate. |

## 12. Verification Strategy

Automated on every PR:

- Gradle build for Android and shared KMP modules.
- iOS simulator target and `:shared` framework build on macOS.
- ktlint.
- detekt.
- Unit tests.
- Architecture rule checks.
- Domain coverage for business rules.
- Sync convergence tests when sync exists.
- Firestore emulator tests when rules exist.
- Provider decoupling check once integrations exist.

Manual at phase gates:

- Offline create vehicle and fuel entries, close app, reopen, reconnect, verify remote data.
- Two-device edit conflict converges.
- Anonymous conversion preserves data.
- Credential collision is clear and non-destructive by default.
- Device clock skew does not corrupt sync.
- TalkBack and VoiceOver for critical flows.

## 13. Out of Plan

Maintenance expenses, advanced analytics, export, receipt images, OCR, reminders, shared vehicles, widgets, wearables, web, App Check, automatic account merging, and real-time Firestore listeners.
