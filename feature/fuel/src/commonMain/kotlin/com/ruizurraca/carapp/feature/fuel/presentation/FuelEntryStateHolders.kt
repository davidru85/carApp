@file:OptIn(
    kotlin.experimental.ExperimentalObjCName::class,
    kotlin.experimental.ExperimentalObjCRefinement::class,
)

package com.ruizurraca.carapp.feature.fuel.presentation

import com.ruizurraca.carapp.core.common.AppError
import com.ruizurraca.carapp.core.common.Confirmation
import com.ruizurraca.carapp.core.common.DispatcherProvider
import com.ruizurraca.carapp.core.common.MinorUnits
import com.ruizurraca.carapp.core.common.Outcome
import com.ruizurraca.carapp.core.common.STATE_HOLDER_TIMEOUT_MS
import com.ruizurraca.carapp.core.common.SyncStatus
import com.ruizurraca.carapp.core.common.UiMessage
import com.ruizurraca.carapp.core.common.UiMessageKind
import com.ruizurraca.carapp.core.common.ValidationError
import com.ruizurraca.carapp.core.common.ValidationWarning
import com.ruizurraca.carapp.core.model.ConsumptionReport
import com.ruizurraca.carapp.core.model.CurrencyCode
import com.ruizurraca.carapp.core.model.EntityId
import com.ruizurraca.carapp.core.model.FuelEntry
import com.ruizurraca.carapp.core.model.FuelEntryListItem
import com.ruizurraca.carapp.feature.fuel.domain.CreateFuelEntryCommand
import com.ruizurraca.carapp.feature.fuel.domain.FuelEntryRepository
import com.ruizurraca.carapp.feature.fuel.domain.MoneyInput
import com.ruizurraca.carapp.feature.fuel.domain.UpdateFuelEntryCommand
import com.ruizurraca.carapp.feature.fuel.domain.resolveMoney
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.plus
import kotlinx.coroutines.withContext
import kotlin.native.HiddenFromObjC
import kotlin.native.ObjCName
import kotlin.time.Instant

@ObjCName(name = "SharedFuelEntryListStateHolder", swiftName = "FuelEntryListStateHolder", exact = true)
class FuelEntryListStateHolder internal constructor(
    scope: CoroutineScope,
    private val vehicleId: String,
    private val repository: FuelEntryRepository,
    private val dispatchers: DispatcherProvider,
) {
    private val holderJob = SupervisorJob(scope.coroutineContext[Job])
    private val holderScope = CoroutineScope(scope.coroutineContext + holderJob)
    private val transientMessage = MutableStateFlow<UiMessage?>(null)
    private var pendingDeleteEntryId: String? = null
    private var closed = false

    val state: StateFlow<FuelEntryListUiState> =
        combine(
            repository.observeFuelEntries(EntityId(vehicleId), includeDeleted = false).flowOn(dispatchers.io),
            repository.observeConsumption(EntityId(vehicleId)).flowOn(dispatchers.io),
            transientMessage,
        ) { entries, report, message ->
            entries.toUiState(
                vehicleId = vehicleId,
                report = report,
                message = message,
            )
        }.stateIn(
            scope = holderScope + dispatchers.main,
            started = SharingStarted.WhileSubscribed(STATE_HOLDER_TIMEOUT_MS),
            initialValue =
                FuelEntryListUiState(
                    vehicleId = vehicleId,
                    isLoading = true,
                    entries = emptyList(),
                    consumptionAverageScaled = null,
                    validConsumptionSegmentCount = 0,
                    isConsumptionReliable = false,
                    syncStatus = SyncStatus.Idle,
                    message = null,
                ),
        )

    fun refresh() {
        if (closed) return
        transientMessage.value = null
    }

    fun requestDelete(entryId: String) {
        if (closed) return
        pendingDeleteEntryId = entryId
        transientMessage.value =
            UiMessage(
                id = DELETE_MESSAGE_ID,
                kind = UiMessageKind.WARNING,
                code = DELETE_CONFIRMATION_CODE,
                confirmation = null,
            )
    }

    fun confirmDelete(entryId: String) {
        if (closed) return
        if (pendingDeleteEntryId != entryId) return
        pendingDeleteEntryId = null
        transientMessage.value = null
        holderScope.launch(dispatchers.main) {
            val result = withContext(dispatchers.io) { repository.deleteFuelEntry(EntityId(entryId)) }
            if (result is Outcome.Err) transientMessage.value = result.error.toUiMessage()
        }
    }

    fun clearMessage() {
        if (closed) return
        pendingDeleteEntryId = null
        transientMessage.value = null
    }

    fun close() {
        if (closed) return
        closed = true
        holderScope.cancel()
    }
}

