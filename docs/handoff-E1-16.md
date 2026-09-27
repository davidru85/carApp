# Agent Handoff - E1-16

## Story

`E1-16 - Vehicle UI Fuel Type Selector - S` (`docs/BACKLOG.md`, `## Follow-Ups Outside the Phase
Milestones`).

## Ready Check

- Backlog story: `E1-16 - Vehicle UI Fuel Type Selector - S`, under `### Deferred scope, now
  scheduled`, with the eight acceptance criteria of `docs/BACKLOG.md` §`### E1-16`.
- Acceptance criteria reviewed: all eight. The selector on both platforms defaulting to `GASOLINE`;
  five localized values in English and Spanish; the selection reaching
  `VehicleFormStateHolder.setFuelType`; persistence on creation; load and update on edit; UI tests on
  both platforms for a non-default value on both paths; and no schema, migration or sync rule change.
- Dependencies checked: **none**. The `E1-16` backlog entry carries no `Depends on:` row. Its only
  named blocker was `E2-03` (post-save routing for first-run creation), which merged on 2026-09-06,
  so the story is unblocked.
- Decisions checked: `D-127` (`Accepted`, 2026-09-06) is the decision that authorises this story; it
  supersedes only the "no MVP UI selector" clause of `D-4` and constrains the selector to exactly the
  five MVP values with `GASOLINE` as the default and no schema, migration or Firestore rule change.
  `D-4` keeps status `Accepted` and everything else in it stands. The three open decisions (`D-149`,
  `D-150`, `D-173`) gate `E3-15`, `E3-16` and `E3-18` only and are irrelevant here.
- Normative sections reviewed: `docs/SPECIFICATION.md` §5.1, §7 F-2, §11 and §3.2;
  `docs/CONTRACTS.md` §20.4, §20.5, §20.8 and §20.10; `docs/TECHNICAL_PLAN.md` §4;
  `docs/adr/0128`; `docs/adr/0005`; `AGENTS.md` §Product Rules and §Architecture Rules.
- Expected verification: the complete non-instrumented command of `AGENTS.md` §Build and verify, plus
  `./gradlew contractCheck --rerun-tasks`, the Android instrumented suite on the `D-84` API 36
  emulator (`./gradlew :androidApp:connectedDebugAndroidTest`) and the iOS app built with
  `xcodebuild` and exercised on a booted iPhone simulator.
- Human review gates identified before work: the story is **not** a gated story. Its product surface
  (`feature/vehicle/**` presentation, `androidApp/**`, `iosApp/**`, localization resources and tests)
  touches **no gated path**. The blocker recorded below was escalated before any work started, and the
  owner's decision is recorded under "Decisions Made".

## Blocking Finding Raised At Intake

`D-127` is `Accepted` and authorises the selector, but the normative prose that forbade it was never
corrected when `D-127` was accepted. `docs/SPECIFICATION.md` §5.1 ("Stored in the MVP, not exposed as
a selector") and §7 F-2 ("`fuelType` is not exposed in the MVP UI") still forbid the control, and
`docs/CONTRACTS.md` §20.10 reinforces the prohibition with a `MUST NOT`. `D-127` is a behavioural
decision and `docs/SPECIFICATION.md` is the behavioural authority, so the story as written could not
be implemented without correcting those sentences in the same change, which `AGENTS.md` §Document
Authority requires ("every document repeating the same rule MUST be corrected in the same change").

Two executable pins also encoded the old prohibition and would have failed a correct implementation:
`PlatformHostContractTest.androidHostBindsThePersistentGraphToSharedStateHolders` asserted
`assertFalse(host.contains("setFuelType"))`, and `VehicleCreationTest` asserted
`VehicleTestTags.FUEL_TYPE_INPUT` does not exist. The `E1-07` acceptance criterion in
`docs/BACKLOG.md` repeated the same prohibition.

All four documents are gated paths or gated-adjacent, so this was escalated to the owner rather than
resolved by the agent. The owner authorised the realignment inside `E1-16` and the inversion of both
executable pins; both are recorded under "Decisions Made".

## In-Progress Checkpoint

- Date: 2026-09-27.
- Branch and base: `story/E1-16-vehicle-fuel-type-selector`, based on `origin/main` at `72640ffd`
  ("Merge pull request #79").
- Current phase and latest commit: intake; no commit yet on this branch. `main` was verified at
  `72640ffd` locally and on `origin/main` before branching.
- Push and pull-request status: branch created locally by `git worktree`; not pushed; no pull request.
- Completed since the previous checkpoint: worktree created; the seven acceptance-relevant files
  read; the blocking finding confirmed and escalated; the owner authorised the realignment and the pin
  inversion.
- Verification evidence and known failures: none yet. The `E1_07_API_36` AVD exists locally and no
  emulator or simulator was running at intake.
- Open decisions or blockers: none. The intake blocker is resolved by the owner's authorisation.
- Exact next step: write the RED tests and commit them as `test(E1-16): ...`.

## Scope Completed

- Intake only.

## Acceptance Evidence

- Pending.

## Out of Scope / Not Done

- Pending.

## Files Changed

- `docs/handoff-E1-16.md` (this file).

## Decisions Made

- **Blocking intake finding, resolved by the owner on 2026-09-27.** The normative prose forbidding the
  selector (`docs/SPECIFICATION.md` §5.1 and §7 F-2, `docs/CONTRACTS.md` §20.10) and the `E1-07`
  acceptance criterion in `docs/BACKLOG.md` are corrected in this story, with a `D-127` supersession
  note in the same style as `docs/adr/0005`. The two executable pins are inverted: the host contract
  test asserts the selector is wired, and the instrumented test asserts it persists instead of
  asserting its absence. This is a document realignment to an existing `Accepted` decision, not a new
  decision, so it introduces no `D-` id, no ADR and no mirrored rows. The owner's authorisation is
  recorded in the conversation of this session and is stated here because it is the reason a gated
  path is touched by a non-gated story.

## Verification Run

- Pending.

## Contract Impact

- Pending. `docs/CONTRACTS.md` §20.10 is edited to realign the `fuelType` paragraph with `D-127`.

## Decision Board Impact

- No decision changes. `D-127` is already `Accepted` and remains the decision that authorises the
  story.

## Shared-Write Modules Touched

- None. `:core:database` is not touched; no schema, migration or sync rule changes.

## Project Log Entry

- [ ] Entry appended

## Risks or Follow-ups

- Pending.

## Human Review Gate

Not applicable. The story is not a gated story, no gated path is touched by the product change, and
the document realignment touches `docs/SPECIFICATION.md`, `docs/CONTRACTS.md` and `docs/BACKLOG.md`
under the owner's explicit authorisation recorded above, which is also why the owner's review applies
to this pull request as usual.
