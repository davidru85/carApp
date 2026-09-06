# ADR-0122 / D-121 - Make First-Run Vehicle Creation Mandatory

## Status

Accepted

Selected by the owner on 2026-09-05, in the second E2-03 review round of pull request #54.

## Context

`D-115` removed the back affordance from the Android first-run creation route and the cancellation
control from the iOS first-run sheet, and recorded as an accepted consequence that the Android system
back gesture would reach the empty vehicle list.

Review found that hiding the controls does not make the step mandatory. On Android the system and
predictive back gestures still popped the route; on iOS the sheet was still interactively
dismissible. The affordances were gone, but the flow was not.

The owner reversed the earlier consequence: first-run creation is a mandatory step and the platform
gestures must not escape it either.

## Options Considered

| Option | Benefits | Costs / Risks |
|--------|----------|---------------|
| Consume the gestures for first-run creation only (Selected) | The mandatory step is actually mandatory; later creation and every edit route keep the platform behaviour untouched | Consuming the system back gesture is unusual on Android and must stay strictly scoped to this one route |
| Leave the gestures escaping the form | Nothing to build; platform-idiomatic | Contradicts a mandatory step: the owner reaches an empty list from a form that offers no way out |
| Make every creation mandatory | One rule | Traps the owner in a form they opened deliberately from the list |
| Block the gestures application-wide | Simplest to reason about | Breaks navigation everywhere for one screen's requirement |

## Decision

Android registers `BackHandler(enabled = !offersBackAffordance)` inside the shared creation route, so
only `VehicleRoutes.CREATE_FIRST` consumes back. iOS applies `.interactiveDismissDisabled` to the
first-run presentation only, driven by `VehicleCreationPresentation.isMandatory`.

This supersedes the `D-115` consequence that the Android system back gesture reaches the empty
vehicle list. Everything else in `D-115` stands: the graph is still mounted once with the vehicle
list as its root, and first-run creation is still identified by its own route rather than by the live
vehicle count.

## Consequences

### Positive

- The mandatory step of F-1 behaves as one on both platforms.
- Later creation stays dismissible, so the change is invisible outside first run.

### Negative

- An owner who reaches first-run creation has no way out except creating the vehicle. That is the
  intent, and it is only reachable when the owner genuinely has no vehicles.

### Constraints Introduced

- Back consumption and dismissal blocking MUST stay scoped to first-run creation.
- A UI regression test MUST exercise the real platform gesture on each host, not only the absence of
  the visible control.

## Verification

- An Android instrumented test performs the system back action on the first-run form and asserts the
  form is still displayed and the vehicle list is not. Proved non-vacuous: with the handler disabled
  the same test fails.
- An iOS UI test performs the swipe-to-dismiss gesture on the first-run sheet and asserts it stays
  presented; a second test proves later creation is still dismissible by the same gesture.
- The iOS first-run state is not reproducible from data alone, because UI tests cannot clear the
  application container that the unit-test target also writes to. A Debug-only
  `CARAPP_UI_TEST_FORCE_FIRST_VEHICLE` environment seam presents it deterministically. It mirrors the
  existing `CARAPP_UI_TEST_FORCE_WELCOME` precedent and, unlike it, cannot exist in a Release build.

## References

- `docs/DECISION_BOARD.md` (`D-121`)
- `docs/SPECIFICATION.md` sections 7 F-1, 7 F-2 and 12
- [ADR-0116](0116-mount-onboarding-navigation-once.md) (`D-115`)
- [ADR-0121](0121-resolve-the-vehicle-list-per-owner.md) (`D-120`)
