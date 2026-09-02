package com.ruizurraca.carapp.integration.firebase.firestore

import com.ruizurraca.carapp.core.common.Outcome
import com.ruizurraca.carapp.core.common.RemoteError
import com.ruizurraca.carapp.core.model.OwnerId
import com.ruizurraca.carapp.core.sync.EntitySnapshot
import com.ruizurraca.carapp.core.sync.EntityType
import com.ruizurraca.carapp.core.sync.RemoteAck
import com.ruizurraca.carapp.core.sync.RemoteCursor
import com.ruizurraca.carapp.core.sync.RemotePage
import com.ruizurraca.carapp.core.sync.RemoteSnapshot
import com.ruizurraca.carapp.core.sync.RemoteSyncSource
import dev.gitlive.firebase.Firebase
import dev.gitlive.firebase.FirebaseException
import dev.gitlive.firebase.firestore.Direction
import dev.gitlive.firebase.firestore.DocumentSnapshot
import dev.gitlive.firebase.firestore.FieldPath
import dev.gitlive.firebase.firestore.FirebaseFirestore
import dev.gitlive.firebase.firestore.FirebaseFirestoreException
import dev.gitlive.firebase.firestore.FirestoreExceptionCode
import dev.gitlive.firebase.firestore.Timestamp
import dev.gitlive.firebase.firestore.code
import dev.gitlive.firebase.firestore.firestore
import dev.gitlive.firebase.firestore.firestoreSettings
import dev.gitlive.firebase.firestore.fromMilliseconds
import dev.gitlive.firebase.firestore.memoryCacheSettings
import dev.gitlive.firebase.firestore.memoryEagerGcSettings
import dev.gitlive.firebase.firestore.toMilliseconds
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put
import kotlin.time.Instant

/** Firebase remote replica with persistent client caching disabled by construction (`D-10`). */
class FirebaseRemoteSyncSource internal constructor(
    private val gateway: FirestoreGateway,
) : RemoteSyncSource {
    constructor() : this(GitLiveFirestoreGateway())

    init {
        gateway.configureMemoryOnlyCache()
    }

    @Suppress("SwallowedException") // Provider failures are deliberately converted to the closed RemoteError API.
    override suspend fun pushSnapshot(
        ownerId: OwnerId,
        snapshot: EntitySnapshot,
    ): Outcome<RemoteAck, RemoteError> =
        try {
            val serverUpdatedAt = gateway.writeDocument(snapshot.toFirestoreWrite(ownerId))
            Outcome.Ok(
                RemoteAck(
                    entityType = snapshot.entityType,
                    entityId = snapshot.entityId,
                    serverUpdatedAt = serverUpdatedAt,
                ),
            )
        } catch (failure: IllegalArgumentException) {
            Outcome.Err(RemoteError.InvalidArgument)
        } catch (failure: FirebaseFirestoreException) {
            Outcome.Err(failure.code.toRemoteError())
        } catch (failure: FirebaseException) {
            Outcome.Err(RemoteError.Unknown)
        }

    @Suppress("SwallowedException") // Provider failures are deliberately converted to the closed RemoteError API.
    override suspend fun pullChanges(
        ownerId: OwnerId,
        entityType: EntityType,
        cursor: RemoteCursor,
        limit: Int,
    ): Outcome<RemotePage, RemoteError> =
        try {
            require(limit > 0)
            val documents =
                gateway.queryDocuments(
                    FirestoreQuery(
                        path = "users/${ownerId.value}/${entityType.collection}",
                        entityType = entityType,
                        updatedAtOrAfter = cursor.lastServerUpdatedAt,
                        afterDocumentId = cursor.lastDocumentId?.value,
                        limit = limit,
                    ),
                )
            val items = documents.map { document -> document.toRemoteSnapshot(entityType) }
            val last = items.lastOrNull()
            Outcome.Ok(
                RemotePage(
                    items = items,
                    nextCursor =
                        last?.let { item ->
                            RemoteCursor(item.serverUpdatedAt, item.entityId)
                        } ?: cursor,
                    hasMore = items.size == limit,
                ),
            )
        } catch (failure: IllegalArgumentException) {
            Outcome.Err(RemoteError.InvalidArgument)
        } catch (failure: FirebaseFirestoreException) {
            Outcome.Err(failure.code.toRemoteError())
        } catch (failure: FirebaseException) {
            Outcome.Err(RemoteError.Unknown)
        }
}

internal interface FirestoreGateway {
    fun configureMemoryOnlyCache()

    suspend fun writeDocument(write: FirestoreWrite): Instant

    suspend fun queryDocuments(query: FirestoreQuery): List<FirestoreDocument>
}

internal data class FirestoreWrite(
    val path: String,
    val fields: Map<String, FirestoreValue>,
)

