# ADR-0248 / D-247 - Deploy the Billing Cutoff from a Development-Only Codebase

## Status

Accepted

The owner selected option A on 2026-10-24. Amends the single-codebase clause of `D-136`
(ADR-0137) and the single-package wording of `D-66` (ADR-0067).

## Context

`functions/src/index.ts` exported `stopBilling` unconditionally, bound to the development service
account. A plain production `firebase deploy --only functions` would therefore ship the `D-66`
cutoff, which production MUST NOT have. The Firebase CLI (15.28.1) accepts a `functions` array of
codebases, each with a unique `codebase` name and a unique `source` directory, and deploys one
codebase with `--only functions:<codebase>`. It assigns an existing function to a codebase first by
name and then by its `firebase-functions-codebase` label, and a deploy of one codebase deletes the
functions labelled with that codebase that its source no longer exports.

## Options Considered

| Option | Benefits | Costs / Risks |
|--------|----------|---------------|
| A. A separate `dev-billing` codebase from `functions-dev-billing/`, deployed only to development; a test asserts the product deploy set excludes `stopBilling` | Production's only deploy target cannot contain the cutoff; executable guard. | A second Node package and lockfile with the same pins; one-time relabel deploy in development. |
| B. A runbook-only `--only functions:<list>` | No code change. | Error-prone; nothing fails when the list is wrong. |

## Decision

The selected option is **A**.

- `firebase.json` declares two codebases: `default` from `functions/` (the four product functions)
  and `dev-billing` from `functions-dev-billing/` (`stopBilling` only), both on the matrix runtime.
- `functions-dev-billing/` holds the billing sources and tests, pins `@google-cloud/billing` 6.0.0,
  `firebase-admin` 14.3.0 (mandatory peer), `firebase-functions` 7.3.2, `typescript` 7.0.2 and
  `@types/node` 22.20.1 exactly, has its own lockfile and an `.npmrc` with `ignore-scripts=true`.
- `functions/` no longer depends on `@google-cloud/billing`.
- Production deploys `functions:default` only; development deploys `functions:dev-billing` first after
  this change, so the existing `stopBilling` is relabelled before a `default`-only deploy could delete it.

## Consequences

### Positive

- The production deploy set cannot include the cutoff; the product package's dependency graph shrinks.

### Negative

- Two Functions packages to keep on the same runtime; TD-01 moves both.

### Constraints Introduced

- `contractCheck` assertion 22 (`FunctionGenerationContract`) requires exactly the two codebases, the
  product export set without `stopBilling` and the development export set equal to `stopBilling`.
- Assertion 19 (`CloudRuntimeContract`) checks both packages' runtime and every codebase runtime.
- CI installs, audits and tests both packages in `contract-check`.

## Verification

- `functions/test/productDeploySet.test.mjs`; `npm --prefix functions-dev-billing test`;
  `FunctionGenerationContractTest`; `CloudRuntimeContractTest`.

## References

- `docs/DECISION_BOARD.md` (decision ID `D-247`); `D-66` (ADR-0067); `D-68`; `D-136` (ADR-0137);
  `docs/TECHNICAL_PLAN.md §13`; `docs/runbooks/development-firebase-cost-controls.md`
