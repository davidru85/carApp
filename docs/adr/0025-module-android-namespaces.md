# ADR-0025 / D-24 - Derive Module Android Namespaces from the Module Path

## Status

Accepted

Accepted by the owner on 2026-08-21.

## Context

Since AGP 9.0, every Gradle module with an Android target MUST declare an Android build namespace, and two modules MUST NOT share one. `E0-06` hit this immediately: `:shared` and `:androidApp` both declared `com.ruizurraca.carapp` and the manifest merger failed.

This is not a single-module problem. `docs/TECHNICAL_PLAN.md §3` plans 17 modules beyond `:shared` — `:core:model`, `:core:common`, `:core:database`, `:core:sync`, `:feature:fuel`, `:integration:firebase-auth`, `:wiring:firebase` and the rest — and each one with an Android target will need a unique namespace.

`docs/identifiers.md` states that agents MUST NOT invent, guess or temporarily change any identifier, but it did not cover per-module Android namespaces. An agent implementing `E0-02` or `E0-03` would therefore have to either invent a value, violating that rule, or escalate and block, and different agents would produce `com.ruizurraca.carapp.core.model`, `...coremodel` and `...model` for the same module.

The Android build namespace is also frequently confused with the Kotlin package root. They are different things: the namespace is a build identifier used for the generated `R` class and the merged manifest, while the package root is where Kotlin declarations live.

## Options Considered

| Option | Benefits | Costs / Risks |
|--------|----------|---------------|
| Derive the namespace from the Gradle module path, recorded as a rule in `docs/identifiers.md` | Covers all 18 modules with one decision. Mirrors the sub-package rule already in that document. A convention plugin can compute it, so no agent ever writes one. | Turns a table of concrete values into a table plus a rule; the rule has to state explicitly that it is not the Kotlin package root. |
| Record a single concrete value for `:shared` | Smallest possible change. | Resolves 1 of 18 cases; the same gap reappears in `E0-02` and `E0-03`. |
| Leave it to each module build script | No documentation change. | Guarantees inconsistent namespaces and makes the "MUST NOT invent identifiers" rule unenforceable. |

## Decision

The Android build namespace of a module is **derived, not chosen**: the shared module package root, followed by the Gradle module path with `:` replaced by `.` and any `-` removed.

- `:shared` → `com.ruizurraca.carapp.shared`
- `:core:model` → `com.ruizurraca.carapp.core.model`
- `:feature:fuel` → `com.ruizurraca.carapp.feature.fuel`
- `:integration:firebase-auth` → `com.ruizurraca.carapp.integration.firebaseauth`

`:androidApp` is the exception and keeps `com.ruizurraca.carapp`, the application namespace fixed in `docs/identifiers.md`. The Kotlin package root of shared code is unchanged: it stays `com.ruizurraca.carapp` with sub-packages following the module path.

## Consequences

### Positive

- The remaining 17 modules get their namespace without an owner decision each.
- The value is computable, so `E0-02` can set it from the Gradle project path and no module build script needs a literal.
- The "agents MUST NOT invent identifiers" rule becomes enforceable for namespaces instead of silently inapplicable.

### Negative

- The namespace and the Kotlin package root differ for every module except `:androidApp`, which reads as a discrepancy until the distinction is understood. The rule states it explicitly for that reason.

### Constraints Introduced

- An agent MUST NOT write an Android namespace literal in a module build script once `E0-02` derives it.
- Adding a module with an Android target requires no namespace decision; the value follows from its Gradle path.

## Verification

- `E0-06` applies the rule to `:shared` and the Android build succeeds with the manifest merger satisfied.
- `E0-02` derives the namespace from the Gradle project path and removes the literal from `shared/build.gradle.kts`.

## References

- `docs/DECISION_BOARD.md` (`D-24`)
- `docs/identifiers.md`, "Module Android namespaces"
- `docs/TECHNICAL_PLAN.md §3`