@ObjCName(name = "SharedFuelEntryFormStateHolder", swiftName = "FuelEntryFormStateHolder", exact = true)
class FuelEntryFormStateHolder internal constructor(
    scope: CoroutineScope,
    vehicleId: String,
    entryId: String?,
    initialDateEpochMillis: Long,
    initialOdometerKm: Flow<Long>,
    initialCurrencyCode: String,
    private val repository: FuelEntryRepository,
    private val dispatchers: DispatcherProvider,
) {
    private val holderJob = SupervisorJob(scope.coroutineContext[Job])
    private val holderScope = CoroutineScope(scope.coroutineContext + holderJob)
    private val inputs =
        MutableStateFlow(
            FormInputs(
                vehicleId = vehicleId,
                entryId = entryId,
                dateEpochMillis = initialDateEpochMillis,
                currencyCode = initialCurrencyCode,
            ),
        )
    private val saving = MutableStateFlow(false)
    private val transientMessage = MutableStateFlow<UiMessage?>(null)
    private val saveCompletions = Channel<Unit>(capacity = Channel.BUFFERED)
    private var pendingConfirmationInputs: FormInputs? = null
    private var odometerEdited = false
    private var closed = false

    init {
        if (entryId == null) {
            holderScope.launch(dispatchers.main) {
                initialOdometerKm.flowOn(dispatchers.io).collect { odometerKm ->
                    if (!odometerEdited) inputs.value = inputs.value.copy(odometerKm = odometerKm)
                }
            }
        } else {
            holderScope.launch(dispatchers.main) {
                val result = withContext(dispatchers.io) { repository.getFuelEntry(EntityId(entryId)) }
                applyLoadedEntry(result)
            }
        }
    }

    val state: StateFlow<FuelEntryFormUiState> =
        combine(inputs, saving, transientMessage) { input, isSaving, message ->
            input.toUiState(isSaving, message)
        }.stateIn(
            scope = holderScope + dispatchers.main,
            started = SharingStarted.WhileSubscribed(STATE_HOLDER_TIMEOUT_MS),
            initialValue = inputs.value.toUiState(isSaving = false, message = null),
        )

    fun setDateEpochMillis(value: Long) = editInputs { copy(dateEpochMillis = value) }

    fun setOdometerKm(value: Long) {
        odometerEdited = true
        editInputs { copy(odometerKm = value) }
    }

    fun setMoneyInputMode(value: MoneyInputMode) = editInputs { copy(moneyInputMode = value).resolveLiveMoney() }

    fun setLitersScaled(value: Long?) = editInputs { copy(litersScaled = value).resolveLiveMoney() }

    fun setPricePerLiterScaled(value: Long?) = editInputs { copy(pricePerLiterScaled = value).resolveLiveMoney() }

    fun setTotalCostMinor(value: Long?) = editInputs { copy(totalCostMinor = value).resolveLiveMoney() }

    fun setCurrencyCode(value: String) = editInputs { copy(currencyCode = value).resolveLiveMoney() }

    fun setFullTank(value: Boolean) = editInputs { copy(isFullTank = value) }

    fun setMissedEntries(value: Boolean) = editInputs { copy(hasMissedEntries = value) }

    fun setNotes(value: String?) = editInputs { copy(notes = value) }

    @HiddenFromObjC
    fun observeSaveCompletions(): Flow<Unit> = saveCompletions.receiveAsFlow()

    fun save() {
        if (closed || saving.value) return
        pendingConfirmationInputs = null
        persist(inputs.value, confirmations = emptySet())
    }

    fun confirmSave(confirmation: Confirmation) {
        if (closed || saving.value) return
        val pending = pendingConfirmationInputs ?: return
        pendingConfirmationInputs = null
        persist(pending, confirmations = setOf(confirmation))
    }

    fun clearMessage() {
        if (closed) return
        transientMessage.value = null
    }

    fun close() {
        if (closed) return
        closed = true
        pendingConfirmationInputs = null
        saveCompletions.close()
        holderScope.cancel()
    }

    private fun editInputs(transform: FormInputs.() -> FormInputs) {
        if (closed) return
        pendingConfirmationInputs = null
        transientMessage.value = null
        inputs.value = inputs.value.transform()
    }

    private fun persist(
        snapshot: FormInputs,
        confirmations: Set<Confirmation>,
    ) {
        val money = snapshot.toMoneyInput()
        if (money == null) {
            transientMessage.value = ValidationError.InvalidMoneyInput.toUiMessage()
            return
        }
        saving.value = true
        transientMessage.value = null
        holderScope.launch(dispatchers.main) {
            val result =
                withContext(dispatchers.io) {
                    if (snapshot.entryId == null) {
                        repository.createFuelEntry(snapshot.toCreateCommand(money, confirmations)).mapToUnit()
                    } else {
                        repository.updateFuelEntry(snapshot.toUpdateCommand(money, confirmations))
                    }
                }
            when (result) {
                is Outcome.Ok -> {
                    pendingConfirmationInputs = null
                    saveCompletions.trySend(Unit)
                }

                is Outcome.Err -> {
                    if (result.error is ValidationWarning.OdometerInconsistent) {
                        pendingConfirmationInputs = snapshot
                    }
                    transientMessage.value = result.error.toUiMessage()
                }
            }
            saving.value = false
        }
    }

    private fun applyLoadedEntry(result: Outcome<FuelEntry?, AppError>) {
        when (result) {
            is Outcome.Err -> {
                transientMessage.value = result.error.toUiMessage()
            }

            is Outcome.Ok -> {
                val entry = result.value
                if (entry == null) {
                    transientMessage.value = ValidationError.EntityNotFound.toUiMessage()
                } else {
                    inputs.value = entry.toFormInputs().resolveLiveMoney()
                }
            }
        }
    }
}

