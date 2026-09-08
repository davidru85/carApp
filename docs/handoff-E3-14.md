# Agent Handoff

## Story

`E3-14 - Orphan Cleanup Ticket Issuance Hardening - M`

## Ready Check

- Backlog story: `E3-14 - Orphan Cleanup Ticket Issuance Hardening - M`, added to `docs/BACKLOG.md`
  in this story from the two post-merge findings of the `E3-11` review of pull request #60. The
  interleaving that `E3-14` deliberately does not close is `E3-15`, which is **not Ready** because
  it depends on the unresolved owner decision `D-149`, which is `Pending`.
- Acceptance criteria reviewed: the five criteria of `E3-14`. The issuance/deletion interleaving is
  explicitly out of this story's scope and is escalated instead of worked around.
- Dependencies checked: `E3-10` (PR #58) and `E3-11` (PR #60) are merged; the branch is based on
  the `main` head `112e973` after the rebase that followed #61 (`E2-07`) and #62 (`E2-08`).
- Decisions checked: `D-23`, `D-63`, `D-128`, `D-129`, `D-131`, `D-132`, `D-134`, `D-135`, `D-137`,
  `D-138`, `D-141`, `D-142` and `D-143` are `Accepted` and govern this code. No decision this story
  depends on is `Proposed` or `Pending` at intake; the story itself introduces `D-148` (`Accepted`)
  and `D-149` (`Pending`, owner decision, needed by `E3-15`).
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
- Branch and base: `story/E3-14-orphan-ticket-issuance-hardening`, based on `main` at `112e973`.
- Current phase and latest commit: two rounds of gated-review remediation are complete; the pull
  request is returned for the owner's next gated review and MUST NOT be merged.

  **Second gated-review intake (2026-09-08).** The owner's review of pull request #63 returned three
  normative findings against the documentation, none against the E3-14 production implementation.
  The validated Admin eligibility implementation, the fail-closed `disabled === false` predicate and
  the concrete Firebase Admin gateway tests are unchanged; no production source file is touched in
  this round. The three findings and their corrections:
  1. **`D-149` was labelled `Proposed` while carrying no recommendation.** `docs/DECISION_BOARD.md`
     defines `Proposed` as "a recommendation is on the table" and `Pending` as "no recommendation
     yet"; ADR-0150 explicitly recommends no option, selects no default and leaves the trade to the
     owner, so `Proposed` was the wrong label. `D-149` is now `Pending` in ADR-0150, the decision
     registry row and the awaiting-owner-confirmation table of `docs/DECISION_BOARD.md`,
     `docs/adr/README.md`, `docs/SPECIFICATION.md` §12, `docs/TECHNICAL_PLAN.md` §2,
     `docs/BACKLOG.md`, `docs/CONTRACTS.md` §11.5, this handoff and the pull-request description.
     **No recommendation was introduced to preserve the `Proposed` label.** `E3-15` stays Not Ready
     and blocked on the owner decision, and `contractCheck` assertion 4 — which treats `Proposed`
     and `Pending` alike — still recognises `D-149` as the one unresolved decision and requires its
     awaiting-confirmation row.
  2. **The Firestore TTL semantics were overstated.** Firestore TTL is not a hard 30-day deletion
     bound: at `expiresAt` a document becomes eligible for asynchronous deletion, expired documents
     may remain queryable, and the typical deletion within 24 hours of expiration that Firebase
     documents is neither a guaranteed maximum nor an SLA. Every claim that the residual risk is
     "bounded by the 30-day TTL", that a record is removed "only by its 30-day TTL" at a guaranteed
     time, that the existing TTL proves the maximum time a record survives, or that options A or B
     obtain a bounded residual window from that TTL, is corrected to the precise statement:
     provider-managed eventual cleanup after a 30-day expiration horizon, with an asynchronous and
     non-hard-bounded deletion delay. Where a provable maximum retention period is required,
     ADR-0150 and `docs/BACKLOG.md` now say explicitly that `E3-15` needs an **additional
     deterministic cleanup mechanism selected by the owner**, and that mechanism is deliberately not
     designed or implemented here. The correction is applied in ADR-0144, ADR-0142, ADR-0150,
     `docs/CONTRACTS.md` §11.5 and §16, `docs/TECHNICAL_PLAN.md` §2, `docs/BACKLOG.md`,
     `docs/DECISION_BOARD.md`, this handoff and the pull-request description; `docs/PROJECT_LOG.md`
     keeps its append-only history and receives a dated correction entry instead of a rewrite.
  3. **Option C was presented as a clean P2 solution.** ADR-0150 said its `deletedUids` marker must
     never expire and therefore accumulates, treating indefinite accumulation as a cost. A marker
     that never expires necessarily retains a stable UID, or a UID-correlatable key, for every
     deleted account forever, which conflicts with `D-143`/ADR-0144, whose accepted rationale is
     that retaining an account identifier after deletion violates the project's account-erasure
     expectation. ADR-0150 now separates the two facts explicitly — its Firestore transaction may
     supply the required serialization point; its proposed never-expiring marker is a distinct,
     unresolved indefinite UID-linked retention problem — and enumerates, without selecting
     anything, what a valid serialization design would have to prove: a justified safety horizon
     covering every already-issued credential or token and every in-flight callable execution plus
     clock skew and retry behaviour for any finite marker lifetime, or an alternative
     privacy-preserving serialization representation with its own proof, in either case reconciled
     with `D-143`. The same round corrects the option B wording: option B delivers a probability
     reduction only — it refuses issuances whose eligibility read follows the disable — and removes
     no already-written record and adds no cleanup, so any convergence for a record it misses comes
     solely from the pre-existing asynchronous TTL fallback and not from the option.

  The three findings of the first review round remain remediated:
  1. **ADR-0150 reworked** (`3da621c`): no option is recommended as satisfying the erasure
     invariant. The owner's counterexample is modelled explicitly (issuer eligibility read passes;
     deletion completes the Auth delete and its second purge; the issuer writes after that purge;
     deletion returns success; the issuer crashes before any post-write revalidation or
     compensating delete; the record outlives success and is left to eventual TTL cleanup), the
     ADR distinguishes eventual convergence (P1) from synchronous crash-safe erasure (P2), option C
     is named the only candidate with a real serialization point without being selected, and every
     accepted option must discharge the crash-safe proof obligations. `E3-15` stays not Ready.
  2. **D-148 eligibility fails closed** (`4208d86` RED, `fb56b11` GREEN): a snapshot without a
     known `disabled: false` value rejects with `failed-precondition` and creates no authorization;
     the predicate requires `disabled === false`. The shared `D-134` predicate and all existing
     linked / disabled / deleted / logging / error-mapping behaviour are preserved, and
     `FirebaseAdminAuthDeletionGateway.getUser` keeps returning the real `UserRecord.disabled`
     value, with new direct unit coverage (`8ce1bdf`).
  3. **ADR-0149 verification record corrected** (`3da621c`): the emulator suite stubs Auth and
     starts only the Firestore emulator, so it does not exercise the real Admin Auth gateway. The
     record now distinguishes handler tests (fakes), concrete-gateway unit coverage (stub `Auth`
     client) and Firestore emulator coverage (real Firestore gateways, stubbed Auth).
