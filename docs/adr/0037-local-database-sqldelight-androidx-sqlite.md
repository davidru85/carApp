# ADR-0037 / D-36 - Use SQLDelight with AndroidX Bundled SQLite

## Status

Accepted

Accepted by the owner on 2026-08-22.

## Context

`E1-01` requires one canonical SQLite schema containing exact table-level `CHECK` constraints,
including the tombstone relation between `deleted` and `deletedAt`. Room 3 KMP does not expose an
entity-schema declaration for those constraints. Creating the tables through callbacks would make
runtime DDL differ from Room's exported schema and migration model, so it would not provide one
verifiable source of truth.

SQLDelight represents the schema and queries directly in committed SQL and validates them during
the build. Its official Android driver uses the platform SQLite version, however. Android API 26
ships SQLite 3.18, while the exact outbox coalescing statement in `docs/CONTRACTS.md §8` requires
the `ON CONFLICT DO UPDATE` syntax added in SQLite 3.24. The project therefore also needs a bundled
SQLite driver on Android rather than the platform driver.

## Options Considered

| Option | Benefits | Costs / Risks |
|--------|----------|---------------|
| Room 3 KMP with callback-owned DDL | Keeps the previously selected API and official AndroidX dependencies. | Splits the runtime schema from Room's generated/exported schema, weakens migration verification, and makes the mandatory constraints invisible to Room tooling. |
| SQLDelight with official Android and Native drivers | Uses only official SQLDelight drivers. | Android API 26-28 cannot execute the required UPSERT; Android and iOS run different SQLite versions. Preserving `minSdk 26` would require changing the exact contract to a multi-statement transaction. |
| SQLDelight with `sqldelight-androidx-driver` and bundled SQLite | Keeps committed SQL as the schema source, supports the exact constraints and UPSERT, preserves `minSdk 26`, and runs the same SQLite engine on Android and iOS. | Adds a small third-party adapter, requires asynchronous SQLDelight generation, and needs Android host-test dependency substitution. |
| Raise `minSdk` to 30 and use the official Android driver | Keeps the exact UPSERT with an official driver. | Drops Android 8 and 9 and changes the product compatibility contract solely for database syntax. |

## Decision

Use SQLDelight 2.3.2 with the SQLite 3.24 dialect. Execute it through
`com.eygraber:sqldelight-androidx-driver` 0.2.1 and
`androidx.sqlite:sqlite-bundled` 2.7.0 on Android and iOS.

SQLDelight generation is asynchronous because the selected adapter is suspending. Native linking
does not link the system SQLite because the bundled artifact already supplies SQLite. Committed
`.sq` files are the canonical schema and query source, `verifyMigrations` remains enabled, and
future version changes use committed `.sqm` migrations plus migration tests that preserve rows.

## Consequences

### Positive

- The mandatory SQLite `CHECK` constraints and the exact outbox UPSERT are build-validated SQL.
- Android and iOS use the same bundled SQLite implementation, including on Android API 26.
- One-shot operations are suspending, and observable queries integrate with `Flow`.
- KSP and Room schema export are removed from the local-database build path.

### Negative

- `sqldelight-androidx-driver` is a third-party dependency with a smaller maintenance surface than
  the official SQLDelight drivers.
- Generated operations return asynchronous results and require the SQLDelight async extensions.
- Android host tests must replace the Android bundled-SQLite artifact with its JVM variant at
  runtime so the native host library is available.

### Constraints Introduced

- SQLDelight, its AndroidX adapter and AndroidX SQLite are restricted to `:core:database`.
- The official SQLDelight Android driver MUST NOT replace the bundled driver while `minSdk 26` and
  the exact `docs/CONTRACTS.md §8` UPSERT remain in force.
- `generateAsync` and `verifyMigrations` MUST remain enabled.
- Native targets using the bundled driver MUST keep SQLDelight's system-SQLite linking disabled.
- Destructive schema recreation is forbidden. Every future schema version requires a committed
  `.sqm` migration and a populated previous-version migration test.

## Verification

- A compatibility spike generated the schema and compiled the exact UPSERT for Android and
  `iosSimulatorArm64` with Kotlin 2.4.10, AGP 9.3.1 and Gradle 9.7.1.
- `E1-01` owns Android and iOS database execution tests, constraint tests, migration verification,
  and the complete repository verification command.

## References

- `docs/DECISION_BOARD.md` (`D-36`)
- `docs/CONTRACTS.md §3.1`, `§8`, `§20.3.2`
- `docs/TECHNICAL_PLAN.md §6`
- `docs/BACKLOG.md` (`E1-01`)
- [ADR-0002](0002-local-database-room-kmp.md) (`D-1`, superseded)
