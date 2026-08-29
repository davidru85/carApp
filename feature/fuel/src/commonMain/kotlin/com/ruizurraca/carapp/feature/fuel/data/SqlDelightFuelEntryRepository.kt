package com.ruizurraca.carapp.feature.fuel.data

import com.ruizurraca.carapp.core.common.AppClock
import com.ruizurraca.carapp.core.common.AppError
import com.ruizurraca.carapp.core.common.CLIENT_MAX_SCHEMA_VERSION
import com.ruizurraca.carapp.core.common.Outcome
import com.ruizurraca.carapp.core.common.OwnerContext
import com.ruizurraca.carapp.core.common.PersistenceError
import com.ruizurraca.carapp.core.common.UuidGenerator
import com.ruizurraca.carapp.core.common.ValidationError
import com.ruizurraca.carapp.core.common.earliestAllowedFuelEntryDate
import com.ruizurraca.carapp.core.database.FuelEntryDatabaseAccess
import com.ruizurraca.carapp.core.model.ConsumptionReport
import com.ruizurraca.carapp.core.model.EntityId
import com.ruizurraca.carapp.core.model.FuelEntry
import com.ruizurraca.carapp.core.model.FuelEntryListItem
import com.ruizurraca.carapp.feature.fuel.domain.CalculateConsumption
import com.ruizurraca.carapp.feature.fuel.domain.CreateFuelEntryCommand
import com.ruizurraca.carapp.feature.fuel.domain.DefaultCalculateConsumption
import com.ruizurraca.carapp.feature.fuel.domain.FuelEntryRepository
import com.ruizurraca.carapp.feature.fuel.domain.FuelEntryValidationContext
import com.ruizurraca.carapp.feature.fuel.domain.UpdateFuelEntryCommand
import com.ruizurraca.carapp.feature.fuel.domain.ValidateCreateFuelEntry
import com.ruizurraca.carapp.feature.fuel.domain.ValidateUpdateFuelEntry
import com.ruizurraca.carapp.feature.fuel.domain.ValidatedFuelEntryValues
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.serialization.SerializationException

