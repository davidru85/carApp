# ADR-0181 - Guard both `AppGraph` surfaces by parsing the contract

## Status

Accepted

## Context

`docs/CONTRACTS.md §18` assertion 14 has been declared since `E0-05` and was never implemented:
"Kotlin-facing `AppGraph` factories take `scope: CoroutineScope`, Swift-facing `SwiftAppGraph`
factories do not, and no exported state-holder function has a Kotlin default argument." Nothing
enforced the Swift-facing surface's shape, and the Objective-C golden header cannot: Kotlin default
arguments do not appear in the generated header at all, so a default added to an exported member
changes no diff and still breaks the Swift call site.

The same blind spot hid a live divergence when `E3-08` started. `E3-03` added
`fun syncStateHolder(scope: CoroutineScope): SyncStateHolder` to the Kotlin-facing `AppGraph` so the
graph could serve the debug diagnostics surface, and the generated header stayed unchanged — because
`AppGraph` is `@HiddenFromObjC`. The interface had eight members and `docs/CONTRACTS.md §20.10`
declared seven.

Three routes were available for each assertion.

## Options Considered

| Option | Benefits | Costs / Risks |
|--------|----------|---------------|
| Option A: implement both assertions in one contract check that parses the `§20.10` code block and the real declarations, comparing them member by member | Assertion 34 enforces the same member list the reviewer reads, so the block becomes the surface definition rather than a second copy that can drift. The parameter text is compared, so a renamed parameter or a changed nullability is a difference | The parser reads braces, parentheses and parameter lists by hand. It must balance delimiters rather than capture with `[^)]*`, because a function-typed parameter would otherwise truncate the list and hide a later default |
| Option B: fix the divergence by editing `§20.10` and implement only assertion 14 | Smaller change | The divergence would be closed but not prevented, and `E3-03` had already shown it happens. A second divergence would be found by a reviewer reading two lists side by side |
| Option C: generate `§20.10` from the sources, so the two cannot differ | The divergence becomes impossible rather than detected | The contract is a normative document that a human reviews and that other documents cite; generating it removes its reviewability, and `docs/CONTRACTS.md` is not assembled from a build step anywhere else |

## Decision

The selected option is: **Option A**, for both assertions.

`SwiftSurfaceContract` is a new contract check registered on `contractCheck`:

- **Assertion 14** requires every Kotlin-facing `AppGraph` state-holder factory to take a `scope`
  parameter, no **exported** `SwiftAppGraph` member to take one, no exported `SwiftAppGraph` member
  to carry a default argument, no exported state-holder class method to carry one, and
  `SwiftAppGraph` to reference no `SyncController`. A default is detected from a parsed parameter
  shape, so a literal, an empty lambda and a constructor call are all caught. A `private` member of
  `SwiftAppGraph` never reaches Swift, so it is filtered out: reporting one would be a false
  positive, and the facade's own `newScopedHolder` takes a scope. A holder source that yields no
  parsed `<Name>StateHolder` class is reported rather than silently dropping out of the check.
- **Assertion 34** requires the `interface AppGraph { … }` block of `docs/CONTRACTS.md §20.10` and
  the real interface to declare the same members, in the same order, with the same parameter
  shapes **including an inline default value**. `§20.10` declares no default, so a default added to
  the interface is a real divergence; because `AppGraph` is hidden from Objective-C export and
  Kotlin defaults never reach the generated header, this comparison is the only place it is
  visible.
- **Assertion 35** applies the same member comparison to the Swift-facing `class SwiftAppGraph`
  block of `§20.10`. The generated header does guard the class, but it is regenerated and committed
  with the change that alters it, so it cannot report that `§20.10` has gone stale; `private`
  members are excluded because they never reach Swift.

The live divergence is closed in the same change by adding `syncStateHolder(scope)` to `§20.10`,
which is a representational clarification of an interface that already shipped, not a behaviour
change.

## Consequences

### Positive

- Both assertions have the failing behaviour they were declared for, and each was proved by mutating
  the real sources: a scoped default on `AppGraph`, a default on an `AppGraph` factory's `scope`, a
  default on `SwiftAppGraph`, a `SwiftAppGraph` member taking a scope, a `SyncController` reference
  and a default on a state-holder method each fail assertion 14; removing `syncStateHolder` from
  `§20.10` fails assertion 34. Every row and its observed message are in the story handoff.
- Every branch that reports a problem has a fixture in `SwiftSurfaceContractTest`, per `D-16`. The
  fixtures fabricate `SwiftSurfaceContract.Inputs` rather than mutating five real files, and each
  asserts the **exact** problem text, so a fixture cannot pass by matching a different failure. One
  fixture mutates the real `AppGraph` source so the default-argument case is proved against the
  repository.
- The `§20.10` block is now compared rather than trusted, so the next interface member added in code
  alone fails the build instead of passing review.
- Assertion 35 applies the same comparison to the Swift-facing block of `§20.10`. The generated
  header is regenerated with the change that alters the class, so it cannot report a stale block;
  `private` members are excluded because they never reach Swift.
