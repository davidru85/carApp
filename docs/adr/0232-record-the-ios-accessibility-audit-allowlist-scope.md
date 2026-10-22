# ADR-0232 / D-231 - Record the iOS Accessibility-Audit Allowlist Scope

## Status

Accepted

The owner approved the exceptions this ADR records on 2026-10-17, during `E4-02`: the system-renderer
findings and, after review round R5, the per-alert-screen and analytics-row entries. They were written
only in `docs/handoffs/E4-02.md` until the PR #91 review round 2 recorded them here on 2026-10-23.

## Context

`D-217` (ADR-0218) ignores an audit issue only through an allowlist entry that names the element, the
issue type and the reason. `D-223` (ADR-0224) fixes every contrast failure the audits measure. The
`E4-02` audit then reported findings on elements that SwiftUI renders and the app cannot style: the
AuthenticationServices button, `Form` row chrome and `TextField` labels, system alerts (title, body and
buttons, drawn in their own window and exposing no identifiers), navigation-bar items and, in dark
appearance, system-drawn colour pairs. The owner approved documented exceptions for them on
2026-10-17; after the R5 fixtures added four alert screens and the conditional analytics row, the owner
approved one entry per alert screen and two for the analytics row.

## Options Considered

| Option | Benefits | Costs / Risks |
|--------|----------|---------------|
| A. Record the approved exceptions as a scoped amendment of `D-217` and `D-223` | The audits stay on every screen; each exception keeps a stated reason; the scope is explicit. | A per-screen alert entry also covers any later issue of the same type on that alert. |
| B. Replace system alerts and `Form` rows with custom views the app can style | No exception. | Large host rework outside `E4-02`; custom alerts lose system accessibility behaviour. Rejected. |
| C. Stop auditing the affected screens | No exception list. | Loses regression protection. Rejected. |

## Decision

The selected option is **A**. An allowlist entry may (1) name an element by identifier or by label;
(2) for a SwiftUI `alert`, which exposes no identifiers, name only the audited screen and the issue
type, covering that alert's chrome on that screen; (3) cover a contrast finding on a system-rendered
element whose colours the app cannot set. Every entry keeps its stated reason, and the complete list
stays in `iosApp/UITests/AccessibilityUITestSupport.swift` and `docs/handoffs/E4-02.md`. A contrast
finding on an element whose colour the app can set is fixed, never allowlisted.

## Consequences

### Positive

- The owner-approved exceptions are bound by a decision and its mirrors, not by handoff prose.
- The round-2 review removed the `settings_delete_local_data` contrast entry, because the app sets that
  label's colour.

### Negative

- A per-screen alert entry hides any further issue of the same type on that system alert.

### Constraints Introduced

- No screen-wide entry for a screen that is not a system alert.
- Every new entry names its reason, is listed in `docs/handoffs/E4-02.md` and needs the owner's approval.

## Verification

- `AccessibilityAuditUITests` and `LargeContentSizeUITests` pass with the allowlist as recorded.

## References

- `docs/DECISION_BOARD.md` (decision ID `D-231`)
- `D-217` (ADR-0218), `D-223` (ADR-0224), `D-225` (ADR-0226)
- `docs/handoffs/E4-02.md`
