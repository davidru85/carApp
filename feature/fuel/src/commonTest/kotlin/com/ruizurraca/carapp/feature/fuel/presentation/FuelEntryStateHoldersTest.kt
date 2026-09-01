package com.ruizurraca.carapp.feature.fuel.presentation

import com.ruizurraca.carapp.core.common.AppError
import com.ruizurraca.carapp.core.common.Confirmation
import com.ruizurraca.carapp.core.common.Outcome
import com.ruizurraca.carapp.core.common.PersistenceError
import com.ruizurraca.carapp.core.common.SyncStatus
import com.ruizurraca.carapp.core.common.UiMessageKind
import com.ruizurraca.carapp.core.common.ValidationError
import com.ruizurraca.carapp.core.common.ValidationWarning
import com.ruizurraca.carapp.core.model.ConsumptionInvalidReason
import com.ruizurraca.carapp.core.model.ConsumptionL100Km
import com.ruizurraca.carapp.core.model.ConsumptionReport
import com.ruizurraca.carapp.core.model.CurrencyCode
import com.ruizurraca.carapp.core.model.EntityId
import com.ruizurraca.carapp.core.model.FuelEntry
import com.ruizurraca.carapp.core.model.FuelEntryListItem
import com.ruizurraca.carapp.core.model.OwnerId
import com.ruizurraca.carapp.core.testing.TestDispatcherProvider
import com.ruizurraca.carapp.feature.fuel.domain.CreateFuelEntryCommand
import com.ruizurraca.carapp.feature.fuel.domain.FuelEntryRepository
import com.ruizurraca.carapp.feature.fuel.domain.MoneyInput
import com.ruizurraca.carapp.feature.fuel.domain.UpdateFuelEntryCommand
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
class FuelEntryStateHoldersTest {
    @Test
    fun listMapsRepositoryRowsAndConsumptionSummary() =
        runTest {
            val repository = FakeFuelEntryRepository()
            val holder = createCollectedListHolder(repository)
            advanceUntilIdle()

            assertEquals(VEHICLE_ID, repository.observedVehicleId)
            assertFalse(repository.observedIncludeDeleted)
            assertFalse(holder.state.value.isLoading)
            assertEquals(SyncStatus.Idle, holder.state.value.syncStatus)

            repository.entries.value = Outcome.Ok(listOf(listItem()))
            repository.consumption.value =
                Outcome.Ok(
                    ConsumptionReport(
                        segments = emptyList(),
                        validSegmentCount = 2,
                        average = ConsumptionL100Km(725L),
                        isReliable = true,
                    ),
                )
            advanceUntilIdle()

            val populated = holder.state.value
            assertEquals(725L, populated.consumptionAverageScaled)
            assertEquals(2, populated.validConsumptionSegmentCount)
            assertTrue(populated.isConsumptionReliable)
            with(populated.entries.single()) {
                assertEquals(ENTRY_ID.value, id)
                assertEquals(NOW.toEpochMilliseconds(), dateEpochMillis)
                assertEquals(456L, odometerKm)
                assertEquals(40_000L, litersScaled)
                assertEquals(6_000L, totalCostMinor)
                assertEquals("EUR", currencyCode)
                assertFalse(isFullTank)
                assertNull(consumptionScaled)
                assertEquals(ConsumptionInvalidReason.EndEntryNotFullTank, invalidReason)
                assertTrue(hasMissedEntries)
                assertTrue(odometerInconsistent)
            }
        }