- Default arguments are checked at the source, which is the only place they exist. Both an
  `AppGraph` factory's own default and a default on anything `§20.10` also declares are caught: the
  member comparison sees the divergence while `§20.10` differs, and assertion 14 catches the case
  where a default is added to both sides and they agree again.

### Negative

- The parser is textual and reads braces and parentheses itself. It is exercised against the
  `AppGraph` and `SwiftAppGraph` sources it guards on every `contractCheck` run, so a shape it
  cannot parse fails loudly instead of passing: an unparsed side reports "the AppGraph members could
  not be parsed on both sides".
- The state-holder members are discovered by the `<Name>StateHolder` naming convention. A holder
  renamed away from that suffix would stop being checked by assertion 14. The convention is already
  load-bearing for the `§20.10` export list and the golden header.
- **The coverage limits, enumerated after review found they were only covered by one general
  sentence.** Each silently reduces what these assertions see:
  - `HOLDER_SOURCES` hardcodes three files. A state holder added in a new module is not covered.
    The no-parsed-class guard reports a named source that yields no holder at all. It does not
    detect one holder of several going missing, and it cannot see a holder added in a new module, so
    adding a fourth holder module still requires adding it here and nothing detects that omission.
  - `STATE_HOLDER` matches `class <Name>StateHolder` anywhere in the lexically masked source, so no
    modifier word can hide a holder. The pattern previously enumerated seven modifiers, and review
    round 8 proved the no-parsed-class guard did **not** bound that limit: the guard fires only when
    a source yields no holder at all, and each of the three `HOLDER_SOURCES` declares two, so
    `expect class VehicleFormStateHolder` removed a real default argument from assertion 14 while
    every assertion reported `PASS`. Comments and string literals are masked, so prose naming a
    holder is not matched.
  - A declaration body is opened by the first brace at parenthesis depth zero, not by the first
    brace. `class SessionStateHolder internal constructor(… onLocalStartAccepted: () -> Unit = {}, …)`
    put a lambda default before the class body, and taking the first brace parsed an empty body, so
    the class contributed no member and left assertion 14 with nothing to check. Review found this
    live on the repository; `bodyBrace` selects the depth-zero brace and a per-class emptiness guard
    reports any class that still yields no member.
  - `matchingBrace`, `bodyBrace` and `splitTopLevel` count delimiters on masked text, so a brace,
    parenthesis or a `>` inside a comment or a string literal no longer misplaces a body or merges a
    parameter list. The masking covers line and block comments, single-quoted, double-quoted and raw
    strings, escapes and `${…}` templates; it is a lexical mask, not a Kotlin parser, so an unusual
    construct it does not recognise is treated as code.
  - `FUN` matches `fun name(`, `fun <T> name(`, `fun Foo.name(` and a backticked `` fun `name`( ``.
    A declaration whose name or parameter list continues on a following line is still matched,
    because the pattern tolerates whitespace between the tokens. The name is normalized without its
    backticks for the comparison key.
  - The scope parameter is recognised by its declared type `CoroutineScope`, not by the identifier
    `scope`. Review round 4 proved that the name match let
    `SwiftAppGraph.syncStateHolder(coroutineScope: CoroutineScope)` pass assertions 14, 34 and 35
    together once `§20.10` was edited in the same change.
  - A `§20.10` that declares no `interface AppGraph` block reports assertion 34 as a failure, the
    way assertion 35 already reported a missing `class SwiftAppGraph` block. It previously threw
    and aborted the whole `contract-check` report, suppressing every other assertion's result.
  - The member signature is `kind`, `suspend`, name, parameter shapes and declared type. A function
    return type and a property type are compared, because a factory returning another holder is a
    different surface even when its name and parameters are unchanged; before review round 5 the
    comparison stopped at the parameter list and a return-type change left both assertions at
    `PASS`. Review round 9 added `suspend`: it changes the call contract of a member, and on the
    `@HiddenFromObjC` Kotlin-facing surface no other check can see it, so
    `suspend fun syncController(): SyncController` against a `§20.10` declaring
    `fun syncController(): SyncController` left all three assertions at `PASS`. No other declaration
    modifier is modelled: `operator`, `infix`, `inline` and `tailrec` do not change the exported
    call shape, and a function type parameter list (`fun <T> name()`) is not compared, so adding one
    on a single side is not detected. `§20.10` declares none of them today.
  - Order spans both member kinds. Functions and properties are parsed by two scanners and the
    combined list is sorted by a source offset that both producers report in the same character
    space, so `fun A`, `val B`, `fun C` and `fun A`, `fun C`, `val B` compare as the different
    surfaces they are. Before review round 6 the two lists were concatenated, which normalized an
    interleaved contract and a grouped implementation to the same order and passed both assertions.
  - A property without an explicit declared type is not compared. The emptiness guard reports a
    class only when no member is parsed; it does not detect an omitted property beside other parsed
    members.
  - Visibility is classified four ways and only `public` members reach the comparison. An `internal`
    or `protected` helper is not exported to Swift, so it is neither compared against `§20.10` nor
    held to the scope and default-argument rules. Before review round 5 only `private` was filtered,
    which compared `internal` helpers that the header can never show. The classification reads
    lexical declaration text with comments, annotations and literals masked, so a word such as
    `internal` inside an annotation message cannot classify a public member. Functions are direct
    members only: a local function or a lambda body is excluded by brace depth, and a commented-out
    declaration is not a member at all. Repeated legal modifiers (`public final val`) and qualified
    annotations are recognised on properties.
  - A malformed `§20.10` block — one whose braces never close — reports assertion 34 or 35 as a
    failure with an unbalanced-block diagnostic, so `validate()` always returns results for 14, 34
    and 35 instead of throwing and truncating the `contract-check` report.
  - A public property carrying `abstract`, `expect`, `actual`, `external` or `inline` was invisible
    to assertions 34 and 35, because `PROPERTY` enumerated its modifiers. The list now carries them.
  - A declaration is located by a whole-word match, not by `indexOf`. A class whose name extends a
    holder's name — `SessionStateHolderShim` before `SessionStateHolder` — shadowed the real body
    and removed its members from assertion 14.
  - `propertyMembers` tracks parenthesis depth as well as brace depth. The constructor parameters of
    a nested class sit at brace depth zero, so their `val`s were reported as public members of the
    enclosing declaration.
  - An accessor or a delegate written on the declaration line is not part of the declared type. The
    signature `val isClosed: Boolean get()` could not equal any `§20.10` spelling, so such a
    property could never be declared in the contract.
  - A leading `context(…)` clause is stripped with the annotations. It put a `(` before the keyword,
    which `TOP_LEVEL_DECLARATION` cannot cross, so the declaration was skipped with no report.
  - The `:wiring:firebase` rule still fails open on a column-zero line it cannot classify: it
    reports nothing for such a line rather than reporting that it could not parse it.

### Constraints Introduced

- `docs/CONTRACTS.md §20.10` and the real Kotlin-facing `AppGraph` MUST declare the same members in
  the same order; `§11.6` names assertion 34 as the check that keeps them equal.
- `docs/CONTRACTS.md §20.10` and the real `SwiftAppGraph` MUST declare the same exported members in
  the same order; `§11.6` names assertion 35 as the check that keeps them equal.
- A public `@HiddenFromObjC` member of an exported state-holder class MUST be declared in `§20.10`
  with that annotation.
- No exported state-holder member and no `SwiftAppGraph` member MAY carry a Kotlin default argument.
- `SwiftAppGraph` MUST NOT expose `SyncController`.

## Verification

`SwiftSurfaceContractTest` holds one fixture per problem branch of the three assertions plus
`contractCheckGuardsTheSwiftFacingSurface`, which runs the repository's real `contractCheck` and
requires assertions 14, 34 and 35 to be present and `PASS`, so the fixtures cannot drift from the
repository they guard. Mutation evidence is recorded in the story handoff; every mutation failed
the named check with the offending member in the message.
`aHolderWithALambdaDefaultInItsConstructorStillHasItsMembersChecked` and
`aHolderClassWithNoParsedMemberIsReported` cover the constructor-brace defect and the per-class
emptiness guard.
`aKotlinFacingReturnTypeChangeIsRejected` and `aSwiftFacingReturnTypeChangeIsRejected` prove the
declared type is compared on both surfaces; the four property fixtures prove a `val` present on only
one side is a divergence and `aPropertyKindChangeIsRejected` proves a `var` is not a `val`; the four
visibility fixtures prove an `internal` or `protected` helper is neither compared nor held to the
scope and default rules.
`anInterleavedFunctionAndPropertyOrderIsRejectedOnTheKotlinFacingSurface` and its Swift-facing
counterpart prove a function property function order is distinguished from a function function
property order on both surfaces.

`Pr71ReviewRegressionTest` holds the ten regressions the PR #71 review reproduced, one per defect:
annotation messages cannot set visibility, qualified annotations and literal parentheses cannot hide
a wiring declaration, an inner assignment or a literal is not a property's Koin initialiser, a local
function or a commented-out declaration is not a member, repeated modifiers and qualified
annotations cannot hide a property, an escaped function name still reports its default argument,
expect/actual is not exempted by the Koin rule, and a malformed contract block returns a FAIL result
instead of aborting the report.

`aKotlinFacingSuspendModifierIsPartOfTheComparedSurface` and
`aSwiftFacingSuspendModifierIsPartOfTheComparedSurface` prove `suspend` is compared on both
surfaces.

`Pr71ReviewRegressionTest` also holds the six regressions of review round 8: an unrecognised class
modifier no longer hides a state holder, an unrecognised property modifier no longer hides an
exported member, a name-extending class no longer shadows a holder body, an accessor on the
declaration line is no longer part of the type, a nested class's constructor parameters are no longer
members of the enclosing declaration, and a `context(…)` clause no longer hides a wiring declaration.

## References

- `docs/DECISION_BOARD.md` (decision ID `D-180`)
- `docs/CONTRACTS.md §11.6`, `§18` (assertions 14, 34 and 35) and `§20.10`
- `docs/BACKLOG.md` (`E3-08`)
