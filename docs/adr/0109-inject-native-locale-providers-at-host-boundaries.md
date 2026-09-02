# ADR-0109 / D-108 - Inject Native Locale Providers at Host Boundaries

## Status

Accepted

Selected by the owner on 2026-09-01 during story E1-10.

## Context

E1-10 validates a platform-reported locale currency against its runtime minor-unit factor. Android
and iOS must use their native locale and currency APIs, while common code depends only on the
provider-free `LocaleProvider` port. Firebase and native platform types must not leak into shared
product logic.

The implementation needs a clear owner for the two small native adapters and an explicit path into
the existing Firebase composition factory. Production wiring must not silently select a staged
locale implementation.

## Options Considered

| Option | Benefits | Costs / Risks |
|--------|----------|---------------|
| Native host adapters injected into Firebase wiring | Keeps native APIs at platform composition edges; preserves provider-free common code; gives focused platform tests; makes production input explicit | Maintains two small adapters and adds one parameter to the production factory |
| Android and iOS source-set adapters owned by `:wiring:firebase` | Keeps host call sites smaller; could preserve a one-argument production factory | Makes Firebase wiring own an unrelated platform service; complicates provider-decoupling and source-set responsibilities |
| Hosts pass a one-time `LocaleInfo` snapshot | Minimizes adapter surface and avoids provider classes | Stops modelling locale as an injectable runtime boundary; duplicates resolution at call sites; cannot read a later locale when a new form is opened |

## Decision

Android and iOS hosts own native `LocaleProvider` adapters and inject them into Firebase wiring at
their platform composition boundaries. Common application code receives only the port.

The production `firebaseAppProviders(databaseFilePath, localeProvider)` overload keeps
`localeProvider` explicit and has no default value. A staged default may exist only on the internal
factory used by tests and incomplete provider wiring.

## Consequences

### Positive

- Android and Foundation APIs remain at their native host edges.
- The shared graph and product layers remain provider-free and easy to test.
- Production composition cannot accidentally use the staged locale fallback.
- Each platform adapter has a separate native minor-unit behavior boundary verified by the
  canonical command.

### Negative

- Both native hosts maintain a small adapter.
- The production provider call requires an additional explicit argument.

### Constraints Introduced

- Production Android and iOS composition MUST inject their native `LocaleProvider` adapters.
- `firebaseAppProviders(databaseFilePath, localeProvider)` MUST keep the locale parameter explicit
  with no default.
- Any default locale provider MUST remain confined to the internal staged/test overload.
- Native locale and currency API types MUST NOT cross the provider boundary into common code.

## Verification

- `:androidApp:testDebugUnitTest` validates Android native currency code and minor-unit extraction
  under the D-109 canonical local and CI route.
- `:shared:iosSimulatorArm64Test` compiles the exact composition-owned `IosLocaleProvider` source
  into its test compilation and executes Foundation `NSNumberFormatter.maximumFractionDigits`,
  non-two-decimal fallback, language-tag and region behavior. The provider remains internal to
  `:composition:ios`; the test adds no production project dependency or Swift export.
- `FirebaseAppProvidersTest` and provider-decoupling verification prove common/provider graph
  construction remains explicit and Firebase types stay isolated.

## References

- `docs/BACKLOG.md` E1-10
- `docs/CONTRACTS.md` §11.6, §13 and §20.0.1
- ADR-0056 / D-55
- ADR-0059 / D-58
- ADR-0060 / D-59
