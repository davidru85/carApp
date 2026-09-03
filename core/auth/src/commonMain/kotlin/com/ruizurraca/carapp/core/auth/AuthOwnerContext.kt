@file:OptIn(kotlin.experimental.ExperimentalObjCRefinement::class)

package com.ruizurraca.carapp.core.auth

import com.ruizurraca.carapp.core.common.OwnerContext
import com.ruizurraca.carapp.core.model.LOCAL_OWNER
import com.ruizurraca.carapp.core.model.OwnerId
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flow
import kotlin.native.HiddenFromObjC

@HiddenFromObjC
class AuthOwnerContext(
    @Suppress("UNUSED_PARAMETER") authState: StateFlow<AuthState>,
) : OwnerContext {
    override val current: OwnerId get() = LOCAL_OWNER

    override fun observe(): Flow<OwnerId> =
        flow {
            error("Auth-backed owner observation is not implemented")
        }
}
