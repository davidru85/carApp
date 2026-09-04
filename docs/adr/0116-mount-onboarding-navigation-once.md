# ADR-0116 / D-115 - Mount the Authenticated Navigation Graph Once

## Status

Accepted

Selected by the owner on 2026-09-04, after the E2-03 review of pull request #54.

## Context

E2-03 introduced an F-1 onboarding gate in front of the existing Vehicle and Fuel Entry navigation.
The Android host derived the mounted graph from the owner's vehicle count and wrapped it in
`key(destination)`, and the iOS host switched its root view on the same value.

That made navigation a function of mutable owner state, with three consequences observed in review:

- Saving the *first* vehicle flipped the destination from first-vehicle creation to the vehicle
  list, which rebuilt the graph and discarded the navigation to the created vehicle detail required
  by `docs/SPECIFICATION.md` F-2. Every later vehicle routed correctly, so the two paths disagreed.
- First-vehicle creation was the start destination, so its back affordance popped the only back
  stack entry and left a blank host with no recovery.
- Any state change that re-entered the waiting branch destroyed the graph, its back stack and the
  cached state holders.

## Options Considered

| Option | Benefits | Costs / Risks |
|--------|----------|---------------|
| One graph mounted once, rooted at the vehicle list, with first-run creation pushed over it (Selected) | Navigation stops depending on owner state; the post-save route to the detail works for the first vehicle exactly as for later ones; back stack exits and holder release stay valid | The empty vehicle list exists beneath the first-run form, and the system back gesture reaches it |
| Freeze the destination on entry and rebuild the back stack after the first save | Keeps the first-run form as the only mounted screen | Needs an explicit post-save stack construction and two different "after save" behaviours, which is more state to keep correct |
| Accept landing on the vehicle list after the first save | No navigation change | Contradicts `SPECIFICATION.md` F-2 and drops the empty-detail invitation to log the first fuel entry, which is the activation step of the flow |

## Decision

The authenticated surface is mounted once and is never rebuilt from owner state.

On Android, `VehicleRoutes.LIST` is always the `NavHost` start destination. First-run creation is a
distinct route, `VehicleRoutes.CREATE_FIRST`, pushed over the list once the vehicle list is known to
be empty (`D-116`). It renders the same form without a back affordance, and saving it routes to the
created vehicle detail with `popUpTo(route) { inclusive = true }`, leaving `LIST -> DETAIL`.

On iOS, `ContentView` mounts `VehicleListView` for both authenticated destinations. First-run
creation is presented over that list without a cancellation control, and the created vehicle is
pushed onto the same `NavigationStack`.

The decision to present first-run creation is taken by a shared-shape pure helper on both hosts:
it waits for the vehicle list to be known, requires it to be empty, and is taken once.

## Consequences

### Positive

- `SPECIFICATION.md` F-2 holds for the first vehicle and for every later one, through one code path.
- The back stack, the detail back control and keyed holder release are correct in the first-run flow.
- Owner state changes no longer dispose navigation or cached state holders.

### Negative

- An empty vehicle list is composed beneath the first-run form.
- The Android system back gesture on the first-run form reaches that empty list rather than leaving
  the application. The owner accepted this over intercepting the system back gesture.

### Constraints Introduced

- The mounted graph MUST NOT be keyed on, or rebuilt from, session phase or vehicle count.
- First-run creation MUST be identified by its own route or presentation flag, never by reading the
  live vehicle count, which stops being zero the moment the vehicle is saved.
- The first-run form MUST NOT expose a back or cancellation affordance.

## Verification

- Android host tests pin the first-run presentation decision, including the unknown-list, non-empty
  and already-presented cases.
- An isolated Compose test proves the form renders no back control without a back target and renders
  one with it.
- An instrumented test starts from cleared application data and proves the first-run form has no
  back control and that saving the first vehicle lands on the created vehicle detail.
- iOS unit tests mirror the presentation decision and pin that first-run creation offers no
  cancellation.

## References

- `docs/DECISION_BOARD.md` (`D-115`)
- `docs/SPECIFICATION.md` sections 7 F-1, 7 F-2 and 12
- `docs/TECHNICAL_PLAN.md` section 2
- [ADR-0117](0117-vehicle-list-loading-means-unknown.md) (`D-116`)
- [ADR-0085](0085-use-compose-navigation-and-instrumented-ui-tests.md) (`D-84`)
- [ADR-0102](0102-ios-deployment-target-and-observableobject-lifecycle.md) (`D-101`)
