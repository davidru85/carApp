# Audit Guardrails Register - TEMPORARY

> **This document is temporary and NOT normative.** It is a management register for audit follow-up only. It creates no rules; any accepted guardrail MUST be moved into a normative document: `AGENTS.md`, `docs/SPECIFICATION.md`, `docs/CONTRACTS.md` or `docs/DECISION_BOARD.md`.
>
> The original full audit - 99 findings with the reasoning behind each one - is preserved in git history at commit `f8b70cb`. Recover it with:
>
> ```text
> git show f8b70cb:AUDIT_GUARDRAILS.md
> ```

## Purpose

This file answers three operational questions:

1. Which findings from the original audit are already absorbed?
2. Which residual ambiguities still need owner or documentation action?
3. When can this temporary file be deleted?

Use this file as a checklist while tightening the definition package. Do not cite it as implementation authority in a story handoff; cite the normative document that absorbed the accepted guardrail.

## Current Status

| Area | Status | Notes |
| :--- | :--- | :--- |
| Original audit from `f8b70cb` | Closed as an active issue list | The 99 original rows have either been absorbed into current normative docs or narrowed into follow-up findings below. |
| Owner decision closure | Closed | `E0-00` is complete. `D-13` through `D-22` are accepted or deferred as appropriate, mirrored in the decision tables, and backed by ADRs. |
| Implementation readiness | Partially ready | The definition package is much more decidable, but the active findings below should be resolved before implementation stories rely on the affected contracts. |
| Temporary file deletion | Not ready | Delete this file only after every `F-xx` row is either absorbed into normative docs or explicitly rejected by the owner. |

## Original Audit Disposition

The original audit contained 99 issue rows across governance, KMP architecture, data modeling, workflows and state, error handling, API contracts, security and verifiability. Sixteen were blocking.

This disposition is a documentation-status check. It means the definition package now contains the intended guardrails, backlog stories and verification hooks. It does **not** mean Phase 0 implementation work, CI checks or product code already exist.

| Disposition | Original IDs | Management result |
| :--- | :--- | :--- |
| Fully absorbed into current normative docs | `G-01`..`G-11`; `K-01`..`K-07`; `K-10`..`K-14`; `D-01`..`D-19`; `W-01`..`W-11`; `W-13`..`W-16`; `W-18`..`W-20`; `E-01`..`E-10`; `C-03`..`C-08`; `C-10`, `C-11`; `S-01`, `S-02`, `S-04`..`S-07`; `V-01`, `V-03`..`V-07` | Do not keep these original rows active. Their accepted fixes now live in the normative docs, derived docs, ADRs, templates and project log. |
| Original problem fixed, narrower residual remains | `C-01`, `C-02`, `C-09`, `K-08`, `K-09`, `S-03`, `V-02`, `W-12`, `W-17` | Track the remaining ambiguity through the `F-xx` findings below, not through the broader original rows. |
| New follow-up findings | `F-01`..`F-19` below | Keep active until each one is resolved into the normative corpus or explicitly rejected. |

## Resolved Themes

| Theme | Original problem | Resolution now in force | Normative home |
| :--- | :--- | :--- | :--- |
| Authority | A vague document could outrank a precise one. | Authority is split by axis: behaviour versus representation. | `AGENTS.md` |
| Normative language | `must`, `should`, `may` and bare present tense had unclear force. | RFC 2119 meanings and bare-present-tense rule are centralised. | `AGENTS.md` |
| Scope and settings | Multiple documents restated slightly different MVP surfaces. | Scope lives in `docs/SPECIFICATION.md`; other docs point to it. | `docs/SPECIFICATION.md §3` |
| Canonical types | Public signatures named project types that were not declared. | `docs/CONTRACTS.md §20` declares canonical shapes. | `docs/CONTRACTS.md §20` |
| Field vocabulary | Domain, Room, Firestore and JSON names could diverge. | Unit and scale suffixes are canonical at every layer. | `docs/CONTRACTS.md §3` |
| Money and consumption arithmetic | Rounding, scale and currency factors were ambiguous. | Exact integer formulas and golden tests are specified. | `docs/CONTRACTS.md §2` |
| Ownership and local identity | Repositories had no legal owner source, and `LOCAL_OWNER` sync was unsafe. | `OwnerContext`, outbox suppression and adoption story are defined. | `docs/CONTRACTS.md §11`, `§12` |
| Sync correctness | Pull pagination, conflict arbitration, backoff and triggers were underdefined. | Cursor, LWW, state machine, retry and trigger contracts are specified. | `docs/CONTRACTS.md §7`..`§9` |
| Firestore safety | Rules were too weak and hard-delete behaviour was accidental. | Operation-split rules, tombstones and field ranges are specified. | `docs/CONTRACTS.md §16` |
| Verifiability | Guardrails had no checks, stories or measurement baselines. | `contract-check`, architecture checks, story intake, baselines and sync tests are defined. | `AGENTS.md`, `docs/CONTRACTS.md §18`, `docs/BACKLOG.md`, `docs/versions-matrix.md` |

