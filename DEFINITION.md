# Project Definition - carApp

> Definition package v1.0. This document consolidates the product, technical, delivery, and agent coordination rules required to start implementation.

## 1. Summary

`carApp` is a cross-platform Android and iOS mobile app for tracking vehicle costs. The MVP is limited to fuel expenses. Users can create vehicles, log refueling events, review history, calculate real consumption, and keep their data available offline with background synchronization.

The repository is currently greenfield. There is no product code yet. The definition phase is complete enough to begin Phase 0.

## 2. MVP Objective

The MVP must let a user:

- Start without creating a permanent account.
- Create at least one vehicle.
- Log fuel entries quickly.
- Use all MVP features without network access.
- Review fuel entry history.
- See per-segment and average fuel consumption using the full-to-full method.
- Convert an anonymous account to Google or Apple without data loss.
- Recover synchronized data on another device.

Success metric: a user can create a vehicle and log fuel entries offline, obtain reliable average consumption after at least two valid full-to-full segments, then reconnect and synchronize without manual repair.

## 3. Normative Documents

| Document | Role |
|----------|------|
| `SPECIFICATION.md` | Normative source for product scope, domain model, business rules, flows, architecture, sync, and non-functional requirements. |
| `CONTRACTS.md` | Normative guardrail layer for API contracts, canonical data types, persistence formats, state machines, errors, and platform boundaries. |
| `DECISION_BOARD.md` | Review board for selected, pending, deferred, and rejected technical/library decisions. |
| `TECHNICAL_PLAN.md` | Closed technical decisions, module architecture, sync algorithm, risk mitigation, and verification plan. |
| `BACKLOG.md` | Agent-sized implementation stories with dependencies and acceptance criteria. |
| `AGENTS.md` | Operating contract for AI agents. |

Conflict order: `SPECIFICATION.md` > `CONTRACTS.md` > `DECISION_BOARD.md` > `TECHNICAL_PLAN.md` > `BACKLOG.md` > `DEFINITION.md` > `AGENTS.md`.

## 4. Product Principles

| ID | Principle | Operational rule |
|----|-----------|------------------|
| P1 | Minimal logging friction | Prefer fast entry, sensible defaults, and warnings over blocking when safe. |
| P2 | Always works | The local database is the user-facing source of truth. Connectivity is optional. |
| P3 | No entry barrier | Anonymous login is a first-class path and can be converted later. |
| P4 | Cloud provider portability | Firebase remains replaceable because it is isolated behind interfaces and integration modules. |

## 5. Functional Scope

### In Scope

- Onboarding.
- Anonymous authentication.
- Google sign-in on Android and iOS.
- Apple sign-in on iOS.
- Anonymous-to-permanent account conversion.
- Credential collision handling without automatic merge.
- Sign-out.
- Account deletion.
- Vehicle create, read, update, delete.
- Fuel entry create, read, update, delete.
- Fuel price and total cost derivation.
- Full-to-full fuel consumption calculation.
- Distance-weighted average consumption.
- Local-first persistence.
- Offline-first synchronization with Cloud Firestore.
- Discreet sync status indicator.
- Minimal settings: currency, unit preferences prepared for future expansion, language if not inherited from the system, session, account deletion, app version.
- Spanish and English localization.
- Accessibility for onboarding, vehicle creation, and fuel logging.

### Out of Scope

- Maintenance, insurance, inspections, taxes, tolls, parking, or other expenses.
- Advanced charts, analytics, and vehicle comparisons.
- CSV/PDF export.
- Receipt attachments and OCR.
- Reminders and notifications.
- Vehicle sharing between accounts.
- Widgets, Wear OS, watchOS, and web.
- Official fuel-price integrations.
- Firebase App Check.
- Automatic account merging after credential collision.
- Firestore real-time listeners.

Any out-of-scope request requires specification approval before implementation.

## 6. Actors

| Actor | Description |
|-------|-------------|
| Anonymous user | Uses the app immediately. Data is local and synchronized under an anonymous backend identity. If the user uninstalls before conversion, data loss is an accepted risk. |
| Authenticated user | Uses Google or Apple. Data can be recovered on another device. |

A user owns zero or more vehicles. A vehicle belongs to exactly one user. Shared ownership is not part of the MVP.

## 7. Domain Model

### Vehicle

Required fields:

