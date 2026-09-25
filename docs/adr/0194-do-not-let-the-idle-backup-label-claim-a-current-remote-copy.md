# ADR-0194 - Do not let the `Idle` backup label claim the remote copy is current

## Status

Accepted

Owner decision taken on 2026-09-25.

## Context

`docs/CONTRACTS.md §9.9` gives the aggregate `SyncStatus.Idle` the meaning "the outbox is empty and no
failure is outstanding". That is a statement about local outstanding work, not about the remote
replica. Two ordinary situations make `Idle` true while the remote copy is absent or stale:

- The owner is the `LOCAL_OWNER` sentinel. `docs/SPECIFICATION.md §9.1` and `§9.1` of the contract say
  nothing is enqueued for remote backup while the owner is that sentinel, so a first offline run
  publishes `Idle` with nothing backed up at all. P2 requires that first launch to work offline, so it
  is the normal first-run state, not an error.
- The local data was edited while the app was closed, or the last push failed for a connectivity code
  and was later resolved locally without a successful push.

The designs draw the chip as `Sincronizado localmente` ("synchronised locally"). The word *locally* is
doing real work there: it asserts the local store is settled, not that the backup is current. A label
such as "backed up" or "up to date" would assert something the aggregate does not carry, and on a fresh
offline install it would be false the moment the owner creates their first vehicle.

## Options Considered

| Option | Benefits | Costs / Risks |
|--------|----------|---------------|
| Option A: the `Idle` label states that nothing is outstanding and never that the remote copy is current | Matches what `§9.9` actually publishes, the design's own wording, and P2's offline first run. No new state, no new `SyncStatus` member, no owner-visible falsehood | The label is less reassuring than "backed up", and read alone it does not tell the owner whether a backup exists |
| Option B: label `Idle` "backed up" | Shortest, most reassuring | False under `LOCAL_OWNER` and after any unresolved remote gap. Publishing it would contradict `§9.1` and P2 on the very first run |
| Option C: add a `SyncStatus` value or a persisted "last successful backup" flag so the label can be exact | The label could then be honest about the remote | Adds a state-machine value or a new persisted field, which `E3-05` is explicitly not authorised to do, and the honest version needs a timestamp and its own copy. `§3.1` asks for a discreet indicator, not a backup history |

## Decision

The selected option is: **Option A**.

The `Idle` visual is labelled as the designs already draw it — "synchronised locally" in English and
"sinpendientes"/"sincronizado localmente" semantics in Spanish, as the platform catalogues word it —
and neither label asserts that a remote copy exists or is current. The other three visuals state
outstanding work (`Pending`), a running cycle (`Syncing`) and a failure (`Failed`), none of which
claims anything about the remote either.

No `SyncStatus` value is added and no persisted field is introduced. `E3-05` renders what the shared
layer publishes.

## Consequences

### Positive

- No owner-visible falsehood: the indicator never claims a backup that `§9.1` may have refused to
  create.
- No representation change. `§9.9`, `§20.10` and the golden header are untouched by this decision.
- The copy matches the design assets on both platforms, so the undrawn-state guidance of
  `docs/DESIGN.md §6` is followed rather than overridden.

### Negative

- An owner reading only the chip cannot tell whether a remote copy exists. `E4-01` owns the settings
  row, which is where a fuller statement would belong if the owner ever wants one.
- A future story that wants an exact backup label needs a decision of its own, because the aggregate
  cannot carry it today. This ADR records why rather than leaving the gap implicit.

### Constraints Introduced

- The `Idle` label MUST NOT assert that the remote copy exists or is current.
- No `SyncStatus` value and no persisted backup flag may be added for a label; a story that needs one
  requires its own decision.
- The indicator MUST state only what `§9.9` publishes.

## Verification

`SyncStatusVisualTest` (Android) and `SyncStatusVisualTests` (iOS) assert that every visual has its own
label, and the iOS test asserts both the English and the Spanish catalogue contain copy for each of the
four visuals, so an untranslated or missing label fails rather than rendering a raw key. Evidence is in
`docs/handoff-E3-05.md`.

## References

- `docs/DECISION_BOARD.md` (decision ID `D-193`)
- `docs/SPECIFICATION.md §3.1`, `§9.1`, P2
- `docs/CONTRACTS.md §9.1`, `§9.9`
- `docs/adr/0193-render-the-backup-status-indicator-on-the-vehicle-list.md`
