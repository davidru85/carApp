# ADR-0198 - Firebase Analytics event names, boolean parameters and the failure policy

## Status

Accepted

## Context

`docs/CONTRACTS.md §16.1` fixes the events and their parameter *kinds* — "enum, boolean or
bucketed-integer parameters" — but not the wire names the provider receives, whether a boolean is
sent as a boolean or as a string, or what happens when the provider fails. `E3-09` has to choose all
three, and the choices are observable in Firebase and in production behaviour.

The provider behaviour was checked against the pinned dependency rather than assumed. GitLive
`firebase-analytics` 2.6.0 on Android converts the parameter map into an `android.os.Bundle`,
handling exactly `String`, `Int`, `Long`, `Double` and `Boolean` and dropping any other value type
silently; on Apple it passes the map to `FIRAnalytics.logEventWithName`, which accepts the Swift
number and boolean types. A boolean is therefore representable on both platforms without
stringifying it.

## Options Considered

| Option | Benefits | Costs or risks |
|--------|----------|----------------|
| Option A: `snake_case` event and parameter names; booleans sent as booleans; a provider failure swallowed and classified into a closed code | Names read naturally in the Firebase console and stay stable identifiers; types match `§20.9`; the product path cannot break on a metrics failure | A new leaf needs a name decision; a swallowed failure is invisible unless the sink is wired |
| Option B: reuse the Kotlin enum names verbatim as event names (`VehicleCreated`) | Zero naming decisions; the mapping is a rename | Firebase's console mixes conventions with the platform's automatic events; renaming later would split the data |
| Option C: stringify every parameter, including booleans, for a single value type | One value type to reason about | Contradicts `§20.9`'s "bucket-level booleans", loses the provider's boolean typing and makes numeric filtering impossible |
| Option D: let a provider failure propagate | Failures are loud | `§16.1` declares three non-suspending `Unit` methods with no error channel, so it would be an unhandled exception on a product path for a feature that only observes |

## Decision

The selected options are **A** for the names, **A** for the boolean typing and **A** with a typed
sink for the failure policy.

- Event names and parameter keys are `snake_case`: `onboarding_started`, `fuel_entry_created`,
  `sync_status_changed`, `permanent_sign_in_selected`, `account_deletion_failed`, and so on; the
  parameter keys are `provider`, `is_full_tank`, `had_notes`, `status`, `reason`,
  `vehicle_count_bucket` and `entry_count_bucket`.
- Enum parameters travel as the Kotlin enum's `name` (`GOOGLE`, `PENDING`, `NETWORK`), which
  `§20.9` already fixes as the canonical spelling.
- `is_full_tank` and `had_notes` travel as booleans, matching `§20.9`'s wording.
- A provider failure is caught, converted to the closed `AnalyticsProviderError` code
  (`ANALYTICS.PROVIDER_FAILED`) in `:core:analytics`, and handed to an injected sink. It never
  propagates, and `CancellationException` is rethrown rather than classified.
- The tracker is injectable into `firebaseAppProviders`. That is what makes the binding assertable
  without a Firebase runtime and lets a build supply a non-collecting tracker.

## Consequences

### Positive

- The names are stable identifiers, so a future rename of a Kotlin leaf does not corrupt the data
  already collected.
- The parameter key set is a closed set, so the forbidden-payload rule is enforced by the emitted
  shape and not only by the type system.
- A metrics failure cannot break a product flow, which is what `D-10` means by best-effort metrics.
- The `setEnabled` flag is honoured at the provider, not only internally, so the provider's own
  buffering is off while the owner has not opted in.

### Negative

- A swallowed provider failure is only observable if the sink is wired; the app graph does not wire
  one yet, and the code is therefore reported only in tests. Wiring it to `CrashReporter` would
  require a `CrashReporter` dependency the integration does not have.
- `snake_case` names are a project convention that a future analytics story must follow; a divergent
  name would be visible only in the Firebase console.

### Constraints Introduced

- Adding, renaming or removing a leaf MUST update the mapping in the same change; the mapping's
  exhaustive `when` with no `else` makes the omission a compile error.
- A parameter value MUST be an enum `name` or a `Boolean`. Adding another value type is a contract
  change, because it would need a new `§20.9` parameter kind.
- The provider failure path MUST classify rather than rethrow, and MUST rethrow `CancellationException`.

## Verification

- `FirebaseAnalyticsTrackerTest.everyEventLeafMapsToAnEventNameAndParameterSet` pins all thirteen
  mappings, written as an exhaustive `when`, so a leaf change stops the test compiling.
- `FirebaseAnalyticsTrackerTest.noParameterValueIsDerivedFromForbiddenPayload` pins the closed key
  set and the allowed value types across every leaf.
- `FirebaseAnalyticsFailureTest` proves a throwing provider does not propagate from any entry point
  and is classified into the closed code.
- `FirebaseAnalyticsOptInTest` proves a fresh install starts disabled at the provider.

## References

- `docs/DECISION_BOARD.md` (decision IDs `D-198`, `D-199`)
- `docs/CONTRACTS.md §16.1`, `§20.9`
- `docs/adr/0011-firebase-analytics.md` (`D-10`)
