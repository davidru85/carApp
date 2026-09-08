# ADR-0146 / D-145 - Carry the Anonymous Reminder on a Typed Session State Field

## Status

Accepted

## Context

`docs/CONTRACTS.md §11.3` requires a dismissible, non-blocking notice that never gates a feature.
`SessionUiState` already carries a `message: UiMessage?` channel whose `code` each host maps to its
own string resources, and `UiMessageKind` already has an `INFO` value, so the reminder could travel
on that channel with no new exported member.

That channel is single-valued and already owned by authentication errors: `startAnonymousSignIn`,
`startPermanentSignIn` and `completeGoogleSignIn` all publish through it, and `clearMessage()`
clears whatever is there. A reminder evaluated on a foreground return would silently replace an
error the owner had not read, and an error arriving afterwards would silently remove the notice.
Both directions are wrong, and neither is visible in a test that looks only at the surviving value.

The schedule also produces an index, not just a fact. `docs/CONTRACTS.md §11.3` makes reminder 3
the last one before cleanup eligibility, so the fourth notice must be able to read differently from
the first.

## Options Considered

| Option | Benefits | Costs / Risks |
|--------|----------|---------------|
| **Typed `anonymousReminderIndex: Int?` field with its own dismissal intent (accepted)** | The notice and the error channel are independent, so neither can silently consume the other; the index reaches the host, so the copy can escalate; the state is directly assertable. | Grows the exported `SessionUiState` and the Objective-C golden header; every construction site gains a parameter. |
| Reuse `SessionUiState.message` with an `INFO` code | No new exported member; dismissal already exists through `clearMessage()`. | The reminder and authentication errors overwrite each other; the index has to be smuggled through `UiMessage.id`; dismissing an error would also dismiss the notice. |
| A separate reminder state holder with its own `UiState` | Complete separation of concerns. | The largest Swift-facing surface of the three, a new factory on both graphs and a second observation for one nullable integer. |

## Decision

`SessionUiState` gains `anonymousReminderIndex: Int?`, the zero-based index of the reminder being
offered, and `SessionStateHolder` gains `dismissAnonymousReminder()`, which clears the field
without touching the persisted position: the index was already consumed when it was shown.

The field is independent of `message`, so `clearMessage()` does not dismiss the notice and a
reminder does not clear an authentication error. An auth-state emission that is still the same
anonymous session preserves the field; every other phase drops it.

Each host maps the index to its own escalating body, and every body states both the recovery
benefit and the device-bound 30-day cleanup risk. Android names Google and iOS names Google and
Apple, matching the providers each platform offers in `docs/SPECIFICATION.md §7 F-1`.

## Consequences

### Positive

- A retention notice can never hide an authentication error, and an error can never hide the
  notice.
- The last reminder before cleanup eligibility can read more urgently than the first.
- The state is asserted directly in shared tests instead of through a message code.

### Negative

- The Swift-facing ABI of `SessionUiState` changed, so the committed Objective-C golden header
  changed with it and is a review signal.
- Every `SessionUiState` construction site names one more argument.

### Constraints Introduced

- `anonymousReminderIndex` MUST stay a typed index; the notice text MUST NOT cross the boundary
  (`docs/CONTRACTS.md §14`).
- `dismissAnonymousReminder()` MUST NOT clear the persisted schedule position, and
  `clearMessage()` MUST NOT clear the reminder.
- Every host body MUST state both the permanent-sign-in recovery benefit and the 30-day cleanup
  risk, and MUST name only providers that platform offers.

## Verification

- `shared/.../AnonymousReminderSessionTest.kt` pins publication, dismissal without reopening, and
  that a foreground return with nothing new due keeps the notice already shown.
- `androidApp/.../AnonymousReminderCopyTest.kt` and `iosApp/Tests/AnonymousReminderCopyTests.swift`
  pin that the four scheduled reminders have distinct bodies and that an index outside the
  schedule still resolves.
- `iosApp/Tests/AnonymousReminderCopyTests.swift` asserts the English and Spanish catalogues
  explicitly, so a missing deadline or benefit fails in either language.
- `androidApp/.../AnonymousReminderBannerTest.kt` renders the banner, asserts the dismissal
  callback and asserts the same copy guarantee on Android.
- `objc-header-golden-check` diffs the regenerated header against the committed golden.

## References

- `docs/CONTRACTS.md` §11.3, §14, §15.3, §20.10
- `docs/SPECIFICATION.md` §7 F-1
- `docs/adr/0063-anonymous-sign-in-benefit-reminders.md` (`D-62`)
