package com.ruizurraca.carapp

/** Swift-facing application graph facade. State-holder accessors are added by E0-07 in place. */
class SwiftAppGraph {
    private var backingDependencies: AppGraphDependencies? = null

    constructor()

    internal constructor(dependencies: AppGraphDependencies) {
        backingDependencies = dependencies
    }

    internal val dependencies: AppGraphDependencies
        get() = checkNotNull(backingDependencies) { "SwiftAppGraph was not built from AppProviders" }

    fun vehicleListStateHolder(): VehicleListStateHolder = VehicleListStateHolder()

    fun vehicleFormStateHolder(vehicleId: String?): VehicleFormStateHolder = VehicleFormStateHolder(vehicleId)

    fun fuelEntryListStateHolder(vehicleId: String): FuelEntryListStateHolder = FuelEntryListStateHolder(vehicleId)

    fun fuelEntryFormStateHolder(
        vehicleId: String,
        entryId: String?,
    ): FuelEntryFormStateHolder = FuelEntryFormStateHolder(vehicleId, entryId)

    fun sessionStateHolder(): SessionStateHolder = SessionStateHolder()

    fun syncStateHolder(): SyncStateHolder = SyncStateHolder()

    fun close() = Unit
}
