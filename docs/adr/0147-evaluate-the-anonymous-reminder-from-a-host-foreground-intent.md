# ADR-0147 / D-146 - Evaluate the Anonymous Reminder From a Host Foreground Intent

## Status

Accepted

## Context

`docs/CONTRACTS.md §11.3` restricts the evaluation of the reminder schedule to app launch and
foreground return, and `docs/SPECIFICATION.md §3.2` keeps operating-system notifications and
schedulers out of the MVP. Shared code cannot observe a foreground transition on its own: only the
hosts know it, through the Android activity lifecycle and the SwiftUI scene phase.

The shared graph already receives platform facts through injected abstractions
(`ConnectivityObserver`, `AppClock`), so a lifecycle abstraction would be the consistent-looking
choice. It is not free: `AppGraphDependencies` has a canonical parameter order fixed by
`docs/CONTRACTS.md §11.6`, adding a member changes that order, the `AppProviders` mirror, the
`:shared:testing` factory and both composition roots.

## Options Considered

| Option | Benefits | Costs / Risks |
|--------|----------|---------------|
| **`SessionStateHolder.evaluateAnonymousReminder()` called by each host (accepted)** | Uses the existing intent mechanism of `docs/CONTRACTS.md §14`; no graph dependency changes; each host keeps its own lifecycle idiom; the trigger is visible where the lifecycle is. | Two hosts must remember to call it, and a host that forgets shows no reminder. |
| A new `AppLifecycleObserver` in `AppGraphDependencies` | The graph observes foreground transitions without host cooperation. | Changes the canonical parameter order, the `AppProviders` mirror, `testAppGraphDependencies` and both composition roots, for one event that is already an intent-shaped fact. |
| Evaluate on every auth-state emission | No host work at all. | An anonymous session emits once, so a returning owner would never be evaluated again; it would also fire on transitions that are not foreground returns. |

## Decision

`SessionStateHolder` exposes `evaluateAnonymousReminder()`. It is guarded so that only an anonymous
session with a provider creation timestamp is evaluated, only one evaluation runs at a time, and a
closed holder does nothing.

Android calls it from an `ON_START` observer on the hosting activity, which covers launch and every
foreground return. iOS calls it when the SwiftUI scene phase becomes `active`. Neither host adds a
scheduler, an alarm or a user notification, and no background work is started.

## Consequences

### Positive

- `AppGraphDependencies` keeps the canonical shape and parameter order of `docs/CONTRACTS.md §11.6`.
- The trigger reads as what it is on each platform, next to the lifecycle it comes from.
- The evaluation is directly testable from shared tests by calling the intent.

### Negative

- The contract lives in two host files, so a new host would have to implement it again.
- The exported Objective-C surface gains one method.

### Constraints Introduced

- Evaluation MUST NOT be triggered by a scheduler, an alarm, a background task or an
  operating-system notification.
- A host MUST call `evaluateAnonymousReminder()` on launch and on every foreground return, and
  MUST NOT call it on any other event.
- The intent MUST stay a no-op for `LOCAL_OWNER`, signed-out and permanent sessions.

## Verification

- `shared/.../AnonymousReminderSessionTest.kt` proves the intent publishes for an anonymous
  session and is a no-op for permanent and signed-out sessions.
- `androidApp/.../MainActivity.kt` registers the `ON_START` observer through `OnForegroundReturn`,
  and `iosApp/carAppApp.swift` calls the model on the active scene phase; neither file adds a
  scheduler or a notification permission.
- `contractCheck` and the architecture checks keep the `AppGraphDependencies` parameter order
  unchanged, which is what this decision preserves.

## References

- `docs/CONTRACTS.md` §11.3, §11.6, §14
- `docs/SPECIFICATION.md` §3.2
- `docs/adr/0063-anonymous-sign-in-benefit-reminders.md` (`D-62`)
