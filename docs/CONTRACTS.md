# System Contracts - carApp MVP

> Normative guardrail layer for implementation. This document defines API contracts, canonical types, state machines, persistence formats, error taxonomy, and boundary rules that future agents MUST follow.

## 1. Authority

Document authority, reading order and normative language (MUST / SHOULD / MAY) are defined once in `AGENTS.md` and MUST NOT be restated here.

What that means for this document:

- `docs/SPECIFICATION.md` is authoritative for **behaviour**: scope, business rules, flows and non-functional targets.
- This document is authoritative for **representation**: types, signatures, field names, numeric semantics, persistence formats, state machines and boundaries. On any representational detail this document wins, including against `docs/SPECIFICATION.md`.
- A type MAY be referenced in a signature in this document only if it is declared in §20. Introducing a new type into a public signature REQUIRES updating §20 in the same change, and is a human review gate.
- A conflict that is behavioural, or that cannot be classified on either axis, MUST be escalated to the project owner rather than resolved by an agent.

## 2. Canonical Data Types

### Identifiers

| Type | Kotlin representation | Persistence | Rules |
|------|-----------------------|-------------|-------|
| `EntityId` | value class wrapping `String` | TEXT / Firestore string | UUID v4 generated on the client. Lowercase canonical text form. |
| `OwnerId` | value class wrapping `String` | TEXT / Firestore string | Firebase UID, or the sentinel `LOCAL_OWNER` before an anonymous UID exists (§11.4). |
| `CurrencyCode` | value class wrapping `String` | TEXT / Firestore string | ISO-4217 uppercase code. MVP default fallback is `EUR`. |

Value classes are Kotlin-internal. They MUST NOT appear on the Swift-facing surface (§15.3).

### Time

| Concept | Kotlin representation | Persistence | Rules |
|---------|-----------------------|-------------|-------|
| Domain instant | `Instant` (exact package pinned in `docs/versions-matrix.md`) | epoch milliseconds in Room columns and JSON payloads; Firestore timestamp remotely | UTC only. No local timezone persistence. |
| Local `updatedAt` | `Instant` | INTEGER epoch milliseconds | Provisional local timestamp. **Never** authoritative for remote conflict arbitration. |
| `serverUpdatedAt` | `Instant?` | INTEGER epoch milliseconds | Authoritative timestamp received from Firestore. Null means never synced. |

`LocalDate`, `LocalDateTime` and `TimeZone` MUST NOT appear in domain, data or sync code. They are permitted only in presentation formatting.

All time reads go through the injected `AppClock` (§20). Direct use of a system clock is FORBIDDEN outside `:wiring:*` and `:core:testing`.

### Money and decimals

`Float` and `Double` are FORBIDDEN for money, volume, price and consumption calculations, in every layer.

| Concept | Kotlin representation | Scale | Persistence |
|---------|-----------------------|-------|-------------|
| `Money` | data class with `minorUnits: Long`, `currency: CurrencyCode` | ISO-4217 minor units | INTEGER + currency code |
| `FuelVolume` | scaled integer value class | 3 decimals, litres × 1000 | INTEGER |
| `PricePerLiter` | scaled integer value class | 3 decimals, currency units × 1000 | INTEGER |
| `ConsumptionL100Km` | scaled integer value class, `scaled: Long` | 2 decimals, L/100 km × 100 | Computed read model, never persisted |

Every scaled type carries its value in a `Long`. `Int` MUST NOT be used for a scaled quantity: the distance-weighted average sums `litersScaled` across every valid segment of a vehicle, and `10 * sum(litersScaled)` overflows `Int` well inside the per-vehicle entry ceiling of §12.

`Money` values of different `currency` MUST NOT be added, subtracted or compared. Any aggregation across currencies is a `ValidationError.InvalidUnit`.

### Canonical monetary arithmetic

Rounding mode is HALF_UP on non-negative inputs. The formulas below are **exact integer arithmetic** and MUST be implemented literally; a floating-point or naive integer-division implementation is a contract violation.

```text
minorUnitFactor = MinorUnits.factorFor(currency)      // EUR -> 100

totalCostMinor      = (litersScaled * pricePerLiterScaled * minorUnitFactor + 500_000) / 1_000_000
pricePerLiterScaled = (totalCostMinor * 1_000_000 + (litersScaled * minorUnitFactor) / 2) / (litersScaled * minorUnitFactor)
litersScaled        = (totalCostMinor * 1_000_000 + (pricePerLiterScaled * minorUnitFactor) / 2) / (pricePerLiterScaled * minorUnitFactor)
```

Golden values that MUST be covered by tests in `:core:model`:

| `litersScaled` | `pricePerLiterScaled` | currency | `totalCostMinor` |
|----------------|-----------------------|----------|------------------|
| `45_123` (45.123 L) | `1_789` (1.789 €/L) | EUR | `8_073` (80.73 €) — exact value is 8072.5047, HALF_UP |
| `40_000` (40 L) | `1_500` (1.500 €/L) | EUR | `6_000` (60.00 €) |
| `1` (0.001 L) | `1` (0.001 €/L) | EUR | `1` (0.01 €) — rounds up from 0.0001 |

MVP currency constraint: `MinorUnits.factorFor` supports **only** 2-decimal ISO-4217 codes in the MVP. A locale suggesting a 0-decimal or 3-decimal currency (JPY, KWD, …) falls back to `EUR`, and an explicit user selection of such a currency returns `ValidationError.InvalidUnit`. Extending the table is a backlog story, not an agent decision.

Which value is derived is never ambiguous: the caller states which two values it supplied through `MoneyInput` (§20), and the third is computed. All three are persisted; `MoneyInput` records the authoritative pair, and re-deriving the third from the stored pair MUST be stable.

### Canonical consumption arithmetic

Consumption uses the same HALF_UP convention as money and MUST be implemented literally. `docs/SPECIFICATION.md §6` R-3 states the mathematical definition in unscaled litres; the formulas below are the only implementable form, because `litersScaled` is litres × 1000 while `ConsumptionL100Km.scaled` is L/100 km × 100.

```text
// L/100 km       = (litersScaled / 1000) / distanceKm * 100 = litersScaled / (10 * distanceKm)
// scaled by 100  = 10 * litersScaled / distanceKm

segmentConsumptionScaled = (10 * segmentLitersScaled + distanceKm / 2) / distanceKm

averageConsumptionScaled = (10 * sum(validSegmentLitersScaled) + sum(validSegmentDistanceKm) / 2)
                           / sum(validSegmentDistanceKm)
```

Both are computed in `Long`. `distanceKm > 0` is guaranteed by the caller: a segment with `distanceKm <= 0` is `SegmentResult.Invalid(NonPositiveDistance)` and never reaches this arithmetic, so the division is total.

The average divides summed litres by summed distance. It is NOT the arithmetic mean of the segment values, and it is NOT recomputed from the already-rounded `segmentConsumptionScaled` values.

Golden values that MUST be covered by tests in `:core:model`:

| Case | `litersScaled` | `distanceKm` | `scaled` result | Meaning |
|------|----------------|--------------|-----------------|---------|
| Segment, rounds down | `45_123` | `600` | `752` | 7.52 L/100 km — exact value is 7.5205 |
| Segment, exact | `40_000` | `500` | `800` | 8.00 L/100 km |
| Segment, rounds up | `30_000` | `397` | `756` | 7.56 L/100 km — exact value is 7.55668 |
| Average of segments 1 and 2 | `85_123` | `1_100` | `774` | 7.74 L/100 km — exact value is 7.73845 |

The last row is the regression test for the distance-weighted rule: the arithmetic mean of `752` and `800` is `776`, which is wrong.

## 3. Canonical Entity Schema

### Field naming rule

A field holding a physical quantity MUST carry its unit or scale suffix (`Km`, `Scaled`, `Minor`). An unsuffixed numeric field name is a contract violation. These names are canonical at **every** layer: domain, Room column, Firestore field and JSON payload key.

### Domain model vs local row vs remote document

Domain models expose business concepts. Local rows add sync metadata. Remote documents contain only synchronized data plus remote metadata.

| Field | Domain | Local Room row | Firestore document | Notes |
|-------|--------|----------------|--------------------|-------|
| `id` | Yes | Yes | Document ID and field | Client-generated UUID v4. |
| `ownerId` | Yes | Yes | Yes | MUST equal the authenticated UID remotely. Stamped by the repository, never supplied by a command. |
| `createdAt` | Yes | Yes | Yes | Client-created UTC timestamp. |
| `updatedAt` | Yes | Yes | Yes | Local provisional in Room; server timestamp remotely. |
| `serverUpdatedAt` | No | Yes | No | Local sync metadata only. |
| `deletedAt` | Yes | Yes | Yes | Null when active. |
| `deleted` | No | Yes (stored) | Yes | Stored, with the invariant `deleted == (deletedAt != null)` enforced by a `CHECK` constraint. Written only by the tombstone helper in `:core:database`. |
| `syncState` | **No** | Yes | No | Local-only. MUST NOT be visible to feature `domain` or `presentation` code. |
| `localRevision` | No | Yes | No | Local-only. Incremented on every local edit. |
| `schemaVersion` | No | Yes | Yes | Starts at `1`. |

