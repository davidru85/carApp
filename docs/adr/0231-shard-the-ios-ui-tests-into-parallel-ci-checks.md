# ADR-0231 / D-230 - Shard the iOS UI Tests into Parallel CI Checks

## Status

Accepted

The owner selected option B on 2026-10-22. It supersedes the single-job, 60-minute clause of `D-229`
(ADR-0230); the rest of `D-229` stands.

## Context

`D-229` (ADR-0230) moved the `carAppUITests` suite out of `ios-simulator-build` into one protected
`ios-ui-tests` job with its own 60-minute ceiling. On run 39087272433 (pull request #91, head
`000fae86`, 2026-10-18) that job was cancelled at its ceiling with no result for most of the suite:

| Phase | Duration |
|-------|----------|
| checkout, setup, the pinned-Xcode check, the framework link and the test-app compile | ~11.5 minutes (01:39:48 to 01:51:16) |
| `carAppUITests` until the cancellation at 02:40:02 | 48.7 minutes, inside `LargeContentSizeUITests`, with five classes not started |

The suite grew from 54 tests on the last green run (`0f94b4cd`, 44 minutes in total) to 85 tests, so it
is estimated at ~80 minutes of tests on the runner. Eight tests that audit system `alert` surfaces take
4 to 7 minutes each (`AccessibilityAuditUITests` pending-sync 415 s, collision 353 s, delete-local-data
274 s, odometer-warning 278 s, delete-account 272 s, and their `LargeContentSizeUITests` AX3
counterparts 257 to 288 s). Most of that time, 37.8 of the 48.7 minutes, is spent by XCTest
re-resolving audit issue elements (`Find the Any (Descendant with identity binding to AX element …)
(retry 1/2)`); this decision does not change that cost, which is a separate question.

One check that needs more than an hour is the failure mode `D-229` was written to remove: a cancelled
check reports no test result and is indistinguishable from a real regression. Raising the ceiling again
would repeat the mistake `D-176` and ADR-0230 both warn against.

Constraints the options must respect:

- every UI job repeats ~11.5 minutes of framework link and test-app compile, which is the floor of any
  shard;
- GitHub documents a limit of five concurrent macOS jobs for the Free and Pro plans, and the workflow
  already had five macOS jobs, so extra shards queue for the few minutes the short jobs take;
- the repository is public, so macOS runner minutes cost nothing;
- `WorkflowTimeoutContract` reads `.github/workflows/ci.yml` line by line and protects check names
  literally (assertions 27 to 30), so a matrix job, whose check names are expanded at run time, would
  need a matrix-aware parser;
- `ios-ui-tests` is not yet in `main`'s branch protection, so replacing it renames no required check.

## Options Considered

| Option | Benefits | Costs / Risks |
|--------|----------|---------------|
| A. One job per test class: audit, large content, and the rest | No method lists; trivial workflow. | The audit job is ~46 minutes and the large-content job ~44, so both still need the 60-minute exception with ~14 minutes of headroom, and the next alert test breaks them again. |
| B. Five shard jobs balanced by test method through a committed manifest, with an implicit `remainder` shard and every shard at the 40-minute default | Each shard holds ~15 to 17 minutes of tests, ~27 to 29 minutes in total, so the 60-minute exception is retired and every job keeps the `D-176` guard. Completeness is by construction: a class entry runs every method another shard does not name, and `remainder` runs every unlisted test. | A manifest to maintain, guarded by a contract assertion; five check names for branch protection; a few minutes of queueing behind the macOS concurrency limit. |
| C. Build once (`build-for-testing`) and share the products with `test-without-building` shards | Saves runner minutes. | No wall-clock gain, because the shards wait for the build job anyway; adds a large artifact (the app, the test runner and Firebase) moved between jobs. Rejected: minutes are free on a public repository. |
| D. `-parallel-testing-enabled` inside one runner | One job. | Not separate checks; the 3-vCPU, 7 GB runner would run several simulators that each audit, which is slower and less stable (`E1-17` already had a UI-test flake). Rejected. |

## Decision

The selected option is **B**.

- `.github/workflows/ci.yml` replaces `ios-ui-tests` with five protected jobs, `ios-ui-tests-audit-1`,
  `ios-ui-tests-audit-2`, `ios-ui-tests-ax3-1`, `ios-ui-tests-ax3-2` and `ios-ui-tests-remainder`. Each
  runs on `macos-latest` at `timeout-minutes: 40`, repeats the checkout, Java, Gradle and pinned-Xcode
  steps, links `:composition:ios:linkDebugFrameworkIosSimulatorArm64`, and runs
  `scripts/ios-ui-test-shard.sh <shard>`. Distinct jobs, not a matrix, keep every check name literal
  for the contract and for branch protection.