## Active Follow-Up Findings

Status values:

- `Open`: not yet resolved in normative docs.
- `Owner decision`: the proposed fix changes scope, stack, release compliance or another owner-owned choice.
- `Ready to fold`: the proposed fix is straightforward documentation work.
- `Rejected`: explicitly rejected by the owner, with the reason recorded here.
- `Closed`: absorbed into normative docs and project log.

| ID | Severity | Domain | Status | Reference | Detected ambiguity / inconsistency | Proposed guardrail definition | Closure evidence required |
| :--- | :--- | :--- | :--- | :--- | :--- | :--- | :--- |
| `F-01` | High | KMP architecture | Closed | `docs/CONTRACTS.md §11.6`, `§20.3`; `docs/TECHNICAL_PLAN.md §3` | `CrashReporter` was declared in `:core:common`, planned in `:core:crash`, omitted from `AppGraphDependencies`, and missing from some module lists. | `CrashReporter` lives in `:core:crash`; `AppGraphDependencies` includes `val crashReporter: CrashReporter` from Phase 0; tests and local builds use the no-op implementation; Firebase Crashlytics remains Phase 4. | Closed on 2026-08-17 by aligning `docs/CONTRACTS.md`, `docs/SPECIFICATION.md`, `docs/TECHNICAL_PLAN.md`, `docs/BACKLOG.md`, README and this register. Project log entry added. |
| `F-02` | High | Swift surface | Closed | `docs/CONTRACTS.md §15.3`, `§20.10` | The Swift-facing API allowed only restricted shapes, but `AppGraph` returned interfaces such as `SyncController` and undefined state-holder types. | The Swift-facing ABI is an explicit allowlist. `createSwiftAppGraph(isDebugBuild)`, `SwiftAppGraph`, concrete state holders, `UiState` classes, `UiMessage`, `SyncStatus` and referenced enums are exported. `AppGraph`, `createAppGraph(AppGraphDependencies)`, `AppGraphDependencies` and `SyncController` are Kotlin-facing only and absent from the Objective-C header. | Closed on 2026-08-17 by updating `docs/CONTRACTS.md §11.6`, `§15.3`, `§20.7`, `§20.10`; `docs/SPECIFICATION.md §8.4`, `§8.5`; `docs/TECHNICAL_PLAN.md §5`; and `E0-07` / `E3-08` backlog criteria. |
| `F-03` | High | API contract | Closed | `docs/CONTRACTS.md §20.10`, `§14` | `VehicleListStateHolder`, `VehicleFormStateHolder`, `FuelEntryListStateHolder`, `FuelEntryFormStateHolder`, `SessionStateHolder` and concrete `UiState` types were referenced but not declared. | `docs/CONTRACTS.md §20.10` now declares every exported state holder, `SyncStateHolder`, all `UiState` data classes, UI row DTOs, `MoneyInputMode`, `SessionPhase`, `UiMessage` and `UiMessageKind`, including the required `state` property, intent functions, close semantics and message-code rules. | Closed on 2026-08-17 by adding canonical shared-surface declarations and lifecycle semantics in `docs/CONTRACTS.md §14` and `§20.10`. |
| `F-04` | Medium | CI contract | Closed | `docs/CONTRACTS.md §18` | `contract-check` said every type in code blocks must be declared in §20, which would also catch standard or external types such as `String`, `Flow`, `CoroutineScope`, `Instant`, `Map` and `Throwable`. | `contract-check` now distinguishes project-owned identifiers from an explicit external allowlist: Kotlin primitives, standard collections, nullable markers, `Throwable`, selected `kotlinx.coroutines` types, the pinned `kotlinx.datetime.Instant` and platform export-hiding annotations. Objective-C header validation also enforces the Swift allowlist. | Closed on 2026-08-17 by updating `docs/CONTRACTS.md §18` and `E0-05` / `E0-07` backlog criteria. |
| `F-05` | High | Data modeling | Closed | `docs/CONTRACTS.md §2`, `§3 FuelEntry`, `§20.5` | The contract said `MoneyInput` recorded the authoritative supplied pair, but the persisted schema had no field recording which pair was supplied. | `MoneyInput` now selects the derived monetary field only during validation. Persistence stores the canonical triple only: `litersScaled`, `pricePerLiterScaled` and `totalCostMinor`. No local, remote or outbox schema contains `moneyInputKind` or any supplied-pair marker. | Closed on 2026-08-17 by updating `docs/CONTRACTS.md §2` and `E1-04` backlog criteria. |
| `F-06` | Medium | Data modeling | Closed | `docs/CONTRACTS.md §2`, `§20.3` | `MinorUnits.factorFor` supported 2-decimal ISO-4217 codes, but no exact KMP-compatible allowlist was defined. | `SUPPORTED_CURRENCY_CODES` is the canonical MVP allowlist in `docs/CONTRACTS.md §20.0.1`; every supported code returns factor `100`, unsupported locale suggestions fall back to `EUR`, and explicit unsupported selections return `ValidationError.InvalidUnit`. | Closed on 2026-08-17 by updating `docs/CONTRACTS.md §2`, `§20.0.1`, `§20.3`, `docs/SPECIFICATION.md §5.3` and `E0-03` / `E1-04` backlog criteria. |
| `F-07` | Medium | Data modeling | Closed | `docs/CONTRACTS.md §5`, `§3 Vehicle` | Name normalization collapsed whitespace, while `nameFold` was described as `name.trim().lowercase()`. Unicode normalization and locale-independent folding were not explicit. | `canonicalVehicleName(input)` is now trim plus collapse of non-empty Unicode whitespace runs to U+0020. `nameFold` is `canonicalVehicleName(name).lowercase()` using Kotlin locale-invariant lowercase. No NFC/NFD is applied in MVP without an approved KMP normalization dependency. | Closed on 2026-08-17 by updating `docs/CONTRACTS.md §3`, `§5` and `E1-02` backlog criteria. |
| `F-08` | Medium | Scope / data modeling | Closed | `docs/CONTRACTS.md §3 Vehicle`, `§20.4`; `docs/adr/0005-vehicle-fuel-type-from-day-one.md` | `FuelType` included `ELECTRIC` and `HYBRID`, while electric energy handling is out of MVP scope. Commands could still carry those values. ADR-0005 stored fuel type with default `GASOLINE` and forbade an MVP selector, but did not decide validation of non-combustion enum values. | The owner chose to exclude `ELECTRIC` and `HYBRID` from the MVP. The canonical `FuelType` enum now contains only `GASOLINE`, `DIESEL`, `LPG`, `CNG` and `OTHER`. Electric and hybrid support is documented as future roadmap scope requiring an energy-model story or ADR. | Closed on 2026-08-17 by updating `AGENTS.md`, README, `docs/CONTRACTS.md §3`, `§5`, `§20.4`, `docs/SPECIFICATION.md §3` and `§12`, `docs/DECISION_BOARD.md`, `docs/TECHNICAL_PLAN.md §2`, ADR-0005, ADR index, backlog criteria and this register. |
| `F-09` | High | Workflow / sync state | Open | `docs/CONTRACTS.md §7`, `§8` | `syncState` is said to be derived from outbox existence, but `LOCAL_OWNER` writes set `PENDING` while the outbox must remain empty. | Define `syncState` as a stored local control column. Derivation rules may update it, but `ownerId == LOCAL_OWNER && localRevision > 0` is representable as `PENDING` without an outbox row. | `docs/CONTRACTS.md §7` and `§8` remove the contradiction; `E1-03`, `E1-06` and `E2-06` criteria reflect it. |
| `F-10` | High | Workflow / adoption | Open | `docs/CONTRACTS.md §11.4` | Adoption must enqueue rows preserving `seq` causality, but no `seq` exists for `LOCAL_OWNER` rows because the outbox is suppressed. | Add a local mutation sequence independent of the outbox, or permit a non-pushable staging outbox for `LOCAL_OWNER`. Adoption orders snapshots by that sequence, then by dependency group. | `docs/CONTRACTS.md §8`, `§11.4`, database schema and `E2-06` criteria agree. |
| `F-11` | Blocker | Account deletion / Firestore | Owner decision | `docs/SPECIFICATION.md §7 F-5`, `docs/CONTRACTS.md §11.5`, `§16` | Account deletion requires deleting remote documents, but Firestore rules reject hard deletes and no admin or backend deletion path is defined. | Add an explicit deletion mechanism: a server/Admin operation with its own ADR and contract, a narrowly scoped client hard-delete exception for account deletion, or a tombstone-only compliance decision with remote purge semantics. | Owner-approved decision and ADR; `docs/SPECIFICATION.md`, `docs/CONTRACTS.md`, `docs/DECISION_BOARD.md`, backlog and Firestore tests agree. |
| `F-12` | High | Firestore contract | Open | `docs/CONTRACTS.md §3`, `§16` | Remote payload validation says "every field", but local-only fields are excluded elsewhere. Extra-key policy is not exact. | Add exact allowed-key schemas for remote `Vehicle` and `FuelEntry`, including required keys, forbidden keys, enum values, nullability and `deleted == (deletedAt != null)`. Emulator tests reject local-only or extra keys. | `docs/CONTRACTS.md §16` contains the remote schemas and `E3-01` acceptance criteria include extra-key rejection. |
| `F-13` | High | Sync pagination | Open | `docs/CONTRACTS.md §9.4`, `docs/TECHNICAL_PLAN.md §8` | The overlap cursor uses `startAfter(max(0, lastServerUpdatedAt - 30s), null)`, but a null document ID is not a concrete Firestore cursor anchor. | Define a legal cursor start: for example `startAt(since, "")` for the first page of an overlapped cycle, then `startAfter(lastUpdatedAt, lastDocumentId)` for subsequent pages. | `docs/CONTRACTS.md §9.4`, `RemoteCursor` semantics, `TECHNICAL_PLAN.md §8` and sync tests agree. |
| `F-14` | High | Odometer workflow | Open | `docs/SPECIFICATION.md §6 R-1`, `docs/CONTRACTS.md §3.1` | Recomputing only the affected entry and immediate successor is underspecified for edits that move an entry in chronological order and for deletes. | Define the recompute set as: inserted or updated row in its new position, successor in old position, successor in new position, and successor after a deleted row; all in one transaction. | `docs/CONTRACTS.md §3.1` and fuel-entry data tests define and verify the recompute set. |
| `F-15` | Medium | Consumption contract | Open | `docs/CONTRACTS.md §20.6`, `§20.4` | `ConsumptionInvalidReason.EndEntryNotFullTank` exists, but `ConsumptionReport.segments` is one per full-tank entry, so the reason has no clear producer. `FuelEntryListItem.invalidReason` for partial entries is undefined. | Either remove `EndEntryNotFullTank`, or define that list projections may report it for non-full entries while `ConsumptionReport.segments` omits them. Specify exact mapping from entry to `FuelEntryListItem.consumption` and `invalidReason`. | `docs/CONTRACTS.md §20.4`, `§20.6` and `E1-05`/`E1-06` acceptance criteria agree. |
| `F-16` | High | Error handling / pull robustness | Open | `docs/CONTRACTS.md §5`, `§9.5` | Pull may not fail on domain constraints, but quarantine only covers future `schemaVersion`. Malformed supported-version documents are not assigned a clear fate. | Define malformed supported-version remote documents as quarantined with reason `MalformedPayload`, skipped from entity tables, logged once with redacted fields, and not applied to product state. Cursor advance remains allowed after quarantine succeeds. | `docs/CONTRACTS.md §9.5`, quarantine schema and sync tests cover malformed supported-version payloads. |
| `F-17` | Medium | Settings workflow | Open | `docs/SPECIFICATION.md §5.3`, `§7 F-5`; `docs/CONTRACTS.md §12` | Settings are device-local and not owner-scoped, but sign-out or local-data deletion clears local data for an owner. It is unclear whether settings survive destructive flows. | Define settings deletion semantics explicitly. Preferred: sign-out and account deletion preserve device settings; anonymous "delete local data" either preserves settings or resets them, but the choice must be stated. | `docs/SPECIFICATION.md F-5`, `docs/CONTRACTS.md §12` and `E2-05`/`E4-01` criteria agree. |
| `F-18` | High | KMP provider wiring | Closed | `docs/SPECIFICATION.md §8.5`, `docs/CONTRACTS.md §11.6`, `§15.3` | `:shared` exposed `createAppGraph(dependencies)`, but `AppGraphDependencies` contained implementation-facing abstractions that are not compatible with the Swift-facing surface. | Platform graph construction is split: Kotlin callers use `createAppGraph(AppGraphDependencies)` and the Kotlin-facing `AppGraph`; Swift callers use `createSwiftAppGraph(isDebugBuild)` and `SwiftAppGraph`, which expose concrete state-holder factories and no provider dependency container. Header validation proves the split. | Closed on 2026-08-17 by aligning `docs/SPECIFICATION.md §8.5`, `docs/CONTRACTS.md §11.6`, `§15.3`, `§20.10`, `docs/TECHNICAL_PLAN.md §5`, and `E0-07` / `E3-08` backlog criteria. |
| `F-19` | Medium | Derived docs alignment | Closed | README architecture summary, `docs/SPECIFICATION.md §8.2`, `docs/TECHNICAL_PLAN.md §3` | README and Specification module lists omitted crash-reporting modules that Technical Plan and Backlog included. | The module lists now include `:core:crash` and `:integration:firebase-crashlytics` consistently where they are restated. | Closed on 2026-08-17 by aligning `docs/SPECIFICATION.md`, `docs/TECHNICAL_PLAN.md`, README and backlog wording. Project log entry added. |

