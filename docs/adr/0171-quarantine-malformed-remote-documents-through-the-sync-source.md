# ADR-0171 / D-170 - Malformed Remote Documents Cannot Be Quarantined Through `RemoteSyncSource`

## Status

Proposed

No option is selected here. The owner chooses before `E3-03` implements the pull-cycle quarantine
behavior of `docs/CONTRACTS.md §9.5`.

## Context

`docs/CONTRACTS.md` line 403 is normative and unconditional:

> A pull transaction MUST NOT fail because of a domain constraint or malformed remote payload.
> Documents that cannot be safely applied are quarantined (§9.5); the only legal pull-cycle
> failures are remote I/O, local persistence failure or failure to persist quarantine.

`§9.5` requires a document that cannot be safely applied to be stored verbatim in a `quarantine`
table, not applied to the entity table, and not to block cursor advance once the quarantine row is
committed. Its quarantine reasons are `UnsupportedSchemaVersion` and `MalformedPayload`, where the
latter covers a missing required field, an unknown enum value, a nullability violation, a primitive
type mismatch, an out-of-range value, a `deleted` / `deletedAt` inconsistency, a document ID /
payload ID mismatch, a malformed JSON payload, or any document that cannot be deserialized into the
supported DTO.

`RemoteSyncSource.pullChanges` returns `Outcome<RemotePage, RemoteError>` and `RemotePage.items` is
`List<RemoteSnapshot>` (`§20.7`). A `RemoteSnapshot` requires a well-formed `entityId`,
`schemaVersion`, `serverUpdatedAt` and `deleted`. The type cannot represent a document that failed
validation, so a `:core:sync` engine built on this interface can never observe one, and the
`MalformedPayload` quarantine of `§9.5` is unimplementable through this contract.

`FirebaseRemoteSyncSource.pullChanges` therefore cannot honour line 403 on three concrete paths.

### Failure path 1 - document ID / payload ID mismatch fails the whole page

`toRemoteSnapshot` begins with `require(fields.getValue(ID_FIELD) == FirestoreString(id))`. One
document whose payload ID disagrees with its document ID throws `IllegalArgumentException`, which
`runRemoteOperation` catches and maps to `Outcome.Err(RemoteError.InvalidArgument)`. The
**entire page** fails, including every valid document on it. `§9.5` lists exactly this condition
("document ID / payload ID mismatch") as a `MalformedPayload` quarantine reason, not as a cycle
failure.

### Failure path 2 - a deserialization failure escapes the closed `Outcome` API

`toFirestoreDocument` reads every field with a typed `get<T>()`. A missing required field or a
primitive type mismatch raises a provider deserialization failure. That failure is caught by no
clause on the path: `runProviderOperation` catches only `FirebaseFirestoreException` and
`FirebaseException`; `runRemoteOperation` and `refreshAndRetry` catch only
`FirestoreGatewayException` and `IllegalArgumentException`. The throwable therefore escapes
`pullChanges` as an unchecked exception, breaking the closed `Outcome` API and the "MUST NOT throw
for expected failures" rule of `§6`. `§9.5` lists "missing a required field" and "primitive type
mismatch" as quarantine reasons.

### Failure path 3 - the output type cannot carry a rejected document

Even when a document converts without throwing, `RemotePage.items` has no representation for a
document that failed validation. The engine sees only well-formed `RemoteSnapshot`s, so it has
nothing to quarantine and no signal that a document was dropped or coerced. `UnsupportedSchemaVersion`
is the one `§9.5` reason that survives this shape, because a document with `schemaVersion = 2`
converts cleanly into a `RemoteSnapshot` and reaches the engine, which can then quarantine it.

### Reachability of the `§9.5` reasons today

- `UnsupportedSchemaVersion`: **reachable.** A higher-version document with the expected fields
  converts to a `RemoteSnapshot` and the engine can quarantine it.
- `MalformedPayload`: **not reachable.** Its sub-cases either fail the whole page (path 1), escape
  the closed API entirely (path 2), or have no representable output (path 3). The escalation is
  about `MalformedPayload` only. An ADR that overstates the defect is as wrong as one that
  understates it.

### This is pre-existing

`toRemoteSnapshot` and `toFirestoreDocument` were staged by `E0-07` and are untouched by `E3-02`.
This is not a regression introduced by `E3-02`; it surfaces now because `E3-02` is the story that
declares the Firebase `RemoteSyncSource` complete, and `E3-03` is the first consumer that must
implement `§9.5`.

## Options Considered

