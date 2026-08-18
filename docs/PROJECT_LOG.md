# Project Log - carApp

> Append-only record of what actually happened in this project: decisions taken, stories completed, problems found, direction changes. It is the fastest way for a new agent or a returning human to learn the current state without reading every normative document.
>
> This log is **history**, not rules. It never overrides `docs/SPECIFICATION.md`, `docs/CONTRACTS.md` or `docs/DECISION_BOARD.md`. If the log and a normative document disagree, the normative document wins and the discrepancy is escalated.

## How to use this log

**Read:** before starting any work, read the three most recent entries. They tell you what was just done, what is in flight and what is blocked.

**Write:** appending an entry is part of the Definition of Done (`AGENTS.md`). Add exactly one entry per completed story, per accepted or changed decision, and per significant event such as a scope change, an incident, a phase gate or a handoff to a different agent.

**Rules:**

- Newest entries at the top, immediately under this section.
- Never edit or delete a past entry. If something was wrong, append a new entry that corrects it and say which entry it corrects.
- Dates are absolute and ISO-8601 (`2026-08-17`), never relative.
- Written in technical English, like every other repository artifact.
- Keep entries short. Link to the PR, the story and the documents rather than re-explaining them.
- Do not put secrets, tokens, personal data or user data in this log.

## Entry template

```markdown
### YYYY-MM-DD — <Short title>

- **Type:** story | decision | milestone | incident | handoff | correction
- **Story / Decision:** `E0-00` / `D-13` / —
- **Author:** human name or agent identifier
- **What changed:** one or two sentences.
- **Why:** the reason, especially if a non-obvious option was rejected.
- **Documents touched:** `docs/CONTRACTS.md §9`, `docs/BACKLOG.md`, …
- **Verification:** commands run, tests added, gates passed.
- **Follow-ups / risks:** anything left open, with an owner if known.
```

---

## Entries

### 2026-08-18 — Documentation audit AUDIT-01 applied: dependency-rule rows for `:core:auth`, `:core:analytics`, `:core:testing`

- **Type:** correction
- **Story / Decision:** `AUDIT-01` (`docs/DOCUMENTATION_AUDIT.md` §1.1, blocking)
- **Author:** opencode agent (glm-5.2:cloud), on behalf of David Ruiz
- **What changed:** added three explicit rows to the `docs/TECHNICAL_PLAN.md §4` dependency-rule table for `:core:auth`, `:core:analytics` and `:core:testing`, which previously had no enforceable rule even though they appear in the canonical module inventory (`docs/CONTRACTS.md §1.1`). Added an explanatory paragraph pinning the `:core:testing` platform-API permission to `expect`/`actual` test doubles only (`docs/CONTRACTS.md §15.1`), keeping its `commonMain` public surface Kotlin-pure. Added matching failing fixtures to the `E0-04` acceptance criteria (one per new row). The architecture check generated from that table can now enforce the boundaries of all three modules.
- **Why:** the architecture check is generated from the `§4` table, so the three modules were previously unenforceable; an agent could make `:core:auth` depend on `:feature:*` or `:integration:*` and the check would not fire. The owner accepted the audit's single proposed solution verbatim and the agent's interpretation that `:core:testing` forbids platform APIs only in its `commonMain` public surface (permitted in `expect`/`actual` test doubles).
- **Documents touched:** `docs/TECHNICAL_PLAN.md §4`, `docs/BACKLOG.md` (E0-04), and this log.
- **Verification:** documentation-only change. The new architecture rules and fixtures will be exercised by `E0-04`; no product code exists yet. Requires human review before merge (gated path `docs/TECHNICAL_PLAN.md` and gated topic "module boundaries and dependency rules").
- **Follow-ups / risks:** the `:core:testing` platform-API carve-out relies on `§15.1` boundaries; if a future change loosens `§15.1`, this row's fixture wording MUST be re-checked. The remaining 19 findings of `docs/DOCUMENTATION_AUDIT.md` are still open and will be processed one by one with their own project-log entries.

### 2026-08-18 — Second documentation audit performed

- **Type:** milestone
- **Story / Decision:** `docs/DOCUMENTATION_AUDIT.md`
- **Author:** opencode agent (glm-5.2:cloud), on behalf of David Ruiz
- **What changed:** a fresh Senior KMP Architecture audit of the current documentation state (after the prior 56-finding audit was folded into the normative docs). The audit document was rewritten with 20 new findings: 5 blocking, 13 guardrails, 1 drift, 1 cosmetic. Top blockers: missing dependency-rule rows for `:core:auth`/`:core:analytics`/`:core:testing`; `:core:analytics` (Phase 0) referencing `AuthProvider` from `:core:auth` (Phase 2); `DatabaseFactory` in `:core:common` returning a `:core:database` type; `SessionStateHolder` missing an F-4 conversion intent; `Confirmation` enum missing deletion leaves for `confirmDelete`.
- **Why:** the prior audit closure left residual gaps that would still let two competent agents produce incompatible implementations; this pass targets those.
- **Documents touched:** `docs/DOCUMENTATION_AUDIT.md`, and this log.
- **Verification:** documentation-only change; cross-referenced findings against the current `AGENTS.md`, `SPECIFICATION.md`, `CONTRACTS.md`, `TECHNICAL_PLAN.md`, `DECISION_BOARD.md`, `BACKLOG.md`, `SECURITY.md`, `identifiers.md` and `versions-matrix.md`.
- **Follow-ups / risks:** the audit is non-normative; each finding must be accepted by the owner and folded into the normative docs in a separate change with a project-log entry. Blocking findings should be resolved before the dependent backlog stories start.

### 2026-08-18 — Documentation audit verification hooks tightened

- **Type:** decision
- **Story / Decision:** `docs/DOCUMENTATION_AUDIT.md`
- **Author:** Codex, on behalf of David Ruiz
- **What changed:** remaining recommended audit guardrails were folded into executable story criteria: `Instant` stays an `E0-06` blocker until the version matrix pins its package, `outbox.lastError` is debug/UI-only, Swift scale suffixes require `shared/README.md`, and supported currencies require platform minor-unit verification with `EUR` fallback.
- **Why:** the owner authorised using the recommended option while away, and these entries make already-accepted prose rules testable by the stories that will implement them.
- **Documents touched:** `docs/CONTRACTS.md`, `docs/BACKLOG.md`, and this log.
- **Verification:** documentation-only change; `git diff --check`.
- **Follow-ups / risks:** changes touch gated documentation and still require normal human review before merge.