- Push and pull-request status: all commits pushed; the head is `31ce7e7` (the follow-up
  documentation commit `31ce7e7` triggered a further check run; see below). Pull request #63 targets
  `main`, stays OPEN and MUST NOT be merged. **The ten protected checks pass on the post-fix
  head**: `android-assemble`, `android-instrumented-tests`, `architecture-check`,
  `contract-check`, `detekt`, `ios-simulator-build`, `ktlint`, `objc-header-golden-check`,
  `provider-decoupling` and `shared-tests`. Run `34227983544` on `6743d2f` passed all ten with no
  re-run; the follow-up commit's run `34230198804` hit the documented `E1-14` flake once in
  `provider-decoupling` (a `:shared` Kotlin/Native test this branch does not touch — the branch
  changes no Kotlin source outside `build-logic`), the failed job alone was re-run, and it and the
  run then completed `success` with all ten checks green on the pull request.
- Verification evidence and known failures: the full battery is green from the post-fix head
  (`6743d2f`): 78 Functions unit tests with 76 pass and 2 emulator-gated skips, the emulator suite
  (2 pass), 155 rules tests, the audit exit 0 with the pre-existing moderate `uuid` advisory,
  `contractCheck` with 150 aligned decisions and `D-149` the one unresolved, the complete required
  Gradle command exit 0 (no `E1-14` flake this time), the dry run exit 0, `git diff --check` clean,
  and the ten protected checks green on run `34227983544` with no re-run.
