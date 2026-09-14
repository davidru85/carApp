package com.ruizurraca.carapp.integration.firebase.firestore

import com.ruizurraca.carapp.core.common.Outcome
import com.ruizurraca.carapp.core.common.RemoteError
import com.ruizurraca.carapp.core.common.instantFromEpochMicroseconds
import com.ruizurraca.carapp.core.model.EntityId
import com.ruizurraca.carapp.core.model.OwnerId
import com.ruizurraca.carapp.core.sync.EntitySnapshot
import com.ruizurraca.carapp.core.sync.EntityType
import com.ruizurraca.carapp.core.sync.RemoteAck
import com.ruizurraca.carapp.core.sync.RemoteCursor
import com.ruizurraca.carapp.core.sync.RemoteDocument
import com.ruizurraca.carapp.core.sync.RemotePage
import com.ruizurraca.carapp.core.sync.RemoteSyncSource
import dev.gitlive.firebase.Firebase
import dev.gitlive.firebase.FirebaseException
import dev.gitlive.firebase.auth.FirebaseAuth
import dev.gitlive.firebase.auth.auth
import dev.gitlive.firebase.firestore.Direction
import dev.gitlive.firebase.firestore.DocumentSnapshot
import dev.gitlive.firebase.firestore.FieldPath
import dev.gitlive.firebase.firestore.FirebaseFirestore
import dev.gitlive.firebase.firestore.FirebaseFirestoreException
import dev.gitlive.firebase.firestore.Timestamp
import dev.gitlive.firebase.firestore.code
import dev.gitlive.firebase.firestore.firestore
import dev.gitlive.firebase.firestore.firestoreSettings
import dev.gitlive.firebase.firestore.fromMilliseconds
import dev.gitlive.firebase.firestore.memoryCacheSettings
import dev.gitlive.firebase.firestore.memoryEagerGcSettings
import dev.gitlive.firebase.firestore.toMilliseconds
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonArray
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
    ): Outcome<RemoteAck, RemoteError> {
        val write =
            try {
                snapshot.toFirestoreWrite(ownerId)
            } catch (failure: IllegalArgumentException) {
                return Outcome.Err(RemoteError.InvalidArgument)
            }
        return runRemoteOperation {
            val serverUpdatedAt = gateway.writeDocument(write)
            RemoteAck(
                entityType = snapshot.entityType,
                entityId = snapshot.entityId,
                serverUpdatedAt = serverUpdatedAt,
            )
        }
    }

    @Suppress("SwallowedException") // Provider failures are deliberately converted to the closed RemoteError API.
    override suspend fun pullChanges(
        ownerId: OwnerId,
        entityType: EntityType,
        cursor: RemoteCursor,
        limit: Int,
    ): Outcome<RemotePage, RemoteError> {
        val query =
            try {
                require(limit > 0)
                FirestoreQuery(
                    path = "users/${ownerId.value}/${entityType.collection}",
                    entityType = entityType,
                    updatedAtOrAfter = cursor.lastServerUpdatedAt,
                    afterDocumentId = cursor.lastDocumentId?.value,
                    limit = limit,
                )
            } catch (failure: IllegalArgumentException) {
                return Outcome.Err(RemoteError.InvalidArgument)
            }
        return runRemoteOperation {
            val documents = gateway.queryDocuments(query)
            val items = documents.map { document -> document.toRemoteDocument(entityType) }
            val last = items.lastOrNull()
            RemotePage(
                items = items,
                nextCursor =
                    last?.let { item ->
                        RemoteCursor(item.serverUpdatedAt, item.documentId)
                    } ?: cursor,
                hasMore = items.size == limit,
            )
        }
    }

    /**
     * Converts a transport document into a [RemoteDocument] without decoding or asserting any
     * product field (`D-170`, `§9.5`). The only strongly-read value is the transport metadata this
     * type genuinely needs: the ordering `updatedAt` timestamp. Every other field is serialized
     * verbatim, so a missing or mistyped product field reaches `:core:sync` as raw JSON and is
     * classified there. A document whose ordering timestamp is missing or unusable fails the page as
     * `RemoteError.InvalidArgument` rather than being silently dropped.
     */
    private fun FirestoreDocument.toRemoteDocument(entityType: EntityType): RemoteDocument {
        val orderingMicros =
            orderingUpdatedAtMicros
                ?: throw IllegalArgumentException("$UPDATED_AT_FIELD must be a provider timestamp")
        return RemoteDocument(
            entityType = entityType,
            documentId = EntityId(id),
            serverUpdatedAt = instantFromEpochMicroseconds(orderingMicros),
            rawJson = fields.toRawJson(),
        )
    }

    @Suppress("SwallowedException") // Gateway failures are deliberately converted to the closed RemoteError API.
    private suspend fun <T> runRemoteOperation(operation: suspend () -> T): Outcome<T, RemoteError> =
        try {
            Outcome.Ok(operation())
        } catch (failure: FirestoreGatewayException) {
            if (failure.failure == FirestoreGatewayFailure.UNAUTHENTICATED) {
                refreshAndRetry(operation)
            } else {
                Outcome.Err(failure.failure.toRemoteError())
            }
        } catch (failure: IllegalArgumentException) {
            Outcome.Err(RemoteError.InvalidArgument)
        }

    @Suppress("SwallowedException") // Gateway failures are deliberately converted to the closed RemoteError API.
    private suspend fun <T> refreshAndRetry(operation: suspend () -> T): Outcome<T, RemoteError> =
        try {
            gateway.refreshAuthToken()
            Outcome.Ok(operation())
        } catch (failure: FirestoreGatewayException) {
            Outcome.Err(failure.failure.toRemoteError())
        } catch (failure: IllegalArgumentException) {
            Outcome.Err(RemoteError.InvalidArgument)
        }
}