### 2026-08-18 — Documentation audit review decisions recorded

- **Type:** decision
- **Story / Decision:** `docs/DOCUMENTATION_AUDIT.md`
- **Author:** Codex, on behalf of David Ruiz
- **What changed:** the owner reviewed the first documentation-audit corrections and accepted the recommended choices for `:core:database` dependency rules, dependency injection over duplicate `expect`/`actual` paths, consumption type ownership in `:core:model`, closed platform API vocabulary, `:core:crash` ownership, and a non-duplicated `SPECIFICATION.md §8.2` module inventory reference. The owner also instructed Codex to use the recommended option for remaining documentation-audit decisions while they are away.
- **Why:** the review keeps decision ownership explicit while allowing the audit cleanup to continue without blocking on every low-risk guardrail choice.
- **Documents touched:** `docs/SPECIFICATION.md`, `docs/CONTRACTS.md`, `docs/TECHNICAL_PLAN.md`, `docs/BACKLOG.md`, `docs/identifiers.md`, and this log.
- **Verification:** manual review in conversation, followed by `git diff --check`.
- **Follow-ups / risks:** every subsequent assumed recommendation in this review should be summarised for owner review after the remaining audit items are processed.

### 2026-08-17 — Documentation audit guardrails folded into project docs

- **Type:** milestone
- **Story / Decision:** `docs/DOCUMENTATION_AUDIT.md`
- **Author:** Codex, on behalf of David Ruiz
- **What changed:** processed the documentation audit in order across architecture, data modelling, sync, auth, error handling, Swift-facing ABI and cross-document consistency. The docs now include a canonical module inventory, explicit `:core:database` dependency rules, tighter `expect`/`actual` boundaries, exact local DDL constraints, outbox coalescing SQL, Firestore `validPayload()` shape, local-owner adoption and account-deletion ordering details, logging redaction rules, `CalculateConsumption` signature cleanup, Swift ABI lifecycle rules, Phase 0 module-set constraints and expanded backlog fixtures.
- **Why:** the audit identified places where two agents could implement different behaviours while still claiming to follow the same docs. The corrections turn those areas into precise contract language and story acceptance criteria.
- **Documents touched:** `docs/SPECIFICATION.md`, `docs/CONTRACTS.md`, `docs/TECHNICAL_PLAN.md`, `docs/BACKLOG.md`, `docs/identifiers.md`, and this log.
- **Verification:** manual documentation review with targeted searches, diff review and code-fence counts for the edited Markdown files. No product code or CI exists yet.
- **Follow-ups / risks:** changes touch gated documentation paths and require human review before merge. `contract-check`, `architecture-check`, Firestore emulator tests and generated Objective-C header checks are specified but not yet implemented.

### 2026-08-17 — MVP single-device constraint recorded

- **Type:** decision
- **Story / Decision:** `D-0`
- **Author:** Codex, on behalf of David Ruiz
- **What changed:** the MVP now explicitly supports one active device per account. Simultaneous multi-device use, active synchronization and moving the source of truth from Room to the remote database are recorded as future scope.
- **Why:** the owner clarified that the remote database is backup-only for the MVP, while future multi-device use will require real synchronization and a remote source of truth.
- **Documents touched:** `AGENTS.md`, `docs/SPECIFICATION.md`, `docs/CONTRACTS.md §10`, `docs/TECHNICAL_PLAN.md`, `docs/BACKLOG.md`, `docs/DEFINITION.md`, `README.md`, `docs/adr/0001-backend-cloud-firestore.md`, `docs/adr/0014-firestore-location-europe-west1.md`, and this log.
- **Verification:** manual documentation update only; no product code exists for this behaviour.
- **Follow-ups / risks:** remaining internal names such as `SyncController`, `RemoteSyncSource` and `syncState` are implementation terms unless a future contract-renaming story changes them. The change touches gated scope, backend and sync topics and requires human review before merge.

### 2026-08-17 — Remote database purpose clarified as backup and recovery

- **Type:** decision
- **Story / Decision:** `D-0`
- **Author:** Codex, on behalf of David Ruiz
- **What changed:** the remote database purpose is now documented as backup and recovery only, so users can retrieve backed-up data on a new device. Active multi-device collaboration is not an MVP goal.
- **Why:** the owner clarified that the remote database exists solely as a backup, not as the product source of truth or a real-time cross-device data layer.
- **Documents touched:** `docs/SPECIFICATION.md`, `docs/CONTRACTS.md §9` and `§10`, `docs/DECISION_BOARD.md`, `docs/SECURITY.md`, `docs/TECHNICAL_PLAN.md`, `docs/BACKLOG.md`, `docs/DEFINITION.md`, `README.md`, `docs/adr/0001-backend-cloud-firestore.md`, `docs/adr/0015-firebase-project-topology.md`, and this log.
- **Verification:** manual documentation update only; no product code exists for this behaviour.
- **Follow-ups / risks:** remaining `sync` type and module names are technical implementation names unless a future contract-renaming story changes them. The change touches gated scope, backend and sync topics and requires human review before merge.

### 2026-08-17 — Post-MVP OCR and Cloud Functions security recorded

- **Type:** milestone
- **Story / Decision:** —
- **Author:** Codex, on behalf of David Ruiz
- **What changed:** post-MVP roadmap notes now explicitly reserve receipt and odometer image capture with local AI text recognition, targeting receipt total amount, receipt price per liter and odometer reading, plus Cloud Functions-mediated remote read/write validation beyond the `D-23` account deletion server operation.
- **Why:** the owner asked to keep these capabilities visible for the future without expanding MVP scope or authorizing implementation dependencies, models, receipt or odometer image storage, server-mediated product reads, App Check enforcement or broader privileged server-side writes.
- **Documents touched:** `docs/SPECIFICATION.md §3`, `docs/SECURITY.md`, `docs/TECHNICAL_PLAN.md §13`, `README.md`, and this log.
- **Verification:** manual documentation update only; no product code exists for these features.
- **Follow-ups / risks:** both future areas require a dedicated story or ADR before implementation. Changes touch gated documentation and security topics and require human review before merge.

