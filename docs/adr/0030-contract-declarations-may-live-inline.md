# ADR-0030 / D-29 - Contract Types May Be Declared Inline, Not Only in §20

## Status

Accepted

Accepted by the owner on 2026-08-21.

## Context

`docs/CONTRACTS.md §20` opened with "Every type referenced by a signature in this document is declared here", and `§18` assertion 1 asserted that every project-owned type named in a code block is declared in `§20`.

`E0-05` implemented that assertion and it failed. Nine types are declared by the section that owns them instead: `Logger` in `§17`, `AnalyticsTracker` in `§16.1`, `RemoteSyncSource` in `§10`, `AuthClient` and `AppGraphDependencies` in `§11`, `VehicleRepository`, `FuelEntryRepository` and `SettingsRepository` in `§12`, and `CalculateConsumption` in `§13`.

Separately, two `kotlin` fences in `§20.9` contained explanatory paragraphs rather than code. They cannot be compiled or copied, and they made the checker read English words as type names.

Nothing was ambiguous for an implementer: every type is declared, and the sections that declare inline do so beside the prose that constrains them.

## Options Considered

| Option | Benefits | Costs / Risks |
|--------|----------|---------------|
| Reword the assertion and the `§20` opener; lift the prose out of the fences | Small, and honest about what the check does. The inline declarations stay next to the prose that gives them meaning. | `§20` is no longer the single place to look for a declaration. |
| Move all nine declarations into `§20` | `§20` becomes literally complete and the assertion can be enforced exactly as first worded. | A large edit to a gated document with real transcription risk, for a problem no implementer hits, and it separates each declaration from the rules that constrain it. |
| Leave both | No work. | `contract-check` reports `PENDING` forever, which trains readers to ignore it. |

## Decision

A type may be declared either in `§20` or inline in the section that owns it. `§18` assertion 1 now asserts that every project-owned type named in a code block is declared **somewhere in the document**, and `§20`'s opening sentence reads "Every type this document does not declare inline is declared here", followed by the list of the sections that declare inline.

The two prose paragraphs in `§20.9` were moved below the fence.

## Consequences

### Positive

- The assertion runs and passes, so it is a real check rather than a permanent `PENDING`.
- Declarations stay beside the constraints that explain them.

### Negative

- A reader looking for a type checks `§20` first and then the owning section. `§20` now names those sections, so the search is bounded.

### Constraints Introduced

- A `kotlin` fence in `docs/CONTRACTS.md` MUST contain only code. `contract-check` reports any prose line inside one.

## Verification

- `contract-check` assertion 1 passes, and the prose-in-fence report is clean.

## References

- `docs/DECISION_BOARD.md` (`D-29`)
- `docs/CONTRACTS.md §18`, `§20`, `§20.9`
- `docs/handoff-E0-05.md`
