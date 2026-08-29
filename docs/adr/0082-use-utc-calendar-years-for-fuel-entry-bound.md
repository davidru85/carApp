# ADR-0082 / D-81 - Use UTC Calendar Years for the Fuel Entry Earliest-Date Bound

## Status

Accepted

## Context

Fuel Entry validation rejects a date earlier than `vehicle.createdAt - 20 years`, with the Unix
epoch as an absolute lower bound. E1-04 deliberately accepted an already-resolved
`earliestAllowedDate` fact so its domain validators would not depend on calendar types. E1-06 must
now produce that fact before validation and mutation inside one local transaction.

The owner first selected literal calendar years in UTC during the E1-04 review round, with a fixed
duration reserved only as a fallback if the accepted `kotlinx-datetime` library could not perform
the operation in `commonMain`. That approval was captured only as a follow-up in the E1-04 handoff,
not in the decision registry or an ADR. Because handoffs are history rather than authority, the
missing record correctly blocked the E1-06 Ready Check until the owner formally reconfirmed the
choice on 2026-08-29.

## Options Considered

| Option | Benefits | Costs / Risks |
|--------|----------|---------------|
| Subtract 20 calendar years in UTC through `kotlinx-datetime` | Implements the behavioral rule literally; preserves time of day; lets the library clamp a missing target day; is deterministic across platforms. | Requires one tightly bounded `TimeZone.UTC` exception to the non-presentation calendar-type ban. |
| Subtract a fixed 7,305 days | Simple deterministic fallback if calendar arithmetic is unavailable. | The library route is available, so the fallback condition does not hold; adopting it would rewrite the business rule to fit an implementation shortcut. |
| Subtract a fixed 7,300 days | Minimal fixed-duration expression. | Deviates by four or five days in every case and always accepts dates forbidden by the calendar-year rule. |

## Decision

`:core:common` exposes `earliestAllowedFuelEntryDate(vehicleCreatedAt: Instant): Instant`. Its
calendar operation is exactly:

```kotlin
vehicleCreatedAt.minus(20, DateTimeUnit.YEAR, TimeZone.UTC)
```

The helper then returns the later of that value and the Unix epoch. The implementation delegates
day-of-month clamping and time-of-day preservation to `kotlinx-datetime`; it does not decompose the
instant or implement calendar rules manually.

The `TimeZone` exception is restricted to this helper and only the literal `TimeZone.UTC` is
permitted. `LocalDate` and `LocalDateTime` remain forbidden. The helper clamps at the producer
boundary because ADR-0078 constrains the fact itself. The Fuel Entry validator retains its own
Unix-epoch clamp as defense against any independently constructed validation context.

## Consequences

### Positive

- The persisted validation fact matches 20 literal calendar years in UTC.
- Leap-year, century and time-of-day behavior comes from the pinned library on every KMP target.
- Domain validation remains free of calendar types.
- Both the fact producer and its consumer preserve the Unix-epoch invariant.

### Negative

- `:core:common` gains an implementation dependency on `kotlinx-datetime`.
- The architecture checker needs a source rule for the narrow `TimeZone.UTC` exception.

### Constraints Introduced

- The helper MUST use the three-argument `Instant.minus` overload with `DateTimeUnit.YEAR` and
  `TimeZone.UTC`.
- The helper MUST clamp its result to the Unix epoch.
- No hand-written `LocalDate` or `LocalDateTime` decomposition is permitted.
- `:core:common` MUST declare `kotlinx-datetime` as an implementation dependency rather than an API
  dependency, so no library type is exposed through its public surface.
- The architecture exception MUST match the helper's full package path, not only the
  `PlatformAbstractions.kt` file name, and the guarded production scope MUST include `:core:*`,
  `:feature:*` and `:shared`.
- Tests pin ordinary calendar subtraction, time-of-day preservation, the synthetic 2120 leap-day
  clamp and the Unix-epoch clamp.

## Verification

- Common tests execute the helper on Android host and `iosSimulatorArm64`.
- Architecture fixtures reject calendar types outside the bounded D-81 exception, including in
  `:shared` production code.
- `architectureCheck` verifies the real source tree and `contractCheck` verifies the decision and
  ADR mirrors.

## References

- `docs/SPECIFICATION.md §6` R-1
- `docs/CONTRACTS.md §2`, `§5`, `§13`
- ADR-0078 / D-77
- `docs/BACKLOG.md` E1-06
