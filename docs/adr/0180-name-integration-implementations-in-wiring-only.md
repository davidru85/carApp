# ADR-0180 - Name `:integration:*` implementations in `:wiring:firebase` only

## Status

Accepted

## Context

`docs/CONTRACTS.md §11.6` and `docs/TECHNICAL_PLAN.md §4` both state that `:wiring:firebase` is the
only module that constructs Firebase implementations. Two existing mechanisms already cover part of
that:

- the dependency table forbids `:integration:*` as a declared edge for every other module, so a
  direct import cannot compile; and
- the Objective-C golden header rejects the exported provider types.

Neither closes the same hole. `:wiring:firebase` declares its integration dependencies with
`implementation`, but `api` is a one-word change that makes those types visible to every consumer
through the declared `api(project(":shared"))` edge — while the dependency table still sees a single
legal `:wiring:firebase` -> `:integration:*` edge. In that state `:composition:ios` can name
`FirebaseAuthClient` in its own source and the compiler accepts it. The type is transitively
visible, the declared graph is unchanged, and the golden header does not change either, because
naming a class in a local expression does not export it.

## Options Considered

| Option | Benefits | Costs / Risks |
|--------|----------|---------------|
| Option A: a source rule rejecting any mention of the `com.ruizurraca.carapp.integration.` package outside `:wiring:firebase` and `:integration:*` | Catches the transitive-visibility hole and the direct case with the same rule; one line of text per finding, naming the file and line | A comment or a string naming the package trips it. Acceptable: the checker strips line comments already, and a string carrying an integration FQCN in a module that must not know it is itself a defect |
| Option B: tighten the architecture table so `:wiring:firebase`'s integration edges must be `implementation` | Expresses the real constraint at the dependency level | Needs a new configuration-aware dimension in the table and the parser. It also does not cover the composition root, which legitimately declares `implementation(project(":wiring:firebase"))` while still being forbidden from naming integration types |
| Option C: rely on the golden header alone | No new rule | The header cannot see a type that is named but never exported, which is exactly this hole |

## Decision

The selected option is: **Option A.**

`ArchitectureChecker` gains a `firebase-implementation-outside-wiring` rule. Any source line in a
module other than `:wiring:firebase` and `:integration:*` that mentions
`com.ruizurraca.carapp.integration.` is a violation. The rule reads the collected source lines, so it
covers a fully-qualified reference and an import alike.

## Consequences

### Positive

- The transitive `api` leak is caught before a header regeneration would have to be committed to
  observe it.
- The rule fires on the module that would have to change, with the file and line.
- It holds for every module, including the composition root and the Android host, whose legitimate
  edge to `:wiring:firebase` is not affected.

### Negative

- The package prefix is textual, so a future integration package renamed away from
  `com.ruizurraca.carapp.integration.` would stop being covered. `NOT_YET_INTRODUCED_MODULES` and the
  provider registry already pin the same prefix elsewhere, and the rule's constant is beside them.

### Constraints Introduced

- No module outside `:wiring:firebase` and `:integration:*` MAY mention
  `com.ruizurraca.carapp.integration.` in its source.
- The rule MUST have a failing fixture for both an import and a fully-qualified reference.

## Verification

`ArchitectureCheckerTest.firebaseImplementationsAreNamedOnlyByWiring` asserts rejection for
`:shared`, `:androidApp`, `:composition:ios` and `:feature:session` with both a direct import and a
fully-qualified reference, and acceptance for `:wiring:firebase`, `:integration:firebase-auth` and a
legal `:shared` import.

The rule was also proved against the real hole: with `:wiring:firebase`'s auth edge switched to
`api` and `:composition:ios` naming `FirebaseAuthClient`, the module compiles the reference but
`architectureCheck` still fails with `:composition:ios: firebase-implementation-outside-wiring` and
`contractCheck` assertion 7 stays green — the two checks see different things, and only this one sees
the leak. Restoring the `implementation` edge and the original source restores a green build.

## References

- `docs/DECISION_BOARD.md` (decision ID `D-179`)
- `docs/CONTRACTS.md §11.6`
- `docs/TECHNICAL_PLAN.md §4`
- `docs/BACKLOG.md` (`E3-08`)