@HiddenFromObjC
fun createFuelEntryListStateHolder(
    scope: CoroutineScope,
    vehicleId: String,
    repository: FuelEntryRepository,
    dispatchers: DispatcherProvider,
): FuelEntryListStateHolder = FuelEntryListStateHolder(scope, vehicleId, repository, dispatchers)

@HiddenFromObjC
fun createFuelEntryFormStateHolder(
    scope: CoroutineScope,
    vehicleId: String,
    entryId: String?,
    initialDateEpochMillis: Long,
    initialOdometerKm: Flow<Long>,
    initialCurrencyCode: String,
    repository: FuelEntryRepository,
    dispatchers: DispatcherProvider,
): FuelEntryFormStateHolder =
    FuelEntryFormStateHolder(
        scope = scope,
        vehicleId = vehicleId,
        entryId = entryId,
        initialDateEpochMillis = initialDateEpochMillis,
        initialOdometerKm = initialOdometerKm,
        initialCurrencyCode = initialCurrencyCode,
        repository = repository,
        dispatchers = dispatchers,
    )

private data class FormInputs(
    val vehicleId: String,
    val entryId: String?,
    val dateEpochMillis: Long,
    val odometerKm: Long = 0L,
    val moneyInputMode: MoneyInputMode = MoneyInputMode.LITERS_AND_PRICE,
    val litersScaled: Long? = null,
    val pricePerLiterScaled: Long? = null,
    val totalCostMinor: Long? = null,
    val currencyCode: String,
    val isFullTank: Boolean = true,
    val hasMissedEntries: Boolean = false,
    val notes: String? = null,
)

private fun FormInputs.resolveLiveMoney(): FormInputs {
    val factor = MinorUnits.factorFor(CurrencyCode(currencyCode)) ?: return clearDerivedMoney()
    val money = toMoneyInput() ?: return clearDerivedMoney()
    return when (val result = resolveMoney(money, factor)) {
        is Outcome.Err -> {
            clearDerivedMoney()
        }

        is Outcome.Ok -> {
            copy(
                litersScaled = result.value.litersScaled,
                pricePerLiterScaled = result.value.pricePerLiterScaled,
                totalCostMinor = result.value.totalCostMinor,
            )
        }
    }
}

private fun FormInputs.clearDerivedMoney(): FormInputs =
    when (moneyInputMode) {
        MoneyInputMode.LITERS_AND_PRICE -> copy(totalCostMinor = null)
        MoneyInputMode.LITERS_AND_TOTAL -> copy(pricePerLiterScaled = null)
        MoneyInputMode.PRICE_AND_TOTAL -> copy(litersScaled = null)
    }

private fun FormInputs.toMoneyInput(): MoneyInput? =
    when (moneyInputMode) {
        MoneyInputMode.LITERS_AND_PRICE -> {
            val liters = litersScaled ?: return null
            val price = pricePerLiterScaled ?: return null
            MoneyInput.LitersAndPrice(liters, price)
        }

        MoneyInputMode.LITERS_AND_TOTAL -> {
            val liters = litersScaled ?: return null
            val total = totalCostMinor ?: return null
            MoneyInput.LitersAndTotal(liters, total)
        }

        MoneyInputMode.PRICE_AND_TOTAL -> {
            val price = pricePerLiterScaled ?: return null
            val total = totalCostMinor ?: return null
            MoneyInput.PriceAndTotal(price, total)
        }
    }

