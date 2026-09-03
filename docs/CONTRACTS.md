# System Contracts - carApp MVP

> Normative guardrail layer for implementation. This document defines API contracts, canonical types, state machines, persistence formats, error taxonomy, and boundary rules that future agents MUST follow.

## 1. Authority

Document authority, reading order and normative language (MUST / SHOULD / MAY) are defined once in `AGENTS.md` and MUST NOT be restated here.

What that means for this document:

- `docs/SPECIFICATION.md` is authoritative for **behaviour**: scope, business rules, flows and non-functional targets.
- This document is authoritative for **representation**: types, signatures, field names, numeric semantics, persistence formats, state machines and boundaries. On any representational detail this document wins, including against `docs/SPECIFICATION.md`.
- A type MAY be referenced in a signature in this document only if it is declared in §20. Introducing a new type into a public signature REQUIRES updating §20 in the same change, and is a human review gate.
- A conflict that is behavioural, or that cannot be classified on either axis, MUST be escalated to the project owner rather than resolved by an agent.

### 1.1 Canonical module inventory

The module inventory below is the canonical representation list. `docs/SPECIFICATION.md §8.2` MUST reference this section instead of duplicating the inventory. `docs/TECHNICAL_PLAN.md §3` MUST reproduce the same module paths and host directories in the same order, and MAY add descriptions after the path on the same line; `contract-check` compares only the path token before whitespace.

```text
build-logic/
gradle/libs.versions.toml

:core:model
:core:common
:core:database
:core:auth
:core:sync
:core:analytics
:core:crash
:core:testing

:integration:firebase-auth
:integration:firebase-firestore
:integration:firebase-analytics
:integration:firebase-crashlytics

:feature:vehicle
:feature:fuel
:feature:session

:shared
:shared:testing
:wiring:firebase
:composition:ios
:androidApp
iosApp/
firestore/
```

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
| Domain instant | `Instant` (exact package pinned in `docs/versions-matrix.md`) | epoch milliseconds in SQLite columns and JSON payloads; Firestore timestamp remotely | UTC only. No local timezone persistence. |
| Local `updatedAt` | `Instant` | INTEGER epoch milliseconds | Provisional local timestamp. **Never** authoritative for remote conflict arbitration. |
| `serverUpdatedAt` | `Instant?` | INTEGER epoch milliseconds | Authoritative timestamp received from Firestore. Null means never synced. |

`LocalDate`, `LocalDateTime` and `TimeZone` MUST NOT appear in domain, data or sync code. They are permitted only in presentation formatting. D-81 adds one bounded infrastructure exception:
`earliestAllowedFuelEntryDate(vehicleCreatedAt: Instant): Instant` in `:core:common` imports
`TimeZone` only to pass the literal `TimeZone.UTC` to
`vehicleCreatedAt.minus(20, DateTimeUnit.YEAR, TimeZone.UTC)`, then clamps the result to the Unix
epoch. `LocalDate` and `LocalDateTime` remain forbidden in that helper and every other non-presentation
source. An architecture rule rejects any wider calendar-type use.

All time reads go through the injected `AppClock` (§20). Direct use of a system clock is FORBIDDEN outside `:wiring:*` and `:core:testing`.

### Money and decimals

`Float` and `Double` are FORBIDDEN for money, volume, price and consumption calculations, in every layer. Display-time conversion in SwiftUI or Compose is not arithmetic and is not covered by this ban; the ban applies to storage, transport and any computation that derives a stored or synchronized value.

| Concept | Kotlin representation | Scale | Persistence |
|---------|-----------------------|-------|-------------|
| `Money` | data class with `minorUnits: Long`, `currency: CurrencyCode` | ISO-4217 minor units | INTEGER + currency code |
| `FuelVolume` | scaled integer value class | 3 decimals, litres × 1000 | INTEGER |
| `PricePerLiter` | scaled integer value class | 3 decimals, currency units × 1000 | INTEGER |
| `ConsumptionL100Km` | scaled integer value class, `scaled: Long` | 2 decimals, L/100 km × 100 | Computed read model, never persisted |

Every scaled type carries its value in a `Long`. `Int` MUST NOT be used for a scaled quantity: the distance-weighted average sums `litersScaled` across every valid segment of a vehicle, and `10 * sum(litersScaled)` overflows `Int` well inside the per-vehicle entry ceiling of §12.

`Money` values of different `currency` MUST NOT be added, subtracted or compared. Any aggregation across currencies is a `ValidationError.InvalidUnit`.

### Canonical monetary arithmetic

Rounding mode is HALF_UP on non-negative inputs. The formulas below are **exact integer arithmetic** and MUST be implemented literally; a floating-point or naive integer-division implementation is a contract violation. Every intermediate expression is evaluated as `Long`; the Kotlin implementation MUST NOT narrow to `Int` at any step.

```text
minorUnitFactor = MinorUnits.factorFor(currency)      // EUR -> 100; validation rejects null before arithmetic

totalCostMinor      = (litersScaled * pricePerLiterScaled * minorUnitFactor + 500_000) / 1_000_000
pricePerLiterScaled = (totalCostMinor * 1_000_000 + (litersScaled * minorUnitFactor) / 2) / (litersScaled * minorUnitFactor)
litersScaled        = (totalCostMinor * 1_000_000 + (pricePerLiterScaled * minorUnitFactor) / 2) / (pricePerLiterScaled * minorUnitFactor)
```

Golden values that MUST be covered by tests in `:core:model`:

| `litersScaled` | `pricePerLiterScaled` | currency | `totalCostMinor` |
|----------------|-----------------------|----------|------------------|
| `45_123` (45.123 L) | `1_789` (1.789 €/L) | EUR | `8_073` (80.73 €) — exact value is 8072.5047, HALF_UP |
| `40_000` (40 L) | `1_500` (1.500 €/L) | EUR | `6_000` (60.00 €) |
| `1_000` (1 L) | `5` (0.005 €/L) | EUR | `1` (0.01 €) — exact value is 0.5, HALF_UP rounds up |
| `1` (0.001 L) | `1` (0.001 €/L) | EUR | `0` — exact value is 0.0001 minor units, HALF_UP rounds down |
| `500_000` (500 L) | `999_999` (999.999 €/L) | EUR | `49_999_950` (499,999.50 €) — intermediate product `49_999_950_000_000` MUST NOT overflow |

MVP currency constraint: `MinorUnits.factorFor` returns `100` only for the codes in `SUPPORTED_CURRENCY_CODES` (§20.0.1), and returns `null` for every other code. A locale suggesting a code outside that set falls back to `EUR`, and an explicit user selection outside that set returns `ValidationError.InvalidUnit`. Extending the table is a backlog story, not an agent decision.

Which value is derived is never ambiguous during validation: the caller states which two values it supplied through `MoneyInput` (§20), and the third is computed. Persistence stores the canonical triple only: `litersScaled`, `pricePerLiterScaled` and `totalCostMinor`. The original supplied pair is not retained locally, remotely or in the outbox payload. After validation succeeds, all three persisted values are authoritative read data; later reads MUST NOT try to infer which two fields the user originally typed.

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

A field holding a physical quantity MUST carry its unit or scale suffix (`Km`, `Scaled`, `Minor`). An unsuffixed numeric field name is a contract violation. These names are canonical at **every** layer: domain, SQLite column, Firestore field and JSON payload key.

### Domain model vs local row vs remote document

Domain models expose business concepts. Local rows add sync metadata. Remote documents contain only synchronized data plus remote metadata.

| Field | Domain | Local SQLite row | Firestore document | Notes |
|-------|--------|----------------|--------------------|-------|
| `id` | Yes | Yes | Document ID and field | Client-generated UUID v4. |
| `ownerId` | Yes | Yes | Yes | MUST equal the authenticated UID remotely. Stamped by the repository, never supplied by a command. |
| `createdAt` | Yes | Yes | Yes | Client-created UTC timestamp. |
| `updatedAt` | Yes | Yes | Yes | Local provisional in SQLite; server timestamp remotely. |
| `serverUpdatedAt` | No | Yes | No | Local sync metadata only. Stored as `INTEGER NULL`; `serverUpdatedAt IS NULL` is the legal "never synced" state. |
| `deletedAt` | Yes | Yes | Yes | Null when active. |
| `deleted` | No | Yes (stored) | Yes | Stored as `INTEGER NOT NULL CHECK(deleted IN (0, 1))`, with the invariant `deleted == (deletedAt != null)` enforced by a `CHECK` constraint. Written only by the tombstone helper in `:core:database`. |
| `syncState` | **No** | Yes | No | Local-only. MUST NOT be visible to feature `domain` or `presentation` code. |
| `localRevision` | No | Yes | No | Local-only. Incremented on every local edit. |
| `localMutationSeq` | No | Yes | No | Local-only. Monotonic database-local mutation order, used to adopt `LOCAL_OWNER` rows into the outbox deterministically. |
| `schemaVersion` | No | Yes | Yes | Starts at `1`. |

Sync status reaches the UI only through the aggregate `SyncStatus` exposed by `SyncController` (§9.6), never per entity.

Remote writes are full-document `set(..., merge = false)` operations. Partial remote updates are FORBIDDEN in the MVP sync engine.

### Vehicle

Canonical fields:

- `id`
- `ownerId`
- `name`
- `nameFold` — local only, generated from `canonicalVehicleName(name).lowercase()`, used for uniqueness checks (§5)
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

`fuelType` is metadata only in the MVP: it does not alter validation, units or consumption. The MVP enum is intentionally limited to combustion or fuel-like labels. `ELECTRIC` and `HYBRID` are not legal MVP values and MUST NOT appear in commands, rows, remote documents, UI state or tests except as rejected malformed input. Electric and hybrid vehicle support requires a future energy-model scope change covering kWh input, consumption units, validation, Firestore rules and migrations.

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

`odometerInconsistent` is a **derived** property cached in a column. Application code MUST NOT write it directly.

The `fuel_entry` table declares **no enforced foreign key** to `vehicle`: sync can legitimately deliver a fuel entry before its vehicle (§9.4).

### UserSettings

Canonical persistence is metric and **device-local**:

- Distances are stored in kilometres, volumes in litres.
- `distanceUnit = KM` and `volumeUnit = LITER` are fixed and read-only in the MVP.
- Unit settings affect presentation and input only, never domain storage.
- Settings live in a single-row local table `user_settings`. They are **not synchronized** in the MVP; there is no remote settings document, no `EntityType` for them and no outbox participation.
- `currency` is the default for *new* fuel entries only. Each fuel entry stores its own `currency`; changing the setting never rewrites existing entries.
- Settings do not survive destructive local-data flows in the MVP. Sign-out, anonymous "delete local data" and account deletion all delete the `user_settings` row. The next first-launch or repository access recreates it from locale defaults with `analyticsEnabled = false`.
- Settings synchronization through Google Play services, Android backup, iCloud or any other platform backup mechanism is out of MVP scope and requires a future ADR or story before any API, entitlement, manifest key or dependency is added.

### 3.1 Database-owned invariants

The following are maintained exclusively by `:core:database`, inside the caller's transaction, because both feature repositories and `:core:sync` write the same tables:

| Invariant | Trigger |
|-----------|---------|
| `vehicle.currentOdometerKm` recomputation | any insert / update / delete on `fuel_entry` |
| `fuel_entry.odometerInconsistent` recomputation for the exact recompute set below | any insert / update / delete on `fuel_entry` |
| `deleted == (deletedAt != null)` | `CHECK` constraint on both entity tables |

`currentOdometerKm` is recomputed for the whole vehicle after every fuel-entry create, update or tombstone write, in the same transaction.

`odometerInconsistent` is recomputed only for rows whose previous non-deleted chronological neighbour may have changed. The recompute set is de-duplicated by `id` and is:

For each active row in that set, D-82 defines the complete derivation as:

```text
odometerInconsistent =
  (previousActiveEntry != null && odometerKm <= previousActiveEntry.odometerKm) ||
  (vehicle != null && odometerKm < vehicle.initialOdometerKm)
```

The neighbour comparison is `<=`; the initial-odometer comparison is `<`. Because `fuel_entry`
has no enforced foreign key, a missing Vehicle makes only the second branch false: the neighbour
branch still applies, the row MUST NOT be flagged merely because it is orphaned, and recomputation
MUST neither throw nor produce `NULL`. The SQL expression retains an outer `COALESCE(..., 0)` as
the final orphan and missing-neighbour defense.

The exact recompute set remains:

- Create: inserted row in its new position, plus its new successor.
- Update where `vehicleId`, `date`, `createdAt`, `id` or `odometerKm` changes: updated row in its new position, plus its pre-update successor (the next non-deleted entry in chronological order before the update), plus its post-update successor (the next non-deleted entry in chronological order after the update). If pre- and post-successors coincide, the row is included once.
- Update that does not modify `vehicleId`, `date`, `createdAt`, `id` or `odometerKm`: no `odometerInconsistent` recompute is required, because chronological neighbours and comparison values are unchanged.
- Tombstone: the successor of the deleted row in its pre-delete chronological position. Subsequent tombstones or edits recompute independently.
- Vehicle-level fuel-entry cascade tombstone: the union of the successor of each tombstoned row in its pre-cascade chronological position, de-duplicated by `id`. A successor that is itself being tombstoned is not added.

All rows in the recompute set are recalculated in the same database transaction as the write that caused the recompute. A missing successor means no row is added for that position. The edited or deleted row's own stored `odometerInconsistent` remains meaningful only while the row is non-deleted; tombstoned rows are ignored by validation, projections and consumption.

The initial-odometer branch adds no recompute trigger: `vehicle.initialOdometerKm` is editable only
while the Vehicle has no non-deleted Fuel Entries, so no Fuel Entry flag can become stale through a
legal Vehicle write.

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

`observeFuelEntries` returns rows in chronological order, not calculation order. The repository's `observeConsumption` sorts its filtered input in calculation order.

Consumption (R-3) operates on non-deleted fuel entries of one vehicle in **calculation** order:

- `P` is the nearest preceding entry with `isFullTank = true` in calculation order.
- `E`, the segment end, MUST have `isFullTank = true`. Entries with `isFullTank = false` never close a segment and never produce a `SegmentResult`.
- Segment membership is `P.odometerKm < X.odometerKm <= E.odometerKm`.
- An entry whose `odometerKm` equals `P.odometerKm`, and which is not `P` itself, **is included** in the segment (its litres count) and additionally invalidates the segment with `DuplicateOdometerInSegment`.
- Partial fuel entries inside the segment membership range are included in the segment litres.
- Partial fuel entries before the first full-tank anchor never contribute to a valid segment.

The complete set of consumption explanation reasons is `ConsumptionInvalidReason` (§20). `EndEntryNotFullTank` is a list-projection reason for partial rows; it is not a `SegmentResult.Invalid` reason.

When more than one segment invalidation reason applies, precedence is
`NoPreviousFullTank > NonPositiveDistance > DuplicateOdometerInSegment >`
`MissedEntriesInSegment > InconsistentOdometerInSegment` (`D-78`).
`EndEntryNotFullTank` does not participate because `CalculateConsumption` never emits it.

## 5. Validation and Save Semantics

Write use cases normally validate commands before repository writes. Expected validation failures
return typed errors and do not throw.

