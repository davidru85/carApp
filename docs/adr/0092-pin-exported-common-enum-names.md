# ADR-0092 / D-91 - Pin Exported Common Enum Names

## Status

Accepted

Accepted for the E1-07 owner code-review correction on 2026-08-31.

## Context

Exporting `:core:common` directly from the sole iOS framework producer changed three enum names
from module-prefixed Objective-C and Swift names to Kotlin-matching names. Unlike the other moved
presentation declarations, `Confirmation`, `AuthProvider` and `SyncTrigger` had no explicit
`@ObjCName`, so their ABI names depended on framework export configuration.

No Swift source under `iosApp/` consumes either spelling yet. The owner nevertheless requires the
rename to be intentional, recorded and stable rather than an accidental golden-header update.

## Options Considered

| Option | Benefits | Costs / Risks |
|--------|----------|---------------|
| Pin Kotlin-matching names with exact annotations | Produces concise Swift names, matches the Kotlin vocabulary and removes module-configuration dependence before any Swift consumer exists. | Intentionally records a rename from the pre-E1-07 golden. |
| Pin the previous module-prefixed names | Preserves the old generated ABI literally. | Keeps artificial `Common` prefixes that no Swift source consumes and diverges from the exported Kotlin type names. |
| Keep module-derived names without annotations | Requires no source annotations. | Future export changes can silently rename public types again, violating the stable ABI contract. |

## Decision

The three exported enums use exact Objective-C naming annotations:

- `SharedConfirmation` with Swift name `Confirmation`;
- `SharedAuthProvider` with Swift name `AuthProvider`;
- `SharedSyncTrigger` with Swift name `SyncTrigger`.

These are intentional E1-07 ABI names. Their enum cases retain the generated Kotlin case mapping.

## Consequences

### Positive

- Framework export configuration can no longer rename the three enum classes.
- Swift receives concise names aligned with the canonical Kotlin vocabulary.
- The contract and golden header identify the rename as intentional.

### Negative

- A consumer built against the pre-E1-07 golden would need to adopt the new names.
- Each exported enum declaration carries an experimental Kotlin/Native annotation opt-in.

### Constraints Introduced

- The three exact Objective-C and Swift names MUST remain listed in `docs/CONTRACTS.md §15.3`.
- A source-level contract test MUST fail if any exact annotation is removed or changed.
- A future rename requires a superseding decision and golden-header review.

## Verification

- `IosCompositionContractTest` checks all three exact source annotations.
- The regenerated Objective-C header contains the contract names.
- `objc-header-golden-check` remains green.

## References

- ADR-0086 / D-85
- `docs/CONTRACTS.md §15.3`, `§20.2`, `§20.3`
- `docs/BACKLOG.md` E1-07
