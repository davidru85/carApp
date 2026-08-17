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
