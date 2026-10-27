# ADR-0258 / D-257 - Read Android Release Signing from an Out-of-Repository Properties File

## Status

Accepted

The owner selected option B on 2026-10-27.

## Context

`androidApp/build.gradle.kts` declares no signing configuration, so `:androidApp:assembleRelease`
produces an unsigned APK. The repository is public (`D-34`), and `docs/SECURITY.md` forbids committing
keystores, passwords and `keystore.properties`. `E4-10` needs a signed APK to register a certificate
fingerprint on the production Firebase Android app, and `E4-09` needs a definitive upload key; neither
may put the key material in the repository.

## Options Considered

| Option | Benefits | Costs / Risks |
|--------|----------|---------------|
| A. Commit a `keystore.properties` with paths and read the passwords from environment variables | One file explains the layout. | A committed file invites a committed password; the file itself is on the forbidden list. |
| B. Resolve an out-of-repository properties file through a Gradle property in `~/.gradle/gradle.properties` | Nothing secret is near the repository; CI and a fresh clone are unaffected; the release is signed only where the file exists. | A developer who wants a signed release must create two files and one Gradle property. |

## Decision

The selected option is **B**.

- `androidApp/build.gradle.kts` reads the Gradle property `carapp.releaseSigningProperties`. When the
  property is absent, or names a file that does not exist, **no** signing config is created and the
  release stays unsigned; nothing fails, so CI and a fresh clone are unchanged.
- The properties file holds `storeFile`, `storePassword`, `keyAlias` and `keyPassword`. With a PKCS12
  keystore the two passwords are equal.
- **The resolved file MUST lie outside the repository root.** The build fails if it does not, so a
  committed copy cannot become the signing source even by accident.
- The owner location is `~/carapp-secrets/keystore.properties`, next to the keystore, and
  `~/.gradle/gradle.properties` carries `carapp.releaseSigningProperties=/Users/<owner>/carapp-secrets/keystore.properties`.
- `keystore.properties` is added to `.gitignore` as a second barrier, with a failing `git check-ignore`
  fixture first.
- **Keystore lifecycle.** The interim, carApp-only keystore is created by the owner in `E4-10`, because
  only the production key restriction needs its SHA; the definitive upload key is generated before
  store publication in `E4-09` (`BL-J`). A dedicated key per app MUST be used, never one reused from
  another project.
- Secrets never enter the repository, a chat or a document; only certificate fingerprints are
  reportable, and they live in the production Firebase registration, not in the repository.

## Consequences

### Positive

- A signed release is possible on the owner's machine and impossible in CI, with no committed secret.
- A fresh clone and every required check behave exactly as before.

### Negative

- The first signed release needs three out-of-repository artefacts; the runbook documents them.
- Fingerprints must be re-registered whenever the key changes (`E4-09` swaps the interim key).

### Constraints Introduced

- The signing properties file MUST resolve outside the repository; the build enforces it.
- `keystore.properties` MUST stay ignored, and the ignore entry is guarded by a fixture.
- Swapping the key requires re-registering its SHA-1 and SHA-256 on the Firebase Android app, the
  Android API-key restriction and the Play Integrity registration (`E4-09`).

## Verification

- A guard fixture proves the build reads no signing config when the property is absent.
- A guard fixture proves a path inside the repository is rejected.
- `E4-10`'s evidence shows a signed APK whose `apksigner verify --print-certs` fingerprint matches the
  registered one.

## References

- `docs/DECISION_BOARD.md` (decision ID `D-257`); `D-34`, `D-41`, `D-238` (ADR-0239), `D-258`
  (ADR-0259), `D-260` (ADR-0261)
- `docs/SECURITY.md`
