# ADR-0223 / D-222 - Format Numbers and Currency with Platform Locale Formatters

## Status

Accepted

The owner selected option A on 2026-10-16.

## Context

Both hosts hard-code `.` as the decimal separator when they format scaled values
(`formatScaled` in `androidApp/src/main/java/com/ruizurraca/carapp/FuelEntryScreens.kt` and in
`iosApp/ScaledFormatting.swift`), so Spanish shows `7.24` instead of `7,24`, and currency codes are
appended as literals. `docs/CONTRACTS.md §14` already makes formatting platform-side: "Numbers and
dates reach the UI as raw scaled values; formatting is platform-side."

## Options Considered

| Option | Benefits | Costs / Risks |
|--------|----------|---------------|
| A. In `E4-02`, platform formatters (`NumberFormat` on Android, `NumberFormatter` on iOS) for decimals and currency | Spanish and English are complete; the `§2` arithmetic is untouched. | Host unit tests for the separator and the currency placement in both locales. |
| B. A separate story | Smaller `E4-02`. | `E4-02` could not claim complete Spanish localization. |
| C. Keep `.` in both locales | No work. | Spanish with `7.24` is not complete localization. Rejected. |

## Decision

The selected option is **A**. The hosts format decimals and currency amounts for display with the
platform formatters of the device locale. The exact integer arithmetic of `docs/CONTRACTS.md §2`
and its rounding are unchanged; only the presentation of the already-computed scaled value changes.

## Consequences

### Positive

- Numbers and amounts read naturally in both languages.

### Negative

- Display strings now depend on the device locale, so host tests pin the locale explicitly.

### Constraints Introduced

- No formatter result is ever parsed back into a stored value.

## Verification

- Host unit tests on both platforms assert the decimal separator and the currency placement for
  `es-ES` and `en`.

## References

- `docs/DECISION_BOARD.md` (decision ID `D-222`)
- `docs/CONTRACTS.md §2`, `§14`; `docs/SPECIFICATION.md §11` (Localization)
- `docs/BACKLOG.md` (`E4-02`)
