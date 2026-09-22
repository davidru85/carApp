# ADR-0186 - Enforce the iOS trigger ban as a source rule, not a Konsist fixture

## Status

Accepted

## Context

`docs/CONTRACTS.md §20.10` states that `PostWriteDebounce`, `ConnectivityRecovered` and `Periodic` are
fired exclusively by platform wiring and MUST NOT be invoked from Swift UI code, and it declared the
enforcement mechanism as "a Konsist fixture MUST ban … from any `iosMain` call site of
`SyncStateHolder.requestSync`". `E3-04` is the story that makes it executable, and doing so exposed
that the declared mechanism cannot implement the declared rule.

Two facts collide. The prohibited surface includes Swift: `iosApp/*.swift` is where Swift UI code
lives, and `§20.10` says "Swift UI code". Konsist parses Kotlin only, so a Konsist fixture scoped to
`iosMain` would leave the Swift host unchecked — the exact call site the rule exists to prevent. And
`E3-04` also makes `Periodic` reachable from the iOS *platform* side through `BGTaskScheduler`, which
is permitted, so the check must distinguish a `SyncStateHolder` call site (banned) from an
`AppGraph.syncController()` call site (required), rather than banning every `requestSync`.

## Options Considered

| Option | Benefits | Costs / Risks |
|--------|----------|---------------|
| Option A: implement the ban as a source rule over both iOS platform file kinds (`iosMain` Kotlin and `iosApp` Swift) with a failing fixture, and correct `§20.10` to name what is executable | The rule covers the surface it was written for, including Swift. It is a pure function over source text, so it has a real failing fixture, which `AGENTS.md` requires of every architecture rule. The correction makes the normative text say what the check does instead of promising a mechanism that cannot cover the surface | The parse is textual. A trigger named inside an unrelated string literal could be read as a call site, so comments are masked and the `StateHolder` receiver is required. The window around each call site is bounded, so a pathological single-line expression could escape it |
| Option B: implement the Konsist fixture exactly as declared, over `iosMain` only | No document change | Leaves `iosApp/*.swift` unchecked while `§20.10` says "Swift UI code", so the declared MUST is not enforced where it matters. It also cannot see the `AppGraph.syncController()` distinction, because Konsist would ban the route `§9.1` requires. A rule that does not fire on the offending shape is the failure mode `AGENTS.md` calls out explicitly |
| Option C: leave it unenforced and note it as debt | No work | `AGENTS.md` requires every architecture rule to have a failing fixture. Recording a MUST as unowned debt is the condition `E0-04` was created to end, and `§20.10` would keep declaring a check that does not exist |

## Decision

The selected option is: **Option A**.

`SwiftTriggerSurfaceRule` is a pure function over a `Map<path, source>` returning one violation per
offending call site. `SwiftTriggerSurfaceContractTest` proves it in seven directions: the real
repository passes, each of the three platform-owned triggers is rejected when requested from a
`SyncStateHolder` call site, `AppForeground` is accepted, a fully commented-out call site inside a
nested block comment is not a violation, and a real call site on a line that also holds a `//` inside
a string literal is still found. The last two pin the masker, which is `KotlinSourceText.code`, so the
rule cannot silently become broader or narrower than the contract it implements.

`docs/CONTRACTS.md §20.10` is corrected in the same change: it now requires an executable check that
covers both iOS platform file kinds, accepts `AppForeground` and `PullToRefresh`, and states that it is
not a Konsist fixture, with the reason (Konsist parses Kotlin only and this surface contains Swift).

The receiver test is an allowlist. A `syncController()` call site from platform composition is the
route `§9.1` requires — the iOS `BGTaskScheduler` handler requests `SyncTrigger.Periodic` exactly
that way — so it is the one receiver the rule accepts, and every other receiver carrying a
platform-owned trigger is a violation. A denylist of receiver names was rejected: a one-line alias
such as `let holder = model.syncStateHolder` hides the holder's name from the call site, so the
ban could be evaded by renaming a local.

## Consequences

### Positive

- The ban covers the surface `§20.10` names, Swift included, and it is proved to fire, so the
  requirement is enforced rather than declared.
- The receiver allowlist makes the rule express the actual distinction: a request on anything that
  is not the single in-process controller is banned, and the platform-wiring request on that
  controller is required.
- The contract text now says what is executable, so the next agent is not sent to build a mechanism
  that cannot cover the surface.

### Negative

- The rule is a textual source rule. Comments are masked to keep offsets and line numbers accurate,
  but it is not a Kotlin or Swift parser, so an unusual construct it does not recognise is treated as
  code.
- The trigger name is matched within a bounded window on either side of `requestSync`. A call site
  whose argument is assembled from unrelated fragments further than that window could escape it. No
  such call site exists, and the window is wide enough for the ordinary shapes including a
  multi-line argument list.
- `§20.10` is a gated document, so the correction is subject to the owner's review. It is a
  representational clarification of a checkable requirement, not a behaviour change.

### Constraints Introduced

- `PostWriteDebounce`, `ConnectivityRecovered` and `Periodic` MUST NOT be requested from a
  `SyncStateHolder` call site anywhere on the iOS platform boundary.
- `AppForeground` and `PullToRefresh` MUST remain permitted from the Swift surface.
- Platform wiring MUST request the platform-owned triggers on `SyncController`, never on a state
  holder.

## Verification

`SwiftTriggerSurfaceContractTest` holds the seven fixtures and runs on `:build-logic:convention:test`,
which is one of the required CI checks. The repository-wide direction is asserted against the real
`composition/ios/src/iosMain` and `iosApp` trees, so a real violation fails the build rather than
being caught in review. Evidence is in `docs/handoff-E3-04.md`.

## References

- `docs/DECISION_BOARD.md` (decision ID `D-185`)
- `docs/CONTRACTS.md §9.1` and `§20.10`
- `docs/adr/0182-make-the-sync-trigger-adapter-the-scheduling-port.md`
- `docs/BACKLOG.md` (`E3-04`)
