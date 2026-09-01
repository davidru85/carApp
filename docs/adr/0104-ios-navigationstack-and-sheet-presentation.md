# ADR-0104 / D-103 - iOS NavigationStack and Sheet Presentation

## Status

Accepted

Selected by the owner on 2026-09-01 for story E1-09.

## Context

The product design defines a hierarchical flow for vehicles and refuelings: Vehicle List (Screen 01)
leads to Vehicle Detail (Screen 04), while Vehicle creation/editing (Screen 03) and Fuel Entry
creation/editing (Screen 05) represent modal forms that create or update resources and return to the
underlying view upon dismissal. SwiftUI provides `NavigationStack` with typed destination routing
introduced in iOS 16, as well as modal presentation modifiers such as `.sheet`.

## Options Considered

| Option | Benefits | Costs / Risks |
|--------|----------|---------------|
| `NavigationStack` with typed `VehicleDetailRoute` for list -> detail; modal `.sheet` presentation for Vehicle Form and Fuel Form | Follows iOS Human Interface Guidelines; forms are presented modally with clear cancellation/save actions; typed routing guarantees compile-time navigation safety. | Requires coordinating sheet dismissal on save completion. |
| Push all screens onto `NavigationStack` without sheets | Uniform navigation paradigm. | Violates iOS HIG: forms feel like drill-down pages rather than task-oriented modal dialogs. |
| Custom coordinator pattern with UIKit navigation | Full low-level navigation control. | Excessive complexity and boilerplate for four screens in a SwiftUI application. |

## Decision

The primary screen hierarchy uses a `NavigationStack` at the application root (`VehicleListView`)
with typed routing:
- `VehicleListView` -> `VehicleDetailView`: pushed onto `NavigationStack` via `NavigationLink(value: VehicleDetailRoute(...))`.
- Vehicle creation / editing: presented as a modal `.sheet` embedding its own `NavigationStack` with explicit `cancel` and `save` actions.
- Fuel Entry creation / editing: presented as a modal `.sheet` embedding its own `NavigationStack` with explicit `cancel` and `save` actions.

## Consequences

### Positive

- Standard iOS modal UX for data entry with natural dismiss affordances.
- Typed navigation routes prevent invalid navigation parameters.
- Sheets isolate form lifecycles and trigger list re-fetching on dismissal.

### Negative

- Requires managing `@State` presentation bindings (`isCreatingVehicle`, `editingVehicle`, `isCreatingFuelEntry`, `activeFuelEntryId`).

### Constraints Introduced

- Vehicle Form and Fuel Entry Form MUST be presented via `.sheet`.
- List-to-detail navigation MUST use `NavigationStack` with typed value destinations.

## Verification

- `VehicleAndFuelFlowUITests` verifies the presentation, save, and dismissal of both vehicle and fuel entry sheets, confirming return to the parent view with updated data.

## References

- ADR-0008 / D-7
- `docs/SPECIFICATION.md §7`
- `docs/BACKLOG.md` E1-09
