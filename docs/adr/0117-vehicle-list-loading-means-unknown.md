# ADR-0117 / D-116 - Vehicle List Loading Means the List Is Unknown

## Status

Accepted

Selected by the owner on 2026-09-04, after the E2-03 review of pull request #54.

## Context

E2-03 needed a gate that stops F-1 from flashing first-vehicle creation before the app knows whether
the owner already has vehicles. Both hosts gated on `VehicleListUiState.isLoading`.

`VehicleListStateHolder` published that field as its refresh flag, so it was also true during any
`refresh()` over an already known list. `docs/CONTRACTS.md §20.10` declares the field but never
defined its meaning, which is what allowed the producing and consuming sides to read it differently.

The observable effect was that the Android "Restore backup" action and the iOS diagnostics refresh
replaced the whole authenticated surface with a waiting indicator, destroying navigation state and
releasing cached state holders. The same trap would fire for any future pull-to-refresh or
connectivity-driven refresh.

## Options Considered

| Option | Benefits | Costs / Risks |
|--------|----------|---------------|
| `isLoading` means the vehicle list is not known yet (Selected) | One definition serves both hosts in the layer that owns presentation logic; no contract shape change, no Swift ABI change | The list screen loses its disabled-while-refreshing affordance for the refresh control |
| Keep publishing the refresh flag and derive "unknown" in each host | No shared change | Duplicates the same rule in Android and iOS, which is the duplication the shared presentation layer exists to prevent |
| Add a second state field that distinguishes unknown from refreshing | Explicit semantics and both affordances retained | Changes `CONTRACTS.md §20.10`, the Swift-facing ABI and the generated header golden for a presentation detail that no current screen needs |

## Decision

`VehicleListUiState.isLoading` means the vehicle list is not known yet. It is true only from the
initial state until the repository emits for the first time; an ordinary `refresh()` never sets it.
The field set of `VehicleListUiState` is unchanged, so the Swift-facing ABI and the generated
Objective-C header golden are unchanged.

The semantics are written into `docs/CONTRACTS.md §20.10` so the ambiguity cannot recur.

Hosts use it to gate F-1 first-run routing (`D-115`) and, while it is true, cover the mounted UI
with a waiting indicator rather than replacing it.

Re-entrancy of `refresh()` remains guarded inside the state holder, so losing the disabled state of
the refresh control cannot produce duplicate refreshes. Whether a future synchronisation UI needs a
separate refreshing signal is deferred to the story that introduces it.

## Consequences

### Positive

- A refresh can no longer unmount navigation or release state holders on either platform.
- Both hosts share one definition of "the vehicle list is not known yet".
- The gate keeps doing what it was introduced for: no first-run flash before the list is resolved.

### Negative

- The Android list refresh control is no longer disabled while a refresh is running.

### Constraints Introduced

- `isLoading` MUST NOT be set by an ordinary refresh.
- Hosts MUST cover, not replace, mounted UI while the list is unknown.
- The cover and the first-run decision MUST read the same observed state. Gating the cover from an
  outer view that observes the same state holder through a second task lets the cover outlive the
  resolved state and block a ready UI, which is what intermittently failed `ios-simulator-build`
  during this work.

## Verification

- A shared state-holder test drives an observable list that has not emitted, asserts the unknown
  state, asserts it resolves on the first emission, and holds a refresh open across a gate to prove
  the refresh never reopens it.
- The existing list tests continue to assert the resolved state after an emission.
- `contractCheck` and the Objective-C golden-header check confirm the unchanged public surface.

## References

- `docs/DECISION_BOARD.md` (`D-116`)
- `docs/CONTRACTS.md` section 20.10
- `docs/SPECIFICATION.md` sections 7 F-1 and 12
- `docs/TECHNICAL_PLAN.md` section 2
- [ADR-0116](0116-mount-onboarding-navigation-once.md) (`D-115`)
- [ADR-0101](0101-walking-skeleton-debug-diagnostics-screen.md) (`D-100`)