Sync status reaches the UI only through the aggregate `SyncStatus` exposed by `SyncController` (§9.6), never per entity.

Remote writes are full-document `set(..., merge = false)` operations. Partial remote updates are FORBIDDEN in the MVP sync engine.

### Vehicle

Canonical fields:

- `id`
- `ownerId`
- `name`
- `nameFold` — local only, generated as `name.trim().lowercase()`, used for uniqueness checks (§5)
- `initialOdometerKm`
- `currentOdometerKm` — derived read model
- `brand`
- `model`
- `fuelType`
- `createdAt`
- `updatedAt`
- `deleted`
- `deletedAt`
- `schemaVersion`

`currentOdometerKm` is defined as:

```text
currentOdometerKm = max(initialOdometerKm, MAX(odometerKm) over non-deleted fuel entries of the vehicle)
```

It is a **maximum**, not a recency selector, and it includes entries flagged `odometerInconsistent` (the odometer physically exists). It is never accepted from user input and is never used for remote conflict arbitration. It is not a remote field.

`currentOdometerKm` MUST NOT be written by any code outside `:core:database`. Feature repositories and `:core:sync` both read it as a read-only column; `:core:database` recomputes it inside the same transaction as any fuel-entry insert, update or delete (§3.1).

`initialOdometerKm` is editable only while the vehicle has no non-deleted fuel entries.

`fuelType` is metadata only in the MVP: it does not alter validation, units or consumption. `ELECTRIC` and `HYBRID` require a separate energy model and are out of MVP scope; no agent may add kWh handling.

### FuelEntry

Canonical fields:

- `id`
- `ownerId`
- `vehicleId`
- `date`
- `odometerKm`
- `litersScaled`
- `pricePerLiterScaled`
- `totalCostMinor`
- `currency`
- `isFullTank`
- `hasMissedEntries`
- `odometerInconsistent`
- `notes`
- `createdAt`
- `updatedAt`
- `deleted`
- `deletedAt`
- `schemaVersion`

Persisted fuel entries MUST contain non-null `litersScaled`, `pricePerLiterScaled` and `totalCostMinor`. Form drafts may contain nullable partial input; repositories MUST NOT persist drafts.

`hasMissedEntries = true` on entry `E` means the user did not log one or more refuels **between the previous logged entry and `E`**. It therefore invalidates the segment ending at `E` and any segment containing `E`, and has no effect on earlier segments.

`odometerInconsistent` is a **derived** property cached in a column. `:core:database` recomputes it for the affected entry and its immediate successor inside the same transaction as any create, update or delete of a fuel entry for that vehicle. Application code MUST NOT write it directly.

The `fuel_entry` table declares **no enforced foreign key** to `vehicle`: sync can legitimately deliver a fuel entry before its vehicle (§9.4).

### UserSettings

Canonical persistence is metric and **device-local**:

- Distances are stored in kilometres, volumes in litres.
- `distanceUnit = KM` and `volumeUnit = LITER` are fixed and read-only in the MVP.
- Unit settings affect presentation and input only, never domain storage.
- Settings live in a single-row local table `user_settings`. They are **not synchronized** in the MVP; there is no remote settings document, no `EntityType` for them and no outbox participation.
- `currency` is the default for *new* fuel entries only. Each fuel entry stores its own `currency`; changing the setting never rewrites existing entries.

### 3.1 Database-owned invariants

The following are maintained exclusively by `:core:database`, inside the caller's transaction, because both feature repositories and `:core:sync` write the same tables:

| Invariant | Trigger |
|-----------|---------|
| `vehicle.currentOdometerKm` recomputation | any insert / update / delete on `fuel_entry` |
| `fuel_entry.odometerInconsistent` recomputation for the affected entry and its successor | any insert / update / delete on `fuel_entry` |
| `deleted == (deletedAt != null)` | `CHECK` constraint on both entity tables |

An architecture check MUST assert that no `UPDATE vehicle SET currentOdometerKm` and no write to `odometerInconsistent` exists outside `:core:database`.

## 4. Ordering Rules

Fuel entry **chronological** order is deterministic:

```text
date ASC, createdAt ASC, id ASC
```

Consumption **calculation** order is deterministic and is a different order:

```text
odometerKm ASC, date ASC, id ASC
```

Odometer validation (R-1) uses the previous non-deleted fuel entry in **chronological** order.

Consumption (R-3) operates on non-deleted fuel entries of one vehicle in **calculation** order:

- `P` is the nearest preceding entry with `isFullTank = true` in calculation order.
- Segment membership is `P.odometerKm < X.odometerKm <= E.odometerKm`.
- An entry whose `odometerKm` equals `P.odometerKm`, and which is not `P` itself, **is included** in the segment (its litres count) and additionally invalidates the segment with `DuplicateOdometerInSegment`.
- Partial fuel entries before the first full-tank anchor never contribute to a valid segment.

The complete and exhaustive set of invalidation reasons is `ConsumptionInvalidReason` (§20). No other document may restate that list.

## 5. Validation and Save Semantics

Write use cases validate commands before repository writes. Expected validation failures return typed errors and do not throw.

### Normalisation

Normalisation runs in the use case **before** validation, and the normalised value is what is persisted and what uniqueness is computed on:

- All strings are `trim()`ed.
- Internal whitespace runs in `name` collapse to a single space.
- For nullable text fields (`brand`, `model`, `notes`), `""` becomes `null`.
- `name` MUST NOT be null or blank.

### Warning protocol

Odometer inconsistency is a warning, not an automatic write:

1. A command without the confirmation returns `Err(ValidationWarning.OdometerInconsistent(previousOdometerKm, enteredOdometerKm))` and mutates nothing.
2. The UI asks for explicit confirmation.
3. The identical command re-issued with `Confirmation.OdometerInconsistent` in `confirmations` succeeds, and the entry is stored with `odometerInconsistent = true`.

A use case MUST NOT return `Ok` and a warning simultaneously. A warning MUST be idempotent.

### Validation constraints

Both ends of every interval are closed and MUST be enforced.

| Field | Rule |
|-------|------|
| Vehicle `name` | Trimmed length 1..40. Unique per owner among non-deleted vehicles, compared on `nameFold`. |
| `brand`, `model` | Null, or trimmed length 1..40. |
| `initialOdometerKm` | 0..2_000_000. Editable only while the vehicle has no non-deleted fuel entries. |
| Fuel entry `date` | Not before `1970-01-01T00:00:00Z`, not before `vehicle.createdAt - 20 years`, and not more than 1 hour after `AppClock.now()`. |
| `odometerKm` | 0..2_000_000; `>= vehicle.initialOdometerKm`; strictly greater than the previous non-deleted entry in chronological order unless confirmed inconsistent. |
| `litersScaled` | 1..500_000 (0.001 L .. 500 L). |
| `pricePerLiterScaled` | 1..999_999. |
| `totalCostMinor` | 1..99_999_999. |
| `currency` | A supported 2-decimal ISO-4217 code (§2). |
| `notes` | Null, or trimmed length 1..280. |

Vehicle name uniqueness is a **local pre-write validation only**. There MUST NOT be a `UNIQUE` index on the synchronized table for it: two devices can legitimately create the same name offline, and a unique index would make the pull transaction fail and stall the cursor. SQLite `NOCASE` MUST NOT be used for folding, because it is ASCII-only.

Remote-applied rows bypass business validation entirely. A pull transaction MUST NOT fail because of a domain constraint; the only legal pull failures are I/O, serialization and schema-version quarantine (§9.5).

## 6. Result and Error Taxonomy

Public use cases and repositories return `Outcome<T, AppError>` or `Flow<Outcome<T, AppError>>` where an expected failure can occur. They do not throw for expected validation, auth, persistence, sync, or remote failures.

`kotlin.Result` and exceptions-as-control-flow are FORBIDDEN in public signatures. Arrow MUST NOT be added without an ADR.

`AppError` is a `sealed interface`; every leaf is a `data class` or `data object` carrying a stable `code` (§20). Presentation MUST handle it with an exhaustive `when` and MUST NOT use an `else` branch. Retry and poison decisions are made on `code`, never on a message string.

### Categories