The Vehicle and Fuel Entry write paths are the explicit D-76 and D-77 exceptions to that sequencing
rule and use a functional-core / imperative-shell split. Their validators own the pure
normalisation and validation rules and remain independently testable. The corresponding E1-03 and
E1-06 data implementations own the sequence: each loads the immutable pre-write facts declared in
§13, invokes the validator and applies the mutation inside one local transaction. Data
implementations do not own or duplicate the rules, validators perform no I/O, and
validation-shaped database queries are not added to the public repository contracts.

### Normalisation

Normalisation runs in the pure validator **before** its validation rules, and the normalised value
is handed to the imperative shell for persistence and used for uniqueness:

- All strings are `trim()`ed.
- Internal Unicode whitespace runs in `name` collapse to one U+0020 space.
- For nullable text fields (`brand`, `model`, `notes`), `""` becomes `null`.
- `name` MUST NOT be null or blank.

Vehicle-name normalisation is exactly:

```text
canonicalVehicleName(input) =
    input.trim()
         .replace(each non-empty run of Unicode whitespace with U+0020)
```

`nameFold = canonicalVehicleName(name).lowercase()` using Kotlin's locale-invariant Unicode lowercase operation. No NFC, NFD or platform-specific collation is applied in the MVP, because no approved KMP dependency provides cross-platform Unicode normalisation. Therefore composed and decomposed Unicode spellings that remain different after `lowercase()` are distinct names. Adding Unicode normalisation requires a decision update and a story that pins the library and migration behaviour.

### Warning protocol

Odometer inconsistency is a warning, not an automatic write:

1. A command without the confirmation returns `Err(ValidationWarning.OdometerInconsistent(previousOdometerKm, enteredOdometerKm))` and mutates nothing.
2. The UI asks for explicit confirmation.
3. The identical command re-issued with `Confirmation.OdometerInconsistent` in `confirmations` succeeds, and the entry is stored with `odometerInconsistent = true`.

A use case MUST NOT return `Ok` and a warning simultaneously. A warning MUST be idempotent.

Under D-77, `FuelEntryValidationContext.earliestAllowedDate` is the already-resolved lower date
bound for the target Vehicle and is never earlier than the Unix epoch. E1-04 validates against that
fact without introducing calendar types into domain code. E1-06 owns calculating the fact from the
Vehicle row before it validates and writes in one transaction. D-81 defines the fact as 20 literal
calendar years before `vehicle.createdAt` in UTC, clamped to the Unix epoch. The producer clamp and
the validator's independent `maxOf(UNIX_EPOCH, context.earliestAllowedDate)` defense are both
required.

### Validation constraints

Both ends of every interval are closed and MUST be enforced.

| Field | Rule |
|-------|------|
| Vehicle `name` | Trimmed length 1..40. Unique per owner among non-deleted vehicles, compared on `nameFold`. |
| `brand`, `model` | Null, or trimmed length 1..40. |
| `initialOdometerKm` | `CreateVehicleCommand.initialOdometerKm` is required and must be in `0..2_000_000`. `UpdateVehicleCommand.initialOdometerKm` is editable only while the vehicle has no non-deleted fuel entries; `null` means unchanged. A non-null in-range update after fuel entries exist returns `ValidationError.EditNotAllowed("initialOdometerKm")` and mutates nothing. `ValidationError.OutOfRange` is reserved for values outside the numeric interval. |
| `fuelType` | One of `GASOLINE`, `DIESEL`, `LPG`, `CNG`, `OTHER`. `ELECTRIC` and `HYBRID` are out of MVP scope. |
| Fuel entry `date` | Not before `1970-01-01T00:00:00Z`, not before `vehicle.createdAt - 20 years`, and not more than 1 hour after `AppClock.now()`. |
| `odometerKm` | 0..2_000_000; `>= vehicle.initialOdometerKm`; strictly greater than the previous non-deleted entry in chronological order unless confirmed inconsistent. |
| `litersScaled` | 1..500_000 (0.001 L .. 500 L). |
| `pricePerLiterScaled` | 1..999_999. |
| `totalCostMinor` | 1..99_999_999. |
| `currency` | One of `SUPPORTED_CURRENCY_CODES` (§20.0.1). |
| `notes` | Null, or trimmed length 1..280. |

Vehicle name uniqueness is a **local pre-write validation only**. There MUST NOT be a `UNIQUE` index on the backup-capable table for it: restored remote data can contain duplicate names from older clients, repair operations or future multi-device scopes, and a unique index would make the pull transaction fail and stall the cursor. SQLite `NOCASE` MUST NOT be used for folding, because it is ASCII-only.

Remote-applied rows bypass business validation entirely. A pull transaction MUST NOT fail because of a domain constraint or malformed remote payload. Documents that cannot be safely applied are quarantined (§9.5); the only legal pull-cycle failures are remote I/O, local persistence failure or failure to persist quarantine.

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
2. maps `kotlinx.serialization.SerializationException` to `PersistenceError.SerializationFailed`,
3. maps SQLDelight migration failures to `PersistenceError.MigrationFailed`,
4. maps `java.util.concurrent.TimeoutException` to `RemoteError.DeadlineExceeded`,
5. maps known SDK failures to typed errors,
6. maps the remainder to `UnexpectedError(origin, throwableClassName)`, **without** including message text that may contain user data (§17).

Domain and presentation code MUST NOT contain `try` / `catch`.

### Logging ownership

An error is logged exactly once, at the boundary that converts a platform failure into an `AppError`. `:integration:*` is the canonical log point for provider exceptions. `:core:database` logs local database failures only and MUST NOT log a wrapped exception that already originated in `:integration:*`. Higher layers propagate silently.

### RemoteError to SyncError mapping

This table is normative; it decides retry versus poison.

| `RemoteError` | `SyncError` | Effect |
|---------------|-------------|--------|
| `Unavailable`, `DeadlineExceeded` | `RetryableNetwork` | retry with backoff, `attemptCount++` |
| `Unauthenticated` | `AuthExpired` | retry after a valid auth session, `attemptCount` unchanged |
| `PermissionDenied` | `PermissionDenied` | **poison** — a rules rejection does not fix itself |
| `InvalidArgument` | `ValidationRejected` | **poison** |
| `NotFound` on push | — | treated as success; the local row is marked `SYNCED` with `serverUpdatedAt = null` |
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

`syncState` is a stored local control column on synchronized local rows. It is not persisted remotely and is not part of the domain model. The outbox influences `syncState`, but it does not fully define it: `ownerId == LOCAL_OWNER`, `syncState == PENDING` and no outbox row is a legal state before local-owner adoption.

Invariants:

- Local editors set `syncState = PENDING` in the same transaction as every create, update or tombstone write.
- The outbox writer is a no-op while `ownerId == LOCAL_OWNER`; it MUST NOT downgrade `syncState` back to `SYNCED`.
- For a real owner, a `PENDING`, `SYNCING`, `FAILED_RETRYABLE` or `FAILED_POISONED` synchronized row normally has an outbox row. The only legal exception is a transient state inside the same database transaction.
- A row in `SYNCING` MUST NOT be deleted by an editor, only by the sync engine.
- Pull writes from remote data set `syncState = SYNCED` only when no local outbox row exists for the entity.

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

While a row remains `SYNCING` after a local edit, its effective UI state is "pending sync after push": the user can keep editing, and the old in-flight remote payload is allowed to be stale. That transient resolves to `SYNCED` only after a later push of the new payload succeeds and its acknowledgement sees an unchanged `localRevision`.

`FAILED_RETRYABLE -> FAILED_POISONED` is governed by the qualified poison rule of §9.7. A connectivity-only failure never poisons.

## 8. Outbox Contract

Outbox payload format:

- JSON encoded with `kotlinx.serialization`.
- Includes `schemaVersion` and `entityType`.
- Includes the full entity snapshot under the canonical field names of §3.
- Encodes instants as epoch milliseconds UTC.
- Excludes `syncState`, `localRevision`, `localMutationSeq`, `serverUpdatedAt`, `nameFold`, `currentOdometerKm` and any other local-only metadata.

Outbox coalescing:

- At most one outbox row per `(entityType, entityId)`.
- `ON CONFLICT DO UPDATE` replaces `payload` and `localRevision`, and resets `attemptCount = 0`, `nextAttemptAt = 0`, `lastError = NULL`, `lastErrorCode = NULL`.
- The original `seq` MUST be preserved to keep causal order.

The coalesce is performed from the repository write path, inside the same transaction as the entity row write, using this statement shape:

```sql
INSERT INTO outbox (entityType, entityId, payload, localRevision)
VALUES (?, ?, ?, ?)
ON CONFLICT(entityType, entityId) DO UPDATE SET
  payload = excluded.payload,
  localRevision = excluded.localRevision,
  attemptCount = 0,
  nextAttemptAt = 0,
  lastError = NULL,
  lastErrorCode = NULL
```

**The outbox MUST NOT be populated while `ownerId == LOCAL_OWNER`.** Before a real UID exists, local writes set `syncState = PENDING` but the outbox writer is a no-op. Outbox rows are created for those entities by local-owner adoption (§11.4).

Every local create, update and tombstone write assigns a new `localMutationSeq` from one database-local monotonic counter shared by `vehicle` and `fuel_entry`. Pull-applied remote writes do not consume this counter. `localMutationSeq` is not a history table: it stores only the latest local mutation order for the row, which is enough because the outbox stores the latest snapshot per entity.

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

`SyncController` is a singleton in the app graph holding a single `Mutex` and a single `Boolean` pending flag. All triggers call `requestSync(reason)`. Calling `requestSync` while a cycle is in progress sets the pending flag and returns immediately; when the cycle completes, the flag is checked and, if set, cleared and followed by another cycle. Android platform triggers MUST use `enqueueUniqueWork(SYNC_WORK, KEEP)` and MUST route through the same in-process `SyncController`; iOS uses a single `BGTaskScheduler` identifier. Cross-process sync is not supported and MUST NOT be introduced.

### 9.2 Cycle admission and order

A cycle MUST NOT start while `ConnectivityObserver.isOnline` is `false`. It ends immediately as a no-op, leaving `attemptCount`, `nextAttemptAt`, `lastError` and `lastErrorCode` untouched, and the pending rows keep their existing state. This is the primary defence against burning the retry budget offline; the qualified poison rule of §9.7 is the second, for connectivity lost mid-cycle.

Once admitted, the order is deterministic, because the backup and recovery simulation depends on it:

- If the local database contains no synchronized rows for the owner (that is, `vehicle` and `fuel_entry` are both empty for that owner) **and** the outbox is empty: pull, then push.
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
- Query ordering is `updatedAt ASC, documentId ASC`.
- The 30-second overlap window is applied **once per cycle**, not per page. At cycle start, compute `overlapSince = max(epoch, cursor.lastServerUpdatedAt - 30 s)`.
- The first page of every cycle MUST use `startAt(overlapSince)`, including cycles that resume after the first pull. Firebase document-ID cursors reject an empty string, so the first boundary deliberately carries only the timestamp and therefore includes every document at that timestamp. Later pages MUST use both concrete cursor components with `startAfter(lastServerUpdatedAt, lastDocumentId)`. `null` MUST NOT be used as a cursor component passed to `startAt`/`startAfter`; the `RemoteCursor.INITIAL` sentinel is exempt because it is materialised as the timestamp-only first-page boundary before reaching Firestore (`§20.7`, D-50).
- Subsequent pages in the same cycle MUST use `startAfter(pageCursor.lastServerUpdatedAt, pageCursor.lastDocumentId)`, where `pageCursor` is the last real document returned by the previous non-empty page.
- A first-page query that omits `startAt(overlapSince)`, or a later-page query that omits either concrete cursor component, is a contract violation. The complete later-page cursor prevents re-reading the same page forever whenever a timestamp cluster exceeds the page size.
- Tombstones are included.
- Each page is applied in one local transaction; apply is idempotent.
- If an outbox row exists for a remote entity, local data is not overwritten.
- Otherwise the remote snapshot is applied iff `local.serverUpdatedAt == null || remote.updatedAt > local.serverUpdatedAt`. The comparison is made on epoch milliseconds as `Long`; Firestore `Timestamp` conversion happens in `:integration:firebase-firestore` at the boundary. **`local.updatedAt` MUST NOT participate in remote conflict arbitration.**
- The cursor advances only after the local transaction succeeds.
- Progress invariant: after a non-empty page, the resulting page cursor MUST be strictly greater than the cursor anchor that produced that page. If not, the engine MUST fail the cycle with `SyncError.ConflictUnresolved` rather than loop.
- Orphan fuel entries (vehicle not yet pulled) are legal transient state. They MUST be persisted, and MUST be excluded from all UI queries and from consumption until their vehicle arrives.

### 9.5 Quarantine and malformed remote payloads

A pulled document that cannot be safely applied MUST be stored verbatim in a `quarantine` table keyed by `(entityType, id)`, MUST NOT be applied to the entity table, and MUST NOT block cursor advance once the quarantine row is committed.

Quarantine reasons are:

- `UnsupportedSchemaVersion`: `schemaVersion > CLIENT_MAX_SCHEMA_VERSION`.
- `MalformedPayload`: `schemaVersion <= CLIENT_MAX_SCHEMA_VERSION`, but the document is missing a required field, has an unknown enum value, violates nullability, has a primitive type mismatch, has an out-of-range value, violates `deleted == (deletedAt != null)`, has a document ID / payload ID mismatch, contains a malformed JSON payload, or cannot be deserialized into the supported DTO.

Quarantine rows store `entityType`, `entityId`, `reason`, `schemaVersion`, `serverUpdatedAt`, raw payload JSON and `createdAt`. They MUST NOT store provider credentials, auth tokens or unredacted SDK error objects.

For both reasons, cursor advance is allowed only after the quarantine row is written in the same local transaction that processes the page. If quarantine persistence fails, the pull cycle fails and the cursor does not advance. A quarantined document is logged once with redacted fields and no raw payload. Quarantined rows are re-evaluated on app upgrade and may also be re-evaluated by an explicit repair story. During the MVP, mobile-client Firestore rules accept exactly `schemaVersion == CLIENT_MAX_SCHEMA_VERSION == 1` (`D-49`). Unsupported higher versions remain a defensive quarantine case for a future reviewed schema rollout or an Admin path; that rollout MUST decide client, rule and deployment sequencing before changing either value.

### 9.6 Conflict resolution

Last-write-wins at whole-document level using the server `updatedAt`. Tombstones are regular documents and win over older updates.

`(updatedAt, documentId)` is the **total ordering of the pull stream**, used for cursor progress and page boundaries. It is NOT a tie-breaker for arbitrating a single document: both sides of a document conflict share the same id.

Accepted limitation: concurrent active editing on multiple devices is not a supported MVP workflow. If the same account writes from multiple devices before one device is treated as the recovery target, last-write-wins can lose one whole-document update.

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

A row failing only for connectivity reasons therefore stays `FAILED_RETRYABLE` indefinitely, with `attemptCount` pinned at the ceiling and the backoff at its 15-minute cap, and resumes as soon as the network returns. On `ConnectivityRecovered`, `SyncController.requestSync(ConnectivityRecovered)` sets `nextAttemptAt = now` for every `FAILED_RETRYABLE` row whose `lastErrorCode` is in `CONNECTIVITY_ERROR_CODES`; `attemptCount` is preserved so later failures keep the correct backoff. Without this qualification the constants above poison every pending row after roughly 17 minutes offline — the sum of the backoff series up to attempt 10 — which would violate `docs/SPECIFICATION.md §2` P2 and strand the user's data behind a manual per-entity repair.

