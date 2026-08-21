# ADR-0035 / D-34 - Repository Is Public and Branch Protection Is Active

## Status

Accepted

Accepted by the owner on 2026-08-21. Supersedes `D-33`.

## Context

`D-33` kept the repository private and deferred branch protection, with an explicit trigger: the `D-31` configuration MUST be applied in the same change that makes the repository public.

Two things then converged.

**Branch protection was unreachable.** On the GitHub Free plan a private repository cannot use branch protection or rulesets; both endpoints returned `403`. Every check built by `E0-04` and `E0-05` ran and reported, but nothing stopped a red pull request from merging.

**Actions minutes ran out.** The account exhausted its included minutes. The first CI run of this repository cost roughly **115 billed minutes**, of which 100 came from three macOS jobs: GitHub bills macOS at ten times wall-clock, with a one-minute minimum. At that rate a 2,000-minute allowance is about 17 runs a month, and every push to a pull request triggers one.

A public repository resolves both: GitHub-hosted standard runners are free for public repositories, and branch protection becomes available.

The repository was checked before publishing. Nothing that would block it is committed: no `google-services.json`, no `GoogleService-Info.plist`, no keystore, no private key, no API key. `E0-07` is the story that introduces the Firebase configuration files, so this was the cheapest possible moment to publish.

## Options Considered

| Option | Benefits | Costs / Risks |
|--------|----------|---------------|
| Make the repository public | Free Actions on standard runners, and branch protection becomes available. Fires the `D-33` trigger. Nothing sensitive is committed yet. | The codebase is readable by anyone from now on, and the Firebase configuration files of `E0-07` will land in the open. |
| Upgrade to GitHub Pro | Repository stays private, protection available. | A recurring cost, and Actions minutes stay metered, so the macOS cost problem remains. |
| Stay private and reduce CI | No cost, no publication. | Branch protection stays impossible, so `docs/CONTRACTS.md §18` stays unsatisfied indefinitely. |

## Decision

The repository is public. Branch protection for `main` was applied in the same change, with the configuration fixed by `D-31`: the nine `§18` check names, a required pull request, no force pushes, no branch deletion, administrator enforcement off.

`D-33` is superseded: its deferral no longer applies and its trigger has fired.

## Consequences

### Positive

- `docs/CONTRACTS.md §18` is satisfied: a red pull request can no longer be merged by the normal path.
- Actions minutes are no longer metered, so the macOS cost stops being a constraint on how often CI runs.

### Negative

- The codebase is public while still incomplete.
- The API-key restriction in `docs/SECURITY.md` stops being good practice and becomes a precondition. In a public repository the keys shipped in `google-services.json` and `GoogleService-Info.plist` are readable by anyone, and only the package-name, bundle-id and signing-certificate restrictions keep them from being usable elsewhere.
- Administrator enforcement is off, so the owner can still bypass a red build. That is deliberate on a single-maintainer repository.

### Constraints Introduced

- `E0-07` MUST NOT commit `google-services.json` or `GoogleService-Info.plist` until the corresponding API keys are restricted in the Google Cloud console. `docs/SECURITY.md` records this.
- Renaming a CI job REQUIRES updating branch protection in the same change.

## Verification

- `gh repo view` reports `visibility=PUBLIC`.
- `gh api repos/davidru85/carApp/branches/main/protection` lists the nine contexts, with `enforce_admins: false`, `allow_force_pushes: false` and `allow_deletions: false`.

## References

- `docs/DECISION_BOARD.md` (`D-34`)
- [ADR-0032](0032-branch-protection-requires-nine-checks.md) (`D-31`), [ADR-0034](0034-repository-stays-private-branch-protection-deferred.md) (`D-33`)
- `docs/CONTRACTS.md §18`, `docs/SECURITY.md`
