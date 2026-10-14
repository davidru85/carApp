@file:OptIn(kotlin.experimental.ExperimentalObjCRefinement::class)

package com.ruizurraca.carapp.core.common

import kotlin.native.HiddenFromObjC

/**
 * The synchronized entity kinds (`docs/CONTRACTS.md §20.3`). An entry's `name` is the outbox
 * `entityType` wire value and [collection] is its Firestore collection.
 *
 * This is the single Kotlin source of the wire value (`D-206`): it lives in `:core:common` so that
 * `:core:database`, which cannot depend on `:core:sync`, can use it. The SQL repeats the values as
 * literals, and `EntityTypeWireValueParityTest` in `build-logic` fails the build when they diverge.
 * It is Kotlin-only, so it stays out of the Swift-facing header.
 */
@HiddenFromObjC
enum class EntityType(
    val collection: String,
) {
    VEHICLE("vehicles"),
    FUEL_ENTRY("fuelEntries"),
}
