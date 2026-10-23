# ADR-0236 / D-235 - Make the No-Account Copy Valid in Every Sign-In Context

## Status

Accepted

The owner selected option A on 2026-10-23, in the PR #91 review round 3. It amends the
`AUTH.NO_ACCOUNT_AVAILABLE` row of the `D-219` table in `docs/CONTRACTS.md §14.1`.

## Context

`AUTH.NO_ACCOUNT_AVAILABLE` is published when the native picker finds no account on the device. Its
`§14.1` copy, "Add one in Settings, or continue without an account.", was written for onboarding.
`E4-05` makes it reachable from the Settings account-link row and `E4-01` from the re-authentication of
an account deletion. There an anonymous owner already has no account, a permanent owner cannot continue
without one, and "Settings" reads as the in-app Settings screen the owner is already on.

## Options Considered

| Option | Benefits | Costs / Risks |
|--------|----------|---------------|
| A. One device-neutral copy: add an account in the device settings, then try again | Correct in onboarding, account linking and re-authentication; one key per host. | Onboarding no longer names the guest path in this message; the welcome screen still shows its button. |
| B. Keep the copy | No change. | Misdirects two of the three contexts. |

## Decision

The selected option is **A**. `error_auth_no_account` reads "There is no Google account on this device.
Add one in the device settings, then try again." on Android and "There is no account available on this
device. Add one in the device settings, then try again." on iOS. The Spanish copy is the one in the
`docs/CONTRACTS.md §14.1` row.

## Consequences

### Positive

- The copy is correct wherever the native picker can report that no account exists.

### Negative

- None.

### Constraints Introduced

- A context-specific variant of this message needs a new decision.

## Verification

- `docs/CONTRACTS.md §14.1` and the four catalogues carry the same copy; assertion 41 keeps the key
  mapping unchanged.

## References

- `docs/DECISION_BOARD.md` (decision ID `D-235`)
- `D-219` (ADR-0220), `D-210` (ADR-0211)
- `docs/CONTRACTS.md §14.1`; `docs/handoffs/E4-02.md`
