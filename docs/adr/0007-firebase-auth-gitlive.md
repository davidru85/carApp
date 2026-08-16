# ADR-0007 / D-6 - Use Firebase Auth Through GitLive Behind AuthClient

## Status

Accepted

## Context

The MVP requires anonymous login, Google sign-in, Apple sign-in on iOS, account linking, sign-out, account deletion, and ID token refresh. Native UI is required to obtain Google and Apple credentials.

## Options Considered

| Option | Benefits | Costs / Risks |
|--------|----------|---------------|
| GitLive Auth 2.6.x behind `AuthClient` | Common auth session model and token access. | Community wrapper, must be isolated. |
| Native Firebase Auth with expect/actual | Official SDKs per platform. | Duplicate platform implementations. |
| Custom auth backend | Full control. | Out of scope for MVP. |

## Decision

Use Firebase Auth through GitLive 2.6.x behind `AuthClient`.

## Consequences

### Positive

- Supports anonymous identity and account linking.
- Keeps auth state common while credential UI remains native.

### Negative

- Requires careful typed error mapping for cancelled dialogs and credential collisions.

### Constraints Introduced

- Native layers obtain provider credentials.
- Common auth code exchanges credentials for Firebase sessions.
- Firebase/GitLive auth types do not leave `:integration:firebase-auth`.

## Verification

- Anonymous login works on both platforms.
- Google sign-in works on both platforms.
- Apple sign-in works on iOS.
- Credential collision tests or integration verification cover F-4.
