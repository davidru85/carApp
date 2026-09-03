@file:OptIn(kotlin.experimental.ExperimentalObjCRefinement::class)

package com.ruizurraca.carapp.core.auth

import com.ruizurraca.carapp.core.common.OwnerContext
import com.ruizurraca.carapp.core.model.LOCAL_OWNER
import com.ruizurraca.carapp.core.model.OwnerId
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlin.native.HiddenFromObjC

/** Exposes the owner implied by provider-free authentication state to local repositories. */
@HiddenFromObjC
class AuthOwnerContext(
    private val authState: StateFlow<AuthState>,
) : OwnerContext {
    override val current: OwnerId get() = authState.value.toOwnerId()

    override fun observe(): Flow<OwnerId> = authState.map(AuthState::toOwnerId)
}

private fun AuthState.toOwnerId(): OwnerId =
    when (this) {
        AuthState.Unknown,
        AuthState.SignedOut,
        -> LOCAL_OWNER

        is AuthState.SignedIn -> OwnerId(session.uid)
    }
