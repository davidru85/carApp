# ADR-0049 - Walking Skeleton Owns Firestore Client Cache Configuration

## Status

Accepted

## Context

E3-01 previously repeated the requirement to disable Firestore offline persistence, but it creates
rules, indexes and emulator tests rather than an application client. E0-07 already owns the first
real Firestore client on Android and iOS and independently requires disabled persistence.

## Options Considered

| Option | Benefits | Costs / Risks |
|--------|----------|---------------|
| Verify disabled persistence in E0-07 | Tests the real client configuration on both app paths and keeps E3-01 focused. | The repeated E3-01 criterion must move. |
| Create `:integration:firebase-firestore` in E3-01 | Satisfies the repeated criterion in its original location. | Pulls GitLive and native Firebase linking into the rules story before a client exists. |

## Decision

E3-01 owns Firestore rules, indexes and emulator tests only. E0-07 owns the first executable
Firestore client configuration and proves that its cache uses memory rather than offline
persistence on the real application paths.

## Consequences

### Positive

- The security-rule pull request remains independent of client/native integration.
- The persistence decision is verified where the configured client is actually constructed.
- No provider module exists before its client behavior can be tested.

### Negative

- E3-01 no longer closes the duplicated client-configuration criterion itself.

### Constraints Introduced

- E3-01 MUST NOT create `:integration:firebase-firestore` merely to hold a future setting.
- E0-07 MUST provide executable evidence that Firestore uses an in-memory cache on Android and iOS.
- D-42 ordering remains unchanged: E3-06 and E3-01 both precede E0-07.

## Verification

- The E3-01 backlog criteria contain no client configuration.
- The E0-07 handoff must record the disabled-persistence test on both application paths.

## References

- `docs/DECISION_BOARD.md` (decision ID `D-48`)
- `docs/SPECIFICATION.md §9.1`
- `docs/adr/0010-disable-firestore-offline-persistence.md`
- `docs/BACKLOG.md` (`E3-01`, `E0-07`)
