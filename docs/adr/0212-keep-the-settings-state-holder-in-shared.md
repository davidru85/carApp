# ADR-0212 / D-211 - Keep the Settings State Holder in Shared

## Status

Accepted

The owner selected option A on 2026-10-16.

## Context

`E4-01` builds the Settings screen of `docs/SPECIFICATION.md §3.1`. Both hosts need one shared state
holder that publishes the device-local settings (`docs/CONTRACTS.md §3`, `§12` `SettingsRepository`)
and carries the currency and analytics intents. No such holder exists. `SettingsRepository` is
declared in `:feature:session`, its `updateSettings` has no production caller, and
`:composition:ios` exports only `:shared`, `:feature:vehicle`, `:feature:fuel` and `:core:common`
to Swift. `D-155` (ADR-0156) already keeps `SessionStateHolder` in `:shared` beside
`SyncStateHolder`, as the app-level exception to the `§14` rule that state holders live in feature
`presentation` packages; the `§14` sentence still names only `SyncStateHolder`.

The supported currencies are `SUPPORTED_CURRENCY_CODES`, a `@HiddenFromObjC` `Set<String>` in
`:core:common`, and `§15.3` makes a currency cross the Swift boundary as `String`.

## Options Considered

| Option | Benefits | Costs / Risks |
|--------|----------|---------------|
| A. `SettingsStateHolder` in `:shared`, beside `SessionStateHolder` and `SyncStateHolder` | Follows `D-155`; no new framework export; every app-level holder in one place. | Extends the Swift-facing surface (`§20.10`, `§18` assertions 34 and 35, the golden header); `§14` must be corrected. |
| B. In `:feature:session` `presentation`, exporting `:feature:session` from `:composition:ios` | Matches the general `§14` rule. | A module-boundary and Swift-ABI change in the style of `D-85` and `D-97`, for one holder. |
| C. Settings fields and intents on `SessionStateHolder` | No new class. | Bloats the authentication orchestrator with non-authentication state. Rejected. |

## Decision

The selected option is **A**.

- `shared/src/commonMain/kotlin/com/ruizurraca/carapp/SettingsStateHolder.kt` declares
  `class SettingsStateHolder internal constructor(scope: CoroutineScope, repository: SettingsRepository, dispatchers: DispatcherProvider)`
  with `val state: StateFlow<SettingsUiState>` and the intents `selectCurrency(code: String)`,
  `setAnalyticsEnabled(enabled: Boolean)`, `clearMessage()` and `close()`.
- `SettingsUiState` (in `UiModels.kt`) carries `currencyCode: String?`, `supportedCurrencyCodes:
  List<String>` in `SUPPORTED_CURRENCY_CODES` order, the typed read-only `distanceUnit` and
  `volumeUnit`, `analyticsEnabled`, `isSaving` and a typed `message: UiMessage?`. It carries no
  user-facing text: hosts produce currency display names through platform currency APIs.
- `AppGraph` gains `settingsStateHolder(scope: CoroutineScope)`; `SwiftAppGraph` gains
  `settingsStateHolder()`, cached like `sessionStateHolder()` and closed by `close()`.
- Backup status stays on `SyncStateHolder` and departure on `SessionStateHolder`; the new holder
  duplicates neither.
- `docs/CONTRACTS.md §14` names `SessionStateHolder`, `SyncStateHolder` and `SettingsStateHolder` as
  the app-level holders that live in `:shared`.

## Consequences

### Positive

- Settings needs no new module export and no change to the `docs/TECHNICAL_PLAN.md §4` table.
- The analytics opt-in gets a production caller of `SettingsRepository.updateSettings`.

### Negative

- The Swift-facing surface grows, which is a gated topic and changes the golden header.

### Constraints Introduced

- The public API keeps the `§15.3` rules: no default arguments, value classes or type parameters.
- `§20.10`, `§18` assertions 34 and 35, `REQUIRED_SWIFT_HEADER_SYMBOLS` and the golden header change
  together with the holder.

## Verification

- `:shared` common tests drive the holder over the real SQLDelight settings repository and a
  recording analytics tracker, including the end-to-end opt-in that `docs/handoffs/E3-09.md` left
  open.
- `contractCheck` assertions 34 and 35 and the golden-header diff pass with the new members.

## References

- `docs/DECISION_BOARD.md` (decision ID `D-211`)
- `D-155` (ADR-0156), `D-85` (ADR-0086), `D-97`
- `docs/CONTRACTS.md §3`, `§12`, `§14`, `§15.3`, `§18`, `§20.0.1`, `§20.10`
- `docs/BACKLOG.md` (`E4-01`)
