# Decision Board - carApp MVP

> Review board for product and technical decisions. Accepted decisions must be reflected in ADRs, `SPECIFICATION.md`, `CONTRACTS.md`, `TECHNICAL_PLAN.md`, and `BACKLOG.md` when they affect implementation.

## Decision Status

- `Accepted`: selected for the MVP.
- `Deferred`: intentionally not part of the MVP but reserved for future use.
- `Pending`: requires owner decision before implementation.
- `Rejected`: explicitly not allowed for the MVP.

## Core Stack Decisions

| ID | Area | Accepted / Proposed Choice | Alternatives Reviewed | Status | Guardrail |
|----|------|----------------------------|-----------------------|--------|-----------|
| D-0 | Remote backend | Cloud Firestore | Supabase, AWS backend, custom API, local-only MVP | Accepted | Firestore is a remote replica behind `RemoteSyncSource`, never UI source of truth. |
| D-1 | Local database | Room 3 KMP | SQLDelight, Realm, DataStore-only | Accepted | Use `androidx.room3` and bundled SQLite. SQLDelight remains only a walking-skeleton fallback. |
| D-2 | iOS interop | SKIE | Raw KMP export, manual wrappers | Accepted | SKIE applies only to `:shared`. |
| D-3 | Dependency injection | Koin KMP | Manual DI, Metro, Kotlin Inject | Accepted | Koin is wiring only. No service locator access in domain/use cases/repositories. |
| D-4 | `fuelType` | Store on `Vehicle` from day one | Add later, store per fuel entry | Accepted | Persist default `GASOLINE`; no MVP UI selector. |
| D-5 | Firestore access | Firebase Firestore integration behind `RemoteSyncSource` | Ktor + Firestore REST, native SDK wrappers, custom API | Accepted | Initial MVP uses Firebase directly behind integration boundaries. Ktor is not required for MVP Firestore sync. |
| D-6 | Authentication | Firebase Auth behind `AuthClient` | Custom auth, native-only auth wrappers | Accepted | Auth provider types never cross `:integration:firebase-auth`. |
| D-7 | Navigation | Native per platform | Shared router, Compose Multiplatform navigation | Accepted | Android uses native Compose navigation; iOS uses SwiftUI navigation. |
| D-8 | Presentation | Shared KMP state holders | Native-only ViewModels, shared UI | Accepted | Shared state holders expose `StateFlow<UiState>` and intents. |
| D-9 | Firestore offline persistence | Disabled | Enabled Firestore SDK cache | Accepted | Room/outbox is the only offline model. |
| D-10 | Metrics | Firebase Analytics behind `AnalyticsTracker` | PostHog, custom metrics, none | Accepted | No analytics calls from domain/data. No personal fuel data in events. |
| D-11 | HTTP/API client | Ktor for future API-based remote implementations | Retrofit, native URLSession/OkHttp wrappers, no HTTP abstraction | Deferred | Do not add Ktor to the MVP until an HTTP API implementation is introduced. |

## Library Review Matrix

| Area | Recommended Library / Tool | Alternatives | Status | Notes |
|------|----------------------------|--------------|--------|-------|
| Coroutines and streams | `kotlinx.coroutines` + Flow | callbacks, Rx | Accepted | Required for KMP async and state streams. |
| Serialization | `kotlinx.serialization-json` | Moshi, manual JSON | Accepted | Required for outbox payloads and remote DTOs. |
| Date/time | Kotlin stdlib time plus `kotlinx-datetime` where needed | platform date APIs only | Accepted | UTC instants only in persistence. |
| Logging | Logger abstraction; implementation pending | Kermit, Napier, custom sinks | Pending | Recommend Kermit implementation, but keep abstraction mandatory. |
| Crash reporting | Firebase Crashlytics | Sentry, none | Pending | Recommend Phase 4, not Phase 0. |
| Flow testing | Turbine if compatible with selected KMP versions | manual collection | Pending | Decide during Phase 0 after version pinning. |
| Test assertions | `kotlin.test` baseline | Kotest | Accepted baseline | Keep tests simple for agent predictability. |
| Test doubles | Hand-written fakes | MockK, Mockative | Accepted baseline | Fakes are preferred for domain/sync. |
| Android background work | WorkManager | foreground-only sync | Accepted for Phase 3 | Platform trigger only; sync engine remains common. |
| iOS background work | BGTaskScheduler | foreground-only sync | Accepted for Phase 3 | Platform trigger only; validate capabilities in Phase 3. |
| Connectivity | `expect`/`actual` connectivity observer | Ktor-only detection, platform-only direct usage | Accepted | Platform-specific observer behind common interface. |
| Localization | Native Android/iOS resources | shared resource library | Pending | Native resources recommended because UI is native. |
| Image loading | Coil if image loading becomes necessary | SDWebImage, platform-native loaders, none | Deferred | Do not add image loading in the MVP unless a story needs it. If needed, Coil is the approved library. |
| Charts | None for MVP | Vico, Swift Charts | Rejected for MVP | Advanced charts are out of scope. |
| Secrets/config | Platform Firebase config files and CI secrets | committed secrets | Accepted | No secrets in repo. Firebase config allowed only if non-secret. |
| Static analysis | ktlint + detekt + Android lint | none, Spotless-only | Accepted | CI blocking checks in Phase 0. |
| Architecture checks | Custom Gradle/Kotlin checks first | Konsist, dependency-analysis plugin | Pending | Start custom; add library only if needed. |

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

Adding Ktor requires a new implementation story and an ADR update. Until then, agents must not add Ktor dependencies.

## Firebase Decoupling Rule

Firebase is an implementation detail. The following abstractions are mandatory:

- `AuthClient`
- `TokenProvider`
- `RemoteSyncSource`
- `AnalyticsTracker`
- `CrashReporter` if crash reporting is added

Firebase SDK, GitLive, or native Firebase types are allowed only inside integration/wiring modules.

## Pending Owner Decisions

| Area | Recommended Default | Decision Needed By |
|------|---------------------|--------------------|
| Logging implementation | Kermit | Phase 0 |
| Crash reporting | Firebase Crashlytics in Phase 4 | Phase 4 |
| Flow testing helper | Turbine if compatible | Phase 0 |
| Localization implementation | Native resources | Phase 1 UI |
| Architecture check library | Custom check first | E0-04 |