## Recommended Closure Order

| Order | Findings | Why first |
| :--- | :--- | :--- |
| Closed | `F-01`, `F-02`, `F-03`, `F-04`, `F-05`, `F-06`, `F-07`, `F-08`, `F-18`, `F-19` | Module ownership, Swift-facing ABI, shared state-holder declarations, contract-check allowlist, monetary persistence, supported currencies, name normalization, MVP fuel-type scope and crash-reporting module lists have been aligned. |
| 1 | `F-09`, `F-10`, `F-13`, `F-14`, `F-16` | Sync and database invariants need exact contracts before `:core:database`, adoption and sync implementation. |
| 2 | `F-11`, `F-17` | Account deletion and settings semantics affect compliance and destructive user flows. |
| 3 | `F-12`, `F-15` | Firestore schema strictness and list/consumption projection semantics can close with the related implementation stories, but earlier is better. |

## How To Close A Finding

For each `F-xx` row:

1. Decide whether the proposed guardrail is accepted, modified or rejected.
2. Move the accepted rule into the proper normative document.
3. Update every derived document that repeats the same rule.
4. Add or update backlog acceptance criteria and verification where needed.
5. Append an entry to `docs/PROJECT_LOG.md`.
6. Change the row status here to `Closed` or `Rejected`, with the closing evidence.

Once all findings are closed or rejected, delete this file in a documentation-only cleanup and append a final project-log entry. The audit reasoning remains available in git history.
