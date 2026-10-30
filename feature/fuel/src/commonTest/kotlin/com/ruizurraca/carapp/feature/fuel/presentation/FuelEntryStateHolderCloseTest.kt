package com.ruizurraca.carapp.feature.fuel.presentation

import com.ruizurraca.carapp.core.common.AppError
import com.ruizurraca.carapp.core.common.Outcome
import com.ruizurraca.carapp.core.model.ConsumptionReport
import com.ruizurraca.carapp.core.model.EntityId
import com.ruizurraca.carapp.core.model.FuelEntry
import com.ruizurraca.carapp.core.model.FuelEntryListItem
import com.ruizurraca.carapp.core.testing.TestDispatcherProvider
import com.ruizurraca.carapp.feature.fuel.domain.CreateFuelEntryCommand
import com.ruizurraca.carapp.feature.fuel.domain.FuelEntryRepository
import com.ruizurraca.carapp.feature.fuel.domain.UpdateFuelEntryCommand
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * `E4-03` (`D-256`), `docs/CONTRACTS.md §14`: `close()` cancels the work a fuel entry holder owns.
 *
 * Each holder owns a child scope, so "owned work" is everything under it. `close()` must cancel that
 * work and leave the caller's job active, because the hosts close a holder while their own scope lives
 * on. These are characterization tests for behaviour that already exists; each has a mutation proof
 * recorded in `docs/handoffs/E4-03.md`.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class FuelEntryStateHolderCloseTest {
    @Test
    fun listCloseCancelsEveryCoroutineTheHolderStarted() =
        runTest {
            val callerJob = Job()
            val holder =
                createFuelEntryListStateHolder(
                    scope = CoroutineScope(callerJob + UnconfinedTestDispatcher(testScheduler)),
                    vehicleId = VEHICLE_ID.value,
                    repository = CloseOnlyFuelEntryRepository(),
                    dispatchers = TestDispatcherProvider(),
                )
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { holder.state.collect {} }
            advanceUntilIdle()
            assertTrue(
                callerJob.children.any { child -> child.isActive },
                "precondition: the holder runs work under the caller's job",
            )

            holder.close()
            advanceUntilIdle()

            assertTrue(
                callerJob.children.none { child -> child.isActive },
                "close() must cancel every coroutine the list holder started",
            )
            callerJob.cancel()
        }

    @Test
    fun listCloseLeavesTheCallerScopeActive() =
        runTest {
            val callerJob = Job()
            val holder =
                createFuelEntryListStateHolder(
                    scope = CoroutineScope(callerJob + UnconfinedTestDispatcher(testScheduler)),
                    vehicleId = VEHICLE_ID.value,
                    repository = CloseOnlyFuelEntryRepository(),
                    dispatchers = TestDispatcherProvider(),
                )

            holder.close()

            assertTrue(callerJob.isActive, "close() releases the holder, never the scope its caller owns")
            callerJob.cancel()
        }

    @Test
    fun formCloseCancelsEveryCoroutineTheHolderStarted() =
        runTest {
            val callerJob = Job()
            val holder =
                createForm(CoroutineScope(callerJob + UnconfinedTestDispatcher(testScheduler)))
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { holder.state.collect {} }
            advanceUntilIdle()
            assertTrue(
                callerJob.children.any { child -> child.isActive },
                "precondition: the holder runs work under the caller's job",
            )

            holder.close()
            advanceUntilIdle()

            assertTrue(
                callerJob.children.none { child -> child.isActive },
                "close() must cancel every coroutine the form holder started",
            )
            callerJob.cancel()
        }

    @Test
    fun formCloseLeavesTheCallerScopeActive() =
        runTest {
            val callerJob = Job()
            val holder = createForm(CoroutineScope(callerJob + UnconfinedTestDispatcher(testScheduler)))

            holder.close()

            assertTrue(callerJob.isActive, "close() releases the holder, never the scope its caller owns")
            callerJob.cancel()
        }

    private fun createForm(scope: CoroutineScope): FuelEntryFormStateHolder =
        createFuelEntryFormStateHolder(
            scope = scope,
            vehicleId = VEHICLE_ID.value,
            entryId = null,
            initialDateEpochMillis = 0L,
            initialOdometerKm = flowOf(0L),
            initialCurrencyCode = "EUR",
            settingsCurrencyCode = flowOf("EUR"),
            repository = CloseOnlyFuelEntryRepository(),
            dispatchers = TestDispatcherProvider(),
        )

    private class CloseOnlyFuelEntryRepository : FuelEntryRepository {
        private val entries = MutableStateFlow<Outcome<List<FuelEntryListItem>, AppError>>(Outcome.Ok(emptyList()))
        private val consumption =
            MutableStateFlow<Outcome<ConsumptionReport, AppError>>(
                Outcome.Ok(ConsumptionReport(emptyList(), 0, null, false)),
            )

        override fun observeFuelEntries(
            vehicleId: EntityId,
            includeDeleted: Boolean,
        ): Flow<Outcome<List<FuelEntryListItem>, AppError>> = entries

        override suspend fun getFuelEntry(id: EntityId): Outcome<FuelEntry?, AppError> = Outcome.Ok(null)

        override suspend fun createFuelEntry(command: CreateFuelEntryCommand): Outcome<EntityId, AppError> =
            Outcome.Ok(VEHICLE_ID)

        override suspend fun updateFuelEntry(command: UpdateFuelEntryCommand): Outcome<Unit, AppError> =
            Outcome.Ok(Unit)

        override suspend fun deleteFuelEntry(id: EntityId): Outcome<Unit, AppError> = Outcome.Ok(Unit)

        override fun observeConsumption(vehicleId: EntityId): Flow<Outcome<ConsumptionReport, AppError>> = consumption
    }

    private companion object {
        val VEHICLE_ID = EntityId("00000000-0000-4000-8000-000000000100")
    }
}
