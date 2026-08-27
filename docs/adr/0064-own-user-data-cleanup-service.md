# ADR-0064 / D-63 - Own the User-Data Cleanup Service

## Status

Accepted

## Context

Firebase's native 30-day cleanup deletes abandoned anonymous Auth users but does not by itself
remove the application's Firestore data. Firebase's Delete User Data extension can react to Auth
deletion, but Firebase Extensions management is sunset on 2027-03-31: already-deployed extensions
may continue executing, while console and CLI update, reconfiguration and uninstall capabilities
end. Adopting it now would create externally scheduled migration debt.

Authentication deletion events currently have no Cloud Functions 2nd gen equivalent. A custom
`auth.user().onDelete` handler therefore requires one narrow 1st gen exception. Separately, the
account-linking collision flow deletes the abandoned Auth account through the Admin SDK and must
not rely on an Auth trigger being delivered.

## Options Considered

| Option | Benefits | Costs / Risks |
|--------|----------|---------------|
| Own an idempotent cleanup service, call it directly from collision handling and retain one 1st gen Auth trigger | Versioned, exact-schema behavior with self-owned migration timing. | Pins one function to 1st gen until an equivalent trigger is generally available. |
| Install the Delete User Data extension | Less repository code. | Its management surface sunsets on 2027-03-31, preventing later console/CLI update, reconfiguration or uninstall and forcing migration debt. |
| Rely only on `auth.user().onDelete` | Smallest implementation. | Admin SDK deletion paths are not allowed to depend on trigger delivery and could leave orphaned data. |

## Decision

E3-10 owns an idempotent `deleteUserData` service and an explicit data-location registry. E3-11
adds two callers:

- `onAnonymousUserDeleted`, the sole permitted 1st gen function, is relied upon only for Firebase's
  automatic anonymous-account cleanup path; delivery from another anonymous deletion is harmless
  overlap;
- `deleteOrphanedAnonymousAccount`, a 2nd gen callable, verifies the captured anonymous token,
  deletes that Auth account and invokes `deleteUserData` directly afterward.

Trigger overlap is expected and harmless. The registry lists every Firestore location and Storage
prefix owned by the schema. The MVP Storage list is explicitly empty. Executable parity tests fail
when a declared data location is absent from the registry.

The temporary 1st gen constraint is tracked canonically as `TD-01` in
`docs/TECHNICAL_PLAN.md §13`. No additional 1st gen function may be introduced.

## Consequences

- Automatic cleanup and collision cleanup share one tested deletion implementation.
- The Admin SDK collision path remains correct even if the Auth deletion trigger does not run.
- The repository owns its migration surface and quarterly review schedule.
- Any future Storage-backed schema change must update the registry and parity test atomically.

## Verification

- E3-10 proves deletion order, idempotency and registry/schema parity.
- E3-11 proves the Admin SDK deletion path removes `users/{uid}` independently of trigger
  delivery.
- Contract checks allow exactly one 1st gen function and identify it by name.
- `TD-01` review history is updated quarterly from 2026-12-01.

## References

- `docs/TECHNICAL_PLAN.md §13` (`TD-01`)
- `docs/BACKLOG.md` (`E3-10`, `E3-11`)
- [Firebase Authentication trigger documentation](https://firebase.google.com/docs/functions/1st-gen/auth-events)
- [Firebase release notes](https://firebase.google.com/support/releases)
- `D-23`, `D-61`
