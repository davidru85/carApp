# Application Identifiers

> **Owner-decided.** Agents MUST NOT invent, guess or "temporarily" change any value in this file. Several values are effectively irreversible once published or created. A separate production Firebase project is required before release, but its project identifier is deferred by `D-14` and MUST be decided before `E4-04`.
>
> Related decisions: `D-13` Firestore location, `D-14` Firebase project topology, `D-22` application identifiers.

## Status

| Value | Status |
|-------|--------|
| Application and bundle identifiers | Accepted |
| Development Firebase project ID | Accepted |
| Production Firebase project ID | Deferred — decide before `E4-04` release preparation |
| Firestore location | Accepted |

## Application

| Item | Value | Notes |
|------|-------|-------|
| Product name | `carApp` | Internal and development display name. The final store display name may still be reviewed in `E4-04`. |
| Android `applicationId` | `com.ruizurraca.carapp` | Immutable once published to Google Play. |
| Android namespace | `com.ruizurraca.carapp` | Kotlin/Java package root for `:androidApp`. |
| iOS bundle identifier | `com.ruizurraca.carapp` | Immutable once published to the App Store. |
| Shared module package root | `com.ruizurraca.carapp` | Sub-packages follow the module path, e.g. `com.ruizurraca.carapp.core.model`. |
| Android build namespace, per module | Derived — see "Module Android namespaces" below | AGP 9 requires every module with an Android target to declare a unique namespace. The value is derived from the module path, so it is never invented. |
| iOS framework name | `Shared` | Produced by `:shared` and consumed through SPM as `import Shared`; this is the canonical SPM module name. |
| Android `minSdk` / `targetSdk` | `26` / pinned in `docs/versions-matrix.md` | `minSdk` is fixed by `docs/SPECIFICATION.md §11`. |
| iOS deployment target | `16.0` | Fixed by `docs/SPECIFICATION.md §11`. |

### Module Android namespaces

Since AGP 9, every module with an Android target MUST declare an Android build namespace, and two
modules MUST NOT share one. The value is **derived, not chosen**:

> Android build namespace = the shared module package root, followed by the Gradle module path with
> `:` replaced by `.` and any `-` removed.

| Module | Android build namespace |
|--------|-------------------------|
| `:shared` | `com.ruizurraca.carapp.shared` |
| `:core:model` | `com.ruizurraca.carapp.core.model` |
| `:feature:fuel` | `com.ruizurraca.carapp.feature.fuel` |
| `:integration:firebase-auth` | `com.ruizurraca.carapp.integration.firebaseauth` |

Two consequences, both deliberate:

- **This is not the Kotlin package root.** Shared code keeps `com.ruizurraca.carapp` as its package
  root with sub-packages following the module path, exactly as the table above states. The Android
  namespace is a build identifier used for the generated `R` class and the merged manifest; a module
  whose Android namespace is `com.ruizurraca.carapp.shared` still declares Kotlin code in
  `com.ruizurraca.carapp`.
- **`:androidApp` is the exception** and keeps `com.ruizurraca.carapp`, the value fixed in the table
  above, because it is the application namespace.

Because the value is derived, an agent MUST NOT write a namespace literal in a module build script.
The convention plugins of `E0-02` compute it from the Gradle project path, which is what makes the
"agents MUST NOT invent identifiers" rule enforceable for the remaining modules.

Debug builds use the `.debug` application ID suffix on Android so debug and release can coexist on one device. iOS uses a separate bundle identifier suffix `.debug` with its own Firebase app registration.

## Firebase

| Item | Value | Notes |
|------|-------|-------|
| Development project ID | `davidruiz-carapp-dev` | Fixed by `D-32`. `carapp-dev`, originally chosen by `D-22`, is held by another Google Cloud customer and is unavailable; Google Cloud project IDs are globally unique. Used by debug builds and by manual testing during development. |
| Production project ID | Deferred until release preparation | A separate production Firebase project will be added before release; agents MUST NOT invent its project ID. Its availability MUST be checked before it is recorded as decided (`D-32`). |
| CI | Firestore emulator only | CI MUST NOT hold Firebase project credentials or write to a real project. |
| Firestore location | `europe-west1` (Belgium, EU single region) | **Immutable after database creation.** Chosen by the owner for the Spanish initial user base. |
| Firestore mode | Native mode | Not Datastore mode. |
| Registered apps in the development project | Android debug and iOS debug | Release app registrations are deferred until the production project is created. |

Configuration files are committed per `docs/SECURITY.md`, and the corresponding API keys MUST be restricted by package name, bundle identifier and signing certificate in the Google Cloud console.

## Repository

| Item | Value |
|------|-------|
| Git remote | `git@github.com:davidru85/carApp.git` |
| Default branch | `main` |
| Visibility | **Public** (`D-34`). Branch protection for `main` is active with the nine `docs/CONTRACTS.md §18` checks. Because the repository is public, the API keys in `google-services.json` and `GoogleService-Info.plist` are readable by anyone and MUST be restricted before those files are committed (`docs/SECURITY.md`). |
| Branch naming | `story/<STORY-ID>-<short-slug>` |

## Change policy

Changing any value in this file is a human review gate (`AGENTS.md`). Changing the `applicationId`, the bundle identifier or the Firestore location after `E0-07` requires a written migration plan, because the first two are irreversible after publication and the third is irreversible after the database is created.
