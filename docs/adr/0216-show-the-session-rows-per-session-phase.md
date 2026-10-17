# ADR-0216 / D-215 - Show the Session Rows per Session Phase

## Status

Accepted

The owner selected option A on 2026-10-16.

## Context

`docs/SPECIFICATION.md §3.1` lists "sign-out or local-data deletion, account deletion" in Settings.
`§7 F-5` offers sign-out only to a permanently authenticated user; an anonymous session has no
sign-out and gets "delete local data" with the same two-step destructive confirmation. `D-155` to
`D-165` and ADR-0163 settle the departure protocol: the anonymous provider-session termination
(`D-156`), the `Confirmation.DeleteLocalData` protocol (`D-158`), the retry (`D-159`) and the
`E4-01` ownership of the surface (`D-162`). None of them decides which rows each phase shows.
`requestDeleteAccount()` already routes a `LOCAL` or `ANONYMOUS` owner to the local-data deletion
with `Confirmation.DeleteLocalData`, and a `PERMANENT` owner to the account deletion with
`Confirmation.DeleteAccount`.

## Options Considered

| Option | Benefits | Costs / Risks |
|--------|----------|---------------|
| A. `PERMANENT` shows "Sign out" and "Delete account"; `LOCAL` and `ANONYMOUS` show one "Delete local data" row that calls `requestDeleteAccount()` | One row per distinct action; matches `F-5` and `D-156`. | Local and anonymous owners see a different row set from permanent owners. |
| B. Show both rows to anonymous owners, mapping to the same intent | Same layout for every phase. | Two rows that do one thing. Rejected. |

## Decision

The selected option is **A**.

- `PERMANENT`: "Sign out" calls `requestSignOut()`, and "Delete account" calls
  `requestDeleteAccount()`, which leads to `Confirmation.DeleteAccount`.
- `LOCAL` and `ANONYMOUS`: one "Delete local data" row calls `requestDeleteAccount()`, which leads
  to `Confirmation.DeleteLocalData` (`D-156`: the identity exists only on this device).
- Every other phase never reaches Settings.

## Consequences

### Positive

- No row duplicates another, and no sign-out is offered where `F-5` forbids it.

### Negative

- The row set depends on the session phase, so each phase needs its own host test.

### Constraints Introduced

- The host derives the rows from `SessionUiState.phase` only; it adds no departure logic.

## Verification

- Android component tests assert the row set for `PERMANENT`, `ANONYMOUS` and `LOCAL`; the iOS UI
  tests cover the anonymous and local paths that a simulator can reach.

## References

- `docs/DECISION_BOARD.md` (decision ID `D-215`)
- `docs/SPECIFICATION.md §3.1`, `§7 F-5`; `docs/CONTRACTS.md §11.5`, `§20.2`, `§20.10`
- `D-156`, `D-158`, `D-159`, `D-162` (ADR-0163)
- `docs/BACKLOG.md` (`E4-01`)
