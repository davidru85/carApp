# ADR-0234 / D-233 - Accept the Account Replacement Write Window as a Residual Risk

## Status

Accepted

The owner selected option A on 2026-10-23, in the PR #91 review round 2.

## Context

When the owner confirms the credential collision of `docs/CONTRACTS.md §11.3`,
`AccountConversionCoordinator.confirm` captures the local snapshot first, then issues the orphan-cleanup
ticket, signs into the existing permanent account and replaces the remote data; the local rebuild then
deletes every local row and restores the snapshot. A vehicle or fuel entry written between the snapshot
capture and the local rebuild is therefore lost, although the replacement itself succeeds. The host
shows replacement progress only inside Settings, and the shared session state stops reporting `isBusy`
at the session switch, so a host cannot block writes for the whole window without a shared change.
`D-228` accepted two interruption hazards of the same protocol; this window is a third, on a successful
replacement.

## Options Considered

| Option | Benefits | Costs / Risks |
|--------|----------|---------------|
| A. Accept it as a residual risk in `docs/SECURITY.md` and make its fix an `E2-10` acceptance criterion | Keeps `E4-05` host-only, as `D-228` decided; the fix lands with the other replacement hazards. | The window stays open until `E2-10` merges. |
| B. Fix it in `E4-05` with a root-level blocking progress screen and a shared busy state | Closes it now. | Shared and host changes in the gated authentication area inside a bundle under review. |
| C. Leave it unrecorded | None. | Violates `AGENTS.md` (residual risks are documented). Rejected. |

## Decision

The selected option is **A**. `docs/SECURITY.md` records the window as an accepted residual risk, and
`E2-10` gains an acceptance criterion that closes it.

## Consequences

### Positive

- The risk is documented and owned.

### Negative

- Until `E2-10` merges, a write made during a confirmed replacement can be lost.

### Constraints Introduced

- No document may describe a confirmed account replacement as lossless for writes made while it runs
  until `E2-10` merges.

## Verification

- `docs/SECURITY.md` and `docs/BACKLOG.md` (`E2-10`) carry the entry and the criterion.

## References

- `docs/DECISION_BOARD.md` (decision ID `D-233`)
- `D-228` (ADR-0229), `D-61`, `D-154`
- `docs/CONTRACTS.md §11.3`; `docs/SECURITY.md`; `docs/BACKLOG.md` (`E2-10`)
