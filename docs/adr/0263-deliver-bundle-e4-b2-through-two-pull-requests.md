# ADR-0263 / D-262 - Deliver Bundle E4-B2 Through Two Pull Requests

## Status

Accepted

The owner selected option A on 2026-10-30. Amends `D-209` (ADR-0210).

## Context

`D-209` (ADR-0210) delivers Phase 4 as two bundles, each through one branch and one pull request, and
fixes `E4-B2` as `E4-04`, then `E4-03`, on `story/E4-B2-release-readiness`. Pull request #94 carried
`E4-04` and the non-normative `E4-03` and `E4-10` plan (`docs/plans/E4-03-and-E4-10.md`). The owner
merged it on 2026-10-30 (merge commit `bf6c8e21`) before `E4-03` started, and GitHub deleted the remote
branch. `E4-03` can therefore no longer reach `main` through the bundle's single pull request, while
`AGENTS.md` §Scope Discipline, `docs/CONTRIBUTING.md` and `docs/BACKLOG.md` all repeat the
one-pull-request rule.

## Options Considered

| Option | Benefits | Costs / Risks |
|--------|----------|---------------|
| A. Deliver `E4-B2` through two pull requests from the same branch: pull request #94 for `E4-04`, and a second one for `E4-03` and the bundle closure | The bundle's stories, order, branch and live checkpoint are unchanged; the second pull request is reviewed like any bundle pull request | One more decision; the one-pull-request sentence of four documents gains an exception |
| B. Take `E4-03` out of the bundle and deliver it as a standalone story on a new branch | The one-pull-request rule stays literally true for `E4-B2` | Changes the composition of `D-209`; needs a separate record closure for `E4-B2` now; `E4-03` loses the bundle checkpoint |
| C. Open the second pull request without recording a decision | No documentation change | Violates `AGENTS.md` §Technical Rules, which gives every decision an ID, an ADR and four mirror rows |

## Decision

The selected option is **A**.

- `E4-B2` is delivered through two pull requests: pull request #94 (`E4-04`, merged on 2026-10-30) and
  a second pull request with `E4-03` and the bundle closure records.
- The branch `story/E4-B2-release-readiness` is recreated from `main` at `bf6c8e21`, a fast-forward of
  the merged branch with no force push, and carries the second pull request.
- `docs/handoffs/E4-B2.md` stays the single live checkpoint until the second pull request merges.
- The stories, their order and the gates of `D-209` are unchanged. No other bundle is affected.

## Consequences

### Positive

- `E4-03` keeps the bundle's records and checkpoint, and the merge history shows exactly what each
  review accepted.

### Negative

- `E4-B2` takes two gated reviews and two CI cycles instead of one.

### Constraints Introduced

- The second `E4-B2` pull request contains only `E4-03`, the `E4-B2` closure records and the records
  of the pull request #94 merge.
- No other bundle may be split across pull requests without a new decision.

## Verification

- `./gradlew contractCheck` reports the decision in all four mirrors with the status `Accepted`.
- `git merge-base --is-ancestor dd616e03 bf6c8e21` exits 0: the recreated branch continues the merged
  history.

## References

- `docs/DECISION_BOARD.md` (decision ID `D-262`); `D-209` (ADR-0210)
- `AGENTS.md` §Scope Discipline; `docs/CONTRIBUTING.md`; `docs/BACKLOG.md`
- `docs/handoffs/E4-B2.md`
