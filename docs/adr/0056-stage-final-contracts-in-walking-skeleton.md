# ADR-0056 - Stage Final Contracts in the Walking Skeleton

## Status

Accepted

## Context

E0-07 is the early integration gate and must generate a golden Objective-C header containing the
complete Swift-facing allowlist. It must also create `AppGraphDependencies` and its exact test
factory. Those contracts refer to types and state holders whose complete product behavior belongs
to later Phase 1, Phase 2 and Phase 3 stories.

Narrowing E0-07 to a temporary ABI would contradict its current acceptance criteria and make the
golden header disposable. Moving the gate after all owning stories would remove the early proof of
Kotlin/Native export, native Firebase linking and Android-to-iOS backup/recovery risk.

## Options Considered

| Option | Benefits | Costs / Risks |
|--------|----------|---------------|
| Create final modules and public contracts now, with only the Vehicle slice functional | Preserves final topology and complete ABI proof while keeping later behavior scoped. | Introduces deterministic contract shells before their feature stories. |
| Narrow E0-07 to a temporary walking-skeleton ABI | Makes the immediate implementation smaller. | Requires contract changes, produces a disposable golden header and creates temporary public API. |
| Move E0-07 after feature, auth and sync implementation | Avoids early shells. | Removes the early integration gate and changes phase sequencing again. |

## Decision

E0-07 creates the final module paths and the public contract types required by
`AppGraphDependencies`, `AppGraph`, `SwiftAppGraph` and the complete Swift allowlist. It implements
real product behavior only for the D-39 Vehicle name backup/restore slice. Exported state holders
outside that slice are deterministic contract shells, are not connected to native UI and perform
no remote or local mutation.

Later stories extend these modules and types in place and retain ownership of their complete
behaviors. E0-07 may use removable `internal` adapters to isolate its vertical slice, but it may
not introduce a temporary module, Firestore collection or public walking-skeleton-only API.

## Consequences

### Positive

- The first golden header exercises the complete intended Swift-facing shape.
- Later implementation does not rename modules or replace public construction APIs.
- The walking skeleton remains a narrow functional proof rather than absorbing later use cases.

### Negative

- Some public state-holder classes exist before their product behavior is implemented.
- The E0-07 pull request creates more module scaffolding than its one functional screen consumes.

### Constraints Introduced

- Every non-slice shell MUST be deterministic, safe to construct and close, and must not mutate
  local or remote state.
- Native E0-07 UI MUST consume only the functional Vehicle slice.
- Temporary slice adapters MUST be `internal` and removable without changing the public ABI.
- Later stories MUST complete the existing modules/types rather than create parallel replacements.
- Handoffs for later stories MUST state which D-55 shells were made functional.

## Verification

- The generated Objective-C header contains the complete allowlist and no temporary public type.
- Provider-free graph construction succeeds with `testAppGraphDependencies(...)`.
- Tests prove non-slice state-holder shells perform no mutation.
- Android and iOS use only the functional Vehicle state holder in E0-07.

## References

- `docs/DECISION_BOARD.md` (decision ID `D-55`)
- `docs/CONTRACTS.md §11.6`, `§15.3`, `§20.10`
- `docs/BACKLOG.md` (`E0-07`, `E1-07`, `E1-09`, `E2-01`, `E2-02`, `E3-02`, `E3-03`, `E3-08`)
- `D-27`
- `D-30`
- `D-39`
