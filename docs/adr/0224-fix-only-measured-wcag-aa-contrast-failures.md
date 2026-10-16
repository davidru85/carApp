# ADR-0224 / D-223 - Fix Only Measured WCAG AA Contrast Failures

## Status

Accepted

The owner selected option A on 2026-10-16.

## Context

`docs/SPECIFICATION.md §11` requires WCAG AA contrast. No contrast audit has been run
(`docs/DESIGN.md §6`), and the colour palette of the design assets is not applied to the current
themes. The design assets are non-normative (`docs/DESIGN.md §7`), and no story owns theming.

## Options Considered

| Option | Benefits | Costs / Risks |
|--------|----------|---------------|
| A. Fix only the WCAG AA failures that the `E4-02` audits measure on the current themes | Meets the requirement with the smallest change. | The app does not adopt the design palette. |
| B. Apply the design palette | Visual alignment with the designs. | Theming work that no story owns; a large extension of `E4-02`. Rejected. |

## Decision

The selected option is **A**. Each contrast failure reported by the Android accessibility checks
(`D-216`) or the iOS audit (`D-217`) is fixed in the current theme, and `docs/handoffs/E4-02.md`
lists every failure and its fix. `docs/DESIGN.md`'s accessibility note records that the audit has
been run.

## Consequences

### Positive

- WCAG AA contrast is evidenced on every audited screen.

### Negative

- The themes stay visually different from the design assets.

### Constraints Introduced

- A palette change needs its own story.

## Verification

- The audits report no contrast failure on the final head.

## References

- `docs/DECISION_BOARD.md` (decision ID `D-223`)
- `docs/SPECIFICATION.md §11` (Accessibility); `docs/DESIGN.md §6`, `§7`
- `D-216` (ADR-0217), `D-217` (ADR-0218)
- `docs/BACKLOG.md` (`E4-02`)
