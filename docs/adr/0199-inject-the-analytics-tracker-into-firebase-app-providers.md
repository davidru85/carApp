# ADR-0199 - Inject the analytics tracker into `firebaseAppProviders`

## Status

Accepted

## Context

`E3-09` creates `:integration:firebase-analytics` and binds its `FirebaseAnalyticsTracker` in
`:wiring:firebase`. `docs/CONTRACTS.md §11.6` and `D-179` make `:wiring:firebase` the only module
that constructs a Firebase implementation, and the backlog criterion "only `:wiring:firebase`
constructs the implementation" has to be assertable. The tracker reaches the Firebase SDK at
construction, because it commands collection off (`D-198`), so a test that builds the providers
cannot construct the real tracker without a Firebase runtime.

Before `E3-09`, the internal `firebaseAppProviders` factory built an inline staged no-op tracker.

This decision was first recorded inside ADR-0198 together with `D-198`. It is recorded here on its
own because `AGENTS.md` requires every decision to have its own ADR. The decision, its options and
its date are unchanged.

## Options Considered

| Option | Benefits | Costs / Risks |
|--------|----------|---------------|
| Option A: `firebaseAppProviders` takes the `AnalyticsTracker` as a parameter; the public overload defaults it to `FirebaseAnalyticsTracker()` and the internal factory defaults it to a single private no-op | The binding is assertable by identity without a Firebase runtime; a build can supply a non-collecting tracker; a graph built without a host collects nothing | Both overloads gain a parameter |
| Option B: construct the implementation inline with no injection point | No new parameter | The binding cannot be asserted without a Firebase runtime, and a test cannot keep the provider out |
| Option C: keep the staged inline no-op beside the new binding | No change to the internal factory | Two no-op trackers in one module, one of them unreachable from the public path |

## Decision

The owner selected **Option A** on 2026-09-29.

## Consequences

### Positive

- `FirebaseAppProvidersTest` asserts that the tracker handed in is the tracker the providers
  expose, so a decorator or a second construction site in `:wiring:firebase` is visible.
- The internal factory's default is the private no-op, so a graph built without a host never
  reaches the provider SDK, which preserves the provider-free posture of `E3-06`.

### Negative

- Both `firebaseAppProviders` overloads carry a parameter that hosts normally leave at its default.

### Constraints Introduced

- `FirebaseAnalyticsTracker()` is constructed only as the default of the public
  `firebaseAppProviders` overload; `docs/CONTRACTS.md §18` assertion 37 enforces it.
- The internal factory's default MUST remain a tracker that collects nothing.

## Verification

- `FirebaseAppProvidersTest.providerFactoryBindsTheGivenAnalyticsTrackerWithoutDecoratingIt`.
- `FirebaseAppProvidersTest.theInternalFactoryDefaultIsNotTheFirebaseTracker`.
- `docs/CONTRACTS.md §18` assertion 37 and its failing fixtures in `AnalyticsIntegrationContractTest`.

## References

- `docs/DECISION_BOARD.md` (decision ID `D-199`)
- `docs/adr/0198-analytics-event-names-booleans-and-failure-policy.md` (`D-198`)
- `docs/CONTRACTS.md §11.6`, `§16.1`, `§18`
- `docs/TECHNICAL_PLAN.md §4`
