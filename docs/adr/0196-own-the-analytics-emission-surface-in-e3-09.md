# ADR-0196 - Own the full analytics emission surface in `E3-09`

## Status

Accepted

## Context

`docs/BACKLOG.md` dimensions `E3-09` as `S` and states five acceptance criteria, all of which are
about the **integration**: the closed `AnalyticsEvent` -> provider mapping, the opt-in gate, the
forbidden-payload rule, the single construction site in `:wiring:firebase`, and the provider-free
fallback. None of them mentions emitting an event from product code.

`docs/CONTRACTS.md §16.1` says something the backlog story does not: collection is enabled only
after the user opts in **from Settings**, `setUserProperties` "is called once on analytics opt-in,
and thereafter on every successful vehicle or fuel-entry create/delete, from the presentation
layer", and "an `E3-09` fixture MUST assert the call cadence". That obligation is assigned to this
story by name, and it cannot be satisfied by a mapper alone.

The repository at intake emitted only six of the thirteen leaves, all inside `:shared`:
`AccountConversionStarted`/`Completed`/`Failed` in `StateHolders.kt` and
`AccountDeletionStarted`/`Completed`/`Failed` in `AccountDepartureFlow.kt`. The other seven —
`OnboardingStarted`, `OnboardingCompleted`, `AnonymousSignInSelected`, `PermanentSignInSelected`,
`VehicleCreated`, `FuelEntryCreated`, `SyncStatusChanged` — were declared and never emitted. There
was also no call site anywhere for `setUserProperties` or `setEnabled`, so even the opt-in switch had
no consumer.

## Options Considered

| Option | Benefits | Costs or risks |
|--------|----------|----------------|
| Option A: `E3-09` owns the integration **and** the emission surface | `§16.1` is fully satisfied; the declared-and-never-emitted leaves stop being dead code; the cadence fixture has a home | Widens an `S` story to `M`/`L`; adds behaviour to `SessionStateHolder` and the repository decorator chain, both already merged and owner-reviewed; needs an emission policy per event |
| Option B: `E3-09` owns only the integration; the emission surface becomes a new story | Keeps the story's stated size; touches no merged flow; diff stays reviewable | Leaves an obligation that `§16.1` attributes to `E3-09` unfulfilled, so it must be reassigned in the normative document, not only in the backlog; seven leaves stay dead until that story runs |
| Option C: `E3-09` emits only the non-UI events and defers the onboarding quartet | Captures the highest-value analytics without touching the welcome flow | `§16.1` is still half-satisfied, with the same reassignment work as Option B and an intermediate diff that is harder to justify |

## Decision

The owner selected **Option A** on 2026-09-29. `E3-09` owns the complete `§16.1` surface: the
Firebase integration, the wiring binding, the event mapping, the opt-in gate, the payload rule, **and**
the emission call sites with the `setUserProperties` cadence.

The call sites are `:shared` orchestration, never feature code. `docs/TECHNICAL_PLAN.md §4` forbids a
feature `presentation` package from reaching `:core:analytics`, and `§16.1` forbids analytics in
domain logic and data persistence logic, so a feature state holder could not emit even if it wanted
to. This is the same constraint `D-155` records for `SessionStateHolder`, and the same shape
`AdoptionNotifyingFuelEntryRepository` already uses to wrap a repository in `:shared`.

## Consequences

### Positive

- The closed hierarchy stops being partly dead: every leaf has an emitter.
- The cadence rule of `§16.1` has an executable fixture in the story that the contract names.
- All emission lives in one module, so the forbidden-payload rule and the "not from domain or data"
  rule are reviewable in one place.

### Negative

- The story is larger than its backlog row claims; the backlog row and this ADR record the increase.
- Behaviour is added to `SessionStateHolder` and to the repository decorator chain, so the review
  surface includes flows that already merged.

### Constraints Introduced

- No analytics call MAY appear under `feature/**`. An architecture or source rule enforces it.
- Emission happens after a use case returns `Ok` **or** `Err`; the failure leaves are emitted from
  presentation-level orchestration, which is what the `§16.1` rewrite settled by `AUDIT-20` allows.
- A delete emits **no** event, because the closed hierarchy declares no `VehicleDeleted` or
  `FuelEntryDeleted` leaf and adding one is a contract change.

## Verification

- `SharedAnalyticsCadenceTest` and `SharedAnalyticsEmissionTest` assert the cadence and the events.
- `AnalyticsContractTest` keeps asserting the hierarchy is closed and `String`-free, so an emitted
  event cannot carry user data.
- The Firebase integration's payload test asserts the emitted parameter key set is closed.

## References

- `docs/DECISION_BOARD.md` (decision ID `D-196`)
- `docs/CONTRACTS.md §16.1`, `§20.9`
- `docs/TECHNICAL_PLAN.md §4`
- `docs/BACKLOG.md` (`E3-09`)
