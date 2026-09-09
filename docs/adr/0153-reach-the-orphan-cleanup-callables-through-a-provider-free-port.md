# ADR-0153 / D-152 - Reach the Orphan Cleanup Callables Through a Provider-Free Port

## Status

Accepted

## Context

`E3-11` deployed two callable operations that `E2-04` must invoke: `issueOrphanCleanupTicket`, which
the app calls while the anonymous session is still active, and `deleteOrphanedAnonymousAccount`,
which it calls after the session has switched to the permanent account. Before this story no client
port existed for either.

Two repository rules constrain how they may be reached. Firebase, GitLive and native Firebase types
are allowed only inside `:integration:*` and `:wiring:*`, and `:shared` MUST NOT depend on
integrations at all. The shared coordinator that drives the five-step protocol therefore cannot see
a callable reference of any kind; it needs a Kotlin-pure seam it can also fake in `commonTest`.

The transport itself is the second half of the problem. A callable is not a plain HTTP endpoint: the
Firebase client SDKs attach the current ID token and the App Check attestation, and App Check is
enforced for this project.

## Options Considered

| Option | Benefits | Costs / Risks |
|--------|----------|---------------|
| A. A provider-free `OrphanCleanupClient` port in `:core:auth` with a GitLive `firebase-functions` adapter in `:integration:firebase-auth` | Adds no new library and no new version — one further artifact of the GitLive release already accepted for auth and Firestore, so the `D-65` Apple compatibility pin still governs it; token and App Check attachment come from the SDK; the port is one Kotlin-pure interface the shared tests fake | One more GitLive artifact in the dependency graph and in the `D-75` standalone Kotlin/Native test exception |
| B. Host-specific `expect`/`actual` bridges over the native Firebase Functions SDKs | Uses each platform's first-party SDK directly | Two implementations to write and keep in step; `expect`/`actual` would carry a platform API into common code, which the architecture rules restrict; the iOS side would need a second native dependency path beside the one `D-75` already derives |
| C. A raw HTTPS client against the callable endpoints | No Firebase client dependency for this call at all | Reimplements ID-token attachment and App Check attestation by hand, which is exactly the security machinery that must not be re-derived; needs an HTTP client, and the Ktor decision forbids adding one without a new ADR; the callable request and response envelope becomes hand-maintained |

## Decision

The selected option is **A**. `:core:auth` declares the port:

```kotlin
data class OrphanCleanupTicket(val value: String)

interface OrphanCleanupClient {
    suspend fun issueOrphanCleanupTicket(): Outcome<OrphanCleanupTicket, AuthError>

    suspend fun deleteOrphanedAnonymousAccount(ticket: OrphanCleanupTicket): Outcome<Unit, AuthError>
}
```

`:integration:firebase-auth` implements it over `dev.gitlive:firebase-functions`, pinned to the
GitLive version already in the catalog and regionalized to `europe-west1`, the Firestore and
Functions location recorded in `docs/identifiers.md`. The raw ticket is read from the `cleanupTicket`
key of the callable response and sent back under the same key, which is what the deployed function
returns and expects. `:wiring:firebase` binds the adapter for the staged and production graphs, and
`AppGraphDependencies` carries the port immediately after `authClient`.

## Consequences

### Positive

- `:shared` keeps its Kotlin-pure surface and the coordinator is testable with a fake.
- App Check and ID-token attachment stay the SDK's responsibility.
- No new library or version decision is required; the GitLive pin remains a single moving part.

### Negative

- The `dev.gitlive:firebase-functions` artifact joins the transitive Firebase graph, so the
  `D-75` derived Kotlin/Native test exception now covers it as well.
- The callable payload keys are a contract with the deployed function that no compiler checks.

### Constraints Introduced

- Firebase and GitLive types MUST NOT escape `:integration:firebase-auth`.
- The adapter MUST map provider failures onto the `docs/CONTRACTS.md §8` `AuthError` taxonomy and
  MUST NOT leak the raw provider error, the ticket or the UID.
- The port and its ticket type are hidden from the generated Objective-C header, like the rest of
  the `§11.6` construction API, so any test double implementing the port MUST carry the same
  `@HiddenFromObjC` annotation.
- The `cleanupTicket` payload key is normative and MUST change with the function, in the same
  change, if it ever changes.

## Verification

- `FirebaseOrphanCleanupClientTest` pins both callable names, the `cleanupTicket` key in both
  directions and the provider-error mapping.
- `architectureCheck` enforces that `:shared` does not depend on integrations and that Firebase and
  GitLive types stay inside `:integration:*`.
- `testAppGraphDependencies` parity keeps the port present in the test, staged and production graphs.
- `iosSimulatorArm64Test` compiles the fake against the hidden port on Kotlin/Native.

## References

- `docs/DECISION_BOARD.md` (decision ID `D-152`), Firebase Decoupling Rule, Ktor Decision
- `docs/CONTRACTS.md §8`, `§11.3`, `§11.5`, `§11.6`
- `docs/TECHNICAL_PLAN.md §2`, `§4`
- `docs/adr/0149-verify-the-issuing-account-through-the-admin-sdk.md`
- `docs/handoff-E3-11.md`, `docs/handoff-E2-04.md`
