# ADR-0220 / D-219 - Map AppError Copy per User-Actionable Outcome

## Status

Accepted

The owner selected option B on 2026-10-16.

## Context

`E4-02` requires Spanish and English strings for every `AppError` and `ConsumptionInvalidReason`
mapping. `AppError` declares 46 leaf codes (`core/common/src/commonMain/kotlin/com/ruizurraca/carapp/core/common/AppError.kt`).
Both hosts map `UiMessage.code` with a fallback: 7 of the 11 `AUTH` codes, all 7 `SYNC` codes, both
`SECURITY` codes, `WARNING.PENDING_SYNC`, `UNEXPECTED` and two `CONFIRMATION` codes fall to the
generic "Something went wrong", and every `REMOTE` code reads "backup could not be restored".

## Options Considered

| Option | Benefits | Costs / Risks |
|--------|----------|---------------|
| A. One distinct string per leaf | Maximal precision. | 46 strings in two languages on two hosts, many near-duplicates. |
| B. Distinct copy per user-actionable outcome, with a documented and tested table that assigns every leaf code a key; codes share a key only where the table says so | Every code is explicit, and the user sees what they can do about it. | The table must be designed and maintained. |
| C. Keep today's grouping and add only a completeness test | Least work. | Keeps messages that mislead the user. Rejected. |

## Decision

The selected option is **B**. Each host keeps one explicit mapping from every `AppError` leaf code,
and from every `CONFIRMATION.*` and `INFO.*` message code, to a string key: Android
`internal fun uiMessageStringResource(code: String): Int` in
`androidApp/src/main/java/com/ruizurraca/carapp/UiMessageStrings.kt`, and iOS
`func uiMessageKey(for code: String) -> String`, which `localizedUiMessage(for:)` calls. Codes that
lead the user to the same action share one key, and only where the mapping says so. Every key has a
Spanish and an English value. `docs/handoffs/E4-02.md` lists the outcome groups with their codes and
keys.

## Consequences

### Positive

- No code falls silently to a generic or wrong message.

### Negative

- Adding an `AppError` leaf requires a mapping entry on both hosts, enforced by `D-220`.

### Constraints Introduced

- A shared key is allowed only where the mapping assigns it explicitly.

## Verification

- Host unit tests assert the key of every code on both hosts, and that each key resolves to a value
  different from the key in both the `en` and `es` resources.

## References

- `docs/DECISION_BOARD.md` (decision ID `D-219`)
- `docs/CONTRACTS.md §6`, `§14`, `§20.2`, `§20.6`
- `D-220` (ADR-0221)
- `docs/BACKLOG.md` (`E4-02`)
