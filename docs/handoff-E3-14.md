# Agent Handoff

## Story

`E3-14 - Orphan Cleanup Ticket Issuance Hardening - M`

## Ready Check

- Backlog story: `E3-14 - Orphan Cleanup Ticket Issuance Hardening - M`, added to `docs/BACKLOG.md`
  in this story from the two post-merge findings of the `E3-11` review of pull request #60. The
  interleaving that `E3-14` deliberately does not close is `E3-15`, which is **not Ready** because
  it depends on the `Proposed` decision `D-149`.
- Acceptance criteria reviewed: the five criteria of `E3-14`. The issuance/deletion interleaving is
  explicitly out of this story's scope and is escalated instead of worked around.
- Dependencies checked: `E3-10` (PR #58) and `E3-11` (PR #60) are merged into `main` at `7a79fab`,
  which is this branch's base. Pull requests #61 and #62 are open and untouched by this work.
- Decisions checked: `D-23`, `D-63`, `D-128`, `D-129`, `D-131`, `D-132`, `D-134`, `D-135`, `D-137`,
  `D-138`, `D-141`, `D-142` and `D-143` are `Accepted` and govern this code. No decision this story
  depends on is `Proposed` or `Pending` at intake; the story itself introduces `D-148` (`Accepted`)
  and `D-149` (`Proposed`, owner decision, needed by `E3-15`).
- Normative sections reviewed: `docs/CONTRACTS.md` §11.3, §11.5 (the deletion order, the ticket
  issuer, the consumption callable and their redaction rules), §16 (the internal server-only
  collection registry), §17 (logging and privacy), §18; `docs/SPECIFICATION.md` §3.2 (the closed
  Cloud Functions scope), §10, §12; `docs/TECHNICAL_PLAN.md` §2, §13 (`TD-01`, the sole 1st gen
  exception); `docs/DECISION_BOARD.md` (the Firebase decoupling rule and the awaiting-confirmation
  section); `docs/SECURITY.md`.
- Expected verification: the complete Functions unit suite, the Firestore emulator integration
  suite, the Firestore rules tests, the dependency audit, `contractCheck` with the build-logic
  fixture tests, the complete required Gradle command, the Functions and Firestore indexes
  dry-run, `git diff --check`, and the ten protected checks on the pull request.
- Human review gates identified before work: the story is marked "Human review required". Gated
  paths touched: `docs/CONTRACTS.md`, `docs/DECISION_BOARD.md`, `docs/adr/**`,
  `docs/SPECIFICATION.md`, `AGENTS.md`. Gated topics: authentication, the remote backend, and
  logging and privacy rules.
- Rule 0 acknowledged: chat replies for this story are in Spanish (es-ES) and every artifact it
  produces is in technical English.

## In-Progress Checkpoint

- Date: 2026-09-08
- Branch and base: `story/E3-14-orphan-ticket-issuance-hardening`, based on `main` at `7a79fab`.
- Current phase and latest commit: documentation phase; this handoff and the project log entries are
  the last change of the story.
- Push and pull-request status: pushed after this commit; the pull request targets `main`.
- Completed since the previous checkpoint: three red/green pairs, the decision records, the contract
  updates and the full verification battery.
- Verification evidence and known failures: see **Verification Run**. One `E1-14` flake occurred and
  is analysed there; no failure attributable to this change.
- Open decisions or blockers: `D-149` is `Proposed` and is the owner's. `E3-15` MUST NOT start until
  it is resolved.
- Exact next step: gated owner review, and a decision on `D-149`.

## Scope Completed

- Finding 2: `createAnonymousDeletionHandler` rejects with a newly constructed sanitized error
  instead of rethrowing the provider exception.
- Finding 1, the forced half: `issueOrphanCleanupTicket` resolves the caller's current Auth record
  through the Admin SDK before any write, and issues only while that record exists, is enabled and
  is still anonymous under the shared `D-134` predicate.
- A `contractCheck` defect that blocked recording the escalation: the awaiting-confirmation summary
  was being parsed as decision registry rows.
- `D-148` with ADR-0149, `D-149` with ADR-0150 as the owner's options, the four decision mirrors,
  and the `docs/CONTRACTS.md §11.5` rules for both findings.

## Acceptance Evidence

1. *The issuer resolves the caller's Auth record before writing, and rejects linked, disabled and
   deleted accounts.* `functions/test/orphanedAnonymousAccount.test.mjs`:
   `ticket issuance rejects stale anonymous claims once the account has been linked`,
   `... for a disabled account` and `... for a deleted account`. Each asserts
   `harness.calls` equals `[["getUser", ORPHAN_UID]]`, so the lookup happened **and** no
   authorization was created. The happy-path test asserts the lookup precedes the write.
2. *A rejected issuance leaks nothing.* `a rejected issuance leaks no UID, token, payload or raw
   failure` serializes the logs and the rejection and asserts none of them contains the UID, the
   token or the raw provider text.
3. *An Admin lookup failure is distinguishable from an eligibility rejection.*
   `ticket issuance maps an Admin lookup failure to internal with a redacted stage` pins the
   `internal` code and the single redacted `AUTH_USER` log line.
4. *The trigger rejects with a sanitized error and still retries.*
   `functions/test/anonymousCleanup.test.mjs`:
   `the native trigger rejects with an error carrying none of the raw failure` fails the deletion
   with a message embedding a UID-bearing Firestore path and asserts that the message, the stack,
   the enumerable properties and the cause carry none of it, while requiring a rejection so
   `failurePolicy: true` still retries; `the native trigger rejection is a newly constructed error,
   not the provider one` pins that the rejected value is not the thrown object. The pre-existing
   `the native trigger retries a failed deletion on redelivery` still passes, which is the retry
   behaviour itself.
5. *Existing `E3-10` and `E3-11` behaviour is preserved.* The complete Functions suite is green:
   73 passing, 0 failing, with the two emulator-gated tests running separately.

## Out of Scope / Not Done

- **The issuance/deletion interleaving is not closed.** It is `E3-15`, blocked on the `Proposed`
  `D-149`. ADR-0150 carries the full interleaving analysis and three concrete owner options with
  their costs. This is an escalation, not a workaround: the analysis is in the repository and the
  residual risk is stated in `docs/CONTRACTS.md §11.5`.
- Pull requests #61 and #62 were not touched, and the merged pull request #60 was not modified or
  rewritten.
- `docs/SECURITY.md` was **not** given an accepted-residual-risk entry. That entry belongs to the
  owner's answer on `D-149`: it is only correct if the owner chooses to defer rather than to fix.
  The awaiting-confirmation table states this explicitly.

## Files Changed

- `functions/src/auth/onAnonymousUserDeleted.ts`, `functions/src/auth/anonymousUserEligibility.ts`,
  `functions/src/callable/deleteOrphanedAnonymousAccount.ts`,
  `functions/src/deletion/firebaseAdminDeletionGateways.ts`.
- `functions/test/anonymousCleanup.test.mjs`, `functions/test/orphanedAnonymousAccount.test.mjs`,
  `functions/test/orphanedAnonymousAccountEmulator.test.mjs`.
- `build-logic/convention/src/main/kotlin/.../contract/DecisionRegistry.kt` (new),
  `.../contract/ContractCheck.kt`,
  `build-logic/convention/src/test/kotlin/.../contract/DecisionRegistryTest.kt` (new).
- `docs/BACKLOG.md` (`E3-14`, `E3-15` and two index rows), `docs/CONTRACTS.md` §11.5,
  `docs/DECISION_BOARD.md`, `docs/SPECIFICATION.md` §12, `docs/TECHNICAL_PLAN.md` §2,
  `docs/adr/0149-...md` (new), `docs/adr/0150-...md` (new), `docs/adr/README.md`, `AGENTS.md`,
  `docs/PROJECT_LOG.md`, this handoff.

## Decisions Made

- `D-148` (ADR-0149, `Accepted`): the ticket issuer resolves the caller's current Auth record
  through the Admin SDK and issues only while it exists, is enabled and is still anonymous.
- `D-149` (ADR-0150, `Proposed`, **owner's**): the mechanism that closes the issuance/deletion
  interleaving. Recommended option A, a second purge pass after Auth deletion paired with an issuer
  post-write read-back; option B disables the Auth user first; option C adds an internal marker
  collection. Listed in the awaiting-confirmation table with `E3-15` as `Needed by`.
- The sanitized trigger rejection is a defect fix under `D-128` and `§11.5`, not a new decision.
- The `contractCheck` parser fix is a defect fix in the tooling, not a decision. It was forced:
  recording a `Proposed` decision in the prescribed form made assertions 2 and 4 mutually
  unsatisfiable, because the awaiting-confirmation rows start with a decision ID and their fifth
  column is `Consequence if unresolved`, not a status. The defect was latent only because the board
  had never carried an unresolved decision.
- **Commit hygiene, stated rather than hidden:** the two ADRs and the four decision mirrors landed
  in `6e7c311`, the red commit of the `contractCheck` fix, rather than in a separate documentation
  commit. The red/green separation of the three behaviours is otherwise exact, and each red commit
  contains only tests and, where needed, a behaviourless declaration.
- Rule 0 held for the whole story: every chat reply was in Spanish (es-ES) and every repository
  artifact is in technical English. No violation occurred.

## Verification Run

Red then green, per behaviour:

| Behaviour | RED | GREEN |
|-----------|-----|-------|
| Sanitized trigger rejection | `2c65533`, 2 failing of 68 | `9490d2b`, 68 passing |
| Admin eligibility at issuance | `73e0b90`, 5 failing of 73 | `d6365c1`, 73 passing |
| Decision registry parse | `6e7c311`, 1 failing of 70 | `0d08fca`, 70 passing |

Commands and results:

- Complete Functions unit suite — `cd functions && npm test`: 75 tests, 73 pass, 0 fail, 2 skipped
  (the emulator-gated tests, which skip without `FIRESTORE_EMULATOR_HOST` and run below).
- Firestore emulator integration suite — `npm run test:emulator`: 2 tests, 2 pass, 0 fail,
  `Script exited successfully (code 0)`.
- Firestore rules tests — `npm run test:firestore-rules`: 155 pass, 0 fail.
- Dependency audit — `cd functions && npm run audit`: exit `0`. It reports a **moderate** `uuid`
  advisory reached through `firebase-admin` → `gaxios`, which is below the configured
  `--audit-level=high` gate and whose only offered fix is a breaking downgrade to
  `firebase-admin@10.3.0`. Recorded here rather than silently passed over; it predates this story.
- `contractCheck` and build-logic fixture tests — `./gradlew contractCheck
  :build-logic:convention:test`: all assertions `PASS`, 146 aligned decisions and ADRs, assertion 4
  reports `1 listed`, no `PENDING`; 70 build-logic tests pass.
- Complete required Gradle verification — the full command of `AGENTS.md` with the four D-75 `-x`
  paths: exit `0`.
- Firebase Functions and Firestore indexes dry-run — `npx firebase deploy --only
  functions,firestore:indexes --project davidruiz-carapp-dev --dry-run --force`: `Dry run
  complete!`, exit `0`. `--force` is required in a non-interactive shell because
  `onAnonymousUserDeleted` declares `failurePolicy: true` (`D-138`); the flag suppresses the
  confirmation prompt and does not deploy under `--dry-run`.
- `git diff --check`: clean.
- The ten protected checks: recorded in the pull request once they complete.

### The one failure that occurred

The first run of the Gradle command failed on
`FuelEntryStateHolderTest.invalidLiveMoneyClearsDerivedValueAndWaitsUntilSaveToPublishError[iosSimulatorArm64]`
with `kotlinx.coroutines.test.UncompletedCoroutinesError`. That is the `E1-14` flake, on the class
and the failure mode that story documents, and on the Native target it names. This branch changes no
Kotlin source at all outside `build-logic`: `git diff --stat main...HEAD -- shared/ feature/
androidApp/ iosApp/ core/` is empty. Re-running the identical command passed.

## Contract Impact

- Updated `docs/CONTRACTS.md` §11.5 with three rules: the issuer's Admin eligibility requirement and
  its error mapping (`D-148`); the explicit statement that `D-148` does not close the
  issuance/deletion interleaving, naming `D-149` and the residual risk bounded by the 30-day TTL;
  and the trigger's obligation to reject with a newly constructed sanitized error while preserving
  the rejection that `failurePolicy: true` retries.

## Decision Board Impact

- Added `D-148` ([ADR-0149](adr/0149-verify-the-issuing-account-through-the-admin-sdk.md)),
  `Accepted`, and `D-149`
  ([ADR-0150](adr/0150-close-the-ticket-issuance-and-account-deletion-race.md)), `Proposed`, with
  identical rows in the four mirroring documents. `D-149` is listed in the awaiting-confirmation
  table with `E3-15` as its `Needed by` story.

## Shared-Write Modules Touched

- None. `:core:database` is not touched.

## Project Log Entry

- [x] Entry appended

## Risks or Follow-ups

- **Residual risk, unresolved by design:** the interleaving of ADR-0150 can still leave one
  UID-bound authorization record alive after a successful account deletion, removed afterwards only
  by the 30-day TTL. The record contains `anonymousUid`, so the `D-143` erasure guarantee is
  conditional until `D-149` is decided and `E3-15` ships. The window is narrow and requires a
  cleanup-ticket issuance concurrent with an account deletion of the same UID, which the client flow
  does not perform, but it is reachable by a caller holding a valid anonymous token.
- The issuer now depends on Admin availability: an Admin outage blocks issuance where it previously
  would not have. That is the intended trade of `D-148`, and it fails closed.
- `E1-14` remains open and makes a red `shared-tests` or `iosSimulatorArm64Test` ambiguous.
- `AGENTS.md` gained one line in the remaining-Phase-3 bullet. Pull request #61 edits the same
  bullet, so whichever merges second resolves a trivial conflict.

## Human Review Gate

Applies. The story is marked "Human review required" in `docs/BACKLOG.md`, and it touches the gated
authentication, remote backend, and logging and privacy topics, plus the gated paths
`docs/CONTRACTS.md`, `docs/SPECIFICATION.md`, `docs/DECISION_BOARD.md`, `docs/adr/**` and
`AGENTS.md`. `D-149` additionally requires an explicit owner decision before `E3-15` may start.
