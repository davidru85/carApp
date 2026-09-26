# ADR-0192 - Put the `§11.6` hidden-member check in the contract check

## Status

Accepted

Owner decision taken on 2026-09-25.

## Context

`docs/CONTRACTS.md §11.6` states: a public member of an exported state-holder class that is
`@HiddenFromObjC` is still declared in `§20.10`, carrying that annotation. The generated Objective-C
header cannot show a hidden member, so leaving one undeclared makes the contract and the code diverge
with nothing able to see it.

`E3-08` closed the same divergence on the Kotlin-facing `AppGraph` and recorded this one as a
deferral with `E3-05` named as its expected owner, because `E3-05` is the first remaining story that
renders a state-holder-backed surface and therefore the first place a hidden member is plausibly
added. Two such members exist today, both in `FuelEntryFormStateHolder`:
`isLoading` and `observeSaveCompletions()`. Review round 2 of pull request #71 declared both by hand,
so no live divergence remained — but the rule stayed prose, and the next hidden member added to an
exported holder could go undeclared with nothing failing.

The open question `E3-08` recorded was where the check lives: inside `SwiftSurfaceContract`, beside
assertions 14, 34 and 35, or among the `D-16` Konsist package rules.

## Options Considered

| Option | Benefits | Costs / Risks |
|--------|----------|---------------|
| Option A: `SwiftSurfaceContract`, inside `contractCheck`, beside assertions 34 and 35 | It is the only site that already reads both the `§20.10` markdown blocks and the Kotlin holder sources, which is exactly the comparison `§11.6` defines. It reuses the existing parser, the existing `AssertionResult` reporting and the existing fixture convention, and `contractCheck` already fails the build on any failed assertion | `contractCheck` is a gated path, so the check is reviewed with the owner. The parser's limits are the ones ADR-0181 already enumerates |
| Option B: the `D-16` Konsist rules | Package rules live together | Konsist parses Kotlin only. It cannot read `docs/CONTRACTS.md`, so it cannot compare the code with `§20.10`; the rule it would implement is not the rule `§11.6` states. `D-185` had already reached the same conclusion for the iOS trigger ban and abandoned the declared Konsist fixture for a source rule |
| Option C: leave the check unexecutable and record the debt | No new check to maintain | `AGENTS.md §Architecture Rules` requires every architecture rule to be an executable check with a failing fixture, and criterion 4 of `E3-05` is precisely this. The rule would remain one a reviewer has to remember |

## Decision

The selected option is: **Option A**.

The executable check is a third assertion returned by `SwiftSurfaceContract`, reported as `§18`
assertion 36. For every state-holder class declared in the three holder sources:

- a **public** member carrying `@HiddenFromObjC` MUST appear in the class's `§20.10` block with the
  same signature and with the annotation;
- a **`§20.10`** member carrying `@HiddenFromObjC` MUST be a public hidden member of the class;
- for every holder class that declares hidden members, `§20.10` MUST declare that class's block; and
- a contract that declares no state-holder class block at all is reported, because the comparison
  could not run on it.

`internal` and `private` members are outside the rule, as are the top-level `@HiddenFromObjC`
`createXStateHolder` factories, which are not class members.

## Consequences

### Positive

- The rule becomes executable with a failing fixture per problem branch, which is what `D-16` and
  `AGENTS.md §Architecture Rules` require of an architecture rule.
- The third direction is covered too: a `§20.10` block that declares a hidden member the class does
  not implement, or declares it without the annotation, now fails as well. A one-sided check would
  have accepted a contract that describes a surface the code does not have.
- The comparison is source-level, so it fires before the expensive framework link that the golden
  header depends on.

### Negative

- `SwiftSurfaceContract` grows a fourth comparison and the accompanying fixtures. It is reported
  separately as assertion 36, so a failure names one rule rather than merging into 34 or 35.
- The parser's coverage limits of ADR-0181 now apply to this comparison too. They are the same
  limits: an untyped property is not compared, recognition is textual, and the holder source list is
  the same three files assertion 14 uses.

### Constraints Introduced

- A public `@HiddenFromObjC` member of an exported state-holder class MUST be declared in `§20.10`
  carrying the annotation.
- The check MUST live in `SwiftSurfaceContract` and MUST be reported as `§18` assertion 36.
- The check MUST have a failing fixture for every problem branch, and the fixtures MUST assert the
  exact problem text.
- The state-holder sources the check reads MUST be the same list assertion 14 already reads, so a new
  exported holder cannot enter one comparison without the other.

## Verification

`SwiftHiddenMemberContractTest` runs the real `contractCheck` for assertion 36 and then covers each
problem branch by mutating the real `§20.10` text or the real holder sources: a hidden property and a
hidden function absent from the contract, a hidden member carrying another modifier, a contract member
missing the annotation, a contract member the class does not implement, a holder whose block is
missing, a contract with no holder block, an `internal` and a `private` hidden member, that annotation
inside a comment and a string literal, a hidden member in a second holder source, and a brace, a
semicolon or a string template inside the previous member's string literal, which must not hide the
annotation. Evidence is in `docs/handoff-E3-05.md`.

## References

- `docs/DECISION_BOARD.md` (decision ID `D-191`)
- `docs/CONTRACTS.md §11.6`, `§18` assertion 36 and `§20.10`
- `docs/adr/0181-guard-both-app-graph-surfaces.md`
- `docs/adr/0186-enforce-the-ios-trigger-ban-as-a-source-rule.md`
- `docs/handoff-E3-08.md`
