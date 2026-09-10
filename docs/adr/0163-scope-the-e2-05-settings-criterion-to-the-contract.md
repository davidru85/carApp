# ADR-0163 / D-162 - Scope the E2-05 Settings Criterion to the Contract

## Status

Accepted

## Context

E2-05's backlog criterion reads "Account deletion is accessible from settings", while the
implementation and the handoff defer the Settings surface to `E4-01`. The criterion was being
reported as satisfied because the state-holder intents exist, which is not what the sentence says.

## Options Considered

| Option | Benefits | Costs / Risks |
|--------|----------|---------------|
| A. The criterion is the callable Settings-facing contract; the surface is E4-01 | Keeps E2-05 within its declared scope and matches how E2-04 handled the same boundary; the criterion is amended to say so literally | Until E4-01 ships, no real owner can delete an account, so the store-compliance obligation moves to that story |
| B. Require a working host entry point in E2-05 | The criterion is satisfied for real and the app is store-compliant on merge | Expands E2-05 into Android and iOS UI, navigation, localized strings and instrumented tests, and takes work E4-01 already owns |
| C. A debug-only entry point | Allows manual end-to-end validation without committing to a design | Adds build-type-conditional code no story owns, and still leaves the criterion unmet |

## Decision

The selected option is **A**.

`docs/BACKLOG.md` E2-05 states the criterion as the application contract, and names `E4-01` as the
owner of the surface and of the store-compliance obligation that depends on it.

## Consequences

### Positive

- The story's acceptance evidence stops claiming something the code does not do.
- The obligation is assigned rather than lost between two stories.

### Negative

- Account deletion is not reachable by an owner until `E4-01` ships.

### Constraints Introduced

- E2-05 MUST NOT report this criterion as satisfied by a host surface it does not deliver.
- `E4-01` MUST deliver the Settings entry point for both hosts.

## Verification

- The amended criterion in `docs/BACKLOG.md`, and the handoff's acceptance evidence, which names the
  contract rather than a screen.

## References

- `docs/DECISION_BOARD.md` (decision ID `D-162`)
- `docs/SPECIFICATION.md §7 F-5`, `§12`
- `docs/CONTRACTS.md §11.5`, `§20.2`, `§20.10`
- `docs/TECHNICAL_PLAN.md §2`
- `docs/handoff-E2-05.md`
