@file:OptIn(kotlin.experimental.ExperimentalObjCRefinement::class)

package com.ruizurraca.carapp.core.testing

import com.ruizurraca.carapp.core.common.Outcome
import com.ruizurraca.carapp.core.common.RemoteError
import com.ruizurraca.carapp.core.common.toEpochMicroseconds
import com.ruizurraca.carapp.core.model.EntityId
import com.ruizurraca.carapp.core.model.OwnerId
import com.ruizurraca.carapp.core.sync.EntitySnapshot
import com.ruizurraca.carapp.core.sync.EntityType
import com.ruizurraca.carapp.core.sync.RemoteAck
import com.ruizurraca.carapp.core.sync.RemoteCursor
import com.ruizurraca.carapp.core.sync.RemoteDocument
import com.ruizurraca.carapp.core.sync.RemotePage
import com.ruizurraca.carapp.core.sync.RemoteSyncSource
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put
import kotlin.native.HiddenFromObjC
import kotlin.time.Instant

/**
 * An in-memory backup replica whose pull semantics match Firestore, for tests that must move product
 * data between two local databases through the production sync path.
 *
 * [FakeRemoteSyncSource] is intentionally scripted: it answers with whatever a test hands it, so it
 * cannot show that one device's committed write is recoverable by another. This fake closes that
 * gap. `pushSnapshot` stores the pushed document under the owner path
 * `users/{ownerId}/{collection}/{entityId}`, assigning the server-owned `updatedAt` the way the
 * provider does; `pullChanges` then serves those stored documents back in the provider's order.
 *
 * A push whose payload disagrees with its snapshot is rejected as `RemoteError.InvalidArgument`, the
 * way `toFirestoreWrite` rejects it before writing anything.
 *
 * Three behaviours are modelled because the engine and `docs/CONTRACTS.md §9.4` depend on all three:
 *
 * - **The server owns `updatedAt`.** The pushed payload's own `updatedAt` is discarded and replaced
 *   by the timestamp the write commits at, so a device that wrote with a clock an hour ahead does not
 *   win conflicts and a restored row carries the server's ordering value. The replacement happens
 *   inside the stored document, which is what the provider actually does.
 * - **Ordering is the total order `(updatedAt, documentId)`.** Paging is a strict comparison against
 *   that pair, never an index offset, so a page boundary cannot silently skip or repeat a document
 *   whose timestamp ties with the boundary.
 * - **Owners are isolated by path.** A pull under one owner never sees another owner's documents,
 *   which is what makes "the same permanent identity recovers its own backup" a meaningful
 *   assertion rather than a coincidence of a shared list.
 *
 * Delivery truncation is modelled by [deliverUpdatedAtTruncatedToMillis], mirroring the seam
 * `:core:sync`'s own fake exposes: the stored ordering key keeps full microsecond precision, and only
 * what is *delivered* into [RemoteDocument] is reduced. The two are deliberately separate, because a
 * truncated delivery boundary is the shape that re-delivers a document; keeping the stored key intact
 * is what lets a test observe that.
 *
 * Push results can be scripted through [nextPushResult] to exercise the retry and failure paths.
 */
