# ADR-0184 - Apply the foreground resume threshold in the shared sync state holder

## Status

Accepted

## Context

`docs/CONTRACTS.md §9.8` states the foreground trigger: it fires "on cold start, and on resume after
more than `FOREGROUND_RESUME_THRESHOLD_MS` in background". Until `E3-04` nothing implemented the
second half. A host that resumed after a second in the background — a notification shade pull, a
permission dialog, a phone call — would either request a cycle on every resume or, with no host wiring
at all, never request one. The threshold existed as a constant with no consumer.

The question was where the comparison belongs. Both hosts observe a lifecycle transition and both can
measure elapsed time, so the rule could be implemented twice, once per host, or once.

There is a second question inside the first: a cold start has **no** background duration. The
threshold is a comparison of a duration, but the cold start is a trigger regardless of duration.
Encoding the cold start as a duration — zero, or the process uptime — would either make it a
trigger by accident (zero is not greater than five minutes, so a threshold comparison alone would
*suppress* the cold start) or require a magic value. The trigger's shape has to carry "there was no
background stay" as a distinct case, not as a number.

## Options Considered

| Option | Benefits | Costs / Risks |
|--------|----------|---------------|
| Option A: `SyncStateHolder.onForegroundReturn(backgroundMillis: Long?)` applies the comparison in shared code, and each host observes its own lifecycle and reports how long the app was in the background, or `null` for a cold start | The rule and its boundary case exist once, are tested once, and cannot drift between Android and iOS. `null` models the cold start honestly: it is not a duration, and it is a trigger. Both hosts report one primitive and neither repeats the comparison, which is also what `D-108` and `D-126` did for locale and connectivity | The member joins the exported Swift surface, so `§20.10` and the golden header must declare it. It takes a nullable `Long`, which is the shape that makes the cold start expressible |
| Option B: each host applies the threshold and calls `requestSync(AppForeground)` itself | No new exported member | The rule is duplicated in Swift and Kotlin, its boundary case ("more than", not "at least") is duplicated too, and the two copies can drift with no test that would catch it. The hosts also need a clock abstraction each |
| Option C: treat the cold start as a zero duration and compare only the threshold | Smallest possible surface | A zero duration is **not** greater than the threshold, so a threshold-only comparison suppresses the cold-start trigger that `§9.8` names explicitly. The boundary case would have to be special-cased anyway, with a magic value to mean "no background stay" |

## Decision

The selected option is: **Option A**.

`SyncStateHolder.onForegroundReturn(backgroundMillis: Long?)` fires `SyncTrigger.AppForeground` when
`backgroundMillis` is `null` (a cold start) **or** greater than `FOREGROUND_RESUME_THRESHOLD_MS`. A
resume of exactly the threshold is not a trigger, because `§9.8` requires **more than** the threshold
in the background.

Each host measures with a monotonic source, never the wall clock, so a clock change while backgrounded
cannot produce a negative or wildly wrong duration. The Android host reads `SystemClock.elapsedRealtime`
(`AndroidForegroundDuration`, with the reader injected so its arithmetic is unit-tested on the host);
the iOS host reads `ProcessInfo.processInfo.systemUptime`. A repeated background entry keeps the
earliest moment, and the measurement is cleared once reported, because it describes one transition.

The launch `onAppear` path keeps calling the reminder evaluation directly and reports the cold start
as such; the scene-phase `.active` transition reports the measured duration.

## Consequences

### Positive

- One implementation and one test of the threshold and of the cold start, on the shared surface, so
  the two hosts cannot disagree about "more than five minutes".
- The cold start is a first-class case rather than a sentinel number, which is why the parameter is
  nullable: `null` literally means "there was no background stay", and it is always a trigger.
- Both hosts report a fact (how long was I backgrounded?) and neither carries a policy.

### Negative

- A new member on the exported Swift surface, so `§20.10` and the generated header must move with it.
  The header is regenerated from the framework rather than hand-edited, so a mistake surfaces as a
  header diff rather than as a Swift runtime failure.
- The Android measurement lives in the Android host and the iOS one in the iOS host, so the arithmetic
  is implemented twice. Only the *rule* is shared. The duplication is four lines on each side and the
  Android copy is unit-tested; the iOS copy is exercised by the iOS host build.
- `AndroidForegroundDuration` is `internal` to `:androidApp`, so it is covered by
  `:androidApp:testDebugUnitTest` rather than by a shared test.

### Constraints Introduced

- The foreground threshold comparison MUST live in `SyncStateHolder.onForegroundReturn`; a host MUST
  NOT apply it itself.
- The background duration MUST be measured with a monotonic clock.
- `onForegroundReturn(null)` MUST fire `AppForeground`: a cold start is a trigger with no duration.
- A resume of exactly `FOREGROUND_RESUME_THRESHOLD_MS` MUST NOT fire a trigger.

## Verification

`SyncStateHolderForegroundTest` covers the cold start, a duration above the threshold, a duration
exactly at the threshold and a duration below it. `AndroidForegroundDurationTest` covers the Android
measurement's cold start, elapsed value, single-use clearing and repeated-entry cases.
`contractCheck` assertions 34 and 35 compare the `§20.10` declaration of the new member with both real
surfaces. Evidence is in `docs/handoff-E3-04.md`.

## References

- `docs/DECISION_BOARD.md` (decision ID `D-183`)
- `docs/CONTRACTS.md §9.8` and `§20.10`
- `docs/adr/0182-make-the-sync-trigger-adapter-the-scheduling-port.md`
- `docs/BACKLOG.md` (`E3-04`)