### 2026-08-17 — Temporary audit guardrails file deleted

- **Type:** decision
- **Story / Decision:** audit closure
- **Author:** Codex, on behalf of David Ruiz
- **What changed:** deleted `docs/AUDIT_GUARDRAILS.md` after all `F-01` through `F-19` follow-up findings were absorbed into the normative and derived documentation.
- **Why:** the file was explicitly temporary and no longer carried active work after owner review.
- **Documents touched:** `docs/AUDIT_GUARDRAILS.md`, and this log.
- **Verification:** manual search for active audit findings and `git diff --check`.
- **Follow-ups / risks:** none.

### 2026-08-17 — Partial refuel consumption explanation defined

- **Type:** decision
- **Story / Decision:** `F-15`
- **Author:** Codex, on behalf of David Ruiz
- **What changed:** `EndEntryNotFullTank` is kept as the list-projection reason for partial refuels. Partial rows show no own consumption, do not produce `SegmentResult`, and still contribute litres to the next full-to-full segment when they fall inside it.
- **Why:** the owner chose to keep a clear UI explanation for non-full refuels while preserving full-to-full consumption as the only calculation model.
- **Documents touched:** `docs/SPECIFICATION.md §6`, `docs/CONTRACTS.md §4`, `§13`, `§20.4` and `§20.6`, `docs/BACKLOG.md` `E1-05`, `E1-06`, `E1-08` and `E1-09`, `docs/AUDIT_GUARDRAILS.md`, and this log.
- **Verification:** manual cross-document search for `F-15`, `EndEntryNotFullTank`, `FuelEntryListItem`, `SegmentResult`, `ConsumptionReport` and partial refuels; `git diff --check`.
- **Follow-ups / risks:** no active audit findings remain. `docs/AUDIT_GUARDRAILS.md` is ready for owner review and later deletion.

### 2026-08-17 — Firestore remote schemas closed

- **Type:** decision
- **Story / Decision:** `F-12`
- **Author:** Codex, on behalf of David Ruiz
- **What changed:** remote `Vehicle` and `FuelEntry` documents now have exact closed key sets in `docs/CONTRACTS.md §16`. Unknown collections, missing keys, extra keys, local-only metadata and inconsistent `deleted` / `deletedAt` pairs are rejected by the Firestore contract.
- **Why:** the owner chose the strict schema option to make remote payload validation predictable and prevent malformed or locally-owned fields from entering Firestore.
- **Documents touched:** `AGENTS.md`, `docs/SPECIFICATION.md §10`, `docs/CONTRACTS.md §16`, `docs/BACKLOG.md`, `docs/AUDIT_GUARDRAILS.md`, and this log.
- **Verification:** manual cross-document search for `F-12`, `validPayload`, `closed remote schema`, forbidden local-only keys, `schemaVersion` and `deletedAt`; `git diff --check`.
- **Follow-ups / risks:** remaining audit finding is `F-15`.

### 2026-08-17 — Account deletion server operation accepted

- **Type:** decision
- **Story / Decision:** `F-11` / `D-23`
- **Author:** Codex, on behalf of David Ruiz
- **What changed:** account deletion now uses a Firebase Admin server operation. The app re-authenticates if needed, calls the authenticated server operation, the server deletes `fuelEntries`, then `vehicles`, then the Firebase Auth user, and only after success does the app clear local data.
- **Why:** the owner chose server/Admin deletion so store-required account deletion can physically purge remote data while client Firestore rules continue to reject hard deletes.
- **Documents touched:** `AGENTS.md`, `docs/SPECIFICATION.md §7` and `§12`, `docs/CONTRACTS.md §6`, `§11.1`, `§11.5` and `§16`, `docs/DECISION_BOARD.md`, `docs/TECHNICAL_PLAN.md`, `docs/adr/README.md`, `docs/adr/0024-account-deletion-server-admin.md`, `docs/BACKLOG.md`, `docs/SECURITY.md`, `docs/AUDIT_GUARDRAILS.md`, and this log.
- **Verification:** manual cross-document search for `D-23`, `F-11`, `E3-10`, `AuthError.AccountDeletionRemoteFailed`, account deletion and `allow delete`; `git diff --check`.
- **Follow-ups / risks:** remaining audit findings are `F-12` and `F-15`. `E3-10` must implement and test the server operation before release.

### 2026-08-17 — Malformed remote payload quarantine decided

- **Type:** decision
- **Story / Decision:** `F-16`
- **Author:** Codex, on behalf of David Ruiz
- **What changed:** quarantine now covers both unsupported future schema versions and malformed supported-version payloads. `QuarantineReason.MalformedPayload` and `QuarantineRecord` are canonical sync types.
- **Why:** the owner chose to keep malformed remote documents out of product tables without blocking cursor progress, provided the quarantine row is committed successfully.
- **Documents touched:** `AGENTS.md`, `docs/CONTRACTS.md §5`, `§9.5` and `§20.7`, `docs/SPECIFICATION.md`, `docs/TECHNICAL_PLAN.md §8` and `§9`, `docs/BACKLOG.md`, `docs/DEFINITION.md`, `docs/SECURITY.md`, `docs/AUDIT_GUARDRAILS.md`, and this log.
- **Verification:** manual cross-document search for `quarantine`, `MalformedPayload`, `QuarantineReason`, `schemaVersion`, `malformed` and `18 sync tests`; `git diff --check`.
- **Follow-ups / risks:** remaining audit findings are `F-11`, `F-12` and `F-15`.

### 2026-08-17 — Odometer recompute set defined

- **Type:** decision
- **Story / Decision:** `F-14`
- **Author:** Codex, on behalf of David Ruiz
- **What changed:** `odometerInconsistent` recomputation now has an exact minimal recompute set for create, update, delete and vehicle cascade delete. `currentOdometerKm` remains recomputed for the whole vehicle in the same transaction.
- **Why:** the owner chose the explicit minimal-set option to handle edits that move an entry in chronological order without recomputing the whole vehicle unnecessarily.
- **Documents touched:** `docs/CONTRACTS.md §3.1` and fuel repository side effects, `docs/SPECIFICATION.md §5` / R-1, `docs/BACKLOG.md`, `docs/AUDIT_GUARDRAILS.md`, and this log.
- **Verification:** manual cross-document search for `odometerInconsistent`, `currentOdometerKm`, `successor`, `recompute` and `F-14`; `git diff --check`.
- **Follow-ups / risks:** `F-16` remains the next sync/database guardrail.

