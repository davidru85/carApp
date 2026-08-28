# ADR-0078 / D-77 - Validate Fuel Entry Commands from Pre-Write Facts

## Status

Accepted

## Context

E1-04 must implement R-1 and R-2 in a Kotlin-pure Fuel Entry domain. R-1 depends on facts absent
from `CreateFuelEntryCommand` and `UpdateFuelEntryCommand`: the current validation time, the
earliest allowed date derived from the target Vehicle, the Vehicle initial odometer and the
previous non-deleted Fuel Entry in the command's target chronological position. R-2 must resolve
the supplied `MoneyInput` pair into the canonical monetary triple without retaining a supplied-pair
marker.

The warning protocol also requires an unconfirmed inconsistent odometer to mutate nothing. Loading
validation facts before a separate repository call would leave a race between validation and the
write. Moving the rules into SQLDelight would make the E1-04 domain incomplete and prevent the
rules from being tested independently.

## Options Considered

| Option | Benefits | Costs / Risks |
|--------|----------|---------------|
| Pure validators receive immutable pre-write facts and return canonical values | Mirrors D-76; keeps R-1 and R-2 deterministic and Kotlin-pure; lets E1-06 validate and write inside one transaction; the result contains only the canonical persistence fields. | E1-06 must construct the context correctly and prove the transaction boundary. |
| CRUD orchestrators query additional validation ports before calling the repository | Conventional use-case orchestration; facts are acquired by the use case. | Expands the public domain surface with persistence-shaped queries and cannot prevent facts changing between the query and write. |
| Validate only inside the E1-06 data repository | Keeps fact loading next to SQLDelight and makes transaction ownership direct. | Mixes business rules into infrastructure, leaves E1-04 without independently testable use cases and encourages duplicate R-2 arithmetic. |

## Decision

E1-04 exposes `ValidateCreateFuelEntry` and `ValidateUpdateFuelEntry`. Each receives its canonical
command and a `FuelEntryValidationContext` containing `now`, `earliestAllowedDate`,
`vehicleInitialOdometerKm` and `previousOdometerKm`. For update, E1-06 prepares the previous entry
after excluding the target row and placing the edited command in its target chronological
position.

The context supplies `earliestAllowedDate` rather than calendar components. E1-04 therefore
enforces the date bound without introducing the `LocalDate`, `LocalDateTime` or `TimeZone` types
forbidden by `docs/CONTRACTS.md §2`. The exact conversion of the behavioural phrase
`vehicle.createdAt - 20 years` remains a separate owner decision before E1-06 constructs this
fact.

Successful validation returns `ValidatedFuelEntryValues`. It contains every editable persistence
value, including the derived canonical monetary triple, but no target entry ID, owner, timestamp,
sync metadata, `odometerInconsistent` value or supplied-pair marker. E1-06 takes the update target
ID from `UpdateFuelEntryCommand` and the owner and timestamps from their existing injected
abstractions. `:core:database` remains the sole writer of `odometerInconsistent`.

Hard validation runs before the warning. Currency support is checked before arithmetic; supplied
money values are range-checked before deriving the third value; the derived triple is then checked
against every closed §5 bound. Invalid numeric values return `ValidationError.OutOfRange`, an
unsupported explicit currency returns `ValidationError.InvalidUnit`, a date beyond the one-hour
future tolerance returns `ValidationError.FutureDate`, and invalid notes return
`ValidationError.InvalidLength`. `ValidationError.InvalidMoneyInput` remains reserved for a future
input representation that can express a structurally invalid pair; canonical `MoneyInput` cannot.

## Consequences

### Positive

- The Fuel Entry domain depends only on `:core:model` and `:core:common`.
- The exact R-2 formulas remain centralized in `:core:model` and every intermediate stays `Long`.
- A warning result is idempotent and cannot mutate anything because both validators are pure.
- E1-06 can load facts, validate and mutate under one local transaction.
- Persistence receives the canonical triple and cannot infer or retain the originally supplied
  pair.

### Negative

- The context and validated-value result are public Kotlin domain types even though only the data
  shell is expected to construct and consume them in production.
- E1-06 must calculate `earliestAllowedDate`; E1-04 deliberately does not settle the unresolved
  calendar-year representation.
- The public context can technically be constructed with stale or incorrect facts, so E1-06 needs
  explicit transaction and context-construction tests.

### Constraints Introduced

- `earliestAllowedDate` is the already-resolved lower date bound and MUST NOT be earlier than the
  Unix epoch.
- `previousOdometerKm` is null only when no non-deleted predecessor exists in chronological order.
- E1-06 excludes the target entry when preparing update facts.
- E1-06 loads facts, validates and writes within one database transaction.
- `ValidatedFuelEntryValues` contains no supplied-pair marker and no database-owned derived field.
- Numeric supplied values are validated before any arithmetic function is called.

## Verification

- Common tests cover create and update success, every closed validation boundary, all three money
  derivations, normalization and the warning/confirmation protocol.
- Android-host and `iosSimulatorArm64` execute the same domain suite.
- Platform-specific tests verify the supported currency inventory through JVM and Foundation
  locale APIs.
- E1-06 tests will prove context preparation, validation and mutation share one transaction.

## References

- `docs/SPECIFICATION.md §6` R-1 and R-2
- `docs/CONTRACTS.md §2`, `§3`, `§4`, `§5`, `§12`, `§13`, `§20.5`
- `docs/BACKLOG.md` (`E1-04`, `E1-06`)
- ADR-0077 / D-76
