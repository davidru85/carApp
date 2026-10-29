# ADR-0259 / D-258 - Move the Production Firebase Cutover to Story E4-10

## Status

Accepted

The owner selected option B on 2026-10-27.

## Context

`E4-04` originally required a production Firebase project, installable release builds against it, and
every owner-run step that creates it. The owner keeps all work against `davidruiz-carapp-dev` until the
last steps before the MVP closes, because a production project needs billing, identity, App Check and
key restrictions that serve no purpose while the app is unpublished. Leaving those criteria in `E4-04`
would keep the story open indefinitely.

## Options Considered

| Option | Benefits | Costs / Risks |
|--------|----------|---------------|
| A. Keep the production criteria in `E4-04` and leave it open until the owner creates the project | One story owns the whole release preparation. | `E4-04` cannot close, `E4-03` cannot start after it, and Phase 4 stalls on an owner action with no date. |
| B. Move every production-dependent item to a new standalone story `E4-10`, and close `E4-04` with the fail-closed mechanism plus a `staging` variant (`D-259`) | `E4-04` and `E4-03` complete against the development project; the production work stays explicit, owned and blocked on the owner. | The MVP closes with `E4-10`, not with `E4-B2`; two stories must agree about which configuration is which. |

## Decision

The selected option is **B**.

**`E4-10 - Production Firebase Cutover`** is added to `docs/BACKLOG.md` as the last story before MVP
closure: a standalone pull request after `E4-B2` merges, not bundled, so `D-209` is unaffected. It is
not Ready until the owner schedules it.

**It takes from `E4-04`:**

- the acceptance criterion "a separate production Firebase project exists, its project identifier has
  been decided by the owner, and no public release build points to the development Firebase project";
- the acceptance criterion "release builds are installable and run on both platforms against the
  production Firebase project";
- owner-run items O2 to O7 and O14, the interim keystore of O4/O8, and the production App Check of
  `D-249`;
- the production runbook, the owner-value recording and the "add the release configuration" half of
  `D-238`;
- the release-artifact evidence against production;
- the recording of `D-237`, after project creation proves the ID available (`D-32`).

**`E4-04` keeps** its remaining criteria and gains two: "release builds fail closed with the `D-238`
mechanism in place" and "the `staging` variant builds and runs against the development project on both
platforms". Its account-deletion criterion is verified end to end against the development project.

**Amended in the same change:** the scope note of ADR-0237; ADR-0210's closure sentence, because the MVP
closes with `E4-10`; `AGENTS.md` §Repository State, whose "No production Firebase project exists. E4-04
owns production identifiers…" becomes `E4-10`; `docs/identifiers.md` wherever it names `E4-04`;
`docs/BACKLOG.md`; and the owner-run checklist of `docs/handoffs/E4-B2.md`.

## Consequences

### Positive

- Phase 4 completes against the project the app already uses, with no unpaid owner dependency.
- The production work is one story with one checklist, instead of a permanent half-open `E4-04`.

### Negative

- No release artifact can be produced before `E4-10`; the locally testable build is `staging`.
- Two stories touch Firebase configuration, so `D-238` splits explicitly: `E4-04` owns the mechanism,
  `E4-10` owns the files.

### Constraints Introduced

- No production project, credential or deploy may enter `E4-04` (`D-258`, `D-260`).
- `E4-10` MUST NOT close without a sideloaded release build reaching the production project on both
  platforms, and MUST record the `D-32` availability proof of `D-237`.
- `E4-10` carries the eleven corrections of the first owner-session attempt, including creating the
  project with the repository-pinned Firebase CLI, linking billing before Identity Platform, and
  enabling both `google.com` and `apple.com` providers.

## Verification

- `docs/BACKLOG.md` lists `E4-10` with its criteria and its non-bundled standing.
- The four mirror tables carry `D-258`.
- `E4-04`'s backlog entry no longer names a production project, and `AGENTS.md` names `E4-10`.

## References

- `docs/DECISION_BOARD.md` (decision ID `D-258`); `D-236` (ADR-0237), `D-237` (reserved; its ADR-0238 is written by `E4-10`), `D-238`
  (ADR-0239), `D-249` (ADR-0250), `D-259` (ADR-0260), `D-209` (ADR-0210), `D-54` (ADR-0055)
- `docs/identifiers.md`, `docs/BACKLOG.md`, `AGENTS.md`