### 2026-08-17 — Pull overlap cursor anchor fixed

- **Type:** decision
- **Story / Decision:** `F-13`
- **Author:** Codex, on behalf of David Ruiz
- **What changed:** pull pagination now uses `startAt(overlapSince, "")` for the first page of an overlapped cycle and `startAfter(pageCursor.lastServerUpdatedAt, pageCursor.lastDocumentId)` for later pages.
- **Why:** the owner chose the concrete-anchor option to avoid an invalid `null` document-id cursor while preserving the 30-second overlap window.
- **Documents touched:** `docs/CONTRACTS.md §9.4` and `§16`, `docs/SPECIFICATION.md §9.3`, `docs/TECHNICAL_PLAN.md §8`, `docs/BACKLOG.md`, `docs/AUDIT_GUARDRAILS.md`, and this log.
- **Verification:** manual cross-document search for `startAt`, `startAfter`, `overlapSince`, `RemoteCursor` and `null`; `git diff --check`.
- **Follow-ups / risks:** `F-14` and `F-16` remain the next sync/database guardrails.

### 2026-08-17 — Local owner adoption ordering decided

- **Type:** decision
- **Story / Decision:** `F-10`
- **Author:** Codex, on behalf of David Ruiz
- **What changed:** first launch now attempts Firebase anonymous authentication automatically; `LOCAL_OWNER` is the offline/Auth-unavailable fallback. Local synchronized rows carry `localMutationSeq`, and adoption builds the initial outbox in dependency-group order and then by `localMutationSeq ASC, id ASC`.
- **Why:** the owner chose deterministic local mutation ordering without creating a staging outbox for `LOCAL_OWNER`, preserving the rule that the outbox stays empty until a real UID exists.
- **Documents touched:** `docs/CONTRACTS.md §3`, `§8`, `§11.2`, `§11.4` and repository rules, `docs/SPECIFICATION.md`, `docs/TECHNICAL_PLAN.md §6`, `docs/BACKLOG.md`, `docs/AUDIT_GUARDRAILS.md`, and this log.
- **Verification:** manual cross-document search for `localMutationSeq`, `local_sequence`, `LOCAL_OWNER`, anonymous authentication and adoption; `git diff --check`.
- **Follow-ups / risks:** `F-13`, `F-14` and `F-16` remain the next sync/database guardrails.

### 2026-08-17 — Sync state storage decided

- **Type:** decision
- **Story / Decision:** `F-09`
- **Author:** Codex, on behalf of David Ruiz
- **What changed:** `syncState` is now defined as a stored local control column. The outbox influences it but does not fully define it, and `LOCAL_OWNER + PENDING + no outbox` is an explicit legal state before adoption.
- **Why:** the owner chose the stored-state option to resolve the contradiction between first-launch offline writes and the outbox suppression rule.
- **Documents touched:** `docs/CONTRACTS.md §7`, `docs/BACKLOG.md`, `docs/AUDIT_GUARDRAILS.md`, and this log.
- **Verification:** manual cross-document search for `syncState`, `LOCAL_OWNER`, `PENDING` and `outbox`; `git diff --check`.
- **Follow-ups / risks:** `F-10` remains open and must define how local-owner mutations preserve causal ordering during adoption.

### 2026-08-17 — MVP settings reset semantics decided

- **Type:** decision
- **Story / Decision:** `F-17`
- **Author:** Codex, on behalf of David Ruiz
- **What changed:** MVP settings now reset during destructive local-data flows. Sign-out, anonymous "delete local data" and account deletion delete `user_settings`; defaults are recreated from locale with `analyticsEnabled = false`.
- **Why:** the owner confirmed that settings should not survive in the MVP, while settings sync through Google Play services / Android backup / iCloud belongs to future roadmap scope.
- **Documents touched:** `AGENTS.md`, README, `docs/SPECIFICATION.md §3`, `§5.3` and `§7 F-5`, `docs/CONTRACTS.md §3 UserSettings` and `§11.5`, `docs/TECHNICAL_PLAN.md`, `docs/BACKLOG.md`, `docs/AUDIT_GUARDRAILS.md`, and this log.
- **Verification:** manual cross-document search for `settings`, `user_settings`, `Google Play`, `Android backup`, `iCloud`, sign-out, local-data deletion and account deletion; `git diff --check`.
- **Follow-ups / risks:** future platform settings sync requires a new story or ADR before adding platform APIs, entitlements, manifest keys or dependencies.

### 2026-08-17 — Electric and hybrid fuel types deferred

- **Type:** decision
- **Story / Decision:** `D-4` / `F-08`
- **Author:** Codex, on behalf of David Ruiz
- **What changed:** the MVP `FuelType` enum now excludes `ELECTRIC` and `HYBRID`; the canonical values are `GASOLINE`, `DIESEL`, `LPG`, `CNG` and `OTHER`. Electric and hybrid support is recorded as future roadmap scope requiring a dedicated energy model.
- **Why:** the owner confirmed that electric and hybrid vehicles are not included in the MVP, and supporting them correctly requires kWh input, mixed energy units, validation, Firestore rules and migration work.
- **Documents touched:** `AGENTS.md`, `docs/CONTRACTS.md §3`, `§5` and `§20.4`, `docs/SPECIFICATION.md §3` and `§12`, `docs/DECISION_BOARD.md`, `docs/TECHNICAL_PLAN.md §2`, `docs/adr/0005-vehicle-fuel-type-from-day-one.md`, `docs/adr/README.md`, `docs/BACKLOG.md`, `README.md`, `docs/AUDIT_GUARDRAILS.md`, and this log.
- **Verification:** manual cross-document search for `FuelType`, `ELECTRIC`, `HYBRID`, `energy model` and `D-4`; `git diff --check`.
- **Follow-ups / risks:** future electric/hybrid support requires a new story or ADR before enum/schema expansion.

