# ADR-0193 - Render the backup status indicator on the vehicle list surface

## Status

Accepted

Owner decision taken on 2026-09-25.

## Context

`docs/SPECIFICATION.md §3.1` requires a "discreet backup status indicator with manual retry" and lists
backup status among the settings rows. The settings screen does not exist yet: `E4-01` owns it, and
`docs/BACKLOG.md` scopes it to "the surface listed in `docs/SPECIFICATION.md §3.1`". `E3-05` therefore
has to put the indicator somewhere that already exists, or build a settings surface and take over
`E4-01`'s scope.

`docs/DESIGN.md §6` records that real-time status updates are not designed, while the same section
says not to treat that as banning a periodic indicator but to derive undrawn states from the design
system instead of inventing a visual language. The vehicle list screen **is** drawn with a status chip
on both platforms: `Sincronizado localmente` with a filled teal dot, on Android screen `02-home.md`
and on iOS screen `02-home.md`. That is the one existing home for a discreet status line.

Two further facts constrain the design:

- `docs/CONTRACTS.md §9.9` makes `Idle` the aggregate with no outstanding work. Under the
  `LOCAL_OWNER` sentinel `§9.1` enqueues nothing for remote backup at all, so `Idle` is also true when
  nothing has ever been backed up. A label that asserts the remote is up to date would be false on a
  first offline run, which `docs/SPECIFICATION.md` P2 requires to work.
- `docs/CONTRACTS.md §20.10` lists `SyncStateHolder.debugLines`, and both hosts already render it in a
  **debug-only** surface (`showDiagnostics = isDebugBuild` on Android, `#if DEBUG` on iOS). A release
  build has no indicator at all today.

## Options Considered

| Option | Benefits | Costs / Risks |
|--------|----------|---------------|
| Option A: the vehicle list screen on both hosts, beside the existing status chip the design already draws | The only product surface that already exists and is already drawn with a status chip. It leaves the settings row to `E4-01`, which owns it, and needs no new navigation. The chip is where an owner looks after logging a refuel, which is exactly when a pending or failed backup matters | The settings row of `§3.1` stays undelivered until `E4-01`. Two render sites, one per host, so the copy exists twice in each language catalogue |
| Option B: build the settings screen now | Delivers the settings row immediately | Takes over `E4-01`'s scope, inflates a size-`S` story, and duplicates the owner of the settings surface |
| Option C: extend the debug-only diagnostics surface | Smallest change, reuses existing code | It is compiled out of release builds, so the requirement would be satisfied only in debug. `§3.1` is a product requirement, not a diagnostics one |

## Decision

The selected option is: **Option A**. The indicator renders on the vehicle list screen of both hosts.

It reads the single relayed `SyncStatus` the shared layer publishes — `SyncStateHolder.state.status` on
iOS, `VehicleListUiState.syncStatus` on Android, both `docs/CONTRACTS.md §14` relays of the one
`SyncController.status` source. The host classifies that resolved value into one of four visuals and
maps each to its own platform strings; it MUST NOT recompute the `§9.9` precedence and MUST NOT derive
`Pending` from connectivity. Manual retry calls the existing `SyncStateHolder.retryFailed()`, and its
failure surfaces through the existing typed `UiMessage` whose `code` is the error code.

The `Idle` label states that nothing is outstanding rather than that the remote copy is current,
because `Idle` does not distinguish the two and `§9.1` makes the second impossible to promise under a
local owner. See `D-193`.

## Consequences

### Positive

- The indicator is present in release builds on both hosts, which is what `§3.1` requires.
- The status chip the design already draws is reused, so no new visual language is invented for an
  undesigned state, which is what `docs/DESIGN.md §6` asks for.
- The host owns no sync policy: it receives one resolved value and classifies it, so the connectivity
  rule exists once, in `:core:sync`, where `§9.9` and the existing `DefaultSyncControllerTest` pin it.

### Negative

- The settings row of `docs/SPECIFICATION.md §3.1` remains undelivered until `E4-01`. `E3-05` does not
  claim it, and the handoff records the split.
- The classification and its copy are implemented once per host. The four-way classification is
  covered by a unit test on the Android host and by a Swift test on iOS, mirroring the `D-183`
  precedent where only the *rule* is shared and the platform mapping is not.

### Constraints Introduced

- The host MUST classify the already-resolved `SyncStatus`; it MUST NOT recompute the `§9.9`
  precedence and MUST NOT derive `Pending` from connectivity.
- The host MUST NOT add a `SyncStatus` value, a second message channel or a new dependency.
- Every user-facing string MUST be a platform resource in both English and Spanish, and `UiState`
  MUST continue to carry no user-facing text.
- Manual retry MUST go through the existing `SyncStateHolder.retryFailed()`.
- The label of `Idle` MUST NOT claim the remote copy is current.

## Verification

`SyncStatusVisualTest` (`:androidApp:testDebugUnitTest`) and `SyncStatusVisualTests` (the iOS unit-test
target) cover the four-way classification, the invariance of the counts inside a status, that a
`Pending` is never classified as `Failed`, that `Syncing` and `Pending` stay distinct, and that every
visual has its own label; the iOS test also asserts that both catalogues carry copy for every visual
and the accessible description `backup_status_description` with one `%@` placeholder, which both hosts
announce as "Backup status: <label>". The instrumented Android suite exercises the rendered chip, its
retry affordance, the mapped retry failure under its own `backup_status_error` tag, distinct from the
vehicle list's `vehicle_error`, and the absence of that failure beside a non-`Failed` status. `SyncStateHolderRetryTest`
covers the manual-retry outcome in both interleavings the aggregate allows: a retry that resolves
after the status has left `Failed` publishes no message, and an older retry that fails after a newer
one succeeded does not overwrite it. Evidence is in `docs/handoff-E3-05.md`.

## References

- `docs/DECISION_BOARD.md` (decision ID `D-192`)
- `docs/SPECIFICATION.md §3.1` and `§9`
- `docs/CONTRACTS.md §9.9`, `§11.6`, `§14` and `§20.10`
- `docs/DESIGN.md §6` and `§7`
- `docs/BACKLOG.md` (`E3-05`, `E4-01`)
