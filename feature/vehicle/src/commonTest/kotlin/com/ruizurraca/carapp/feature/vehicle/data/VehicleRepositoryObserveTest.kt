package com.ruizurraca.carapp.feature.vehicle.data

import app.cash.turbine.ReceiveTurbine
import app.cash.turbine.test
import com.ruizurraca.carapp.core.common.AppError
import com.ruizurraca.carapp.core.common.Outcome
import com.ruizurraca.carapp.core.model.EntityId
import com.ruizurraca.carapp.core.model.OwnerId
import com.ruizurraca.carapp.core.model.Vehicle
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull

class VehicleRepositoryObserveTest {
    @Test
    fun observationSwitchesToTheCurrentOwner() =
        runTest {
            withVehicleRepositoryTestScope(OwnerId("owner-a")) {
                seedVehicle(ownerId = OwnerId("owner-a"), name = "Alpha")
                seedVehicle(id = SECOND_VEHICLE_ID, ownerId = OwnerId("owner-b"), name = "Beta")

                repository.observeVehicles(includeDeleted = false).test {
                    assertEquals(listOf("Alpha"), awaitOk().map { it.name })

                    ownerContext.set(OwnerId("owner-b"))

                    assertEquals(listOf("Beta"), awaitOk().map { it.name })
                    cancelAndIgnoreRemainingEvents()
                }
            }
        }

    @Test
    fun includeDeletedControlsTombstoneVisibility() =
        runTest {
            withVehicleRepositoryTestScope(OwnerId("owner-a")) {
                seedVehicle(name = "Active")
                seedVehicle(id = SECOND_VEHICLE_ID, name = "Deleted", deletedAt = 3_000)

                val activeOnly =
                    assertIs<Outcome.Ok<List<com.ruizurraca.carapp.core.model.Vehicle>>>(
                        repository.observeVehicles(includeDeleted = false).first(),
                    )
                val withDeleted =
                    assertIs<Outcome.Ok<List<com.ruizurraca.carapp.core.model.Vehicle>>>(
                        repository.observeVehicles(includeDeleted = true).first(),
                    )

                assertEquals(listOf("Active"), activeOnly.value.map { it.name })
                assertEquals(listOf("Active", "Deleted"), withDeleted.value.map { it.name })
            }
        }

    @Test
    fun observeVehicleMapsAnExistingTombstone() =
        runTest {
            withVehicleRepositoryTestScope(OwnerId("owner-a")) {
                seedVehicle(deletedAt = 3_000)

                val result =
                    assertIs<Outcome.Ok<com.ruizurraca.carapp.core.model.Vehicle?>>(
                        repository.observeVehicle(EntityId(VEHICLE_ID)).first(),
                    )

                assertEquals(instantEpoch3Seconds, result.value?.deletedAt)
            }
        }

    @Test
    fun observeVehicleReturnsOkNullForAbsence() =
        runTest {
            withVehicleRepositoryTestScope {
                val result =
                    assertIs<Outcome.Ok<com.ruizurraca.carapp.core.model.Vehicle?>>(
                        repository.observeVehicle(EntityId(VEHICLE_ID)).first(),
                    )

                assertNull(result.value)
            }
        }
}

private suspend fun ReceiveTurbine<Outcome<List<Vehicle>, AppError>>.awaitOk(): List<Vehicle> =
    assertIs<Outcome.Ok<List<Vehicle>>>(awaitItem()).value

private val instantEpoch3Seconds = kotlin.time.Instant.fromEpochMilliseconds(3_000)
