# ADR-0222 / D-221 - Make the UI Text Rules Executable

## Status

Accepted

The owner selected option A on 2026-10-16.

## Context

`docs/CONTRACTS.md §14`: "`UiState` MUST NOT contain user-facing text." `docs/SPECIFICATION.md §11`:
"no hardcoded user-facing strings". No executable check enforces either rule. iOS renders
interpolated literals that no catalogue entry backs (`iosApp/VehicleDetailView.swift`,
`iosApp/FuelEntryFormView.swift`). The Debug-only diagnostics surfaces of `D-100`
(`iosApp/DiagnosticsView.swift`; the Android `SyncDiagnosticsScreen` on
`VehicleRoutes.SYNC_DIAGNOSTICS`) legitimately show developer text.

## Options Considered

| Option | Benefits | Costs / Risks |
|--------|----------|---------------|
| A. Executable `contractCheck` (or Konsist) rules, each with failing fixtures | The rules hold by construction, like every other rule in this repository. | Four rules, their fixtures and an allowlist to maintain. |
| B. Reviewer-only enforcement | No code. | Contradicts the repository rule that rules are executable and each has a failing fixture. Rejected. |

## Decision

The selected option is **A**. Four rules, each with a failing fixture written first (`D-16`):

1. `String` fields of a `UiState` are limited to an explicit allowlist: identifiers,
   `currencyCode` and user-entered values.
2. A host source scan rejects literal user-facing text: Compose `Text("…")` and
   `contentDescription = "…"`, and SwiftUI `Text(verbatim:)` and interpolated `Text("\(…)")`.
3. Every iOS localization key referenced in source exists in both `en.lproj/Localizable.strings`
   and `es.lproj/Localizable.strings`.
4. The Debug-only diagnostics surfaces of `D-100` are allowlisted explicitly, by file.

## Consequences

### Positive

- User-facing text can no longer leak into `UiState` or host code unnoticed.

### Negative

- The source scan is textual; a new UI construct that shows text needs the scan to follow.

### Constraints Introduced

- The allowlists of rules 1 and 4 change only with a reviewed reason.

## Verification

- The rules pass on the final head; their fixtures fail as intended in
  `./gradlew :build-logic:convention:test`.

## References

- `docs/DECISION_BOARD.md` (decision ID `D-221`)
- `docs/CONTRACTS.md §14`, `§18`; `docs/SPECIFICATION.md §11` (Localization)
- `D-16` (ADR-0017), `D-100`
- `docs/BACKLOG.md` (`E4-02`)