Manual retry through `SyncController.retryFailed()` resets every `FAILED_RETRYABLE` and `FAILED_POISONED` row to `PENDING`, sets `nextAttemptAt = now`, **resets `attemptCount` to 0** and clears `lastError` and `lastErrorCode`. Connectivity-only failures already auto-resume, so this method is for user-initiated recovery from permanent failures. Preserving the count would make manual retry useless on exactly the rows that need it, because the count is already at the ceiling.

### 9.8 Trigger constants

| Trigger | Value |
|---------|-------|
| Post-write debounce | 2 s |
| Minimum interval between automatic cycles | 30 s |
| Periodic background interval | 6 h (Android `WorkManager` periodic; iOS `BGAppRefresh`, best effort) |
| Foreground | on cold start, and on resume after more than `FOREGROUND_RESUME_THRESHOLD_MS` in background |
| Pull-to-refresh | bypasses the minimum interval, never the mutex |

All five are `SyncTrigger` values passed to `requestSync(reason)` and logged with the cycle id.

### 9.9 Aggregate status

`SyncController.status: StateFlow<SyncStatus>` with precedence `Failed > Syncing > Pending > Idle`.

Being offline with pending rows renders as `Pending`, never as an error. This is a rule about aggregation, not only about admission: a row in `FAILED_RETRYABLE` whose `lastErrorCode` is a connectivity code (§9.7) counts towards `Pending`, never towards `Failed`. Otherwise a single failure as the network dropped mid-cycle would show the user an error for a condition that is not one.

The precedence function for `Failed` MUST count only rows whose `lastErrorCode` is not in `CONNECTIVITY_ERROR_CODES`.

## 10. RemoteSyncSource Contract

`RemoteSyncSource` is implemented only by integration modules. The MVP implementation is Firebase-backed. It represents a backup and recovery replica only: the SQLDelight local database remains the source of truth for product behaviour and UI reads, the MVP supports one active device per account, and the remote database exists solely so a user can retrieve backed-up data on a new device. Future simultaneous multi-device use requires a separate story or ADR that moves the source of truth from the local database to the remote database. Future API-backed implementations may use Ktor, but Ktor is not an MVP dependency until such an implementation is explicitly approved by ADR.

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
- `pullChanges` returns `nextCursor = inputCursor` and `hasMore = false` exactly when `items` is empty. Otherwise `nextCursor.lastServerUpdatedAt` and `nextCursor.lastDocumentId` MUST equal the last item in `items`.
- Token refresh is the responsibility of this module: on `Unauthenticated` it MUST force a token refresh through the provider SDK and retry the operation once before mapping to `RemoteError.Unauthenticated`.
- `:core:sync` does not depend on `:core:auth`. Token handling lives entirely in `RemoteSyncSource` (the integration layer); the sync engine never calls `AuthClient` or `TokenProvider`. The `AuthExpired` state-machine transition (`§7`) is sync-internal and signals that `RemoteSyncSource` could not authenticate; re-authentication is delegated to the session/presentation layer, not to `:core:sync`.
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

`deleteAccount()` is the client entry point for the `D-23` server/Admin account deletion operation. It MUST NOT call the mobile Firebase Auth account deletion API directly. It maps server operation failures to `AuthError.AccountDeletionRemoteFailed`, authentication freshness failures to `AuthError.RequiresRecentLogin`, caller mismatch or IAM rejection to `AuthError.PermissionDenied`, and connectivity failures to `AuthError.NetworkUnavailable`.

### 11.2 First launch

On first launch, when the user selects the "Continue without account" path, the app MUST attempt Firebase anonymous authentication. If anonymous authentication succeeds, the app uses that Firebase UID immediately and normal outbox synchronization applies.

First launch MUST also succeed offline. If anonymous authentication cannot complete because connectivity or Firebase Auth is unavailable, the app creates a temporary local session with `ownerId = LOCAL_OWNER`, all MVP features work, and the outbox stays empty (§8). Anonymous UID acquisition is retried in the background when connectivity returns; on success, local-owner adoption runs (§11.4).

An unlinked Firebase anonymous identity is device-bound. It can be resumed only while the same
device retains its Firebase Auth session; the app MUST NOT expose a cross-device recovery promise
for it. Linking Google or Apple while preserving the UID converts it into a portable permanent
identity.

Authentication with Identity Platform native automatic cleanup MUST be enabled in every Firebase
project. Firebase owns its fixed 30-day eligibility threshold. Unlinked anonymous accounts are
eligible; accounts linked to any permanent provider are excluded. The provider's user-creation
timestamp is the canonical origin for that elapsed time and for the reminder schedule below.

### 11.3 Anonymous conversion

Normal anonymous conversion MUST preserve the UID through credential linking. If linking succeeds,
all existing data remains owned by that UID. A provider response that would change the UID without
a credential collision aborts conversion and returns `AuthError.UidWouldChange`.

`AuthError.CredentialAlreadyInUse` starts a separate collision flow. Cancellation leaves the
anonymous session and all local and remote data untouched. Continuing requires
`Confirmation.AdoptExistingAccount` after the UI states that the existing permanent-account data
will be replaced by the current anonymous-session snapshot. The MVP never merges both data sets.

After confirmation, the operation is ordered as follows:

1. Persist a complete, durable local snapshot of the current anonymous owner's synchronized
   `Vehicle` and `FuelEntry` rows, including tombstones, and capture a fresh Firebase ID token for
   that anonymous UID.
2. Sign into the permanent account that owns the colliding provider credential.
3. Replace that permanent account's remote `vehicles` and `fuelEntries` data with the captured
   snapshot. Replacement is idempotent and resumable; an interrupted attempt resumes from the
   durable snapshot rather than pulling and overwriting it.
4. Rebuild the permanent owner's local rows from the same snapshot, preserving a recoverable
   operation marker until both the remote replacement and step 5 succeed.
5. Call `deleteOrphanedAnonymousAccount` with the captured anonymous token. The backend verifies
   the token, verifies that it represents the abandoned anonymous UID rather than the current
   permanent UID, deletes that anonymous Firebase Auth account and directly invokes the D-63
   user-data deletion service for the anonymous UID.

Retry after any interruption MUST converge on the same permanent-account snapshot and the same
deleted anonymous identity. It MUST NOT re-enter normal recovery pull while the replacement marker
exists. Exact backend idempotency and deletion semantics are in §11.5.

#### Anonymous sign-in benefit reminders

The reminder schedule is the fixed list of elapsed-day thresholds `[1, 3, 8, 18]`, held in one
configuration constant and anchored to the Firebase anonymous user-creation timestamp. Evaluation
runs on app launch and foreground return. It is disabled for `LOCAL_OWNER`, signed-out and
permanently authenticated sessions.

The device persists the last-shown zero-based reminder index. When several unseen thresholds are
due, exactly the highest due index is shown and that index is persisted, consuming every lower
pending reminder. A reminder is dismissible and never blocks an existing feature. Index 3
completes the schedule. Successful permanent-provider linking or sign-in clears the pending
anonymous reminder state.

The boundary cases are normative: no reminder at 12 hours; reminder 0 on day 1; no new reminder on
day 2; reminder 1 on day 4; reminder 2 on day 9; only reminder 3 on day 20 even when no earlier
reminder was shown; and no reminder on day 31 after reminder 3 has been consumed. The notice copy
MUST explain the benefit of permanent sign-in and the device-bound, 30-day cleanup risk. These are
foreground authentication-retention notices, not operating-system notifications.

### 11.4 Local owner adoption

On the first successful authentication after a `LOCAL_OWNER` period, in one transaction:

1. Rewrite every row with `ownerId = LOCAL_OWNER` to the new UID.
2. Increment `localRevision` on each rewritten row.
3. Reset every non-`SYNCED` synchronized row to `syncState = PENDING`, clear `lastError` / `lastErrorCode` context, and enqueue an outbox snapshot ordered by the push dependency order of §8 and then by `localMutationSeq ASC, id ASC`. The inserted outbox rows receive `seq` in that order.

Adoption MUST preserve each row's existing `localMutationSeq`; the adoption rewrite itself does not consume a new mutation sequence. Outbox `seq` values assigned during adoption are sequential and strictly greater than any pre-existing outbox `seq`; the first inserted adoption row receives `(max pre-existing seq) + 1`. The adoption transaction holds the database write lock for the whole operation, and no concurrent sync writes are possible because the sync engine does not run while the owner is `LOCAL_OWNER`. `SYNCING` rows are therefore impossible under `LOCAL_OWNER`; the reset rule covers the legal `PENDING`, `FAILED_RETRYABLE` and `FAILED_POISONED` states. The operation MUST be idempotent and MUST be covered by a test that starts from a populated local-owner database. Implemented by story `E2-06`.

### 11.5 Sign-out and account deletion

Sign-out is offered only to a permanently authenticated user. For an anonymous session the action is labelled "delete local data" and requires the same two-step destructive confirmation as account deletion. Signing out clears all local rows for that owner, including `SYNCED` ones, and deletes the device-local `user_settings` row; recovery is by re-authenticating and pulling from `RemoteCursor.INITIAL`, while settings are recreated from defaults.

Anonymous "delete local data" clears every local table, including `user_settings`, `outbox`, `sync_cursor` and `quarantine`.

Account deletion order is normative:

1. Before calling the server operation, the app MUST verify the Firebase ID token is fresh, meaning `AppClock.now() - issuedAt <= FRESH_LOGIN_THRESHOLD_MS` (using the `issuedAt` field of `AuthToken`, §20.8); otherwise it MUST trigger a fresh re-authentication UI flow and re-submit step 2.
2. Call the Firebase Admin server account deletion operation selected by `D-23`, authenticated with the current Firebase user.
3. The server operation verifies that the authenticated caller UID equals the target UID, deletes remote documents under `users/{uid}` in this order: `fuelEntries`, then `vehicles`, using Admin privileges outside client Firestore rules.
4. Only after remote document deletion fully succeeds, the server operation deletes the Firebase Auth user for the same UID.
5. Only after the server operation returns success, the app clears local data, including `user_settings`.

The order `fuelEntries`, then `vehicles` is normative. Reversing it would leave a brief window during which a fuel entry exists without its vehicle, which is recoverable but adds an unnecessary transient state.

The server operation MUST be idempotent for already-deleted documents and MUST NOT delete any document outside `users/{uid}`. It MAY page internally, but partial progress is not reported as success. If step 2, 3 or 4 fails, the app flow aborts with a typed `AuthError`, preserves local data, and does not perform client-side hard deletes. Deleting the auth account before the data would leave unreachable orphan documents.

If the local database has pending outbox rows at the time of account deletion, those rows are dropped together with all local data after the server operation succeeds. The server operation is the authoritative purge; the client's outbox state is discarded. Rows typed shortly before deletion may be lost, which is the expected trade-off of the destructive operation.

The D-63 user-data deletion service is the single reusable implementation that removes application
data for a UID. Its explicit registry MUST contain every location declared by the remote data
schema. The registry is currently exactly:

```text
Firestore collection: users/{uid}/fuelEntries/{entryId}
Firestore collection: users/{uid}/vehicles/{vehicleId}
Cloud Storage prefixes: []
```

The deletion order remains `fuelEntries`, then `vehicles`. Cloud Storage is not used by the MVP,
but the empty prefix list is an executable registry entry rather than an undocumented convention.
A server-side contract test compares the registry with the declared remote data schema and fails
when either gains a location that the other omits.

Two anonymous-deletion entry points reuse this service:

- `onAnonymousUserDeleted` is the sole permitted Cloud Functions 1st gen function. The application
  relies on it only for Firebase's native automatic anonymous-account cleanup path, and it invokes
  the service for an eligible deleted anonymous UID. Delivery caused by another anonymous deletion
  is treated as harmless idempotent overlap, never as the primary guarantee for that path.
- `deleteOrphanedAnonymousAccount` is a Cloud Functions 2nd gen callable used by the confirmed
  account-linking collision flow. It verifies the captured anonymous ID token and caller context,
  deletes the orphaned anonymous Auth account through the Admin SDK, and invokes the deletion
  service directly after that deletion. It MUST NOT rely on `onAnonymousUserDeleted` being
  delivered.

Both paths are idempotent. Trigger/callable overlap is expected and harmless. An integration test
MUST suppress or disregard trigger delivery for the Admin SDK path and still prove that
`users/{uid}` is removed. The temporary 1st gen exception and its migration surface are tracked as
`TD-01` in `docs/TECHNICAL_PLAN.md §13`; no other 1st gen function is permitted.

### 11.6 App Graph Contract

`:shared` exposes provider-free graph construction through an explicit port and retains the
complete dependency container used internally by graph construction:

```kotlin
data class AppGraphDependencies(
    val databaseFactory: DatabaseFactory,
    val authClient: AuthClient,
    val tokenProvider: TokenProvider,
    val ownerContext: OwnerContext,
    val remoteSyncSource: RemoteSyncSource,
    val analyticsTracker: AnalyticsTracker,
    val crashReporter: CrashReporter,
    val clock: AppClock,
    val dispatchers: DispatcherProvider,
    val uuidGenerator: UuidGenerator,
    val logger: Logger,
    val isDebugBuild: Boolean,
    val localeProvider: LocaleProvider,
    val connectivityObserver: ConnectivityObserver,
    val syncTriggerAdapter: SyncTriggerAdapter,
)

interface AppProviders {
    val databaseFactory: DatabaseFactory
    val authClient: AuthClient
    val tokenProvider: TokenProvider
    val ownerContext: OwnerContext
    val remoteSyncSource: RemoteSyncSource
    val analyticsTracker: AnalyticsTracker
    val crashReporter: CrashReporter
    val clock: AppClock
    val dispatchers: DispatcherProvider
    val uuidGenerator: UuidGenerator
    val logger: Logger
    val localeProvider: LocaleProvider
    val connectivityObserver: ConnectivityObserver
    val syncTriggerAdapter: SyncTriggerAdapter
}

fun buildAppGraph(isDebugBuild: Boolean, providers: AppProviders): AppGraph
```

`CrashReporter` is a required graph dependency from Phase 0 so graph construction never changes shape when release hardening begins. `:core:crash` owns the abstraction and the no-op implementation used by tests and local builds. Firebase Crashlytics is introduced only in Phase 4, in `:integration:firebase-crashlytics`, and MUST be bound through `:wiring:firebase` before release builds point at Firebase.

`:core:crash` exposes the `CrashReporter` interface and a default no-op implementation in `commonMain`. It MUST NOT contain `expect`/`actual` declarations. Platform crash-reporting implementations live in `:integration:*` and are aggregated by `:wiring:firebase`.

`AppProviders`, `AppGraphDependencies` and `buildAppGraph(isDebugBuild, providers)` are
construction APIs for Kotlin callers. They are public to Kotlin/JVM and Kotlin/Native source that
performs platform composition, but they are NOT part of the Swift-facing ABI. They MUST be hidden
from the generated Objective-C header through Kotlin/Native interop controls such as
`@HiddenFromObjC` or an equivalent explicit export exclusion. Swift code consumes only the facade
declared in §20.10.