    @Test
    fun listPublishesRepositoryErrorsAndHandlesDeleteOutcomes() =
        runTest {
            val repository = FakeFuelEntryRepository()
            val holder = createCollectedListHolder(repository)
            advanceUntilIdle()

            holder.requestDelete(ENTRY_ID.value)
            advanceUntilIdle()
            assertEquals(
                UiMessageKind.WARNING,
                holder.state.value.message
                    ?.kind,
            )
            assertEquals(
                "INFO.CONFIRM_DELETE_FUEL_ENTRY",
                holder.state.value.message
                    ?.code,
            )

            holder.confirmDelete(OTHER_ENTRY_ID.value)
            assertTrue(repository.deletedIds.isEmpty())

            repository.deleteResult = Outcome.Err(PersistenceError.TransactionFailed)
            holder.confirmDelete(ENTRY_ID.value)
            advanceUntilIdle()
            assertEquals(listOf(ENTRY_ID), repository.deletedIds)
            assertEquals(
                "PERSISTENCE.TRANSACTION_FAILED",
                holder.state.value.message
                    ?.code,
            )

            holder.refresh()
            assertNull(holder.state.value.message)
            repository.deleteResult = Outcome.Ok(Unit)
            holder.requestDelete(OTHER_ENTRY_ID.value)
            holder.confirmDelete(OTHER_ENTRY_ID.value)
            advanceUntilIdle()
            assertEquals(listOf(ENTRY_ID, OTHER_ENTRY_ID), repository.deletedIds)
            assertNull(holder.state.value.message)

            repository.entries.value = Outcome.Err(PersistenceError.DatabaseUnavailable)
            advanceUntilIdle()
            assertEquals(
                "PERSISTENCE.DATABASE_UNAVAILABLE",
                holder.state.value.message
                    ?.code,
            )
            repository.entries.value = Outcome.Ok(emptyList())
            repository.consumption.value = Outcome.Err(PersistenceError.MigrationFailed)
            advanceUntilIdle()
            assertEquals(
                "PERSISTENCE.MIGRATION_FAILED",
                holder.state.value.message
                    ?.code,
            )

            holder.clearMessage()
            holder.close()
            holder.close()
            holder.refresh()
            holder.requestDelete(ENTRY_ID.value)
            holder.confirmDelete(ENTRY_ID.value)
            holder.clearMessage()
        }

    @Test
    fun creationFormSupportsAllMoneyModesAndPreservesEditedOdometer() =
        runTest {
            val repository = FakeFuelEntryRepository()
            val odometer = MutableStateFlow(100L)
            val holder = createForm(backgroundScope, repository, odometer = odometer)
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { holder.state.collect {} }
            advanceUntilIdle()

            assertEquals(100L, holder.state.value.odometerKm)
            odometer.value = 200L
            advanceUntilIdle()
            assertEquals(200L, holder.state.value.odometerKm)

            holder.setDateEpochMillis(NOW.toEpochMilliseconds() + 1_000L)
            holder.setOdometerKm(300L)
            odometer.value = 400L
            holder.setLitersScaled(40_000L)
            holder.setPricePerLiterScaled(1_500L)
            advanceUntilIdle()
            assertEquals(300L, holder.state.value.odometerKm)
            assertEquals(6_000L, holder.state.value.totalCostMinor)

            holder.setMoneyInputMode(MoneyInputMode.LITERS_AND_TOTAL)
            holder.setLitersScaled(30_000L)
            holder.setTotalCostMinor(6_000L)
            assertEquals(2_000L, holder.state.value.pricePerLiterScaled)

            holder.setMoneyInputMode(MoneyInputMode.PRICE_AND_TOTAL)
            holder.setPricePerLiterScaled(2_000L)
            holder.setTotalCostMinor(5_000L)
            assertEquals(25_000L, holder.state.value.litersScaled)

            holder.setCurrencyCode("JPY")
            assertEquals(25_000L, holder.state.value.litersScaled)
            holder.setCurrencyCode("EUR")
            holder.setMoneyInputMode(MoneyInputMode.LITERS_AND_PRICE)
            holder.setLitersScaled(null)
            holder.setPricePerLiterScaled(null)
            holder.setFullTank(false)
            holder.setMissedEntries(true)
            holder.setNotes("draft")
            holder.save()
            advanceUntilIdle()

            val invalid = holder.state.value
            assertEquals("VALIDATION.INVALID_MONEY_INPUT", invalid.message?.code)
            assertEquals(UiMessageKind.ERROR, invalid.message?.kind)
            assertFalse(invalid.isFullTank)
            assertTrue(invalid.hasMissedEntries)
            assertEquals("draft", invalid.notes)

            holder.clearMessage()
            assertNull(holder.state.value.message)
            val beforeClose = holder.state.value
            holder.close()
            holder.close()
            holder.setDateEpochMillis(0L)
            holder.setOdometerKm(0L)
            holder.setNotes("ignored")
            holder.save()
            holder.confirmSave(Confirmation.OdometerInconsistent)
            holder.clearMessage()
            assertEquals(beforeClose, holder.state.value)
        }