- `id`: UUID string, generated on the client.
- `ownerId`: backend user ID.
- `name`: 1 to 40 characters after trimming, unique per user case-insensitively.
- `initialOdometer`: odometer value at vehicle creation, 0 to 2,000,000, editable only while the vehicle has no non-deleted fuel entries.
- `currentOdometer`: derived read model, max of `initialOdometer` and latest non-deleted fuel entry odometer.
- `fuelType`: enum, default `GASOLINE`, stored from day one but not exposed as an MVP selector.
- `createdAt`, `updatedAt`, `deletedAt`.
- `syncState`: local-only sync state.

Optional fields:

- `brand`: 0 to 40 characters.
- `model`: 0 to 40 characters.

### FuelEntry

Required fields:

- `id`: UUID string, generated on the client.
- `vehicleId`: vehicle foreign key.
- `date`: instant, defaults to now, cannot be more than 1 hour in the future.
- `odometer`: odometer at refueling time.
- `liters`: decimal with 3 fractional digits, greater than 0 and at most 500.
- `pricePerLiter`: decimal with 3 fractional digits, derived when needed.
- `totalCostMinor`: integer minor currency units, derived when needed.
- `currency`: ISO-4217 currency code.
- `isFullTank`: default `true`.
- `hasMissedEntries`: default `false`.
- `odometerInconsistent`: set when the odometer rule is violated but the user saves anyway.
- `createdAt`, `updatedAt`, `deletedAt`.
- `syncState`: local-only sync state.

Optional field:

- `notes`: 0 to 280 characters.

### UserSettings

- `currency`.
- `distanceUnit`, with `KM` used in the MVP and `MILES` prepared.
- `volumeUnit`, with `LITER` used in the MVP and `GALLON` prepared.

## 8. Business Rules

### R-1 Odometer Consistency

A fuel entry odometer should be strictly greater than the previous fuel entry odometer for the same vehicle by date, and greater than or equal to `initialOdometer`.

If the value is inconsistent, the app warns the user but allows saving. The entry is marked with `odometerInconsistent = true`. Any segment containing such an entry produces no consumption value.

### R-2 Price and Total Cost

The user provides any two of:

- `liters`
- `pricePerLiter`
- `totalCostMinor`

The third value is calculated:

```text
totalCostMinor = round(liters * pricePerLiter * 100)
pricePerLiter  = totalCostMinor / 100 / liters
liters         = totalCostMinor / 100 / pricePerLiter
```

Money is stored and calculated with integer minor units. `Float` and `Double` are prohibited for monetary values.

### R-3 Fuel Consumption

For a full-tank entry `E`, find the previous full-tank entry `P` for the same vehicle.

```text
segment     = entries where P.odometer < entry.odometer <= E.odometer
liters      = sum(segment.liters)
distanceKm  = E.odometer - P.odometer
consumption = liters / distanceKm * 100
```

A segment produces no consumption if:

- `P` does not exist.
- `E.isFullTank` is false.
- Any segment entry has `hasMissedEntries = true`.
- Any segment entry has `odometerInconsistent = true`.
- `distanceKm <= 0`.

Vehicle average consumption is:

```text
sum(validSegmentLiters) / sum(validSegmentDistanceKm) * 100
```

It is not the arithmetic mean of segment consumption values.

### R-4 Deletion

All synchronized deletions are logical tombstones. Deleting a vehicle tombstones its fuel entries. Local physical purge of synchronized tombstones can happen after 90 days.

## 9. Functional Flows

| ID | Flow | Expected result |
|----|------|-----------------|
| F-1 | First launch and authentication | User chooses sign-in or anonymous continuation, then is routed based on whether vehicles exist. |
| F-2 | First vehicle creation | User creates the first vehicle before using the app meaningfully. |
| F-3 | Fuel logging | Defaults are optimized for speed; save is local and immediate. |
| F-4 | Anonymous account conversion | Existing anonymous data remains attached after linking Google or Apple credentials. |
| F-5 | Sign-out and account deletion | Pending sync is handled explicitly; account deletion removes remote and local data after confirmation. |

## 10. Technical Stack

| Area | Decision |
|------|----------|
| Shared platform | Kotlin Multiplatform |
| Android UI | Jetpack Compose |
| iOS UI | SwiftUI |
| Shared presentation | Common state holders exposing `StateFlow<UiState>` and intent functions |
| iOS interop | SKIE, only in `:shared` |
| Build system | Gradle Kotlin DSL, version catalog, convention plugins |
| Local database | Room 3.0 KMP with `androidx.sqlite:sqlite-bundled` |
| Remote backend | Cloud Firestore |
| Authentication | Firebase Auth through GitLive 2.6.x |
| Metrics | Firebase Analytics behind `AnalyticsTracker` |
| Dependency injection | Koin KMP for wiring, constructor injection for implementation classes |
| Dates | `kotlinx-datetime` |
| Serialization | `kotlinx.serialization` |
| Quality | ktlint, detekt, tests, architecture checks in CI |

