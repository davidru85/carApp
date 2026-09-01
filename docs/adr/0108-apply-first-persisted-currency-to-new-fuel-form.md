# ADR-0108 / D-107 - Apply the First Persisted Currency to a New Fuel Entry Form

## Status

Accepted

Selected by the owner on 2026-09-01 during story E1-10.

## Context

D-94 gave a new Fuel Entry form an immediate locale-derived currency while E1-10 was pending.
E1-10 now supplies a persisted device-local currency asynchronously. A form still needs an
immediate fallback, but persistence must replace that fallback exactly once without overwriting an
explicit user edit. A settings change made elsewhere after the form opens must apply to the next
form, not mutate the currency of the form already in progress. Edit forms must preserve the stored
Fuel Entry currency unconditionally.

The synchronous Kotlin and Swift holder factories must remain unchanged, and applying the
persisted currency must continue to recompute any live money projection under the selected unit.

## Options Considered

| Option | Benefits | Costs / Risks |
|--------|----------|---------------|
| Holder-owned first persisted currency with locale fallback and edit protection | Reuses the holder lifecycle; keeps synchronous factories; gives each form a stable snapshot; avoids graph-global presentation state | A new form can briefly expose the locale fallback; each new form starts one short-lived collector |
| Graph-wide shared settings `StateFlow` | Uses one database collector; later consumers can reuse a cached current value | Adds graph state and lifecycle complexity; needs fallback/error semantics; still needs per-form edit protection and snapshotting |
| Suspended one-shot read before holder construction | Constructs the form with one exact persisted snapshot; needs no holder collector | Makes graph and Swift holder factories asynchronous; widens the ABI and host orchestration for a local default |

## Decision

`FuelEntryFormStateHolder` owns a short-lived read of `settingsCurrencyCode` only when
`entryId == null`. The locale-derived D-94 value remains the initial currency. The holder consumes
the first persisted currency, applies it only if the user has not edited currency, calls
`resolveLiveMoney()` after application, and then terminates collection. Later settings emissions
cannot mutate that open form.

When `entryId != null`, the holder never consumes settings currency; loading the existing Fuel
Entry remains the sole source of its currency.

## Consequences

### Positive

- Each new form gets the current persisted default once and remains stable while being edited.
- A fast explicit edit always wins over delayed persistence.
- Existing Fuel Entries cannot be rewritten or visually changed by settings observation.
- No asynchronous factory or graph-global presentation state is introduced.

### Negative

- The locale fallback may be visible briefly before SQLDelight emits.
- Every new form performs one short database-backed collection.

### Constraints Introduced

- New forms MUST consume no more than the first successful persisted currency.
- Later settings changes MUST NOT mutate an already-open form.
- The locale-derived currency MUST remain the initial fallback.
- `currencyEdited` MUST prevent a delayed persisted value from replacing an explicit edit.
- Applying persisted currency MUST call `resolveLiveMoney()`.
- Edit forms MUST NOT consume or apply settings currency.

## Verification

- `persistedCurrencyReplacesLocaleFallbackOnANewForm` proves first-value application.
- `explicitCurrencyEditBeforePersistedValueArrivesIsNotOverwritten` proves edit precedence.
- `existingEntryCurrencyIsNeverReplacedBySettings` proves edit-form isolation.
- `laterSettingsChangesDoNotMutateAnOpenCreationForm` proves the collector terminates after one
  value.

## References

- `docs/BACKLOG.md` E1-10
- `docs/CONTRACTS.md` §11.5, §12 and §20.5
- ADR-0095 / D-94
