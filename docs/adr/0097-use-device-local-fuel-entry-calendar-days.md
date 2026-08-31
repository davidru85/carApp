# ADR-0097 / D-96 - Use Device-Local Fuel Entry Calendar Days

## Status

Accepted

Accepted by the owner for E1-08 on 2026-08-31.

## Context

Fuel Entry persistence uses an exact UTC `Instant`, while the native form presents and edits a
calendar day. Converting a selected day at UTC midnight can display the preceding day in the
initial Spanish market during UTC+1 or UTC+2 early-morning use. The conversion remains native UI
logic and does not change `setDateEpochMillis(Long)` or the Swift ABI.

## Options Considered

| Option | Benefits | Costs / Risks |
|--------|----------|---------------|
| Convert the selected `LocalDate` with `atStartOfDay(deviceZone)` and format with the same injected zone | Matches the user's calendar day and handles zone transitions through the date-time library. | The persisted instant varies by device zone as intended. |
| Normalize selected days to UTC midnight and format in UTC | Is simple and globally deterministic. | Displays the wrong local day on the F-3 fast path in positive UTC offsets. |
| Preserve the previous local time-of-day when changing the calendar day | Retains an approximate refuelling time. | Adds ambiguous DST behavior and invents semantics for a date-only control. |

## Decision

Android presents and edits calendar days in the device time zone. A picked `LocalDate` is converted
with `atStartOfDay(zone)` and formatted with that same zone. The zone is injected into testable UI
conversion code; no static current-system-zone lookup exists there. The untouched creation
default remains the exact `AppClock.now()` instant and is not normalized. E1-09 MUST apply the
identical rule on iOS.

## Consequences

### Positive

- The selected and displayed day matches the user's device calendar.
- Tests remain deterministic through explicit zone injection.

### Negative

- The persisted start-of-day instant differs across time zones for the same calendar label.

### Constraints Introduced

- Native UI conversion uses one injected zone for selection and formatting.
- The default exact `now` value remains untouched until the user picks another day.
- E1-09 MUST use the same device-local rule.

## Verification

- Android unit tests cover UTC+1/+2 and a DST transition with an injected zone.
- Instrumented tests verify the displayed default and selected day.

## References

- `docs/SPECIFICATION.md §5.2`, `§7` F-3
- `docs/CONTRACTS.md §2`, `§15.3`, `§20.10`