| Category | Purpose |
|----------|---------|
| `ValidationError` | Command rejected before any write. |
| `ValidationWarning` | Write withheld pending explicit confirmation. |
| `AuthError` | Authentication, linking, token and account-deletion failures. |
| `PersistenceError` | Local database failures. |
| `SyncError` | Sync engine outcomes that drive the state machine. |
| `RemoteError` | Provider-space failures returned by `RemoteSyncSource`. |
| `SecurityError` | Rule rejection and owner mismatch. |
| `UnexpectedError` | Anything not classified above. |

Full leaf lists are declared in §20.

### Unexpected exceptions

Every module boundary that touches a platform API (`:core:database`, `:integration:*`) MUST wrap calls so that it:

1. rethrows `CancellationException` unchanged,
2. maps known SDK failures to typed errors,
3. maps the remainder to `UnexpectedError`, **without** including message text that may contain user data (§17).

Domain and presentation code MUST NOT contain `try` / `catch`.

### Logging ownership

An error is logged exactly once, at the boundary that converts a platform failure into an `AppError` — that is, inside `:integration:*` and `:core:database`. Higher layers propagate silently.

### RemoteError to SyncError mapping

This table is normative; it decides retry versus poison.

| `RemoteError` | `SyncError` | Effect |
|---------------|-------------|--------|
| `Unavailable`, `DeadlineExceeded` | `RetryableNetwork` | retry with backoff, `attemptCount++` |
| `Unauthenticated` | `AuthExpired` | retry after a valid auth session, `attemptCount` unchanged |
| `PermissionDenied` | `PermissionDenied` | **poison** — a rules rejection does not fix itself |
| `InvalidArgument` | `ValidationRejected` | **poison** |
| `NotFound` on push | — | treated as success; the local row is marked synced |
| `NotFound` on pull | — | ignored |
| `Unknown` | `RetryableNetwork` | retry with backoff, `attemptCount++` |

There is exactly **one** retry counter, `outbox.attemptCount`, and exactly one ceiling, `MAX_RETRYABLE_ATTEMPTS` (§9.7). No `RemoteError` carries a separate, lower cap: the outbox holds a single counter, so a per-code ceiling would not be representable and two agents would resolve it differently. `Unknown` is retried on the same terms as `Unavailable` — a rules rejection or an invalid argument poisons immediately because it is known to be permanent, whereas `Unknown` means the opposite, so giving up on it sooner than on a network error would be backwards.

`outbox.lastErrorCode` stores the code of the **originating** error, which for a remote failure is the `RemoteError` code (`REMOTE.UNAVAILABLE`, `REMOTE.UNKNOWN`, …), not the `SyncError` it maps to. This granularity is load-bearing: `Unavailable` and `Unknown` both map to `RetryableNetwork`, and only the originating code separates a failure that MUST NOT poison from one that MUST.

### Read versus write absence

Read APIs express absence as `Ok(null)`. `ValidationError.EntityNotFound` is returned only by **write** operations targeting a missing entity; `EntityDeleted` only by writes targeting a tombstoned entity.

## 7. Sync State Machine

Local sync state enum:

```text
PENDING
SYNCING
SYNCED
FAILED_RETRYABLE
FAILED_POISONED
```

`syncState` is derived from the existence and status of the outbox row. A row in `SYNCING` MUST NOT be deleted by an editor, only by the sync engine.

Allowed transitions:

| From | To | Trigger |
|------|----|---------|
| `SYNCED` | `PENDING` | Local create/update/delete. |
| `PENDING` | `PENDING` | Further local edit. Payload coalesced, `attemptCount = 0`, `nextAttemptAt = 0`. |
| `PENDING` | `SYNCING` | Sync engine starts push for the row. |
| `SYNCING` | `SYNCING` | Local edit during an in-flight push. `localRevision` is incremented; the ack path detects the mismatch. |
| `SYNCING` | `SYNCED` | Remote ack received and `localRevision` unchanged. |
| `SYNCING` | `PENDING` | `localRevision` changed during push. |
| `SYNCING` | `FAILED_RETRYABLE` | Retryable network or remote failure. |
| `FAILED_RETRYABLE` | `PENDING` | Automatic due retry, manual retry, or a local edit. |
| `FAILED_RETRYABLE` | `FAILED_POISONED` | `attemptCount` reaches `MAX_RETRYABLE_ATTEMPTS` **and** `lastErrorCode` is not a connectivity code (§9.7). |
| `SYNCING` | `FAILED_POISONED` | Validation, security or payload failure. |
| `FAILED_POISONED` | `PENDING` | User or repair flow edits the entity and re-enqueues a valid snapshot, **or** the user invokes `SyncController.retryFailed()` (§9.7). |

`FAILED_POISONED` is never retried automatically.

## 8. Outbox Contract

Outbox payload format:

- JSON encoded with `kotlinx.serialization`.
- Includes `schemaVersion` and `entityType`.
- Includes the full entity snapshot under the canonical field names of §3.
- Encodes instants as epoch milliseconds UTC.
- Excludes `syncState`, `localRevision`, `serverUpdatedAt`, `nameFold`, `currentOdometerKm` and any other local-only metadata.

Outbox coalescing:

- At most one outbox row per `(entityType, entityId)`.
- `ON CONFLICT DO UPDATE` replaces `payload` and `localRevision`, and resets `attemptCount = 0`, `nextAttemptAt = 0`, `lastError = NULL`, `lastErrorCode = NULL`.
- The original `seq` MUST be preserved to keep causal order.

**The outbox MUST NOT be populated while `ownerId == LOCAL_OWNER`.** Before a real UID exists, local writes set `syncState = PENDING` but the outbox writer is a no-op. Outbox rows are created for those entities by local-owner adoption (§11.4).

Push dependency order — this is the normative order; a two-group "vehicles before fuel entries" reading is insufficient because it would push a vehicle tombstone ahead of its fuel-entry tombstones:

1. Vehicle upserts.
2. Fuel entry upserts.
3. Fuel entry tombstones.
4. Vehicle tombstones.

Within a group, rows are ordered by `seq`. Dependency grouping is a stable partition of the batch, never a re-sort of `seq` inside a group.

Vehicle deletion creates tombstones for the vehicle and all its non-deleted fuel entries in one local transaction.

Tombstone purge: a tombstone is purgeable locally only when `syncState == SYNCED` **and** `serverUpdatedAt` is older than 90 days **and** no outbox row exists for it. Purge runs at most once per app start, in one transaction, in `:core:sync`. Remote tombstones are never purged in the MVP.

## 9. Sync Cycle Contract

### 9.1 Concurrency

`SyncController` is a singleton in the app graph holding a `Mutex`. All triggers call `requestSync(reason)`, which sets a pending flag and returns immediately; concurrent triggers coalesce into one pending cycle. Android platform triggers MUST use `enqueueUniqueWork(SYNC_WORK, KEEP)` and MUST route through the same in-process `SyncController`; iOS uses a single `BGTaskScheduler` identifier. Cross-process sync is not supported and MUST NOT be introduced.

### 9.2 Cycle admission and order

A cycle MUST NOT start while `ConnectivityObserver.isOnline` is `false`. It ends immediately as a no-op, leaving `attemptCount`, `nextAttemptAt`, `lastError` and `lastErrorCode` untouched, and the pending rows keep their existing state. This is the primary defence against burning the retry budget offline; the qualified poison rule of §9.7 is the second, for connectivity lost mid-cycle.

Once admitted, the order is deterministic, because the convergence simulation depends on it:

- If the local database contains no rows for the owner **and** the outbox is empty: pull, then push.
- Otherwise: push, then pull.

### 9.3 Push

- Batch limit: 50 outbox rows, selected where `nextAttemptAt <= now`, ordered by `seq`, then partitioned by the dependency order of §8.
- Remote writes use the client-generated document ID and a server timestamp.
- The authoritative `serverUpdatedAt` comes from the write result where the SDK provides it; otherwise the document is re-read. The ack timestamp is a **lower bound** on this device's write, never proof of content: a re-read returning newer content is not an error, and the next pull reconciles it.
- Local confirmation happens in one transaction: if `outbox.localRevision == entity.localRevision`, delete the outbox row and mark `SYNCED`; otherwise keep the row and update only `serverUpdatedAt`.

### 9.4 Pull

