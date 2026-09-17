# ADR-0179 - Define `:wiring:firebase` product logic as a declaration shape

## Status

Accepted

## Context

`docs/TECHNICAL_PLAN.md §4` states that `:wiring:firebase` forbids "product logic" and defines it
checkably: "every top-level declaration there MUST be a Koin `Module`, a factory returning an
abstraction, or a platform initialiser. No use cases, repositories, mappers, validation or business
`expect`/`actual`."

That definition was prose only. `E0-04` built the architecture check from the dependency table and
recorded the gap: "the `:wiring:firebase` 'product logic' rule needs a Kotlin declaration parser and
the module itself, so it belongs with `E3-08`" (`docs/handoff-E0-04.md`). `E0-07` then created the
module and left the rule unimplemented, so until now a repository, mapper or use case could be
declared in the composition root with nothing failing.

The available data is what `ArchitectureCheckPlugin` already collects: the module's source lines,
comment-stripped, with no AST. A rule over that data has to decide, from the text of a column-zero
line, whether the declaration it introduces is one of the three admitted shapes.

## Options Considered

| Option | Benefits | Costs / Risks |
|--------|----------|---------------|
| Option A: reject every non-function, non-module top-level declaration, at any visibility | A mapper, repository or use case is rejected even when it is `private`, which is what the prose names; no exemption list to drift | Rejects a private tuning constant, so the module's own data must live inside the factories or a companion |
| Option B: reject only `internal`/public product-shaped declarations, admit everything `private` | Never trips on a legitimate private helper | A private mapper or repository passes, and the prose explicitly names mappers and repositories as forbidden. The rule would enforce visibility instead of shape, which is not what `§4` says |
| Option C: require an AST, adding a Kotlin parsing dependency to the build logic | Exact declaration shapes | A new build-logic dependency for one rule, and the checker's other rules are all line-based pure functions over `ModuleUnderCheck`; mixing models for one rule costs more than it buys |

## Decision

The selected option is: **Option A.**

Every column-zero declaration in `:wiring:firebase` is classified from its keyword. A function is
admitted, because a factory and a platform initialiser are both functions and the private ones are
the helpers of the factories in the same file. A `val`/`var` is admitted when it is `private`, which
is the wiring module's own tuning data, and rejected otherwise. A Koin `Module` is admitted by its
declared type or its `module { … }` initialiser. Every other declaration — `class`, `interface`,
`object`, `enum class`, `typealias`, `fun interface`, and any `expect`/`actual` — is rejected, at
any visibility, because none of them is one of the three admitted shapes.

Indentation is the top-level test: only column-zero lines are inspected, so a member of an object
expression or of a class the rule already rejected is not re-reported. `fun interface` is classified
as an interface, because the `fun` there modifies a type declaration rather than introducing the
abstraction factory `§4` admits.

## Consequences

### Positive

- The `§4` definition becomes executable, and the check reports the offending declaration and line.
- The admitted shapes are exactly the three the table names, so the rule cannot silently widen.
- `expect`/`actual` is rejected in every shape, which is the clause of the sentence that is easiest
  to forget when reading "every top-level declaration".

### Negative

- A private tuning constant must be written inside a factory or a companion rather than at top
  level. `:wiring:firebase` has fourteen such constants today, and they are all `private const val`
  so they pass, but a future non-constant private property would not.
- The classification is textual. A declaration whose keyword the regular expression cannot see is
  not reported. That is bounded by the rule's direction: a missed declaration fails open, and the
  `:wiring:firebase` source is a small, reviewed file.

### Constraints Introduced

- `:wiring:firebase` MUST NOT declare a top-level `class`, `interface`, `object`, `enum class`,
  `typealias`, `fun interface`, or a non-private top-level property.
- `:wiring:firebase` MUST NOT declare `expect`/`actual` in any shape.
- The rule MUST have a failing fixture per rejected shape, per `D-16`.

## Verification

`ArchitectureCheckerTest.wiringFirebaseDeclarationsAreBoundedToModulesFactoriesAndInitialisers`
asserts rejection for twelve shapes and acceptance for eight, including every shape the real module
uses today. The rule was also mutated against the real repository: appending
`internal data class StrayMapping(val id: String)` to `FirebaseAppProviders.kt` makes
`architectureCheck` fail with `:wiring:firebase: wiring-product-logic` and removing it restores a
green build.

## References

- `docs/DECISION_BOARD.md` (decision ID `D-178`)
- `docs/TECHNICAL_PLAN.md §4` and `§4.1`
- `docs/CONTRACTS.md §11.6`
- `docs/BACKLOG.md` (`E3-08`)
- `docs/handoff-E0-04.md` (the rule this decision implements)
