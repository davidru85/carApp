# ADR-0032 / D-31 - Branch Protection Requires All Nine CI Checks

## Status

Accepted

Accepted by the owner on 2026-08-21.

## Context

`docs/CONTRACTS.md §18` fixes nine CI check names and states that, once CI exists, branch protection for `main` MUST require them. `E0-05` created the workflow with exactly those names.

Two of the nine have nothing to verify yet. `objc-header-golden-check` compares a golden Objective-C header that `E0-07` produces, and `provider-decoupling` proves the Firebase integration can be swapped, which `E3-06` owns. Both currently pass with a visible warning, and both fail loudly if the thing they guard appears without the check being implemented.

## Options Considered

| Option | Benefits | Costs / Risks |
|--------|----------|---------------|
| Require all nine now | Matches `§18` exactly. The two placeholders cost nothing and cannot pass silently once they have a subject. | Two required checks assert nothing today. |
| Require the seven real ones and add the others later | Every required check means something today. | `§18` names these checks; a later edit to branch protection is easy to forget, and the two that would be forgotten are the two nobody is watching. |

## Decision

Branch protection for `main` requires all nine checks by name: `android-assemble`, `shared-tests`, `ios-simulator-build`, `ktlint`, `detekt`, `architecture-check`, `provider-decoupling`, `contract-check`, `objc-header-golden-check`. It also requires a pull request, forbids force pushes and forbids branch deletion.

Administrator enforcement is off, so the owner can land the Phase 0 stack, whose earlier pull requests predate the workflow and therefore report no checks at all.

## Consequences

### Positive

- The rules built in `E0-04` and `E0-05` stop being advisory.
- The check names are locked, so renaming a job in the workflow breaks protection visibly rather than silently dropping a gate.

### Negative

- Administrator bypass exists. On a single-maintainer repository it is the mechanism that keeps the repository usable; it is a deliberate trade, not an oversight.

### Constraints Introduced

- Renaming a CI job REQUIRES updating branch protection in the same change.

## Blocked on the repository plan

The decision is accepted but **could not be applied**. `carApp` is a private repository on the
GitHub Free plan, where both enforcement mechanisms are gated:

```text
PUT  /repos/davidru85/carApp/branches/main/protection -> 403
POST /repos/davidru85/carApp/rulesets                 -> 403
"Upgrade to GitHub Pro or make this repository public to enable this feature."
```

Until the plan changes or the repository becomes public, every check built by `E0-04` and `E0-05`
runs on each pull request and reports, but nothing prevents a red pull request from being merged.
The owner chooses between upgrading to GitHub Pro, making the repository public, and accepting an
advisory-only CI in the meantime. Whichever is chosen is recorded as a further decision.

## Verification

- Once the plan allows it, `gh api repos/davidru85/carApp/branches/main/protection` lists the nine
  contexts. Today that call returns `403`.

## References

- `docs/DECISION_BOARD.md` (`D-31`)
- `docs/CONTRACTS.md §18`
- `.github/workflows/ci.yml`
