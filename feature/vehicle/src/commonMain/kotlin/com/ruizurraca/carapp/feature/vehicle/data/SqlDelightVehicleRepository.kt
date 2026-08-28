package com.ruizurraca.carapp.feature.vehicle.data

import com.ruizurraca.carapp.core.common.AppClock
import com.ruizurraca.carapp.core.common.AppError
import com.ruizurraca.carapp.core.common.CLIENT_MAX_SCHEMA_VERSION
import com.ruizurraca.carapp.core.common.Outcome
import com.ruizurraca.carapp.core.common.OwnerContext
import com.ruizurraca.carapp.core.common.PersistenceError
import com.ruizurraca.carapp.core.common.UuidGenerator
import com.ruizurraca.carapp.core.database.AppDatabase
import com.ruizurraca.carapp.core.database.Fuel_entry
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
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

class SqlDelightVehicleRepository internal constructor(
    private val localDataSource: VehicleLocalDataSource,
    private val ownerContext: OwnerContext,
    private val clock: AppClock,
    private val uuidGenerator: UuidGenerator,
) : VehicleRepository {
    private val validateCreate = ValidateCreateVehicle()
    private val validateUpdate = ValidateUpdateVehicle()

    constructor(
        database: AppDatabase,
        ownerContext: OwnerContext,
        clock: AppClock,
        uuidGenerator: UuidGenerator,
    ) : this(
        localDataSource = SqlDelightVehicleLocalDataSource(database),
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
                    is Outcome.Err -> validation
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
                        insertVehicle(vehicle, vehicle.outboxPayloadOrNull())
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
                val existing = vehicle(ownerId, command.id)
                    ?: return@writeTransaction Outcome.Err(com.ruizurraca.carapp.core.common.ValidationError.EntityNotFound)
                if (existing.deletedAt != null) {
                    return@writeTransaction Outcome.Err(com.ruizurraca.carapp.core.common.ValidationError.EntityDeleted)
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
                    is Outcome.Err -> validation
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
                        updateVehicle(updated, updated.outboxPayloadOrNull())
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
                val existing = vehicle(ownerId, id)
                    ?: return@writeTransaction Outcome.Err(com.ruizurraca.carapp.core.common.ValidationError.EntityNotFound)
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
                    vehicleOutboxPayload = tombstone.outboxPayloadOrNull(),
                    fuelEntryOutboxPayload = { entry ->
                        if (ownerId == LOCAL_OWNER) null else entry.toTombstonePayload(ownerId.value, now.toEpochMilliseconds())
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

private fun LocalVehicle.outboxPayloadOrNull(): String? =
    if (ownerId == LOCAL_OWNER) null else toOutboxPayload()

private fun LocalVehicle.toOutboxPayload(): String =
    buildJsonObject {
        put("id", id.value)
        put("ownerId", ownerId.value)
        put("name", name)
        put("initialOdometerKm", initialOdometerKm)
        put("brand", brand?.let(::JsonPrimitive) ?: JsonNull)
        put("model", model?.let(::JsonPrimitive) ?: JsonNull)
        put("fuelType", fuelType.name)
        put("createdAt", createdAt.toEpochMilliseconds())
        put("updatedAt", updatedAt.toEpochMilliseconds())
        put("deleted", deletedAt != null)
        put("deletedAt", deletedAt?.let { JsonPrimitive(it.toEpochMilliseconds()) } ?: JsonNull)
        put("schemaVersion", schemaVersion)
    }.toString()

private fun Fuel_entry.toTombstonePayload(
    ownerId: String,
    timestamp: Long,
): String =
    buildJsonObject {
        put("id", id)
        put("ownerId", ownerId)
        put("vehicleId", vehicleId)
        put("date", date)
        put("odometerKm", odometerKm)
        put("litersScaled", litersScaled)
        put("pricePerLiterScaled", pricePerLiterScaled)
        put("totalCostMinor", totalCostMinor)
        put("currency", currency)
        put("isFullTank", isFullTank != 0L)
        put("hasMissedEntries", hasMissedEntries != 0L)
        put("odometerInconsistent", odometerInconsistent != 0L)
        put("notes", notes?.let(::JsonPrimitive) ?: JsonNull)
        put("createdAt", createdAt)
        put("updatedAt", timestamp)
        put("deleted", true)
        put("deletedAt", timestamp)
        put("schemaVersion", schemaVersion)
    }.toString()

private const val PENDING = "PENDING"
