# ADR-0044 - Provider Exclusion Uses a Gradle Property

## Status

Accepted

## Context

The provider-decoupling proof must be identical locally and in CI. A second settings file or a
generated CI-only build would duplicate or obscure the real module graph.

## Options Considered

| Option | Benefits | Costs / Risks |
|--------|----------|---------------|
| Gradle property in the canonical settings file | One graph definition; reproducible locally and in CI. | Adds a small conditional settings branch. |
| Separate settings file | Visual separation. | Duplicates repositories, plugin management and module declarations. |
| Generate temporary settings in CI | Leaves the main file unconditional. | Harder to reproduce and fragile under graph changes. |

## Decision

The selected option is the Gradle property `carapp.excludeFirebaseProviders=true`, consumed by
the canonical `settings.gradle.kts`.

## Consequences

### Positive

- Local and CI proofs execute the same build model.
- Provider exclusion cannot drift into a second settings file.

### Negative

- Settings evaluation has one explicit mode flag.

### Constraints Introduced

- The property name and `true` value are stable CI inputs.
- Provider-decoupling verification MUST use the canonical settings file.
- A separate provider-free settings file is forbidden.

## Verification

- A functional fixture evaluates both normal and excluded settings modes.
- CI invokes the provider-free build with the property set to `true`.

## References

- `docs/DECISION_BOARD.md` (decision ID `D-43`)
- `docs/SPECIFICATION.md §8.5`
- `docs/TECHNICAL_PLAN.md §5`
- `docs/BACKLOG.md` (`E3-06`)
