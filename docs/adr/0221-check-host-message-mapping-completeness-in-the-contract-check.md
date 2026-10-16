# ADR-0221 / D-220 - Check Host Message-Mapping Completeness in the Contract Check

## Status

Accepted

The owner selected option A on 2026-10-16.

## Context

`docs/CONTRACTS.md §6`: "Presentation MUST handle it with an exhaustive `when` and MUST NOT use an
`else` branch." The hosts receive an error as `UiMessage.code`, a `String`, so the compiler cannot
check exhaustiveness, and both hosts map it with a fallback branch (`else` in `MainActivity.kt`,
`default` in `iosApp/UiMessageMapping.swift`). `core/common/src/commonTest/.../AppErrorCodesTest.kt`
lists 45 of the 46 leaves and misses `AuthError.NoAccountAvailable`.

## Options Considered

| Option | Benefits | Costs / Risks |
|--------|----------|---------------|
| A. A `contractCheck` assertion that parses the `AppError` leaf codes plus the `CONFIRMATION.*` and `INFO.*` message codes and fails when either host's mapping source has no explicit non-fallback entry for one; `§6` allows a host `else` / `default` only as an unreachable safety net guarded by that assertion | Restores the `§6` guarantee for `String` codes; no Swift-ABI change. | One more assertion with failing fixtures to maintain. |
| B. Make `UiMessage` a typed sealed value across Swift | The compiler checks exhaustiveness. | A large Swift-ABI change. |
| C. Amend `§6` to allow `else` with no check | No work. | The rule would guarantee nothing. Rejected. |

## Decision

The selected option is **A**.

- A new `contractCheck` assertion, numbered after the last assertion of `docs/CONTRACTS.md §18` at
  the time it is added, parses the leaf codes declared in
  `core/common/src/commonMain/kotlin/com/ruizurraca/carapp/core/common/AppError.kt` and the
  `CONFIRMATION.*` and `INFO.*` message codes that shared code publishes, and fails when the Android
  mapping source (`androidApp/src/main/java/com/ruizurraca/carapp/UiMessageStrings.kt`) or the iOS
  mapping source (`iosApp/UiMessageMapping.swift`) has no explicit entry, other than the fallback,
  for one of them.
- Failing fixtures, written before the rule (`D-16`), prove that it fires for a missing Android
  entry and for a missing iOS entry.
- `docs/CONTRACTS.md §6` is amended: a host `else` / `default` branch over `UiMessage.code` is
  allowed only as an unreachable safety net guarded by that assertion.
- `AppErrorCodesTest` gains `AuthError.NoAccountAvailable` and is made impossible to fall behind,
  by checking it against the parsed source.

## Consequences

### Positive

- A new error code cannot ship without copy on both hosts.

### Negative

- The assertion parses host source text, so a mapping written in an unexpected shape needs the
  parser to follow.

### Constraints Introduced

- Every host mapping of `UiMessage.code` lives in the two mapping sources named above.

## Verification

- The new assertion passes on the final head, and its fixtures fail as intended in
  `./gradlew :build-logic:convention:test`.

## References

- `docs/DECISION_BOARD.md` (decision ID `D-220`)
- `docs/CONTRACTS.md §6`, `§18`, `§20.2`
- `D-16` (ADR-0017), `D-219` (ADR-0220)
- `docs/BACKLOG.md` (`E4-02`)
