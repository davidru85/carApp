# ADR-0072 / D-71 - Use Normal iOS Simulator Signing

## Status

Accepted

## Context

The E0-01 greenfield scaffold disabled code signing globally so command-line simulator builds
could not unexpectedly select a developer identity, prompt for Apple-account access or depend on
a developer team. That was appropriate while the iOS host contained only the placeholder
`Greeting` surface and used no Apple services.

E0-07 introduces Firebase Authentication and App Check. Firebase Auth persists its anonymous
session through the Apple Keychain. An unsigned simulator application cannot access the Keychain
correctly, so a test that passes only after globally disabling signing does not exercise the
production persistence mechanism. The original protection therefore blocks the behavior that the
walking-skeleton acceptance test is intended to prove.

## Options Considered

| Option | Benefits | Costs / Risks |
|--------|----------|---------------|
| Let Xcode perform normal simulator signing without a committed developer team or account-specific credential | Exercises real Keychain-backed Auth persistence and requires no Apple Events or developer account for simulator acceptance. | The build no longer has the E0-01 blanket safeguard against all signing activity. |
| Override signing only in the acceptance command | Preserves the checked-in global prohibition. | Makes the test command different from normal development and can still hide configuration regressions. |
| Keep the app unsigned and clear SQLDelight through a test hook without restarting | Avoids signing changes. | Tests application cleanup logic instead of Auth persistence and therefore cannot satisfy E0-07 acceptance. |

## Decision

Remove the project-wide `CODE_SIGNING_ALLOWED = NO`, blank `CODE_SIGN_IDENTITY` and
`CODE_SIGNING_REQUIRED = NO` settings. Let Xcode use its normal simulator signing behavior.

No development team, account-specific certificate or provisioning profile is committed. XcodeGen
may emit Xcode's generic `iPhone Developer` identity as part of normal automatic signing; it does
not identify an account or team. Physical-device and release signing remain outside this decision,
and release Firebase configuration continues to fail closed under D-54. Simulator acceptance is
driven only through command-line Xcode and Simulator tooling; Apple Events and Accessibility
control are forbidden.

## Consequences

### Positive

- Firebase Auth acceptance exercises the same Keychain persistence mechanism used by a signed
  production application.
- Normal local and CI simulator builds share the checked-in configuration.
- The test does not need a SQLDelight cleanup hook or other product-only behavior.

### Negative

- Future Xcode changes to simulator signing can now affect the build and must be caught by CI.

### Constraints Introduced

- The simulator build MUST NOT commit a development team or account-specific signing credential.
- Acceptance MUST restart the signed app, preserve the Keychain, remove only the local database
  files, and prove recovery with the same anonymous identity.
- Automation MUST NOT request Apple Events, Accessibility or general Mac-control permission.

## Verification

- A repository contract test rejects the former global signing prohibition.
- The generated Xcode project contains no globally disabled signing setting, account-specific
  credential or committed team.
- Command-line simulator acceptance proves the anonymous identity survives termination and local
  database deletion before the remote Vehicle is restored.

## References

- `docs/DECISION_BOARD.md` (decision ID `D-71`)
- `docs/SPECIFICATION.md`
- `docs/TECHNICAL_PLAN.md`
- `docs/adr/0055-keep-firebase-configuration-debug-only.md`
