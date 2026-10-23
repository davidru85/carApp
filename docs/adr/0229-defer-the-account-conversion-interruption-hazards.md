# ADR-0229 / D-228 - Defer the Account Conversion Interruption Hazards to E2-10

## Status

Accepted

The owner selected option A on 2026-10-16.

Extended by `D-233` (ADR-0234, 2026-10-23): the write window of a successful confirmed replacement is a
third accepted residual risk, and `E2-10` closes it with the two hazards below.

## Context

`E2-04` delivered the account-conversion flow in shared code, but no host invoked it until `E4-05`.
While the host surface was specified, two interruption paths of the destructive replacement
protocol (`docs/CONTRACTS.md §11.3`, `D-151` to `D-154`) became reachable for the first time. Both
were found by reading the code; no test demonstrates them yet.

1. A confirmed replacement that fails, or whose process dies, before the session switch leaves the
   durable marker in `SNAPSHOT_CAPTURED` while the session is still anonymous
   (`AccountConversionCoordinator.confirm`). Every sync preflight then fails, because
   `resumePending()` refuses an anonymous session, so the anonymous owner's backup stays failed
   until the owner links again. The retry reuses the snapshot captured at the first attempt, as
   `§11.3` requires, and `replaceLocalSnapshot` clears every local row before rebuilding from it,
   so rows written between the failure and the retry are lost. Only a local-data deletion clears
   the marker.
2. After the session switch the local rows still belong to the anonymous UID until the local
   rebuild. If the resume fails, for example after an offline relaunch, the recovery window closes,
   the permanent owner's list resolves as empty and F-1 offers mandatory first-vehicle creation; a
   vehicle created there is deleted when the resumed `replaceLocalSnapshot` runs.

## Options Considered

| Option | Benefits | Costs / Risks |
|--------|----------|---------------|
| A. Record both as accepted residual risks in `docs/SECURITY.md`, create the follow-up story `E2-10`, and keep `E4-05` host-only | `E4-05` stays an M host story; the fix gets its own ready check, contract amendment and gated review. | Until the follow-up lands, an owner whose collision replacement fails mid-way can lose backup or rows written in that window. |
| B. Widen `E4-05` to fix both now | The hazards never reach a user. | Amends `docs/CONTRACTS.md §11.3`, touches the gated authentication and synchronization topics, needs shared TDD, and grows `E4-05` from M to L. |
| C. Block `E4-05` until another story fixes them | No reachable hazard. | The reminders keep promising permanent sign-in with no way to do it; the work is the same as B in a different order. Rejected. |

## Decision

The selected option is **A**. `E4-05` makes no shared change. Both hazards are accepted residual
risks in `docs/SECURITY.md` and are listed under Risks or Follow-ups in `docs/handoffs/E4-05.md`.
`E2-10` is added to `docs/BACKLOG.md` §Follow-Ups Outside the Phase Milestones, in a new
subsection "Defects found after Phase 2 closed": it is a correctness follow-up, not a
store-submission prerequisite. The `E4-05` owner-run acceptance only observes and records
hazard 2 and creates no data while doing so.

## Consequences

### Positive

- The conversion surface ships in `E4-B1` without re-opening the `E2-04` protocol.

### Negative

- The hazards stay reachable until `E2-10` merges.

### Constraints Introduced

- `E2-10` is a correctness follow-up outside the `D-209` bundles; it does not block Phase 4.
- Its fix needs its own decision for the `docs/CONTRACTS.md §11.3` amendment.
- No document may describe the account replacement as lossless across an interruption until
  `E2-10` merges.

## Verification

- `docs/SECURITY.md` lists both hazards under Accepted Residual Risks with `D-228`.
- `docs/BACKLOG.md` contains `E2-10` with acceptance criteria that close both hazards, and its
  Story Index row.
- `docs/handoffs/E4-05.md` lists both hazards under Risks or Follow-ups.

## References

- `docs/DECISION_BOARD.md` (decision ID `D-228`)
- `docs/CONTRACTS.md §11.3`, `§11.4`; `docs/SECURITY.md` (Accepted Residual Risks)
- `D-151` to `D-154` (ADR-0152 to ADR-0155), `D-227`
- `docs/handoffs/E2-04.md`, `docs/handoffs/E4-05.md`
- `docs/BACKLOG.md` (`E2-04`, `E4-05`, `E2-10`)