- Entity types are pulled in dependency order: `VEHICLE` before `FUEL_ENTRY`.
- Cursor stores `(lastServerUpdatedAt, lastDocumentId)`.
- Page limit: 200 documents.
- Query ordering is `updatedAt ASC, documentId ASC`, and pagination MUST use `startAfter(lastServerUpdatedAt, lastDocumentId)`. A query that filters only on `updatedAt >= since` without a `startAfter` anchor is a contract violation: it re-reads the same page forever whenever a timestamp cluster exceeds the page size.
- The 30-second overlap window is applied **once per cycle**, not per page: a cycle starts from `startAfter(max(0, cursor.lastServerUpdatedAt - 30 s), null)`.
- Tombstones are included.
- Each page is applied in one local transaction; apply is idempotent.
- If an outbox row exists for a remote entity, local data is not overwritten.
- Otherwise the remote snapshot is applied iff `local.serverUpdatedAt == null || remote.updatedAt > local.serverUpdatedAt`. **`local.updatedAt` MUST NOT participate in remote conflict arbitration.**
- The cursor advances only after the local transaction succeeds.
- Progress invariant: if a page returns `limit` documents and the resulting cursor is not strictly greater than the cursor that produced it, the engine MUST fail the cycle with `SyncError.ConflictUnresolved` rather than loop.
- Orphan fuel entries (vehicle not yet pulled) are legal transient state. They MUST be persisted, and MUST be excluded from all UI queries and from consumption until their vehicle arrives.

### 9.5 Schema version handling

A pulled document with `schemaVersion > CLIENT_MAX_SCHEMA_VERSION` MUST be stored verbatim in a `quarantine` table keyed by `(entityType, id)`, MUST NOT be applied to the entity table, and MUST NOT block cursor advance. Quarantined rows are re-evaluated on app upgrade. Firestore rules validate only a lower bound on `schemaVersion`, so rules deploys never gate app releases.

### 9.6 Conflict resolution

Last-write-wins at whole-document level using the server `updatedAt`. Tombstones are regular documents and win over older updates.

`(updatedAt, documentId)` is the **total ordering of the pull stream**, used for cursor progress and page boundaries. It is NOT a tie-breaker for arbitrating a single document: both sides of a document conflict share the same id.

Accepted limitation: two devices editing different fields of the same document concurrently can lose one whole-document update.

### 9.7 Backoff and retry

```text
exp   = min(attemptCount, 20)
base  = min(900_000L, 1000L shl exp)          // hard cap 15 minutes
delay = base * (800 + jitter.nextInt(0, 401)) / 1000   // ±20 %
delay = delay.coerceIn(1_000L, 900_000L)
```

The jitter source MUST be injectable for deterministic tests. `MAX_RETRYABLE_ATTEMPTS = 10`.

**Connectivity failures MUST NOT consume the poison budget.** `attemptCount` still increments on every failure, because it is the exponent that drives the backoff, but the poison rule is qualified:

```text
CONNECTIVITY_ERROR_CODES = { "REMOTE.UNAVAILABLE", "REMOTE.DEADLINE_EXCEEDED" }

poison  iff  attemptCount >= MAX_RETRYABLE_ATTEMPTS
        and  lastErrorCode not in CONNECTIVITY_ERROR_CODES
```

A row failing only for connectivity reasons therefore stays `FAILED_RETRYABLE` indefinitely, with `attemptCount` pinned at the ceiling and the backoff at its 15-minute cap, and resumes as soon as the network returns. Without this qualification the constants above poison every pending row after roughly 17 minutes offline — the sum of the backoff series up to attempt 10 — which would violate `docs/SPECIFICATION.md §2` P2 and strand the user's data behind a manual per-entity repair.

Manual retry through `SyncController.retryFailed()` sets `nextAttemptAt = now`, **resets `attemptCount` to 0** and clears `lastError` and `lastErrorCode`. Preserving the count would make manual retry useless on exactly the rows that need it, because the count is already at the ceiling.

### 9.8 Trigger constants

| Trigger | Value |
|---------|-------|
| Post-write debounce | 2 s |
| Minimum interval between automatic cycles | 30 s |
| Periodic background interval | 6 h (Android `WorkManager` periodic; iOS `BGAppRefresh`, best effort) |
| Foreground | on cold start, and on resume after more than 5 min in background |
| Pull-to-refresh | bypasses the minimum interval, never the mutex |

All five are `SyncTrigger` values passed to `requestSync(reason)` and logged with the cycle id.

### 9.9 Aggregate status

`SyncController.status: StateFlow<SyncStatus>` with precedence `Failed > Syncing > Pending > Idle`.

Being offline with pending rows renders as `Pending`, never as an error. This is a rule about aggregation, not only about admission: a row in `FAILED_RETRYABLE` whose `lastErrorCode` is a connectivity code (§9.7) counts towards `Pending`, never towards `Failed`. Otherwise a single failure as the network dropped mid-cycle would show the user an error for a condition that is not one.

## 10. RemoteSyncSource Contract

`RemoteSyncSource` is implemented only by integration modules. The MVP implementation is Firebase-backed. Future API-backed implementations may use Ktor, but Ktor is not an MVP dependency until such an implementation is explicitly approved by ADR.

Required interface shape:

```kotlin
interface RemoteSyncSource {
    suspend fun pushSnapshot(
        ownerId: OwnerId,
        snapshot: EntitySnapshot,
    ): Outcome<RemoteAck, RemoteError>

    suspend fun pullChanges(
        ownerId: OwnerId,
        entityType: EntityType,
        cursor: RemoteCursor,
        limit: Int,
    ): Outcome<RemotePage, RemoteError>
}
```

DTOs are declared in §20.

Side effects:

- `pushSnapshot` performs at most one full-document remote write and at most one remote read per snapshot.
- `pullChanges` performs no local writes.
- On an empty page, `nextCursor` equals the input cursor and `hasMore` is `false`.
- Token refresh is the responsibility of this module: on `Unauthenticated` it MUST force a token refresh through the provider SDK and retry the operation once before mapping to `RemoteError.Unauthenticated`.
- No Firebase, GitLive or Ktor type appears in the interface or the DTOs.

## 11. Auth Contracts

### 11.1 AuthClient

```kotlin
interface AuthClient {
    val authState: StateFlow<AuthState>
    suspend fun signInAnonymously(): Outcome<AuthSession, AuthError>
    suspend fun signInWithCredential(credential: NativeAuthCredential): Outcome<AuthSession, AuthError>
    suspend fun linkCredential(credential: NativeAuthCredential): Outcome<AuthSession, AuthError>
    suspend fun reauthenticate(credential: NativeAuthCredential): Outcome<AuthSession, AuthError>
    suspend fun signOut(): Outcome<Unit, AuthError>
    suspend fun deleteAccount(): Outcome<Unit, AuthError>
}
```

`AuthState` distinguishes *not yet determined* from *signed out* (§20). Routing decisions in `F-1` MUST NOT be made while `AuthState.Unknown`.

Platform UI obtains Google and Apple credentials; common code exchanges or links them.

### 11.2 First launch

First launch MUST succeed offline. The app creates a local session with `ownerId = LOCAL_OWNER`, all MVP features work, and the outbox stays empty (§8). Anonymous UID acquisition is a background task retried on connectivity; on success, local-owner adoption runs (§11.4).

### 11.3 Anonymous conversion

Anonymous conversion MUST preserve the UID through credential linking. If a provider flow would change the UID, the MVP aborts conversion and returns a typed error.

Credential collision cancellation leaves the anonymous session and local data untouched. Choosing the existing account requires `Confirmation.AdoptExistingAccount`, clears local anonymous data, signs into the existing account, then performs an initial pull from `RemoteCursor.INITIAL`.

### 11.4 Local owner adoption

On the first successful authentication after a `LOCAL_OWNER` period, in one transaction:

1. Rewrite every row with `ownerId = LOCAL_OWNER` to the new UID.
2. Increment `localRevision` on each rewritten row.
3. Enqueue an outbox snapshot for every non-synced row, preserving `seq` causality.

The operation MUST be idempotent and MUST be covered by a test that starts from a populated local-owner database. Implemented by story `E2-06`.

### 11.5 Sign-out and account deletion

Sign-out is offered only to a permanently authenticated user. For an anonymous session the action is labelled "delete local data" and requires the same two-step destructive confirmation as account deletion. Signing out clears all local rows for that owner, including `SYNCED` ones; recovery is by re-authenticating and pulling from `RemoteCursor.INITIAL`.

Account deletion order is normative:

1. If `AuthError.RequiresRecentLogin`, require fresh re-authentication first.
2. Delete remote documents in batches of at most 400 per write batch: `fuelEntries`, then `vehicles`, retrying on `Unavailable`.
3. Only after remote deletion fully succeeds, call `deleteAccount()`.
4. Then clear local data.

If step 2 fails, the flow aborts with a typed `AuthError` and the account is NOT deleted. Deleting the auth account before the data would leave unreachable orphan documents.

### 11.6 App Graph Contract

`:shared` exposes the application graph through a dependency container:

```kotlin
data class AppGraphDependencies(
    val databaseFactory: DatabaseFactory,
    val authClient: AuthClient,
    val tokenProvider: TokenProvider,
    val ownerContext: OwnerContext,
    val remoteSyncSource: RemoteSyncSource,
    val analyticsTracker: AnalyticsTracker,
    val clock: AppClock,
    val dispatchers: DispatcherProvider,
    val uuidGenerator: UuidGenerator,
    val logger: Logger,
    val isDebugBuild: Boolean,
    val localeProvider: LocaleProvider,
    val connectivityObserver: ConnectivityObserver,
    val syncTriggerAdapter: SyncTriggerAdapter,
)

fun createAppGraph(dependencies: AppGraphDependencies): AppGraph
```

