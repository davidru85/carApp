# ADR-0120 / D-119 - Declare the Repository Files the Guards Read as Task Inputs

## Status

Accepted

Selected by the owner on 2026-09-05.

## Context

The guard tests in `build-logic/convention/src/test` read committed repository files — the Xcode
project, `iosApp/project.yml`, the Android manifest and resources, the version catalogue, the CI
workflow, `docs/**` — through the `carapp.repoRoot` system property.

Gradle cannot observe a read made that way. The test task declared none of those files as inputs, so
after any of them changed Gradle still considered the task up to date and reported the previous
result without executing anything.

That is how the `D-71` violation of E2-03 reached CI. The full `AGENTS.md` gate was reported locally
as passing with 636 tasks while `:build-logic:convention:test` had not run, and the protected
`architecture-check` job failed on a clean CI checkout. The defect is worse than any single rule it
hides: a guard whose whole purpose is to catch a mistake reported a stale pass, and every local
verification claim about it was unfounded.

## Options Considered

| Option | Benefits | Costs / Risks |
|--------|----------|---------------|
| Declare the repository files as task inputs (Selected) | Gradle re-runs exactly when guarded content changes; local results become trustworthy again; incremental builds keep working | The declared set has to grow when a guard starts reading something new |
| Mark the task as never up to date | One line, nothing to maintain | Pays the full suite on every invocation and discards caching even when it was legitimate |
| Rely on `--rerun-tasks` discipline | No change | Depends on every human and agent remembering, which is precisely the kind of lapse this suite exists to catch; it had already failed once |

## Decision

`build-logic/convention/build.gradle.kts` declares `guardedRepositoryInputs`, a file tree over the
committed paths the guards read, as an input of the test task with relative path sensitivity. Build
outputs and Xcode user state are excluded.

`GuardedRepositoryInputsTest` closes the maintenance gap of the selected option: it extracts every
repository-relative path the guards read, keeps those that resolve to a committed file, and fails
when one is not covered by the declared patterns. A guard that starts reading a new file therefore
fails loudly instead of silently going stale.

## Consequences

### Positive

- A guarded configuration change re-runs the guards without `--rerun-tasks`, so a local pass means
  what it says.
- The declared set cannot silently fall behind the guards.

### Negative

- Editing a guarded file re-runs the build-logic suite, a few seconds on an incremental build.
- The input declaration and the coverage test are themselves configuration that has to stay correct;
  a reformatting of the declaration breaks the coverage test loudly rather than silently.

### Constraints Introduced

- A guard that reads a repository file MUST have that file covered by `guardedRepositoryInputs`.
- Verification evidence for these guards MAY stop citing `--rerun-tasks`, because an ordinary
  invocation is now sound. Any claim that the suite passed MUST still come from a run that actually
  executed it.

## Verification

- `GuardedRepositoryInputsTest` fails when a read path is undeclared; it failed before the
  declaration existed and passes after it.
- Behavioural proof of the original defect and its repair: injecting `DEVELOPMENT_TEAM` into
  `iosApp/project.yml` and running `./gradlew :build-logic:convention:test` without `--rerun-tasks`
  now executes the suite and fails on the signing guard, where before the change the same command
  reported `UP-TO-DATE`.

## References

- `docs/DECISION_BOARD.md` (`D-119`)
- `AGENTS.md`, the complete non-instrumented verification command
- [ADR-0072](0072-use-normal-ios-simulator-signing.md) (`D-71`)
- [ADR-0119](0119-supply-ios-device-signing-locally.md) (`D-118`)