@HiddenFromObjC
class InMemoryRemoteSyncSource(
    private val clock: () -> Instant = { FakeAppClock.DEFAULT_NOW },
) : RemoteSyncSource {
    private val documents = mutableMapOf<String, MutableList<StoredDocument>>()
    private val pushResults = ArrayDeque<Outcome<RemoteAck, RemoteError>>()

    /** Every push this replica received, in order, for assertions about what was backed up. */
    val pushCalls = mutableListOf<EntitySnapshot>()

    /** Every pull page request this replica received, in order. */
    val pullCalls = mutableListOf<PullCall>()

    /**
     * The result the next `pushSnapshot` returns instead of a successful acknowledgement. Consumed
     * once, so a test can script a failure followed by the real behaviour.
     */
    fun nextPushResult(result: Outcome<RemoteAck, RemoteError>) {
        pushResults += result
    }

    var deliverUpdatedAtTruncatedToMillis = false

    /** Seeds a document directly, for tests that start from an already-backed-up account. */
    fun seed(
        ownerId: OwnerId,
        entityType: EntityType,
        entityId: EntityId,
        rawJson: String,
        serverUpdatedAt: Instant,
    ) {
        store(
            ownerId = ownerId,
            entityType = entityType,
            document =
                StoredDocument(
                    document =
                        RemoteDocument(
                            entityType = entityType,
                            documentId = entityId,
                            serverUpdatedAt = serverUpdatedAt,
                            rawJson = rawJson,
                        ),
                    storedUpdatedAtMicros = serverUpdatedAt.toEpochMicroseconds(),
                ),
        )
    }

    /** The documents stored for one owner and entity type, in the provider's own order. */
    fun storedIds(
        ownerId: OwnerId,
        entityType: EntityType,
    ): List<String> = documentsFor(ownerId, entityType).sortedWith(STORED_ORDER).map { it.document.documentId.value }

    /**
     * The stored document for one entity, delivered as the provider would deliver it. Its
     * `serverUpdatedAt` is the server-assigned value, not the one the pushing device's payload
     * carried, and its `rawJson` has the same field replaced.
     */
    fun stored(
        ownerId: OwnerId,
        entityType: EntityType,
        entityId: EntityId,
    ): RemoteDocument? =
        documentsFor(ownerId, entityType)
            .firstOrNull { it.document.documentId == entityId }
            ?.let(::deliver)

    override suspend fun pushSnapshot(
        ownerId: OwnerId,
        snapshot: EntitySnapshot,
    ): Outcome<RemoteAck, RemoteError> {
        pushCalls += snapshot
        pushResults.removeFirstOrNull()?.let { return it }
        val parsed = Json.parseToJsonElement(snapshot.json).jsonObject
        // `toFirestoreWrite` rejects a payload whose identity fields disagree with the snapshot
        // before anything is written, so a replica that accepted one would hide exactly the class of
        // defect `E1-11` was: a payload the production path cannot push.
        if (!matchesProviderPreconditions(ownerId, snapshot, parsed)) {
            return Outcome.Err(RemoteError.InvalidArgument)
        }
        val committedAt = clock()
        val serverUpdatedAt = committedAt.toEpochMicroseconds()
        // The provider's server timestamp replaces the payload's own `updatedAt`; every other field
        // is carried verbatim, exactly as `toFirestoreWrite` does on the real path.
        val storedJson =
            rewriteUpdatedAt(
                rawJson = payloadWithoutEntityType(parsed),
                updatedAtMillis = committedAt.toEpochMilliseconds(),
            )
        store(
            ownerId = ownerId,
            entityType = snapshot.entityType,
            document =
                StoredDocument(
                    document =
                        RemoteDocument(
                            entityType = snapshot.entityType,
                            documentId = snapshot.entityId,
                            serverUpdatedAt = committedAt,
                            rawJson = storedJson,
                        ),
                    storedUpdatedAtMicros = serverUpdatedAt,
                ),
        )
        return Outcome.Ok(RemoteAck(snapshot.entityType, snapshot.entityId, committedAt))
    }

    override suspend fun pullChanges(
        ownerId: OwnerId,
        entityType: EntityType,
        cursor: RemoteCursor,
        limit: Int,
    ): Outcome<RemotePage, RemoteError> {
        pullCalls += PullCall(ownerId, entityType, cursor, limit)
        val ordered = documentsFor(ownerId, entityType).sortedWith(STORED_ORDER)
        val boundaryMicros = cursor.lastServerUpdatedAt.toEpochMicroseconds()
        val boundaryId = cursor.lastDocumentId?.value
        val remaining =
            if (boundaryId == null) {
                // The first-page boundary of `§9.4` carries only the timestamp, so a document exactly
                // at `startAt(overlapSince)` is included.
                ordered.filter { it.storedUpdatedAtMicros >= boundaryMicros }
            } else {
                // A strict total-order comparison, never an index offset: `startAfter(updatedAt,
                // documentId)` excludes exactly the boundary pair and nothing more.
                ordered.dropWhile { stored ->
                    stored.storedUpdatedAtMicros < boundaryMicros ||
                        (
                            stored.storedUpdatedAtMicros == boundaryMicros &&
                                stored.document.documentId.value <= boundaryId
                        )
                }
            }
        val items = remaining.take(limit).map(::deliver)
        return Outcome.Ok(
            RemotePage(
                items = items,
                nextCursor =
                    items.lastOrNull()?.let { RemoteCursor(it.serverUpdatedAt, it.documentId) } ?: cursor,
                hasMore = items.size == limit,
            ),
        )
    }

    private fun store(
        ownerId: OwnerId,
        entityType: EntityType,
        document: StoredDocument,
    ) {
        val list = documents.getOrPut(key(ownerId, entityType)) { mutableListOf() }
        list.removeAll { it.document.documentId == document.document.documentId }
        list += document
    }

    private fun documentsFor(
        ownerId: OwnerId,
        entityType: EntityType,
    ): List<StoredDocument> = documents[key(ownerId, entityType)].orEmpty()

    private fun key(
        ownerId: OwnerId,
        entityType: EntityType,
    ): String = "${ownerId.value}/${entityType.collection}"

    /** The integration boundary: the value delivered into `RemoteDocument.serverUpdatedAt`. */
    private fun deliver(stored: StoredDocument): RemoteDocument =
        if (deliverUpdatedAtTruncatedToMillis) {
            stored.document.copy(
                serverUpdatedAt = Instant.fromEpochMilliseconds(stored.document.serverUpdatedAt.toEpochMilliseconds()),
            )
        } else {
            stored.document
        }

    /**
     * The preconditions `toFirestoreWrite` enforces before it writes anything: the payload's own
     * identity fields must agree with the snapshot that carries it.
     */
    private fun matchesProviderPreconditions(
        ownerId: OwnerId,
        snapshot: EntitySnapshot,
        parsed: JsonObject,
    ): Boolean {
        val id = parsed[ID_FIELD]?.jsonPrimitive?.contentOrNull
        val owner = parsed[OWNER_ID_FIELD]?.jsonPrimitive?.contentOrNull
        val schemaVersion = parsed[SCHEMA_VERSION_FIELD]?.jsonPrimitive?.longOrNull
        val payloadEntityType = parsed[ENTITY_TYPE_FIELD]?.jsonPrimitive?.contentOrNull
        if (id != snapshot.entityId.value || owner != ownerId.value) return false
        if (schemaVersion != snapshot.schemaVersion.toLong()) return false
        return payloadEntityType == snapshot.entityType.name
    }

    /**
     * Drops the transport-only `entityType` key, which never lands in the stored document, and
     * replaces `updatedAt` with the server's committed value.
     */
    private fun payloadWithoutEntityType(parsed: JsonObject): JsonObject =
        buildJsonObject {
            parsed.forEach { (field, value) ->
                if (field != ENTITY_TYPE_FIELD) put(field, value)
            }
        }

    private fun rewriteUpdatedAt(
        rawJson: JsonObject,
        updatedAtMillis: Long,
    ): String =
        buildJsonObject {
            rawJson.forEach { (field, value) ->
                if (field == UPDATED_AT_FIELD) {
                    put(field, JsonPrimitive(updatedAtMillis))
                } else {
                    put(field, value)
                }
            }
        }.toString()

    internal companion object {
        const val ENTITY_TYPE_FIELD = "entityType"
        const val UPDATED_AT_FIELD = "updatedAt"
        const val ID_FIELD = "id"
        const val OWNER_ID_FIELD = "ownerId"
        const val SCHEMA_VERSION_FIELD = "schemaVersion"

        val STORED_ORDER: Comparator<StoredDocument> =
            compareBy({ it.storedUpdatedAtMicros }, { it.document.documentId.value })
    }
}

/** One stored document plus the ordering key the provider keeps at full precision. */
@HiddenFromObjC
data class StoredDocument(
    val document: RemoteDocument,
    val storedUpdatedAtMicros: Long,
)

/** One pull page request, so a test can assert the cursor and limit the engine actually sent. */
@HiddenFromObjC
data class PullCall(
    val ownerId: OwnerId,
    val entityType: EntityType,
    val cursor: RemoteCursor,
    val limit: Int,
)
