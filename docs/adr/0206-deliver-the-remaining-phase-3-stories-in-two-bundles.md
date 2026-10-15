# ADR-0206 / D-205 - Deliver the Remaining Phase 3 Stories in Two Bundles

## Status

Accepted

The owner selected option A on 2026-10-14.

## Context

After `E3-09` merged through pull request #84, six Phase 3 stories remain: `E3-13`, `E3-15`,
`E3-16`, `E3-18`, `E3-20` and `E3-21`. `docs/CONTRIBUTING.md` (Branches and Commits) requires one
story per pull request and asks for a pull request above 40 files to be split, and `AGENTS.md`
assigns work one story at a time. Every recent story spent most of its elapsed time in owner review
rounds and in the ten required CI checks per pushed head, not in implementation.

The six stories fall into two groups by the code they change:

- `E3-13`, `E3-21`, `E3-18` and `E3-20` harden the client synchronization path. All four change
  `:core:sync`, `:core:database` or the Firestore pull boundary, so all four sit on the gated paths
  `core/sync/**` and `core/database/**`, and `:core:database` is a shared-write module that only one
  story at a time may modify.
- `E3-15` and `E3-16` close the two server-side races of the orphan-cleanup authorization in
  `functions/`: issuance in `issueOrphanCleanupTicket` and the purge inside `deleteAccount`.

The owner asked to group the remaining Phase 3 stories into two bundles developed consecutively, to
accelerate delivery.

## Options Considered

| Option | Benefits | Costs / Risks |
|--------|----------|---------------|
| A. One branch and one pull request per bundle; every story keeps its own ready check, handoff, TDD cycle and project-log entry | One gated owner review and one CI cycle per bundle instead of one per story; the shared-write module is changed by one agent in one ordered sequence. | The first bundle's pull request will exceed the 40-file guideline; a finding against one story holds the whole bundle until it is corrected. |
| B. One agent per bundle, still one pull request per story, opened consecutively | No convention changes. | Six reviews and six CI cycles remain; it accelerates only the hand-over between stories. |

## Decision

The selected option is **A**, limited to the two bundles below. It is not a general change to the
one-story-per-pull-request convention.

| Bundle | Stories, in execution order | Branch | Start condition |
|--------|-----------------------------|--------|-----------------|
| `E3-B1` Client synchronization hardening | `E3-13`, `E3-21`, `E3-18`, `E3-20` | `story/E3-B1-client-sync-hardening` | `D-173`, `D-206`, `D-207` and `D-208` accepted (all on 2026-10-14) |
| `E3-B2` Server-side cleanup races | `E3-15`, `E3-16` | `story/E3-B2-server-cleanup-races` | `E3-B1` merged, and `D-149` and `D-150` accepted (both met on 2026-10-15) |

Inside a bundle:

- Every story keeps its own ready check, `docs/handoffs/<STORY>.md`, RED / GREEN / REFACTOR commits
  in the order of `docs/SPECIFICATION.md §11`, acceptance evidence and `docs/PROJECT_LOG.md` entry.
  A story's commits use its own story ID as the Conventional Commits scope; a commit that serves the
  bundle as a whole uses the bundle ID (`E3-B1`, `E3-B2`).
- Stories run one at a time in the declared order, so the `:core:database` shared-write rule holds
  within the bundle. A story starts only after the previous one has reached its final phase.
- `docs/handoffs/E3-B1.md` (or `E3-B2.md`) holds the bundle ready check and the single live
  `In-Progress Checkpoint` required by `D-105`; each story handoff points to it while the story is in
  flight.
- Every decision a story needs is accepted before that story starts, and the decisions known at the
  bundle intake are put to the owner before the first story's RED phase. A decision discovered while a
  bundle is in flight pauses the bundle until the owner decides; it is never taken provisionally.
- The pull request is created after the last story's final phase, so step 4 of the `§11` workflow
  applies to the bundle. Its description carries the template sections per story.
- The 40-file split guideline of `docs/CONTRIBUTING.md` does not apply to these two pull requests.
- The human review gates of the bundle are the union of its stories' gates. The owner merges; agents
  never merge.
- The owner MAY remove a story from a bundle; it then returns to the backlog with its prior status.

## Consequences

### Positive

- Two gated reviews and two CI cycles for six stories.
- `E3-B1` changes the shared-write `:core:database` in one ordered sequence, and the `E3-13`
  single-source change lands before the other stories touch the same statements.

### Negative

- The `E3-B1` pull request is large, and its review covers four stories at once.
- A blocking finding against one story delays the others in the same bundle.

### Constraints Introduced

- `docs/CONTRIBUTING.md` and `AGENTS.md` state the exception and link here.
- No other story may be bundled without a new decision.

## Verification

- `E3-B1` and `E3-B2` each merge through exactly one pull request whose commits carry the story IDs
  of the table above, in the declared order.
- Each story in a bundle has its own handoff and project-log entry when its bundle merges.

## References

- `docs/DECISION_BOARD.md` (decision ID `D-205`)
- `docs/CONTRIBUTING.md` (Branches and Commits)
- `docs/SPECIFICATION.md §11` (TDD commit and push workflow)
- `docs/BACKLOG.md` (`E3-13`, `E3-15`, `E3-16`, `E3-18`, `E3-20`, `E3-21`)
- `AGENTS.md` (Scope Discipline, Continuous Progress Documentation)
