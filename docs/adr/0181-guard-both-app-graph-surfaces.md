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
  parameter, no `SwiftAppGraph` member to take one, no `SwiftAppGraph` member to carry a default
  argument, no state-holder class method to carry one, and `SwiftAppGraph` to reference no
  `SyncController`. A default is detected by the presence of `=` in the parameter text, of any
  shape, so a literal, an empty lambda and a constructor call are all caught.
- **Assertion 34** requires the `interface AppGraph { … }` block of `docs/CONTRACTS.md §20.10` and
  the real interface to declare the same members, in the same order, with the same
  `name(parameter: Type)` signatures.

The live divergence is closed in the same change by adding `syncStateHolder(scope)` to `§20.10`,
which is a representational clarification of an interface that already shipped, not a behaviour
change.

## Consequences

### Positive

- Both assertions have the failing behaviour they were declared for, and each was proved by mutating
  the real sources: a scoped default on `AppGraph`, a default on `SwiftAppGraph`, a default on a
  state-holder method, and a `SyncController` member each fail assertion 14; removing
  `syncStateHolder` from `§20.10` fails assertion 34.
- The `§20.10` block is now compared rather than trusted, so the next interface member added in code
  alone fails the build instead of passing review.
- Default arguments are checked at the source, which is the only place they exist.

### Negative

- The parser is textual and reads braces and parentheses itself. It is exercised against the five
  real files it guards on every `contractCheck` run, so a shape it cannot parse fails loudly instead
  of passing: an unparsed side reports "the AppGraph members could not be parsed on both sides".
- The state-holder members are discovered by the `<Name>StateHolder` naming convention. A holder
  renamed away from that suffix would stop being checked by assertion 14. The convention is already
  load-bearing for the `§20.10` export list and the golden header.

### Constraints Introduced

- `docs/CONTRACTS.md §20.10` and the real Kotlin-facing `AppGraph` MUST declare the same members in
  the same order; `§11.6` names assertion 34 as the check that keeps them equal.
- No exported state-holder member and no `SwiftAppGraph` member MAY carry a Kotlin default argument.
- `SwiftAppGraph` MUST NOT expose `SyncController`.

## Verification

`SwiftSurfaceContractTest.contractCheckGuardsTheSwiftFacingSurface` runs the repository's real
`contractCheck` and requires assertions 14 and 34 to be present and `PASS`. Mutation evidence is
recorded in the story handoff; the four assertion-14 mutations and the assertion-34 mutation each
failed the check with the offending member named.

## References

- `docs/DECISION_BOARD.md` (decision ID `D-180`)
- `docs/CONTRACTS.md §11.6`, `§18` (assertions 14 and 34) and `§20.10`
- `docs/BACKLOG.md` (`E3-08`)
