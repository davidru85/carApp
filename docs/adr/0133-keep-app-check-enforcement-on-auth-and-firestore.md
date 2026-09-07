# ADR-0133 / D-132 - Keep App Check Enforcement on Authentication and Firestore

## Status

Accepted

## Context

D-67 enforces App Check only for Authentication and Firestore. D-131 explicitly deferred the
Cloud Functions scope until E3-11, when both 2nd gen deletion callables (`deleteAccount` and
`deleteOrphanedAnonymousAccount`) exist and can be governed by one decision. Both callables
already verify the Firebase Auth caller context, and `deleteOrphanedAnonymousAccount` also
requires a single-purpose server authorization bound to the abandoned anonymous UID before any
deletion. D-141 later added `issueOrphanCleanupTicket` under the same posture.

## Options Considered

| Option | Benefits | Costs / Risks |
|--------|----------|---------------|
| Keep D-67 unchanged: no App Check enforcement on Cloud Functions | The deletion callables still require a verified Firebase caller and explicit server-side authorization; the accepted D-67 scope remains stable. | Any authenticated client can invoke the callables without an App Check token; abuse mitigation stays with authentication and authorization checks and the D-66 billing cutoff. |
| Extend D-67 to both callables with enforcement now | Single enforcement posture across Functions; rejects unauthenticated device abuse earlier. | Changes the accepted D-67 scope; requires every client call site (E2-04, E2-05) to attach App Check before the callables can be used; needs additional debug-provider provisioning in development. |
| Enable monitor-only mode and enforce later | Observes real traffic before blocking anything. | Defers a decision twice, leaves the unenforced window open, and adds a second operational change without removing any client work. |

## Decision

Keep D-67 unchanged. Cloud Functions are not added to the App Check enforcement scope in E3-11.
The deletion callables and the D-141 ticket issuer continue to rely on verified Firebase
Authentication context, explicit authorization and payload validation, and typed failure mapping;
the D-66 alerts-only budget and billing cutoff remain the development cost safety net.

## Consequences

### Positive

- No client change is required before E2-04 and E2-05 consume the callables.
- The accepted D-67 scope and its runbooks stay untouched.
- TD-01 does not inherit an unrelated enforcement migration.

### Negative

- Authenticated clients can invoke the callables without an App Check token; only authentication,
  payload validation and IAM/authorization checks gate the destructive operations.
- Automatic cleanup abuse remains bounded by authentication state and the delayed billing cutoff
  rather than by caller-integrity enforcement.

### Constraints Introduced

- A future extension of App Check to Cloud Functions is a separate owner decision that MUST
  cover both deletion callables and the cleanup-ticket issuer together.
- Any new callable added before then inherits this posture and MUST NOT enable Functions App
  Check piecemeal.

## Verification

- `functions/src/**` contains no App Check verification dependency or middleware.
- `dependencyReachability.test.mjs` pins the exported function set; no additional enforced
  callable appears.
- The D-66 budget and cutoff runbooks remain the only development cost controls.

## References

- `docs/DECISION_BOARD.md` (`D-67`, `D-131`)
- `docs/adr/0132-bound-the-account-deletion-callable-runtime.md`
- `docs/TECHNICAL_PLAN.md §13` (`TD-01`)
- `docs/BACKLOG.md` (`E3-11`)
