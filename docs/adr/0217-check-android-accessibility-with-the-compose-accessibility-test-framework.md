# ADR-0217 / D-216 - Check Android Accessibility with the Compose Accessibility Test Framework

## Status

Accepted

The owner selected option A on 2026-10-16.

## Context

`E4-02` makes TalkBack usable on F-1 to F-3, Settings, the departure screens and the `E4-05`
conversion surface. Android has no automated accessibility check: one `contentDescription` exists
(`SyncStatusIndicatorView.kt`), and no test asserts headings, labels or touch targets. The
Compose-native route is the BOM-managed `androidx.compose.ui:ui-test-junit4-accessibility` artifact,
which brings Google's Accessibility Test Framework (ATF) and `enableAccessibilityChecks()` for
Compose tests. It is listed in the Compose BOM but not in `gradle/libs.versions.toml`, and ATF does
not arrive transitively through the existing test dependencies. `DeviceConfigurationOverride.FontScale`
and `DeviceConfigurationOverride.Locales` already ship in the pinned `ui-test` artifact. A new
library is a gated stack topic.

## Options Considered

| Option | Benefits | Costs / Risks |
|--------|----------|---------------|
| A. BOM-managed `ui-test-junit4-accessibility` (ATF) with `enableAccessibilityChecks()` in the Compose tests | Compose-native; the version moves with `composeBom`; the checks run in the existing instrumented suite. | One new test-only dependency; its coordinates and resolved version are confirmed at the `E4-02` intake. |
| B. `espresso-accessibility` | Long-established. | View-based and weaker on a Compose-only app. Rejected. |
| C. No library: a manual TalkBack audit and semantics assertions only | No dependency. | No automated protection against regressions. |

## Decision

The selected option is **A**. `androidx.compose.ui:ui-test-junit4-accessibility` is declared in
`gradle/libs.versions.toml` without a version literal, resolved through the `composeBom` platform
(`2026.08.00` when this decision was taken), and used only as an `androidTestImplementation`
dependency of `:androidApp`. The Compose instrumented tests of every audited screen call
`enableAccessibilityChecks()`. `docs/versions-matrix.md` records the artifact and the version the
BOM resolves.

## Consequences

### Positive

- Accessibility regressions on the audited screens fail the instrumented suite.

### Negative

- One more test dependency to keep aligned with the Compose BOM.

### Constraints Introduced

- No other Android accessibility-check library is added without a new decision.
- The dependency never reaches a release artifact.

## Verification

- The instrumented tests of F-1 to F-3, Settings, the departure screens and the `E4-05` surface run
  with the checks enabled and pass; temporarily removing one label makes one of them fail, and the
  result is recorded in `docs/handoffs/E4-02.md`.

## References

- `docs/DECISION_BOARD.md` (decision ID `D-216`, Library Review Matrix)
- `docs/SPECIFICATION.md §11` (Accessibility)
- `docs/versions-matrix.md`, `gradle/libs.versions.toml`
- `docs/BACKLOG.md` (`E4-02`)
