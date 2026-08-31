# ADR-0099 / D-98 - Publish Kotlin-only Fuel Save Completion

## Status

Accepted

Selected during E1-08 on 2026-08-31 after the API 36 instrumented test exposed a navigation race.

## Context

The Android Fuel Entry form must leave the form only after a successful create or update. The
existing UI state exposes `isSaving` and `message`, but its combined `StateFlow` may conflate the
short saving transition. During confirmed-warning saves, the previous warning may also still be
the first state observed by a newly launched Compose effect. Inferring success from those values is
therefore racy. Adding a saved field to `FuelEntryFormUiState` would change its established Swift
signature.

## Options Considered

| Option | Benefits | Costs / Risks |
|--------|----------|---------------|
| Publish a Kotlin-only completion flow from the holder | Gives Android an exact success event and keeps the Swift ABI unchanged. | Adds one Kotlin presentation API that must remain hidden from Objective-C. |
| Infer completion from `isSaving` and `message` | Adds no holder API. | Conflation and the two-step warning path can miss or misclassify the transition. |
| Add a saved field to `FuelEntryFormUiState` | Mirrors the Vehicle form pattern and is directly observable by both platforms. | Changes the established Fuel Entry Objective-C signature during an ABI-preservation story. |

## Decision

`FuelEntryFormStateHolder.observeSaveCompletions()` returns a Kotlin-only `Flow<Unit>`. The holder
emits once after a successful repository create or update and does not emit for warnings or errors.
The Android form collects this flow for navigation. `@HiddenFromObjC` keeps the method out of the
Shared framework header.

## Consequences

### Positive

- Android navigation follows the actual persistence outcome without timing assumptions.
- Confirmed odometer-warning saves use the same completion path as ordinary saves.
- The exported Fuel form state and holder signatures remain unchanged.

### Negative

- Kotlin hosts must collect a separate event flow alongside form state.
- Future holder refactors must preserve exactly-once successful completion emission.

### Constraints Introduced

- Validation warnings and errors MUST NOT emit completion.
- The completion method MUST remain absent from the Objective-C header.
- Native hosts continue to use the established state-based ABI; E1-09 MUST NOT duplicate Android
  business logic.

## Verification

- Shared holder tests count one event after confirmed successful persistence.
- The API 36 instrumented flow navigates after confirmation and observes the saved list row.
- Generated-versus-golden Objective-C header comparison remains empty.

## References

- ADR-0091 / D-90
- ADR-0098 / D-97
- `docs/CONTRACTS.md §15.3`
- `docs/BACKLOG.md` E1-08
