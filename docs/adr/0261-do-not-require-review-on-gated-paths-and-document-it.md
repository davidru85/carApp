# ADR-0261 / D-260 - Do Not Require Review on Gated Paths, and Document It

## Status

Accepted

The owner selected option A on 2026-10-27.

## Context

`AGENTS.md` §Human Review Gates claims gated paths are "Enforced by `CODEOWNERS` plus required review,
not by prose". That was false. Branch protection on `main` had fifteen required checks, **zero** required
approving reviews, code-owner review **off** and administrator enforcement **off**. `davidru85` is the
only collaborator and the only merger, and every pull request is authored by that account because agents
act with the owner's GitHub session, so GitHub forbids self-approval: enabling required review would
force an administrator bypass on every merge, which is weaker than the current state because it trains
everyone to bypass.

Separately, secret scanning and push protection had been disabled since the one earlier attempt in
August, while `docs/SECURITY.md` states that secret scanning MUST be enabled.

## Options Considered

| Option | Benefits | Costs / Risks |
|--------|----------|---------------|
| A. Keep required review off, correct the documentation to describe what is enforced, and record the resulting residual risk | Honest documentation; no bypass ritual; secret scanning can still be enabled immediately. | Nothing technical stops an agent merge: only the process rule "agents never merge" does. |
| B. Enable required review with code-owner enforcement | A mechanical gate on the gated paths. | Every merge needs an administrator bypass because the author is the sole reviewer; the gate becomes theatre. |
| C. Create a machine account to author agent pull requests, so the owner can approve them | Real second-party review. | New credentials and a new identity for every agent run; declined by the owner for now. |

## Decision

The selected option is **A**.

- Required approving review stays **off**, code-owner review stays **off**, and administrator enforcement
  stays **off**. `CODEOWNERS` still requests the owner's review automatically, which is advisory.
- `AGENTS.md` §Human Review Gates is rewritten to say exactly that: `davidru85` is the only collaborator
  and the only merger; `CODEOWNERS` requests the owner's review; required review is deliberately off
  because self-approval is impossible; the gate is therefore the owner's attention, not a rule the
  platform enforces.
- `docs/SECURITY.md` records the residual risk: agents act with the owner's GitHub credentials, so only
  the process rule that agents never merge stands between an agent and `main`.
- **Secret scanning and push protection are enabled** (2026-10-27). The history scan raised exactly two
  `google_api_key` alerts, both from commit `0ddef323`, for
  `androidApp/src/debug/google-services.json` and
  `iosApp/Config/Debug/GoogleService-Info-Debug.plist`. Both were resolved `wont_fix` because Firebase
  client configuration is not a secret under `docs/SECURITY.md` — it is restricted by application and by
  API target, verified on 2026-10-27. Per GitHub's pattern table `google_api_key` has user alerts but no
  push protection, so committing restricted Firebase client configuration is unaffected.
- Dependabot security updates, non-provider patterns and validity checks stay disabled.
- The machine-account alternative was **declined for now**; no story is created for it.

## Consequences

### Positive

- The documentation matches the platform, so the next agent is not misled about what will stop it.
- Secret scanning is on, satisfying the standing requirement in `docs/SECURITY.md`.

### Negative

- An agent that ignores the "never merge" rule is not stopped by branch protection.
- Two acknowledged development keys stay in history as `wont_fix` alerts.

### Constraints Introduced

- `AGENTS.md` MUST NOT claim a review enforcement that branch protection does not provide.
- Enabling required review later requires a superseding decision, and it needs either a second reviewer
  or the machine account.
- Any new alert class that appears in secret scanning MUST be triaged and recorded, not silenced.

## Verification

- `gh api repos/davidru85/carApp/branches/main/protection` reports fifteen checks, zero approvals, no
  code-owner review, no administrator enforcement (read 2026-10-27).
- `gh api repos/davidru85/carApp --jq '.security_and_analysis'` reports `secret_scanning` and
  `secret_scanning_push_protection` `enabled`.
- The two alerts are listed as resolved `wont_fix` with the recorded justification.

## References

- `docs/DECISION_BOARD.md` (decision ID `D-260`); `D-31`, `D-34`, `D-229`, `D-230`
- `AGENTS.md` §Human Review Gates, §What is enforced; `docs/SECURITY.md`
