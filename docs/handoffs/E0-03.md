# Agent Handoff - E0-03

> **Closure update (2026-08-27):** D-26 closed `DEC-1`; D-27 and D-56 closed `DEC-2` by placing
> `testAppGraphDependencies(...)` in `:shared:testing`, delivered by E0-07. E0-05 applied and
> passed the D-18 Kover thresholds. The original Phase 0 omissions below are historical evidence,
> not current open criteria. The backoff helper remains correctly owned by E3-03 rather than E0-03.

## Story

`E0-03 - Base Core Modules - M` (`docs/BACKLOG.md`).

## Ready Check

- [x] Backlog story is explicit — `E0-03`, unblocked by `E0-01`; stacked on `E0-02` for the convention plugins.
- [x] Acceptance criteria reviewed — the nine criteria listed under `E0-03`.
- [x] Dependencies checked — needs `E0-02`'s `carapp.kmp.library`; `:core:analytics` is `E0-08` and is not created here.
- [x] Required decisions are not `Proposed` or `Pending` — none pending.
- [x] Normative sections reviewed — `docs/CONTRACTS.md §2`, `§17`, `§20.0`, `§20.0.1`, `§20.1`, `§20.2`, `§20.3`, `§20.3.1`, `§11.6`; `docs/TECHNICAL_PLAN.md §3` and `§4`.
- [x] Expected verification identified — host and Kotlin/Native tests per module, golden-value tests, source guard.
- [x] Human review gates identified before work — none for the code; **two contract questions surfaced below require an owner decision**.
- [x] Rule 0 acknowledged — chat replies in Spanish (es-ES), every artifact in technical English.

## Scope Completed

Four modules, each with a build file of four lines or fewer, which is the first real evidence for the `E0-02` "no more than five lines" criterion:

- **`:core:model`** — `EntityId`, `OwnerId`, `CurrencyCode`, `LOCAL_OWNER`, `Money`, `FuelVolume`, `PricePerLiter`, `ConsumptionL100Km`, and the five canonical formulas of `§2` as exact integer arithmetic.
- **`:core:common`** — `Outcome` with its five extensions, the complete `AppError` hierarchy with all 44 stable codes, `Confirmation`, `AppClock`, `UuidGenerator`, `DispatcherProvider`, `LogLevel`, `Logger`, `LocaleInfo`/`LocaleProvider`, `ConnectivityObserver`, `SyncTrigger`/`SyncTriggerAdapter`, `AuthProvider`, `OwnerContext`, `MinorUnits` and the named constants of `§20.0.1`.
- **`:core:crash`** — `CrashReporter` and `NoOpCrashReporter`, with no `expect`/`actual` and no provider type.
- **`:core:testing`** — deterministic fakes for every Phase 0 abstraction: `FakeAppClock`, `FakeUuidGenerator`, `TestDispatcherProvider`, `RecordingLogger`, `FakeLocaleProvider`, `FakeConnectivityObserver`, `FakeOwnerContext`, `RecordingSyncTriggerAdapter`.

The dependency direction runs `:core:common` → `:core:model` and never the reverse, as `docs/TECHNICAL_PLAN.md §4` requires.

## Acceptance Evidence

| Criterion | Evidence |
|-----------|----------|
| `Outcome`, the `AppError` hierarchy, `Confirmation`, and the platform abstractions match `§20` | `AppErrorCodesTest` pins all 44 codes leaf by leaf and asserts uniqueness; `OutcomeTest` covers all five extensions; `AppProvider`/`SyncTrigger`/`Confirmation` enum orders asserted. |
| `:core:crash` exposes `CrashReporter` and a no-op, with no Firebase, GitLive, Android or iOS type | `CrashReporter.kt` imports only `AppError`; `NoOpCrashReporterTest` exercises both methods. |
| `§20.0` types match exactly, `value`/`scaled` naming, every scaled value a `Long` | `Identifiers.kt`, `Money.kt`; `ConstructionNeverValidatesTest` asserts `Long.MIN_VALUE`/`MAX_VALUE` round-trip through the scaled types. |
| None of those types validates on construction | `ConstructionNeverValidatesTest` wraps a malformed UUID, an empty string and an unsupported currency code and asserts all succeed. |
| Named constants of `§20.0.1` exist, including `SUPPORTED_CURRENCY_CODES` | `ConstantsTest` and `MinorUnitsTest`; the latter asserts the set is exactly the 21 documented codes. |
| The three monetary and two consumption formulas are exact integer arithmetic and pass every golden value | `MonetaryArithmeticTest` and `ConsumptionArithmeticTest` cover every row of both `§2` tables, **except one that the contract gets wrong — see `DEC-1`**. The average test also asserts the distance-weighted result differs from the arithmetic mean of the rounded segments. |
| A test proves no monetary or consumption path uses `Float` or `Double` | `NoFloatingPointArithmeticTest` scans `:core:model` sources. It was verified to fail: a temporary `val temporaryOffender: Double` made it report `_Offender.kt:2` and fail the build; removing it returned the build to green. |
| `:core:testing` exposes `testAppGraphDependencies(...)` | **Not done. Impossible in Phase 0 — see `DEC-2`.** |
| Kover thresholds pass for `:core:model` and `:core:common` | **Not verifiable yet.** Kover is configured by `E0-05`; see "Out of Scope". |

## Out of Scope / Not Done

