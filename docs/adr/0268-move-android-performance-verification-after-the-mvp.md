# ADR-0268 / D-267 - Move Android Performance Verification After the MVP

## Status

Accepted

The owner decided on 2026-11-05, after O17 showed that an emulator cannot run the Android gates. Test
effort had already exceeded the plan, so the owner limited the remaining work to what is essential and
postponed the rest to a phase after the MVP. Amends `D-252` (ADR-0253) and `D-226` (ADR-0227) for when
their verification runs.

## Context

`docs/BACKLOG.md` §`E4-03` requires the cold-start and smoothness targets on the reference devices, and
`docs/versions-matrix.md` allows a recorded substitute device. iOS met them on the iPhone 13 mini (O18).
The Android devices on hand do not help:

- The OnePlus 3 (Android 9) produces no gate figure, as the O17 dry run showed. The runbook now requires
  Android 14 or later.
- The Pixel 11 Pro is the owner's personal phone, and the owner chose not to use it.

The owner then chose an emulator: an AVD with a Pixel 6a profile, Android 16 (API 36) arm64 with Google
Play, 6 GB of RAM, the host GPU and 60 Hz. Macrobenchmark refuses an emulator unless
`-e androidx.benchmark.suppressErrors EMULATOR` is passed, and its figures come from the host's CPU and
GPU, so they are not hardware figures. The results on 2026-11-05 were:

| Step | Result |
|------|--------|
| A3, signed-out cold start | ten values, median 250.26 ms |
| A4, seeding offline | `seed=ok vehicles=1 fuelEntries=1000` |
| A5, adoption online | fails: App Check answers `403 App attestation failed`, and the app stays on the welcome screen for 5 minutes |
| A6 and A7, after `Continue without an account` offline | fail with `The seeded vehicle never appeared` |

A local owner has no returning-user state, because `SessionPhase.LOCAL` exists only in the published state
of one process. After a restart the auth state is `SignedOut`, which routes to the welcome screen. The
`benchmark` build cannot use a debug App Check provider (`D-259`).

## Options Considered

| Option | Benefits | Costs / Risks |
|--------|----------|---------------|
| A. Close `E4-03` with A3 as an indicative figure and move the rest after the MVP | No harness change, no device session, and the MVP moves on | The MVP closes with no valid Android cold-start or smoothness figure |
| B. Add an emulator-only fling test that passes the welcome screen | Runs the A7 readout end to end for the first time | A TDD cycle for one more indicative figure; A6 stays impossible |
| C. Measure on the Pixel 11 Pro | Valid figures now | The owner declined to use a personal phone |
| D. Relax the App Check requirement of the development project, or give `benchmark` a debug provider | Could reach the adopted state on an emulator | Weakens the development backend or breaks `D-259`, for figures that stay indicative |

## Decision

The selected option is **A**.

- `E4-03` closes with the iOS figures of O18 and O19 and the Android A3 figure, labelled indicative. The
  A3 figure is never reported as a gate verdict.
- The post-MVP story `E5-05` measures A3, A6 and A7 on a physical Android device, with the unchanged
  procedure of `docs/runbooks/performance-measurement.md` §2. It MUST be done before any App Store or
  Google Play submission.
- The harness does not change. `androidx.benchmark.suppressErrors` is passed only on the emulator command
  line, and the build configuration never sets it.
- The post-MVP story `E5-07` investigates the welcome screen that a local owner sees at a cold start.
- The post-MVP story `E5-08` holds the owner-run acceptances of bundle `E4-B1`: the `E4-01` departure
  checklists, the `E4-05` provider acceptance and the `E4-02` screen-reader walkthrough of `D-226`.

## Consequences

### Positive

- `E4-03` and bundle `E4-B2` can reach review without another device session.
- The postponed work is recorded in stories with acceptance criteria, so none of it is lost.

### Negative

- The MVP closes with the Android targets of `docs/SPECIFICATION.md §11` unverified on hardware.
- The A6 and A7 procedures have never completed on any Android device, so `E5-05` is their first complete
  run and may need harness fixes.
- The `E4-B1` acceptances stay unverified on devices by the owner at MVP closure.

### Constraints Introduced

- An emulator figure MUST NOT be reported as meeting or failing a performance gate.
- `E5-05` MUST run without suppressing any Macrobenchmark error.

## Verification

- `docs/handoffs/E4-B2.md` O17 records the emulator steps and their results.
- `docs/handoffs/E4-03.md` §Acceptance Evidence labels the A3 figure indicative and the Android gates
  `not run`.
- `docs/BACKLOG.md` §`E5-05`, §`E5-07` and §`E5-08` carry the acceptance criteria.

## References

- `docs/DECISION_BOARD.md` (decision ID `D-267`); `D-226` (ADR-0227); `D-251` (ADR-0252); `D-252` (ADR-0253);
  `D-259` (ADR-0260); `D-268` (ADR-0269)
- `docs/runbooks/performance-measurement.md` §1 and §2; `docs/versions-matrix.md` "Performance measurement
  baselines"
