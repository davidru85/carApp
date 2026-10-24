package com.ruizurraca.carapp

import com.ruizurraca.carapp.core.common.AppError
import com.ruizurraca.carapp.core.common.Outcome
import com.ruizurraca.carapp.core.common.PersistenceError
import com.ruizurraca.carapp.core.common.UnexpectedError
import com.ruizurraca.carapp.core.model.ConsumptionReport
import com.ruizurraca.carapp.core.model.CurrencyCode
import com.ruizurraca.carapp.core.model.EntityId
import com.ruizurraca.carapp.core.model.FuelEntry
import com.ruizurraca.carapp.core.model.FuelEntryListItem
import com.ruizurraca.carapp.core.model.FuelType
import com.ruizurraca.carapp.core.model.UserSettings
import com.ruizurraca.carapp.core.model.Vehicle
import com.ruizurraca.carapp.core.testing.RecordingCrashReporter
import com.ruizurraca.carapp.feature.fuel.domain.CreateFuelEntryCommand
import com.ruizurraca.carapp.feature.fuel.domain.FuelEntryRepository
import com.ruizurraca.carapp.feature.fuel.domain.MoneyInput
import com.ruizurraca.carapp.feature.fuel.domain.UpdateFuelEntryCommand
import com.ruizurraca.carapp.feature.session.domain.SettingsRepository
import com.ruizurraca.carapp.feature.session.domain.UpdateSettingsCommand
import com.ruizurraca.carapp.feature.vehicle.domain.CreateVehicleCommand
import com.ruizurraca.carapp.feature.vehicle.domain.UpdateVehicleCommand
import com.ruizurraca.carapp.feature.vehicle.domain.VehicleEditFacts
import com.ruizurraca.carapp.feature.vehicle.domain.VehicleRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Instant

/**
 * `E4-04` (`D-243`): the graph boundary of the holders that `docs/TECHNICAL_PLAN.md §4` keeps away
 * from `:core:crash`. Every repository call and every observed emission is offered to the policy.
 */
class NonFatalReportingRepositoriesTest {
    private val unexpected = UnexpectedError(origin = ":feature:vehicle", throwableClassName = "IllegalStateException")
    private val crashReporter = RecordingCrashReporter()
    private val nonFatals = NonFatalReporting(crashReporter)

    @Test
    fun everyVehicleRepositoryResultCarryingAnUnexpectedErrorIsRecorded() =
        runTest {
            val repository = NonFatalReportingVehicleRepository(FailingVehicleRepository(unexpected), nonFatals)

            repository.observeVehicles(includeDeleted = false).first()
            repository.observeVehicle(VEHICLE_ID).first()
            repository.observeVehicleEditFacts(VEHICLE_ID).first()
            repository.createVehicle(createVehicleCommand())
            repository.updateVehicle(updateVehicleCommand())
            repository.deleteVehicle(VEHICLE_ID)

            assertEquals(List(6) { unexpected }, crashReporter.nonFatals.map { it.first })
        }

    @Test
    fun everyFuelEntryRepositoryResultCarryingAnUnexpectedErrorIsRecorded() =
        runTest {
            val repository = NonFatalReportingFuelEntryRepository(FailingFuelEntryRepository(unexpected), nonFatals)

            repository.observeFuelEntries(VEHICLE_ID, includeDeleted = false).first()
            repository.getFuelEntry(ENTRY_ID)
            repository.createFuelEntry(createFuelEntryCommand())
            repository.updateFuelEntry(updateFuelEntryCommand())
            repository.deleteFuelEntry(ENTRY_ID)
            repository.observeConsumption(VEHICLE_ID).first()

            assertEquals(List(6) { unexpected }, crashReporter.nonFatals.map { it.first })
        }

    @Test
    fun everySettingsRepositoryResultCarryingAnUnexpectedErrorIsRecorded() =
        runTest {
            val repository = NonFatalReportingSettingsRepository(FailingSettingsRepository(unexpected), nonFatals)

            repository.settings.first()
            repository.updateSettings(UpdateSettingsCommand(currency = null, analyticsEnabled = true))

            assertEquals(List(2) { unexpected }, crashReporter.nonFatals.map { it.first })
        }

