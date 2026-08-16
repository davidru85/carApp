# Contributing

This repository is currently optimized for owner-led development with AI agents.

## Required Reading

Before contributing, read:

1. `SPECIFICATION.md`
2. `TECHNICAL_PLAN.md`
3. `BACKLOG.md`
4. `AGENTS.md`

## Working Rules

- Work from one backlog story at a time.
- Keep changes inside MVP scope.
- Preserve architecture boundaries.
- Add or update tests with behavior changes.
- Keep user-facing strings localizable.
- Do not use `Float` or `Double` for money.
- Do not duplicate business logic in native UI.
- Do not let UI observe Firestore directly.

## Pull Requests

Every PR should include:

- The backlog story ID.
- Scope completed.
- Verification run.
- Risks or follow-ups.
- Any human review gate touched.

## Human Review Gates

Human review is mandatory for:

- Phase 0 closure.
- E0-07 walking skeleton.
- E1-05 consumption calculation.
- E3-01 Firestore security rules.
- E3-03 synchronization engine.
- Any change to scope, stack, backend, auth, sync, architecture, or money representation.
