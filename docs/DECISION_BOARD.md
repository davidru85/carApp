# Decision Board - carApp MVP

> **Sole registry of decision IDs (`D-n`)** and authoritative for which libraries, services and technical options are allowed. Accepted decisions MUST be reflected in an ADR and mirrored in `docs/SPECIFICATION.md §12` and `docs/TECHNICAL_PLAN.md §2`; `contract-check` asserts that mirrored decision IDs and statuses stay aligned. See `AGENTS.md` for authority and normative language.

## Decision Status

One vocabulary, shared with the ADR `Status` field. An ADR recording a deferral has `Status: Deferred`, not `Accepted`.

- `Proposed`: a recommendation is on the table and requires owner confirmation before the first story that depends on it starts.
- `Accepted`: selected for the MVP.
- `Deferred`: intentionally not part of the MVP but reserved for future use.
- `Pending`: no recommendation yet; requires an owner decision.
- `Rejected`: explicitly not allowed for the MVP.
- `Superseded`: replaced by a later decision, which MUST be named.

**Blocking rule:** a `Proposed` or `Pending` decision that any Ready story depends on is itself a blocking backlog item. A story that needs it is not Ready (`AGENTS.md`, Definition of Ready).

## Core Stack Decisions

| ID | Area | Choice | Alternatives Reviewed | Status | Guardrail |
|----|------|--------|-----------------------|--------|-----------|
| D-0 | Remote backend | Cloud Firestore | Supabase, AWS backend, custom API, local-only MVP | Accepted | Firestore is a remote replica behind `RemoteSyncSource`, never UI source of truth. |
| D-1 | Local database | Room 3 KMP | SQLDelight, Realm, DataStore-only | Accepted | `androidx.room3` with bundled SQLite. SQLDelight is a walking-skeleton fallback only, and adopting it requires a superseding ADR. |
| D-2 | iOS interop | SKIE | Raw KMP export, manual wrappers | Accepted | SKIE applies only to `:shared`. The Swift-facing surface obeys `docs/CONTRACTS.md §15.3`. |
| D-3 | Dependency injection | Koin KMP | Manual DI, Metro, Kotlin Inject | Accepted | Koin is wiring only. No service locator access in domain, use cases or repositories. |
| D-4 | `fuelType` | Store on `Vehicle` from day one | Add later, store per fuel entry | Accepted | Persist default `GASOLINE`; no MVP UI selector; metadata only. |
| D-5 | Firestore access | Firebase Firestore behind `RemoteSyncSource` | Ktor + Firestore REST, native SDK wrappers, custom API | Accepted | Firebase used directly behind integration boundaries. |
| D-6 | Authentication | Firebase Auth behind `AuthClient` | Custom auth, native-only wrappers | Accepted | Auth provider types never cross `:integration:firebase-auth`. |
| D-7 | Navigation | Native per platform | Shared router, Compose Multiplatform navigation | Accepted | No shared destination sealed class. |
| D-8 | Presentation | Shared KMP state holders | Native-only ViewModels, shared UI | Accepted | State holders expose `StateFlow<UiState>` and intents; `UiState` carries no user-facing text. |
| D-9 | Firestore offline persistence | Disabled | Enabled Firestore SDK cache | Accepted | Room and the outbox are the only offline model. |
| D-10 | Metrics | Firebase Analytics behind `AnalyticsTracker` | PostHog, custom metrics, none | Accepted | Closed `AnalyticsEvent` hierarchy; no analytics calls from domain or data; off by default. |
| D-11 | HTTP/API client | Ktor, for future API-based remote implementations | Retrofit, native URLSession/OkHttp wrappers, no HTTP abstraction | Deferred | Do not add Ktor to the MVP. |
| D-12 | Image loading | Coil, if image loading ever becomes necessary | SDWebImage, platform-native loaders, none | Deferred | No image loading dependency until a story requires it; Coil is then the only approved library. |
| D-13 | Firestore location | `europe-west1` single region | `eur3` European multi-region, `nam5` United States multi-region | Accepted | Firestore is a backup and sync replica only; Room is the source of truth. The location is immutable after database creation and must be verified before `E0-07` creates the database. |
| D-14 | Firebase project topology | One development Firebase project plus the local emulator; production topology deferred until release preparation | Two projects from day one, three projects with staging | Accepted | Development uses one real Firebase project for manual testing, CI uses the emulator only, and production Firebase topology MUST be decided before public release. |
| D-15 | Logging implementation | Kermit behind `Logger` | Napier, custom sinks, no implementation | Accepted | The `Logger` abstraction is mandatory regardless; Kermit never appears outside the sink implementation. |
| D-16 | Architecture checks | Konsist for package-level rules, custom Gradle check for module-level rules | Custom checks only, dependency-analysis plugin | Accepted | Gradle cannot express intra-module package rules, and features are one module each. Every rule needs a failing fixture test. |
| D-17 | Flow testing helper | Turbine | Manual collection | **Proposed** | Confirm compatibility during version pinning in `E0-06`. |
| D-18 | Coverage measurement | Kover with per-module thresholds | No measurement, JaCoCo | **Proposed** | `:core:model` and `:core:common` at least 90%, feature `domain` 85%, `:core:sync` 80%, enforced in CI. |
| D-19 | Result type | Custom `Outcome<T, E>` in `:core:common` | `kotlin.Result`, Arrow `Either`, exceptions | **Proposed** | `kotlin.Result` has a single type parameter. Arrow is rejected for MVP dependency surface. Declared in `docs/CONTRACTS.md §20.1`. |
| D-20 | Localization implementation | Native Android and iOS resources | Shared resource library (moko-resources) | **Proposed** | UI is native. `UiState` carries typed values only, so shared code never needs a string bundle. |
| D-21 | Crash reporting | Firebase Crashlytics | Sentry, none | Pending | Recommended for Phase 4, not Phase 0. |
| D-22 | Application identifiers | Fixed in `docs/identifiers.md` | — | Accepted | Agents MUST NOT invent an applicationId, bundle id, namespace, project name or region. Production Firebase project IDs are deferred by `D-14`. |