    @Test
    fun aTypedRepositoryErrorIsReturnedUnchangedAndNotRecorded() =
        runTest {
            val repository =
                NonFatalReportingVehicleRepository(
                    FailingVehicleRepository(PersistenceError.TransactionFailed),
                    nonFatals,
                )

            val result = repository.deleteVehicle(VEHICLE_ID)

            assertEquals(Outcome.Err(PersistenceError.TransactionFailed), result)
            assertEquals(emptyList(), crashReporter.nonFatals)
        }

    private fun createVehicleCommand() =
        CreateVehicleCommand(
            name = "Roadster",
            initialOdometerKm = 0,
            brand = null,
            model = null,
            confirmations = emptySet(),
        )

    private fun updateVehicleCommand() =
        UpdateVehicleCommand(
            id = VEHICLE_ID,
            name = "Roadster",
            initialOdometerKm = null,
            brand = null,
            model = null,
            fuelType = FuelType.GASOLINE,
            confirmations = emptySet(),
        )

    private fun createFuelEntryCommand() =
        CreateFuelEntryCommand(
            vehicleId = VEHICLE_ID,
            date = Instant.fromEpochMilliseconds(1_700_000_000_000),
            odometerKm = 2_000,
            money = MoneyInput.LitersAndTotal(litersScaled = 40_000, totalCostMinor = 6_000),
            currency = CurrencyCode("EUR"),
            isFullTank = true,
            hasMissedEntries = false,
            notes = null,
            confirmations = emptySet(),
        )

    private fun updateFuelEntryCommand() =
        UpdateFuelEntryCommand(
            id = ENTRY_ID,
            vehicleId = VEHICLE_ID,
            date = Instant.fromEpochMilliseconds(1_700_000_000_000),
            odometerKm = 2_000,
            money = MoneyInput.LitersAndTotal(litersScaled = 40_000, totalCostMinor = 6_000),
            currency = CurrencyCode("EUR"),
            isFullTank = true,
            hasMissedEntries = false,
            notes = null,
            confirmations = emptySet(),
        )

    private class FailingVehicleRepository(
        private val error: AppError,
    ) : VehicleRepository {
        override fun observeVehicles(includeDeleted: Boolean): Flow<Outcome<List<Vehicle>, AppError>> =
            flowOf(Outcome.Err(error))

        override fun observeVehicle(id: EntityId): Flow<Outcome<Vehicle?, AppError>> = flowOf(Outcome.Err(error))

        override fun observeVehicleEditFacts(id: EntityId): Flow<Outcome<VehicleEditFacts?, AppError>> =
            flowOf(Outcome.Err(error))

        override suspend fun createVehicle(command: CreateVehicleCommand): Outcome<EntityId, AppError> =
            Outcome.Err(error)

        override suspend fun updateVehicle(command: UpdateVehicleCommand): Outcome<Unit, AppError> = Outcome.Err(error)

        override suspend fun deleteVehicle(id: EntityId): Outcome<Unit, AppError> = Outcome.Err(error)
    }

    private class FailingFuelEntryRepository(
        private val error: AppError,
    ) : FuelEntryRepository {
        override fun observeFuelEntries(
            vehicleId: EntityId,
            includeDeleted: Boolean,
        ): Flow<Outcome<List<FuelEntryListItem>, AppError>> = flowOf(Outcome.Err(error))

        override suspend fun getFuelEntry(id: EntityId): Outcome<FuelEntry?, AppError> = Outcome.Err(error)

        override suspend fun createFuelEntry(command: CreateFuelEntryCommand): Outcome<EntityId, AppError> =
            Outcome.Err(error)

        override suspend fun updateFuelEntry(command: UpdateFuelEntryCommand): Outcome<Unit, AppError> =
            Outcome.Err(error)

        override suspend fun deleteFuelEntry(id: EntityId): Outcome<Unit, AppError> = Outcome.Err(error)

        override fun observeConsumption(vehicleId: EntityId): Flow<Outcome<ConsumptionReport, AppError>> =
            flowOf(Outcome.Err(error))
    }

    private class FailingSettingsRepository(
        private val error: AppError,
    ) : SettingsRepository {
        override val settings: Flow<Outcome<UserSettings, AppError>> = flowOf(Outcome.Err(error))

        override suspend fun updateSettings(command: UpdateSettingsCommand): Outcome<Unit, AppError> =
            Outcome.Err(error)
    }

    private companion object {
        val VEHICLE_ID = EntityId("c0ffee00-0000-4000-8000-000000000001")
        val ENTRY_ID = EntityId("c0ffee00-0000-4000-8000-000000000002")
    }
}
