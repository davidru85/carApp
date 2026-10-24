# ADR-0237 / D-236 - Limit E4-04 to Engineering Release Readiness

## Status

Accepted

The owner selected option B on 2026-10-24.

## Context

The owner stated on 2026-10-15 that carApp is primarily a portfolio project: publication in the App
Store or Google Play is not the current objective, although it may happen later. The same reason
postponed the in-app privacy-policy link as `E4-06`.

`D-209` (ADR-0210) delivers `E4-04` (release preparation) and then `E4-03` (performance and
reliability) as bundle `E4-B2`. The acceptance criteria of `E4-04` mix two kinds of work:

- engineering work that makes a release build real and safe: a separate production Firebase project
  and its configuration (`D-14`, `D-54`), Crashlytics behind `CrashReporter` (`D-21`) with
  crash-reporting redaction, the release-logging proof, launcher icons and launch screens, release
  builds installable on both platforms, a working account deletion (the client never calls the
  `D-23` operation today, so every permanent-account deletion fails), and branch protection;
- store-only work: a published privacy policy and the store privacy labels, the Google Play Data
  safety form and the App Store nutrition labels, the iOS privacy manifest, Sign in with Apple token
  revocation on account deletion (expected by App Store Review Guideline 5.1.1(v) and Apple's
  account-deletion guidance), iPad validation (`TARGETED_DEVICE_FAMILY` is `1,2` while the app is
  portrait-only), the Play Console and App Store Connect records, Google Play app signing and
  distribution certificates.

The store-only work needs paid developer accounts, legal text and console records that serve only a
publication. `docs/SPECIFICATION.md §11` (Privacy) listed the privacy policy and the store privacy
labels among the MVP requirements.

## Options Considered

| Option | Benefits | Costs / Risks |
|--------|----------|---------------|
| A. Keep `E4-04` as written | The MVP can be submitted to both stores when Phase 4 closes. | The owner must create paid developer accounts and store records, publish a privacy policy and a deletion page and submit privacy forms that serve no current objective; two more decisions (Apple revocation, iPad) and the full owner-run checklist. |
| B. Engineering-only `E4-04`; the store-only work moves to the new not-Ready story `E4-09` under "Store-submission prerequisites" | Phase 4 delivers a real, measurable release candidate against a production project; no store account or legal text is needed now; the store work is recorded and blocks any submission. | Phase 4 closes without store readiness. Until `E4-09`: production App Check on Android cannot require Play-recognized versions, deleting an Apple-linked account does not revoke its Sign in with Apple token, and no privacy manifest or public privacy policy exists. |
| C. Remove `E4-04` from `E4-B2` and deliver `E4-03` alone | Least work now; `E4-03` measures a release-optimized `benchmark` build that uses the development project. | Phase 4 and the MVP stay open; the broken account deletion stays broken; the measured build lacks Crashlytics and the launch screens, so cold start must be measured again; the `D-209` reason for running `E4-04` first is lost. |

## Decision

The selected option is **B**.

- `E4-04` keeps: the production Firebase project and the release configuration of both hosts; the
  Crashlytics integration with its consent, redaction and non-fatal call sites; the release-logging
  proof; the release-configuration guards; the launcher icons and launch screens; the Debug-only
  gating of UI-test hooks; the Functions split that keeps `stopBilling` out of production; the `D-23`
  account-deletion invoker; the R8 and `targetSdk` decisions; release builds that install and run on
  both platforms against the production project; and the branch-protection check.
- `E4-09 - Store Submission Readiness` is added to `docs/BACKLOG.md` under "Store-submission
  prerequisites", beside `E4-06` and `E2-10`. It is not Ready, low priority and outside the `D-209`
  bundles. It holds the privacy policy and the account-deletion web page, the Data safety form and
  the nutrition labels, the iOS privacy manifest, Sign in with Apple token revocation, the iOS
  device-family validation, Google Play app signing, the store records and listing assets, the switch
  of production App Check to Play-recognized versions, and the re-check of the Google Play
  target-API requirement at submission.
- `docs/SPECIFICATION.md §11` (Privacy) requires the privacy policy and the store privacy labels
  before any App Store or Google Play submission instead of within the MVP phases.
- The `E4-04` acceptance criteria are amended accordingly. ADR-0210 records the amendment; the
  composition and order of `E4-B2` are unchanged.

## Consequences

### Positive

- Phase 4 ends with a working, measured release candidate against a dedicated production backend.
- No store account, payment, legal text or console record is needed for the portfolio objective.

### Negative

- The MVP is not ready to submit when Phase 4 closes.
- Until `E4-09` lands, a user who deletes an Apple-linked account keeps the app listed under
  Sign in with Apple in their Apple Account settings, production App Check on Android accepts
  versions that Google Play does not recognise, and no privacy manifest or public privacy policy
  exists. `docs/SECURITY.md` records it.

### Constraints Introduced

- `E4-09`, `E4-06` and `E2-10` MUST be done before any App Store or Google Play submission.
- No release build is distributed through a store before `E4-09` merges.
- Each choice inside `E4-09` gets its own `D-n` at that story's intake.

## Verification

- `docs/BACKLOG.md` lists `E4-09` under "Store-submission prerequisites" with the deferred
  criteria, and the `E4-04` criteria no longer contain them.
- `docs/handoffs/E4-B2.md` marks each deferred owner-run item "deferred to `E4-09`".

## References

- `docs/DECISION_BOARD.md` (decision ID `D-236`)
- `D-209` (ADR-0210), `D-14` (ADR-0015), `D-21` (ADR-0023), `D-23` (ADR-0024), `D-54` (ADR-0055),
  `D-67` (ADR-0068)
- `docs/SPECIFICATION.md §11`
- `docs/BACKLOG.md` (`E4-04`, `E4-06`, `E2-10`, `E4-09`)
- `docs/SECURITY.md` (Accepted Residual Risks)