Rules:

- Koin may construct `AppGraphDependencies` in wiring and platform modules.
- It MUST contain abstractions only. Firebase, GitLive, Koin, Ktor, Android and iOS concrete types MUST NOT appear in it.
- Tests provide fakes without starting Koin, through the `:core:testing` factory `testAppGraphDependencies(...)` in which every parameter is defaulted. Adding a member REQUIRES updating that factory in the same change.
- `AppGraph` (§20) exposes state-holder factories, `SyncController` and `close()` — never repositories, use cases or DAOs.

## 12. Repository Contracts

Repositories are interfaces owned by feature domain packages. Implementations live in feature data packages.

All write methods:

- Run in local database transactions where multiple rows or outbox entries change.
- Stamp `ownerId` from `OwnerContext`, plus `id`, `createdAt`, `updatedAt`, `localRevision`. Commands never carry these.
- Enqueue outbox snapshots for synchronized entities, subject to §8.
- Return `Outcome<..., AppError>`.
- Never call Firebase directly.

Repositories obtain the current owner through `OwnerContext` from `:core:common`. Feature `data` MUST NOT depend on `:core:auth`.

### VehicleRepository

```kotlin
interface VehicleRepository {
    fun observeVehicles(includeDeleted: Boolean): Flow<Outcome<List<Vehicle>, AppError>>
    fun observeVehicle(id: EntityId): Flow<Outcome<Vehicle?, AppError>>
    suspend fun createVehicle(command: CreateVehicleCommand): Outcome<EntityId, AppError>
    suspend fun updateVehicle(command: UpdateVehicleCommand): Outcome<Unit, AppError>
    suspend fun deleteVehicle(id: EntityId): Outcome<Unit, AppError>
}
```

| Method | Side effects | Errors it may return |
|--------|--------------|----------------------|
| `observeVehicles` | none | `PersistenceError` |
| `observeVehicle` | none; absence is `Ok(null)` | `PersistenceError` |
| `createVehicle` | inserts one row, enqueues one outbox snapshot | `ValidationError.{RequiredField, InvalidLength, OutOfRange, DuplicateName}`, `PersistenceError` |
| `updateVehicle` | updates one row, bumps `localRevision`, coalesces the outbox snapshot | as above plus `EntityNotFound`, `EntityDeleted` |
| `deleteVehicle` | tombstones the vehicle and all its non-deleted fuel entries in one transaction, enqueues tombstone snapshots in dependency order | `EntityNotFound`, `PersistenceError` |

Default arguments are omitted deliberately: they do not exist in the Objective-C export.

### FuelEntryRepository

```kotlin
interface FuelEntryRepository {
    fun observeFuelEntries(vehicleId: EntityId, includeDeleted: Boolean): Flow<Outcome<List<FuelEntryListItem>, AppError>>
    suspend fun getFuelEntry(id: EntityId): Outcome<FuelEntry?, AppError>
    suspend fun createFuelEntry(command: CreateFuelEntryCommand): Outcome<EntityId, AppError>
    suspend fun updateFuelEntry(command: UpdateFuelEntryCommand): Outcome<Unit, AppError>
    suspend fun deleteFuelEntry(id: EntityId): Outcome<Unit, AppError>
    fun observeConsumption(vehicleId: EntityId): Flow<Outcome<ConsumptionReport, AppError>>
}
```

| Method | Side effects | Errors it may return |
|--------|--------------|----------------------|
| `observeFuelEntries` | none; returns a lightweight projection, excludes orphans | `PersistenceError` |
| `getFuelEntry` | none; absence is `Ok(null)` | `PersistenceError` |
| `createFuelEntry` | inserts one row, recomputes `currentOdometerKm` and `odometerInconsistent` (§3.1), enqueues one outbox snapshot | `ValidationError.*`, `ValidationWarning.OdometerInconsistent`, `PersistenceError` |
| `updateFuelEntry` | as create, plus recomputation for the successor entry | as above plus `EntityNotFound`, `EntityDeleted` |
| `deleteFuelEntry` | tombstones one row, recomputes read models, enqueues a tombstone snapshot | `EntityNotFound`, `PersistenceError` |
| `observeConsumption` | none | `PersistenceError` |

The MVP loads at most `MAX_ENTRIES_IN_MEMORY = 5_000` entries per vehicle. Consumption is computed from a dedicated projection query, not from the UI list.

### SettingsRepository

```kotlin
interface SettingsRepository {
    val settings: Flow<Outcome<UserSettings, AppError>>
    suspend fun updateSettings(command: UpdateSettingsCommand): Outcome<Unit, AppError>
}
```

Settings are device-local (§3). `updateSettings` writes one row and enqueues nothing.

## 13. Use Case Contracts

Every use case:

- Lives in a feature `domain` package or an appropriate `:core:*` module.
- Accepts immutable command or query models, declared in §20. Write command models are not UI form draft models.
- Returns `Outcome<T, AppError>` for expected failures.
- Does not depend on platform APIs, and does not access Room, Firebase, GitLive, Koin, Ktor, Android or iOS APIs directly.

Consumption calculation:

```kotlin
fun interface CalculateConsumption {
    operator fun invoke(
        vehicleId: EntityId,
        initialOdometerKm: Long,
        entries: List<FuelEntry>,
    ): ConsumptionReport
}
```

Contract: `entries` MUST contain only non-deleted entries of `vehicleId`, in any order; the implementation sorts them in calculation order (§4). The function is **pure and total** — it never throws and returns no error type. Invalid segments are represented as `SegmentResult.Invalid(reason)`.

## 14. Presentation State Contract

Shared state holders:

- Live in feature `presentation` packages.
- Expose immutable `StateFlow<UiState>` built with `stateIn(scope, SharingStarted.WhileSubscribed(5_000), initialValue)`.
- Accept intent functions.
- Take `scope: CoroutineScope` in the constructor and expose `close()`. The platform adapter owns creation and cancellation: Android uses `viewModelScope`, iOS creates the scope in the `ObservableObject` `init` and cancels it in `deinit`.
- Emit every `UiState` on `dispatchers.main`; database and computation work uses `dispatchers.io` or `dispatchers.default`.
- Use the injected `DispatcherProvider`. Never create `GlobalScope`.
- Never call platform UI, Firebase, GitLive or Koin APIs.

`UiState` MUST NOT contain user-facing text. Messages are represented as typed values (`AppError` leaves, `ConsumptionInvalidReason`, enum states) that each platform maps to its own string resources. Numbers and dates reach the UI as raw scaled values; formatting is platform-side. This is what makes "no hardcoded user-facing strings" achievable from shared code.

Platform adapters contain rendering and lifecycle glue only. Validation, formatting decisions, repository calls and business logic remain shared.

## 15. Platform Boundary Contract

### 15.1 Mechanism

Injection is the **only** mechanism for anything present in `AppGraphDependencies`. `expect`/`actual` is reserved for platform factory functions that construct those implementations, and for native credential acquisition.

`expect`/`actual` declarations MUST be `internal` and MUST NOT appear in any public API surface.

Allowed `expect`/`actual`:

- Internal factories for the Room driver and database file location.
- Internal factory for UUID generation.
- Internal factories for connectivity, locale, logger sink and analytics sink implementations.
- Native Google and Apple credential acquisition.

Forbidden `expect`/`actual`:

- Domain entities, business validation, consumption calculation.
- Repository interfaces, sync algorithm, conflict resolution, error taxonomy.
- Anything already injected through `AppGraphDependencies`.

### 15.2 Dependency Injection Contract

Koin KMP is the accepted dependency injection library for the MVP.

- Koin modules are wiring artifacts only.
- Koin APIs are allowed in application, wiring, integration and platform composition modules.
- Koin APIs are FORBIDDEN in feature `domain` packages, use cases, repository interfaces, repository implementations and shared presentation business logic.
- Implementation classes use constructor injection.
- Domain tests MUST NOT require a Koin runtime.
- Koin MUST NOT become a service locator inside product logic.

### 15.3 Swift-facing surface

The public API of `:shared` MUST use only `String`, `Long`, `Int`, `Boolean`, `data class`, `sealed class`, `enum class` and `Flow` of those. It MUST NOT use `value class`, type parameters, or default arguments, because inline value classes are not exported to Objective-C, generic hierarchies export poorly, and default arguments do not exist in the export.

Repository and use-case interfaces are Kotlin-internal contracts and MUST NOT be exported to Swift. Only `AppGraph`, state holders, `UiState` data classes, `SyncStatus` and the typed enums they reference are exported.