## Library Review Matrix

| Area | Library / Tool | Alternatives | Status | Notes |
|------|----------------|--------------|--------|-------|
| Coroutines and streams | `kotlinx.coroutines` + Flow | callbacks, Rx | Accepted | Required for KMP async and state streams. |
| Serialization | `kotlinx.serialization-json` | Moshi, manual JSON | Accepted | Required for outbox payloads and remote DTOs. |
| Date/time | `kotlinx-datetime` | platform date APIs only | Accepted | UTC instants only in persistence. The exact `Instant` package is pinned in `docs/versions-matrix.md`. |
| Logging | Kermit behind `Logger` | Napier, custom sinks | Accepted (D-15) | Abstraction mandatory; implementation swappable. Does not replace `AnalyticsTracker` or `CrashReporter`. |
| Crash reporting | Firebase Crashlytics | Sentry, none | Pending (D-21) | Phase 4. |
| Flow testing | Turbine | manual collection | Proposed (D-17) | Validate against the pinned coroutines version. |
| Test assertions | `kotlin.test` | Kotest | Accepted | Keep tests simple for agent predictability. |
| Test doubles | Hand-written fakes | MockK, Mockative | Accepted | Fakes are preferred for domain and sync. |
| Coverage | Kover | JaCoCo, none | Proposed (D-18) | Thresholds enforced in CI. |
| Android background work | WorkManager | foreground-only sync | Accepted for Phase 3 | Trigger only: it calls `SyncController.requestSync(reason)` and carries no scheduling policy. |
| iOS background work | BGTaskScheduler | foreground-only sync | Accepted for Phase 3 | Same constraint; a single task identifier. |
| Connectivity | `ConnectivityObserver` behind a common interface | Ktor-only detection, platform-only direct usage | Accepted | Injected, not `expect`/`actual` in public API. |
| Localization | Native Android/iOS resources | shared resource library | Proposed (D-20) | Native resources because UI is native. |
| Architecture checks | Konsist + custom Gradle check | custom only, dependency-analysis plugin | Accepted (D-16) | See D-16. |
| Charts | None for MVP | Vico, Swift Charts | Rejected for MVP | Advanced charts are out of scope. |
| Image loading | Coil | SDWebImage, platform-native | Deferred (D-12) | Only if a story requires it. |
| HTTP client | Ktor | Retrofit, URLSession | Deferred (D-11) | Only with an approved API implementation story. |
| Secrets/config | Platform Firebase config files and CI secrets | committed secrets | Accepted | See `docs/SECURITY.md` for the allowlist and denylist. |
| Static analysis | ktlint + detekt + Android lint | none, Spotless-only | Accepted | Config files committed in Phase 0. Baseline suppression files are FORBIDDEN. |

## Ktor Decision

Ktor is not part of the initial MVP dependency set while Firebase is the selected database backend.

The architecture must still support a future migration path:

```text
RemoteSyncSource
  -> FirebaseRemoteSyncSource        MVP implementation
  -> HttpApiRemoteSyncSource         Future Ktor implementation
  -> SupabaseRemoteSyncSource        Future option
  -> AwsRemoteSyncSource             Future option
```

Adding Ktor requires a new implementation story and an ADR update. Until then, agents MUST NOT add Ktor dependencies.

## Firebase Decoupling Rule

Firebase is an implementation detail. These abstractions are mandatory:

- `AuthClient`
- `TokenProvider`
- `RemoteSyncSource`
- `AnalyticsTracker`
- `CrashReporter`, if crash reporting is added

Firebase SDK, GitLive and native Firebase types are allowed only inside integration and wiring modules.

## Decisions Awaiting Owner Confirmation

Everything below is either `Proposed` (a recommendation is on the table) or `Pending` (no recommendation). A story named in `Needed by` MUST NOT start until its row is resolved.

| ID | Area | Recommendation | Needed by | Consequence if unresolved |
|----|------|----------------|-----------|---------------------------|
| D-19 | Result type | `Outcome<T, E>` | Before `E0-03` | Every public signature in `docs/CONTRACTS.md` depends on it. |
| D-17 | Flow testing helper | Turbine | Before `E0-05` | Falls back to hand-written collection helpers in `:core:testing`. |
| D-18 | Coverage | Kover | Before `E0-05` | "High coverage" stays unmeasurable. |
| D-20 | Localization | Native resources | Before `E1-07` | Shared code has no defined path to localized strings. |
| D-21 | Crash reporting | Crashlytics | Before `E4-04` | Non-blocking until release hardening. |
