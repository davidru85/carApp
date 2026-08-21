# Phase 0 - Open Decisions

> **Temporary and non-normative working document** (`AGENTS.md`). It exists so the owner can take
> six decisions in one pass. Each accepted option becomes a decision ID, an ADR and rows in the four
> mirroring documents, per the `AGENTS.md` rule that a decision taken during implementation MUST be
> recorded as an ADR. **This file is deleted once every decision below is absorbed.**

Phase 0 is otherwise complete: `E0-01` through `E0-06` and `E0-08` are merged or in review. `E0-07`
is blocked, which is `DEC-5`.

---

## `DEC-1` — `docs/CONTRACTS.md §2` golden row 3 contradicts its own formula

**Found by:** `E0-03`, implementing the monetary arithmetic.

Golden row 3 says `litersScaled = 1`, `pricePerLiterScaled = 1`, EUR gives `totalCostMinor = 1`,
annotated "rounds up from 0.0001". The formula in the same section — which that section says MUST be
implemented literally — gives `0`:

```text
(1 * 1 * 100 + 500_000) / 1_000_000 = 500_100 / 1_000_000 = 0
```

The formula is right and the row is wrong: 0.001 L at 0.001 €/L is 0.000001 €, which is 0.0001 minor
units, and HALF_UP of 0.0001 is 0. The row's parenthetical "(0.01 €)" is one cent — ten thousand
times the real value. The other three rows agree with the formula exactly, including the overflow row.

| Option | For | Against |
|--------|-----|---------|
| **A. Correct the row to `0`** | The formula is mathematically right and three rows already agree. No code changes. | The row's purpose was to demonstrate rounding **up**, and a `0` expectation demonstrates nothing. |
| **B. Add a "minimum one minor unit" rule** | No non-zero purchase would ever cost 0. | Contradicts HALF_UP, changes the canonical formula, affects every small amount, and no product requirement asks for it. |
| **C. Correct the row to `0` and add a real HALF_UP round-up row** ✅ | Keeps the regression case the row was meant to be. `litersScaled = 1_000`, `pricePerLiterScaled = 5`, EUR is exactly 0.5 minor units and rounds up to `1`. | Two table edits instead of one. |

**Recommended: C.** The row exists to catch a rounding regression; fixing the number without
restoring that coverage would trade one gap for another.

---

## `DEC-2` — `testAppGraphDependencies(...)` cannot exist in Phase 0

**Found by:** `E0-03`. Also the reason `contract-check` assertion 13 reports `PENDING`.

`AppGraphDependencies` (`§11.6`) has 15 members. Four have types owned by modules Phase 0 forbids:
`DatabaseFactory` (`:core:database`), `AuthClient` and `TokenProvider` (`:core:auth`),
`RemoteSyncSource` (`:core:sync`). The Phase 0 preamble says those modules "MUST NOT be pulled into
Phase 0 early", and `E0-04` is required to enforce it. `AppGraphDependencies` itself is a `:shared`
type built by `E0-07`.

| Option | For | Against |
|--------|-----|---------|
| **A. Move the criterion to `E0-07`** ✅ | `E0-07` is the first story where `AppGraphDependencies` exists at all. No rule is bent and no module is created early. | `E0-03` closes with one criterion relocated. |
| **B. Let Phase 0 create interface-only `:core:database`, `:core:auth`, `:core:sync`** | The factory exists sooner. | Directly contradicts the Phase 0 preamble and the `E0-04` rule that enforces it, and leaves three stub modules standing for months. |
| **C. Build a partial factory with the 11 available parameters** | Something exists now. | Breaks `§11.6`'s "same parameter count and order", makes assertion 13 permanently red, and gets rewritten anyway. |

**Recommended: A.**

---

## `DEC-3` — the feature-layer package rules are unimplemented

**Found by:** `E0-04`.

Three rows of `docs/TECHNICAL_PLAN.md §4` govern packages inside one Gradle module — feature
`domain`, `data` and `presentation`. `D-16` assigns package-level rules to Konsist. Konsist rules
need a module to live in, and no `:feature:*` module exists. Konsist is pinned at 0.17.3 by `E0-06`
and currently unused. The 14 module-level rows are enforced today.

| Option | For | Against |
|--------|-----|---------|
| **A. Move them to `E1-07`, the first feature module** ✅ | Konsist rules land with the code they guard. Nothing is unguarded meanwhile, because no feature exists to break the rule. | `E0-04` closes with three of its criteria deferred. |
| **B. Create a dedicated architecture-test module now** | The rules exist earlier and have one home. | That module is not in the canonical inventory of `§1.1`, so it would fail `contract-check` assertion 8 — a rule breaking a rule. |
| **C. Extend the custom Gradle checker to do package analysis and drop Konsist** | One tool, already working and already tested. | Contradicts `D-16`; needs a superseding ADR and a rewrite of package analysis the checker does not do today. |

**Recommended: A.**

---

## `DEC-4` — the contract does not hold its own declarations

**Found by:** `E0-05`, writing `contract-check` assertion 1.

