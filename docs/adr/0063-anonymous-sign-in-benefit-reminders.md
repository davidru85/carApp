# ADR-0063 / D-62 - Use a Fixed Anonymous Sign-In Reminder Timeline

## Status

Accepted

## Context

An unlinked anonymous account is device-bound and becomes eligible for automatic deletion after
30 days. The product needs non-blocking reminders that explain the recovery benefit of linking a
permanent provider without repeatedly prompting an inactive user.

## Options Considered

| Option | Benefits | Costs / Risks |
|--------|----------|---------------|
| Fixed reminders on days 1, 3, 8 and 18 with backlog collapse | Predictable, testable and leaves a 12-day margin before cleanup. | Requires local reminder state and foreground evaluation. |
| Recalculate each due date from the previous display | Gives every reminder its full interval. | Extends the sequence unpredictably and can continue beyond cleanup eligibility. |
| Show every missed reminder on return | Preserves every message. | Produces a burst of repetitive prompts and disregards the user's inactivity. |

## Decision

The schedule is anchored to the Firebase anonymous account creation timestamp. The due elapsed
days are 1, 3, 8 and 18. On launch and foreground return, the app displays only the highest-index
due reminder whose index exceeds the persisted last-shown index; all lower pending reminders are
consumed by that update.

The intervals are held in one configuration constant. Reminder state survives restarts, clears
after permanent-provider sign-in and completes permanently after reminder 4. Reminders are
dismissible, non-blocking and never gate an existing feature.

## Consequences

- A return on day 20 shows only reminder 4.
- Product copy must disclose device-bound recovery and the 30-day cleanup risk.
- The reminder is an authentication-retention notice, not a fuel, maintenance or notification
  scheduling feature.

## Verification

- E2-07 covers 12 hours and days 1, 2, 4, 9, 20 and 31.
- The day-20 assertion emits reminder 4 once and consumes reminders 1 through 3.
- Signing in permanently clears pending reminder state.

## References

- `docs/CONTRACTS.md §11.3`
- `docs/BACKLOG.md` (`E2-07`)
- `D-60`

