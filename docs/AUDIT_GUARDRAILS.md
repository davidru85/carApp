# Audit Resolution Log — TEMPORARY

> **This document is temporary and NOT normative.** It records how the specification audit of 2026-08-17 was resolved. It creates no rules; every rule it refers to now lives in a normative document. Delete this file once the owner has reviewed the remediation.
>
> The full original audit — 99 findings with the reasoning behind each one — is preserved in git history at commit `f8b70cb`. Recover it with `git show f8b70cb:AUDIT_GUARDRAILS.md`.

## What the audit found

The definition package was complete in intent but not machine-decidable. The failure mode was not missing content: it was prose that reads as precise but leaves an implementer with a choice.

99 findings across governance, KMP architecture, data modeling, workflows and state, error handling, API contracts, security and verifiability. 16 were blocking, meaning they would have produced divergent or incorrect implementations, or committed an irreversible decision by accident.

## What changed, by theme

| Theme | Problem | Resolution | Now lives in |
|-------|---------|------------|--------------|
| Document authority | A single linear ranking put the vague `docs/SPECIFICATION.md` above the precise `docs/CONTRACTS.md`, so imprecision legally won. | Authority split into a behaviour axis and a representation axis. | `AGENTS.md` |
| Normative language | `must`, `should` and bare present tense were used interchangeably, including on critical rules. | RFC 2119 block, plus the rule that bare present tense means MUST. | `AGENTS.md` |
| Duplicated rules | Reading order, gates, settings lists and scope appeared in four to six documents, each slightly different. | One canonical location per rule; every other document links to it. | `AGENTS.md`, `docs/SPECIFICATION.md §3.1` |
| Undefined types | About twenty types appeared in normative signatures and were declared nowhere, including the result type itself. | Complete canonical type declarations. | `docs/CONTRACTS.md §20` |
| Field vocabulary | Two incompatible naming schemes for the same fields, with no declared mapping. | One vocabulary with mandatory unit and scale suffixes, canonical at every layer. | `docs/CONTRACTS.md §3` |
| Money arithmetic | The formula hard-coded a EUR factor, and the scaled version truncated before rounding. | Exact integer arithmetic with golden test values and a constrained currency set. | `docs/CONTRACTS.md §2` |
| Ownership of `ownerId` | Dependency rules made it impossible for a repository to learn the current UID. | `OwnerContext` in `:core:common`, stamped by repositories, never by commands. | `docs/CONTRACTS.md §12`, `docs/TECHNICAL_PLAN.md §4` |
| Read-model invariants | Two writers, one invariant, no owner. | `currentOdometerKm` and `odometerInconsistent` are owned exclusively by `:core:database`. | `docs/CONTRACTS.md §3.1` |
| Pull pagination | The query had no `startAfter` anchor, so a timestamp cluster larger than a page looped forever. | Compound cursor in the query, overlap applied once per cycle, strict-progress invariant. | `docs/CONTRACTS.md §9.4` |
| Conflict arbitration | Compared against the local clock and used a meaningless id tie-breaker. | Arbitration on `serverUpdatedAt`; `(updatedAt, documentId)` reclassified as stream ordering. | `docs/CONTRACTS.md §9.6` |
| Offline first launch | "100% usable offline" contradicted "first launch requires network". | `LOCAL_OWNER` local session, outbox suppressed until a real UID exists, plus an adoption story. | `docs/SPECIFICATION.md §7`, `docs/CONTRACTS.md §11.2`–`§11.4`, story `E2-06` |
| Error handling | A name list, not a type; no codes, no exception policy, no `RemoteError` mapping. | Sealed hierarchy with stable codes, cancellation policy, and a normative retry-versus-poison table. | `docs/CONTRACTS.md §6`, `§20.2` |
| Swift interop | Value classes, generics and default arguments on the exported surface. | Explicit surface constraints plus a committed Objective-C header golden file. | `docs/CONTRACTS.md §15.3` |
| Firestore rules | The sample rule enforced three of the six stated requirements and let hard deletes through. | Rules split by operation, `allow delete: if false`, per-field range validation. | `docs/CONTRACTS.md §16` |
| One-way doors | Firestore region, project topology and application identifiers were undecided. | Recorded as decisions with ADRs and a dedicated identifiers document, gated on owner confirmation. | `docs/DECISION_BOARD.md`, `docs/identifiers.md`, ADR-0014, ADR-0015, ADR-0021 |
| Unresolved decisions | Five `Pending` items blocked Phase 0 stories that were nominally Ready. | Recommendations recorded as `Proposed`, with an explicit blocking rule and a closure story. | `docs/DECISION_BOARD.md`, story `E0-00` |
| Verifiability | Unmeasurable acceptance criteria, an undefined `contract-check`, and contracts with no implementing story. | Measurement baselines, a defined contract check, and five new stories. | `docs/versions-matrix.md`, `docs/CONTRACTS.md §18`, `docs/BACKLOG.md` |

## New stories created

`E0-00` owner decision closure, `E1-10` settings persistence, `E2-06` local owner adoption, `E3-07` tombstone purge, `E3-08` app graph and Firebase wiring.

## New documents created

`docs/PROJECT_LOG.md`, `docs/identifiers.md`, `docs/versions-matrix.md`, and ADR-0014 through ADR-0021.

## Still open

Ten decisions are `Proposed` or `Pending` and need owner confirmation before Phase 0 starts. They are listed in the "Decisions Awaiting Owner Confirmation" table of `docs/DECISION_BOARD.md` and closed by story `E0-00`.

Until that table is empty of Phase 0 rows, no implementation story is Ready.

## Deleting this file

Delete it once the owner has reviewed the remediation. Nothing depends on it: it is excluded from the `README.md` documentation table on purpose, and `AGENTS.md` explicitly marks it as non-normative. The audit reasoning stays available in git history.