| Option | Benefits | Costs / Risks |
|--------|----------|---------------|
| A. `pullChanges` returns raw per-document results | The engine receives each raw document, validates it itself and writes the quarantine row in the same local transaction that processes the page, exactly as `§9.5` requires. Classification stays in one place (`:core:sync`) and the source stays a thin transport. | Changes `RemotePage` / `§20.7`: `items` can no longer be `List<RemoteSnapshot>`. Either `RemotePage` carries raw documents beside valid snapshots or `RemoteSyncSource` gains a raw-document result type. The engine absorbs the schema-aware validation now duplicated in the integration, and the DTO change touches `:core:sync`, the golden Swift surface if exposed, and both acceptance tests. |
| B. The source classifies and returns a per-document quarantine verdict | The engine receives valid snapshots plus, for each rejected document, a typed reason and raw payload it can persist. Validation remains where the schema is already decoded, and the engine's transaction only writes rows. | Also changes `§20.7`, and moves `§9.5` classification into the integration: the source now owns the mapping from a malformed document to a `QuarantineReason`, so a contract-level rule lives outside `:core:sync` and every future `RemoteSyncSource` implementation must reproduce it. |
| C. The source skips malformed documents and advances the cursor past them | The smallest change: the page succeeds, the cursor advances and the pull cycle never fails. | Silent data loss with no quarantine row. The skipped document is never re-evaluated and never reported, which directly conflicts with `§9.5` ("MUST be stored verbatim in a `quarantine` table") and with the recovery premise of `docs/SPECIFICATION.md`. Not acceptable as stated. |

## Decision

Not yet taken. The recommendation is **option A**: `pullChanges` returns raw per-document results
and the engine validates and quarantines, so `§9.5` classification is executed in `:core:sync`
beside the local transaction that writes the quarantine row, and the integration does not own a
contract rule. Option A is the only option that restores both halves of line 403 — "MUST NOT fail
because of a malformed payload" and "documents that cannot be applied are quarantined" — without
moving `§9.5` semantics into an integration module. Option B is a viable fallback if the DTO change
is judged too wide; option C is rejected because it drops data silently. The owner chooses; the
decision is `Proposed`, not `Accepted`.

## Consequences

### Positive

- Line 403 and `§9.5` become implementable through the declared interface, so `E3-03` can satisfy
  them without an undeclared side channel.
- Under option A the quarantine decision and its persistent write live in the same module and the
  same transaction, which is what `§9.5` requires for cursor advance.

### Negative

- `RemotePage` / `§20.7` change, which is a representation contract change requiring an owner
  decision. The exact new shape is not designed here.
- Until the owner decides, a single malformed remote document can fail a whole pull page (path 1)
  or escape the closed `Outcome` API (path 2), and `§9.5` `MalformedPayload` quarantine cannot be
  implemented as written.

### Constraints Introduced

- No `§9.5` `MalformedPayload` classification MAY be implemented outside the option the owner
  selects, because every option except C changes the `RemoteSyncSource` output contract.
- Option C MUST NOT be implemented: silently skipping a document without a quarantine row
  contradicts `§9.5` and is presented here only to make its cost explicit.
- `E3-03` MUST NOT assume it can quarantine a `MalformedPayload` document until this decision is
  resolved, because the current output type cannot deliver one.

## Verification

- Option A: a `:core:sync` test constructs a page that contains one valid document and one document
  whose payload ID disagrees with its document ID and asserts a committed quarantine row with
  reason `MalformedPayload`, the valid document applied, and the cursor advanced. It also asserts a
  second malformed document that throws during decoding produces the same quarantine outcome rather
  than a failed `Outcome`.
- Option B: the same test at the source boundary asserts the source returns one valid snapshot plus
  one typed quarantine verdict, and a `:core:sync` test asserts the verdict is persisted before the
  cursor advances.
- Option C: a test asserting the cursor advances past the malformed document would be required, but
  it would encode the data loss and is not recommended.
- Under every option, `E3-03` MUST prove `RemoteCursor.INITIAL` never reaches `pullChanges`, per
  `§20.7`.

## References

- `docs/CONTRACTS.md` line 403, `§6`, `§9.4`, `§9.5`, `§20.7`
- `docs/SPECIFICATION.md §8.3`, `§9`, `§11`
- `docs/TECHNICAL_PLAN.md §7`, `§8`
- `docs/BACKLOG.md` (`E3-03`)
- `docs/handoff-E3-02.md`
- `docs/adr/0170-bound-the-pull-cursor-guarantee-to-millisecond-distinguishable-clusters.md`
