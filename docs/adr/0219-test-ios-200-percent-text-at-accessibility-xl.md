# ADR-0219 / D-218 - Test the iOS 200% Text Requirement at the AX3 Content Size

## Status

Accepted

The owner selected option A on 2026-10-16.

## Context

`docs/SPECIFICATION.md §11` requires the app to be usable at a system font size up to 200%. On
Android this is `fontScale = 2.0`, tested with `DeviceConfigurationOverride.FontScale(2f)`. iOS uses
Dynamic Type content size categories instead of a scale factor. Relative to the default Body size of
17 pt, AX2 (`UICTContentSizeCategoryAccessibilityL`, 33 pt) is about 194%, AX3
(`UICTContentSizeCategoryAccessibilityXL`, 40 pt) about 235%, and AX5
(`UICTContentSizeCategoryAccessibilityXXXL`, 53 pt) about 312%. No category is exactly 200%.

## Options Considered

| Option | Benefits | Costs / Risks |
|--------|----------|---------------|
| A. AX3, `UICTContentSizeCategoryAccessibilityXL` | The smallest category at or above 200%, so a pass proves the requirement. | Slightly stricter than 200%. |
| B. AX2 | Closest to 200%. | Below 200%, so a pass does not prove the requirement. Rejected. |
| C. AX5 | The largest category. | Much stricter than the requirement; forces layout work beyond it. |

## Decision

The selected option is **A**. An iOS UI test launches the app with the arguments
`-UIPreferredContentSizeCategoryName UICTContentSizeCategoryAccessibilityXL` and asserts that every
primary action of each audited screen can be scrolled to and is hittable.

## Consequences

### Positive

- The iOS evidence is at least as strict as the Android `fontScale = 2.0` evidence.

### Negative

- Layouts must work about 17% above the literal requirement.

### Constraints Introduced

- Fixed font sizes are replaced by Dynamic Type text styles on the audited screens.

## Verification

- The AX3 UI test passes on the simulator for every audited screen.

## References

- `docs/DECISION_BOARD.md` (decision ID `D-218`)
- `docs/SPECIFICATION.md §11` (Accessibility)
- `docs/BACKLOG.md` (`E4-02`)
