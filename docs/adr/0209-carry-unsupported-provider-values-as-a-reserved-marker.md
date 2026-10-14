# ADR-0209 / D-208 - Carry Unsupported Provider Values as a Reserved Marker

## Status

Accepted

The owner selected option A on 2026-10-14.

## Context

`E3-20` closes the last totality gap of the pull boundary. The Firestore integration converts every
provider field to a provider-free value before serializing the document into `rawJson`. That
conversion throws `IllegalArgumentException` for a provider value type it does not support — a
`GeoPoint`, a `Blob` / `NSData`, a `DocumentReference`, a vector, or a map with a non-string key — in
the Android and iOS `untypedFields()` actuals and again in the common `toJsonElement`.
`runRemoteOperation` turns that into `RemoteError.InvalidArgument` for the whole page, `failPullCycle`
leaves the stored cursor where it was, and the next cycle requests the same page: one such document
stalls the pull permanently instead of being quarantined, which `docs/CONTRACTS.md §9.5` forbids
("The classification MUST be total").

The closed Firestore schema of `§16` and the `validPayload()` rules make the case near-unreachable,
so this is a closure gap, not a live defect. Two `§9.5` rules bound the fix: the integration MUST NOT
classify a raw document as malformed or discard it, and it transports product fields verbatim
(`D-170`); `:core:sync` classifies.

## Options Considered

| Option | Benefits | Costs / Risks |
|--------|----------|---------------|
| A. The integration replaces an unsupported value by a reserved JSON marker object naming only its type; `:core:sync` quarantines every document carrying the marker as `MalformedPayload` | The page survives, the document reaches the engine, the cursor advances. The integration still classifies nothing. Mirrors the `E3-19` push-side totality. No public contract type changes. | `rawJson` is no longer byte-for-byte the provider document where the marker stands; `§9.5` must define the marker. |
| B. Omit the unsupported field | Quarantined through the exact-key check. | Silently alters the document; the quarantined `rawJson` loses the field; a future optional field would be applied without its value. |
| C. Add a per-document "unconvertible" list to `RemotePage` | Semantically explicit. | Changes the public `RemoteSyncSource` contract (`§10`, `§20.7`), every fake and the quarantine path, for a near-unreachable case. |

## Decision

The selected option is **A**.

- `:core:sync` declares the reserved key once: `UNSUPPORTED_PROVIDER_VALUE_KEY`. A marker is the JSON
  object `{ "<key>": "<provider value type name>" }`; the type name is a class name, never a field
  value, so it carries no user data.
- The integration's conversion is total. Wherever a provider value cannot be represented — at a top
  level field, inside a map or inside a list — it emits the marker in that position and keeps every
  other field verbatim. A map with a non-string key becomes one marker. The only page-level failure
  left is the missing or unusable ordering timestamp of `§9.5`.
- `:core:sync` quarantines every document whose raw JSON contains the marker anywhere as
  `MalformedPayload`, explicitly, so the guarantee does not depend on the current schema being made
  of primitives. A supported-version check still comes first: a document of an unsupported
  `schemaVersion` stays `UnsupportedSchemaVersion`.
- The quarantined `rawJson` stores the marker in place of the unrepresentable value.

## Consequences

### Positive

- One unsupported value can no longer stall the pull; the cursor advances past its document.
- Classification stays in `:core:sync`; the integration only transports.

### Negative

- The stored quarantine payload does not contain the original provider value, which has no JSON form.
- A product field named like the reserved key would be misread as a marker; the key is chosen outside
  the `§16` field vocabulary, and the closed schema rejects unknown keys on write.

### Constraints Introduced

- `docs/CONTRACTS.md §9.5` defines the marker and the engine rule; `§20.7` declares the key.
- The Android and iOS actuals cannot be exercised without a live provider, so they follow the
  provider-integration TDD exemption of `docs/SPECIFICATION.md §11`; the common conversion and the
  engine rule are test-first.

## Verification

- `FirebaseRemoteSyncSourceTest`: a document carrying an unsupported value at the top level, inside a
  map, inside a list, or as a map with a non-string key reaches the engine as a raw document with the
  marker in place, and the page succeeds.
- An end-to-end test through the real engine and database: such a document becomes a
  `MalformedPayload` quarantine record and the pull cursor advances past it.
- A `:core:sync` test of the marker detection, and a controller test that a marked document is
  quarantined while an unsupported `schemaVersion` keeps precedence.

## References

- `docs/DECISION_BOARD.md` (decision ID `D-208`)
- `docs/CONTRACTS.md §9.5`, `§10`, `§16`, `§20.7`
- `D-170` (ADR-0171), the raw pull boundary
- `docs/BACKLOG.md` (`E3-20`); `docs/handoffs/E3-19.md` (the push-side counterpart)
