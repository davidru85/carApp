# ADR-0270 / D-269 - Let the Leak Check Wait Out a Bounded Framework Retention

## Status

Accepted

On 2026-11-06 the owner told the agent to fix the failing check rather than postpone it. The agent selected
the mechanism, and the owner's gated review of pull request #96 reviews it. Amends `D-256` (ADR-0257) for how
long the leak check waits.

## Context

`D-256` wraps every Compose instrumented test in `DetectLeaksAfterTestSuccess`. LeakCanary's default
`AndroidDetectLeaksInterceptor` dumps the heap once an object has stayed retained for 5 seconds.

`AccessibilityFlowTest.fuelEntryListWithIndicatorsPassesAccessibilityChecks` then failed
`android-instrumented-tests` with a `NoLeakAssertionFailedError`, signature
`0deaf2c5b3220cbfbb07a298157ac2a688e8c24b`, in two CI runs:

| Run | Head | Message due after the heap dump | `watchDurationMillis` |
|-----|------|---------------------------------|-----------------------|
| `42120336218` | `3943868d` | 2,861 ms | 5,159 |
| `42130986110` | `b67c5b15` | 3,021 ms | 5,145 |

The path runs from the main `MessageQueue` through a delayed `Message` to
`com.android.internal.jank.FrameTracker`, then `InteractionJankMonitor$Configuration.mContext`, `DecorContext`
and `PhoneWindow` to the destroyed `MainActivity`. No other application object is on it. The message was due
about 8 seconds after the activity was destroyed, so the retention ends about 3 seconds after LeakCanary looks.

That is 2 failures in the 10 CI runs since the leak check landed, on a required check. The test passed nine
times out of nine on a local API 36 emulator (six alone, three with its class), so no local run reproduces it.

## Options Considered

| Option | Benefits | Costs / Risks |
|--------|----------|---------------|
| A. Give LeakCanary's own interceptor a 15-second retained delay | One line through a public LeakCanary API; a deterministic fixture can prove it; survives a rename of the hidden framework class; no wait in a test that leaves no retained object | An application retention that ends within 15 seconds no longer fails; a real leak takes 10 seconds longer to report |
| B. Classify the `InteractionJankMonitor$Configuration.mContext` path as a library leak with a `LibraryLeakReferenceMatcher` | LeakCanary's documented tool for framework leaks; no added wait | No deterministic fixture is possible, because a test cannot build the hidden class; it silently stops matching if the class changes; it would keep passing if the framework ever retained the activity for good |
| C. Keep the check as it is and re-run on this signature | No code | A required check stays red in about 1 run in 5, which teaches the reflex `AGENTS.md` warns against |
| D. Change the one failing test | Smallest diff | The other 23 Compose test classes can hit the same retention |

## Decision

The selected option is **A**.

- `leakDetecting` installs `AndroidDetectLeaksAssert` with `AndroidDetectLeaksInterceptor(retainedDelayMillis
  = 15_000)`. 15 seconds is nearly twice the 8 seconds observed.
- An object still retained after that delay fails the test exactly as before, through the same heap analysis
  and the same reporter.
- `LeakDetectionTest` runs the real rule chain over two fixtures: an activity held by a delayed main-thread
  message for 8 seconds after its destruction passes, and an activity retained by a static field fails.
- No dependency changes.
- `E5-09` keeps its identifier and is delivered by pull request #96, the `E4-B2` record closure, which carries
  no other story. `E1-18` inside pull request #73 is the precedent.

## Consequences

### Positive

- `android-instrumented-tests` stops failing on this retention, without a class-specific exception.
- The check still verifies that the retention ends, so a framework or application reference that never lets go
  still fails.

### Negative

- An application retention that ends within 15 seconds no longer fails the leak check. The state-holder
  cancellation tests of `D-256` still cover the holder scopes.
- A real leak takes 10 seconds longer to fail, in that one failing test only.
- The fix is proven on a fixture of the same shape, not on the real `FrameTracker` message, because that
  failure does not reproduce locally. The two due times above are the evidence that 15 seconds is enough.

### Constraints Introduced

- The retained delay lives in one named constant in `androidApp/src/androidTest/.../LeakDetection.kt`.
- Raising it needs a new decision: a longer delay widens what the check tolerates.
- `LeakDetectionTest` keeps both fixtures, so that neither direction of the check can break unnoticed.

## Verification

- `LeakDetectionTest` in the `android-instrumented-tests` check. Its first fixture fails without the delay.
- `docs/handoffs/E5-09.md` carries the failing and passing output.

## References

- `docs/DECISION_BOARD.md` (decision ID `D-269`); `D-256` (ADR-0257); `D-263` (ADR-0264)
- `docs/BACKLOG.md` §`E5-09`; `docs/handoffs/E5-09.md`