Rules:

- `DatabaseFactory` is imported from `:core:database` (`§20.3.2`), not `:core:common`; `:core:common` is forbidden from depending on SQLDelight or SQLite. `:core:database` is a `:core:*` module, so `:shared` may depend on it.
- Koin may construct `AppProviders` implementations only in wiring and platform composition.
- It MUST contain abstractions only. Firebase, GitLive, Koin, Ktor, Android and iOS concrete types MUST NOT appear in it.
- Its parameter order is canonical and MUST match the code block above exactly: `databaseFactory, authClient, tokenProvider, ownerContext, remoteSyncSource, analyticsTracker, crashReporter, clock, dispatchers, uuidGenerator, logger, isDebugBuild, localeProvider, connectivityObserver, syncTriggerAdapter`.
- Tests provide fakes without starting Koin through the `:shared:testing` factory
  `testAppGraphDependencies(...)`, whose implementation reuses the generic fakes from
  `:core:testing`. Every parameter is defaulted. Adding a member REQUIRES updating that factory in
  the same change, preserving the same parameter order. Consumer modules MUST depend on
  `:shared:testing` only from `commonTest` (`D-56`).
- `AppProviders` has exactly the same members and order as `AppGraphDependencies` after removing
  `isDebugBuild`. `buildAppGraph` supplies that flag and maps every other member without replacing
  or decorating it (`D-59`).
- The Kotlin-facing `AppGraph` (§20.10) exposes state-holder factories, `SyncController` and `close()` — never repositories, use cases or DAOs.
- The Swift-facing `SwiftAppGraph` (§20.10) exposes state-holder factories without `CoroutineScope`, a sync state holder instead of `SyncController`, and `close()`.
- Each `AppGraph` owns exactly one `DatabaseHandle` created by its `DatabaseFactory` and releases it
  idempotently from `close()`. `SwiftAppGraph.close()` closes its wrapped graph after its cached
  holders, so the same handle is released transitively (`D-89`).
- `:integration:firebase-*` modules MAY declare Koin `Module` declarations for their own provider
  bindings. `:wiring:firebase` is the only module that constructs Firebase implementations;
  integration modules MUST NOT reference `buildAppGraph`.
- `:composition:ios` produces the single framework named `Shared`, declares
  `api(project(":shared"))`, exports `:shared` plus the `:feature:vehicle`, `:feature:fuel` and
  `:core:common` declarations moved by D-85 and D-97, depends on `:wiring:firebase` with
  `implementation`, and owns the
  only `createSwiftAppGraph(isDebugBuild)` declaration. It contains no product logic, wraps the
  `AppGraph` returned by `buildAppGraph` in `SwiftAppGraph`, and never exports an integration
  module (`D-58`, `D-86`).

## 12. Repository Contracts

Repositories are interfaces owned by feature domain packages. Implementations live in feature data packages.

All write methods:

- Run in local database transactions where multiple rows or outbox entries change.
- Stamp `ownerId` from `OwnerContext`, plus `id`, `createdAt`, `updatedAt`, `localRevision` and `localMutationSeq`. Commands never carry these.
- Enqueue outbox snapshots for synchronized entities, subject to §8.
- Return `Outcome<..., AppError>`.
- Never call Firebase directly.

Repositories obtain the current owner through `OwnerContext` from `:core:common`. Feature `data` MUST NOT depend on `:core:auth`.

### VehicleRepository

```kotlin
interface VehicleRepository {
    fun observeVehicles(includeDeleted: Boolean): Flow<Outcome<List<Vehicle>, AppError>>
    fun observeVehicle(id: EntityId): Flow<Outcome<Vehicle?, AppError>>
    fun observeVehicleEditFacts(id: EntityId): Flow<Outcome<VehicleEditFacts?, AppError>>
    suspend fun createVehicle(command: CreateVehicleCommand): Outcome<EntityId, AppError>
    suspend fun updateVehicle(command: UpdateVehicleCommand): Outcome<Unit, AppError>
    suspend fun deleteVehicle(id: EntityId): Outcome<Unit, AppError>
}
```

| Method | Side effects | Errors it may return |
|--------|--------------|----------------------|
| `observeVehicles` | none | `PersistenceError` |
| `observeVehicle` | none; absence is `Ok(null)` | `PersistenceError` |
| `observeVehicleEditFacts` | none; observes the Vehicle and active Fuel Entry count; absence is `Ok(null)` | `PersistenceError` |
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
| `updateFuelEntry` | as create, plus recomputation of the §3.1 recompute set | as above plus `EntityNotFound`, `EntityDeleted` |
| `deleteFuelEntry` | tombstones one row, recomputes read models, enqueues a tombstone snapshot | `EntityNotFound`, `PersistenceError` |
| `observeConsumption` | none | `PersistenceError` |

The MVP loads at most `MAX_ENTRIES_IN_MEMORY = 5_000` entries per vehicle. D-83 keeps the highest
5,000 rows under each projection's complete canonical ordering: the chronologically newest rows
for the list and the highest-odometer calculation rows for consumption. Each query selects that
window in descending order under the limit and returns it in the ascending §4 ordering. The
production repository always binds the named constant; tests MAY inject a smaller positive limit
to prove the same window semantics without materialising 5,000 rows. Consumption is computed from
a dedicated projection query, not from the UI list.

`FuelEntryRepository.observeConsumption` returns the full `ConsumptionReport` and is Kotlin-only. It is never observed directly from the Swift facade. Swift-facing fuel-entry list state projects it onto `FuelEntryListUiState.consumptionAverageScaled`, `validConsumptionSegmentCount`, `isConsumptionReliable` and per-row `FuelEntryListItemUi.consumptionScaled` / `invalidReason`.

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
- Does not depend on platform APIs, and does not access SQLDelight, SQLite, Firebase, GitLive, Koin, Ktor, Android or iOS APIs directly.
- MAY use `kotlinx.coroutines.Flow`, `StateFlow` and `suspend` signatures. MUST NOT call `kotlinx.coroutines.GlobalScope`, `kotlinx.coroutines.runBlocking` or any `Dispatchers.*` static; execution context comes from the caller or injected abstractions.

Vehicle command validation lives in the `:feature:vehicle` domain package:

```kotlin
fun canonicalVehicleName(input: String): String

data class VehicleNameCandidate(
    val id: EntityId,
    val name: String,
)

data class CreateVehicleValidationContext(
    val activeVehicles: List<VehicleNameCandidate>,
)

data class UpdateVehicleValidationContext(
    val activeVehicles: List<VehicleNameCandidate>,
    val hasNonDeletedFuelEntries: Boolean,
)

class ValidateCreateVehicle {
    operator fun invoke(
        command: CreateVehicleCommand,
        context: CreateVehicleValidationContext,
    ): Outcome<CreateVehicleCommand, AppError>
}

class ValidateUpdateVehicle {
    operator fun invoke(
        command: UpdateVehicleCommand,
        context: UpdateVehicleValidationContext,
    ): Outcome<UpdateVehicleCommand, AppError>
}
```

Both validators form the functional core and perform no repository call. `activeVehicles`
contains only non-deleted vehicles for the current owner. Update validation excludes the candidate
whose `id` equals `command.id`. E1-03 is the imperative shell: it loads these facts, invokes the
validator and writes inside the same local transaction.

The successful value is the fully normalised command. Validation order is required name, string
lengths, numeric odometer range, odometer edit restriction, then folded-name uniqueness. When an
update value is both outside the range and locked by existing fuel entries, `OutOfRange` wins. The
exact declared errors are:

| Use case | Errors |
|----------|--------|
| `ValidateCreateVehicle` | `ValidationError.RequiredField(name)`, `InvalidLength(name, brand, model)`, `OutOfRange(initialOdometerKm)`, `DuplicateName` |
| `ValidateUpdateVehicle` | `ValidationError.RequiredField(name)`, `InvalidLength(name, brand, model)`, `OutOfRange(initialOdometerKm)`, `EditNotAllowed(initialOdometerKm)`, `DuplicateName`. `EditNotAllowed` applies only to a non-null in-range value when `hasNonDeletedFuelEntries` is true. |

No other `AppError` leaf is returned by these pure validators. Entity absence, tombstones and
persistence failures remain repository outcomes (§12).

Fuel Entry command validation uses the D-77 functional core:

```kotlin
data class FuelEntryValidationContext(
    val now: Instant,
    val earliestAllowedDate: Instant,
    val vehicleInitialOdometerKm: Long,
    val previousOdometerKm: Long?,
)

data class ValidatedFuelEntryValues(
    val vehicleId: EntityId,
    val date: Instant,
    val odometerKm: Long,
    val litersScaled: Long,
    val pricePerLiterScaled: Long,
    val totalCostMinor: Long,
    val currency: CurrencyCode,
    val isFullTank: Boolean,
    val hasMissedEntries: Boolean,
    val notes: String?,
)

class ValidateCreateFuelEntry {
    operator fun invoke(
        command: CreateFuelEntryCommand,
        context: FuelEntryValidationContext,
    ): Outcome<ValidatedFuelEntryValues, AppError>
}

class ValidateUpdateFuelEntry {
    operator fun invoke(
        command: UpdateFuelEntryCommand,
        context: FuelEntryValidationContext,
    ): Outcome<ValidatedFuelEntryValues, AppError>
}
```

Both validators are pure and perform no repository call. `earliestAllowedDate` is the resolved
lower bound for the target Vehicle and MUST be at or after the Unix epoch. `previousOdometerKm` is
the odometer of the previous non-deleted row in the command's target chronological position, or
null when no predecessor exists. For update, E1-06 prepares the context after excluding the target
row. E1-06 loads these facts, validates and writes in one local transaction.

Validation normalises `notes` with the §5 nullable-text rule and returns the fully normalised,
canonical persistence values. It checks currency support before monetary arithmetic, validates
the two supplied `MoneyInput` values before deriving the third, then validates the complete triple.
Numeric violations return `ValidationError.OutOfRange` with the canonical field name and §5
bounds. An unsupported explicit currency returns `ValidationError.InvalidUnit(currency.value)`;
a date beyond `context.now + 1 hour` returns `ValidationError.FutureDate`; and an invalid note
returns `ValidationError.InvalidLength("notes", 1, 280)`.

Hard validation precedes the R-1 warning. An odometer below `vehicleInitialOdometerKm` uses that
initial value as the warning reference. Otherwise, an odometer less than or equal to a non-null
`previousOdometerKm` uses the previous value. `Confirmation.OdometerInconsistent` suppresses only
that warning; it never suppresses a hard range, date, money, currency or note error.

`ValidatedFuelEntryValues` contains no target entry ID, owner, timestamps, synchronization
metadata, `odometerInconsistent` value or supplied-pair marker. E1-06 takes the update target ID
from `UpdateFuelEntryCommand`; `:core:database` remains the sole writer of the derived
`odometerInconsistent` column.

Consumption calculation:

```kotlin
fun interface CalculateConsumption {
    operator fun invoke(entries: List<FuelEntry>): ConsumptionReport
}
```

Contract: `entries` MUST contain only non-deleted entries for one vehicle, in any order; the implementation sorts them in calculation order (§4). The repository's `observeConsumption` filters to a single vehicle and excludes deleted entries before invoking `CalculateConsumption`; the use case itself does not filter. The `vehicleId` is implicit in `entries`, and `initialOdometerKm` is not used because the algorithm computes segment windows from full-tank entries. The function is **pure and total** — it never throws and returns no error type. Invalid segments are represented as `SegmentResult.Invalid(reason)`.

`CalculateConsumption` creates one `SegmentResult` for each entry with `isFullTank = true`, in calculation order. It MUST NOT create a segment for partial entries and MUST NOT produce `SegmentResult.Invalid(EndEntryNotFullTank)`. Partial entries inside a full-to-full segment still contribute their litres to that segment.

## 14. Presentation State Contract

Shared state holders:

- Live in feature `presentation` packages. `SyncStateHolder` is the app-level exception and lives
  in `:shared`.
- Expose immutable `StateFlow<UiState>` built with `stateIn(scope = scope + dispatchers.main, started = SharingStarted.WhileSubscribed(STATE_HOLDER_TIMEOUT_MS), initialValue = initialValue)`.
- Accept intent functions.
- Kotlin-facing factories take a `scope: CoroutineScope` parameter and the caller owns that scope. Android passes `viewModelScope`.
- Swift-facing factories take no scope parameter. `SwiftAppGraph` creates and owns one child scope per state holder.
- Each pattern exposes `close()`, which cancels work owned by that state holder. Swift graph `close()` cancels every cached state holder it created.
- Emit every `UiState` on `dispatchers.main`; database flows are collected on `dispatchers.io`, computation work uses `dispatchers.default`, and mapping from `Outcome<...>` to `UiState` happens on `dispatchers.main`.
- Use the injected `DispatcherProvider`. Never create `GlobalScope`.
- Never call platform UI, Firebase, GitLive or Koin APIs.

`UiState` MUST NOT contain user-facing text. Messages are represented as `UiMessage` (§20.10), whose `code` is a stable programmatic code, not display copy. Domain-specific typed values such as `ConsumptionInvalidReason`, enum states and confirmation identifiers remain typed fields. Each platform maps those values to its own string resources. Numbers and dates reach the UI as raw scaled values; formatting is platform-side. This is what makes "no hardcoded user-facing strings" achievable from shared code.

Every state holder that exposes `SyncStatus` (`VehicleListUiState.syncStatus`, `FuelEntryListUiState.syncStatus` and `SyncUiState.status` from `SyncStateHolder`) observes the same `SyncController.status: StateFlow<SyncStatus>` flow. The values are eventually consistent and converge to the same `SyncStatus`. List state holders MUST NOT independently compute `SyncStatus`; they MUST relay the single `SyncController.status` source. A unit test MUST assert that two holders fed by the same `SyncController` converge.

D-88 records the one temporary exception: E1-07 retains D-55 direct Vehicle restoration and
publishes constant `SyncStatus.Idle` without constructing a provisional `SyncController`. E3-03
MUST remove that exception. D-95 records the parallel Fuel Entry list exception: E1-08 publishes
constant `SyncStatus.Idle` without constructing a provisional controller. E3-03 MUST remove both
exceptions, wire every exposing holder to the single controller and add the two-holder convergence
test.

Platform adapters contain rendering and lifecycle glue only. Validation, formatting decisions, repository calls and business logic remain shared.

## 15. Platform Boundary Contract

### 15.1 Mechanism

Injection is the **only** mechanism for anything present in `AppGraphDependencies`. Any interface already injected through `AppGraphDependencies` MUST NOT also have an `expect`/`actual` form. `expect`/`actual` is reserved for provider-free platform factory functions and for native credential acquisition.

`expect`/`actual` declarations MUST be `internal` and MUST NOT appear in any public API surface.

Allowed `expect`/`actual`:

- Internal platform factories for the SQLDelight AndroidX driver and database file location.
- Native Google and Apple credential acquisition.

Forbidden `expect`/`actual`:

