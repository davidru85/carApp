# Shared Swift surface

The `Shared` framework is produced by `:composition:ios` and exports the Swift-facing declarations
owned by this module. Swift continues to consume it with `import Shared`.

Scaled fields use integers across the Kotlin/Swift boundary:

- `litersScaled`: litres multiplied by 1,000.
- `pricePerLiterScaled`: minor currency units per litre multiplied by 100,000.
- `totalCostMinor`: currency minor units; use the currency's supported minor-unit factor.
- `consumptionScaled` and `consumptionAverageScaled`: litres per 100 km multiplied by 1,000.
- `odometerKm`: whole kilometres.
- `dateEpochMillis`: UTC milliseconds from the Unix epoch.

Identifiers cross as lowercase canonical UUID strings and currency codes as ISO 4217 strings.
User-facing text remains in the native Android and iOS resource catalogues; `UiState` carries only
typed values and stable message codes.