### 2026-08-17 — Monetary and name data guardrails tightened

- **Type:** correction
- **Story / Decision:** `F-05`, `F-06`, `F-07`; `F-08` remains owner decision
- **Author:** Codex, on behalf of David Ruiz
- **What changed:** `MoneyInput` now selects the derived field only during validation, while persistence stores only the canonical monetary triple. `SUPPORTED_CURRENCY_CODES` is now the exact MVP currency allowlist, and vehicle name normalization / `nameFold` are defined as KMP-pure operations.
- **Why:** the previous contract implied an authoritative supplied monetary pair without storing it, left currency support dependent on an unspecified ISO lookup, and described `nameFold` differently from the normalization rules.
- **Documents touched:** `docs/CONTRACTS.md §2`, `§3`, `§5`, `§20.0.1` and `§20.3`, `docs/SPECIFICATION.md §5.3`, `docs/BACKLOG.md`, `docs/AUDIT_GUARDRAILS.md`, and this log.
- **Verification:** manual cross-document search for `MoneyInput`, `moneyInputKind`, `SUPPORTED_CURRENCY_CODES`, `MinorUnits`, `canonicalVehicleName`, `nameFold`, `FuelType`, `ELECTRIC` and `HYBRID`; `git diff --check`.
- **Follow-ups / risks:** `F-08` still requires owner choice: reject `ELECTRIC` / `HYBRID` in MVP validation or remove them until an energy-model story exists.

### 2026-08-17 — Swift-facing graph contract made explicit

- **Type:** correction
- **Story / Decision:** `D-2` / `F-02`, `F-03`, `F-04`, `F-18`
- **Author:** Codex, on behalf of David Ruiz
- **What changed:** the Swift-facing ABI is now an explicit allowlist with `createSwiftAppGraph(isDebugBuild)`, `SwiftAppGraph`, concrete state holders, declared `UiState` classes and `UiMessage`; Kotlin-facing graph construction remains available through `createAppGraph(AppGraphDependencies)` but is hidden from the Objective-C header.
- **Why:** the previous contract mixed Kotlin construction APIs with Swift-exported APIs, exposed implementation-facing abstractions such as `SyncController`, and referenced state-holder / `UiState` types that were not declared.
- **Documents touched:** `docs/CONTRACTS.md §11.6`, `§14`, `§15.3`, `§18`, `§20.7` and `§20.10`, `docs/SPECIFICATION.md §8.4` and `§8.5`, `docs/TECHNICAL_PLAN.md §5`, `docs/BACKLOG.md`, `docs/AUDIT_GUARDRAILS.md`, and this log.
- **Verification:** manual cross-document search for `SwiftAppGraph`, `AppGraphDependencies`, `createAppGraph`, `AppGraph`, `SyncController`, `CoroutineScope`, `Outcome` and `AppError`; `git diff --check`.
- **Follow-ups / risks:** changes touch gated contract and specification documents and require human review before merge. Next open audit block is `F-05`, `F-06`, `F-07` and `F-08`.

### 2026-08-17 — Crash reporting module ownership aligned

- **Type:** correction
- **Story / Decision:** `D-21` / `F-01`, `F-19`
- **Author:** Codex, on behalf of David Ruiz
- **What changed:** `CrashReporter` is now owned by `:core:crash`, included in `AppGraphDependencies` from Phase 0, and represented consistently in the module lists. Firebase Crashlytics remains a Phase 4 integration behind that abstraction.
- **Why:** the previous wording split ownership between `:core:common`, `:core:crash` and Phase 4 wiring, which made graph construction and module bootstrap ambiguous.
- **Documents touched:** `docs/CONTRACTS.md §11.6` and `§20.3.1`, `docs/SPECIFICATION.md §8.2` and `§8.3`, `docs/TECHNICAL_PLAN.md §4`, `docs/BACKLOG.md`, `README.md`, `docs/AUDIT_GUARDRAILS.md`, and this log.
- **Verification:** manual cross-document search for `CrashReporter`, `:core:crash` and `:integration:firebase-crashlytics`.
- **Follow-ups / risks:** changes touch gated contract and specification documents and require human review before merge. `F-02`, `F-03`, `F-04` and `F-18` remain open around the Swift-facing graph surface.

### 2026-08-17 — Empty owner-decision queue made explicit

- **Type:** correction
- **Story / Decision:** `E0-00`
- **Author:** Codex, on behalf of David Ruiz
- **What changed:** the "Decisions Awaiting Owner Confirmation" section now has a clear no-open-decisions state instead of an empty table with a sentence where a row would normally be.
- **Why:** the future `contract-check` should not have to parse a malformed table to determine that no `Proposed` or `Pending` decisions exist.
- **Documents touched:** `docs/DECISION_BOARD.md`, `docs/CONTRACTS.md §18`, `docs/AUDIT_GUARDRAILS.md`, and this log.
- **Verification:** manual review of the decision-board empty state and the corresponding `contract-check` assertion.
- **Follow-ups / risks:** if a future `Proposed` or `Pending` decision is introduced, this section must become a real table with a `Needed by` story or phase.

### 2026-08-17 — D-14 future production project clarified

- **Type:** decision
- **Story / Decision:** `D-14`
- **Author:** Codex, on behalf of David Ruiz
- **What changed:** documentation now states that development uses only `carapp-dev` for now, but public release requires a separate production Firebase project.
- **Why:** the owner clarified that the future topology is two Firebase projects, development and production, while production project creation and its project ID remain deferred until release preparation.
- **Documents touched:** `docs/DECISION_BOARD.md`, `docs/SPECIFICATION.md §12`, `docs/TECHNICAL_PLAN.md §2`, `docs/identifiers.md`, `docs/BACKLOG.md`, `docs/adr/0015-firebase-project-topology.md`, `docs/adr/README.md`, `docs/AUDIT_GUARDRAILS.md`, and this log.
- **Verification:** manual cross-document search for production Firebase topology, production project ID and `D-14` wording.
- **Follow-ups / risks:** the production Firebase project ID remains an owner decision before `E4-04`; agents MUST NOT invent it.

### 2026-08-17 — Firebase identifier wording aligned with D-14

