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

## Continuity Between Agents

`AGENTS.md` §`Continuous Progress Documentation` is the canonical D-105 rule. Keep the current
story handoff updated at every material checkpoint and before yielding unfinished work, so another
agent can continue from the repository alone. Use the `In-Progress Checkpoint` fields in
`docs/templates/agent-handoff.md`; do not use chat history or an uncommitted mental plan as the sole
record of progress.

## Local Setup

Install the repository hooks once per clone:

```bash
git config core.hooksPath scripts/git-hooks
```

`scripts/git-hooks/pre-push` refuses a direct push to `main`. GitHub would accept one, because
administrator enforcement is off, and that hatch exists for recovery rather than for use. Override
it only for a genuine recovery, with `CARAPP_ALLOW_DIRECT_PUSH=1`.

**If more than one agent shares a clone, work in a `git worktree`.** Branch checkouts are global to a
clone, so a concurrent agent switching branches moves the ground under everyone else. A worktree
gives each agent its own checkout of its own branch:

```bash
git worktree add ../carApp-<slug> -b <type>/<slug> origin/main
```

## Branches and Commits

- Branch: `story/<STORY-ID>-<short-slug>`, for example `story/E1-04-fuel-entry-domain`.
- Confirm the branch before every commit. `git status` shows it, and in a shared clone it may not be
  the branch you left.
- Commits follow Conventional Commits with the story ID as scope: `feat(E1-04): derive price from liters and total`.
- Commit messages, code comments, ADRs and all repository artifacts are written in technical English. Conversation with the project owner may happen in Spanish.
- One story per pull request. A PR touching more than 40 files, or more than two modules outside its story's scope, should be split.
- **Every commit MUST be authored by `David Ruiz <davidru85@gmail.com>`.** The owner is the only
  author this history records; agents commit on their behalf and never under a placeholder identity.

### Commit identity

Set the author once per clone, and never leave a local override in place:

```bash
git config user.name "David Ruiz"
git config user.email "davidru85@gmail.com"
```

Worktrees share the clone's `.git/config` unless `extensions.worktreeConfig` is enabled, so a
`git config user.name …` run for a throwaway test — even in a temporary worktree you delete
afterwards — silently becomes the author of every later commit in every worktree of that clone. Set
the owner identity in each throwaway worktree as the first thing you do, or set the shared value back
before you commit. `git config --list --show-origin | grep user.` shows which file is winning.

If a commit was authored by a placeholder, correct it before it reaches `main`. `main` is protected by
a pull request, so the fix is a normal history rewrite on the *branch* — never on `main` — which needs
a force-push:

```bash
git commit --amend --no-edit --reset-author
# the whole branch:
git rebase origin/main --exec 'git commit --amend --no-edit --reset-author'
git push --force-with-lease origin <your-branch>
```

The rewrite range is bounded to this branch's own commits on top of `origin/main`; `--root` MUST NOT
be used, because it rewrites the whole repository history rather than the branch.

`--reset-author` takes the identity from the configuration above, so fix the configuration first.
`--force-with-lease` is required rather than `--force`, and the rewrite stays confined to your own
story branch: `main` is protected, no force push reaches it, and a pull request already merged cannot
be corrected this way.

## Before Opening a Pull Request

Run what CI runs. A pull request that fails a required check cannot merge, and finding out locally
costs seconds instead of a round trip:

```bash
./gradlew ktlintCheck detekt architectureCheck contractCheck :build-logic:convention:test \
          koverVerify :androidApp:assembleDebug :androidApp:testDebugUnitTest \
          testAndroidHostTest iosSimulatorArm64Test \
          -x :integration:firebase-auth:iosSimulatorArm64Test \
          -x :integration:firebase-firestore:iosSimulatorArm64Test \
          -x :wiring:firebase:iosSimulatorArm64Test \
          -x :composition:ios:iosSimulatorArm64Test
```

`AGENTS.md` §`Repository State` explains what each check proves.

## Pull Requests

`main` is protected. A change reaches it only through a pull request, and the ten checks of
`docs/CONTRACTS.md §18` MUST report green: `android-assemble`, `shared-tests`,
`ios-simulator-build`, `ktlint`, `detekt`, `architecture-check`, `provider-decoupling`,
`contract-check`, `objc-header-golden-check`, `android-instrumented-tests`. Force pushes and branch
deletion are refused.

Administrator enforcement is off, so the repository owner can bypass a red build. That is an escape
hatch for a single-maintainer repository, not a workflow: **do not ask for it, and do not rely on
it.** Renaming a CI job REQUIRES updating branch protection in the same change, or the renamed
check stops being required and silently stops gating.

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
