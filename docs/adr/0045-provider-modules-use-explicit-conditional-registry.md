# ADR-0045 - Provider Modules Use an Explicit Conditional Registry

## Status

Accepted

## Context

E3-06 runs before provider directories exist, but its exclusion behavior must still be provable.
Automatic directory discovery would silently admit unreviewed modules, while waiting for each
future story to add ad-hoc conditions would make the current proof vacuous.

## Options Considered

| Option | Benefits | Costs / Risks |
|--------|----------|---------------|
| Explicit provider registry, included only when directories exist | Testable now; no empty projects; provider set stays closed. | A new provider path requires a reviewed registry update. |
| Automatic directory discovery | No registry maintenance. | Silently admits unknown modules and weakens the canonical inventory. |
| Add conditions with each future module | Minimal change today. | E3-06 cannot prove future exclusion now and conditions can drift. |

## Decision

The selected option is an explicit registry of the canonical `:integration:firebase-*` modules and
`:wiring:firebase`. A registered project is included only when its directory exists and provider
exclusion is not active.

## Consequences

### Positive

- Functional fixtures can create representative provider directories and prove both modes.
- Planned modules are not created as empty Gradle projects.
- Unknown provider modules are not silently included.

### Negative

- Extending the provider module inventory requires updating the registry.

### Constraints Introduced

- Registry paths MUST match the canonical inventory in `docs/CONTRACTS.md §1.1`.
- Filesystem-wide provider auto-discovery is forbidden.
- Missing registered directories MUST NOT create Gradle projects.

## Verification

- Functional tests prove an existing registered provider appears normally and disappears in
  excluded mode, while missing registered paths create no project.
- `contractCheck` continues to enforce the canonical module inventory.

## References

- `docs/DECISION_BOARD.md` (decision ID `D-44`)
- `docs/CONTRACTS.md §1.1`
- `docs/TECHNICAL_PLAN.md §3` and `§5`
- `docs/BACKLOG.md` (`E3-06`)