- `scripts/ios-ui-test-shards.txt` assigns `<shard> <TestClass>[/<testMethod>]` entries. `audit-1` and
  `ax3-1` name the three slowest alert audits of their class; `audit-2` and `ax3-2` name the class and
  therefore run every other method of it, including methods added later; `remainder` is implicit and
  runs every test no entry names, including classes added later.
- `scripts/ios-ui-test-shard.sh` turns a shard into `-only-testing`/`-skip-testing` arguments, picks the
  first available iPhone simulator, and runs `xcodebuild … test`. It is written for the macOS system
  bash 3.2 and has a `--print-selection` mode.
- `scripts/check-xcode-pin.sh` holds the pinned-Xcode check that `ios-simulator-build` and the five
  shards share, so the check has one definition.
- `WorkflowTimeoutContract.JOB_CEILING_MINUTES` becomes empty and `PROTECTED_CHECK_NAMES` replaces
  `ios-ui-tests` with the five shard names. `docs/CONTRACTS.md §18` lists the five required checks and
  gains assertion 45, implemented by `IosUiTestShardContract`.

## Consequences

### Positive

- Every UI shard reports its own result well inside its ceiling, so a failure names the shard and a
  cancellation stops hiding the rest of the suite.
- The 40-minute guard of `D-176` applies to every job again, with no per-job exception.
- A new test always runs: in its class's shard or in `remainder`. Assertion 45 turns the ways the
  manifest can rot (a renamed test, a duplicate, a shard with no job, a job that selects UI tests by
  hand) into contract failures instead of silently skipped tests.

### Negative

- The owner must add the five shard checks to `main`'s branch protection (`ios-ui-tests` was never
  added); until then no UI check is required.
- Each pull request spends five macOS runners on the framework link and the test-app compile, and some
  shards wait a few minutes for a macOS slot.
- The initial balance uses estimates for the tests run 39087272433 never reached; a shard can drift and
  needs rebalancing from a complete run.

### Constraints Introduced

- A UI test selection MUST go through `scripts/ios-ui-test-shard.sh` and the manifest; no job passes
  `-only-testing:carAppUITests` or `-skip-testing:carAppUITests` to `xcodebuild` itself (assertion 45).
- Every shard the manifest defines, plus `remainder`, MUST run in exactly one job whose id and check
  name are `ios-ui-tests-<shard>`, and that name MUST be in `PROTECTED_CHECK_NAMES` and the
  `docs/CONTRACTS.md §18` required list. Adding, removing or renaming a shard and updating branch
  protection are one change (`docs/CONTRIBUTING.md`).
- A shard that drifts past about 20 minutes of tests is rebalanced in the manifest or split into a new
  shard; it MUST NOT be given a taller ceiling unless a decision names it in `JOB_CEILING_MINUTES`.
- The rest of `D-229` is unchanged: `ios-simulator-build` keeps `-only-testing:carAppTests` and the
  `:feature:fuel:linkReleaseTestIosArm64` step at 40 minutes.

## Verification

- `xcodebuild … test-without-building -enumerate-tests` with each shard's `--print-selection` arguments
  enables 3, 14, 3, 13 and 52 tests for `audit-1`, `audit-2`, `ax3-1`, `ax3-2` and `remainder`; their
  union equals the 85 tests enabled by `-only-testing:carAppUITests`, and no two shards share a test.
- `./gradlew :build-logic:convention:test` keeps the firing fixtures: a stale method, an unknown class,
  a duplicate identifier, a malformed line, a listed `remainder`, a shard without a job, a job running an
  undefined shard, a missing `remainder` job, a shard run by two jobs, a shard job named after another
  shard and a direct `-only-testing:carAppUITests` each fail assertion 45; a shard at 60 minutes fails
  assertion 28; renaming any shard check fails assertion 30.
- `./gradlew contractCheck` reports assertions 27 to 30 and 45 `[PASS]` with no `[PENDING]`.
- The owner confirms on the pull request head that each shard completes inside 40 minutes.

## References

- `docs/DECISION_BOARD.md` (decision ID `D-230`)
- `docs/CONTRACTS.md §18` (required checks, assertions 27 to 30 and 45)
- `docs/adr/0230-split-ios-ui-tests-into-their-own-ci-job.md` (`D-229`)
- `docs/adr/0177-size-the-ci-job-hang-guard-above-the-slowest-healthy-run.md` (`D-176`)
- `docs/adr/0103-ios-unit-and-ui-test-targets-in-ci.md` (`D-102`)
- `scripts/ios-ui-test-shards.txt`, `scripts/ios-ui-test-shard.sh`, `scripts/check-xcode-pin.sh`
- `docs/handoffs/E4-B1.md`