internal data class FirestoreQuery(
    val path: String,
    val entityType: EntityType,
    val updatedAtOrAfter: Instant,
    val afterDocumentId: String?,
    val limit: Int,
)

internal data class FirestoreDocument(
    val id: String,
    val fields: Map<String, FirestoreValue>,
)

internal sealed interface FirestoreValue

internal data class FirestoreString(
    val value: String,
) : FirestoreValue

internal data class FirestoreLong(
    val value: Long,
) : FirestoreValue

internal data class FirestoreBoolean(
    val value: Boolean,
) : FirestoreValue

internal data class FirestoreTimestamp(
    val epochMilliseconds: Long,
) : FirestoreValue

internal data object FirestoreServerTimestamp : FirestoreValue

internal data object FirestoreNull : FirestoreValue

private class GitLiveFirestoreGateway(
    private val firestore: FirebaseFirestore = Firebase.firestore,
) : FirestoreGateway {
    override fun configureMemoryOnlyCache() {
        firestore.settings =
            firestoreSettings {
                cacheSettings =
                    memoryCacheSettings {
                        gcSettings = memoryEagerGcSettings { }
                    }
            }
    }

    override suspend fun writeDocument(write: FirestoreWrite): Instant {
        val reference = firestore.document(write.path)
        reference.set(write.fields.mapValues { (_, value) -> value.toProviderValue() })
        val timestamp = reference.get().get<Timestamp>(UPDATED_AT_FIELD)
        return Instant.fromEpochMilliseconds(timestamp.toMilliseconds().toLong())
    }

    override suspend fun queryDocuments(query: FirestoreQuery): List<FirestoreDocument> {
        val boundary = query.updatedAtOrAfter.toFirestoreTimestamp()
        var firestoreQuery =
            firestore
                .collection(query.path)
                .where { UPDATED_AT_FIELD greaterThanOrEqualTo boundary }
                .orderBy(UPDATED_AT_FIELD, Direction.ASCENDING)
                .orderBy(FieldPath.documentId, Direction.ASCENDING)
        firestoreQuery =
            query.afterDocumentId?.let { documentId ->
                firestoreQuery.startAfterFieldValues {
                    add(boundary)
                    add(documentId)
                }
            } ?: firestoreQuery.startAtFieldValues { add(boundary) }
        return firestoreQuery
            .limit(query.limit)
            .get()
            .documents
            .map { document -> document.toFirestoreDocument(query.entityType) }
    }
}

private fun EntitySnapshot.toFirestoreWrite(ownerId: OwnerId): FirestoreWrite {
    val jsonObject = Json.parseToJsonElement(json).jsonObject
    require(jsonObject.getValue(ID_FIELD).jsonPrimitive.content == entityId.value)
    require(jsonObject.getValue(OWNER_ID_FIELD).jsonPrimitive.content == ownerId.value)
    require(jsonObject.getValue(SCHEMA_VERSION_FIELD).jsonPrimitive.longOrNull == schemaVersion.toLong())
    val payloadEntityType = jsonObject[ENTITY_TYPE_FIELD]?.jsonPrimitive?.content
    require(payloadEntityType == entityType.name)
    return FirestoreWrite(
        path = "users/${ownerId.value}/${entityType.collection}/${entityId.value}",
        fields =
            jsonObject
                .filterKeys { it != ENTITY_TYPE_FIELD }
                .mapValues { (field, value) ->
                    value.toFirestoreValue(field)
                },
    )
}

private fun JsonElement.toFirestoreValue(field: String): FirestoreValue =
    when {
        this === JsonNull -> {
            FirestoreNull
        }

        field == UPDATED_AT_FIELD -> {
            FirestoreServerTimestamp
        }

        field in EPOCH_MILLISECOND_FIELDS -> {
            FirestoreTimestamp(
                requireNotNull(jsonPrimitive.longOrNull) { "$field must be epoch milliseconds" },
            )
        }

        this is JsonPrimitive && isString -> {
            FirestoreString(content)
        }

        this is JsonPrimitive && booleanOrNull != null -> {
            FirestoreBoolean(requireNotNull(booleanOrNull))
        }

        this is JsonPrimitive && longOrNull != null -> {
            FirestoreLong(requireNotNull(longOrNull))
        }

        else -> {
            throw IllegalArgumentException("Unsupported Firestore payload field: $field")
        }
    }

private fun FirestoreValue.toProviderValue(): Any? =
    when (this) {
        is FirestoreString -> value
        is FirestoreLong -> value
        is FirestoreBoolean -> value
        is FirestoreTimestamp -> Timestamp.fromMilliseconds(epochMilliseconds.toDouble())
        FirestoreServerTimestamp -> Timestamp.ServerTimestamp
        FirestoreNull -> null
    }

