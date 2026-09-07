# ADR-0148 / D-147 - Complete a Launch Evaluation When the Session Resolves

## Status

Accepted

## Context

`D-146` (ADR-0147) made `SessionStateHolder.evaluateAnonymousReminder()` the single evaluation
entry point, called by each host on launch and on every foreground return. The implementation reads
`authClient.authState.value` at the moment of the call.

On a cold start that value is `AuthState.Unknown` until the provider finishes restoring the session.
`docs/CONTRACTS.md §11.1` separates *not yet determined* from *signed out* precisely because the two
are not the same condition, but the evaluation treated both as "nothing to evaluate". The launch
evaluation therefore no-opped silently, and a reminder that was already due was not shown until the
owner backgrounded and returned to the app. For the day-18 notice, which exists to warn before the
30-day cleanup of `D-60`, an owner who simply keeps using the app in one session never sees it.

The obvious repairs are worse than the defect. Evaluating on every auth-state emission would make a
restored session an evaluation trigger of its own, which contradicts `§11.3`. Retrying on a timer
would introduce the scheduler that `docs/SPECIFICATION.md §3.2` keeps out of the MVP.

## Options Considered

| Option | Benefits | Costs / Risks |
|--------|----------|---------------|
| **Remember the blocked request and complete it once when the session resolves (accepted)** | The launch evaluation the host already asked for is answered as soon as an answer exists; no new trigger, no scheduler and no new observer, because the auth-state collector already runs; the deferral is one-shot and any non-anonymous resolution consumes it. | One boolean of state in the holder, and a reader must understand that the completion is the original request finishing late rather than a second evaluation. |
| Evaluate on every anonymous `SignedIn` emission | No extra state; trivially simple. | Makes a restored or re-emitted session a trigger, which `§11.3` does not permit, and would re-evaluate on transitions that are not launch or foreground return. |
| Leave the defect and let the next foreground return show the notice | No change at all. | A due reminder is delayed by a whole app session, and an owner who never backgrounds the app can miss the last warning before cleanup eligibility. |

## Decision

`evaluateAnonymousReminder()` distinguishes the two non-evaluable states. A `SignedOut` or permanent
session is nothing to evaluate, as before. `AuthState.Unknown` marks the request as pending instead,
and the existing auth-state collector completes it exactly once when the state resolves to an
anonymous `SignedIn`.

The deferral is bounded by three rules:

- it is **one-shot**: resolving consumes the pending request, so a later session change does not
  re-run it;
- any resolution other than an anonymous `SignedIn` consumes the pending request **without**
  running it;
- an evaluation is never created by a resolution that no host asked for, so a restored session is
  not a trigger.

`close()` drops a pending request. No scheduler, alarm, background task or observer beyond the
`D-146` host intents is introduced.

## Consequences

### Positive

- A reminder that is due at launch is shown during that launch, which is what `§11.3` intends by
  "evaluation runs on app launch".
- The single evaluation entry point of `D-146` is preserved; hosts are unchanged by this decision.
- The behaviour is deterministic and directly testable from shared tests.

### Negative

- `SessionStateHolder` carries one more piece of internal state, and its auth-state collector now
  has a second responsibility beyond publishing the phase.
- A reader who sees the collector calling the evaluation may mistake it for a new trigger; the
  KDoc and this ADR exist to prevent that.

### Constraints Introduced

- The pending evaluation MUST be one-shot and MUST be consumed by any resolution away from
  `AuthState.Unknown`.
- A resolution that arrives with no pending request MUST NOT evaluate anything.
- The deferral MUST NOT be generalised into an observer, a retry loop or a scheduler; launch and
  foreground return remain the only moments a host asks for an evaluation.

## Verification

- `shared/.../AnonymousReminderEvaluationRaceTest.kt` pins the four boundaries: the deferred
  completion, the one-shot rule, the permanent resolution that consumes without running, and the
  resolved session that evaluates nothing when nobody asked.
- `docs/CONTRACTS.md §11.3` states the rule normatively.

## References

- `docs/CONTRACTS.md` §11.1, §11.3
- `docs/adr/0147-evaluate-the-anonymous-reminder-from-a-host-foreground-intent.md` (`D-146`)
- `docs/adr/0063-anonymous-sign-in-benefit-reminders.md` (`D-62`)
