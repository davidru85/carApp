# ADR-0215 / D-214 - Render Departure Progress and Failure at the Root Route

## Status

Accepted

The owner selected option A on 2026-10-16.

## Context

A confirmed deletion sets `SessionPhase.DELETING`, and a success ends in `UNKNOWN`. A failure keeps
the phase while `SessionStateHolder` publishes the typed `message` and `pendingDepartureRetry`
(`D-159`), and `retryDeparture()` repeats only the unfinished part. Both hosts route `UNKNOWN` and
`DELETING` to a bare progress indicator (`resolveOnboardingDestination` in
`androidApp/src/main/java/com/ruizurraca/carapp/AndroidOnboarding.kt` and in
`iosApp/Onboarding.swift`), and the Android `NavHost` is unmounted during `DELETING`. A failed local
clear therefore leaves its error and its retry unreachable.

## Options Considered

| Option | Benefits | Costs / Risks |
|--------|----------|---------------|
| A. A host departure screen at the root routing level, shown while the phase is `DELETING` or whenever `pendingDepartureRetry` is not null | Survives the Android `NavHost` unmount; identical on both hosts; host code only. | One new screen per host. |
| B. Keep Settings mounted through `DELETING` and render the failure there | No new screen. | The Android root routing must be restructured so Settings survives `DELETING`, and Settings mixes its rows with a departure state. Rejected. |

## Decision

The selected option is **A**. Android adds
`DepartureScreen(session, onRetry, onDismissMessage, modifier)` in
`androidApp/src/main/java/com/ruizurraca/carapp/DepartureScreen.kt`, and iOS adds `DepartureView` in
`iosApp/DepartureView.swift`. The root routing of both hosts shows it while
`session.phase == SessionPhase.DELETING` or `session.pendingDepartureRetry != null`; it renders the
progress, the typed failure from `session.message` and the `retryDeparture()` action. Shared tests
are added only if the routing input changes; the routing itself is host code.

## Consequences

### Positive

- Every departure failure and its retry are reachable on both hosts.

### Negative

- The root routing of both hosts gains one destination.

### Constraints Introduced

- The departure screen contains no departure logic: it renders `SessionUiState` and calls existing
  intents.

## Verification

- Android component tests over `DepartureScreen` and the routing function; iOS tests over the
  routing function and the view.

## References

- `docs/DECISION_BOARD.md` (decision ID `D-214`)
- `docs/CONTRACTS.md §11.5`, `§20.10`; `D-159`, `D-165`
- `docs/BACKLOG.md` (`E4-01`)
