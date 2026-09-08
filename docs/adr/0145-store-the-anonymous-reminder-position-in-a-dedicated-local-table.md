# ADR-0145 / D-144 - Store the Anonymous Reminder Position in a Dedicated Local Table

## Status

Accepted

## Context

`D-62` (ADR-0063) fixed the anonymous sign-in benefit reminder schedule but deliberately left the
physical persistence location to the E2-07 intake, as `docs/PROJECT_LOG.md` records. The schedule
needs one device-local value, the zero-based index of the last reminder shown, and
`docs/CONTRACTS.md §11.3` requires it to survive process and app restarts.

Two properties constrain the choice. The value is not a user preference: the owner never sets it,
it carries no meaning outside the reminder schedule, and it is not part of the settings the product
recreates from defaults. It is also identity-scoped: a device that starts a different anonymous
session must start the schedule again, so the stored position is only meaningful next to the
anonymous UID that produced it.

Until this story the local schema had never been migrated. Schema version 1 shipped with `E1-01`,
and `docs/TECHNICAL_PLAN.md §6` already fixes what a version bump costs: a committed `.sqm`
migration and a test that migrates a populated previous-version database.

## Options Considered

| Option | Benefits | Costs / Risks |
|--------|----------|---------------|
| **Dedicated `anonymous_reminder` table in schema version 2 (accepted)** | The row models exactly one concept and carries the anonymous UID that scopes it; it is cleared independently of settings; the migration is additive, so nothing existing can be lost. | Introduces the first schema migration and touches the shared-write `:core:database` module. |
| A new column on `user_settings` | Reuses an existing device-local row. | Costs the same migration while storing schedule state in the settings row; the UID cannot be expressed without a second column; sign-out clears settings for reasons unrelated to the schedule. |
| A platform key-value store (DataStore / `UserDefaults`) | No schema migration. | Requires a new `AppGraphDependencies` member, which changes the canonical parameter order and the test factory, plus two host implementations; it splits local state across two stores for one integer. |

## Decision

The last-shown reminder index is persisted in a dedicated single-row `anonymous_reminder` table
introduced by schema version 2 through the committed migration `1.sqm`. The row holds the
`anonymousUid` it belongs to and the zero-based `lastShownIndex`.

`AnonymousReminderRepository` reports a stored position as absent when it belongs to a different
anonymous UID, which is what restarts the schedule for a new anonymous identity, and exposes
`clear()`, which permanent sign-in and successful linking call.

A persistence failure is returned as a typed `PersistenceError.TransactionFailed` and swallowed by
the state holder: a non-blocking retention notice is not worth reporting an error the owner cannot
act on, and the notice is simply not shown.

## Consequences

### Positive

- The reminder position is independent of `user_settings` and of every synchronized entity.
- A new anonymous identity restarts the schedule without any explicit reset step.
- The migration path of `docs/TECHNICAL_PLAN.md §6` becomes executable rather than theoretical.

### Negative

- `:core:database` is a shared-write module, so this story owns it for its duration.
- Every later schema change now starts from version 2 and must extend the migration chain.

### Constraints Introduced

- The `anonymous_reminder` table MUST stay device-local: it is never synchronized, never enqueued
  in the outbox and never part of the closed remote schema of `docs/CONTRACTS.md §16`.
- A stored position MUST be reported as absent for any anonymous UID other than the one that
  produced it.
- Every later schema version MUST ship its own `.sqm` migration and a populated previous-version
  migration test; destructive recreation stays forbidden.

## Verification

- `core/database/.../AnonymousReminderMigrationTest.kt` pins schema version 2, migrates a
  populated version one database without losing a vehicle, a fuel entry or the settings row, and
  proves the migration and a fresh install both produce a usable empty table.
- `core/database/.../AnonymousReminderDatabaseAccessTest.kt` pins the single-row upsert, the
  absent read, deletion and that a reminder write touches no synchronized entity and no outbox row.
- `feature/session/.../SqlDelightAnonymousReminderRepositoryTest.kt` pins read-back, survival
  across a new repository over the same database, the restart for a different anonymous UID, and
  clearing.

## References

- `docs/CONTRACTS.md` §11.3
- `docs/TECHNICAL_PLAN.md` §6
- `docs/adr/0063-anonymous-sign-in-benefit-reminders.md` (`D-62`)
