# ADR-0127 / D-126 - Observe Real Platform Connectivity at the Host Composition Boundary

## Status

Accepted

Taken while implementing `E2-06` on 2026-09-06, in the owner's second review round of pull request
#55. The review found that `E2-06`'s "adoption is triggered automatically when connectivity returns"
criterion could not be satisfied by the shipped app, because production had no connectivity
observation at all.

## Context

`ConnectivityObserver` has existed since `E0-03` and is injected through `AppGraphDependencies`.
Every consumer reads it: `VehicleSliceRuntime` gates its push and pull on it, `docs/CONTRACTS.md
§9.2` forbids starting a sync cycle while it is `false`, and `§11.2` makes returning connectivity
the trigger for anonymous UID acquisition.

Nothing ever implemented it. `:wiring:firebase` supplied `MutableStateFlow(true)` - a value that is
never observed, never changes, and is always optimistic. Tests injected `FakeConnectivityObserver`
and passed, which is exactly why the gap survived four stories: the contract was exercised
everywhere except in the app that ships.

For `E2-06` that is not a cosmetic gap. A device that starts offline reaches `LOCAL_OWNER`, and the
`§11.2` retry is supposed to fire when the network comes back. With a constant `true` there is no
edge to fire on, and the stub also lies in the other direction: `onLocalOwnerWriteCommitted()` and
`VehicleSliceRuntime` believe they are online while the device is in a tunnel.

No existing decision assigns this implementation elsewhere. `E3-08` completes `:wiring:firebase` but
names no provider; `D-55` stages exported *state holders*, not platform abstractions; `D-88` and
`D-95` stage sync status only. The gap is unowned, and `E2-06` is the story whose acceptance
criterion it blocks.

## Options Considered

| Option | Benefits | Costs / Risks |
|--------|----------|---------------|
| Native observers injected at each host composition boundary (Selected) | Reuses the exact `D-108` shape already accepted for `LocaleProvider`, so there is one rule for platform providers rather than two; platform APIs stay at the host edges and common code stays provider-free; no new library, both APIs are first-party platform ones | Two small platform implementations to maintain; the observer's own behaviour needs a seam to be testable off-device |
| Implement inside `:wiring:firebase` source sets | One place | Puts Android and Darwin platform APIs inside the Firebase wiring module, which `D-108` rejected for the same reason: the provider-decoupling check exists to keep that module about Firebase |
| Defer to `E3-08` | No work now | `E3-08` is a Phase 3 story. `E2-06` would ship claiming an automatic trigger that cannot fire in the shipped app, and the sync cycle's `§9.2` connectivity guard would stay inert until then |
| Add a connectivity library | Less platform code | Requires an owner decision for a library that is not `Accepted`, for behaviour both platforms expose directly |

## Decision

Each host composition boundary constructs a real `ConnectivityObserver` and passes it to
`firebaseAppProviders(...)`, exactly as `D-108` does for `LocaleProvider`:

- **Android** — `AndroidConnectivityObserver.fromSystemService(context)` registers a default-network
  callback on `ConnectivityManager` and publishes `NET_CAPABILITY_INTERNET` **and**
  `NET_CAPABILITY_VALIDATED`. The manifest declares `ACCESS_NETWORK_STATE`.
- **iOS** — `IosConnectivityObserver.fromNetworkPathMonitor()` starts an `NWPathMonitor` on a global
  background queue and publishes `nw_path_status_satisfied`.

Both take their platform registration as a constructor parameter, so the observer's own behaviour -
the initial value, and every later transition reaching the contract - is exercisable without
platform runtime behaviour. The iOS source lives in its own composition-owned directory and is
reused into `:shared` `iosTest`, the `D-109` topology, so it runs on the canonical
`iosSimulatorArm64Test` route rather than in the `D-75`-excluded `:composition:ios` suite.

The staged default in `:wiring:firebase` changes from always-online to always-offline. A default
that claims a network nobody observed makes every consumer act on a fact that was never established;
one that reports nothing is the safe direction, because each consumer already treats offline as
"do not attempt" and stays retryable.

An executable guard requires both hosts to inject their observer, the manifest permission to exist,
the always-online stub to stay gone, and the iOS source-reuse route to remain wired.

## Consequences

### Positive

- The `§11.2` retry, the `§9.2` sync-cycle guard and the `VehicleSliceRuntime` push and pull gates
  all read a fact about the device instead of a constant.
- One rule covers platform providers: `D-108` for locale, this for connectivity, same shape.
- No new dependency; both APIs ship with the platforms.

### Negative

- Two platform implementations exist whose glue - the callback registration itself - is covered by
  the app running rather than by a unit test.
- The Android observer requires a manifest permission the app did not previously need. It is a
  normal-protection permission and is not user-visible.

### Constraints Introduced

- A host composition boundary MUST inject a real `ConnectivityObserver`. The staged default is for
  tests and for the staged provider graph only.
- The staged default MUST NOT report online. A default that claims connectivity is a false fact, not
  a conservative one.
- Platform connectivity APIs MUST NOT appear outside the host composition boundaries, exactly as
  `D-108` requires for locale.

## Verification

- `AndroidConnectivityObserverTest` in the canonical `:androidApp:testDebugUnitTest` route and
  `IosConnectivityObserverTest` on the canonical `iosSimulatorArm64Test` route both pin the initial
  value and every later transition.
- `IosCompositionContractTest.bothHostsInjectRealPlatformConnectivityIntoTheProviderGraph` fails if
  either host stops injecting, if the manifest permission disappears, if the always-online stub
  returns, or if the iOS source-reuse route is dropped.
- `provider-decoupling` still passes with the Firebase provider registry excluded, so no platform
  API leaked into common code.
- The regenerated Objective-C header matches the committed golden: none of this is exported.

## References

- `docs/DECISION_BOARD.md` (`D-126`)
- `docs/CONTRACTS.md` sections 9.2, 11.2 and 20.3
- [ADR-0109](0109-inject-native-locale-providers-at-host-boundaries.md) (`D-108`)
- [ADR-0125](0125-gate-anonymous-retry-on-adoptable-data.md) (`D-124`)
