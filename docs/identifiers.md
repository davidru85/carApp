# Application Identifiers

> **Owner-decided.** Agents MUST NOT invent, guess or "temporarily" change any value in this file. Every value below marked `Proposed` requires owner confirmation in `E0-00` before `E0-01` starts. Several of them are effectively irreversible once published or created.
>
> Related decisions: `D-13` Firestore location, `D-14` Firebase project topology, `D-22` application identifiers.

## Status

| Value | Status |
|-------|--------|
| Application and bundle identifiers | **Proposed** — confirm in `E0-00` |
| Firebase project IDs | **Proposed** — confirm in `E0-00` |
| Firestore location | Accepted |

## Application

| Item | Value | Notes |
|------|-------|-------|
| Product name | `carApp` | Working name. The store display name may differ and is decided in `E4-04`. |
| Android `applicationId` | `com.davidru85.carapp` | Immutable once published to Google Play. |
| Android namespace | `com.davidru85.carapp` | Kotlin/Java package root for `:androidApp`. |
| iOS bundle identifier | `com.davidru85.carapp` | Immutable once published to the App Store. |
| Shared module package root | `com.davidru85.carapp` | Sub-packages follow the module path, e.g. `com.davidru85.carapp.core.model`. |
| iOS framework name | `Shared` | Produced by `:shared` and consumed through SPM. |
| Android `minSdk` / `targetSdk` | `26` / pinned in `docs/versions-matrix.md` | `minSdk` is fixed by `docs/SPECIFICATION.md §11`. |
| iOS deployment target | `16.0` | Fixed by `docs/SPECIFICATION.md §11`. |

Debug builds use the `.debug` application ID suffix on Android so debug and release can coexist on one device. iOS uses a separate bundle identifier suffix `.debug` with its own Firebase app registration.

## Firebase

| Item | Value | Notes |
|------|-------|-------|
| Development project ID | `carapp-dev` | Used by debug builds and by manual testing. |
| Production project ID | `carapp-prod` | Used by release builds only. |
| CI | Firestore emulator only | CI MUST NOT hold production credentials or write to a real project. |
| Firestore location | `europe-west1` (Belgium, EU single region) | **Immutable after database creation.** Chosen by the owner for the Spanish initial user base. |
| Firestore mode | Native mode | Not Datastore mode. |
| Registered apps per project | Android debug, Android release, iOS debug, iOS release | Each with its own `google-services.json` / `GoogleService-Info.plist`. |

Configuration files are committed per `docs/SECURITY.md`, and the corresponding API keys MUST be restricted by package name, bundle identifier and signing certificate in the Google Cloud console.

## Repository

| Item | Value |
|------|-------|
| Git remote | `git@github.com:davidru85/carApp.git` |
| Default branch | `main` |
| Branch naming | `story/<STORY-ID>-<short-slug>` |

## Change policy

Changing any value in this file is a human review gate (`AGENTS.md`). Changing the `applicationId`, the bundle identifier or the Firestore location after `E0-07` requires a written migration plan, because the first two are irreversible after publication and the third is irreversible after the database is created.