    @Test
    fun persistedCurrencyReplacesLocaleFallbackOnANewForm() =
        runTest {
            val settingsCurrency = MutableSharedFlow<String>()
            val holder =
                createForm(
                    scope = backgroundScope,
                    repository = FakeFuelEntryRepository(),
                    settingsCurrencyCode = settingsCurrency,
                )
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { holder.state.collect {} }

            assertEquals("EUR", holder.state.value.currencyCode)

            settingsCurrency.emit("GBP")
            advanceUntilIdle()

            assertEquals("GBP", holder.state.value.currencyCode)
        }

    @Test
    fun explicitCurrencyEditBeforePersistedValueArrivesIsNotOverwritten() =
        runTest {
            val settingsCurrency = MutableSharedFlow<String>()
            val holder =
                createForm(
                    scope = backgroundScope,
                    repository = FakeFuelEntryRepository(),
                    settingsCurrencyCode = settingsCurrency,
                )
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { holder.state.collect {} }

            holder.setCurrencyCode("USD")
            settingsCurrency.emit("GBP")
            advanceUntilIdle()

            assertEquals("USD", holder.state.value.currencyCode)
        }

    @Test
    fun existingEntryCurrencyIsNeverReplacedBySettings() =
        runTest {
            val holder =
                createForm(
                    scope = backgroundScope,
                    repository = FakeFuelEntryRepository(getResult = Outcome.Ok(fuelEntry())),
                    entryId = ENTRY_ID.value,
                    settingsCurrencyCode = flowOf("GBP"),
                )
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { holder.state.collect {} }

            advanceUntilIdle()

            assertEquals("EUR", holder.state.value.currencyCode)
        }

    @Test
    fun laterSettingsChangesDoNotMutateAnOpenCreationForm() =
        runTest {
            val settingsCurrency = MutableStateFlow("GBP")
            val holder =
                createForm(
                    scope = backgroundScope,
                    repository = FakeFuelEntryRepository(),
                    settingsCurrencyCode = settingsCurrency,
                )
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { holder.state.collect {} }
            advanceUntilIdle()
            assertEquals("GBP", holder.state.value.currencyCode)

            settingsCurrency.value = "USD"
            advanceUntilIdle()

            assertEquals("GBP", holder.state.value.currencyCode)
        }

    @Test
    fun modeSwitchReDerivesWithTheAcceptedOneScaleUnitRoundingDrift() =
        runTest {
            val holder = createForm(backgroundScope, FakeFuelEntryRepository())
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { holder.state.collect {} }
            holder.setLitersScaled(40_001L)
            holder.setPricePerLiterScaled(1_549L)
            advanceUntilIdle()

            assertEquals(6_196L, holder.state.value.totalCostMinor)

            holder.setMoneyInputMode(MoneyInputMode.PRICE_AND_TOTAL)
            advanceUntilIdle()

            with(holder.state.value) {
                assertEquals(40_000L, litersScaled)
                assertEquals(1_549L, pricePerLiterScaled)
                assertEquals(6_196L, totalCostMinor)
            }
        }