private fun FirestoreDocument.toRemoteSnapshot(entityType: EntityType): RemoteSnapshot {
    require(fields.getValue(ID_FIELD) == FirestoreString(id))
    val schemaVersion = (fields.getValue(SCHEMA_VERSION_FIELD) as FirestoreLong).value.toInt()
    val serverUpdatedAt = (fields.getValue(UPDATED_AT_FIELD) as FirestoreTimestamp).epochMilliseconds
    val deleted = (fields.getValue(DELETED_FIELD) as FirestoreBoolean).value
    return RemoteSnapshot(
        entityType = entityType,
        entityId =
            com.ruizurraca.carapp.core.model
                .EntityId(id),
        schemaVersion = schemaVersion,
        serverUpdatedAt = Instant.fromEpochMilliseconds(serverUpdatedAt),
        deleted = deleted,
        json = fields.toSnapshotJson(),
    )
}

private fun Map<String, FirestoreValue>.toSnapshotJson(): String =
    buildJsonObject {
        this@toSnapshotJson.forEach { (field, value) ->
            when (value) {
                is FirestoreString -> {
                    put(field, value.value)
                }

                is FirestoreLong -> {
                    put(field, value.value)
                }

                is FirestoreBoolean -> {
                    put(field, value.value)
                }

                is FirestoreTimestamp -> {
                    put(field, value.epochMilliseconds)
                }

                FirestoreNull -> {
                    put(field, JsonNull)
                }

                FirestoreServerTimestamp -> {
                    throw IllegalArgumentException("Unresolved server timestamp in remote snapshot")
                }
            }
        }
    }.toString()

private fun DocumentSnapshot.toFirestoreDocument(entityType: EntityType): FirestoreDocument =
    when (entityType) {
        EntityType.VEHICLE -> {
            FirestoreDocument(
                id = id,
                fields =
                    mapOf(
                        ID_FIELD to FirestoreString(get(ID_FIELD)),
                        OWNER_ID_FIELD to FirestoreString(get(OWNER_ID_FIELD)),
                        "name" to FirestoreString(get("name")),
                        "initialOdometerKm" to FirestoreLong(get("initialOdometerKm")),
                        "brand" to getNullableString("brand"),
                        "model" to getNullableString("model"),
                        "fuelType" to FirestoreString(get("fuelType")),
                        "createdAt" to getTimestamp("createdAt"),
                        UPDATED_AT_FIELD to getTimestamp(UPDATED_AT_FIELD),
                        DELETED_FIELD to FirestoreBoolean(get(DELETED_FIELD)),
                        "deletedAt" to getNullableTimestamp("deletedAt"),
                        SCHEMA_VERSION_FIELD to FirestoreLong(get(SCHEMA_VERSION_FIELD)),
                    ),
            )
        }

        EntityType.FUEL_ENTRY -> {
            throw IllegalArgumentException("FuelEntry pull belongs to E3-02")
        }
    }

private fun DocumentSnapshot.getNullableString(field: String): FirestoreValue =
    get<String?>(field)?.let(::FirestoreString) ?: FirestoreNull

private fun DocumentSnapshot.getTimestamp(field: String): FirestoreTimestamp =
    FirestoreTimestamp(get<Timestamp>(field).toMilliseconds().toLong())

private fun DocumentSnapshot.getNullableTimestamp(field: String): FirestoreValue =
    get<Timestamp?>(field)?.let { value -> FirestoreTimestamp(value.toMilliseconds().toLong()) } ?: FirestoreNull

private fun Instant.toFirestoreTimestamp(): Timestamp = Timestamp.fromMilliseconds(toEpochMilliseconds().toDouble())

private fun FirestoreExceptionCode.toRemoteError(): RemoteError =
    when (this) {
        FirestoreExceptionCode.UNAVAILABLE -> RemoteError.Unavailable
        FirestoreExceptionCode.DEADLINE_EXCEEDED -> RemoteError.DeadlineExceeded
        FirestoreExceptionCode.PERMISSION_DENIED -> RemoteError.PermissionDenied
        FirestoreExceptionCode.UNAUTHENTICATED -> RemoteError.Unauthenticated
        FirestoreExceptionCode.INVALID_ARGUMENT -> RemoteError.InvalidArgument
        FirestoreExceptionCode.NOT_FOUND -> RemoteError.NotFound
        else -> RemoteError.Unknown
    }

private const val ENTITY_TYPE_FIELD = "entityType"
private const val ID_FIELD = "id"
private const val OWNER_ID_FIELD = "ownerId"
private const val SCHEMA_VERSION_FIELD = "schemaVersion"
private const val UPDATED_AT_FIELD = "updatedAt"
private const val DELETED_FIELD = "deleted"
private val EPOCH_MILLISECOND_FIELDS = setOf("createdAt", "date", "deletedAt")
