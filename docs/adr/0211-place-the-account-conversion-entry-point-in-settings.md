# ADR-0211 / D-210 - Place the Account Conversion Entry Point in Settings

## Status

Accepted

The owner selected option B on 2026-10-15.

## Context

`E2-04` delivered anonymous account conversion completely in shared code: `SessionStateHolder`
exposes `startAccountConversion(provider)` and `confirmAccountConversion(confirmation)`, and the
collision flow, the durable operation marker (`D-151`) and the orphan cleanup are tested there. No
host invokes either intent, so an anonymous user cannot link an account, although the `D-62`
reminder banners tell anonymous users to sign in with Google (and Apple on iOS).

Three records place the entry point in Settings:

- `docs/SPECIFICATION.md §7 F-4`: "From settings, the user can link Google or Apple credentials".
- `docs/handoffs/E2-07.md`: "The settings entry point that starts conversion belongs to `E2-04`".
- `docs/handoffs/E2-04.md`: "the Settings surface remains owned by E4-01".

`docs/SPECIFICATION.md §3.1` lists the settings rows "exactly" and as the single source, and omits
conversion. The incomplete record is `§3.1`, not `F-4`. `docs/DESIGN.md §6` also records that the
conversion row and the credential-collision confirmation are undesigned.

## Options Considered

| Option | Benefits | Costs / Risks |
|--------|----------|---------------|
| A. Amend `§3.1` and build the conversion surface inside `E4-01` | One story owns every Settings row. | `E4-01` would grow to L on top of its departure surface and mix Settings with a gated authentication review. Not selected. |
| B. Amend `§3.1` and create the new story `E4-05` inside `E4-B1`, between `E4-01` and `E4-02` | The conversion surface gets its own ready check, design approval and gated authentication review; `E4-02` audits it in the same bundle. | One more story and handoff; `E4-05` depends on the Settings screen that `E4-01` builds. Selected. |
| C. Put the action on the reminder banner | No Settings change. | The banner appears only on elapsed days 1, 3, 8 and 18, and `E2-07` deliberately gave it no action; `F-4` still starts conversion from Settings. Not selected. |
| D. Ship the MVP without a host conversion surface | No work. | `§3.1` keeps conversion in scope, the reminders promise it, and the `E2-04` work would be unreachable. Rejected. |

## Decision

The selected option is **B**.

- `docs/SPECIFICATION.md §3.1` adds an anonymous-only account-link item to the settings list: Google
  on Android; Apple and Google on iOS, per `§7 F-1`. The list keeps the word "exactly" and stays the
  single source. It is the only `§3.1` change of bundle `E4-B1`.
- `E4-05 - Account Conversion Host Surface - M` is added to Phase 4 in `docs/BACKLOG.md` and runs in
  bundle `E4-B1` after `E4-01` and before `E4-02` (`D-209`). It is marked "Human review required",
  because it is under the authentication topic.
- `E4-01`'s criterion "shows exactly the `§3.1` surface" excludes the account-link row, which
  `E4-05` adds to the same screen, in the same way `D-162` split the `E2-05` contract from the
  `E4-01` surface.
- The host starts a conversion with `startAccountConversion(provider)`, runs the existing native
  picker and completes through `completeGoogleSignIn` or `completeAppleSignIn`. It never calls
  `startPermanentSignIn` for a conversion and contains no conversion logic.
- `docs/DESIGN.md §6` names `E4-05` as the owner of the undesigned row and collision confirmation.

## Consequences

### Positive

- Anonymous users can reach the conversion that the reminders promise, without losing data.
- The gated authentication review of the conversion surface stays separate from Settings.

### Negative

- Phase 4 grows by one story.
- The row's behaviour while the session is `LOCAL`, and its design, are settled at the bundle intake
  and recorded at the `E4-05` intake.

### Constraints Introduced

- `docs/SPECIFICATION.md §3.1` remains the only settings list; `README.md`, `docs/DEFINITION.md` and
  `docs/BACKLOG.md` MUST NOT restate a different one.
- No host conversion code calls `startPermanentSignIn`.

## Verification

- `E4-05`'s Android component tests show the row only for an anonymous session and offer Google
  only; an iOS UI test shows the row with Apple and Google for an anonymous session.
- An owner-run manual acceptance against the development project links an anonymous account that
  holds data on both hosts with the UID and data preserved, and covers the collision cancel and
  confirm paths and an interrupted replacement.

## References

- `docs/DECISION_BOARD.md` (decision ID `D-210`)
- `docs/SPECIFICATION.md §3.1`, `§7 F-1`, `§7 F-4`
- `docs/CONTRACTS.md §11.3`, `§20.10`
- `D-162` (ADR-0163), `D-209` (ADR-0210), `D-151` to `D-154`
- `docs/handoffs/E2-04.md`, `docs/handoffs/E2-07.md`
- `docs/DESIGN.md §6`
- `docs/BACKLOG.md` (`E4-01`, `E4-05`)
