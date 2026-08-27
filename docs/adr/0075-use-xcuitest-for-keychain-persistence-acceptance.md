# ADR-0075 / D-74 - Use XCUITest for Keychain Persistence Acceptance

## Status

Accepted

## Context

D-71 restores normal simulator signing because Firebase Authentication persists its anonymous
session through the signed application's Keychain. Unit tests and an unsigned process cannot prove
that behavior. A UI test also cannot inspect the app's Keychain directly because its runner is a
separate process and must not receive a shared Keychain access group merely for test convenience.

The simulator retains Keychain entries between runs, so acceptance must begin from an erased
simulator and must demonstrate that the assertion fails when the application's retained-session
hydration is temporarily removed. Otherwise a green run could be consuming stale state or an
in-memory session.

## Options Considered

| Option | Benefits | Costs / Risks |
|--------|----------|---------------|
| Dedicated XCUITest target with bundle identifier `com.ruizurraca.carapp.uitests` | Drives the real signed app, observes only its UI and can terminate and relaunch the separate process automatically. | Adds a test-only target and identifier that must be excluded from every archive. |
| Manual simulator acceptance | Adds no target. | Does not close the automation loop and is not repeatable evidence. |
| Debug-only launch hook inside the host application | Avoids a separate test bundle. | Contaminates the application binary with test behavior and weakens what the test proves. |

## Decision

Add a dedicated XCUITest target with bundle identifier `com.ruizurraca.carapp.uitests`. It launches
the real Debug application, creates an anonymous session through accessible UI controls, calls
`terminate()` and then `launch()` on the same `XCUIApplication`, and asserts the retained session
only through the UI shown by the new app process.

The target has no Keychain sharing entitlement and the app's entitlements remain unchanged. The
test begins from an erased simulator. A temporary mutation that removes retained-session hydration
must produce a recorded red run before the unchanged production implementation produces green.

## Consequences

### Positive

- Acceptance exercises the signed application process and its real Firebase Auth Keychain path.
- A restart cannot pass from in-memory state.
- The automated evidence is repeatable without Apple Events, Accessibility control or host hooks.

### Negative

- The simulator must be erased and Firebase App Check needs a registered local debug token.
- UI acceptance is slower and more environment-sensitive than shared-code tests.

### Constraints Introduced

- `com.ruizurraca.carapp.uitests` is test-only and MUST NOT be embedded, archived or distributed.
- The UI-test target MUST NOT share a Keychain access group with the application.
- The host application MUST NOT gain a launch hook or test-only cleanup behavior.
- Acceptance MUST include `terminate()` followed by `launch()` in the same test method.
- A Release archive MUST prove the UI-test bundle is absent.

## Verification

- Repository contract tests enforce the exact identifier, target type, lack of Keychain sharing
  and Release exclusion.
- The persistence XCUITest is observed red after retained-session hydration is temporarily removed
  and green after the production implementation is restored.
- The simulator is erased before the run and shut down afterwards.
- A Release archive inventory contains the app and no `carAppUITests` bundle or test identifier.

## References

- `docs/DECISION_BOARD.md` (decision ID `D-74`)
- `docs/SPECIFICATION.md`
- `docs/TECHNICAL_PLAN.md`
- `docs/identifiers.md`
- `docs/versions-matrix.md`
- `docs/adr/0072-use-normal-ios-simulator-signing.md`
