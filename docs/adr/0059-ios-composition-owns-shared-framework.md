# ADR-0059 / D-58 - iOS Composition Owns the Shared Framework

## Status

Accepted

## Context

`:shared` defines the provider-free application graph and the Swift-facing state holders, while
`:wiring:firebase` is the only module allowed to construct Firebase providers and depends on
`:shared`. Making `:shared` construct Firebase wiring would introduce a Gradle dependency cycle.

The iOS application must still link exactly one Kotlin/Native framework named `Shared`, and SKIE
must process the declarations exported from `:shared` without changing Swift's `import Shared`.

## Options Considered

| Option | Benefits | Costs / Risks |
|--------|----------|---------------|
| Add a thin `:composition:ios` framework module | Keeps `:shared` provider-free, avoids the cycle, centralizes platform composition and produces one Kotlin/Native runtime. | Adds one deliberately narrow module and requires Xcode/CI path changes. |
| Allow `:shared` to depend on Firebase or wiring | Keeps the original framework owner. | Creates a Gradle cycle, breaks provider-free testing and violates provider separation. |
| Link separate shared and wiring frameworks with prior registration | Avoids a compile-time dependency cycle. | Replaces it with runtime ordering, global state, symbol-collision risk and duplicate Kotlin/Native runtimes. |

## Decision

Create `:composition:ios` as the iOS composition root. It applies SKIE, produces the only static
Kotlin/Native framework with `baseName = "Shared"`, declares `api(project(":shared"))`, exports
`:shared` and declares `implementation(project(":wiring:firebase"))`.

`:shared` retains `SwiftAppGraph`, state holders, models, `AppProviders` and the provider-free
`buildAppGraph(isDebugBuild, providers)` function, but no longer declares a framework binary or
applies SKIE. `:composition:ios` owns the single exported
`createSwiftAppGraph(isDebugBuild)` factory and delegates without product logic.

D-58 supersedes the module-ownership part of D-2. SKIE itself remains accepted.

## Consequences

### Positive

- The Gradle graph remains acyclic.
- Provider-free tests exercise the real `buildAppGraph` entry point using fakes.
- Wiring modules remain symmetric and do not own platform framework production.
- Swift keeps the stable `Shared` framework and module name.
- Exactly one Kotlin/Native runtime is linked into the iOS application.

### Negative

- Xcode, CI and golden-header generation must use `:composition:ios` build outputs.
- `:composition:ios` must be excluded with Firebase provider modules during provider-free builds.

### Constraints Introduced

- Only `:composition:ios` may apply SKIE or declare the `Shared` framework binary.
- `:composition:ios` may depend only on `:shared` and `:wiring:firebase` and contains no product
  logic.
- `createSwiftAppGraph(isDebugBuild)` MUST occur exactly once in the exported graph.
- No global provider registry, service locator or second Kotlin/Native framework may be added.

## Verification

- Gradle functional tests enforce framework and SKIE ownership.
- Provider-free Android host and iOS simulator tests compile `:shared` without composition or
  Firebase provider modules.
- Device and simulator frameworks link from `:composition:ios` with `baseName = "Shared"`.
- Xcode embeds one `Shared.framework` and the generated Objective-C header matches its golden.

## References

- `docs/adr/0003-ios-interop-skie.md` (`D-2`)
- `docs/adr/0043-provider-decoupling-precedes-first-integration.md` (`D-42`)
- `docs/CONTRACTS.md §11.6`, `§15.3`, `§20.10`
- `docs/BACKLOG.md` (`E0-07`)