internal interface FirestoreGateway {
    fun configureMemoryOnlyCache()

    suspend fun writeDocument(write: FirestoreWrite): Instant

    suspend fun queryDocuments(query: FirestoreQuery): List<FirestoreDocument>

    suspend fun refreshAuthToken()
}

/**
 * A pulled document as product-neutral transport data (`D-170`): the document id and the provider's
 * untyped field map, with values normalized to JSON-friendly Kotlin types and timestamps converted
 * to epoch milliseconds. The gateway produces these; `FirebaseRemoteSyncSource` maps them and never
 * decodes a product field, so `§9.5` validation belongs to `:core:sync`.
 */
internal enum class FirestoreGatewayFailure {
    UNAVAILABLE,
    DEADLINE_EXCEEDED,
    PERMISSION_DENIED,
    UNAUTHENTICATED,
    INVALID_ARGUMENT,
    NOT_FOUND,
    UNKNOWN,
}

internal class FirestoreGatewayException(
    val failure: FirestoreGatewayFailure,
    cause: Throwable? = null,
) : Exception(cause)

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

/**
 * A pulled document as product-neutral transport data (`D-170`, `D-174`): the document id, the raw
 * product field map with values normalized to JSON-friendly Kotlin types, and the ordering
 * `updatedAt` at the provider's full microsecond precision. Product fields, including the `updatedAt`
 * value serialized into `rawJson`, stay epoch milliseconds; only [orderingUpdatedAtMicros] carries
 * the sub-millisecond component the pull cursor needs.
 */
internal data class FirestoreDocument(
    val id: String,
    val fields: Map<String, Any?>,
    val orderingUpdatedAtMicros: Long?,
)

/**
 * The provider's untyped field map for this snapshot, with provider values normalized to
 * JSON-friendly Kotlin types (String, Long, Double, Boolean, Map, List, null) and timestamps
 * converted to epoch milliseconds. Platform-specific because GitLive 2.6 exposes no neutral
 * field-map accessor; each integration target reads its own provider map. It MUST NOT decode or
 * assert product fields (`D-170`, `§9.5`).
 */
internal expect fun DocumentSnapshot.untypedFields(): Map<String, Any?>

/**
 * The `updatedAt` ordering timestamp at the provider's full precision, as epoch microseconds
 * (`D-174`). Firestore server timestamps carry microsecond precision; truncating them to
 * milliseconds makes a later-page `startAfter` boundary non-exclusive, so the ordering cursor
 * MUST carry this value. Returns `null` when the field is absent or not a provider timestamp, which
 * the caller turns into a closed page failure (the ordering timestamp is transport metadata).
 *
 * This is deliberately separate from [untypedFields]: product fields, including the `updatedAt`
 * value serialized into `rawJson`, stay epoch milliseconds per `§8` and `§20.7`, so only the
 * ordering cursor gains precision.
 */
internal expect fun DocumentSnapshot.orderingUpdatedAtMicros(): Long?

/**
 * Builds the transport document from a provider snapshot without decoding any product field. It reads
 * the provider's untyped field map plus the ordering `updatedAt` at full precision (`D-174`); the
 * ordering-timestamp validation happens when the engine document is materialized, so a missing or
 * mistyped product field is carried through verbatim.
 */
internal fun DocumentSnapshot.toFirestoreDocument(): FirestoreDocument =
    FirestoreDocument(
        id = id,
        fields = untypedFields(),
        orderingUpdatedAtMicros = orderingUpdatedAtMicros(),
    )

/**
 * Serializes the untyped product fields to JSON without decoding or asserting any of them. The
 * provider-free value set is String, Long, Double, Boolean, Map, List and null.
 */
internal fun Map<String, Any?>.toRawJson(): String =
    buildJsonObject {
        forEach { (field, value) ->
            put(field, value.toJsonElement())
        }
    }.toString()

