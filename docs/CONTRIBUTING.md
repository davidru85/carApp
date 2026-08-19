# Contributing

This repository is optimized for owner-led development with AI agents.

## Required Reading

`AGENTS.md` is the entry point. It defines document authority, normative language, the document map, the Definition of Ready and Done, and the human review gates. Reading order is listed there and MUST NOT be restated here.

## Working Rules

- Work from one backlog story at a time.
- Keep changes inside MVP scope (`docs/SPECIFICATION.md §3`).
- Preserve architecture boundaries (`docs/TECHNICAL_PLAN.md §4`).
- Add or update tests with behaviour changes.
- Keep user-facing strings localizable, and out of `UiState`.
- Do not use `Float` or `Double` for money.
- Do not duplicate business logic in native UI.
- Do not let the UI observe Firestore directly.
- Do not invent identifiers, project names, regions or versions: they live in `docs/identifiers.md`, `docs/versions-matrix.md` and `gradle/libs.versions.toml`.
- `:core:database` is a shared-write module. Only one story at a time may modify it, and the handoff must declare it.
- Follow the design assets indexed by `docs/DESIGN.md` when implementing UI. They are non-normative: where a design asset and `docs/SPECIFICATION.md` or `docs/CONTRACTS.md` disagree, escalate instead of implementing the design.

## Branches and Commits

- Branch: `story/<STORY-ID>-<short-slug>`, for example `story/E1-04-fuel-entry-domain`.
- Commits follow Conventional Commits with the story ID as scope: `feat(E1-04): derive price from liters and total`.
- Commit messages, code comments, ADRs and all repository artifacts are written in technical English. Conversation with the project owner may happen in Spanish.
- One story per pull request. A PR touching more than 40 files, or more than two modules outside its story's scope, should be split.

## Pull Requests

Use `.github/pull_request_template.md`, which mirrors `docs/templates/agent-handoff.md`. Every PR includes:

- The backlog story ID.
- Scope completed.
- Files changed and decisions made.
- Verification run, with the exact commands.
- Contract impact and decision board impact.
- Risks or follow-ups.
- Any human review gate touched.

A PR is not complete until an entry has been appended to `docs/PROJECT_LOG.md`.

## Human Review Gates

Defined canonically in `AGENTS.md`. Do not restate or reinterpret them here.