GitLive 3.0 alpha is explicitly out of scope for the MVP. Kotlin, SKIE, and Xcode versions must be pinned during Phase 0 and not upgraded during the MVP without a new decision.

## 11. Module Architecture

```text
build-logic/
gradle/libs.versions.toml

:core:model
:core:common
:core:database
:core:auth
:core:sync
:core:analytics
:core:testing

:integration:firebase-auth
:integration:firebase-firestore
:integration:firebase-analytics

:feature:vehicle
:feature:fuel
:feature:session

:shared
:wiring:firebase
:androidApp
iosApp/
firestore/
```

Each feature module contains internal `domain`, `data`, and `presentation` packages.

Rules:

- Feature `domain` packages depend only on `:core:model` and `:core:common`.
- Feature `data` packages can depend on their own `domain`, `:core:database`, and `:core:sync`.
- Feature `presentation` packages depend on their own `domain`, never on `data`.
- Features do not depend on other features.
- `:core:sync` does not depend on integrations.
- `:shared` does not depend on integrations.
- Only `:wiring:firebase` constructs Firebase implementations.
- Firebase, GitLive, Koin, and future Ktor implementation types never cross their allowed boundaries.

## 12. Local Persistence

Planned tables:

- `vehicle`
- `fuel_entry`
- `outbox`
- `sync_cursor`

Every synchronized entity includes:

- `id`
- `ownerId`
- `updatedAt`
- `serverUpdatedAt`
- `deleted`
- `syncState`
- `localRevision`

The outbox stores one full snapshot per touched entity, not one row per operation. `UNIQUE(entityType, entityId)` coalesces changes while preserving causal order through the original `seq`.

## 13. Offline-First Synchronization

Firestore is a remote replica. The UI observes only the local database.

Push:

- Read due outbox rows ordered by `seq`.
- Push vehicles before fuel entries.
- Write `set(snapshot + serverTimestamp())`.
- Re-read the remote document to obtain authoritative `updatedAt`.
- Confirm locally only if `outbox.localRevision == entity.localRevision`.
- Use exponential backoff with jitter for retryable failures.
- Mark validation failures as poisoned instead of retrying forever.

Pull:

- Use a per-entity cursor.
- Query from `max(0, cursor - 30 seconds)`.
- Page by `updatedAt` with a limit of 200.
- Include tombstones.
- Apply each page in one local transaction.
- Do not overwrite local data when an outbox row exists for the same entity.
- Advance cursor only after local apply succeeds.

Conflicts:

- Last-write-wins at whole-document level.
- Server timestamp is authoritative.
- Lexicographic `id` tie-breaker makes the order deterministic.
- Tombstones win over older updates.

Accepted limitation: two devices editing different fields of the same document concurrently can lose one whole-document update. This is acceptable for the MVP and must be documented.

## 14. Security

Firestore layout:

```text
users/{uid}/vehicles/{vehicleId}
users/{uid}/fuelEntries/{entryId}
users/{uid}/meta/settings
```

Security rules must ensure:

- Only `request.auth.uid == uid` can read or write under `users/{uid}`.
- Anonymous Firebase users are allowed.
- Writes must use `request.time` as `updatedAt`.
- Tests with the Firestore emulator prove user isolation and timestamp rejection.

## 15. Implementation Phases

### Phase 0 - Foundations

KMP bootstrap, base modules, convention plugins, architecture checks, ktlint, detekt, CI, and ADRs.

Gate: Android and iOS compile in CI and architecture violations fail with clear messages.

### Phase 0.5 - Walking Skeleton

One screen crosses native UI, shared state holder, Room, Firestore, and real anonymous auth.

Gate: data written on Android appears on iOS and vice versa. If Room KMP blocks iOS/KSP progress, switch to SQLDelight the same day.

### Phase 1 - Local Persistence

Vehicles, fuel entries, consumption calculation, and native UI without remote sync.

Gate: E1-05 is fully tested, performant, and human-reviewed.

### Phase 2 - Authentication

Common auth contract, Firebase Auth integration, anonymous login, Google, Apple, conversion, sign-out, and account deletion.

Gate: anonymous conversion preserves data and credential collision never destroys data without explicit confirmation.

### Phase 3 - Backend and Sync

Firestore rules, Firestore integration, sync engine, UI sync status, and provider decoupling proof.

