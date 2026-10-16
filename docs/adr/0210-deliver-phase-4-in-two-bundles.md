# ADR-0210 / D-209 - Deliver Phase 4 in Two Bundles

## Status

Accepted

The owner selected option A on 2026-10-15.

## Context

Phase 3 closed on 2026-10-15 when bundle `E3-B2` merged through pull request #89, and its record
closure merged through pull request #90. Phase 4 holds `E4-01` (Settings UI), `E4-02`
(accessibility and localization), `E4-03` (performance and reliability hardening) and `E4-04`
(release preparation), plus `E4-05` (account conversion host surface), which the owner created on
the same day through `D-210` (ADR-0211). `docs/CONTRIBUTING.md` (Branches and Commits) requires one
story per pull request and asks for a pull request above 40 files to be split, and `D-205`
(ADR-0206) made an exception only for the two Phase 3 bundles, stating that no other story may be
bundled without a new decision. Under `D-205` six stories took two gated reviews and two CI cycles.

The five Phase 4 stories fall into two groups:

- `E4-01`, `E4-05` and `E4-02` build and harden the product surface. `E4-05` adds its account-link
  row to the Settings screen that `E4-01` builds, and `E4-02` audits every screen for accessibility
  and localization, so both must exist before it runs.
- `E4-04` and `E4-03` prepare the release. `D-54` (ADR-0055) keeps the Firebase configuration
  debug-only and makes release builds fail closed until `E4-04` adds the reviewed production
  configuration. `docs/SPECIFICATION.md §11` measures cold start in a release build, so `E4-03`'s
  release-build measurements are not possible before `E4-04`. Running `E4-04` first also means
  performance is measured on the final release candidate, after Crashlytics and the splash have
  changed cold start.

The owner asked to deliver Phase 4 as two consecutive bundles under the rules of `D-205`.

## Options Considered

| Option | Benefits | Costs / Risks |
|--------|----------|---------------|
| A. Two consecutive bundles, one branch and one pull request each; every story keeps its own ready check, handoff, TDD cycle, commit scope and project-log entry | Two gated owner reviews and two CI cycles instead of five; `E4-02` audits the product surface in the same branch that builds it; `E4-B2` measures performance on the real release candidate. | Each pull request exceeds the 40-file guideline; a finding against one story holds its whole bundle until it is corrected. |
| B. One pull request per story, opened consecutively | No convention change. | Five gated reviews and five CI cycles; `E4-03` still cannot run before `E4-04`, so the backlog order changes anyway. |

## Decision

The selected option is **A**, limited to the two bundles below. It extends the `D-205` exception to
these two bundles only; it is not a general change to the one-story-per-pull-request convention.

| Bundle | Stories, in execution order | Branch | Start condition |
|--------|-----------------------------|--------|-----------------|
| `E4-B1` Product surface | `E4-01` → `E4-05` → `E4-02` | `story/E4-B1-product-surface` | Pull request #90 merged (met on 2026-10-15) |
| `E4-B2` Release readiness | `E4-04` → `E4-03` | `story/E4-B2-release-readiness` | `E4-B1` merged |

`E4-B2` inverts the original backlog order of `E4-03` and `E4-04` with the owner's approval, for the
`D-54` reason above.

Inside a bundle:

- Every story keeps its own ready check, `docs/handoffs/<STORY>.md`, RED / GREEN / REFACTOR commits
  in the order of `docs/SPECIFICATION.md §11`, acceptance evidence and `docs/PROJECT_LOG.md` entry.
  A story's commits use its own story ID as the Conventional Commits scope; a commit that serves the
  bundle as a whole uses the bundle ID (`E4-B1`, `E4-B2`).
- Stories run one at a time in the declared order. A story starts only after the previous one has
  reached its final phase.
- `docs/handoffs/E4-B1.md` (or `E4-B2.md`) holds the bundle ready check and the single live
  `In-Progress Checkpoint` required by `D-105`; each story handoff points to it while the story is
  in flight.
- Every decision a story needs is accepted before that story starts, and the decisions known at the
  bundle intake are put to the owner before the first story's RED phase. A decision discovered while
  a bundle is in flight pauses the bundle until the owner decides; it is never taken provisionally.
- The pull request is created after the last story's final phase, so step 4 of the `§11` workflow
  applies to the bundle. Its description carries the template sections per story.
- The 40-file split guideline of `docs/CONTRIBUTING.md` does not apply to these two pull requests.
- The human review gates of a bundle are the union of its stories' gates. The owner merges; agents
  never merge.
- If the owner adds, removes or splits a story while answering a bundle's intake questions, this
  decision records the final composition; that completes this decision and does not reopen it. A
  story the owner removes returns to the backlog with its prior status.

## Consequences

### Positive

- Two gated reviews and two CI cycles for five stories.
- `E4-02` audits Settings, the departure screens and the conversion surface in the same pull request
  that introduces them.
- `E4-03` measures cold start on the release build that will ship.

### Negative

- Both pull requests are large, and each review covers several stories at once.
- A blocking finding against one story delays the others in the same bundle.

### Constraints Introduced

- `docs/CONTRIBUTING.md` and `AGENTS.md` state the exception and link here.
- No other story may be bundled without a new decision.
- `E4-06`, the postponed in-app privacy-policy link, is not part of either bundle.

## Verification

- `E4-B1` and `E4-B2` each merge through exactly one pull request whose commits carry the story IDs
  of the table above, in the declared order.
- Each story in a bundle has its own handoff and project-log entry when its bundle merges.

## References

- `docs/DECISION_BOARD.md` (decision ID `D-209`)
- `D-205` (ADR-0206), the Phase 3 precedent
- `D-54` (ADR-0055), release builds fail closed until production configuration exists
- `D-210` (ADR-0211), which creates `E4-05`
- `docs/CONTRIBUTING.md` (Branches and Commits)
- `docs/SPECIFICATION.md §11` (Performance; TDD commit and push workflow)
- `docs/BACKLOG.md` (`E4-01` to `E4-05`)
- `AGENTS.md` (Scope Discipline, Continuous Progress Documentation)