`§20` opens with "Every type referenced by a signature in this document is declared here". Nine are
not: `Logger` is in `§17`, `AnalyticsTracker` in `§16.1`, `RemoteSyncSource` in `§10`,
`AppGraphDependencies` in `§11.6`, `VehicleRepository`, `FuelEntryRepository` and
`SettingsRepository` in `§12`, `CalculateConsumption` in `§13`, `AuthClient` in `§11`.

Separately, two `kotlin` fences in `§20.9` contain explanatory paragraphs rather than code. They
cannot be compiled or copied, and they made the checker read English words as type names.

| Option | For | Against |
|--------|-----|---------|
| **A. Move all nine declarations into `§20`, and lift the prose out of the fences** | Makes `§20`'s own claim true and lets assertion 1 be enforced exactly as `§18` words it. | A large edit to a gated document, with real transcription risk, for a problem no implementer actually hits. |
| **B. Reword assertion 1 to "declared anywhere in `docs/CONTRACTS.md`", and fix the two prose fences** ✅ | Small, and honest about what the check does. Nothing is ambiguous today: every type *is* declared. Fixing the fences is unambiguously right either way. | `§20`'s opening sentence stays an overstatement unless it is also reworded. |
| **C. Leave both** | No work. | `contract-check` reports `PENDING` forever, which trains readers to ignore it. |

**Recommended: B**, with `§20`'s opening sentence reworded to "Every type this document does not
declare inline is declared here."

---

## `DEC-5` — `E0-07` walking skeleton is blocked twice

**Found by:** attempting it. This is the only Phase 0 story not delivered.

**Blocker 1, owner-only.** `E0-07` requires a real anonymous sign-in and a Firestore write, in the
development project `carapp-dev` with the database in `europe-west1` (`D-13`, `D-14`, `D-22`). That
project does not exist, and no agent can create it: it needs a Google account, billing settings and
the Android and iOS app registrations that produce `google-services.json` and
`GoogleService-Info.plist`.

**Blocker 2, a backlog contradiction.** `E0-07` says the screen crosses "native UI, shared state
holder, **Room**, Firestore and real anonymous auth", and its decision gate is about Room KMP on
iOS. Room lives in `:core:database`, which is `E1-01` — and the Phase 0 preamble forbids creating it,
a rule `E0-04` now enforces. `E0-07` cannot use Room without breaking the rule that guards Phase 0.

| Option | For | Against |
|--------|-----|---------|
| **A. Owner creates the Firebase project; `E0-07` is allowed to create `:core:database`** | Keeps the walking skeleton inside Phase 0, where it proves the riskiest integration before any feature is written. | Punches an exception through the Phase 0 module set, which `E0-04` enforces; the exception has to be written into the preamble and the rule. |
| **B. Reduce `E0-07` to exclude Room, keeping Firestore and auth with in-memory local state** | No rule bent, no Firebase reordering. | Stops being a walking skeleton. Its headline criterion — a value restored on a clean second device — is a persistence claim, and the Room-on-iOS decision gate is the single largest technical risk in the plan. Proving everything except the risky part is the wrong trade. |
| **C. Move `E0-07` to the start of Phase 1, immediately after `E1-01`** ✅ | No contradiction: `:core:database` exists by then and Room is legitimately available. Phase 0 closes cleanly on tooling and contracts, which is what it actually delivered. | The riskiest integration is proven one story later, and `E0-07`'s human gate moves with it. |

**Recommended: C**, plus creating the Firebase project whenever convenient, since `E1-01` does not
need it and `E0-07` cannot start without it.

Under C, `E0-07` and `E1-01` swap places in the execution order and Phase 0 closes now.

---

## `DEC-6` — branch protection for `main`

**Found by:** `E0-05`. `docs/CONTRACTS.md §18` says branch protection MUST require the nine checks
once CI exists. CI now exists but has never run, and branch protection cannot be configured from a
pull request: it needs repository admin rights, and GitHub offers a check by name only after it has
reported at least once.

Two of the nine, `objc-header-golden-check` and `provider-decoupling`, have nothing to verify yet.
Both pass with a visible warning, and both fail loudly if the thing they guard ever appears without
the check being implemented.

| Option | For | Against |
|--------|-----|---------|
| **A. Require all nine now** ✅ | Matches `§18` exactly. The two placeholders cost nothing and cannot pass silently once they have a subject. | Two required checks assert nothing today. |
| **B. Require the seven real ones, add the others later** | Every required check means something. | `§18` says these checks; a later edit to branch protection is easy to forget, and the two that get forgotten are the two nobody is watching. |

**Recommended: A**, after this stack merges and CI has reported once.

---

## Already decided, recorded here only for visibility

These were resolved during implementation and need no owner input. Each is explained in its story's
handoff.

- The monetary formulas take `minorUnitFactor` as a parameter rather than resolving it, because
  `MinorUnits` lives in `:core:common` and `:core:model` MUST NOT depend on it, while the golden
  tests MUST live in `:core:model`.
- `MagicNumber` is suppressed in the two arithmetic files: those literals **are** the contract
  formula, and naming them would hide the only thing a reviewer has to check.
- The Xcode pin fails CI on a major-version drift and warns on a patch drift.
- `:shared` gained the Android build namespace `com.ruizurraca.carapp.shared` under `D-24`.