- **Type:** correction
- **Story / Decision:** `E0-00` / `D-14`
- **Author:** Codex, on behalf of David Ruiz
- **What changed:** `E0-00` and the document map now require only the development Firebase project ID during development, while production Firebase topology and project IDs remain explicitly deferred by `D-14`.
- **Why:** the previous wording said "Firebase project IDs" in plural and could lead an agent to invent a production project ID to satisfy an already-closed owner-decision story.
- **Documents touched:** `AGENTS.md`, `docs/BACKLOG.md`, `docs/AUDIT_GUARDRAILS.md`, and this log.
- **Verification:** manual cross-document search for Firebase project ID wording and production-project references.
- **Follow-ups / risks:** production Firebase topology remains an owner decision before `E4-04`.

### 2026-08-17 — E0-00 closure reflected as completed

- **Type:** story
- **Story / Decision:** `E0-00`
- **Author:** Codex, on behalf of David Ruiz
- **What changed:** orientation documents now state that owner decision closure is complete and that implementation starts at `E0-01`.
- **Why:** after `D-13` through `D-22` were accepted and pushed, keeping `E0-00` as the next implementation step would send future agents back through already-closed decisions.
- **Documents touched:** `README.md`, `docs/DEFINITION.md`, `docs/BACKLOG.md`, `docs/AUDIT_GUARDRAILS.md`, and this log.
- **Verification:** manual cross-document search for `E0-00`, `Proposed`, `Pending`, and owner-decision closure wording.
- **Follow-ups / risks:** `docs/AUDIT_GUARDRAILS.md` remains temporary until the owner finishes reviewing every audit-remediation theme.

### 2026-08-17 — D-21 crash reporting accepted

- **Type:** decision
- **Story / Decision:** `D-21`
- **Author:** Codex, on behalf of David Ruiz
- **What changed:** Firebase Crashlytics was accepted for Phase 4 behind a new `CrashReporter` abstraction.
- **Why:** the owner selected Crashlytics for release hardening while keeping crash reporting separate from Kermit logging and Firebase Analytics.
- **Documents touched:** `docs/DECISION_BOARD.md`, `docs/SPECIFICATION.md §8` and `§12`, `docs/CONTRACTS.md §11.6` and `§20.3`, `docs/TECHNICAL_PLAN.md §2` and `§3`, `docs/BACKLOG.md`, `docs/versions-matrix.md`, `docs/SECURITY.md`, `docs/adr/0016-logging-kermit.md`, `docs/adr/0023-firebase-crashlytics.md`, `docs/adr/README.md`, and this log.
- **Verification:** manual cross-document search for `D-21`, Crashlytics and `CrashReporter` references.
- **Follow-ups / risks:** production Firebase topology remains deferred by `D-14` until `E4-04`.

### 2026-08-17 — D-20 localization implementation accepted

- **Type:** decision
- **Story / Decision:** `D-20`
- **Author:** Codex, on behalf of David Ruiz
- **What changed:** native Android and iOS resources were accepted for localization, with `UiState` carrying typed values only and no user-facing text.
- **Why:** the owner selected the existing recommendation; the UI is native, so native resource catalogues keep localization idiomatic without adding a shared resource dependency.
- **Documents touched:** `docs/DECISION_BOARD.md`, `docs/SPECIFICATION.md §12`, `docs/TECHNICAL_PLAN.md §2`, `docs/adr/0020-localization-native-resources.md`, `docs/adr/README.md`, and this log.
- **Verification:** manual cross-document search for `D-20`, localization, native resources and `UiState` status references.
- **Follow-ups / risks:** `D-21` remains unresolved and is needed before `E4-04`.

### 2026-08-17 — D-18 coverage measurement accepted

- **Type:** decision
- **Story / Decision:** `D-18`
- **Author:** Codex, on behalf of David Ruiz
- **What changed:** Kover was accepted as the coverage measurement tool with the existing thresholds: `:core:model` and `:core:common` at 90%, feature `domain` at 85%, and `:core:sync` at 80%.
- **Why:** the owner selected the existing recommendation so coverage targets become CI-enforced pass/fail criteria rather than review judgement.
- **Documents touched:** `docs/DECISION_BOARD.md`, `docs/SPECIFICATION.md §12`, `docs/TECHNICAL_PLAN.md §2`, `docs/BACKLOG.md`, `docs/adr/0022-coverage-kover.md`, `docs/adr/README.md`, and this log.
- **Verification:** manual cross-document search for `D-18`, Kover and coverage status references.
- **Follow-ups / risks:** Kover version compatibility remains validated in `E0-06`; `D-20` and `D-21` remain unresolved.

### 2026-08-17 — D-17 Flow testing helper accepted

- **Type:** decision
- **Story / Decision:** `D-17`
- **Author:** Codex, on behalf of David Ruiz
- **What changed:** Turbine was accepted as the Flow testing helper, and the previous combined D-17/D-18 ADR was split into separate ADRs so each decision can carry its own status.
- **Why:** the owner selected Turbine; `D-18` remains unresolved, so sharing one ADR made status verification ambiguous.
- **Documents touched:** `docs/DECISION_BOARD.md`, `docs/SPECIFICATION.md §12`, `docs/TECHNICAL_PLAN.md §2`, `docs/BACKLOG.md`, `docs/adr/0019-flow-testing-turbine.md`, `docs/adr/0022-coverage-kover.md`, `docs/adr/README.md`, and this log.
- **Verification:** manual cross-document search for `D-17`, `D-18`, Turbine, Kover and ADR status references.
- **Follow-ups / risks:** Turbine compatibility remains validated in `E0-06`; `E0-05` remains blocked by `D-18`.

### 2026-08-17 — D-19 result channel accepted

- **Type:** decision
- **Story / Decision:** `D-19`
- **Author:** Codex, on behalf of David Ruiz
- **What changed:** the owner accepted the custom `Outcome<T, E>` result channel in `:core:common`.
- **Why:** `kotlin.Result` cannot represent a typed error channel, and Arrow would add a dependency for one core type; the custom type matches the contracts exactly.
- **Documents touched:** `docs/DECISION_BOARD.md`, `docs/SPECIFICATION.md §12`, `docs/TECHNICAL_PLAN.md §2`, `docs/BACKLOG.md`, `docs/adr/0018-outcome-result-type.md`, `docs/adr/README.md`, and this log.
- **Verification:** manual cross-document search for `D-19`, `Outcome<T, E>` and result type status references.
- **Follow-ups / risks:** `D-17`, `D-18`, `D-20` and `D-21` remain unresolved; `E0-05` is still blocked by `D-17` and `D-18`.