- Domain entities, business validation, consumption calculation.
- Repository interfaces, sync algorithm, conflict resolution, error taxonomy.
- Anything already injected through `AppGraphDependencies`, including `UuidGenerator`, `ConnectivityObserver`, `LocaleProvider`, `Logger` and `AnalyticsTracker`.
- Provider-specific analytics, logging or crash-reporting SDK bindings.

### 15.2 Dependency Injection Contract

Koin KMP is the accepted dependency injection library for the MVP.

- Koin modules are wiring artifacts only.
- Koin APIs are allowed in application, wiring, integration and platform composition modules.
- Koin APIs are FORBIDDEN in feature `domain` packages, use cases, repository interfaces, repository implementations and shared presentation business logic.
- Implementation classes use constructor injection.
- Domain tests MUST NOT require a Koin runtime.
- Koin MUST NOT become a service locator inside product logic.

### 15.3 Swift-facing surface

The public Swift-facing ABI of the `Shared` framework is an allowlist, not "all public Kotlin
declarations". `:composition:ios` produces the framework and exports the allowlisted declarations
owned by `:shared`, `:feature:vehicle`, `:feature:fuel` and `:core:common`. It MUST NOT export
`:integration:*`.
The allowlist is:

- `SwiftAppGraph`.
- `createSwiftAppGraph(isDebugBuild: Boolean)`.
- Concrete state-holder classes declared in §20.10.
- `UiState` data classes and UI row data classes declared in §20.10.
- `UiMessage`, `UiMessageKind`, `SyncStatus` and the typed enums referenced by those state classes.

`SwiftAppGraph` exposes keyed release functions for cached Vehicle forms, Fuel Entry lists and
Fuel Entry forms. Each release removes and closes one holder and cancels its graph-owned child
scope; a later factory call for the same key returns a fresh holder (`D-90`).

Swift-facing signatures MUST use only `String`, `Long`, `Int`, `Boolean`, `Unit`, nullable variants of those, `data class`, `sealed class`, `enum class`, read-only `List<T>` where `T` is also Swift-facing, and `StateFlow<T>` where `T` is one declared `UiState` class. They MUST NOT expose `value class`, project-owned type parameters, default arguments, `CoroutineScope`, `Outcome`, `AppError`, repository or use-case interfaces, command models, `EntityId`, `OwnerId`, `CurrencyCode`, SQLDelight or SQLite types, Firebase types, GitLive types, Koin types, Ktor types, Android types or iOS types.

`AppProviders`, `AppGraphDependencies`, `buildAppGraph(isDebugBuild, providers)`, the
Kotlin-facing `AppGraph`, `SyncController`, repository interfaces and use-case interfaces are
Kotlin-facing contracts and MUST NOT be exported to Swift.

`SegmentResult` and `ConsumptionReport` are domain types and MUST NOT appear on the Swift-facing ABI. Only their projected summary fields in `FuelEntryListUiState` and `FuelEntryListItemUi.consumptionScaled` / `invalidReason` cross the boundary.

`SyncStatus` and all of its leaves (`Idle`, `Syncing`, `Pending`, `Failed`) are part of the Swift-facing allowlist. Both the sealed hierarchy and a separate enum-shaped DTO MUST NOT coexist.

`SwiftAppGraph` MUST expose a no-argument constructor on the Swift-facing side. The `isDebugBuild` flag is consumed by `createSwiftAppGraph(isDebugBuild)` and applied during graph construction. State holders MUST NOT use Kotlin default arguments anywhere in public signatures.

Exported enum cases MUST keep their Kotlin case names on the Objective-C/Swift boundary. Where SKIE or Kotlin/Native would otherwise alter names, the enum class or entries MUST use explicit Objective-C naming annotations so generated headers contain the contract names. The exact common-enum names are `SharedConfirmation` / `Confirmation`, `SharedAuthProvider` / `AuthProvider` and `SharedSyncTrigger` / `SyncTrigger` for Objective-C / Swift respectively (`D-91`).

Nullable primitives and `StateFlow<T>` / `List<T>` exports MUST use the SKIE and Kotlin/Native annotations required by the pinned SKIE version to produce stable Swift types. The generated header golden is the executable source of truth for those annotations.

A golden file of the generated Objective-C header remains committed at
`shared/build/generated/objc-header/Shared.h.golden`. The `objc-header-golden-check` CI step
generates its source from `composition/ios/build`, diffs the two files on every PR and treats a
change as a review signal.

### 15.4 HTTP/API Client Contract

Ktor is the reserved HTTP client for future API-based remote implementations.

- Do not add Ktor dependencies during the MVP while Firebase Firestore is the selected remote database implementation.
- A future Ktor implementation must implement existing provider abstractions such as `RemoteSyncSource`.
- A future Ktor implementation MUST execute HTTP requests on `dispatchers.io`, with a 30-second connect timeout and a 60-second read timeout. Retries remain the sync engine's responsibility; the Ktor implementation MUST NOT retry on its own.
- Ktor types must not appear in feature, domain, repository or presentation contracts.
- Adding Ktor requires an ADR update and a backlog story defining the target API contract.

### 15.5 Image Loading Contract

Coil is the approved image loading library if the project ever needs image loading.

- Until a backlog story requires image loading, no image-loading dependency of any kind may be added.
- If a story requires image loading, it MUST add Coil, declare the story ID as the consumer, and update the library review matrix in `docs/DECISION_BOARD.md`.
- No alternative image loading library may be introduced without updating `docs/DECISION_BOARD.md` and adding or updating an ADR.
- Image loading must remain in UI and platform layers and must not enter domain, data or sync logic.

## 16. Firestore Contract

Remote collections:

```text
users/{uid}/vehicles/{vehicleId}
users/{uid}/fuelEntries/{entryId}
```

This list is also the declared Firestore data-location schema used by the D-63 deletion-registry
parity test in §11.5. There is no remote settings document and there are no Cloud Storage prefixes
in the MVP (§3).

The remote schema is closed. A remote document MUST contain exactly the required key set for its collection, including nullable fields with explicit `null` values. Extra keys, missing keys, unknown collections and local-only metadata are invalid. This applies equally to active documents and tombstones, because tombstones are full-document updates with `deleted = true`.

Remote timestamp fields use Firestore `timestamp` values. The outbox JSON still encodes instants as epoch milliseconds (§8); the Firestore integration converts them at the boundary.

Allowed remote `Vehicle` keys:

| Key | Required | Type and constraints |
|-----|----------|----------------------|
| `id` | Yes | UUID v4 string; MUST equal `{vehicleId}`. |
| `ownerId` | Yes | String; MUST equal authenticated `{uid}`. |
| `name` | Yes | String length 1..40 after repository normalisation. |
| `initialOdometerKm` | Yes | Integer in `0..2000000`. |
| `brand` | Yes (nullable) | Null, or string length 1..40 after repository normalisation. |
| `model` | Yes (nullable) | Null, or string length 1..40 after repository normalisation. |
| `fuelType` | Yes | One of `GASOLINE`, `DIESEL`, `LPG`, `CNG`, `OTHER`. |
| `createdAt` | Yes | Firestore timestamp. |
| `updatedAt` | Yes | Firestore timestamp; MUST equal `request.time` on create/update. |
| `deleted` | Yes | Boolean. |
| `deletedAt` | Yes (nullable) | Null when `deleted = false`; Firestore timestamp when `deleted = true`. |
| `schemaVersion` | Yes | Integer, exactly `1` for the MVP remote schema. |

Allowed remote `FuelEntry` keys:

| Key | Required | Type and constraints |
|-----|----------|----------------------|
| `id` | Yes | UUID v4 string; MUST equal `{entryId}`. |
| `ownerId` | Yes | String; MUST equal authenticated `{uid}`. |
| `vehicleId` | Yes | UUID v4 string. |
| `date` | Yes | Firestore timestamp, not before `1970-01-01T00:00:00Z` and not more than one hour after `request.time`. |
| `odometerKm` | Yes | Integer in `0..2000000`. |
| `litersScaled` | Yes | Integer in `1..500000`. |
| `pricePerLiterScaled` | Yes | Integer in `1..999999`. |
| `totalCostMinor` | Yes | Integer in `1..99999999`. |
| `currency` | Yes | One of `SUPPORTED_CURRENCY_CODES` (§20.0.1). |
| `isFullTank` | Yes | Boolean. |
| `hasMissedEntries` | Yes | Boolean. |
| `odometerInconsistent` | Yes | Boolean. It is remotely stored as part of the snapshot, but local writes to the database column remain owned by `:core:database` (§3.1). |
| `notes` | Yes (nullable) | Null, or string length 1..280 after repository normalisation. |
| `createdAt` | Yes | Firestore timestamp. |
| `updatedAt` | Yes | Firestore timestamp; MUST equal `request.time` on create/update. |
| `deleted` | Yes | Boolean. |
| `deletedAt` | Yes (nullable) | Null when `deleted = false`; Firestore timestamp when `deleted = true`. |
| `schemaVersion` | Yes | Integer, exactly `1` for the MVP remote schema. |

Forbidden remote keys include `syncState`, `localRevision`, `localMutationSeq`, `serverUpdatedAt`, `nameFold`, `currentOdometerKm`, and any key not listed for the target collection. Nullable fields MUST appear in every remote document with an explicit `null` value when empty; an absent nullable key is invalid. A document with `deleted = false` and non-null `deletedAt`, or `deleted = true` and null `deletedAt`, is malformed and MUST be rejected by Firestore rules on write or quarantined on pull (§9.5).

`CLIENT_MAX_SCHEMA_VERSION` (§20.0.1) MUST equal the highest `schemaVersion` value accepted by the Firestore rules. Bumping `CLIENT_MAX_SCHEMA_VERSION` REQUIRES updating the rules in the same change.

Remote rules, split by operation. `allow write` is not used, because it would include delete, and on delete `request.resource` is null:

```javascript
rules_version = '2';
service cloud.firestore {
  match /databases/{db}/documents {
    match /users/{uid}/{collection}/{docId} {

      allow read: if request.auth != null
        && request.auth.uid == uid
        && knownCollection(collection);

      allow create, update: if request.auth != null
        && request.auth.uid == uid
        && request.resource.data.ownerId == uid
        && request.resource.data.updatedAt == request.time
        && request.resource.data.schemaVersion is int
        && request.resource.data.schemaVersion == 1
        && request.resource.data.id == docId
        && validPayload();

      // Deletion is a tombstone update. Hard deletes are forbidden by design.
      allow delete: if false;
    }
  }
}
```

Account deletion hard deletes run only through the `D-23` Firebase Admin server operation. The Admin SDK bypasses Firestore rules entirely; the `allow delete: if false` rule therefore still rejects any mobile client hard delete, which is the load-bearing guarantee. The Admin operation's authorization comes from server IAM plus Firebase authentication token verification, not from Firestore rules. Firestore emulator tests MUST continue to prove that client SDK hard deletes are rejected.

The rule file path is `firestore/rules/main.rules`. `knownCollection(collection)` MUST return true only for `vehicles` and `fuelEntries`. `validPayload()` MUST dispatch by collection and enforce the exact key sets above with `request.resource.data.keys().hasOnly([...])` and `hasAll([...])`, plus primitive type, enum, nullability, `deleted == (deletedAt != null)` and range checks for every field. App Check reduces calls from unofficial clients but does not authorize a user or validate a document. Closed schema and range validation in rules therefore remain the controls preventing an authenticated or attested compromised client from writing a document that breaks parsing on the user's other device.

App Check baseline protection MUST be `ENFORCED` for both Firebase Authentication and Cloud
Firestore before any build leaves local development (`D-67`). Provider selection is build-bound:

- Android release code uses Play Integrity and contains neither the debug provider dependency nor
  a debug provider factory.
- iOS physical non-Debug code uses App Attest and contains no debug-token material.
- Android emulator and iOS Simulator Debug builds may use the debug provider only with a token
  registered outside the repository.
- Debug tokens MUST NOT be committed, emitted in CI or embedded in a distributed build.
- Firebase Authentication and these Firestore Rules remain mandatory when App Check is enforced.

The development cloud-cost contract (`D-66`) is infrastructure-only. Its EUR 10 monthly budget
uses actual-cost email thresholds at 50%, 90% and 100% and publishes to the project-local billing
topic. The `stopBilling` 2nd gen function removes the development project's billing association
when reported actual cost is greater than or equal to the budget. Budget alerts are notifications,
not a spending cap; reporting delay can produce overshoot. Production MUST NOT deploy or inherit
`stopBilling`: its budget response is aggressive notification plus manual intervention.

`stopBilling` MUST use the dedicated keyless identity governed by D-69, check that billing remains
enabled before attempting an update and return without writing when it is already disabled. Its
Pub/Sub failure policy MUST set retry to false (`D-70`). Cloud Monitoring MUST notify the owner on
every function execution error and every Cloud Billing administrative change. Acceptance MUST
measure consecutive real publications from the project budget; documentation of the general
Cloud Billing cadence is not a substitute for the observed interval.

`validPayload()` MUST be equivalent to this shape:

```javascript
function validPayload() {
  return collection == "vehicles" ? validVehicle(docId) :
         collection == "fuelEntries" ? validFuelEntry(docId) :
         false;
}

function validVehicle(vehicleId) {
  return request.resource.data.keys().hasOnly([
      "id", "ownerId", "name", "initialOdometerKm", "brand", "model",
      "fuelType", "createdAt", "updatedAt", "deleted", "deletedAt", "schemaVersion"
    ])
    && request.resource.data.keys().hasAll([
      "id", "ownerId", "name", "initialOdometerKm", "brand", "model",
      "fuelType", "createdAt", "updatedAt", "deleted", "deletedAt", "schemaVersion"
    ])
    && request.resource.data.id == vehicleId
    && isUuid(request.resource.data.id)
    && request.resource.data.ownerId == request.auth.uid
    && request.resource.data.name is string
    && request.resource.data.name.size() >= 1
    && request.resource.data.name.size() <= 40
    && request.resource.data.initialOdometerKm is int
    && request.resource.data.initialOdometerKm >= 0
    && request.resource.data.initialOdometerKm <= 2000000
    && nullableString(request.resource.data.brand, 1, 40)
    && nullableString(request.resource.data.model, 1, 40)
    && request.resource.data.fuelType in ["GASOLINE", "DIESEL", "LPG", "CNG", "OTHER"]
    && request.resource.data.createdAt is timestamp
    && request.resource.data.updatedAt == request.time
    && request.resource.data.deleted is bool
    && deletedShapeIsValid()
    && request.resource.data.schemaVersion is int
    && request.resource.data.schemaVersion == 1;
}

function validFuelEntry(entryId) {
  return request.resource.data.keys().hasOnly([
      "id", "ownerId", "vehicleId", "date", "odometerKm", "litersScaled",
      "pricePerLiterScaled", "totalCostMinor", "currency", "isFullTank",
      "hasMissedEntries", "odometerInconsistent", "notes", "createdAt",
      "updatedAt", "deleted", "deletedAt", "schemaVersion"
    ])
    && request.resource.data.keys().hasAll([
      "id", "ownerId", "vehicleId", "date", "odometerKm", "litersScaled",
      "pricePerLiterScaled", "totalCostMinor", "currency", "isFullTank",
      "hasMissedEntries", "odometerInconsistent", "notes", "createdAt",
      "updatedAt", "deleted", "deletedAt", "schemaVersion"
    ])
    && request.resource.data.id == entryId
    && isUuid(request.resource.data.id)
    && request.resource.data.ownerId == request.auth.uid
    && isUuid(request.resource.data.vehicleId)
    && request.resource.data.vehicleId is string
    && request.resource.data.date is timestamp
    && request.resource.data.date >= timestamp.date(1970, 1, 1)
    && request.resource.data.date <= request.time + duration.value(1, "h")
    && request.resource.data.odometerKm is int
    && request.resource.data.odometerKm >= 0
    && request.resource.data.odometerKm <= 2000000
    && request.resource.data.litersScaled is int
    && request.resource.data.litersScaled >= 1
    && request.resource.data.litersScaled <= 500000
    && request.resource.data.pricePerLiterScaled is int
    && request.resource.data.pricePerLiterScaled >= 1
    && request.resource.data.pricePerLiterScaled <= 999999
    && request.resource.data.totalCostMinor is int
    && request.resource.data.totalCostMinor >= 1
    && request.resource.data.totalCostMinor <= 99999999
    && request.resource.data.currency in [
      "ARS", "AUD", "BRL", "CAD", "CHF", "COP", "CZK", "DKK", "EUR", "GBP", "HUF",
      "MAD", "MXN", "NOK", "NZD", "PEN", "PLN", "RON", "SEK", "USD", "UYU"
    ]
    && request.resource.data.isFullTank is bool
    && request.resource.data.hasMissedEntries is bool
    && request.resource.data.odometerInconsistent is bool
    && nullableString(request.resource.data.notes, 1, 280)
    && request.resource.data.createdAt is timestamp
    && request.resource.data.updatedAt == request.time
    && request.resource.data.deleted is bool
    && deletedShapeIsValid()
    && request.resource.data.schemaVersion is int
    && request.resource.data.schemaVersion == 1;
}

function nullableString(value, min, max) {
  return value == null || (value is string && value.size() >= min && value.size() <= max);
}

function isUuid(value) {
  return value is string
      && value.matches("^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$");
}

function deletedShapeIsValid() {
  return (!request.resource.data.deleted && request.resource.data.deletedAt == null)
      || (request.resource.data.deleted && request.resource.data.deletedAt is timestamp);
}
```

