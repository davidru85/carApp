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
declared type, matched **exactly** — `Module`, `Module?` or the fully-qualified
`org.koin.core.module.Module`, never a type whose name merely begins with `Module`, so
`ModuleRegistry` and `ModuleUsage` are rejected — or by a `module { … }` initialiser that is the
initialiser of the declaration (`=\s*module\s*\{`), never an incidental mention of `module {`
inside an unrelated expression. Every other declaration — `class`, `interface`,
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

- Private top-level properties, including non-constant properties, are admitted as wiring-owned
  data. Type declarations remain forbidden at every visibility. The Koin property exemption never
  overrides the expect/actual prohibition: both are evaluated before it can apply, so
  `expect val bindings: Module` and `actual val bindings: Module = module { }` are reported.
- The classification is textual, and its limits are enumerated rather than left to one general
  sentence:
  - Every leading annotation is removed before matching by a lexical scanner that masks comments,
    string and character literals while preserving character offsets, so a use-site target
    (`@get:JvmName("x")`), a qualified name (`@kotlin.Deprecated("x")`) and nested parentheses
    (`@Deprecated("x", ReplaceWith("y"))`) no longer leave the `@` on the line and hide the whole
    declaration. Because delimiters are counted on masked text, a parenthesis inside a literal is
    not a delimiter either. Review round 4 found the nested shape passing silently and the review of
    PR #71 found the qualified and literal-parenthesis shapes doing the same. A line carrying only
    an annotation still parses as no declaration, which is correct, and an annotation whose
    parenthesis never closes on the line is left untouched and parses as no declaration.
  - The Koin exemption applies only to a `val`/`var`. A type declaration that inherits a type named
    `Module` — `class FirebaseWiring : Module` — is a `class` and is rejected.
  - The `module { … }` initialiser is matched on the text after the declaration's own assignment,
    anchored to the start of that initialiser and masked of literals and comments, so
    `val leaked = run { val bindings = module { }; 1 }` and `val leaked = "= module {"` are not
    bindings. The exemption also still requires the declared type to be exactly `Module`.
  - A declaration whose keyword the regular expression still cannot see is not reported. The rule
    fails open by direction and `:wiring:firebase` is a single reviewed file.
- The violation message names the declaration in source order, the way it is written:
  `internal class StrayMapper`, `enum class StrayMode`, `fun interface StrayCallback`. The earlier
  shape interleaved the declared name between the two keyword words and produced
  `declares enum  StrayMode class`; the fixtures now assert the message text, not only the rule id.

### Constraints Introduced

- `:wiring:firebase` MUST NOT declare a top-level `class`, `interface`, `object`, `enum class`,
  `typealias`, `fun interface`, or a non-private top-level property.
- `:wiring:firebase` MUST NOT declare `expect`/`actual` in any shape.
- The rule MUST have a failing fixture per rejected shape, per `D-16`.

## Verification

`ArchitectureCheckerTest.wiringFirebaseDeclarationsAreBoundedToModulesFactoriesAndInitialisers`
asserts rejection for fourteen shapes and acceptance for ten, including every shape the real module
uses today, and `koinModuleMatchingIsExactRatherThanAPrefixOrAMention` rejects
`val moduleRegistry: ModuleRegistry`, `var modulesUsed: ModuleUsage`, `val suffixed: ModuleWiring`
and a line that mentions `module {` in an unrelated expression.
`theViolationNamesTheDeclaredTypeInSourceOrder` asserts the message text for `class`, `enum class`,
`fun interface` and a property.
`aSupertypeNamedModuleAndAnAnnotatedDeclarationDoNotEscapeTheRule` rejects a `class`, an `object` and
an `interface` inheriting `Module`, an annotated `class` and an annotated non-private property, and
accepts an annotated Koin binding and an annotated private factory.
`anAnnotationWithNestedParenthesesOrAUseSiteTargetDoesNotHideTheDeclaration` rejects a `class`
annotated with nested parentheses, a property carrying a use-site target, and a doubly annotated
`object`, and accepts a use-site-targeted Koin binding.

The rule was also mutated against the real repository: appending `internal class StrayMapper` to
`FirebaseAppProviders.kt` makes `architectureCheck` fail with
`:wiring:firebase: wiring-product-logic` and the message `declares internal class StrayMapper`;
`internal val moduleRegistry: ModuleRegistry = ModuleRegistry()` and
`internal val mentioned = … + module { }` each fail with the property named. Removing each mutation
restores a green build. The rows and their observed output are in the story handoff.

## References

- `docs/DECISION_BOARD.md` (decision ID `D-178`)
- `docs/TECHNICAL_PLAN.md §4` and `§4.1`
- `docs/CONTRACTS.md §11.6`
- `docs/BACKLOG.md` (`E3-08`)
- `docs/handoff-E0-04.md` (the rule this decision implements)