- Open decisions or blockers: `D-149` is `Pending` and is the owner's. The second review round
  corrected the status: the repository defines `Proposed` as "a recommendation is on the table" and
  `Pending` as "no recommendation yet", and ADR-0150 recommends no option, selects no default and
  leaves the trade entirely to the owner. The remediation explicitly does not decide it and does
  not pre-select an option.
- Exact next step: the owner's second gated review of pull request #63, and a decision on `D-149`.

## Scope Completed

- Finding 2: `createAnonymousDeletionHandler` rejects with a newly constructed sanitized error
  instead of rethrowing the provider exception.
- Finding 1, the forced half: `issueOrphanCleanupTicket` resolves the caller's current Auth record
  through the Admin SDK before any write, and issues only while that record exists, is known to be
  enabled and is still anonymous under the shared `D-134` predicate. The eligibility predicate fails
  closed when the enabled state is unavailable.
- A `contractCheck` defect that blocked recording the escalation: the awaiting-confirmation summary
  was being parsed as decision registry rows.
- `D-148` with ADR-0149, `D-149` with ADR-0150 as the owner's options, the four decision mirrors,
  and the `docs/CONTRACTS.md §11.5` rules for both findings.
- The first gated-review remediation: the ADR-0150 soundness correction with all mirrors, the
  fail-closed eligibility correction, the gateway unit coverage, the ADR-0149 verification-record
  correction, and the append-only `docs/PROJECT_LOG.md` correction entry.
- The second gated-review remediation, documentation only: the `D-149` status correction to
  `Pending` across every mirror, the Firestore TTL semantics correction everywhere the rule is
  repeated, the option C retention analysis and the option B P1 wording in ADR-0150, and a second
  append-only `docs/PROJECT_LOG.md` correction entry. No production source file changed.

## Acceptance Evidence

1. *The issuer resolves the caller's Auth record before writing, and rejects linked, disabled,
   deleted and state-unknown accounts.* `functions/test/orphanedAnonymousAccount.test.mjs`:
   `ticket issuance rejects stale anonymous claims once the account has been linked`,
   `... for a disabled account`, `... for a deleted account` and `ticket issuance rejects an
   anonymous snapshot whose disabled state is unknown`. Each asserts `harness.calls` equals
   `[["getUser", ORPHAN_UID]]`, so the lookup happened **and** no authorization was created. The
   happy-path test asserts the lookup precedes the write.
2. *A rejected issuance leaks nothing.* `a rejected issuance leaks no UID, token, payload or raw
   failure` serializes the logs and the rejection and asserts none of them contains the UID, the
   token or the raw provider text.
3. *An Admin lookup failure is distinguishable from an eligibility rejection.*
   `ticket issuance maps an Admin lookup failure to internal with a redacted stage` pins the
   `internal` code and the single redacted `AUTH_USER` log line.
