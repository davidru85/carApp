# ADR-0065 / D-64 - Split Anonymous Lifecycle Delivery Across Owning Stories

## Status

Accepted

## Context

Correct anonymous-account lifecycle behavior spans authentication providers, account conversion,
local reminder persistence, backend deletion and cross-device recovery. Folding all of it into
E0-07 would absorb most of Phase 2 and several Phase 3 responsibilities into the opening walking
skeleton gate.

## Options Considered

| Option | Benefits | Costs / Risks |
|--------|----------|---------------|
| Keep E0-07 narrow and assign each behavior to its owning story | Produces reviewable PRs and preserves traceability from decisions to implementation. | Cross-device recovery evidence moves later. |
| Implement the complete lifecycle in E0-07 | Keeps the original cross-device criterion in the early gate. | Creates an oversized PR and implements functionality before its domain and backend owners exist. |

## Decision

E0-07 implements real anonymous authentication and the minimal Vehicle local/remote path without
claiming cross-device anonymous recovery. E2-02 owns permanent providers, E2-04 owns destructive
collision conversion, E2-07 owns reminders, E3-10 owns the reusable deletion service, E3-11 owns
anonymous cleanup entry points and E3-12 owns the Android-to-iOS permanent-account recovery proof.

## Consequences

- E0-07 remains a reviewable integration gate for the highest-risk native and provider boundaries.
- The cross-device test runs at the first point where permanent auth and complete sync coexist.
- E2-04 depends on E3-11 even though their numeric phases differ; dependency readiness, not phase
  numbering, controls execution.

## Verification

- The backlog dependency graph names every new edge.
- No E0-07 acceptance test attempts to move an anonymous credential between devices.
- E3-12 is human-gated and uses one permanent provider identity on clean Android/iOS local data.

## References

- `docs/BACKLOG.md` (`E0-07`, `E2-02`, `E2-04`, `E2-07`, `E3-10`, `E3-11`, `E3-12`)
- `D-55`, `D-60`, `D-61`, `D-62`, `D-63`
