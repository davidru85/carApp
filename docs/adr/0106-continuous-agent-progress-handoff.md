# ADR-0106 / D-105 - Continuous Agent Progress Handoff

## Status

Accepted

Selected by the owner on 2026-09-01 during story E1-10.

## Context

Development may continue across multiple AI agents because a session can reach its subscription,
token or context limit before a story is complete. Chat history is not a durable repository
artifact, and a handoff written only at completion leaves an in-flight story ambiguous: a
replacement agent cannot reliably distinguish completed work from unverified work, locate the
latest commit or identify the exact next step.

The project already requires one handoff per story and an append-only project log at completion.
The missing rule is when the handoff becomes authoritative and how often it must reflect current
progress without turning the project log into a stream of temporary status entries.

## Options Considered

| Option | Benefits | Costs / Risks |
|--------|----------|---------------|
| Maintain the story handoff from intake through completion; update it after every material state change and before yielding unfinished work | A replacement agent can recover branch, phase, evidence, failures, decisions and next action from versioned artifacts; minimizes repeated work and unsafe assumptions | Adds small documentation updates during implementation; an abrupt process termination can still occur between two material checkpoints |
| Update the handoff only at RED, GREEN and REFACTOR phase boundaries | Lower documentation frequency; aligns directly with TDD commits | Loses important failures, decisions and partial progress within a long phase; a token limit can arrive before the next boundary |
| Keep the handoff completion-only and rely on chat history, commit messages and the final project log | No extra in-progress documentation work | Chat state may be unavailable to the replacement agent; uncommitted work, failed verification and exact next steps remain ambiguous |

## Decision

The story handoff is a live continuity record from intake through completion. Its
`In-Progress Checkpoint` is updated after every material state change and before unfinished work is
yielded. A checkpoint records:

- date, branch, base, current phase and latest commit;
- push and pull-request status;
- work completed since the previous checkpoint;
- verification evidence and known failures, including their established story owner;
- open decisions, blockers and risks;
- the exact next step.

A replacement agent reads the checkpoint first, confirms it against the working tree and commit
history, and continues from that state. The append-only project log remains completion-focused and
is not updated for every intermediate checkpoint.

## Consequences

### Positive

- In-flight work remains recoverable when the active AI agent changes.
- Test, commit and push phase boundaries are explicit throughout TDD execution.
- Known failures and deferred work retain a named owner instead of being rediscovered.
- The project log remains concise and append-only.

### Negative

- Material implementation checkpoints require an accompanying handoff update.
- The agent must judge whether a state change is material; the closed trigger list in `AGENTS.md`
  limits that ambiguity.

### Constraints Introduced

- Every in-flight story MUST have a handoff created at intake.
- The handoff MUST be updated at the D-105 material checkpoints and before yielding unfinished
  work, pausing for owner input or ending a session.
- A checkpoint MUST contain every field listed by this decision and the handoff template.
- A replacement agent MUST verify the checkpoint against repository state before continuing.
- Intermediate checkpoints MUST NOT be appended to `docs/PROJECT_LOG.md` as if the story were
  complete.

## Verification

- `contractCheck` proves D-105 and ADR status parity across the four decision mirrors.
- The handoff and pull-request templates contain the `In-Progress Checkpoint` field set.
- `AGENTS.md` defines the canonical trigger and continuation rules; `docs/CONTRIBUTING.md` links to
  them without creating a second authority.

## References

- `AGENTS.md` §`Continuous Progress Documentation`
- `docs/templates/agent-handoff.md`
- `docs/CONTRIBUTING.md`