private fun FormInputs.toCreateCommand(
    money: MoneyInput,
    confirmations: Set<Confirmation>,
): CreateFuelEntryCommand =
    CreateFuelEntryCommand(
        vehicleId = EntityId(vehicleId),
        date = Instant.fromEpochMilliseconds(dateEpochMillis),
        odometerKm = odometerKm,
        money = money,
        currency = CurrencyCode(currencyCode),
        isFullTank = isFullTank,
        hasMissedEntries = hasMissedEntries,
        notes = notes,
        confirmations = confirmations,
    )

private fun FormInputs.toUpdateCommand(
    money: MoneyInput,
    confirmations: Set<Confirmation>,
): UpdateFuelEntryCommand =
    UpdateFuelEntryCommand(
        id = EntityId(requireNotNull(entryId)),
        vehicleId = EntityId(vehicleId),
        date = Instant.fromEpochMilliseconds(dateEpochMillis),
        odometerKm = odometerKm,
        money = money,
        currency = CurrencyCode(currencyCode),
        isFullTank = isFullTank,
        hasMissedEntries = hasMissedEntries,
        notes = notes,
        confirmations = confirmations,
    )

private fun FormInputs.toUiState(
    isSaving: Boolean,
    message: UiMessage?,
): FuelEntryFormUiState =
    FuelEntryFormUiState(
        vehicleId = vehicleId,
        entryId = entryId,
        dateEpochMillis = dateEpochMillis,
        odometerKm = odometerKm,
        moneyInputMode = moneyInputMode,
        litersScaled = litersScaled,
        pricePerLiterScaled = pricePerLiterScaled,
        totalCostMinor = totalCostMinor,
        currencyCode = currencyCode,
        isFullTank = isFullTank,
        hasMissedEntries = hasMissedEntries,
        notes = notes,
        isSaving = isSaving,
        message = message,
    )

private fun FuelEntry.toFormInputs(): FormInputs =
    FormInputs(
        vehicleId = vehicleId.value,
        entryId = id.value,
        dateEpochMillis = date.toEpochMilliseconds(),
        odometerKm = odometerKm,
        litersScaled = litersScaled,
        pricePerLiterScaled = pricePerLiterScaled,
        totalCostMinor = totalCostMinor,
        currencyCode = currency.value,
        isFullTank = isFullTank,
        hasMissedEntries = hasMissedEntries,
        notes = notes,
    )

private fun Outcome<List<FuelEntryListItem>, AppError>.toUiState(
    vehicleId: String,
    report: Outcome<ConsumptionReport, AppError>,
    message: UiMessage?,
): FuelEntryListUiState {
    val entries = (this as? Outcome.Ok)?.value.orEmpty()
    val consumption = (report as? Outcome.Ok)?.value
    val error = (this as? Outcome.Err)?.error ?: (report as? Outcome.Err)?.error
    return FuelEntryListUiState(
        vehicleId = vehicleId,
        isLoading = false,
        entries = entries.map(FuelEntryListItem::toUiItem),
        consumptionAverageScaled = consumption?.average?.scaled,
        validConsumptionSegmentCount = consumption?.validSegmentCount ?: 0,
        isConsumptionReliable = consumption?.isReliable ?: false,
        syncStatus = SyncStatus.Idle,
        message = message ?: error?.toUiMessage(),
    )
}

private fun FuelEntryListItem.toUiItem(): FuelEntryListItemUi =
    FuelEntryListItemUi(
        id = id.value,
        dateEpochMillis = date.toEpochMilliseconds(),
        odometerKm = odometerKm,
        litersScaled = litersScaled,
        totalCostMinor = totalCostMinor,
        currencyCode = currency.value,
        isFullTank = isFullTank,
        consumptionScaled = consumption?.scaled,
        invalidReason = invalidReason,
        hasMissedEntries = hasMissedEntries,
        odometerInconsistent = odometerInconsistent,
    )

private fun AppError.toUiMessage(): UiMessage =
    UiMessage(
        id = ERROR_MESSAGE_ID,
        kind = if (this is ValidationWarning) UiMessageKind.WARNING else UiMessageKind.ERROR,
        code = code,
        confirmation =
            if (this is ValidationWarning.OdometerInconsistent) {
                Confirmation.OdometerInconsistent
            } else {
                null
            },
    )

private fun <T, E> Outcome<T, E>.mapToUnit(): Outcome<Unit, E> =
    when (this) {
        is Outcome.Ok -> Outcome.Ok(Unit)
        is Outcome.Err -> this
    }

private const val ERROR_MESSAGE_ID = 1L
private const val DELETE_MESSAGE_ID = 2L
private const val DELETE_CONFIRMATION_CODE = "INFO.CONFIRM_DELETE_FUEL_ENTRY"