    @Test
    fun modeSwitchKeepsAllValuesWhenTheParticipatingPairIsPresent() =
        runTest {
            val holder = createForm(backgroundScope, FakeFuelEntryRepository())
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { holder.state.collect {} }
            holder.setLitersScaled(40_000L)
            holder.setPricePerLiterScaled(1_500L)
            advanceUntilIdle()

            holder.setMoneyInputMode(MoneyInputMode.LITERS_AND_TOTAL)
            advanceUntilIdle()
            with(holder.state.value) {
                assertEquals(40_000L, litersScaled)
                assertEquals(1_500L, pricePerLiterScaled)
                assertEquals(6_000L, totalCostMinor)
            }

            holder.setMoneyInputMode(MoneyInputMode.PRICE_AND_TOTAL)
            advanceUntilIdle()
            with(holder.state.value) {
                assertEquals(40_000L, litersScaled)
                assertEquals(1_500L, pricePerLiterScaled)
                assertEquals(6_000L, totalCostMinor)
            }
        }

    @Test
    fun createPublishesWarningThenCompletionOnlyAfterConfirmation() =
        runTest {
            val repository = FakeFuelEntryRepository()
            val holder = createForm(backgroundScope, repository)
            var completionCount = 0
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { holder.state.collect {} }
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
                holder.observeSaveCompletions().collect { completionCount += 1 }
            }
            holder.enterValidCreateValues()
            repository.createResult =
                Outcome.Err(
                    ValidationWarning.OdometerInconsistent(
                        previousOdometerKm = 100L,
                        enteredOdometerKm = 50L,
                    ),
                )

            holder.confirmSave(Confirmation.OdometerInconsistent)
            holder.save()
            advanceUntilIdle()

            assertEquals(0, completionCount)
            assertEquals(
                UiMessageKind.WARNING,
                holder.state.value.message
                    ?.kind,
            )
            assertEquals(
                Confirmation.OdometerInconsistent,
                holder.state.value.message
                    ?.confirmation,
            )
            assertTrue(
                repository.createdCommands
                    .single()
                    .confirmations
                    .isEmpty(),
            )

            repository.createResult = Outcome.Ok(ENTRY_ID)
            holder.confirmSave(Confirmation.OdometerInconsistent)
            advanceUntilIdle()

            assertEquals(1, completionCount)
            assertConfirmedCreate(repository.createdCommands.last())