A golden file of the generated Objective-C header is committed and diffed on every PR; a change to it is a review signal.

### 15.4 HTTP/API Client Contract

Ktor is the reserved HTTP client for future API-based remote implementations.

- Do not add Ktor dependencies during the MVP while Firebase Firestore is the selected remote database implementation.
- A future Ktor implementation must implement existing provider abstractions such as `RemoteSyncSource`.
- Ktor types must not appear in feature, domain, repository or presentation contracts.
- Adding Ktor requires an ADR update and a backlog story defining the target API contract.

### 15.5 Image Loading Contract

Coil is the approved image loading library if the project ever needs image loading.

- Do not add image loading dependencies until a backlog story requires image loading.
- No alternative image loading library may be introduced without updating `docs/DECISION_BOARD.md` and adding or updating an ADR.
- Image loading must remain in UI and platform layers and must not enter domain, data or sync logic.

## 16. Firestore Contract

Remote collections:

```text
users/{uid}/vehicles/{vehicleId}
users/{uid}/fuelEntries/{entryId}
```

There is no remote settings document in the MVP (§3).

Remote rules, split by operation. `allow write` is not used, because it would include delete, and on delete `request.resource` is null:

```javascript
rules_version = '2';
service cloud.firestore {
  match /databases/{db}/documents {
    match /users/{uid}/{collection}/{docId} {

      allow read: if request.auth != null && request.auth.uid == uid;

      allow create, update: if request.auth != null
        && request.auth.uid == uid
        && request.resource.data.ownerId == uid
        && request.resource.data.updatedAt == request.time
        && request.resource.data.schemaVersion is int
        && request.resource.data.schemaVersion >= 1
        && request.resource.data.id == docId
        && validPayload();

      // Deletion is a tombstone update. Hard deletes are forbidden by design.
      allow delete: if false;
    }
  }
}
```

`validPayload()` MUST enforce presence, primitive type **and range** for every field, mirroring the intervals of §5 — for example `notes` size at most 280, `litersScaled` an int in `1..500000`, `odometerKm` an int in `0..2000000`. Without App Check, range validation in rules is the only thing preventing a compromised client from writing a document that breaks parsing on the user's other device.

Anonymous users are valid authenticated users.

Remote queries:

```text
where(updatedAt >= since)
orderBy(updatedAt ASC)
orderBy(documentId ASC)
startAfter(cursor.lastServerUpdatedAt, cursor.lastDocumentId)
limit(200)
```

This query is served by the automatic single-field index. `firestore/firestore.indexes.json` MUST exist and MUST be empty until a query requires a composite index.

Required emulator tests:

- User A cannot read under `users/B`.
- User A cannot write under `users/B`.
- Anonymous user can read and write under their own UID.
- Writes with a client-controlled `updatedAt` are rejected.
- Writes with `ownerId != uid` are rejected.
- Hard delete is rejected.
- Out-of-range field values are rejected.
- Tombstones are returned by delta pull.

## 16.1 Analytics Contract

```kotlin
interface AnalyticsTracker {
    fun track(event: AnalyticsEvent)
    fun setUserProperties(properties: AnalyticsUserProperties)
    fun setEnabled(enabled: Boolean)
}
```

`AnalyticsEvent` is a **closed sealed hierarchy** (§20): one leaf per allowed event, each carrying only enum, boolean or bucketed-integer parameters. No leaf may carry a free-text `String`. This makes the forbidden-payload rule enforceable by the type system rather than by review.

Allowed events are exactly the leaves declared in §20.

Forbidden in any payload: exact odometer values, exact fuel volume, exact cost or price, notes, raw entity IDs, Firebase UID, auth tokens or credentials, raw sync payloads.

Collection is **disabled by default**. It is enabled only after the user opts in from Settings. While disabled, `track` and `setUserProperties` are no-ops and nothing is buffered.

Analytics calls are FORBIDDEN in domain logic and data persistence logic. Shared presentation or application-level orchestration may track product events after successful use case results.

## 17. Logging and Privacy

```kotlin
interface Logger {
    fun log(
        level: LogLevel,
        tag: String,
        message: String,
        fields: Map<String, String>,
        throwable: Throwable?,
    )
}
```

Levels: `DEBUG`, `INFO`, `WARN`, `ERROR`.

Every sync cycle generates a `cycleId` that is included as a field on every sync log line and stored in `outbox.lastError` context. Field values come from an allowlist — codes, counts, entity types, sync states — never user data.

Logs MUST never include: ID tokens or credentials, raw Firestore payloads, notes, exact odometer values, exact costs, or the Firebase UID in release builds.

Redaction is decided from the injected `isDebugBuild` flag: debug builds may log entity IDs in full; release builds log the first 8 characters followed by an ellipsis, and never log the UID at any length.

## 18. CI and Branch Protection Contract

Phase 0 defines the exact CI check names. Required checks:

- `android-assemble`
- `shared-tests`
- `ios-simulator-build`
- `ktlint`
- `detekt`
- `architecture-check`
- `provider-decoupling`
- `contract-check`

`contract-check` is a script that asserts:

1. Every type named in a code block in this document is declared in §20.
2. The decision ID set and status are identical in `docs/DECISION_BOARD.md`, `docs/SPECIFICATION.md §12`, `docs/TECHNICAL_PLAN.md §2` and `docs/adr/README.md`.
3. Every ADR linked from `docs/adr/README.md` has a `Status` heading whose value matches the status recorded for its decision ID.
4. Every `Proposed` or `Pending` decision in `docs/DECISION_BOARD.md` appears in the "Decisions Awaiting Owner Confirmation" table with a `Needed by` story or phase.
5. Every interface declared in this document appears in at least one `docs/BACKLOG.md` story.
6. `.github/pull_request_template.md` remains a superset of `docs/templates/agent-handoff.md` section headings.
7. The committed Objective-C header golden file for `:shared` is unchanged.

Once CI exists, branch protection for `main` MUST require these checks before merge.

## 19. Human Review

The canonical human review gate list lives in `AGENTS.md` and MUST NOT be restated here. Any change to this document is gated.

## 20. Canonical Type Definitions

Every type referenced by a signature in this document is declared here. Implementations MUST match these shapes.

### 20.0 Identifiers, money and scaled values — `:core:model`

The foundational types of §2. They were previously described only in prose tables, which left their property names, widths and construction semantics open.

```kotlin
@JvmInline value class EntityId(val value: String)         // lowercase canonical UUID v4
@JvmInline value class OwnerId(val value: String)          // Firebase UID, or LOCAL_OWNER
@JvmInline value class CurrencyCode(val value: String)     // ISO-4217 uppercase

val LOCAL_OWNER: OwnerId = OwnerId("LOCAL_OWNER")          // §11.2, §11.4

data class Money(val minorUnits: Long, val currency: CurrencyCode)

@JvmInline value class FuelVolume(val scaled: Long)          // litres × 1000
@JvmInline value class PricePerLiter(val scaled: Long)       // currency units × 1000
@JvmInline value class ConsumptionL100Km(val scaled: Long)   // L/100 km × 100
```

**Property naming is canonical.** Identifier types expose `value`; scaled quantity types expose `scaled`. An implementation using `raw`, `id`, `amount` or a unit-specific name is a contract violation. `scaled` deliberately matches the `…Scaled` field suffix of §3, so `fuelEntry.litersScaled` and `FuelVolume.scaled` name the same idea with the same word.

**Construction never validates.** These types have no `init` block, throw nothing, and reject nothing. Wrapping a malformed UUID or an unsupported currency code is legal at the type level. Validation lives in the use cases of §5, which return typed errors.

This is a deliberate constraint, not an oversight. §5 requires that a pull transaction MUST NOT fail because of a domain constraint, and its only legal failures are I/O, serialization and schema-version quarantine. A throwing constructor would turn one malformed remote document into an exception inside the pull transaction, stalling the cursor permanently — the exact failure mode §5 exists to prevent. The MVP ships without App Check (`docs/SECURITY.md`), so such a document is reachable, and Firestore rules validate ranges and types but cannot verify that a string is a real ISO-4217 code.

**Every scaled value is a `Long`**, per §2. Mixing widths across these types is a contract violation.

All of the above are Kotlin-internal and MUST NOT appear on the Swift-facing surface (§15.3).

### 20.0.1 Named constants — `:core:common`

Constants referred to by name elsewhere in this document. Writing the literal inline instead of referencing one of these is a contract violation. They live beside the backoff helper in `:core:common` (`docs/TECHNICAL_PLAN.md §3`), which every module that needs them already depends on; `LOCAL_OWNER` is the exception and lives with `OwnerId` in §20.0.

