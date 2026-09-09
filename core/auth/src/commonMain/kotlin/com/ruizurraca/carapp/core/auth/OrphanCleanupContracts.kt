@file:OptIn(kotlin.experimental.ExperimentalObjCRefinement::class)

package com.ruizurraca.carapp.core.auth

import com.ruizurraca.carapp.core.common.AuthError
import com.ruizurraca.carapp.core.common.Outcome
import kotlin.native.HiddenFromObjC

/** The raw, single-purpose server ticket retained only by the durable conversion operation. */
@HiddenFromObjC
data class OrphanCleanupTicket(
    val value: String,
)

/** Provider-free client port for the two E3-11 callable operations. */
@HiddenFromObjC
interface OrphanCleanupClient {
    suspend fun issueOrphanCleanupTicket(): Outcome<OrphanCleanupTicket, AuthError>

    suspend fun deleteOrphanedAnonymousAccount(ticket: OrphanCleanupTicket): Outcome<Unit, AuthError>
}
