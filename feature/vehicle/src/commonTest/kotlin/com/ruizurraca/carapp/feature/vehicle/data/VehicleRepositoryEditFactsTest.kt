package com.ruizurraca.carapp.feature.vehicle.data

import app.cash.turbine.test
import com.ruizurraca.carapp.core.common.Outcome
import com.ruizurraca.carapp.core.model.EntityId
import com.ruizurraca.carapp.core.model.OwnerId
import com.ruizurraca.carapp.feature.vehicle.domain.VehicleEditFacts
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class VehicleRepositoryEditFactsTest {
    @Test
    fun editFactsReemitWhenAnActiveFuelEntryAppears() =
        runTest {
            withVehicleRepositoryTestScope(OwnerId("owner-a")) {
                seedVehicle()

                repository.observeVehicleEditFacts(EntityId(VEHICLE_ID)).test {
                    assertTrue(awaitFacts().canEditInitialOdometer)

                    seedFuelEntry(
                        id = FIRST_FUEL_ENTRY_ID,
                        date = 1_100,
                        odometerKm = 50,
                    )

                    assertFalse(awaitFacts().canEditInitialOdometer)
                    cancelAndIgnoreRemainingEvents()
                }
            }
        }

    @Test
    fun missingVehicleEmitsOkNull() =
        runTest {
            withVehicleRepositoryTestScope {
                repository.observeVehicleEditFacts(EntityId(VEHICLE_ID)).test {
                    assertNull(assertIs<Outcome.Ok<VehicleEditFacts?>>(awaitItem()).value)
                    cancelAndIgnoreRemainingEvents()
                }
            }
        }
}

private suspend fun app.cash.turbine.ReceiveTurbine<Outcome<VehicleEditFacts?, *>>.awaitFacts(): VehicleEditFacts =
    requireNotNull(assertIs<Outcome.Ok<VehicleEditFacts?>>(awaitItem()).value)
