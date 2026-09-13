# ADR-0169 / D-168 - Keep Firestore Authentication Retry Inside the Integration

## Status

Accepted

## Context

`RemoteSyncSource` requires an unauthenticated Firestore operation to force an authentication token
refresh and retry that operation exactly once inside `:integration:firebase-firestore`. The module
already owns the provider query and write operations, but it previously had no way to request the
provider refresh.

GitLive Firebase Auth is already accepted and pinned at the same release as GitLive Firestore. This
choice therefore decides the ownership boundary, not a new library or version.

## Options Considered

| Option | Benefits | Costs / Risks |
|--------|----------|---------------|
| A. Refresh through the GitLive Firebase Auth client directly in `:integration:firebase-firestore` | Keeps the complete refresh-and-retry transaction in the module that owns the failed operation; adds no provider-free contract, graph parameter or wiring behavior; leaks no provider type | Gives the Firestore integration a direct dependency on the GitLive Auth artifact and a second reference to the provider client |
| B. Inject the existing provider-free `TokenProvider` from `:core:auth` through wiring | Reuses the authentication adapter and avoids a direct Auth artifact dependency in the Firestore integration | Splits the required transaction across auth, wiring and Firestore modules; adds constructor and wiring coupling; makes "inside this module" true only of the retry, not the refresh |
| C. Add a narrow provider-free token-refresh port to the app graph | Gives the capability an explicit abstraction and keeps provider SDK access in the auth integration | Widens `:core:*`, `AppGraphDependencies`, test graph parity and the Swift-visible graph surface for one provider recovery behavior |

## Decision

The selected option is **A**. `:integration:firebase-firestore` uses the same accepted GitLive
Firebase Auth release to force one token refresh after `UNAUTHENTICATED`, then retries the same
Firestore operation once. A refresh failure or a second operation failure returns the exact closed
`RemoteError` leaf and triggers no further attempt.

The provider exceptions are converted immediately to an internal failure vocabulary. Neither
Firebase nor GitLive types cross the module boundary.

## Consequences

### Positive

- The complete recovery transaction remains local to the remote-source implementation.
- Core sync, core auth, app-graph and wiring contracts do not gain a provider-specific recovery seam.
- The retry ceiling is visible and testable at one boundary.

### Negative

- The Firestore integration depends on both the GitLive Firestore and Auth artifacts.
- A future provider migration must replace both clients in this module.

### Constraints Introduced

- The module MUST force at most one token refresh for one failed operation.
- The retried operation MUST be the same query or write and MUST run at most once.
- Cancellation MUST propagate and MUST NOT be converted to `RemoteError`.
- Provider exceptions MUST be translated before leaving the integration boundary.

## Verification

- `FirebaseRemoteSyncSourceTest` proves successful push and pull recovery, the retry ceiling and the
  closed provider **error-code** taxonomy. It does not cover payload deserialization failures, which
  escape the closed `Outcome` API; that gap is `D-170` / ADR-0171.
- The architecture and provider-decoupling checks prove that no provider type crosses the module
  boundary.

## References

- `docs/DECISION_BOARD.md` (decision ID `D-168`)
- `docs/CONTRACTS.md §6`, `§9.4`, `§20.7`
- `docs/TECHNICAL_PLAN.md §2`, `§4`
- `docs/BACKLOG.md` (`E3-02`)
- `docs/handoff-E3-02.md`
- `docs/adr/0171-quarantine-malformed-remote-documents-through-the-sync-source.md` (`D-170`)