4. *The concrete gateway forwards the real record and classifies only the missing-user error.*
   `functions/test/firebaseAdminDeletionGateways.test.mjs`:
   `the Admin Auth gateway forwards disabled and providerData of the resolved record` and
   `the Admin Auth gateway maps only auth/user-not-found to null`, which rethrows any other
   provider failure unchanged.
5. *The trigger rejects with a sanitized error and still retries.*
   `functions/test/anonymousCleanup.test.mjs`:
   `the native trigger rejects with an error carrying none of the raw failure` fails the deletion
   with a message embedding a UID-bearing Firestore path and asserts that the message, the stack,
   the enumerable properties and the cause carry none of it, while requiring a rejection so
   `failurePolicy: true` still retries; `the native trigger rejection is a newly constructed error,
   not the provider one` pins that the rejected value is not the thrown object. The pre-existing
   `the native trigger retries a failed deletion on redelivery` still passes, which is the retry
   behaviour itself.
6. *Existing `E3-10` and `E3-11` behaviour is preserved.* The complete Functions suite is green:
   76 passing, 0 failing, with the two emulator-gated tests running separately and passing.

## Out of Scope / Not Done

- **The issuance/deletion interleaving is not closed.** It is `E3-15`, blocked on the `Pending`
  `D-149`. ADR-0150 carries the corrected interleaving analysis, the convergence-versus-erasure
  distinction and the owner's options with their proof obligations. This is an escalation, not a
  workaround: the analysis is in the repository and the residual risk is stated in
  `docs/CONTRACTS.md §11.5`.
- **`D-149` is not decided and no option is pre-selected.** The reworked ADR-0150 names option C as
  the only candidate with a real serialization point, but the choice between a bounded residual
  window (options A and B) and that serialization point (option C) is the owner's.
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
  `functions/test/orphanedAnonymousAccountEmulator.test.mjs`,
  `functions/test/firebaseAdminDeletionGateways.test.mjs` (new).
- `build-logic/convention/src/main/kotlin/.../contract/DecisionRegistry.kt` (new),
  `.../contract/ContractCheck.kt`,
  `build-logic/convention/src/test/kotlin/.../contract/DecisionRegistryTest.kt` (new).
- `docs/BACKLOG.md` (`E3-14`, `E3-15` and two index rows), `docs/CONTRACTS.md` §11.5 and §16,
  `docs/DECISION_BOARD.md`, `docs/SPECIFICATION.md` §12, `docs/TECHNICAL_PLAN.md` §2,
  `docs/adr/0149-...md` (new), `docs/adr/0150-...md` (new), `docs/adr/README.md`, `AGENTS.md`,
  `docs/PROJECT_LOG.md`, this handoff. The second review round additionally corrects the TTL
  semantics in `docs/adr/0142-use-server-issued-orphan-cleanup-tickets.md` and
  `docs/adr/0144-erase-orphan-cleanup-authorizations-on-account-deletion.md`; both keep their
  `Accepted` status and their decisions are unchanged, only the overstated TTL wording is fixed.

## Decisions Made

- `D-148` (ADR-0149, `Accepted`): the ticket issuer resolves the caller's current Auth record
  through the Admin SDK and issues only while it exists, is known to be enabled — an explicit
  `disabled === false` state, failing closed when the state is unavailable — and is still
  anonymous. The review-round correction that made the enabled state explicit is a sharpening of
  the same decision, not a new one: the contract already permitted issuance only for a known
  enabled record, and the `disabled !== true` implementation was the defect.
