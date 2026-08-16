# ADR-0013 / D-12 - Use Coil If Image Loading Becomes Necessary

## Status

Accepted

## Context

The MVP currently has no confirmed requirement for remote image loading. Future UI work may require image loading for assets, vehicle images, or other visual content.

## Options Considered

| Option | Benefits | Costs / Risks |
|--------|----------|---------------|
| Coil | Kotlin-first image loading, strong Android/Compose fit, approved owner preference. | Should not be added before there is an actual image-loading story. |
| Platform-native loaders | Native fit per platform. | Duplicated behavior and configuration. |
| SDWebImage | Mature iOS image loading. | iOS-only and not aligned with Kotlin-first stack. |

## Decision

Do not add image loading dependencies until a backlog story requires them. If image loading is required, use Coil.

## Consequences

### Positive

- Avoids premature dependencies.
- Prevents agents from selecting competing image loaders.

### Negative

- Future image work must validate Coil behavior for the specific platform/UI target involved.

### Constraints Introduced

- Coil is the only approved image loading library.
- Image loading remains in UI/platform layers.
- Domain, data, and sync modules must not depend on Coil.

## Verification

- Version catalog does not include Coil until needed.
- Architecture checks prevent Coil outside approved UI/platform modules once introduced.