```kotlin
const val CLIENT_MAX_SCHEMA_VERSION: Int = 1   // §9.5  — highest schemaVersion this client applies
const val MAX_RETRYABLE_ATTEMPTS: Int = 10     // §9.7  — attemptCount ceiling
const val MAX_ENTRIES_IN_MEMORY: Int = 5_000   // §12   — per-vehicle fuel entry load ceiling
const val SYNC_WORK: String = "carapp-sync"    // §9.1  — Android enqueueUniqueWork name

// §9.7 — failures that MUST NOT consume the poison budget
val CONNECTIVITY_ERROR_CODES: Set<String> = setOf(
    "REMOTE.UNAVAILABLE",
    "REMOTE.DEADLINE_EXCEEDED",
)
```

`CLIENT_MAX_SCHEMA_VERSION` is bumped only by a story that also ships the migration able to read the new version, and bumping it REQUIRES re-evaluating the `quarantine` table on upgrade (§9.5).

### 20.1 Result channel — `:core:common`

```kotlin
sealed interface Outcome<out T, out E> {
    data class Ok<out T>(val value: T) : Outcome<T, Nothing>
    data class Err<out E>(val error: E) : Outcome<Nothing, E>
}
// Extensions: map, mapError, flatMap, getOrNull, fold.
```

### 20.2 Error taxonomy — `:core:common`

```kotlin
sealed interface AppError { val code: String }

sealed interface ValidationError : AppError {
    data class RequiredField(val field: String) : ValidationError { override val code = "VALIDATION.REQUIRED_FIELD" }
    data class InvalidLength(val field: String, val min: Int, val max: Int) : ValidationError { override val code = "VALIDATION.INVALID_LENGTH" }
    data class OutOfRange(val field: String, val min: Long, val max: Long) : ValidationError { override val code = "VALIDATION.OUT_OF_RANGE" }
    data class DuplicateName(val name: String) : ValidationError { override val code = "VALIDATION.DUPLICATE_NAME" }
    data object FutureDate : ValidationError { override val code = "VALIDATION.FUTURE_DATE" }
    data object InvalidMoneyInput : ValidationError { override val code = "VALIDATION.INVALID_MONEY_INPUT" }
    data class InvalidUnit(val detail: String) : ValidationError { override val code = "VALIDATION.INVALID_UNIT" }
    data object EntityDeleted : ValidationError { override val code = "VALIDATION.ENTITY_DELETED" }
    data object EntityNotFound : ValidationError { override val code = "VALIDATION.ENTITY_NOT_FOUND" }
}

sealed interface ValidationWarning : AppError {
    data class OdometerInconsistent(val previousOdometerKm: Long, val enteredOdometerKm: Long) :
        ValidationWarning { override val code = "WARNING.ODOMETER_INCONSISTENT" }
    data class PendingSyncBeforeSignOut(val pendingCount: Int) :
        ValidationWarning { override val code = "WARNING.PENDING_SYNC" }
}

sealed interface AuthError : AppError {
    data object Cancelled : AuthError { override val code = "AUTH.CANCELLED" }
    data object NetworkUnavailable : AuthError { override val code = "AUTH.NETWORK_UNAVAILABLE" }
    data object CredentialAlreadyInUse : AuthError { override val code = "AUTH.CREDENTIAL_ALREADY_IN_USE" }
    data object ProviderUnavailable : AuthError { override val code = "AUTH.PROVIDER_UNAVAILABLE" }
    data object TokenExpired : AuthError { override val code = "AUTH.TOKEN_EXPIRED" }
    data object PermissionDenied : AuthError { override val code = "AUTH.PERMISSION_DENIED" }
    data object RequiresRecentLogin : AuthError { override val code = "AUTH.REQUIRES_RECENT_LOGIN" }
    data object UidWouldChange : AuthError { override val code = "AUTH.UID_WOULD_CHANGE" }
    data object Unknown : AuthError { override val code = "AUTH.UNKNOWN" }
}

sealed interface PersistenceError : AppError {
    data object DatabaseUnavailable : PersistenceError { override val code = "PERSISTENCE.DATABASE_UNAVAILABLE" }
    data object TransactionFailed : PersistenceError { override val code = "PERSISTENCE.TRANSACTION_FAILED" }
    data object MigrationFailed : PersistenceError { override val code = "PERSISTENCE.MIGRATION_FAILED" }
    data object SerializationFailed : PersistenceError { override val code = "PERSISTENCE.SERIALIZATION_FAILED" }
    data object ConstraintViolation : PersistenceError { override val code = "PERSISTENCE.CONSTRAINT_VIOLATION" }
}

sealed interface SyncError : AppError {
    data object RetryableNetwork : SyncError { override val code = "SYNC.RETRYABLE_NETWORK" }
    data object AuthExpired : SyncError { override val code = "SYNC.AUTH_EXPIRED" }
    data object PermissionDenied : SyncError { override val code = "SYNC.PERMISSION_DENIED" }
    data object ValidationRejected : SyncError { override val code = "SYNC.VALIDATION_REJECTED" }
    data object PayloadPoisoned : SyncError { override val code = "SYNC.PAYLOAD_POISONED" }
    data object ConflictUnresolved : SyncError { override val code = "SYNC.CONFLICT_UNRESOLVED" }
    data object RemoteUnavailable : SyncError { override val code = "SYNC.REMOTE_UNAVAILABLE" }
}

sealed interface RemoteError : AppError {
    data object Unavailable : RemoteError { override val code = "REMOTE.UNAVAILABLE" }
    data object DeadlineExceeded : RemoteError { override val code = "REMOTE.DEADLINE_EXCEEDED" }
    data object PermissionDenied : RemoteError { override val code = "REMOTE.PERMISSION_DENIED" }
    data object Unauthenticated : RemoteError { override val code = "REMOTE.UNAUTHENTICATED" }
    data object InvalidArgument : RemoteError { override val code = "REMOTE.INVALID_ARGUMENT" }
    data object NotFound : RemoteError { override val code = "REMOTE.NOT_FOUND" }
    data object Unknown : RemoteError { override val code = "REMOTE.UNKNOWN" }
}

sealed interface SecurityError : AppError {
    data object RulesRejected : SecurityError { override val code = "SECURITY.RULES_REJECTED" }
    data object OwnerMismatch : SecurityError { override val code = "SECURITY.OWNER_MISMATCH" }
}

data class UnexpectedError(
    val origin: String,
    val throwableClassName: String,
) : AppError { override val code = "UNEXPECTED" }

enum class Confirmation { OdometerInconsistent, DiscardPendingChanges, DeleteAccount, AdoptExistingAccount }
```

### 20.3 Platform abstractions — `:core:common`

```kotlin
fun interface AppClock { fun now(): Instant }

fun interface UuidGenerator { fun newId(): String }   // lowercase canonical UUID v4

interface DispatcherProvider {
    val main: CoroutineDispatcher
    val default: CoroutineDispatcher
    val io: CoroutineDispatcher
}

enum class LogLevel { DEBUG, INFO, WARN, ERROR }

data class LocaleInfo(val languageTag: String, val region: String?, val suggestedCurrency: CurrencyCode)
fun interface LocaleProvider { fun current(): LocaleInfo }

interface ConnectivityObserver { val isOnline: StateFlow<Boolean> }

enum class SyncTrigger { AppForeground, ConnectivityRecovered, PostWriteDebounce, PullToRefresh, Periodic }
fun interface SyncTriggerAdapter { fun schedule(reason: SyncTrigger) }

interface OwnerContext {
    val current: OwnerId
    fun observe(): Flow<OwnerId>
}

interface DatabaseFactory { fun create(): AppDatabase }

object MinorUnits { fun factorFor(currency: CurrencyCode): Int }   // 2-decimal ISO-4217 only in the MVP
```

### 20.4 Domain models — `:core:model`

```kotlin
enum class FuelType { GASOLINE, DIESEL, LPG, CNG, ELECTRIC, HYBRID, OTHER }
enum class DistanceUnit { KM, MILES }
enum class VolumeUnit { LITER, GALLON }

data class Vehicle(
    val id: EntityId,
    val ownerId: OwnerId,
    val name: String,
    val initialOdometerKm: Long,
    val currentOdometerKm: Long,
    val brand: String?,
    val model: String?,
    val fuelType: FuelType,
    val createdAt: Instant,
    val updatedAt: Instant,
    val deletedAt: Instant?,
)

data class FuelEntry(
    val id: EntityId,
    val ownerId: OwnerId,
    val vehicleId: EntityId,
    val date: Instant,
    val odometerKm: Long,
    val litersScaled: Long,
    val pricePerLiterScaled: Long,
    val totalCostMinor: Long,
    val currency: CurrencyCode,
    val isFullTank: Boolean,
    val hasMissedEntries: Boolean,
    val odometerInconsistent: Boolean,
    val notes: String?,
    val createdAt: Instant,
    val updatedAt: Instant,
    val deletedAt: Instant?,
)

data class FuelEntryListItem(
    val id: EntityId,
    val date: Instant,
    val odometerKm: Long,
    val litersScaled: Long,
    val totalCostMinor: Long,
    val currency: CurrencyCode,
    val isFullTank: Boolean,
    val consumption: ConsumptionL100Km?,
    val invalidReason: ConsumptionInvalidReason?,
)

data class UserSettings(
    val currency: CurrencyCode,
    val distanceUnit: DistanceUnit,
    val volumeUnit: VolumeUnit,
    val analyticsEnabled: Boolean,
)
```