            repository.createResult = Outcome.Err(PersistenceError.ConstraintViolation)
            holder.save()
            advanceUntilIdle()
            assertEquals(1, completionCount)
            assertEquals(
                "PERSISTENCE.CONSTRAINT_VIOLATION",
                holder.state.value.message
                    ?.code,
            )
            assertNull(
                holder.state.value.message
                    ?.confirmation,
            )
        }

    @Test
    fun saveCompletionWaitsForTheNextCollectorAndIsConsumedOnce() =
        runTest {
            val repository = FakeFuelEntryRepository()
            val holder = createForm(backgroundScope, repository)
            holder.enterValidCreateValues()

            holder.save()
            advanceUntilIdle()

            val completion = withTimeoutOrNull(1_000L) { holder.observeSaveCompletions().first() }
            assertEquals(Unit, completion)

            val duplicate = withTimeoutOrNull(1L) { holder.observeSaveCompletions().first() }
            assertNull(duplicate)
        }

    @Test
    fun queuedSaveCompletionsConflateToOnePendingNavigation() =
        runTest {
            val repository = FakeFuelEntryRepository()
            val holder = createForm(backgroundScope, repository)
            holder.enterValidCreateValues()

            holder.save()
            advanceUntilIdle()
            holder.save()
            advanceUntilIdle()

            assertEquals(2, repository.createdCommands.size)
            assertEquals(Unit, withTimeoutOrNull(1_000L) { holder.observeSaveCompletions().first() })
            assertNull(withTimeoutOrNull(1L) { holder.observeSaveCompletions().first() })
        }

    @Test
    fun editLoadsExistingEntryAndPublishesUpdateOutcomes() =
        runTest {
            val repository = FakeFuelEntryRepository(getResult = Outcome.Ok(fuelEntry()))
            val holder = createForm(backgroundScope, repository, entryId = ENTRY_ID.value)
            var completionCount = 0
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { holder.state.collect {} }
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
                holder.observeSaveCompletions().collect { completionCount += 1 }
            }
            advanceUntilIdle()

            with(holder.state.value) {
                assertEquals(ENTRY_ID.value, entryId)
                assertEquals(456L, odometerKm)
                assertEquals(40_000L, litersScaled)
                assertEquals(1_500L, pricePerLiterScaled)
                assertEquals(6_000L, totalCostMinor)
                assertFalse(isFullTank)
                assertTrue(hasMissedEntries)
                assertEquals("original", notes)
            }

            holder.setMoneyInputMode(MoneyInputMode.LITERS_AND_TOTAL)
            holder.setLitersScaled(30_000L)
            holder.setTotalCostMinor(6_000L)
            holder.setNotes("updated")
            holder.save()
            advanceUntilIdle()

            assertEquals(1, completionCount)
            with(repository.updatedCommands.single()) {
                assertEquals(ENTRY_ID, id)
                assertEquals(MoneyInput.LitersAndTotal(30_000L, 6_000L), money)
                assertEquals("updated", notes)
            }

            repository.updateResult = Outcome.Err(ValidationError.EntityDeleted)
            holder.setMoneyInputMode(MoneyInputMode.PRICE_AND_TOTAL)
            holder.setPricePerLiterScaled(2_000L)
            holder.setTotalCostMinor(5_000L)
            holder.save()
            advanceUntilIdle()
            assertEquals(1, completionCount)
            assertIs<MoneyInput.PriceAndTotal>(repository.updatedCommands.last().money)
            assertEquals(
                "VALIDATION.ENTITY_DELETED",
                holder.state.value.message
                    ?.code,
            )
        }

    @Test
    fun editLoadReportsMissingAndPersistenceFailures() =
        runTest {
            val missingRepository = FakeFuelEntryRepository(getResult = Outcome.Ok(null))
            val missing = createForm(backgroundScope, missingRepository, entryId = ENTRY_ID.value)
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { missing.state.collect {} }
            advanceUntilIdle()
            assertEquals(
                "VALIDATION.ENTITY_NOT_FOUND",
                missing.state.value.message
                    ?.code,
            )

            val failedRepository =
                FakeFuelEntryRepository(
                    getResult = Outcome.Err(PersistenceError.SerializationFailed),
                )
            val failed = createForm(backgroundScope, failedRepository, entryId = OTHER_ENTRY_ID.value)
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { failed.state.collect {} }
            advanceUntilIdle()
            assertEquals(
                "PERSISTENCE.SERIALIZATION_FAILED",
                failed.state.value.message
                    ?.code,
            )
        }

    @Test
    fun saveIgnoresConcurrentRequests() =
        runTest {
            val repository = FakeFuelEntryRepository()
            val gate = CompletableDeferred<Unit>()
            repository.createGate = gate
            val holder = createForm(backgroundScope, repository)
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { holder.state.collect {} }
            holder.setLitersScaled(40_000L)
            holder.setPricePerLiterScaled(1_500L)

            holder.save()
            assertTrue(holder.state.value.isSaving)
            holder.save()
            holder.confirmSave(Confirmation.OdometerInconsistent)
            assertEquals(1, repository.createdCommands.size)

            gate.complete(Unit)
            advanceUntilIdle()
            assertFalse(holder.state.value.isSaving)
        }

    @Test
    fun modeSwitchPreservesTypedValuesWhenTheNewModePairIsIncomplete() =
        runTest {
            val holder = createForm(backgroundScope, FakeFuelEntryRepository())
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { holder.state.collect {} }
            holder.setLitersScaled(40_000L)
            advanceUntilIdle()

            holder.setMoneyInputMode(MoneyInputMode.PRICE_AND_TOTAL)
            advanceUntilIdle()

            with(holder.state.value) {
                assertEquals(40_000L, litersScaled)
                assertNull(pricePerLiterScaled)
                assertNull(totalCostMinor)
            }
        }

    @Test
    fun editModePublishesLoadingUntilTheEntryResolves() =
        runTest {
            val loadGate = CompletableDeferred<Unit>()
            val gateRepository =
                object : FuelEntryRepository {
                    val entries = MutableStateFlow<Outcome<List<FuelEntryListItem>, AppError>>(Outcome.Ok(emptyList()))
                    val consumption =
                        MutableStateFlow<Outcome<ConsumptionReport, AppError>>(
                            Outcome.Ok(ConsumptionReport(emptyList(), 0, null, false)),
                        )

                    override fun observeFuelEntries(
                        vehicleId: EntityId,
                        includeDeleted: Boolean,
                    ): Flow<Outcome<List<FuelEntryListItem>, AppError>> = entries

                    override suspend fun getFuelEntry(id: EntityId): Outcome<FuelEntry?, AppError> {
                        loadGate.await()
                        return Outcome.Ok(fuelEntry())
                    }

                    override suspend fun createFuelEntry(command: CreateFuelEntryCommand): Outcome<EntityId, AppError> =
                        Outcome.Ok(ENTRY_ID)

                    override suspend fun updateFuelEntry(command: UpdateFuelEntryCommand): Outcome<Unit, AppError> =
                        Outcome.Ok(Unit)

                    override suspend fun deleteFuelEntry(id: EntityId): Outcome<Unit, AppError> = Outcome.Ok(Unit)

                    override fun observeConsumption(vehicleId: EntityId): Flow<Outcome<ConsumptionReport, AppError>> =
                        consumption
                }
            val holder = createForm(backgroundScope, gateRepository, entryId = ENTRY_ID.value)
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { holder.state.collect {} }
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { holder.isLoading.collect {} }
            advanceUntilIdle()

            assertTrue(holder.isLoading.value)

            loadGate.complete(Unit)
            advanceUntilIdle()

            assertFalse(holder.isLoading.value)
            assertEquals(456L, holder.state.value.odometerKm)
        }

    private fun TestScope.createCollectedListHolder(repository: FakeFuelEntryRepository): FuelEntryListStateHolder {
        val holder =
            createFuelEntryListStateHolder(
                scope = backgroundScope,
                vehicleId = VEHICLE_ID.value,
                repository = repository,
                dispatchers = TestDispatcherProvider(),
            )
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { holder.state.collect {} }
        return holder
    }

    private fun FuelEntryFormStateHolder.enterValidCreateValues() {
        setOdometerKm(50L)
        setLitersScaled(40_000L)
        setPricePerLiterScaled(1_500L)
    }

    private fun assertConfirmedCreate(command: CreateFuelEntryCommand) {
        assertEquals(VEHICLE_ID, command.vehicleId)
        assertEquals(NOW, command.date)
        assertEquals(50L, command.odometerKm)
        assertEquals(MoneyInput.LitersAndPrice(40_000L, 1_500L), command.money)
        assertEquals(CurrencyCode("EUR"), command.currency)
        assertEquals(setOf(Confirmation.OdometerInconsistent), command.confirmations)
    }

    private fun createForm(
        scope: kotlinx.coroutines.CoroutineScope,
        repository: FuelEntryRepository,
        entryId: String? = null,
        odometer: Flow<Long> = flowOf(100L),
        settingsCurrencyCode: Flow<String> = flowOf("EUR"),
    ): FuelEntryFormStateHolder =
        createFuelEntryFormStateHolder(
            scope = scope,
            vehicleId = VEHICLE_ID.value,
            entryId = entryId,
            initialDateEpochMillis = NOW.toEpochMilliseconds(),
            initialOdometerKm = odometer,
            initialCurrencyCode = "EUR",
            settingsCurrencyCode = settingsCurrencyCode,
            repository = repository,
            dispatchers = TestDispatcherProvider(),
        )

    private fun listItem() =
        FuelEntryListItem(
            id = ENTRY_ID,
            date = NOW,
            odometerKm = 456L,
            litersScaled = 40_000L,
            totalCostMinor = 6_000L,
            currency = CurrencyCode("EUR"),
            isFullTank = false,
            consumption = null,
            invalidReason = ConsumptionInvalidReason.EndEntryNotFullTank,
            hasMissedEntries = true,
            odometerInconsistent = true,
        )

    private fun fuelEntry() =
        FuelEntry(
            id = ENTRY_ID,
            ownerId = OwnerId("owner-a"),
            vehicleId = VEHICLE_ID,
            date = NOW,
            odometerKm = 456L,
            litersScaled = 40_000L,
            pricePerLiterScaled = 1_500L,
            totalCostMinor = 6_000L,
            currency = CurrencyCode("EUR"),
            isFullTank = false,
            hasMissedEntries = true,
            odometerInconsistent = true,
            notes = "original",
            createdAt = NOW,
            updatedAt = NOW,
            deletedAt = null,
        )

    private class FakeFuelEntryRepository(
        var getResult: Outcome<FuelEntry?, AppError> = Outcome.Ok(null),
    ) : FuelEntryRepository {
        val entries = MutableStateFlow<Outcome<List<FuelEntryListItem>, AppError>>(Outcome.Ok(emptyList()))
        val consumption =
            MutableStateFlow<Outcome<ConsumptionReport, AppError>>(
                Outcome.Ok(ConsumptionReport(emptyList(), 0, null, false)),
            )
        var createResult: Outcome<EntityId, AppError> = Outcome.Ok(ENTRY_ID)
        var updateResult: Outcome<Unit, AppError> = Outcome.Ok(Unit)
        var deleteResult: Outcome<Unit, AppError> = Outcome.Ok(Unit)
        var createGate: CompletableDeferred<Unit>? = null
        var observedVehicleId: EntityId? = null
        var observedIncludeDeleted = true
        val createdCommands = mutableListOf<CreateFuelEntryCommand>()
        val updatedCommands = mutableListOf<UpdateFuelEntryCommand>()
        val deletedIds = mutableListOf<EntityId>()

        override fun observeFuelEntries(
            vehicleId: EntityId,
            includeDeleted: Boolean,
        ): Flow<Outcome<List<FuelEntryListItem>, AppError>> {
            observedVehicleId = vehicleId
            observedIncludeDeleted = includeDeleted
            return entries
        }

        override suspend fun getFuelEntry(id: EntityId): Outcome<FuelEntry?, AppError> = getResult

        override suspend fun createFuelEntry(command: CreateFuelEntryCommand): Outcome<EntityId, AppError> {
            createdCommands += command
            createGate?.await()
            return createResult
        }

        override suspend fun updateFuelEntry(command: UpdateFuelEntryCommand): Outcome<Unit, AppError> {
            updatedCommands += command
            return updateResult
        }

        override suspend fun deleteFuelEntry(id: EntityId): Outcome<Unit, AppError> {
            deletedIds += id
            return deleteResult
        }

        override fun observeConsumption(vehicleId: EntityId): Flow<Outcome<ConsumptionReport, AppError>> = consumption
    }

    private companion object {
        val VEHICLE_ID = EntityId("00000000-0000-4000-8000-000000000100")
        val ENTRY_ID = EntityId("00000000-0000-4000-8000-000000000101")
        val OTHER_ENTRY_ID = EntityId("00000000-0000-4000-8000-000000000102")
        val NOW = Instant.fromEpochMilliseconds(1_777_777_777_000L)
    }
}
