# ADR-0034 / D-33 - Repository Stays Private; Branch Protection Deferred

## Status

Superseded

Accepted by the owner on 2026-08-21 and superseded the same day by `D-34`
([ADR-0035](0035-repository-public-and-branch-protection-active.md)), when the repository was made
public and the deferred branch protection was applied. The trigger this ADR defined is what fired.

## Context

`D-31` requires branch protection on `main` for the nine CI checks of `docs/CONTRACTS.md §18`. It could not be applied. `carApp` is a private repository on the GitHub Free plan, where both enforcement mechanisms are gated:

```text
PUT  /repos/davidru85/carApp/branches/main/protection -> 403
POST /repos/davidru85/carApp/rulesets                 -> 403
"Upgrade to GitHub Pro or make this repository public to enable this feature."
```

The owner chose to keep the repository private for now rather than pay for GitHub Pro or publish a repository that is not ready to be read by strangers.

The consequence is precise and worth stating plainly: every check built by `E0-04` and `E0-05` still runs on every pull request and still reports, but **nothing stops a red pull request from being merged**. CI is advisory, not enforcing, and `§18`'s "branch protection for `main` MUST require these checks" is unsatisfied.

## Options Considered

| Option | Benefits | Costs / Risks |
|--------|----------|---------------|
| Stay private, defer branch protection, record the trigger | No cost, and no premature publication. The checks still surface every failure on the pull request. | `§18` is unsatisfied for as long as it lasts, and the only thing standing between a red build and `main` is the maintainer reading the checks. |
| Upgrade to GitHub Pro | Protection now, repository stays private. | A recurring cost for a project with one maintainer and no users. |
| Make the repository public | Protection now, no cost. | Publishes an unfinished codebase, and `docs/SECURITY.md` assumes a private repository for the committed Firebase configuration files. |

## Decision

The repository stays private and branch protection is deferred.

**The trigger is explicit: branch protection MUST be applied in the same change that makes the repository public, or that moves it to a plan where protection is available.** Whoever performs that change owns applying it. It MUST NOT be left as a follow-up.

The configuration to apply is fixed by `D-31` and is not re-decided at that point: the nine `§18` check names, a required pull request, no force pushes, no branch deletion, and administrator enforcement off.

## Consequences

### Positive

- No cost and no premature publication.
- The decision has a named trigger instead of living as a memory.

### Negative

- Until the trigger fires, CI reports but does not gate. A merge on red is possible and only discipline prevents it.
- `docs/CONTRACTS.md §18` has a requirement that the repository knowingly does not meet. That is recorded here rather than quietly ignored.

### Constraints Introduced

- `E4-04` MUST NOT close while the repository is public without branch protection active.
- Making the repository public is now a change with a required companion step.

## Verification

- While private: `gh api repos/davidru85/carApp/branches/main/protection` returns `403`, and this ADR is the record of why.
- After the trigger: the same call lists the nine contexts of `D-31`.

## References

- `docs/DECISION_BOARD.md` (`D-33`)
- [ADR-0032](0032-branch-protection-requires-nine-checks.md) (`D-31`)
- `docs/CONTRACTS.md §18`, `docs/SECURITY.md`
