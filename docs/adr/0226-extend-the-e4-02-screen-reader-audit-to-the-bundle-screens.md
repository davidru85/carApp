# ADR-0226 / D-225 - Extend the E4-02 Screen-Reader Audit to the Bundle Screens

## Status

Accepted

The owner selected option A on 2026-10-16.

## Context

`docs/SPECIFICATION.md §11` requires TalkBack and VoiceOver support "for F-1 through F-3", and the
`E4-02` acceptance criteria in `docs/BACKLOG.md` named only those flows. Bundle `E4-B1` (`D-209`)
adds three surfaces outside them: the Settings screen of `E4-01`, the host departure screens of
`E4-01` (sign-out, local-data deletion, account deletion and the root-level departure screen of
`D-214`) and the account-conversion surface of `E4-05` (`D-210`). Account deletion is a store
requirement. Auditing more screens than `§11` lists does not contradict it, but it extends the
acceptance scope of `E4-02`, which is the owner's choice.

## Options Considered

| Option | Benefits | Costs / Risks |
|--------|----------|---------------|
| A. Yes: `E4-02` also audits Settings, the departure screens and the `E4-05` surface, and its backlog criteria say so | Every screen the bundle adds is audited in the same pull request; the store-required account deletion is covered. | More accessibility checks, 200% text runs, Spanish runs and iOS audits in `E4-02`, over three more surfaces. |
| B. No: `E4-02` audits F-1 to F-3 only | A smaller `E4-02`. | The account-deletion and conversion surfaces ship without a screen-reader audit. Rejected. |

## Decision

The selected option is **A**. The `E4-02` criterion in `docs/BACKLOG.md` reads: TalkBack and
VoiceOver audits pass for F-1 to F-3, the Settings screen, the departure screens and the `E4-05`
account-conversion surface. The Android accessibility checks (`D-216`), the iOS audit (`D-217`), the
200% text runs (`D-218`) and the Spanish runs cover the same set of screens.
`docs/SPECIFICATION.md §11` is not amended: it states the minimum, and this decision sets the
story's acceptance scope above it.

## Consequences

### Positive

- No screen that bundle `E4-B1` adds ships without a screen-reader audit.

### Negative

- `E4-02` grows by three audited surfaces.

### Constraints Introduced

- A row added to Settings later, such as the `E4-06` privacy-policy link, passes the same checks.

## Verification

- `docs/handoffs/E4-02.md` lists the audit result per screen and per platform for every screen
  named above.

## References

- `docs/DECISION_BOARD.md` (decision ID `D-225`)
- `docs/SPECIFICATION.md §11` (Accessibility)
- `D-209` (ADR-0210), `D-210` (ADR-0211), `D-214` (ADR-0215), `D-216` to `D-218`
- `docs/BACKLOG.md` (`E4-02`, `E4-06`)
