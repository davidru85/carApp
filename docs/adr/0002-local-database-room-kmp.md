# ADR-0002 - Use Room 3.0 KMP for Local Database

## Status

Accepted

## Context

The app requires durable local persistence on Android and iOS. The local database is the only UI source of truth and must support offline writes, observable queries, transactions, migrations, and sync metadata.

## Options Considered

| Option | Benefits | Costs / Risks |
|--------|----------|---------------|
| Room 3.0 KMP | Familiar Android model, KMP support, Flow integration, schema and migration tooling. | Newer iOS/KSP path may have friction. |
| SQLDelight | Mature KMP SQLite option, explicit SQL, strong multiplatform story. | More manual mapping and less aligned with selected Room conventions. |

## Decision

Use Room 3.0 KMP with `androidx.sqlite:sqlite-bundled`.

## Consequences

### Positive

- Same bundled SQLite version across Android and iOS.
- Modern SQLite features are available even with Android `minSdk 26`.
- Observable local data model integrates with Flow.

### Negative

- The walking skeleton must validate Room on iOS early.

### Constraints Introduced

- If Room KMP/KSP blocks iOS progress during the walking skeleton, switch to SQLDelight immediately.
- Database access remains behind repositories and `:core:database` boundaries.

## Verification

- Android and iOS database instantiation tests.
- Migration tests.
- Walking skeleton on `iosSimulatorArm64`.