### 2026-08-17 — D-16 architecture checks accepted

- **Type:** decision
- **Story / Decision:** `D-16`
- **Author:** Codex, on behalf of David Ruiz
- **What changed:** Konsist was accepted for package-level architecture rules, with a custom Gradle configuration check for module-level rules.
- **Why:** the owner selected the existing recommendation; package rules are intra-module source rules that Gradle cannot see, while module rules remain cheaper to enforce from the Gradle graph.
- **Documents touched:** `docs/DECISION_BOARD.md`, `docs/SPECIFICATION.md §12`, `docs/TECHNICAL_PLAN.md §2`, `docs/BACKLOG.md`, `docs/adr/0017-architecture-checks-konsist.md`, `docs/adr/README.md`, and this log.
- **Verification:** manual cross-document search for `D-16`, Konsist and architecture check status references.
- **Follow-ups / risks:** Konsist version pinning remains part of `E0-06`; `D-17`, `D-18`, `D-19`, `D-20` and `D-21` remain unresolved.

### 2026-08-17 — D-15 logging telemetry boundary clarified

- **Type:** decision
- **Story / Decision:** `D-15`
- **Author:** Codex, on behalf of David Ruiz
- **What changed:** documentation now states that Kermit is the logging implementation only and does not replace `AnalyticsTracker`, Firebase Analytics, `CrashReporter` or Firebase Crashlytics.
- **Why:** the owner said Kermit should be used for Firebase Analytics and Crashlytics; the clarification preserves the intent that Firebase integrations may use Kermit-backed logging for diagnostics while keeping analytics events and crash reports on separate contracts.
- **Documents touched:** `docs/DECISION_BOARD.md`, `docs/CONTRACTS.md §17`, `docs/adr/0016-logging-kermit.md`, and this log.
- **Verification:** manual cross-document search for Kermit, Analytics, Crashlytics, `AnalyticsTracker` and `CrashReporter`.
- **Follow-ups / risks:** `D-21` still decides whether Firebase Crashlytics is part of the MVP hardening phase.

### 2026-08-17 — D-15 logging implementation accepted

- **Type:** decision
- **Story / Decision:** `D-15`
- **Author:** Codex, on behalf of David Ruiz
- **What changed:** Kermit was accepted as the logging implementation behind the common `Logger` abstraction.
- **Why:** the owner selected the existing recommendation; Kermit provides KMP platform sinks while remaining hidden behind the repository-owned logging contract.
- **Documents touched:** `docs/DECISION_BOARD.md`, `docs/SPECIFICATION.md §12`, `docs/TECHNICAL_PLAN.md §2`, `docs/BACKLOG.md`, `docs/adr/0016-logging-kermit.md`, `docs/adr/README.md`, and this log.
- **Verification:** manual cross-document search for `D-15`, Kermit and logging status references.
- **Follow-ups / risks:** Kermit version pinning remains part of `E0-06`; `D-16`, `D-17`, `D-18`, `D-19`, `D-20` and `D-21` remain unresolved.

### 2026-08-17 — D-22 application identifiers accepted

- **Type:** decision
- **Story / Decision:** `D-22`
- **Author:** Codex, on behalf of David Ruiz
- **What changed:** the owner accepted `carApp` as the product name, `com.ruizurraca.carapp` as the Android application ID, Android namespace, iOS bundle identifier and shared package root, `Shared` as the iOS framework name, and `carapp-dev` as the development Firebase project ID.
- **Why:** these identifiers are required before `E0-01` creates platform projects and Firebase app registrations.
- **Documents touched:** `docs/DECISION_BOARD.md`, `docs/SPECIFICATION.md §12`, `docs/TECHNICAL_PLAN.md §2`, `docs/BACKLOG.md`, `docs/identifiers.md`, `docs/adr/0021-application-identifiers.md`, `docs/adr/README.md`, and this log.
- **Verification:** manual cross-document search for `D-22`, `carApp`, `carapp-dev` and `com.ruizurraca.carapp`.
- **Follow-ups / risks:** production Firebase topology and production project identifiers remain deferred by `D-14` until `E4-04`.

### 2026-08-17 — D-22 application package prefix changed

- **Type:** decision
- **Story / Decision:** `D-22`
- **Author:** Codex, on behalf of David Ruiz
- **What changed:** the proposed Android `applicationId`, Android namespace, iOS bundle identifier and shared package root changed from `com.davidru85.carapp` to `com.ruizurraca.carapp`.
- **Why:** the owner selected the `ruizurraca` package prefix.
- **Documents touched:** `docs/identifiers.md`, `docs/adr/0021-application-identifiers.md`, and this log.
- **Verification:** manual cross-document search for `com.davidru85.carapp` and `com.ruizurraca.carapp`.
- **Follow-ups / risks:** `D-22` still requires owner confirmation for the remaining identifier values, including the product name and development Firebase project ID.

### 2026-08-17 — D-14 Firebase project topology accepted for development

- **Type:** decision
- **Story / Decision:** `D-14`
- **Author:** Codex, on behalf of David Ruiz
- **What changed:** Firebase topology changed from proposed `dev` plus `prod` projects to one development Firebase project plus the local emulator; production Firebase topology is deferred until release preparation.
- **Why:** the owner chose a simpler development setup because Firestore is only a backup and synchronization replica, while Room remains the source of truth.
- **Documents touched:** `docs/DECISION_BOARD.md`, `docs/SPECIFICATION.md §12`, `docs/TECHNICAL_PLAN.md §2`, `docs/BACKLOG.md`, `docs/identifiers.md`, `docs/adr/0015-firebase-project-topology.md`, `docs/adr/README.md`, `docs/SECURITY.md`, and this log.
- **Verification:** manual cross-document search for `D-14`, Firebase topology, project ID, emulator and production references.
- **Follow-ups / risks:** development Firebase project ID remains governed by `D-22`. Production topology and production project IDs must be decided before `E4-04`.

