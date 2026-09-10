# ADR-0156 / D-155 - Keep the Session State Holder in `:shared`

## Status

Accepted

## Context

`docs/BACKLOG.md` E2-05 carried an acceptance criterion to move `SessionStateHolder` and its
`SessionUiState` / `SessionPhase` types from the D-55 `:shared` shells into the
`:feature:session` `presentation` package, mirroring the D-85 move that ADR-0086 applied to Vehicle
presentation. ADR-0086 assumed the move without accounting for what `SessionStateHolder` depends on.

`SessionStateHolder` is the authentication orchestrator: it calls `AuthClient` (`:core:auth`) and
`AnalyticsTracker` (`:core:analytics`). `docs/TECHNICAL_PLAN.md §4` forbids feature `presentation`
from depending on `:core:auth` and `:core:analytics`, and the architecture check is generated from
that table. A literal move would therefore fail `architectureCheck` unless the dependency rule were
relaxed for `:feature:session`, which is a gated change to module boundaries.

## Options Considered

| Option | Benefits | Costs / Risks |
|--------|----------|---------------|
| A. Keep `SessionStateHolder` in `:shared` | No dependency-rule change; the auth orchestrator stays beside `SyncStateHolder`, the other app-level holder; the Swift ABI does not move | The D-85 move of Session presentation is not applied; the E2-05 criterion and `docs/CONTRACTS.md §20.10` must record the deviation |
| B. Move to `:feature:session` and relax the rule | The feature owns its session state literally | Changes the dependency table and its fixtures (gated); opens an exception other features could imitate; `:feature:session` `presentation` would reach `:core:auth`/`:core:analytics` |
| C. Move only the UI types, keep the holder in `:shared` | The pure types need no auth dependency | Partial move that splits the Swift ABI across two modules; the criterion asks for holders and types together |

## Decision

The selected option is **A**. `SessionStateHolder` stays in `:shared` as the app-level
authentication orchestrator, alongside `SyncStateHolder`. The E2-05 acceptance criterion is amended
to record this, and `docs/CONTRACTS.md §20.10` documents the sign-out and account-deletion intents
in place.

## Consequences

### Positive

- No change to the dependency table that generates `architectureCheck`.
- The authentication orchestrator keeps its access to `:core:auth` and `:core:analytics` without a
  special-case rule.
- No Swift-facing declaration moves module, so no exported name is renamed by a module change.
  E2-05 does add members to the session surface, so the committed Objective-C golden header does
  change; what `D-155` preserves is that none of those changes is a move-induced rename.

### Negative

- The D-85 / ADR-0086 move of Session presentation is not applied; the Session holder remains in
  `:shared` rather than in its owning feature module.

### Constraints Introduced

- `SessionStateHolder` and its `SessionUiState` / `SessionPhase` types remain in `:shared`.
- `SyncStateHolder` remains the app-level state holder in `:shared`.
- Any future move of Session presentation MUST first resolve the `:core:auth` / `:core:analytics`
  dependency of `SessionStateHolder` against `docs/TECHNICAL_PLAN.md §4`.

## Verification

- `architectureCheck` passes with no change to the dependency table.
- The regenerated Objective-C golden header contains no renamed or relocated declaration. It does
  contain the members E2-05 adds — `SessionUiState.pendingSyncCount`,
  `SessionUiState.pendingDepartureRetry`, `DepartureRetry`, `startReauthentication(provider:)`
  and `retryDeparture()` — which are additions under `D-157`, `D-159` and the re-authentication
  flow, not consequences of where the state holder lives. An earlier revision of this ADR claimed
  the header was unchanged; that was false and is corrected here.

## References

- `docs/DECISION_BOARD.md` (decision ID `D-155`)
- `docs/SPECIFICATION.md §7 F-5`
- `docs/CONTRACTS.md §11.5`, `§20.10`
- `docs/TECHNICAL_PLAN.md §4`
- `docs/adr/0086-own-vehicle-presentation-in-feature-module.md`
- `docs/handoff-E2-05.md`