Gate: deterministic convergence tests and Firestore emulator tests pass.

### Phase 4 - MVP Hardening

Settings, accessibility, i18n, performance, release builds, and store-readiness checklist.

Gate: release builds are installable and store requirements are complete.

## 16. Non-Functional Requirements

- Android `minSdk 26`.
- iOS 16+.
- Cold start to content under 2 seconds.
- Smooth scrolling with 1,000 fuel entries.
- Consumption calculation for 1,000 entries under 100 ms.
- Full MVP usability without network access.
- Spanish and English localization from day one.
- No hardcoded user-facing strings.
- WCAG AA color contrast.
- TalkBack and VoiceOver for F-1 through F-3.
- Build, tests, lint, and architecture checks on every PR.
- macOS CI runner from the first PR.
- In-app account deletion.

## 17. Tools and Services

Development:

- Kotlin Multiplatform.
- Gradle Kotlin DSL.
- Android Studio.
- Xcode.
- Firebase CLI.
- Firestore emulator.

Quality:

- ktlint.
- detekt.
- KMP unit tests.
- Android UI tests where appropriate.
- iOS build verification.
- Architecture tests.
- Firestore emulator rule tests.
- Deterministic sync simulation.

AI agent coordination:

- Assign one backlog story at a time.
- Include relevant specification sections in the prompt.
- Require acceptance criteria evidence.
- Require explicit risk and verification notes in every handoff.

## 18. Agent Operating Rules

- Work only on the assigned backlog story.
- Read the required docs before implementation.
- Preserve architecture boundaries.
- Add tests with code.
- Do not add dependencies without a documented decision.
- Do not use Firebase APIs outside integration and wiring modules.
- Do not duplicate business logic in SwiftUI or Compose.
- Do not let UI observe the network.
- Do not physically delete synchronized data except during explicit tombstone purge.
- Report files changed, tests run, decisions made, and residual risks.

## 19. Definition of Ready

A story is ready when:

- Acceptance criteria are verifiable.
- Dependencies are explicit.
- The story does not require an unresolved technical decision.
- Scope is within the MVP.
- Verification commands or checks are known.
- Data model changes include migration expectations.
- Human review gates are identified.

## 20. Definition of Done

A story is done when:

- Acceptance criteria pass.
- Relevant tests are added or updated.
- Lint is clean.
- Relevant builds pass.
- Architecture rules pass.
- MVP scope has not expanded.
- Documentation is updated if a decision, flow, or model changed.
- Residual risks are documented.
- Sync work includes convergence tests.
- Security work includes Firestore emulator tests.

## 21. Human Review Gates

Human review is mandatory for:

- Phase 0 closure.
- E0-07 walking skeleton.
- E1-05 consumption calculation.
- E3-01 Firestore security rules.
- E3-03 synchronization engine.
- Any change to stack, backend, auth, sync, module boundaries, money representation, or product scope.

## 22. Main Risks

| Risk | Impact | Mitigation |
|------|--------|------------|
| iOS/KMP/SKIE/Room toolchain friction | High | Early walking skeleton, macOS CI, pinned versions, SQLDelight fallback. |
| Silent data loss in sync | Critical | Common sync engine, convergence tests, deterministic simulation, debug screen. |
| Incorrect Firestore rules | Critical | Emulator tests before production deployment. |
| Scope creep | Medium | Explicit out-of-scope list and escalation rule. |
| Business logic duplicated in native UI | Medium | Shared state holders and architecture checks. |

## 23. Definition Phase Checklist

- [x] MVP objective and success metric defined.
- [x] In-scope and out-of-scope functionality defined.
- [x] Actors defined.
- [x] Domain model defined.
- [x] Business rules R-1 through R-4 defined.
- [x] Functional flows F-1 through F-5 defined.
- [x] Technical stack selected.
- [x] Module architecture defined.
- [x] Dependency rules defined.
- [x] Offline-first sync strategy defined.
- [x] Firestore security strategy defined.
- [x] Non-functional requirements defined.
- [x] Implementation backlog defined.
- [x] Human review gates defined.
- [x] Agent rules defined.
- [x] Definition of Ready and Definition of Done defined.
- [x] ADRs for accepted decisions materialized in `docs/adr/`.
- [x] Contractual guardrail layer materialized in `CONTRACTS.md`.
- [x] Library and technical decision board materialized in `DECISION_BOARD.md`.
- [ ] Version catalog created and versions pinned during Phase 0.
- [ ] Real CI commands validated during Phase 0.

The remaining unchecked items belong to Phase 0 because they require the project skeleton and actual tool versions.