### 2026-08-17 — D-13 Firestore location accepted

- **Type:** decision
- **Story / Decision:** `D-13`
- **Author:** Codex, on behalf of David Ruiz
- **What changed:** Firestore location changed from the proposed `eur3` multi-region to the accepted `europe-west1` single region, and the documentation now states that Firestore is a backup and synchronization replica while Room remains the source of truth.
- **Why:** the owner chose `europe-west1` after reviewing the cost and availability tradeoff, because Firestore is not the primary product database.
- **Documents touched:** `docs/DECISION_BOARD.md`, `docs/SPECIFICATION.md §9` and `§12`, `docs/CONTRACTS.md §10`, `docs/TECHNICAL_PLAN.md §2`, `docs/identifiers.md`, `docs/adr/0014-firestore-location-europe-west1.md`, `docs/adr/README.md`, `docs/BACKLOG.md`, and this log.
- **Verification:** manual cross-document search for `D-13`, `eur3` and Firestore location references.
- **Follow-ups / risks:** `D-14`, `D-15`, `D-16`, `D-17`, `D-18`, `D-19`, `D-20`, `D-21` and `D-22` remain unresolved.

### 2026-08-17 — Agent handoff and contract-check guardrails tightened

- **Type:** milestone
- **Story / Decision:** —
- **Author:** Codex, on behalf of David Ruiz
- **What changed:** added an explicit story intake protocol, required acceptance evidence in handoffs, and expanded `contract-check` to verify decision statuses, ADR status alignment, unresolved decision tracking and PR-template/handoff coverage.
- **Why:** the documentation already defined strong product contracts, but future agents still needed a more mechanical way to prove a story was Ready before implementation and Done after implementation.
- **Documents touched:** `AGENTS.md`, `docs/SPECIFICATION.md §12`, `docs/CONTRACTS.md §18`, `docs/DECISION_BOARD.md`, `docs/TECHNICAL_PLAN.md §2`, `docs/BACKLOG.md`, `docs/adr/README.md`, `docs/templates/agent-handoff.md`, `.github/pull_request_template.md`, and this log.
- **Verification:** manual documentation review with `rg` and targeted file reads. No product code or CI exists yet.
- **Follow-ups / risks:** changes touch gated documentation paths and require human review before merge.

### 2026-08-17 — Audit findings folded back into the documentation

- **Type:** milestone
- **Story / Decision:** —
- **Author:** Claude (Cowork session), on behalf of David Ruiz
- **What changed:** the 99 findings of the specification audit were applied across the whole documentation set. The main structural changes: document authority is now split into a behaviour axis (`docs/SPECIFICATION.md`) and a representation axis (`docs/CONTRACTS.md`) instead of a single linear ranking; `docs/CONTRACTS.md` gained `§20` with the complete canonical type definitions that were previously referenced but never declared; the field vocabulary was unified on the `…Km` / `…Scaled` / `…Minor` names; the monetary formulas were rewritten as exact integer arithmetic with golden test values; the pull query gained a `startAfter` anchor; LWW arbitration now compares `serverUpdatedAt` instead of the local clock; first launch was made offline-capable through the `LOCAL_OWNER` sentinel with a new adoption story; and human review gates, reading order and normative language were consolidated into `AGENTS.md` as the single entry point.
- **Why:** the definition package was complete in intent but not machine-decidable. Roughly twenty types appeared in normative signatures without being defined anywhere, two documents used incompatible field names, and several rules were mutually contradictory — enough that two competent agents would have produced two incompatible implementations, with no CI check able to catch it.
- **Documents touched:** `AGENTS.md`, `docs/SPECIFICATION.md`, `docs/CONTRACTS.md`, `docs/DECISION_BOARD.md`, `docs/TECHNICAL_PLAN.md`, `docs/BACKLOG.md`, `docs/DEFINITION.md`, `README.md`, `docs/CONTRIBUTING.md`, `docs/SECURITY.md`, `docs/adr/*`, `docs/identifiers.md`, `docs/versions-matrix.md`, `docs/templates/agent-handoff.md`, `.github/pull_request_template.md`, and this log.
- **Verification:** manual cross-document review. The automated `contract-check` that would enforce these invariants does not exist yet; it is specified in `docs/CONTRACTS.md §18` and implemented by `E0-05`.
- **Follow-ups / risks:** ten decisions are `Proposed` or `Pending` and require owner confirmation before Phase 0 starts — see `E0-00` and the "Decisions Awaiting Owner Confirmation" table in `docs/DECISION_BOARD.md`. Five new stories were added (`E0-00`, `E1-10`, `E2-06`, `E3-07`, `E3-08`) and are not yet estimated against real capacity. `docs/AUDIT_GUARDRAILS.md` remains in the repository as a temporary resolution log and should be deleted once the owner has reviewed the changes.

### 2026-08-17 — Specification audit performed

- **Type:** milestone
- **Story / Decision:** —
- **Author:** Claude (Cowork session), on behalf of David Ruiz
- **What changed:** a full architectural audit of the definition package produced `docs/AUDIT_GUARDRAILS.md`: 99 findings across governance, KMP architecture, data modeling, workflows and state, error handling, API contracts, security and verifiability, of which 16 were classified as blocking. Committed as `f8b70cb`.
- **Why:** the project is intended to be implemented mostly by AI agents, so ambiguity in the specification translates directly into divergent implementations rather than into questions.
- **Documents touched:** `docs/AUDIT_GUARDRAILS.md` (new, temporary).
- **Verification:** every finding cites a literal section of the documents as they stood on 2026-08-16.
- **Follow-ups / risks:** the audit file is temporary and is deleted once its findings are absorbed and reviewed.

### 2026-08-16 — Definition package completed

- **Type:** milestone
- **Story / Decision:** `D-0` to `D-11`
- **Author:** David Ruiz
- **What changed:** the initial definition package was written: specification, contracts, decision board, technical plan, backlog, agent operating guide, and thirteen ADRs covering the closed technical decisions. The repository remains greenfield, with no product code.
- **Why:** to make the project implementable by AI agents with predictable boundaries, before writing any code.
- **Documents touched:** all initial documents.
- **Verification:** —
- **Follow-ups / risks:** version pinning and real CI command validation deferred to Phase 0.
