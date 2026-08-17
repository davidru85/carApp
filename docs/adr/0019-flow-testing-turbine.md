# ADR-0019 / D-17 - Flow Testing with Turbine

## Status

Accepted

Accepted by the owner on 2026-08-17.

## Context

`docs/BACKLOG.md` asks for Flow assertions across shared presentation, repositories and the sync engine, which is painful without a helper. Leaving the helper undecided blocked the `E0-05` quality tooling story and would encourage agents to write ad hoc collection code in each test.

## Options Considered

| Option | Benefits | Costs / Risks |
|--------|----------|---------------|
| Turbine for Flow testing | Concise, well-established, works in KMP common tests. | Must match the pinned coroutines version. |
| Manual Flow collection | No dependency. | Verbose and error-prone; encourages skipping emission assertions. |

## Decision

Use Turbine for Flow testing.

If Turbine turns out to be incompatible with the pinned coroutines version during `E0-06`, fall back to hand-written collection helpers in `:core:testing` and record that in `docs/PROJECT_LOG.md`; this does not require a new ADR because the abstraction is test-only.

## Consequences

### Positive

- Emission assertions are cheap enough that agents actually write them.

### Negative

- One additional test dependency.

### Constraints Introduced

- Hand-written fakes remain the preferred test double; mocking libraries are still not accepted.

## Verification

- `E0-06` validates Turbine against the pinned coroutines version.

## References

- `docs/DECISION_BOARD.md` (`D-17`)
- `docs/versions-matrix.md`
