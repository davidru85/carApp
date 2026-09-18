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
    The mitigation is the no-parsed-class guard: the three named sources are each checked to yield
    at least one `<Name>StateHolder`, so a source that stops being recognised is reported. Adding a
    fourth holder module still requires adding it here, and nothing detects that omission.
  - `STATE_HOLDER` matches a `class …StateHolder` at the start of a line with the known modifiers
    (`public`/`internal`/`private`/`abstract`/`open`/`sealed`/`data`) and an annotation on the same
    line. An unrecognised modifier word, or an annotation whose `@` sits on its own preceding line
    with the `class` on the next, would drop the holder from the check; the no-parsed-class guard
    reports the source rather than passing silently, which is what makes this limit bounded.
  - A declaration body is opened by the first brace at parenthesis depth zero, not by the first
    brace. `class SessionStateHolder internal constructor(… onLocalStartAccepted: () -> Unit = {}, …)`
    put a lambda default before the class body, and taking the first brace parsed an empty body, so
    the class contributed no member and left assertion 14 with nothing to check. Review found this
    live on the repository; `bodyBrace` selects the depth-zero brace and a per-class emptiness guard
    reports any class that still yields no member.
  - `matchingBrace` counts braces without string- or character-literal awareness. A literal
    containing an unbalanced brace inside a guarded block would misplace the body. No such literal
    exists in the guarded sources, and the fix would be the same textual parser growing a scanner.
  - `FUN` matches `fun name(`, `fun <T> name(` and `fun Foo.name(`. It cannot match a declaration
    whose name is on a following line, or a parameter list opened on a following line; such a
    member would drop out of the comparison on both sides, which the emptiness guard reports for
    `AppGraph` and `SwiftAppGraph` but not for an individual holder.
  - `splitTopLevel` ignores the `>` of `->` so a function-typed parameter cannot merge the
    parameters after it. It is still not literal-aware, so a `>` inside a string default would be
    counted.

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

## References

- `docs/DECISION_BOARD.md` (decision ID `D-180`)
- `docs/CONTRACTS.md §11.6`, `§18` (assertions 14, 34 and 35) and `§20.10`
- `docs/BACKLOG.md` (`E3-08`)