- **`testAppGraphDependencies(...)` is absent** (`DEC-2`).
- **Kover thresholds are unverified.** `D-18` requires 90% for both modules, but the Kover plugin is applied by `E0-05`. The modules are written test-first and every public declaration is exercised, but the number is not measured. `E0-05` MUST confirm it and this story MUST NOT be treated as closing that criterion.
- The backoff helper that `docs/TECHNICAL_PLAN.md §3` lists for `:core:common` is not implemented. It is defined by `§9.7` and belongs to `E3-03`; `E0-03`'s criteria do not name it.
- `:core:analytics` is `E0-08` and is deliberately not created here.

## Decisions Required From The Owner

### `DEC-1` — `docs/CONTRACTS.md §2` golden row 3 contradicts its own formula

Golden row 3 states that `litersScaled = 1`, `pricePerLiterScaled = 1`, EUR gives `totalCostMinor = 1`, annotated "rounds up from 0.0001". The formula in the same section, which that section says MUST be implemented literally, gives `0`:

```text
(1 * 1 * 100 + 500_000) / 1_000_000 = 500_100 / 1_000_000 = 0
```

The formula is right and the row is wrong. 0.001 L at 0.001 €/L is 0.000001 €, which is 0.0001 minor units; HALF_UP of 0.0001 is 0. The row's parenthetical "(0.01 €)" is one cent, ten thousand times the real value. The other three golden rows agree with the formula exactly, including the overflow row.

This story implements the formula literally and asserts `0`, because "implement the formula literally" is an unambiguous MUST while the row contradicts both the formula and HALF_UP. The owner decides how the contract is corrected.

### `DEC-2` — `testAppGraphDependencies(...)` cannot exist in Phase 0

`E0-03` requires `:core:testing` to expose it. `AppGraphDependencies` (`§11.6`) has 15 members, four of whose types live in modules Phase 0 forbids: `DatabaseFactory` (`:core:database`, `E1-01`), `AuthClient` and `TokenProvider` (`:core:auth`, Phase 2), `RemoteSyncSource` (`:core:sync`, Phase 3). The Phase 0 preamble says those modules "MUST NOT be pulled into Phase 0 early", and `E0-04` requires an architecture rule that fails the build if they are. `AppGraphDependencies` itself is a `:shared` type built by `E0-07`.

So three of the backlog's own rules cannot all hold. The factory is omitted here and the decision is put to the owner.

## Files Changed

- `core/model/`, `core/common/`, `core/crash/`, `core/testing/` — new modules with `commonMain` sources and `commonTest` tests; `core/model` additionally has an `androidHostTest` source guard.
- `settings.gradle.kts` — the four modules and `TYPESAFE_PROJECT_ACCESSORS`.
- `build-logic/.../KmpLibraryConventionPlugin.kt` — coroutines moved into the convention plugin, so no module repeats it.
- `shared/build.gradle.kts` — dropped its now-redundant coroutines line.

## Decisions Made

- **No `SHOULD` deviated from.** No new decision ID was taken; `DEC-1` and `DEC-2` are put to the owner rather than decided here, so no ADR is created by this story.
- **The monetary formulas take `minorUnitFactor` as a parameter** instead of resolving it. `MinorUnits` lives in `:core:common` and `:core:model` MUST NOT depend on `:core:common` (`docs/TECHNICAL_PLAN.md §4`), while the golden tests MUST live in `:core:model`. Passing the factor in satisfies both, and matches `§2`, where validation resolves the factor and rejects an unsupported currency before any arithmetic runs.
- **The formulas are top-level functions, not a new type.** `§2` states them as formulas, not as an API, and introducing a wrapper type would add a project-owned type that `§20` does not declare.
- **The `Float`/`Double` ban is enforced by a source-scanning JVM host test.** A runtime assertion cannot detect it: a floating-point implementation returns the right answer for most inputs and drifts only where nobody looks. `E0-04` generalises this to every module.

## Verification Run

- [x] Relevant tests pass
- [ ] Lint passes (ktlint, detekt) — not configured yet; `E0-05`
- [ ] Coverage thresholds hold — Kover not applied yet; `E0-05`
- [ ] Architecture checks pass — not implemented yet; `E0-04`
- [ ] Contract check passes — not implemented yet; `E0-05`
- [x] Relevant builds pass
- [x] Documentation updated

```text
./gradlew :core:model:testAndroidHostTest :core:model:iosSimulatorArm64Test
./gradlew :core:common:testAndroidHostTest :core:common:iosSimulatorArm64Test
./gradlew :core:crash:testAndroidHostTest :core:crash:iosSimulatorArm64Test
./gradlew :core:testing:testAndroidHostTest :core:testing:iosSimulatorArm64Test
```

All green on both the Android host and `iosSimulatorArm64`. The `Float`/`Double` guard was additionally verified to fail on an injected offender before being returned to green.

## Contract Impact

- [x] No contract changes made. **`DEC-1` proposes one**, in `docs/CONTRACTS.md §2`, and is left to the owner.

## Decision Board Impact

- [x] No decision changes.

## Shared-Write Modules Touched

- [x] None. `:core:database` does not exist.

## Project Log Entry

- [x] Entry appended to `docs/PROJECT_LOG.md`.

## Risks or Follow-ups

- `DEC-1` and `DEC-2` are open and both block a clean close of this story's criteria.
- Coverage is unmeasured until `E0-05`.
- The fakes use `Dispatchers.Unconfined` rather than a `StandardTestDispatcher`. That is deterministic for the current fakes but will not be enough once `:core:sync` needs virtual time; `E3-03` should revisit `TestDispatcherProvider`.
- `NoFloatingPointArithmeticTest` guards `:core:model` only. Until `E0-04` generalises it, `:core:common` and later modules are unguarded.

## Human Review Gate

- [x] Not applicable for the code.
- [x] `DEC-1` would touch `docs/CONTRACTS.md`, a gated path and a gated topic, if the owner accepts it.
