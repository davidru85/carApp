package com.ruizurraca.carapp.feature.fuel.data

import com.ruizurraca.carapp.core.model.EntityId
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Instant

class SqlDelightFuelEntryLocalDataSourceTest {
    @Test
    fun limitedListProjectionKeepsNewestRowsInChronologicalOrder() =
        runTest {
            withFuelEntryRepositoryTestScope {
                seedVehicle()
                seedEntriesByDate()

                val rows =
                    localDataSourceWithLimit(3L)
                        .observeFuelEntryList(ownerContext.current, EntityId(VEHICLE_ID), includeDeleted = false)
                        .first()

                assertEquals(listOf("entry-3", "entry-4", "entry-5"), rows.map { it.id.value })
            }
        }

    @Test
    fun limitedConsumptionProjectionKeepsHighestOdometersInCalculationOrder() =
        runTest {
            withFuelEntryRepositoryTestScope {
                seedVehicle()
                seedEntriesByOdometer()

                val rows =
                    localDataSourceWithLimit(3L)
                        .observeConsumptionEntries(ownerContext.current, EntityId(VEHICLE_ID))
                        .first()

                assertEquals(listOf(300L, 400L, 500L), rows.map { it.odometerKm })
                assertEquals(listOf("entry-3", "entry-4", "entry-5"), rows.map { it.id.value })
            }
        }

    @Test
    fun listProjectionUsesChronologicalOrderAndExcludesOrphans() =
        runTest {
            withFuelEntryRepositoryTestScope {
                seedVehicle()
                seedFuelEntry(FIRST_ENTRY_ID, date = Instant.fromEpochMilliseconds(2_000L), odometerKm = 300L)
                seedFuelEntry(SECOND_ENTRY_ID, date = Instant.fromEpochMilliseconds(1_000L), odometerKm = 200L)
                seedFuelEntry(
                    THIRD_ENTRY_ID,
                    date = Instant.fromEpochMilliseconds(1_000L),
                    createdAt = Instant.fromEpochMilliseconds(500L),
                    odometerKm = 100L,
                )
                seedFuelEntry(
                    ORPHAN_ENTRY_ID,
                    vehicleId = SECOND_VEHICLE_ID,
                    date = Instant.fromEpochMilliseconds(500L),
                    odometerKm = 50L,
                )

                val rows =
                    localDataSource
                        .observeFuelEntryList(ownerContext.current, EntityId(VEHICLE_ID), includeDeleted = false)
                        .first()

                assertEquals(listOf(THIRD_ENTRY_ID, SECOND_ENTRY_ID, FIRST_ENTRY_ID), rows.map { it.id.value })
            }
        }

    @Test
    fun consumptionProjectionUsesCalculationOrderAndExcludesDeletedRows() =
        runTest {
            withFuelEntryRepositoryTestScope {
                seedVehicle()
                seedFuelEntry(FIRST_ENTRY_ID, date = Instant.fromEpochMilliseconds(1_000L), odometerKm = 300L)
                seedFuelEntry(SECOND_ENTRY_ID, date = Instant.fromEpochMilliseconds(3_000L), odometerKm = 100L)
                seedFuelEntry(THIRD_ENTRY_ID, date = Instant.fromEpochMilliseconds(2_000L), odometerKm = 200L)
                seedFuelEntry(
                    ORPHAN_ENTRY_ID,
                    date = Instant.fromEpochMilliseconds(4_000L),
                    odometerKm = 50L,
                    deletedAt = Instant.fromEpochMilliseconds(5_000L),
                )

                val rows =
                    localDataSource
                        .observeConsumptionEntries(ownerContext.current, EntityId(VEHICLE_ID))
                        .first()

                assertEquals(listOf(SECOND_ENTRY_ID, THIRD_ENTRY_ID, FIRST_ENTRY_ID), rows.map { it.id.value })
            }
        }

    @Test
    fun includeDeletedControlsListProjectionVisibility() =
        runTest {
            withFuelEntryRepositoryTestScope {
                seedVehicle()
                seedFuelEntry(FIRST_ENTRY_ID, date = ENTRY_DATE, odometerKm = 100L)
                seedFuelEntry(
                    SECOND_ENTRY_ID,
                    date = ENTRY_DATE,
                    odometerKm = 200L,
                    deletedAt = NOW,
                )

                val active =
                    localDataSource
                        .observeFuelEntryList(ownerContext.current, EntityId(VEHICLE_ID), includeDeleted = false)
                        .first()
                val all =
                    localDataSource
                        .observeFuelEntryList(ownerContext.current, EntityId(VEHICLE_ID), includeDeleted = true)
                        .first()

                assertEquals(listOf(FIRST_ENTRY_ID), active.map { it.id.value })
                assertEquals(listOf(FIRST_ENTRY_ID, SECOND_ENTRY_ID), all.map { it.id.value })
            }
        }

    private suspend fun FuelEntryRepositoryTestScope.seedEntriesByDate() {
        for (index in 1L..5L) {
            seedFuelEntry(
                id = "entry-$index",
                date = Instant.fromEpochMilliseconds(index * 1_000L),
                odometerKm = index * 100L,
            )
        }
    }

    private suspend fun FuelEntryRepositoryTestScope.seedEntriesByOdometer() {
        for (index in 1L..5L) {
            seedFuelEntry(
                id = "entry-$index",
                date = Instant.fromEpochMilliseconds((6L - index) * 1_000L),
                odometerKm = index * 100L,
            )
        }
    }
}