Firestore rules cannot test that `fuelEntry.vehicleId` points at an existing active vehicle without creating a cross-document coupling that would reject legal transient orphans. Orphan fuel entries are legal transient state and MUST NOT be rejected by the rule; handling is defined in §9.4.

The `updatedAt == request.time` comparison uses Firestore equality semantics. A client passing a `serverTimestamp()` placeholder is resolved by the server. A client passing a literal timestamp that differs from `request.time`, even by 1 ms, is rejected.

Anonymous users are valid authenticated users.

Remote queries:

```text
where(updatedAt >= overlapSince)
orderBy(updatedAt ASC)
orderBy(documentId ASC)
first page of cycle: startAt(overlapSince)
later pages: startAfter(pageCursor.lastServerUpdatedAt, pageCursor.lastDocumentId)
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
- Reads and writes under unknown remote collections are rejected.
- Writes missing any required key are rejected.
- Writes with any extra key are rejected.
- Writes with local-only keys such as `syncState`, `localRevision`, `localMutationSeq`, `serverUpdatedAt`, `nameFold` or `currentOdometerKm` are rejected.
- Writes where `deleted` and `deletedAt` disagree are rejected.
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

`setUserProperties` is called once on analytics opt-in, and thereafter on every successful vehicle or fuel-entry create/delete, from the presentation layer. It MUST NOT be called from domain or data. Buckets are computed from the current list size. An `E3-09` fixture MUST assert the call cadence.

Analytics calls are FORBIDDEN in domain logic and data persistence logic. Shared presentation or application-level orchestration may track product events after a use case returns `Ok` **or** `Err`, provided the event payload carries no user data. Success and failure events are both permitted; the closed `AnalyticsEvent` hierarchy is the sole source of allowed events. A fixture MUST assert failure events are emitted from presentation, not domain or data.

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

Every sync cycle generates a `cycleId` (§20.7) that is included as a field on every sync log line and stored in the `outbox.cycleId` column (`TECHNICAL_PLAN.md §6`) on every failed attempt. Field values come from an allowlist, never raw user data. The allowlist is exactly: `AppError.code`, `SyncState` enum name, `EntityType` enum name, `SyncTrigger` enum name, `ConsumptionInvalidReason` enum name, `Confirmation` enum name, `cycleId`, integer counts and durations, and `Boolean` flags. Strings are limited to enum names, stable codes and `cycleId`.

Logs MUST never include: ID tokens or credentials, raw Firestore payloads, notes, exact odometer values, exact costs, or the Firebase UID in release builds.

Redaction is the responsibility of the `Logger` implementation, not the caller. The implementation accepts entity IDs as `String`; the caller MUST NOT pre-redact. Redaction is decided from the injected `isDebugBuild` flag: debug builds may log entity IDs in full; release builds log the first 8 characters followed by an ellipsis, and never log the UID at any length.

`Throwable.message` and `Throwable.stackTrace` MUST be redacted to stable codes and MUST NOT be logged as text. The throwable class name may be logged in full at `DEBUG`; at `INFO`, `WARN` and `ERROR`, only the class name's last segment may be logged.

`Logger` is not an analytics or crash-reporting API. Logging events MUST NOT be treated as `AnalyticsEvent` values or crash reports. Provider integrations may use `Logger` internally for operational diagnostics, subject to the same privacy rules.

## 18. CI and Branch Protection Contract

D-31 and D-84 define the exact CI check names. Required checks:

- `android-assemble`
- `shared-tests`
- `ios-simulator-build`
- `ktlint`
- `detekt`
- `architecture-check`
- `provider-decoupling`
- `contract-check`
- `objc-header-golden-check`
- `android-instrumented-tests`

The `shared-tests` check executes Android-host tests for every KMP module. Its standalone
`iosSimulatorArm64Test` exception is derived from the project dependency graph (`D-75`): every KMP
module whose Native test binary transitively links `:integration:firebase-auth` or
`:integration:firebase-firestore` qualifies. CI compares that derived set with the explicit task
exclusions and MUST fail in both directions. The current resolution is
`:integration:firebase-auth`, `:integration:firebase-firestore`, `:wiring:firebase` and
`:composition:ios`; this is observed output, not a permanent allowlist, and changes when the graph
changes.

Optional checks:

- `database-lock` — when `core/database/.story-lock` exists, CI verifies that the current story named in the handoff owns `:core:database`. A failing lock means another in-flight story owns the shared-write module. The lock is created when a database story starts and removed in the same PR before completion; it is a coordination guard, not a permanent repository artifact.

`contract-check` is a script that asserts:

1. Every project-owned type named in a code block in this document is declared somewhere in this document. `§20` is where a type is declared unless the section that owns it declares it inline.
2. The decision ID set and status are identical in `docs/DECISION_BOARD.md`, `docs/SPECIFICATION.md §12`, `docs/TECHNICAL_PLAN.md §2` and `docs/adr/README.md`.
3. Every ADR linked from `docs/adr/README.md` has a `Status` heading whose value matches the status recorded for its decision ID.
4. Every `Proposed` or `Pending` decision in `docs/DECISION_BOARD.md` appears in the "Decisions Awaiting Owner Confirmation" section with a `Needed by` story or phase. If no such decisions exist, the section states that none are awaiting owner confirmation and contains no empty table.
5. Every interface declared in this document appears in at least one `docs/BACKLOG.md` story.
6. `.github/pull_request_template.md` remains a superset of `docs/templates/agent-handoff.md` section headings.
7. The committed Objective-C header golden file for `:shared` is unchanged.
8. `docs/SPECIFICATION.md §8.2` references the canonical module inventory of §1.1, and `docs/TECHNICAL_PLAN.md §3` reproduces it, comparing the path token before whitespace for technical-plan lines.
9. The dependency allowlists in `docs/SPECIFICATION.md §8.3`, `docs/TECHNICAL_PLAN.md §4` and the Gradle module graph are generated from the same source or compare equal on module path, allowed dependencies and forbidden dependencies.
10. Decision-table comparison uses only the `ID`, `Choice` and `Status` columns; explanatory wording outside those columns is not compared.
11. `CLIENT_MAX_SCHEMA_VERSION` in §20.0.1 equals the highest `schemaVersion` accepted by `firestore/rules/main.rules`.
12. §7 explicitly references the qualified poison rule of §9.7.
13. `testAppGraphDependencies(...)` has the same parameter count and order as `AppGraphDependencies`, with every parameter defaulted.
14. Kotlin-facing `AppGraph` factories take `scope: CoroutineScope`, Swift-facing `SwiftAppGraph` factories do not, and no exported state-holder function has a Kotlin default argument.
15. No `TBD` remains in `docs/versions-matrix.md` after `E0-06` lands.
16. No image-loading dependency appears in `gradle/libs.versions.toml` without a story reference and the Coil decision path required by §15.5.
17. The push dependency order in `docs/TECHNICAL_PLAN.md §8` references the canonical order of §8 instead of restating a divergent order.
18. `AuthProvider` is declared in §20.3 (`:core:common`) before any reference to it in §20.8 (`:core:auth`) or §20.9 (`:core:analytics`), so a Phase 0 module (`:core:analytics`) can compile without depending on a Phase 2 module (`:core:auth`).
19. The Cloud Functions runtime, Functions manifest and cloud-runtime CI assertion equal the
    normative runtime row in `docs/versions-matrix.md`; a hardcoded second runtime fails.
20. The Google authentication and Cloud SDK GitHub Actions use the immutable SHAs recorded in
    `docs/versions-matrix.md`, not floating tags.
21. The `shared-tests` workflow executes both aggregate test tasks. Its declared standalone
    Kotlin/Native exclusions equal the set derived by taking the transitive reverse dependency
    closure from `:integration:firebase-auth` and `:integration:firebase-firestore` across the
    Native-test project graph; equality fails on either addition or removal.

The protected `contract-check` job also performs a read-only deployed-runtime assertion for
internal pull requests targeting `main` and pushes to `main`. GitHub OIDC is admitted through a
provider condition restricted to the immutable repository/owner, the
`cloud-runtime-verification` environment and those exact event/ref contexts. The service account's
custom role contains exactly `cloudfunctions.functions.get`. The job reads the expected runtime
from `docs/versions-matrix.md`, not from `functions/package.json`, then separately fails if the
manifest disagrees with the matrix. Fork pull requests receive no cloud identity.

For assertion 1, the parser strips comments and string literals before collecting identifiers. It ignores the following non-project identifiers: Kotlin primitives (`String`, `Long`, `Int`, `Boolean`, `Unit`), Kotlin standard library containers and primitives (`List`, `Set`, `Map`, `MutableMap`, `Pair`, `Nothing`), nullable markers, `Throwable`, `kotlinx.coroutines` types (`Flow`, `StateFlow`, `CoroutineScope`, `CoroutineDispatcher`), the pinned datetime type recorded in `docs/versions-matrix.md`, platform annotation names used only to hide declarations from Objective-C export, and **SQLDelight-generated types owned by `:core:database`** (`AppDatabase`, generated query interfaces and generated row classes). SQLDelight-generated types are allowed only in `:core:database` signatures and in the `DatabaseHandle` / `DatabaseFactory` declarations (`§20.3.2`); any appearance in `:core:common`, `:core:sync`, feature `domain` or the `:shared` public API remains a violation. Before `E0-06` pins the datetime package, the `Instant` reference in §20 is treated as a known `TBD` placeholder and `contract-check` reports the `E0-06` blocker instead of accepting a guessed package. After `E0-06`, the ignored type MUST equal the exact fully-qualified `Instant` package recorded in the matrix. Any other capitalized identifier in a public signature is treated as project-owned and MUST be declared in §20.

For the Objective-C header assertion, `contract-check` MUST fail if the header exports any forbidden type from §15.3 or omits any Swift-facing type explicitly listed in §20.10.

Once CI exists, branch protection for `main` MUST require these checks before merge.

## 19. Human Review

The canonical human review gate list lives in `AGENTS.md` and MUST NOT be restated here. Any change to this document is gated.

## 20. Canonical Type Definitions

Every type this document does not declare inline is declared here. Implementations MUST match these shapes.

A few types are declared by the section that owns them rather than here — `Logger` in `§17`, `AnalyticsTracker` in `§16.1`, `RemoteSyncSource` in `§10`, `AuthClient` and `AppGraphDependencies` in `§11`, the repositories in `§12` and the use cases in `§13`. That is deliberate: those declarations are inseparable from the prose that constrains them. `contract-check` assertion 1 accepts either location.

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

This is a deliberate constraint, not an oversight. §5 requires that a pull transaction MUST NOT fail because of a domain constraint or malformed remote payload. A throwing constructor would turn one malformed remote document into an exception inside the pull transaction, stalling the cursor permanently — the exact failure mode §5 exists to prevent. App Check proves caller integrity but does not validate payload semantics, and Firestore rules validate ranges and types but cannot verify that a string is a real ISO-4217 code, so such a document remains reachable from a compromised official client.

**Every scaled value is a `Long`**, per §2. Mixing widths across these types is a contract violation.

All of the above are Kotlin-internal and MUST NOT appear on the Swift-facing surface (§15.3).

### 20.0.1 Named constants — `:core:common`

Constants referred to by name elsewhere in this document. Writing the literal inline instead of referencing one of these is a contract violation. They live beside the backoff helper in `:core:common` (`docs/TECHNICAL_PLAN.md §3`), which every module that needs them already depends on; `LOCAL_OWNER` is the exception and lives with `OwnerId` in §20.0.

```kotlin
const val CLIENT_MAX_SCHEMA_VERSION: Int = 1   // §9.5  — highest schemaVersion this client applies
const val MAX_RETRYABLE_ATTEMPTS: Int = 10     // §9.7  — attemptCount ceiling
const val MAX_ENTRIES_IN_MEMORY: Int = 5_000   // §12   — per-vehicle fuel entry load ceiling
const val SYNC_WORK: String = "carapp-sync"    // §9.1  — Android enqueueUniqueWork name
const val STATE_HOLDER_TIMEOUT_MS: Long = 5_000L          // §14   — WhileSubscribed timeout
const val FOREGROUND_RESUME_THRESHOLD_MS: Long = 300_000L // §9.8  — 5 minutes
const val FRESH_LOGIN_THRESHOLD_MS: Long = 300_000L       // §11.5 — 5 minutes

// §2 — every supported MVP currency has exactly two decimal minor units, factor 100
val SUPPORTED_CURRENCY_CODES: Set<String> = setOf(
    "ARS",
    "AUD",
    "BRL",
    "CAD",
    "CHF",
    "COP",
    "CZK",
    "DKK",
    "EUR",
    "GBP",
    "HUF",
    "MAD",
    "MXN",
    "NOK",
    "NZD",
    "PEN",
    "PLN",
    "RON",
    "SEK",
    "USD",
    "UYU",
)