@OptIn(ExperimentalCoroutinesApi::class)
class SqlDelightFuelEntryRepository internal constructor(
    private val localDataSource: FuelEntryLocalDataSource,
    private val ownerContext: OwnerContext,
    private val clock: AppClock,
    private val uuidGenerator: UuidGenerator,
    private val calculateConsumption: CalculateConsumption,
) : FuelEntryRepository {
    private val validateCreate = ValidateCreateFuelEntry()
    private val validateUpdate = ValidateUpdateFuelEntry()

    constructor(
        databaseAccess: FuelEntryDatabaseAccess,
        ownerContext: OwnerContext,
        clock: AppClock,
        uuidGenerator: UuidGenerator,
    ) : this(
        localDataSource = SqlDelightFuelEntryLocalDataSource(databaseAccess),
        ownerContext = ownerContext,
        clock = clock,
        uuidGenerator = uuidGenerator,
        calculateConsumption = DefaultCalculateConsumption(),
    )

    override fun observeFuelEntries(
        vehicleId: EntityId,
        includeDeleted: Boolean,
    ): Flow<Outcome<List<FuelEntryListItem>, AppError>> =
        ownerContext
            .observe()
            .flatMapLatest { ownerId ->
                localDataSource.observeFuelEntryList(ownerId, vehicleId, includeDeleted)
            }.map<List<LocalFuelEntry>, Outcome<List<FuelEntryListItem>, AppError>> { rows ->
                val calculationRows =
                    rows.filter { it.vehicleId == vehicleId && it.deletedAt == null }
                val report = calculateConsumption(calculationRows.map(LocalFuelEntry::toDomainFuelEntry))
                Outcome.Ok(rows.map { it.toFuelEntryListItem(report) })
            }.catch { throwable -> emitReadFailure(throwable) }

    override suspend fun getFuelEntry(id: EntityId): Outcome<FuelEntry?, AppError> =
        runRead {
            localDataSource.fuelEntry(ownerContext.current, id)?.toDomainFuelEntry()
        }

    override suspend fun createFuelEntry(command: CreateFuelEntryCommand): Outcome<EntityId, AppError> =
        runWrite {
            val ownerId = ownerContext.current
            val id = EntityId(uuidGenerator.newId())
            val now = clock.now()
            localDataSource.writeTransaction {
                val vehicle =
                    vehicle(ownerId, command.vehicleId)
                        ?: return@writeTransaction Outcome.Err(ValidationError.EntityNotFound)
                if (vehicle.deletedAt != null) {
                    return@writeTransaction Outcome.Err(ValidationError.EntityDeleted)
                }
                val previous =
                    previousActiveFuelEntry(
                        vehicleId = command.vehicleId,
                        date = command.date,
                        createdAt = now,
                        id = id,
                        excludedId = null,
                    )
                when (
                    val validation =
                        validateCreate(
                            command,
                            validationContext(now, vehicle, previous),
                        )
                ) {
                    is Outcome.Err -> {
                        validation
                    }

                    is Outcome.Ok -> {
                        val entry = validation.value.toLocalFuelEntry(id, ownerId, now)
                        insertFuelEntry(entry, LocalFuelEntry::toFuelEntryOutboxPayloadOrNull)
                        Outcome.Ok(id)
                    }
                }
            }
        }

    override suspend fun updateFuelEntry(command: UpdateFuelEntryCommand): Outcome<Unit, AppError> =
        runWrite {
            val ownerId = ownerContext.current
            val now = clock.now()
            localDataSource.writeTransaction {
                val existing =
                    fuelEntry(ownerId, command.id)
                        ?: return@writeTransaction Outcome.Err(ValidationError.EntityNotFound)
                if (existing.deletedAt != null) {
                    return@writeTransaction Outcome.Err(ValidationError.EntityDeleted)
                }
                val vehicle =
                    vehicle(ownerId, command.vehicleId)
                        ?: return@writeTransaction Outcome.Err(ValidationError.EntityNotFound)
                if (vehicle.deletedAt != null) {
                    return@writeTransaction Outcome.Err(ValidationError.EntityDeleted)
                }
                val previous =
                    previousActiveFuelEntry(
                        vehicleId = command.vehicleId,
                        date = command.date,
                        createdAt = existing.createdAt,
                        id = existing.id,
                        excludedId = existing.id,
                    )
                when (
                    val validation =
                        validateUpdate(
                            command,
                            validationContext(now, vehicle, previous),
                        )
                ) {
                    is Outcome.Err -> {
                        validation
                    }

                    is Outcome.Ok -> {
                        val updated = existing.withValidatedValues(validation.value, ownerId, now)
                        updateFuelEntry(updated, LocalFuelEntry::toFuelEntryOutboxPayloadOrNull)
                        Outcome.Ok(Unit)
                    }
                }
            }
        }

    override suspend fun deleteFuelEntry(id: EntityId): Outcome<Unit, AppError> =
        runWrite {
            val ownerId = ownerContext.current
            val now = clock.now()
            localDataSource.writeTransaction {
                val existing =
                    fuelEntry(ownerId, id)
                        ?: return@writeTransaction Outcome.Err(ValidationError.EntityNotFound)
                if (existing.deletedAt != null) return@writeTransaction Outcome.Ok(Unit)

                val tombstone =
                    existing.copy(
                        ownerId = ownerId,
                        updatedAt = now,
                        deletedAt = now,
                        syncState = PENDING,
                        localRevision = existing.localRevision + 1L,
                    )
                tombstoneFuelEntry(tombstone, LocalFuelEntry::toFuelEntryOutboxPayloadOrNull)
                Outcome.Ok(Unit)
            }
        }

    override fun observeConsumption(vehicleId: EntityId): Flow<Outcome<ConsumptionReport, AppError>> =
        ownerContext
            .observe()
            .flatMapLatest { ownerId -> localDataSource.observeConsumptionEntries(ownerId, vehicleId) }
            .map<List<LocalFuelEntry>, Outcome<ConsumptionReport, AppError>> { rows ->
                val filtered =
                    rows
                        .filter { it.vehicleId == vehicleId && it.deletedAt == null }
                        .map(LocalFuelEntry::toDomainFuelEntry)
                Outcome.Ok(calculateConsumption(filtered))
            }.catch { throwable -> emitReadFailure(throwable) }

    private fun validationContext(
        now: kotlin.time.Instant,
        vehicle: FuelEntryVehicleFacts,
        previous: LocalFuelEntry?,
    ) = FuelEntryValidationContext(
        now = now,
        earliestAllowedDate = earliestAllowedFuelEntryDate(vehicle.createdAt),
        vehicleInitialOdometerKm = vehicle.initialOdometerKm,
        previousOdometerKm = previous?.odometerKm,
    )

    private suspend fun <T> runRead(block: suspend () -> T): Outcome<T, AppError> =
        try {
            Outcome.Ok(block())
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (_: Throwable) {
            Outcome.Err(PersistenceError.DatabaseUnavailable)
        }

    private suspend fun <T> runWrite(block: suspend () -> Outcome<T, AppError>): Outcome<T, AppError> =
        try {
            block()
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (_: SerializationException) {
            Outcome.Err(PersistenceError.SerializationFailed)
        } catch (_: Throwable) {
            Outcome.Err(PersistenceError.TransactionFailed)
        }

    private suspend fun <T> kotlinx.coroutines.flow.FlowCollector<Outcome<T, AppError>>.emitReadFailure(
        throwable: Throwable,
    ) {
        if (throwable is CancellationException) throw throwable
        emit(Outcome.Err(PersistenceError.DatabaseUnavailable))
    }
}

private fun ValidatedFuelEntryValues.toLocalFuelEntry(
    id: EntityId,
    ownerId: com.ruizurraca.carapp.core.model.OwnerId,
    now: kotlin.time.Instant,
): LocalFuelEntry =
    LocalFuelEntry(
        id = id,
        ownerId = ownerId,
        vehicleId = vehicleId,
        date = date,
        odometerKm = odometerKm,
        litersScaled = litersScaled,
        pricePerLiterScaled = pricePerLiterScaled,
        totalCostMinor = totalCostMinor,
        currency = currency,
        isFullTank = isFullTank,
        hasMissedEntries = hasMissedEntries,
        odometerInconsistent = false,
        notes = notes,
        createdAt = now,
        updatedAt = now,
        serverUpdatedAt = null,
        deletedAt = null,
        syncState = PENDING,
        localRevision = 1L,
        localMutationSeq = 0L,
        schemaVersion = CLIENT_MAX_SCHEMA_VERSION.toLong(),
    )

private fun LocalFuelEntry.withValidatedValues(
    values: ValidatedFuelEntryValues,
    ownerId: com.ruizurraca.carapp.core.model.OwnerId,
    now: kotlin.time.Instant,
): LocalFuelEntry =
    copy(
        ownerId = ownerId,
        vehicleId = values.vehicleId,
        date = values.date,
        odometerKm = values.odometerKm,
        litersScaled = values.litersScaled,
        pricePerLiterScaled = values.pricePerLiterScaled,
        totalCostMinor = values.totalCostMinor,
        currency = values.currency,
        isFullTank = values.isFullTank,
        hasMissedEntries = values.hasMissedEntries,
        notes = values.notes,
        updatedAt = now,
        syncState = PENDING,
        localRevision = localRevision + 1L,
    )

private const val PENDING = "PENDING"
