# ADR-0101 / D-100 - Walking-Skeleton Debug Diagnostics Screen

## Status

Accepted

Selected by the owner on 2026-09-01 for story E1-09.

## Context

During Phase 0, story E0-07 created a minimal walking skeleton UI on iOS (`ContentView.swift`) to
exercise the application graph, anonymous authentication session start, backup restore, and Keychain
persistence. With Phase 1 delivering real user flows (F-2 Vehicle management in E1-07/E1-09 and F-3 Fuel
Entry management in E1-08/E1-09), the production root of the iOS app must become the Vehicle list
screen (`VehicleListView`). However, the E0-07 walking skeleton acceptance evidence and the D-74
Keychain persistence UI test (`CarAppKeychainPersistenceUITests`) must remain executable without
shipping scaffolding UI to end users or complicating production code.

## Options Considered

| Option | Benefits | Costs / Risks |
|--------|----------|---------------|
| Move walking skeleton controls to a `#if DEBUG` diagnostics screen accessible from the navigation bar | Keeps E0-07 acceptance and D-74 Keychain UI tests fully executable; clean production UI with zero test scaffolding shipped in Release builds. | Requires a debug navigation bar item and a dedicated diagnostics view. |
| Delete the walking skeleton UI and its tests | Reduces codebase size and removes Phase 0 scaffolding entirely. | Destroys executable regression evidence for the Phase 0 walking skeleton and Keychain persistence across app restarts. |
| Keep walking skeleton controls on the production Vehicle list screen | No new screens or navigation routes needed. | Pollutes production user interface with internal development and debug actions. |

## Decision

The production root view of the iOS application becomes `VehicleListView`. The walking-skeleton
session and backup controls are moved to `DiagnosticsView`, gated behind `#if DEBUG` and reachable
via a navigation bar leading button in `VehicleListView`. The D-74 Keychain persistence XCUITest
(`CarAppKeychainPersistenceUITests`) drives this route by tapping the diagnostics button, so the
E0-07 walking skeleton acceptance evidence remains executable in development while shipping zero
scaffolding in Release configurations.

## Consequences

### Positive

- Clean, production-ready Vehicle list root screen matching Android parity.
- Phase 0 walking-skeleton evidence and Keychain persistence tests remain fully automated and executable.
- Debug-only affordances are completely excluded from Release builds via `#if DEBUG`.

### Negative

- Requires maintaining the diagnostics view and route alongside the primary user flows.

### Constraints Introduced

- Release builds MUST NOT expose diagnostics or walking skeleton controls.
- `CarAppKeychainPersistenceUITests` MUST navigate through the diagnostics button.

## Verification

- `VehicleAndFuelFlowUITests` exercises the production Vehicle and Fuel Entry flows starting from `VehicleListView`.
- `CarAppKeychainPersistenceUITests` launches the app, taps `diagnostics_button`, and drives the session persistence verification.

## References

- ADR-0075 / D-74
- `docs/BACKLOG.md` E0-07, E1-09
- `docs/CONTRACTS.md §16`
