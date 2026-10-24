# ADR-0243 / D-242 - Keep the Silent Release Logger Until a Redacting Logger Story

## Status

Accepted

The owner selected option B on 2026-10-24.

## Context

`docs/BACKLOG.md` `E4-04` asks for "Release logging redaction is verified". No production `Logger`
exists: `firebaseAppProviders` binds `stagedLogger()`, whose `log` body is empty, in every build, and
Kermit (`D-15`) is pinned but used by no module. The graph calls the logger in two places, both
`WARN` lines (a quarantined remote document and a failed adoption trigger). `docs/CONTRACTS.md §17`
defines what a logger may emit; it does not require one to emit anything.

## Options Considered

| Option | Benefits | Costs / Risks |
|--------|----------|---------------|
| A. Implement the redacting Kermit `Logger` of `§17` now: entity IDs truncated to eight characters plus an ellipsis in release, never the UID, throwables reduced to codes, only the last class-name segment at `INFO` and above, plus an optional Crashlytics breadcrumb sink limited to the `§17` allowlist | Operational diagnostics in release builds | A placement decision (new module or existing one), a Kermit sink, five redaction rules with a test each and the optional sink: roughly another medium story inside `E4-04` |
| B. Keep the silent logger, prove with a test that it emits nothing, and open a follow-up story | `E4-04` stays bounded; release logs carry no user data by construction | No diagnostics from any device until the follow-up; the two `WARN` lines are discarded; ADR-0023's breadcrumb sink is deferred |

## Decision

The selected option is **B**. `FirebaseAppProvidersTest.theProvidersLoggerReadsNothingItIsGiven`
proves that the logger the providers bind reads none of its inputs — neither the field map nor the
throwable's message — at any level, so it cannot emit them; a mutation that prints its inputs makes it
fail. Story `E4-08` "Redacting Release Logger (Kermit)" records option A's work.
`docs/CONTRACTS.md §17` states the interim.

## Consequences

### Positive

- The release-logging criterion is verified by an executable test, not by inspection.
- No user data can reach a log line while the silent logger is bound.

### Negative

- No operational diagnostics from debug or release devices until `E4-08`.

### Constraints Introduced

- Binding any other `Logger` in `:wiring:firebase` MUST deliver the `§17` redaction with one test per
  rule, under `E4-08` or a superseding decision.
- The proof test MUST stay until that story replaces it with the redaction tests.

## Verification

- `FirebaseAppProvidersTest.theProvidersLoggerReadsNothingItIsGiven`, and its recorded mutation result.

## References

- `docs/DECISION_BOARD.md` (decision ID `D-242`)
- `docs/adr/0016-logging-kermit.md` (`D-15`)
- `docs/adr/0023-firebase-crashlytics.md` (`D-21`)
- `docs/CONTRACTS.md §17`
- `docs/BACKLOG.md` (`E4-04`, `E4-08`)