- `D-149` (ADR-0150, `Pending`, **owner's**): the mechanism that closes the issuance/deletion
  interleaving. **No option is pre-selected and no default is presented.** Option A (a second purge
  pass) and option B (disabling the Auth user first) converge only eventually — the ADR-0150
  counterexample shows a record outliving success after a crash, after which only the
  provider-managed asynchronous TTL cleanup removes it, with no proven maximum — while option C (an
  internal `deletedUids` marker collection read inside the issuance transaction) is the only
  candidate whose transaction supplies a real serialization point. Option C is **not** a clean
  solution: its proposed never-expiring marker retains a UID-correlatable key for every deleted
  account indefinitely, which conflicts with `D-143`, so its serialization half and its retention
  half are stated separately and the retention half is unresolved. The accepted option must
  discharge the ADR's crash-safe proof obligations. Listed in the awaiting-confirmation table with
  `E3-15` as `Needed by`.
- The sanitized trigger rejection is a defect fix under `D-128` and `§11.5`, not a new decision.
- The `contractCheck` parser fix is a defect fix in the tooling, not a decision. It was forced:
  recording an unresolved decision — `Proposed` or `Pending` — in the prescribed form made
  assertions 2 and 4 mutually
  unsatisfiable, because the awaiting-confirmation rows start with a decision ID and their fifth
  column is `Consequence if unresolved`, not a status. The defect was latent only because the board
  had never carried an unresolved decision.
- The ADR-0150 rework is a correction of this story's own record, not a decision change: the
  unsound option-A recommendation is withdrawn rather than replaced by another selection, and
  `D-149` is still undecided and still the owner's. Its **status label** changed in the second
  review round, from `Proposed` to `Pending`, because withdrawing the recommendation left the
  decision with none, and `docs/DECISION_BOARD.md` defines `Proposed` as "a recommendation is on
  the table" and `Pending` as "no recommendation yet". This is a labelling correction that makes
  the board match ADR-0150, not a new decision and not a change of the decision's content.
- **Commit hygiene, stated rather than hidden:** the two ADRs and the four decision mirrors landed
  in `6e7c311`, the red commit of the `contractCheck` fix, rather than in a separate documentation
  commit. The red/green separation of the review-round behaviours is exact: the gateway coverage pin
  (`8ce1bdf`, green by design — it pins existing behaviour the fail-closed fix must preserve), the
  fail-closed RED (`4208d86`) and GREEN (`fb56b11`).
- Rule 0 held for the whole story, including the review round: every chat reply was in Spanish
  (es-ES) and every repository artifact is in technical English. No violation occurred.

## Verification Run

Red then green, per behaviour:

| Behaviour | RED | GREEN |
|-----------|-----|-------|
| Sanitized trigger rejection | `2c65533`, 2 failing of 68 | `9490d2b`, 68 passing |
| Admin eligibility at issuance | `73e0b90`, 5 failing of 73 | `d6365c1`, 73 passing |
| Decision registry parse | `6e7c311`, 1 failing of 70 | `0d08fca`, 70 passing |
| Fail-closed eligibility on unknown state (review round) | `4208d86`, 1 failing of 78 | `fb56b11`, 78 passing |

Post-fix battery, run from the head `6743d2f`:

- Complete Functions unit suite — `cd functions && npm test`: 78 tests, 76 pass, 0 fail, 2 skipped
  (the emulator-gated tests, which run below).
- Firestore emulator integration suite — `npm run test:emulator`: 2 tests, 2 pass, 0 fail,
  `Script exited successfully (code 0)`.
- Firestore rules tests — `npm run test:firestore-rules`: 155 pass, 0 fail.
- Dependency audit — `cd functions && npm run audit`: exit `0`. It reports a **moderate** `uuid`
  advisory (GHSA-w5hq-g745-h8pq, missing buffer bounds check in v3/v5/v6) reached through
  `firebase-admin` → `gaxios` → `teeny-request` → `uuid`, below the configured `--audit-level=high`
  gate, whose only offered fix is a breaking downgrade to `firebase-admin@10.3.0`. Recorded here
  rather than silently passed over; it predates this story and is tracked by `TD-01`.
- `contractCheck` and build-logic fixture tests — `./gradlew contractCheck
  :build-logic:convention:test`: all assertions `PASS`, **150 aligned decisions** and ADRs
  (the rebased branch carries `D-144` through `D-147` from `E2-07` and `E2-08` in addition to this
  story's `D-148` and `D-149`), assertion 4 reports `1 listed` (`D-149`), no `PENDING`;
  the build-logic suite passes.
- Complete required Gradle verification — the full command of `AGENTS.md` with the four D-75 `-x`
  paths: exit `0`, `BUILD SUCCESSFUL`. **No `E1-14` flake occurred in this run**; the command
  passed first time.
- Firebase Functions and Firestore indexes dry-run — `npx firebase deploy --only
  functions,firestore:indexes --project davidruiz-carapp-dev --dry-run --force`:
  `Dry run complete!`, exit `0`. `--force` is required in a non-interactive shell because
  `onAnonymousUserDeleted` declares `failurePolicy: true` (`D-138`); the flag suppresses the
  confirmation prompt and does not deploy under `--dry-run`.
- `git diff --check`: clean.
- The ten protected checks on pull request #63 from the post-fix head `6743d2f`, run `34227983544`:
  all ten pass with no re-run. `android-assemble`, `android-instrumented-tests`,
  `architecture-check`, `contract-check`, `detekt`, `ios-simulator-build`, `ktlint`,
  `objc-header-golden-check`, `provider-decoupling` and `shared-tests`.

### The one flake of the post-fix check run, and its re-run

The follow-up documentation commit `31ce7e7` triggered run `34230198804`. Its
`provider-decoupling` job failed once on
`LocalOwnerAdoptionTriggerTest.aFuelEntryWriteTriggersAcquisitionAndAdoptionAfterAnEarlierAttemptFailed[iosSimulatorArm64]`
with `kotlinx.coroutines.test.UncompletedCoroutinesError`, in the run that also logs
`The number of threads 4 is more than the number of processors 3` — the `E1-14` flake class, on
the failure mode and target that story documents, in a test this branch does not introduce or
touch: `git diff --stat origin/main...HEAD -- shared/ feature/ androidApp/ iosApp/ core/` is empty,
so the branch changes no Kotlin source at all outside `build-logic`. The failed job alone was
re-run; it and the whole run then completed `success`, and all ten checks are green on the pull
request. `E1-14` remains open and makes a single red Native test ambiguous; the full analysis is
in `docs/BACKLOG.md` (`E1-14`).

### The one failure that occurred (pre-review run, superseded)

The first pre-review run of the Gradle command failed on
`FuelEntryStateHolderTest.invalidLiveMoneyClearsDerivedValueAndWaitsUntilSaveToPublishError[iosSimulatorArm64]`
with `kotlinx.coroutines.test.UncompletedCoroutinesError`. That is the `E1-14` flake, on the class
and the failure mode that story documents, and on the Native target it names. Re-running the
identical command passed. The post-fix run of the same command passed first time; `E1-14` remains
open and documented in `docs/BACKLOG.md`.

## Contract Impact

- Updated `docs/CONTRACTS.md` §11.5 with four rules: the issuer's Admin eligibility requirement and
  its error mapping (`D-148`), including the explicit known-enabled state; the explicit statement
  that `D-148` does not close the issuance/deletion interleaving, naming `D-149`, the residual risk
  whose only cleanup today is provider-managed eventual TTL cleanup after a 30-day expiration
  horizon with a non-hard-bounded deletion delay, and the convergence-versus-erasure distinction
  with the proof obligation; and the trigger's obligation to reject with a newly constructed sanitized error while
  preserving the rejection that `failurePolicy: true` retries.

## Decision Board Impact

- Added `D-148` ([ADR-0149](adr/0149-verify-the-issuing-account-through-the-admin-sdk.md)),
  `Accepted`, and `D-149`
  ([ADR-0150](adr/0150-close-the-ticket-issuance-and-account-deletion-race.md)), now `Pending`
  (recorded as `Proposed` in the original delivery), with
  identical rows in the four mirroring documents. `D-149` is listed in the awaiting-confirmation
  table with `E3-15` as its `Needed by` story. The review round corrected the `D-148` rows to state
  the known-enabled requirement and reworked the `D-149` rows, the awaiting-confirmation row and
  ADR-0150 so that no option claims the erasure invariant without the crash-safe serialization
  proof. The second review round moved `D-149` from `Proposed` to `Pending` in every mirror,
  because ADR-0150 offers no recommendation.

## Shared-Write Modules Touched

- None. `:core:database` is not touched.

## Project Log Entry

- [x] Entry appended — one story entry, one decision entry and one correction entry from the
  original delivery, plus one correction entry per gated-review round (two). `docs/PROJECT_LOG.md`
  stays append-only: no historical entry was edited or deleted.

## Risks or Follow-ups

- **Residual risk, unresolved by design:** the interleaving of ADR-0150 can still leave one
  UID-bound authorization record alive after a successful account deletion. **The retention of that
  record is not bounded by the 30-day TTL and MUST NOT be stated as if it were.** The only cleanup
  that exists today is the Firestore TTL on `expiresAt`: at `expiresAt` the document becomes
  eligible for asynchronous deletion, expired documents may remain queryable, and Firebase documents
  a typical deletion within 24 hours of expiration, which is neither a guaranteed maximum nor an
  SLA. The correct statement is provider-managed eventual cleanup after a 30-day expiration horizon,
  with an asynchronous and non-hard-bounded deletion delay. A provable maximum retention period
  would require an additional deterministic cleanup mechanism, which `E3-15` can only add once the
  owner selects it; it is deliberately not designed in this pull request. The record contains
  `anonymousUid`, so the `D-143` erasure guarantee is conditional until `D-149` is decided and
  `E3-15` ships. The window is narrow and requires a cleanup-ticket issuance concurrent with an
  account deletion of the same UID, which the client flow does not perform, but a caller holding a
  valid anonymous token can reach it.
- **The `D-149` options are presented without a recommendation**, which is why the decision is
  `Pending` rather than `Proposed`. Option A removes only the authorizations that had already
  landed when its second pass ran; option B narrows the window at the eligibility read but removes
  nothing and adds no cleanup of its own, so any convergence for a record either one misses comes
  solely from the pre-existing asynchronous TTL fallback, not from the option. Option C is the only
  candidate whose transaction supplies a real serialization point, but it is **not** a clean
  solution: its proposed never-expiring `deletedUids` marker retains a stable, UID-correlatable key
  for every deleted account indefinitely, which conflicts with `D-143`, whose accepted rationale is
  that retaining an account identifier after deletion violates the project's erasure expectation.
  ADR-0150 separates option C's sound serialization half from its unresolved retention half and
  enumerates what a valid design would have to prove — a justified safety horizon covering every
  already-issued credential or token, every in-flight callable execution, clock skew and retry
  behaviour, or else a privacy-preserving serialization representation with its own proof — without
  selecting either. Whichever option is accepted must discharge the ADR-0150 proof obligations.
- The issuer now depends on Admin availability: an Admin outage blocks issuance where it previously
  would not have. That is the intended trade of `D-148`, and it fails closed.
- `E1-14` remains open and makes a red `shared-tests`, `provider-decoupling` or
  `iosSimulatorArm64Test` ambiguous: the post-fix check run `34230198804` failed once in
  `provider-decoupling` on that flake, in a test this branch does not touch, and passed on the
  re-run of the failed job. The post-fix Gradle command passed first time.

## Human Review Gate

Applies. The story is marked "Human review required" in `docs/BACKLOG.md`, and it touches the gated
authentication, remote backend, and logging and privacy topics, plus the gated paths
`docs/CONTRACTS.md`, `docs/SPECIFICATION.md`, `docs/DECISION_BOARD.md`, `docs/adr/**` and
`AGENTS.md`. `D-149` additionally requires an explicit owner decision before `E3-15` may start.