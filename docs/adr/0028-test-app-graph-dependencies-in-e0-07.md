# ADR-0028 / D-27 - Build `testAppGraphDependencies` in `E0-07`, Not `E0-03`

## Status

Accepted

Accepted by the owner on 2026-08-21.

## Context

`E0-03` required `:core:testing` to expose `testAppGraphDependencies(...)` with every parameter defaulted to a fake. It cannot.

`AppGraphDependencies` (`docs/CONTRACTS.md §11.6`) has 15 members. Four have types owned by modules Phase 0 forbids creating: `DatabaseFactory` (`:core:database`, `E1-01`), `AuthClient` and `TokenProvider` (`:core:auth`, Phase 2) and `RemoteSyncSource` (`:core:sync`, Phase 3). The Phase 0 preamble says those modules "MUST NOT be pulled into Phase 0 early", and `E0-04` implements a rule that fails the build if they appear. `AppGraphDependencies` itself is a `:shared` type built by `E0-07`.

Three of the backlog's own rules therefore could not all hold.

## Options Considered

| Option | Benefits | Costs / Risks |
|--------|----------|---------------|
| Move the criterion to `E0-07` | `E0-07` is the first story in which `AppGraphDependencies` exists at all. No rule is bent and no module is created early. | `E0-03` closes with one criterion relocated. |
| Let Phase 0 create interface-only `:core:database`, `:core:auth` and `:core:sync` | The factory would exist sooner. | Contradicts the Phase 0 preamble and the `E0-04` rule that enforces it, and leaves three stub modules standing for months. |
| Build a partial factory with the 11 available parameters | Something exists immediately. | Breaks the `§11.6` requirement of the same parameter count and order, makes `contract-check` assertion 13 permanently red, and would be rewritten anyway. |

## Decision

`testAppGraphDependencies(...)` is built by `E0-07`, in the same story that declares `AppGraphDependencies`. The criterion is removed from `E0-03` and added to `E0-07`.

## Consequences

### Positive

- The Phase 0 module set stays enforceable with no exception.
- The factory is written once, against the complete member list.

### Negative

- `contract-check` assertion 13 reports `PENDING` until `E0-07` lands.

### Constraints Introduced

- Adding a member to `AppGraphDependencies` still requires updating the factory in the same change, preserving parameter order (`§11.6`).

## Verification

- `contract-check` assertion 13 turns from `PENDING` to `PASS` when `E0-07` lands.

## References

- `docs/DECISION_BOARD.md` (`D-27`)
- `docs/CONTRACTS.md §11.6`
- `docs/handoff-E0-03.md`