private fun Any?.toJsonElement(): JsonElement =
    when (this) {
        null -> {
            JsonNull
        }

        is String -> {
            JsonPrimitive(this)
        }

        is Boolean -> {
            JsonPrimitive(this)
        }

        is Long -> {
            JsonPrimitive(this)
        }

        is Int -> {
            JsonPrimitive(this)
        }

        is Double -> {
            JsonPrimitive(this)
        }

        is Map<*, *> -> {
            buildJsonObject {
                forEach { (key, value) ->
                    put(
                        requireNotNull(key as? String) { "Non-string map key in remote document" },
                        value.toJsonElement(),
                    )
                }
            }
        }

        is List<*> -> {
            buildJsonArray { this@toJsonElement.forEach { add(it.toJsonElement()) } }
        }

        else -> {
            throw IllegalArgumentException("Unsupported remote field type: ${this::class.simpleName}")
        }
    }

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
    private val auth: FirebaseAuth = Firebase.auth,
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

    override suspend fun writeDocument(write: FirestoreWrite): Instant =
        runProviderOperation {
            val reference = firestore.document(write.path)
            reference.set(write.fields.mapValues { (_, value) -> value.toProviderValue() })
            val timestamp = reference.get().get<Timestamp>(UPDATED_AT_FIELD)
            // Full precision (`D-174`): the acknowledged server timestamp drives the local
            // `serverUpdatedAt` that the next pull compares against.
            instantFromEpochMicroseconds(
                timestamp.seconds * MICROS_PER_SECOND + timestamp.nanoseconds / NANOS_PER_MICROSECOND,
            )
        }

    override suspend fun queryDocuments(query: FirestoreQuery): List<FirestoreDocument> =
        runProviderOperation {
            // The boundary keeps the provider's full microsecond precision (`D-174`). Truncating it to
            // milliseconds would make `startAfter` compare a truncated value against the stored
            // microsecond one, so the last document of the previous page sorts after the boundary and
            // is re-delivered.
            val boundary = query.updatedAtOrAfter.toProviderTimestamp()
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
            firestoreQuery
                .limit(query.limit)
                .get()
                .documents
                .map { document -> document.toFirestoreDocument() }
        }

    override suspend fun refreshAuthToken() {
        runProviderRefresh {
            val currentUser =
                auth.currentUser
                    ?: throw FirestoreGatewayException(FirestoreGatewayFailure.UNAUTHENTICATED)
            currentUser.getIdToken(forceRefresh = true)
        }
    }

    @Suppress("SwallowedException")
    private suspend fun <T> runProviderOperation(operation: suspend () -> T): T =
        try {
            operation()
        } catch (failure: CancellationException) {
            throw failure
        } catch (failure: FirebaseFirestoreException) {
            throw FirestoreGatewayException(failure.code.toGatewayFailure(), failure)
        } catch (failure: FirebaseException) {
            throw FirestoreGatewayException(FirestoreGatewayFailure.UNKNOWN, failure)
        }
}

/**
 * Runs the provider token refresh inside the closed failure vocabulary.
 *
 * `auth.currentUser` is evaluated inside this region on purpose: acquiring the handle can itself
 * throw a provider throwable, and a refresh failure that escapes as an unchecked exception would
 * break the closed `Outcome` API of `pushSnapshot` and `pullChanges`. A Firebase failure maps to
 * `UNAUTHENTICATED`, any other throwable to `UNKNOWN`, and cancellation always propagates. This
 * scope is deliberately narrower than the payload conversion path, whose escape is `D-170`.
 */
@Suppress("TooGenericExceptionCaught")
internal suspend fun <T> runProviderRefresh(operation: suspend () -> T): T =
    try {
        operation()
    } catch (failure: CancellationException) {
        throw failure
    } catch (failure: FirestoreGatewayException) {
        throw failure
    } catch (failure: FirebaseException) {
        throw FirestoreGatewayException(FirestoreGatewayFailure.UNAUTHENTICATED, failure)
    } catch (failure: Throwable) {
        throw FirestoreGatewayException(FirestoreGatewayFailure.UNKNOWN, failure)
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

/**
 * The provider `Timestamp` for this instant at full precision (`D-174`). `Timestamp(seconds,
 * nanoseconds)` preserves the sub-millisecond component, which `fromMilliseconds` would drop.
 */
private fun Instant.toProviderTimestamp(): Timestamp = Timestamp(epochSeconds, nanosecondsOfSecond)

private fun FirestoreGatewayFailure.toRemoteError(): RemoteError =
    when (this) {
        FirestoreGatewayFailure.UNAVAILABLE -> RemoteError.Unavailable
        FirestoreGatewayFailure.DEADLINE_EXCEEDED -> RemoteError.DeadlineExceeded
        FirestoreGatewayFailure.PERMISSION_DENIED -> RemoteError.PermissionDenied
        FirestoreGatewayFailure.UNAUTHENTICATED -> RemoteError.Unauthenticated
        FirestoreGatewayFailure.INVALID_ARGUMENT -> RemoteError.InvalidArgument
        FirestoreGatewayFailure.NOT_FOUND -> RemoteError.NotFound
        FirestoreGatewayFailure.UNKNOWN -> RemoteError.Unknown
    }

private const val ENTITY_TYPE_FIELD = "entityType"
private const val ID_FIELD = "id"
private const val OWNER_ID_FIELD = "ownerId"
private const val SCHEMA_VERSION_FIELD = "schemaVersion"
private const val UPDATED_AT_FIELD = "updatedAt"
private const val MICROS_PER_SECOND = 1_000_000L
private const val NANOS_PER_MICROSECOND = 1_000L
private val EPOCH_MILLISECOND_FIELDS = setOf("createdAt", "date", "deletedAt")
