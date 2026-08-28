package com.ruizurraca.carapp.feature.vehicle.data

import com.ruizurraca.carapp.core.common.AppClock
import com.ruizurraca.carapp.core.common.AppError
import com.ruizurraca.carapp.core.common.CLIENT_MAX_SCHEMA_VERSION
import com.ruizurraca.carapp.core.common.Outcome
import com.ruizurraca.carapp.core.common.OwnerContext
import com.ruizurraca.carapp.core.common.PersistenceError
import com.ruizurraca.carapp.core.common.UuidGenerator
import com.ruizurraca.carapp.core.common.ValidationError
import com.ruizurraca.carapp.core.database.FuelEntryDatabaseRow
import com.ruizurraca.carapp.core.database.VehicleDatabaseAccess
import com.ruizurraca.carapp.core.model.EntityId
import com.ruizurraca.carapp.core.model.LOCAL_OWNER
import com.ruizurraca.carapp.core.model.Vehicle
import com.ruizurraca.carapp.feature.vehicle.domain.CreateVehicleCommand
import com.ruizurraca.carapp.feature.vehicle.domain.CreateVehicleValidationContext
import com.ruizurraca.carapp.feature.vehicle.domain.UpdateVehicleCommand
import com.ruizurraca.carapp.feature.vehicle.domain.UpdateVehicleValidationContext
import com.ruizurraca.carapp.feature.vehicle.domain.ValidateCreateVehicle
import com.ruizurraca.carapp.feature.vehicle.domain.ValidateUpdateVehicle
import com.ruizurraca.carapp.feature.vehicle.domain.VehicleRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.serialization.SerializationException

@OptIn(ExperimentalCoroutinesApi::class)
class SqlDelightVehicleRepository internal constructor(
    private val localDataSource: VehicleLocalDataSource,
    private val ownerContext: OwnerContext,
    private val clock: AppClock,
    private val uuidGenerator: UuidGenerator,
) : VehicleRepository {
    private val validateCreate = ValidateCreateVehicle()
    private val validateUpdate = ValidateUpdateVehicle()

    constructor(
        databaseAccess: VehicleDatabaseAccess,
        ownerContext: OwnerContext,
        clock: AppClock,
        uuidGenerator: UuidGenerator,
    ) : this(
        localDataSource = SqlDelightVehicleLocalDataSource(databaseAccess),
        ownerContext = ownerContext,
        clock = clock,
        uuidGenerator = uuidGenerator,
    )

    override fun observeVehicles(includeDeleted: Boolean): Flow<Outcome<List<Vehicle>, AppError>> =
        ownerContext
            .observe()
            .flatMapLatest { ownerId -> localDataSource.observeVehicles(ownerId, includeDeleted) }
            .map<List<LocalVehicle>, Outcome<List<Vehicle>, AppError>> { rows ->
                Outcome.Ok(rows.map(LocalVehicle::toDomainVehicle))
            }.catch { throwable -> emitReadFailure(throwable) }

    override fun observeVehicle(id: EntityId): Flow<Outcome<Vehicle?, AppError>> =
        ownerContext
            .observe()
            .flatMapLatest { ownerId -> localDataSource.observeVehicle(ownerId, id) }
            .map<LocalVehicle?, Outcome<Vehicle?, AppError>> { row ->
                Outcome.Ok(row?.toDomainVehicle())
            }.catch { throwable -> emitReadFailure(throwable) }

    override suspend fun createVehicle(command: CreateVehicleCommand): Outcome<EntityId, AppError> =
        runWrite {
            val ownerId = ownerContext.current
            val id = EntityId(uuidGenerator.newId())
            val now = clock.now()
            localDataSource.writeTransaction {
                when (
                    val validation =
                        validateCreate(
                            command,
                            CreateVehicleValidationContext(activeVehicleCandidates(ownerId)),
                        )
                ) {
                    is Outcome.Err -> {
                        validation
                    }

                    is Outcome.Ok -> {
                        val normalised = validation.value
                        val vehicle =
                            LocalVehicle(
                                id = id,
                                ownerId = ownerId,
                                name = normalised.name,
                                nameFold = normalised.name.lowercase(),
                                initialOdometerKm = normalised.initialOdometerKm,
                                currentOdometerKm = normalised.initialOdometerKm,
                                brand = normalised.brand,
                                model = normalised.model,
                                fuelType = normalised.fuelType,
                                createdAt = now,
                                updatedAt = now,
                                serverUpdatedAt = null,
                                deletedAt = null,
                                syncState = PENDING,
                                localRevision = 1,
                                localMutationSeq = 0,
                                schemaVersion = CLIENT_MAX_SCHEMA_VERSION.toLong(),
                            )
                        insertVehicle(vehicle, vehicle.toVehicleOutboxPayloadOrNull())
                        Outcome.Ok(id)
                    }
                }
            }
        }

    override suspend fun updateVehicle(command: UpdateVehicleCommand): Outcome<Unit, AppError> =
        runWrite {
            val ownerId = ownerContext.current
            val now = clock.now()
            localDataSource.writeTransaction {
                val existing =
                    vehicle(ownerId, command.id)
                        ?: return@writeTransaction Outcome.Err(ValidationError.EntityNotFound)
                if (existing.deletedAt != null) {
                    return@writeTransaction Outcome.Err(ValidationError.EntityDeleted)
                }
                when (
                    val validation =
                        validateUpdate(
                            command,
                            UpdateVehicleValidationContext(
                                activeVehicles = activeVehicleCandidates(ownerId),
                                hasNonDeletedFuelEntries = hasActiveFuelEntries(command.id),
                            ),
                        )
                ) {
                    is Outcome.Err -> {
                        validation
                    }

                    is Outcome.Ok -> {
                        val normalised = validation.value
                        val updated =
                            existing.copy(
                                ownerId = ownerId,
                                name = normalised.name,
                                nameFold = normalised.name.lowercase(),
                                initialOdometerKm =
                                    normalised.initialOdometerKm ?: existing.initialOdometerKm,
                                brand = normalised.brand,
                                model = normalised.model,
                                fuelType = normalised.fuelType,
                                updatedAt = now,
                                syncState = PENDING,
                                localRevision = existing.localRevision + 1,
                            )
                        updateVehicle(updated, updated.toVehicleOutboxPayloadOrNull())
                        Outcome.Ok(Unit)
                    }
                }
            }
        }

    override suspend fun deleteVehicle(id: EntityId): Outcome<Unit, AppError> =
        runWrite {
            val ownerId = ownerContext.current
            val now = clock.now()
            localDataSource.writeTransaction {
                val existing =
                    vehicle(ownerId, id)
                        ?: return@writeTransaction Outcome.Err(ValidationError.EntityNotFound)
                if (existing.deletedAt != null) return@writeTransaction Outcome.Ok(Unit)

                val tombstone =
                    existing.copy(
                        ownerId = ownerId,
                        updatedAt = now,
                        deletedAt = now,
                        syncState = PENDING,
                        localRevision = existing.localRevision + 1,
                    )
                tombstoneVehicleCascade(
                    vehicle = tombstone,
                    vehicleOutboxPayload = tombstone.toVehicleOutboxPayloadOrNull(),
                    fuelEntryOutboxPayload = { entry ->
                        if (ownerId == LOCAL_OWNER) {
                            null
                        } else {
                            entry.toFuelEntryTombstonePayload(
                                ownerId = ownerId.value,
                                timestamp = now.toEpochMilliseconds(),
                            )
                        }
                    },
                )
                Outcome.Ok(Unit)
            }
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

private const val PENDING = "PENDING"
