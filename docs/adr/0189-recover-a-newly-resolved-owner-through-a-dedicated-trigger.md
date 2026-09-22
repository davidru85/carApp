# ADR-0189 / D-188 - Recover a Newly Resolved Owner Through a Dedicated Trigger

## Status

Accepted

## Context

`docs/SPECIFICATION.md §9.1` promises that a permanent-account backup "can be restored on a new
device". `E3-12` has to prove it. Reconnaissance for that story found two independent defects that
together made the promise unreachable on a clean device.

**First, nothing triggered the recovery.** `docs/CONTRACTS.md §9.8` closed the `SyncTrigger`
inventory at exactly five values - `AppForeground`, `ConnectivityRecovered`, `PostWriteDebounce`,
`PullToRefresh` and `Periodic` - and every one of them names a cause. `SyncEngine.executeCycle`
reads `ownerContext.current` once (`core/sync/src/commonMain/.../SyncEngine.kt:728`) and no code in
`:core:sync` observes `OwnerContext`. The app graph observed the auth state only to resume an
interrupted account conversion (`shared/.../AppGraph.kt:182-186`). A permanent sign-in on a device
whose local database holds nothing for that UID therefore performed no pull: recovery happened only
if an unrelated lifecycle trigger happened to fire.

**Second, an unrecovered empty list looked like a confirmed one.** `VehicleListStateHolder`
observes the owner and, on a transition, clears the list and starts a fresh local observation
(`feature/vehicle/.../VehicleStateHolders.kt:152-164`). On a clean device that observation
succeeds with zero rows, so `isLoading` becomes `false` on an empty list. That is exactly the state
`docs/SPECIFICATION.md` F-1 answers with mandatory first-vehicle creation
(`androidApp/.../AndroidOnboarding.kt:90-94`), which on iOS is non-dismissible
(`iosApp/VehicleListView.swift:148`) and on Android removes the back affordance and swallows the
system back gesture (`MainActivity.kt:378`, `:430`). The owner was placed in a form they could not
leave while their vehicle sat in Firestore.

The two are independent: fixing the trigger alone still leaves the window between the owner
transition and the cycle settling, and that window is what the first-run gate reads.

### The options for the first defect

| Option | Benefits | Costs / Risks |
|--------|----------|---------------|
| **A dedicated `SyncTrigger.OwnerChanged` (accepted)** | The cause becomes explicit and testable; it works for every owner transition rather than only the clean-device case; it does not depend on host lifecycle code, so both hosts behave identically by construction; the existing admission windows still apply. | Widens the closed enum, which is exported to Swift and appears in the golden Objective-C header, so the ABI changes and four mirroring documents must be updated; adds one more entry to the inventory the checks police. |
| Reuse `SyncTrigger.AppForeground` for the owner transition | No enum change and no ABI change; the smallest possible diff. | The trigger name then lies about its cause, which degrades log-based diagnosis and any future admission policy; it blurs the `§9.8` table, whose value is that each row names one cause; a later story needing to distinguish a real foreground return would have to split them anyway. |
| Gate first-run creation on the pull instead of adding a trigger | No trigger and no ABI change; the fix lands where the defect shows. | It does not make recovery happen, only stops the wrong screen from appearing, so the list can stay unresolved for as long as the device is foregrounded and online with no trigger. |

## Decision

`SyncTrigger` gains a sixth value, `OwnerChanged`, and `DefaultAppGraph` requests one cycle whenever
the resolved owner becomes a non-sentinel identity.

The trigger is fired by the graph, which is the only place that observes `OwnerContext.observe()`
and the only layer entitled to know that a session changed
(`docs/SPECIFICATION.md §9.1`: the `SyncController` is the single entry point). It is not a
platform-scheduling trigger, so it never reaches `SyncTriggerAdapter`, and `§20.10`'s ban on firing
it from Swift UI code is enforced by the same existing rule that bans the other platform-owned
triggers.

`LOCAL_OWNER` is deliberately not a cause. A device that has never authenticated has nothing remote
to fetch under its sentinel, `§9.2` refuses a cycle there anyway, and `§11.2` requires first launch
to reach first-vehicle creation while offline.

The second defect is fixed by keeping a list that is empty *because recovery is outstanding*
unresolved. `OwnerRecoveryGate` is raised when the owner changes and lowered when the cycle it
requested has completed, including on failure and on cancellation, and the vehicle list consults it
before publishing `isLoading`. A non-empty list is known regardless and is never held back; an
offline device settles its refused cycle at once, so `SPECIFICATION.md` P2 is unaffected.

## Consequences

### Positive

- New-device recovery is a real behaviour rather than a hope: the sign-in itself fetches the owner's
  data, on both hosts, without either host having to remember a lifecycle call.
- `docs/SPECIFICATION.md §9.1`'s promise becomes executable, and `E3-12` can state it as a tested
  property: two `AppGraph`s over two independent databases sharing one replica.
- The two failure modes are now distinguishable in the UI: "nothing fetched yet" is `isLoading`,
  "fetched and empty" is the confirmed empty list that opens first-run creation.

### Negative

- The exported `SyncTrigger` enum widens, so the golden Objective-C header changes and the Swift
  surface gains one case. The change is additive: no existing case is renamed or reordered.
- The gate introduces a second reason for `isLoading` to be `true`, so `§20.10`'s description of the
  two unknown states becomes three. The reason is stated normatively there.

### Constraints Introduced

- `OwnerChanged` MUST be requested by the app graph only, and MUST NOT be routed through
  `SyncTriggerAdapter` or fired from Swift UI code.
- The gate MUST lower on the cycle's completion, including a failure and a cancellation; a raised
  gate with nothing left to lower it would strand an owner behind an unresolved list.
- `SPECIFICATION.md` P2 holds: on a device whose cycle is refused for connectivity or `LOCAL_OWNER`,
  an empty list MUST still reach first-vehicle creation.

## Verification

- `shared/.../CrossDeviceRecoveryTest.kt` builds two graphs over two in-memory SQLDelight databases
  sharing one `InMemoryRemoteSyncSource`, and asserts both directions of the proof, that an
  anonymous identity's backup path is unreachable from another identity, that recovery reads only
  bounded pages, that a sign-in admits exactly one recovery cycle with no other trigger fired, and
  that a clean device never publishes a known empty list while recovery is outstanding.
- `inMemoryRemoteSyncSource` was added to `:core:testing` because Kotlin Multiplatform cannot consume
  another module's `commonTest` (`D-56`); the previous Firestore-faithful fake was `private` to
  `:core:sync`'s own test source.
- The full non-instrumented command of `AGENTS.md`, `contractCheck`, the architecture checks and the
  Objective-C golden comparison must pass.

## References

- `docs/CONTRACTS.md` §9.1, §9.2, §9.8, §11.2, §20.3, §20.10
- `docs/SPECIFICATION.md` §7 F-1, §9.1, P2
- `docs/TECHNICAL_PLAN.md` §9
- `docs/BACKLOG.md` (`E3-12`)
- `docs/adr/0065-split-anonymous-lifecycle-delivery.md` (`D-64`)