// §9.7 — failures that MUST NOT consume the poison budget
val CONNECTIVITY_ERROR_CODES: Set<String> = setOf(
    "REMOTE.UNAVAILABLE",
    "REMOTE.DEADLINE_EXCEEDED",
)
```

`CLIENT_MAX_SCHEMA_VERSION` is bumped only by a story that also ships the migration able to read the new version, and bumping it REQUIRES re-evaluating the `quarantine` table on upgrade (§9.5).

Every code in `SUPPORTED_CURRENCY_CODES` MUST be verified as two-decimal by the MVP platform locale APIs on Android and iOS. If a runtime reports a different minor-unit factor for any supported code, validation falls back to `EUR` rather than accepting a different factor.

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
    data class EditNotAllowed(val field: String) : ValidationError { override val code = "VALIDATION.EDIT_NOT_ALLOWED" }
    data class DuplicateName(val name: String) : ValidationError { override val code = "VALIDATION.DUPLICATE_NAME" }
    data object FutureDate : ValidationError { override val code = "VALIDATION.FUTURE_DATE" }
    data object InvalidMoneyInput : ValidationError { override val code = "VALIDATION.INVALID_MONEY_INPUT" }
    data object NoOp : ValidationError { override val code = "VALIDATION.NO_OP" }
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
    data object AccountDeletionRemoteFailed : AuthError { override val code = "AUTH.ACCOUNT_DELETION_REMOTE_FAILED" }
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

@ObjCName(name = "SharedConfirmation", swiftName = "Confirmation", exact = true)
enum class Confirmation { OdometerInconsistent, DiscardPendingChanges, DeleteAccount, AdoptExistingAccount }
```

`UnexpectedError.origin` is the Gradle module path that converted the failure, for example `":integration:firebase-firestore"`.

A confirmation is required by the use case, not by the UI. The UI MUST NOT proceed without an explicit confirmation returned to the use case.

| Confirmation | Flow |
|--------------|------|
| `OdometerInconsistent` | Odometer warning override in fuel-entry create/update. |
| `DiscardPendingChanges` | Sign-out or local-data deletion with pending outbox rows. |
| `DeleteAccount` | Account deletion destructive confirmation. |
| `AdoptExistingAccount` | Anonymous-to-permanent credential collision where the user confirms that the current anonymous-session snapshot replaces the existing permanent-account data. |

### 20.3 Platform abstractions — `:core:common`

The public `earliestAllowedFuelEntryDate(vehicleCreatedAt: Instant): Instant` helper is the bounded
D-81 calendar exception specified normatively in §2.

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

@ObjCName(name = "SharedSyncTrigger", swiftName = "SyncTrigger", exact = true)
enum class SyncTrigger { AppForeground, ConnectivityRecovered, PostWriteDebounce, PullToRefresh, Periodic }
fun interface SyncTriggerAdapter { fun schedule(reason: SyncTrigger) }

@ObjCName(name = "SharedSyncSyncStatus", swiftName = "SyncSyncStatus", exact = true)
sealed class SyncStatus {
    data object Idle : SyncStatus()
    data object Syncing : SyncStatus()
    data class Pending(val count: Int) : SyncStatus()
    data class Failed(val retryableCount: Int, val poisonedCount: Int) : SyncStatus()
}

@ObjCName(name = "SharedUiMessageKind", swiftName = "UiMessageKind", exact = true)
enum class UiMessageKind { INFO, WARNING, ERROR }

@ObjCName(name = "SharedUiMessage", swiftName = "UiMessage", exact = true)
data class UiMessage(
    val id: Long,
    val kind: UiMessageKind,
    val code: String,
    val confirmation: Confirmation?,
)

@ObjCName(name = "SharedAuthProvider", swiftName = "AuthProvider", exact = true)
enum class AuthProvider { ANONYMOUS, GOOGLE, APPLE }   // shared by :core:analytics (Phase 0) and :core:auth (Phase 2); lives here so neither module depends on the other

interface OwnerContext {
    val current: OwnerId
    fun observe(): Flow<OwnerId>
}

object MinorUnits { fun factorFor(currency: CurrencyCode): Int? }   // supported -> 100, unsupported -> null
```

### 20.3.1 Crash reporting types — `:core:crash`

```kotlin
interface CrashReporter {
    fun recordNonFatal(error: AppError, fields: Map<String, String>)
    fun setEnabled(enabled: Boolean)
}
```

The no-op implementation lives in `:core:crash` and is the default fake selected by the
`:shared:testing` graph factory. Firebase Crashlytics types stay inside
`:integration:firebase-crashlytics` and `:wiring:firebase`.

`recordNonFatal` trigger policy: it MUST be called for every `UnexpectedError` and for every `SyncError.Poisoned` / `FAILED_POISONED` transition; it MUST NOT be called for validation warnings, expected `AuthError` leaves (`Cancelled`, `RequiresRecentLogin`, `CredentialAlreadyInUse`), or connectivity-only `RemoteError` codes. `fields` follows the same allowlist as `Logger` (`§17`). An `E3-03` / `E4-04` fixture MUST assert the call sites.

### 20.3.2 Database types — `:core:database`

```kotlin
// AppDatabase is the SQLDelight-generated database type owned by :core:database.
// It is generated from the committed .sq schema; contract-check assertion 1 allows
// SQLDelight-generated types only in :core:database and these database-owner signatures.

interface DatabaseHandle {
    val database: AppDatabase
    fun close()
}

interface DatabaseFactory { fun create(): DatabaseHandle }
```

`DatabaseHandle` owns exactly one `AppDatabase` and its underlying `SqlDriver`. Its `close()` is
idempotent, releases that driver exactly once and makes later database operations fail. The caller
of `DatabaseFactory.create()` owns the returned handle; the factory does not retain or close it.

`DatabaseFactory` and `DatabaseHandle` live in `:core:database` (not `:core:common`) because their
signatures contain `AppDatabase`, a SQLDelight-generated type owned by `:core:database`, and `:core:common` is
forbidden from depending on SQLDelight or SQLite (`docs/TECHNICAL_PLAN.md §4`). `:shared` carries
`databaseFactory: DatabaseFactory` in `AppGraphDependencies` (`§11.6`) and imports it from
`:core:database`. `:core:testing` is allowed to depend on `:core:database` so it can provide a
generic fake; `:shared:testing` composes that fake into `AppGraphDependencies` (`D-56`). D-59 also
allows `:wiring:firebase` to reference the `DatabaseFactory` abstraction while implementing
`AppProviders`, but it MUST NOT expose or reference `AppDatabase`. Any appearance of `AppDatabase`
or either database lifetime type in `:core:common`, `:core:sync`, feature `domain` or the
Swift-facing public API remains a violation. `DatabaseHandle` and `DatabaseFactory` MUST NOT appear
in the Objective-C header.

### 20.4 Domain models — `:core:model`

```kotlin
enum class FuelType { GASOLINE, DIESEL, LPG, CNG, OTHER }
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
    val hasMissedEntries: Boolean,
    val odometerInconsistent: Boolean,
)

