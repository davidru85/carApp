# ADR-0107 / D-106 - Bootstrap Device-Local Settings in AppGraph

## Status

Accepted

Selected by the owner on 2026-09-01 during story E1-10.

## Context

E1-10 requires the single device-local settings row to be created on first launch. It also requires
destructive local-data deletion to remove that row and the next repository access to recreate
locale-derived defaults with analytics disabled. These are related timing requirements but they
must not create two competing authorities for default values.

`SqlDelightSettingsRepository` already self-heals a missing row when its settings flow is accessed.
That behavior must remain the functional authority for deletion recovery. The literal first-launch
criterion additionally needs an eager accelerator even when no UI or state holder consumes
settings. The accelerator must not make application startup depend on an asynchronous database
write, and its lifetime must remain safe when an application graph is closed immediately.

The general graph-close race registered as issue #42 remains owned by E1-12 under D-89. E1-10 must
add its settings-specific lifecycle regression without widening scope into that general fix.

## Options Considered

| Option | Benefits | Costs / Risks |
|--------|----------|---------------|
| Repository self-healing plus a one-shot, best-effort `AppGraph` bootstrap | Preserves one defaulting authority; satisfies first-launch creation without host orchestration; works on both platforms; keeps locale APIs outside `:core:database` | Adds a graph-owned coroutine scope; cancellation and the missing-row write require explicit close ordering and a closed-state guard |
| Explicit suspending `AppGraph` initializer called by both hosts | Makes completion and failure timing explicit; avoids hidden eager work | Changes common and Swift-facing startup orchestration; requires host loading or error behavior; future hosts can omit the call |
| Create the row in `DatabaseFactory` | Ensures the row exists before graph consumers; needs no background bootstrap | Moves locale-derived product defaults into `:core:database`; widens factory and test contracts; weakens the current dependency boundary |

## Decision

Keep `SqlDelightSettingsRepository` self-healing exactly as the functional authority for missing
settings. `DefaultAppGraph` starts one terminating read that waits for the first successful
repository settings value and then completes. This read is only a first-launch accelerator: graph
construction does not await it, its failure does not block or crash startup, and it never produces
a UI error. A later repository access performs the same self-healing if bootstrap does not finish.

The graph owns `CoroutineScope(SupervisorJob() + dependencies.dispatchers.io)`. `AppGraph.close()`
marks the volatile graph flag closed, cancels that scope and only then closes `databaseHandle`.
Before the repository resolves locale defaults or writes them for the graph bootstrap, it re-checks
a graph-supplied creation gate. Volatility makes closure visible across JVM and Kotlin/Native
threads, but the gate and write are not atomic; the residual check-then-act window remains under
D-89 and is not solved by E1-10.

## Consequences

### Positive

- First launch creates device-local defaults even without a settings consumer.
- Deletion recovery and first launch share the repository's locale/default rule.
- Startup remains non-blocking and cannot surface a bootstrap-specific UI failure.
- The bootstrap terminates after one successful value and cannot become a graph-lifetime
  subscription.

### Negative

- `DefaultAppGraph` owns one additional coroutine scope and must cancel it explicitly.
- A small closure window remains part of the general D-89 lifecycle problem; E1-12, not E1-10,
  owns issue #42 and its general solution.

### Constraints Introduced

- `SqlDelightSettingsRepository` MUST remain self-healing on normal repository access.
- The eager bootstrap MUST terminate after the first successful settings value and MUST NOT
  subscribe indefinitely.
- Bootstrap failure MUST be silently absorbed and MUST NOT crash, block startup or publish a UI
  error.
- The graph scope MUST be exactly a supervisor scope on `dispatchers.io`.
- The graph closure flag read by the bootstrap MUST be volatile.
- `AppGraph.close()` MUST cancel the graph scope before closing the database handle.
- Missing-row creation initiated by bootstrap MUST re-check graph closure immediately before the
  write.
- E1-10 MUST NOT attempt the general issue #42 fix owned by E1-12 / D-89.

## Verification

- `FuelEntryStateHolderTest.graphBootstrapCreatesSettingsWithoutAConsumer` observes the defaults
  created by a graph with no settings consumer.
- `AppGraphCloseTest.kotlinGraphCanCloseImmediatelyWhileSettingsBootstrapStartsWithoutAConsumer`
  constructs and closes a graph immediately and proves idempotent database closure without an
  exception on Android host.
- `SqlDelightSettingsRepositoryTest.closedGraphGatePreventsBootstrapDefaultWrite` proves a false
  gate neither resolves locale defaults nor writes or emits; it does not prove atomicity with
  `AppGraph.close()`.
- `SqlDelightSettingsRepositoryTest.sqlDelightObservationCreatesLocaleDefaults` proves the real
  SQLDelight-backed repository remains self-healing.
- Existing deletion-recreation tests prove normal repository access remains authoritative.

## References

- `docs/BACKLOG.md` E1-10 and E1-12
- `docs/CONTRACTS.md` §11.5 and §20.3
- ADR-0090 / D-89
- issue #42
