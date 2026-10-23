# ADR-0235 / D-234 - Route the Departure Hand-over to the Departure Screen

## Status

Accepted

The owner selected option A on 2026-10-23, in the PR #91 review round 3. It extends `D-214`
(ADR-0215), whose routing rule names only `DELETING` and a published `pendingDepartureRetry`.

## Context

After a successful remote departure step, `AccountDepartureFlow.publishRemoteHandover()` publishes
`SessionPhase.UNKNOWN` with `isBusy = true` while the local clear runs, and the local clear cannot be
cancelled. `D-214` routes `DELETING` and a published retry to the root departure screen and states that
the screen is identical on both hosts. Android also routed the hand-over to that screen
(`resolveRootDestination` in `androidApp/src/main/java/com/ruizurraca/carapp/DepartureScreen.kt`, pinned
by `DepartureRouteTest.theHandoverAfterARemoteStepRoutesToTheDepartureScreen`). iOS followed the literal
`D-214` rule, so `requiresDepartureScreen` returned `false` and the root drew the cold-start
`ProgressView()`, which has no accessibility label, for the whole local clear. A sign-out showed the
same unlabelled spinner for its whole tail.

## Options Considered

| Option | Benefits | Costs / Risks |
|--------|----------|---------------|
| A. Both hosts route `UNKNOWN` with `isBusy` to the departure screen | One rule on both hosts; the departure copy and the labelled progress stay on screen until the departure ends; host code only. | One more routing input on iOS. |
| B. Both hosts follow the literal `D-214` rule | No iOS change. | Android loses the departure copy during the hand-over and both hosts draw a generic spinner. |
| C. Keep the divergence as a residual risk | No change. | Breaks the `D-214` parity and the `E4-02` progress announcement on iOS. |

## Decision

The selected option is **A**. The root routing of both hosts shows the departure screen while the
session phase is `DELETING`, while `pendingDepartureRetry` is not null (`D-214`), and while the phase is
`UNKNOWN` and `isBusy` is true. An `UNKNOWN` session that is not busy is the cold start and keeps the
waiting destination. iOS passes `SessionUiState.isBusy` to `requiresDepartureScreen`.

## Consequences

### Positive

- The departure progress, its label and its copy stay visible until the local clear ends, on both hosts.

### Negative

- iOS routing reads one more published value.

### Constraints Introduced

- `isBusy` routes only the `UNKNOWN` hand-over; a busy `LOCAL`, `ANONYMOUS`, `PERMANENT` or
  `SIGNED_OUT` session keeps its `D-214` routing.

## Verification

- Android: `DepartureRouteTest.theHandoverAfterARemoteStepRoutesToTheDepartureScreen` and
  `DepartureRouteTest.anIdleUnknownSessionStillWaits`.
- iOS: `DeparturePresentationTests.testTheHandoverAfterARemoteStepRequiresTheDepartureScreen`,
  `testAnIdleUnknownSessionDoesNotRequireTheDepartureScreen` and
  `testABusySessionOutsideUnknownDoesNotRequireTheDepartureScreen`.

## References

- `docs/DECISION_BOARD.md` (decision ID `D-234`)
- `D-214` (ADR-0215), `D-159`
- `docs/CONTRACTS.md §20.10`; `docs/handoffs/E4-01.md`