data class UserSettings(
    val currency: CurrencyCode,
    val distanceUnit: DistanceUnit,
    val volumeUnit: VolumeUnit,
    val analyticsEnabled: Boolean,
)
```

`FuelEntryListItem` consumption mapping is exact:

- For an entry with `isFullTank = false`, `consumption = null` and `invalidReason = EndEntryNotFullTank`.
- For an entry with `isFullTank = true` whose segment result is `SegmentResult.Valid`, `consumption = segment.consumption` and `invalidReason = null`.
- For an entry with `isFullTank = true` whose segment result is `SegmentResult.Invalid`, `consumption = null` and `invalidReason = segment.reason`.

The list projection MUST NOT run a different consumption algorithm. It derives row values from the same full-to-full segment rules of §4 and §20.6.

### 20.4.1 Vehicle edit projection — `:feature:vehicle` domain

```kotlin
data class VehicleEditFacts(
    val vehicle: Vehicle,
    val canEditInitialOdometer: Boolean,
)
```

`VehicleEditFacts` is Kotlin-only and MUST NOT appear in the Swift-facing surface. Its editability
value is advisory; the `updateVehicle` transaction remains authoritative and rejects a stale
optimistic edit with `ValidationError.EditNotAllowed("initialOdometerKm")`.

### 20.5 Commands

```kotlin
data class CreateVehicleCommand(
    val name: String,
    val initialOdometerKm: Long,
    val brand: String?,
    val model: String?,
    val fuelType: FuelType = FuelType.GASOLINE,
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

data class FuelEntryValidationContext(
    val now: Instant,
    val earliestAllowedDate: Instant,
    val vehicleInitialOdometerKm: Long,
    val previousOdometerKm: Long?,
)

data class ValidatedFuelEntryValues(
    val vehicleId: EntityId,
    val date: Instant,
    val odometerKm: Long,
    val litersScaled: Long,
    val pricePerLiterScaled: Long,
    val totalCostMinor: Long,
    val currency: CurrencyCode,
    val isFullTank: Boolean,
    val hasMissedEntries: Boolean,
    val notes: String?,
)

data class UpdateSettingsCommand(
    val currency: CurrencyCode?,
    val analyticsEnabled: Boolean?,
)
```

`UpdateVehicleCommand` carries every editable field. `null` for `initialOdometerKm` means "leave unchanged"; all other fields are supplied with their current or edited values. A future patch may introduce a sparse-update variant, but the MVP uses this complete-field command shape.

For `UpdateSettingsCommand`, `null` means unchanged. If both fields are `null`, `SettingsRepository.updateSettings` returns `Err(ValidationError.NoOp)` and mutates nothing.

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

`EndEntryNotFullTank` is produced only by the `FuelEntryListItem` projection for an entry with `isFullTank = false`. `SegmentResult.Invalid` MUST NOT use `EndEntryNotFullTank`, because `ConsumptionReport.segments` contains one result per full-tank entry only.

`SegmentResult.Valid.consumption` and `ConsumptionReport.average` are both produced by the canonical consumption arithmetic of §2, which is the only normative statement of those formulas. Partial entries inside a valid full-to-full segment are included in `SegmentResult.Valid.litersScaled`; the partial row itself has no consumption value. `average` is distance-weighted over the valid segments only, and is NOT the arithmetic mean of the segment values.

`isReliable == true` iff `validSegmentCount >= 2`. When `validSegmentCount == 1`, `average != null` but `isReliable == false`; a single segment is a value, not a reliable average. When `validSegmentCount == 0`, `average == null` and `isReliable == false`.

`ConsumptionInvalidReason`, `SegmentResult` and `ConsumptionReport` live in `:core:model`. Moving any of them to `:core:common` would violate the `:core:model` dependency rule because `FuelEntryListItem` and the domain models depend on them.

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

@JvmInline value class CycleId(val value: String) // lowercase canonical UUID v4 generated per sync cycle

enum class QuarantineReason {
    UnsupportedSchemaVersion,
    MalformedPayload,
}

data class QuarantineRecord(
    val entityType: EntityType,
    val entityId: EntityId,
    val reason: QuarantineReason,
    val schemaVersion: Int,
    val serverUpdatedAt: Instant,
    val rawJson: String,
    val createdAt: Instant,
)

interface SyncController {
    val status: StateFlow<SyncStatus>
    fun requestSync(reason: SyncTrigger)
    suspend fun retryFailed(): Outcome<Unit, AppError>
}
```

`SyncStatus` is declared in §20.3 and owned by `:core:common`; `SyncController` remains owned by
`:core:sync` and imports it. This preserves the strict feature-presentation dependency boundary
selected by D-85.

A `sync_cursor` row is created lazily on first pull with `RemoteCursor.INITIAL`. Deleting the row is the only supported way to force a full re-pull.

`SyncController.retryFailed()` returns `Err(PersistenceError.TransactionFailed)` if the reset transaction fails; otherwise `Ok(Unit)`. It MUST NOT return `SyncError` or `RemoteError` leaves because it performs no remote work. An `E3-03` fixture MUST assert the only failure path is local-transaction failure.

`RemoteCursor.INITIAL` is a sentinel representing "no cursor stored yet"; it is never passed to `RemoteSyncSource.pullChanges`. The sync engine materialises it as the timestamp-only `startAt(overlapSince)` first-page boundary per `§9.4`. The `null` prohibition in `§9.4` applies to cursor components passed to `startAt`/`startAfter`; `INITIAL` is exempt because no nullable document-ID component reaches Firestore. An `E3-03` test MUST prove `INITIAL` never reaches `RemoteSyncSource`.

### 20.8 Auth types — `:core:auth`

```kotlin
// AuthProvider is declared in §20.3 (:core:common) and imported here.

data class AuthSession(
    val uid: String,
    val isAnonymous: Boolean,
    val providers: Set<AuthProvider>,
    val createdAt: Instant? = null,
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

data class AuthToken(val value: String, val issuedAt: Instant, val expiresAt: Instant)

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

The `SyncStatus -> SyncStatusCategory` mapping uses the same connectivity-code rule as `§9.9`: `Idle -> IDLE`, `Syncing -> SYNCING`, `Pending -> PENDING`, and `Failed -> FAILED` only when at least one counted row has `lastErrorCode` not in `CONNECTIVITY_ERROR_CODES` (`§9.7`); otherwise `Failed -> PENDING`. A unit test MUST assert the mapping under all combinations, including a `Failed` whose every counted row has a connectivity `lastErrorCode` mapping to `PENDING`.

The `AuthError -> ConversionFailureReason` mapping is normative: `Cancelled -> CANCELLED`, `CredentialAlreadyInUse -> CREDENTIAL_IN_USE`, `NetworkUnavailable -> NETWORK`, `UidWouldChange -> UID_WOULD_CHANGE`, everything else -> `UNKNOWN`. The `AuthError -> DeletionFailureReason` mapping is normative: `RequiresRecentLogin -> REQUIRES_RECENT_LOGIN`, `AccountDeletionRemoteFailed -> REMOTE_FAILED`, `NetworkUnavailable -> NETWORK`, everything else -> `UNKNOWN`. Unit tests MUST assert exhaustiveness of both mappings.

No leaf carries a free-text `String`. Adding one is a contract violation. The leaves are fixed; renaming or splitting a leaf requires a contract change. `FuelEntryCreated.isFullTank` and `FuelEntryCreated.hadNotes` are bucket-level booleans and never carry exact values or note content.

`CountBucket` bounds are exact: `ZERO == 0`, `ONE == 1`, `TWO_TO_FIVE == 2..5`, `SIX_TO_TWENTY == 6..20`, and `MORE_THAN_TWENTY >= 21`.

### 20.10 Shared surface — `:shared`, feature presentation modules and `:core:common`, exported by `:composition:ios`

```kotlin
// Kotlin-facing construction API. Hidden from Objective-C/Swift export.
interface AppGraph {
    fun vehicleListStateHolder(scope: CoroutineScope): VehicleListStateHolder
    fun vehicleFormStateHolder(scope: CoroutineScope, vehicleId: String?): VehicleFormStateHolder
    fun fuelEntryListStateHolder(scope: CoroutineScope, vehicleId: String): FuelEntryListStateHolder
    fun fuelEntryFormStateHolder(scope: CoroutineScope, vehicleId: String, entryId: String?): FuelEntryFormStateHolder
    fun sessionStateHolder(scope: CoroutineScope): SessionStateHolder
    fun syncController(): SyncController
    fun close()
}

// Swift-facing construction API. Declared once in :composition:ios and exported.
fun createSwiftAppGraph(isDebugBuild: Boolean): SwiftAppGraph

// Swift-facing facade. This is exported.
class SwiftAppGraph {
    fun vehicleListStateHolder(): VehicleListStateHolder
    fun vehicleFormStateHolder(vehicleId: String?): VehicleFormStateHolder
    fun releaseVehicleFormStateHolder(vehicleId: String?)
    fun fuelEntryListStateHolder(vehicleId: String): FuelEntryListStateHolder
    fun releaseFuelEntryListStateHolder(vehicleId: String)
    fun fuelEntryFormStateHolder(vehicleId: String, entryId: String?): FuelEntryFormStateHolder
    fun releaseFuelEntryFormStateHolder(vehicleId: String, entryId: String?)
    fun sessionStateHolder(): SessionStateHolder
    fun syncStateHolder(): SyncStateHolder
    fun close()
}

class VehicleListStateHolder {
    val state: StateFlow<VehicleListUiState>
    fun refresh()
    fun selectVehicle(vehicleId: String?)
    fun requestDelete(vehicleId: String)
    fun confirmDelete(vehicleId: String)
    fun clearMessage()
    fun close()
}

class VehicleFormStateHolder {
    val state: StateFlow<VehicleFormUiState>
    fun setName(value: String)
    fun setInitialOdometerKm(value: Long)
    fun setBrand(value: String?)
    fun setModel(value: String?)
    fun setFuelType(value: FuelType)
    fun save()
    fun clearMessage()
    fun close()
}

class FuelEntryListStateHolder {
    val state: StateFlow<FuelEntryListUiState>
    fun refresh()
    fun requestDelete(entryId: String)
    fun confirmDelete(entryId: String)
    fun clearMessage()
    fun close()
}

class FuelEntryFormStateHolder {
    val state: StateFlow<FuelEntryFormUiState>
    fun setDateEpochMillis(value: Long)
    fun setOdometerKm(value: Long)
    fun setMoneyInputMode(value: MoneyInputMode)
    fun setLitersScaled(value: Long?)
    fun setPricePerLiterScaled(value: Long?)
    fun setTotalCostMinor(value: Long?)
    fun setCurrencyCode(value: String)
    fun setFullTank(value: Boolean)
    fun setMissedEntries(value: Boolean)
    fun setNotes(value: String?)
    fun save()
    fun confirmSave(confirmation: Confirmation)
    fun clearMessage()
    fun close()
}

class SessionStateHolder {
    val state: StateFlow<SessionUiState>
    fun startAnonymousSignIn()
    fun startPermanentSignIn(provider: AuthProvider)
    fun startAccountConversion(provider: AuthProvider)
    fun confirmAccountConversion(confirmation: Confirmation)
    fun requestSignOut()
    fun confirmSignOut(confirmation: Confirmation)
    fun requestDeleteAccount()
    fun confirmDeleteAccount(confirmation: Confirmation)
    fun clearMessage()
    fun close()
}

class SyncStateHolder {
    val state: StateFlow<SyncUiState>
    fun requestSync(reason: SyncTrigger)
    fun retryFailed()
    fun clearMessage()
    fun close()
}

data class VehicleListUiState(
    val isLoading: Boolean,
    val vehicles: List<VehicleListItemUi>,
    val selectedVehicleId: String?,
    val syncStatus: SyncStatus,
    val message: UiMessage?,
)

data class VehicleListItemUi(
    val id: String,
    val name: String,
    val currentOdometerKm: Long,
    val fuelType: FuelType,
    val deleted: Boolean,
)

data class VehicleFormUiState(
    val vehicleId: String?,
    val savedVehicleId: String?,
    val name: String,
    val initialOdometerKm: Long,
    val brand: String?,
    val model: String?,
    val fuelType: FuelType,
    val canEditInitialOdometer: Boolean,
    val isSaving: Boolean,
    val message: UiMessage?,
)

data class FuelEntryListUiState(
    val vehicleId: String,
    val isLoading: Boolean,
    val entries: List<FuelEntryListItemUi>,
    val consumptionAverageScaled: Long?,
    val validConsumptionSegmentCount: Int,
    val isConsumptionReliable: Boolean,
    val syncStatus: SyncStatus,
    val message: UiMessage?,
)

data class FuelEntryListItemUi(
    val id: String,
    val dateEpochMillis: Long,
    val odometerKm: Long,
    val litersScaled: Long,
    val totalCostMinor: Long,
    val currencyCode: String,
    val isFullTank: Boolean,
    val consumptionScaled: Long?,
    val invalidReason: ConsumptionInvalidReason?,
    val hasMissedEntries: Boolean,
    val odometerInconsistent: Boolean,
)

enum class MoneyInputMode { LITERS_AND_PRICE, LITERS_AND_TOTAL, PRICE_AND_TOTAL }

data class FuelEntryFormUiState(
    val vehicleId: String,
    val entryId: String?,
    val dateEpochMillis: Long,
    val odometerKm: Long,
    val moneyInputMode: MoneyInputMode,
    val litersScaled: Long?,
    val pricePerLiterScaled: Long?,
    val totalCostMinor: Long?,
    val currencyCode: String,
    val isFullTank: Boolean,
    val hasMissedEntries: Boolean,
    val notes: String?,
    val isSaving: Boolean,
    val message: UiMessage?,
)

enum class SessionPhase { UNKNOWN, LOCAL, ANONYMOUS, PERMANENT, SIGNED_OUT, DELETING }

data class SessionUiState(
    val phase: SessionPhase,
    val providers: List<AuthProvider>,
    val isBusy: Boolean,
    val message: UiMessage?,
)

data class SyncUiState(
    val status: SyncStatus,
    val isOnline: Boolean,
    val message: UiMessage?,
)

// UiMessage and UiMessageKind are declared in §20.3 and owned by :core:common.
```

Identifiers and currency codes cross the Swift-facing boundary as `String`, never as `EntityId`, `OwnerId` or `CurrencyCode`, per §15.3. Swift-facing `id` fields are lowercase canonical UUID v4 strings. Kotlin-to-Swift conversion is a contract: `EntityId.value` is exactly the field value, already lowercase. Dates cross as epoch milliseconds in UTC. The `litersScaled`, `totalCostMinor`, `odometerKm`, `dateEpochMillis`, `consumptionAverageScaled` and `consumptionScaled` suffixes are the Swift-facing scale documentation; `:shared` README material MUST document those factors for iOS consumers.

`dateEpochMillis` remains an absolute instant. Fuel Entry presentation formats it as a calendar
day in an injected device time zone. A user-selected day is converted with
`LocalDate.atStartOfDay(deviceZone)`; the untouched creation default remains the exact `AppClock`
`now` instant and is never normalised. E1-09 MUST apply the same rule on iOS (`D-96`).

Typed enums such as `FuelType` and `AuthProvider` are not user-facing text and are exposed on the Swift side. Each platform maps them to localized strings in its own resource catalogue.

`UiMessage.code` is exactly one of:

- an `AppError.code`, for example `VALIDATION.REQUIRED_FIELD`;
- `CONFIRMATION.<Confirmation enum name>`, for example `CONFIRMATION.OdometerInconsistent`;
- `INFO.<stable string>` for informational messages.

It is never display copy.

`SyncStateHolder.requestSync` is intended for user-initiated sync only. The Swift-facing surface MUST pass `SyncTrigger.PullToRefresh` (and `SyncTrigger.AppForeground` if the platform emits it from a lifecycle hook). `SyncTrigger.PostWriteDebounce`, `SyncTrigger.ConnectivityRecovered` and `SyncTrigger.Periodic` are fired exclusively by `SyncTriggerAdapter` from platform wiring and MUST NOT be invoked from Swift UI code, to avoid duplicating `BGTaskScheduler`/`WorkManager` wiring and bypassing the single-`SyncController` invariant of `§9.1`. A Konsist fixture MUST ban `PostWriteDebounce`, `ConnectivityRecovered` and `Periodic` from any `iosMain` call site of `SyncStateHolder.requestSync`.

`SessionStateHolder.startAccountConversion(provider)` calls `AuthClient.linkCredential` (not `signInWithCredential`), preserves the UID, and maps `AuthError.UidWouldChange` / `AuthError.CredentialAlreadyInUse` to the F-4 collision flow (`SPECIFICATION.md §7 F-4`). `confirmAccountConversion(confirmation)` handles the collision confirmation through `Confirmation.AdoptExistingAccount` or cancellation.

The code block declares public members, not constructors. State-holder constructors are implementation details; callers obtain them only from `AppGraph` or `SwiftAppGraph`. Swift obtains the graph through `createSwiftAppGraph(isDebugBuild)`, whose signature MUST NOT grow provider SDK parameters. Every state holder owns exactly one `StateFlow` property named `state`, every intent function returns immediately, and expected success or failure is reported by a later state emission. `close()` is idempotent and cancels work owned by that state holder. After `close()`, intent functions MUST do nothing and MUST NOT throw.

`SwiftAppGraph` state-holder factories are idempotent within the same graph instance: the first call creates and caches a state holder, and later calls for the same factory arguments return the same instance. `releaseVehicleFormStateHolder`, `releaseFuelEntryListStateHolder` and `releaseFuelEntryFormStateHolder` idempotently remove and close one cached holder and cancel its child scope; a later factory call for the released key creates a fresh instance. After `SwiftAppGraph.close()`, cached state holders are cancelled and removed; any later factory call throws `IllegalStateException`.

`AppGraph.close()` is idempotent and releases the graph-owned `DatabaseHandle`. `SwiftAppGraph.close()`
first closes its cached state holders and then closes the wrapped `AppGraph`, so the database
connection is released transitively. The D-55 staged Fuel, Session and Sync shells do not acquire
additional database handles and remain safe to close.

`FuelEntryFormStateHolder.setMoneyInputMode(mode)` keeps the values already present and immediately
re-derives the value that does not participate in the selected mode from the participating pair
through the D-93 resolver. A value previously derived MAY therefore become an input of the next
derivation. If the participating pair is present, switching modes MUST NOT clear any money value.

`VehicleFormUiState.fuelType` is present for round-trip fidelity and defaults to `GASOLINE`; `VehicleFormStateHolder.setFuelType` exists for testability and future use, but the MVP UI MUST NOT render a `fuelType` selector (`SPECIFICATION.md §7 F-2`, `§5.1`, decision `D-4`). An `E1-07` acceptance criterion MUST assert no `fuelType` control is rendered, while the field round-trips on save.

`VehicleFormUiState.vehicleId` is the identity being edited and remains `null` for a creation
holder. `savedVehicleId` is a completion signal: it is cleared when `save()` begins and contains
the successfully created or updated ID after completion. Successful creation resets every input
to its creation default before publishing `savedVehicleId`, so a retained creation holder cannot
take the update path or retain the previous Vehicle's fields (`D-90`).

The MVP `VehicleListStateHolder` calls `observeVehicles(includeDeleted = false)`; `VehicleListItemUi.deleted` is present for future/debug use and is always `false` in the MVP list. A debug screen (referenced by `E3-03`) MAY call `observeVehicles(includeDeleted = true)` outside the state holder. An `E1-07` fixture MUST assert the production list never contains `deleted = true`.

`SessionPhase` transitions are normative:

```text
UNKNOWN -> LOCAL | ANONYMOUS | PERMANENT | SIGNED_OUT
ANONYMOUS -> PERMANENT | DELETING | SIGNED_OUT
PERMANENT -> DELETING | SIGNED_OUT
LOCAL -> ANONYMOUS | PERMANENT | DELETING
DELETING -> UNKNOWN
UNKNOWN -> SIGNED_OUT after local-data clear
```

From `LOCAL`, `DELETING` means "clearing local data only" (no server operation, because there is no Firebase Auth account); from `ANONYMOUS` or `PERMANENT`, `DELETING` means "running the `D-23` server operation then clearing local data". The `DELETING -> UNKNOWN` transition is followed by `UNKNOWN -> SIGNED_OUT` only after the local-data clear completes. `E2-05` MUST test both paths.

`SyncStatus` is exported through the SKIE/Kotlin-Native sealed-class shape fixed by the generated header. Swift consumers MUST be able to distinguish `Idle`, `Syncing`, `Pending(count)` and `Failed(retryableCount, poisonedCount)` exhaustively.

The declarations moved by D-85 MUST use exact Objective-C names matching the committed golden
header: `SharedVehicleListStateHolder`, `SharedVehicleFormStateHolder`,
`SharedVehicleListUiState`, `SharedVehicleListItemUi`, `SharedVehicleFormUiState`,
`SharedUiMessage`, `SharedUiMessageKind`, `SharedSyncSyncStatus` and its four existing leaf names.
Their existing Swift names MUST also remain unchanged. A module-derived rename is a contract
failure, not an acceptable golden update.

The Fuel Entry declarations moved by D-97 MUST likewise retain the exact names already present in
the golden: `SharedFuelEntryListStateHolder`, `SharedFuelEntryFormStateHolder`,
`SharedFuelEntryListUiState`, `SharedFuelEntryListItemUi`, `SharedFuelEntryFormUiState` and
`SharedMoneyInputMode`, with their existing Swift names. `FuelEntryRepository`, Fuel Entry command
and validation declarations, `MoneyInput`, the pure money resolver, consumption use cases and
`SqlDelightFuelEntryRepository` MUST remain absent from the header. Kotlin/Native's package-grouped
header ordering MAY relocate those six unchanged declaration blocks when their owning package
moves. The reviewed E1-08 golden update contains only that ordering change; a module-derived rename
or signature change is a contract failure.

`FuelEntryFormStateHolder.observeSaveCompletions()` emits once after each successful create or
update, including a confirmed odometer-warning save, and never emits for validation or persistence
errors. Delivery is conflated: while no collector is attached, the holder retains at most one
pending completion so multiple queued successes cannot trigger repeated navigation. It is
Kotlin-only through `@HiddenFromObjC`; Android navigation consumes it, and it MUST remain absent
from the Objective-C header.

`VehicleListUiState.selectedVehicleId` is the navigation source for the vehicle detail screen; `null` means no vehicle is selected.

`VehicleListStateHolder.confirmDelete(vehicleId)` and `FuelEntryListStateHolder.confirmDelete(entryId)` take no `Confirmation` argument: entity deletion is a direct action, not a typed-warning confirmation. If a pending-sync warning applies (e.g. deleting a vehicle with unsynced fuel entries), it is surfaced through `UiMessage` before the destructive action, not through `Confirmation`. The `Confirmation` enum is reserved for typed warnings that require an explicit override (`OdometerInconsistent`, `DiscardPendingChanges`, `DeleteAccount`, `AdoptExistingAccount`).

The Kotlin-facing `AppGraph`, `AppProviders`, `AppGraphDependencies` and
`buildAppGraph(isDebugBuild, providers)` MUST be absent from the Objective-C header.
`createSwiftAppGraph(isDebugBuild)`, `SwiftAppGraph`, the state-holder classes, the `UiState`
classes, `UiMessage`, `SyncStatus`, `FuelType`, `AuthProvider`, `Confirmation`,
`ConsumptionInvalidReason`, `MoneyInputMode`, `SessionPhase`, `SyncTrigger` and `UiMessageKind`
MUST be present.