### 20.5 Commands

```kotlin
data class CreateVehicleCommand(
    val name: String,
    val initialOdometerKm: Long,
    val brand: String?,
    val model: String?,
    val fuelType: FuelType,
    val confirmations: Set<Confirmation>,
)

data class UpdateVehicleCommand(
    val id: EntityId,
    val name: String,
    val initialOdometerKm: Long?,   // null = unchanged; non-null only while the vehicle has no entries
    val brand: String?,
    val model: String?,
    val fuelType: FuelType,
    val confirmations: Set<Confirmation>,
)

// Encodes R-2's "any two of three" so that a wrong combination is unrepresentable.
sealed interface MoneyInput {
    data class LitersAndPrice(val litersScaled: Long, val pricePerLiterScaled: Long) : MoneyInput
    data class LitersAndTotal(val litersScaled: Long, val totalCostMinor: Long) : MoneyInput
    data class PriceAndTotal(val pricePerLiterScaled: Long, val totalCostMinor: Long) : MoneyInput
}

data class CreateFuelEntryCommand(
    val vehicleId: EntityId,
    val date: Instant,
    val odometerKm: Long,
    val money: MoneyInput,
    val currency: CurrencyCode,
    val isFullTank: Boolean,
    val hasMissedEntries: Boolean,
    val notes: String?,
    val confirmations: Set<Confirmation>,
)

data class UpdateFuelEntryCommand(
    val id: EntityId,
    val vehicleId: EntityId,
    val date: Instant,
    val odometerKm: Long,
    val money: MoneyInput,
    val currency: CurrencyCode,
    val isFullTank: Boolean,
    val hasMissedEntries: Boolean,
    val notes: String?,
    val confirmations: Set<Confirmation>,
)

data class UpdateSettingsCommand(
    val currency: CurrencyCode?,
    val analyticsEnabled: Boolean?,
)
```

### 20.6 Consumption

```kotlin
enum class ConsumptionInvalidReason {
    NoPreviousFullTank,
    EndEntryNotFullTank,
    MissedEntriesInSegment,
    InconsistentOdometerInSegment,
    NonPositiveDistance,
    DuplicateOdometerInSegment,
}

sealed interface SegmentResult {
    data class Valid(
        val fromEntryId: EntityId,
        val toEntryId: EntityId,
        val litersScaled: Long,
        val distanceKm: Long,
        val consumption: ConsumptionL100Km,
    ) : SegmentResult

    data class Invalid(
        val toEntryId: EntityId,
        val reason: ConsumptionInvalidReason,
    ) : SegmentResult
}

data class ConsumptionReport(
    val segments: List<SegmentResult>,   // one per entry with isFullTank = true, in calculation order
    val validSegmentCount: Int,
    val average: ConsumptionL100Km?,     // distance-weighted; null when validSegmentCount == 0
    val isReliable: Boolean,             // validSegmentCount >= 2
)
```

`SegmentResult.Valid.consumption` and `ConsumptionReport.average` are both produced by the canonical consumption arithmetic of §2, which is the only normative statement of those formulas. `average` is distance-weighted over the valid segments only, and is NOT the arithmetic mean of the segment values.

### 20.7 Sync types — `:core:sync`

```kotlin
enum class EntityType(val collection: String) {
    VEHICLE("vehicles"),
    FUEL_ENTRY("fuelEntries"),
}

data class EntitySnapshot(
    val entityType: EntityType,
    val entityId: EntityId,
    val schemaVersion: Int,
    val json: String,
)

data class RemoteSnapshot(
    val entityType: EntityType,
    val entityId: EntityId,
    val schemaVersion: Int,
    val serverUpdatedAt: Instant,
    val deleted: Boolean,
    val json: String,
)

data class RemoteAck(
    val entityType: EntityType,
    val entityId: EntityId,
    val serverUpdatedAt: Instant,
)

data class RemoteCursor(
    val lastServerUpdatedAt: Instant,
    val lastDocumentId: EntityId?,
) {
    companion object {
        val INITIAL = RemoteCursor(Instant.fromEpochMilliseconds(0), null)
    }
}

data class RemotePage(
    val items: List<RemoteSnapshot>,
    val nextCursor: RemoteCursor,
    val hasMore: Boolean,
)

sealed interface SyncStatus {
    data object Idle : SyncStatus
    data object Syncing : SyncStatus
    data class Pending(val count: Int) : SyncStatus
    data class Failed(val retryableCount: Int, val poisonedCount: Int) : SyncStatus
}

interface SyncController {
    val status: StateFlow<SyncStatus>
    fun requestSync(reason: SyncTrigger)
    suspend fun retryFailed(): Outcome<Unit, AppError>
}
```

A `sync_cursor` row is created lazily on first pull with `RemoteCursor.INITIAL`. Deleting the row is the only supported way to force a full re-pull.

### 20.8 Auth types — `:core:auth`

```kotlin
enum class AuthProvider { ANONYMOUS, GOOGLE, APPLE }

data class AuthSession(
    val uid: String,
    val isAnonymous: Boolean,
    val providers: Set<AuthProvider>,
)

sealed interface AuthState {
    data object Unknown : AuthState      // not yet determined; routing MUST wait
    data object SignedOut : AuthState
    data class SignedIn(val session: AuthSession) : AuthState
}

sealed interface NativeAuthCredential {
    data class Google(val idToken: String, val accessToken: String?) : NativeAuthCredential
    data class Apple(val idToken: String, val rawNonce: String) : NativeAuthCredential
}

data class AuthToken(val value: String, val expiresAt: Instant)

interface TokenProvider {
    suspend fun getIdToken(forceRefresh: Boolean): Outcome<AuthToken, AuthError>
}
```

### 20.9 Analytics types — `:core:analytics`

```kotlin
sealed interface AnalyticsEvent {
    data object OnboardingStarted : AnalyticsEvent
    data object OnboardingCompleted : AnalyticsEvent
    data object AnonymousSignInSelected : AnalyticsEvent
    data class PermanentSignInSelected(val provider: AuthProvider) : AnalyticsEvent
    data object VehicleCreated : AnalyticsEvent
    data class FuelEntryCreated(val isFullTank: Boolean, val hadNotes: Boolean) : AnalyticsEvent
    data class SyncStatusChanged(val status: SyncStatusCategory) : AnalyticsEvent
    data object AccountConversionStarted : AnalyticsEvent
    data object AccountConversionCompleted : AnalyticsEvent
    data class AccountConversionFailed(val reason: ConversionFailureReason) : AnalyticsEvent
    data object AccountDeletionStarted : AnalyticsEvent
    data object AccountDeletionCompleted : AnalyticsEvent
    data class AccountDeletionFailed(val reason: DeletionFailureReason) : AnalyticsEvent
}

enum class SyncStatusCategory { IDLE, SYNCING, PENDING, FAILED }
enum class ConversionFailureReason { CANCELLED, CREDENTIAL_IN_USE, NETWORK, UID_WOULD_CHANGE, UNKNOWN }
enum class DeletionFailureReason { REQUIRES_RECENT_LOGIN, REMOTE_FAILED, NETWORK, UNKNOWN }

data class AnalyticsUserProperties(
    val vehicleCountBucket: CountBucket,
    val entryCountBucket: CountBucket,
)

enum class CountBucket { ZERO, ONE, TWO_TO_FIVE, SIX_TO_TWENTY, MORE_THAN_TWENTY }
```

No leaf carries a free-text `String`. Adding one is a contract violation.

### 20.10 Shared surface — `:shared`

```kotlin
interface AppGraph {
    fun vehicleListStateHolder(scope: CoroutineScope): VehicleListStateHolder
    fun vehicleFormStateHolder(scope: CoroutineScope, vehicleId: String?): VehicleFormStateHolder
    fun fuelEntryListStateHolder(scope: CoroutineScope, vehicleId: String): FuelEntryListStateHolder
    fun fuelEntryFormStateHolder(scope: CoroutineScope, vehicleId: String, entryId: String?): FuelEntryFormStateHolder
    fun sessionStateHolder(scope: CoroutineScope): SessionStateHolder
    fun syncController(): SyncController
    fun close()
}
```

Identifiers cross this boundary as `String`, never as `EntityId`, per §15.3.
