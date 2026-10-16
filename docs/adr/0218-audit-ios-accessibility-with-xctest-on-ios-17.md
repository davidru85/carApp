# ADR-0218 / D-217 - Audit iOS Accessibility with XCTest on iOS 17 or Later

## Status

Accepted

The owner selected option A on 2026-10-16.

## Context

`E4-02` makes VoiceOver usable on F-1 to F-3, Settings, the departure screens and the `E4-05`
conversion surface. iOS has unlabeled icon-only buttons and fixed font sizes, and no accessibility
test. `XCUIApplication.performAccessibilityAudit()` audits the current screen of a UI test for
issues such as missing labels, insufficient contrast, small hit regions and clipped Dynamic Type
text, but it needs an iOS 17 runtime. The deployment target is iOS 16.0 (`iosApp/project.yml`), and
the pinned Xcode is 26.6 (`docs/versions-matrix.md`).

## Options Considered

| Option | Benefits | Costs / Risks |
|--------|----------|---------------|
| A. `performAccessibilityAudit()` per audited screen in `carAppUITests`, guarded by `if #available(iOS 17, *)`, with a minimal documented allowlist of ignored issues, plus a recorded VoiceOver walkthrough | Automated regression protection without raising the deployment target. | The allowlist must be maintained and justified; on an iOS 16 runtime the audit does not run. |
| B. Manual audit only | No test code. | No protection against regressions. Rejected. |

## Decision

The selected option is **A**. For each audited screen a `carAppUITests` test navigates to the screen
and calls `try app.performAccessibilityAudit()` inside `if #available(iOS 17, *)`. An issue is
ignored only through the audit's issue handler and only when it is on the allowlist; each allowlist
entry names the element, the issue type and the reason, in the test and in
`docs/handoffs/E4-02.md`. The VoiceOver walkthrough is recorded with the acceptance method the owner
chose at the bundle intake: the agent's simulator evidence plus an owner-run device walkthrough.

## Consequences

### Positive

- Accessibility regressions on the audited screens fail the iOS UI suite.

### Negative

- The audit is skipped on an iOS 16 runtime; the deployment target stays iOS 16.0.

### Constraints Introduced

- An allowlist entry without a stated reason is not allowed.
- This decision does not raise the deployment target.

## Verification

- The audits pass on the simulator; temporarily removing one label makes the corresponding audit
  fail, and the result is recorded in `docs/handoffs/E4-02.md`.

## References

- `docs/DECISION_BOARD.md` (decision ID `D-217`)
- `docs/SPECIFICATION.md §11` (Accessibility)
- `iosApp/project.yml`, `docs/versions-matrix.md`
- `docs/BACKLOG.md` (`E4-02`)
