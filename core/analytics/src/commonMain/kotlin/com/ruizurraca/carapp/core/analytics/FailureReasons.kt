package com.ruizurraca.carapp.core.analytics

import com.ruizurraca.carapp.core.common.AuthError

/**
 * The two normative `AuthError` mappings of `docs/CONTRACTS.md §20.9`.
 *
 * They live here rather than at the call sites so that the mapping is written once and can be
 * asserted exhaustively. `§20.9` requires unit tests to assert exhaustiveness of both.
 */

/** `Cancelled -> CANCELLED`, `CredentialAlreadyInUse -> CREDENTIAL_IN_USE`, `NetworkUnavailable -> NETWORK`, `UidWouldChange -> UID_WOULD_CHANGE`, everything else -> `UNKNOWN`. */
fun AuthError.toConversionFailureReason(): ConversionFailureReason = when (this) {
    AuthError.Cancelled -> ConversionFailureReason.CANCELLED
    AuthError.CredentialAlreadyInUse -> ConversionFailureReason.CREDENTIAL_IN_USE
    AuthError.NetworkUnavailable -> ConversionFailureReason.NETWORK
    AuthError.UidWouldChange -> ConversionFailureReason.UID_WOULD_CHANGE
    AuthError.AccountDeletionRemoteFailed,
    AuthError.PermissionDenied,
    AuthError.ProviderUnavailable,
    AuthError.RequiresRecentLogin,
    AuthError.TokenExpired,
    AuthError.Unknown,
    -> ConversionFailureReason.UNKNOWN
}

/** `RequiresRecentLogin -> REQUIRES_RECENT_LOGIN`, `AccountDeletionRemoteFailed -> REMOTE_FAILED`, `NetworkUnavailable -> NETWORK`, everything else -> `UNKNOWN`. */
fun AuthError.toDeletionFailureReason(): DeletionFailureReason = when (this) {
    AuthError.RequiresRecentLogin -> DeletionFailureReason.REQUIRES_RECENT_LOGIN
    AuthError.AccountDeletionRemoteFailed -> DeletionFailureReason.REMOTE_FAILED
    AuthError.NetworkUnavailable -> DeletionFailureReason.NETWORK
    AuthError.Cancelled,
    AuthError.CredentialAlreadyInUse,
    AuthError.PermissionDenied,
    AuthError.ProviderUnavailable,
    AuthError.TokenExpired,
    AuthError.UidWouldChange,
    AuthError.Unknown,
    -> DeletionFailureReason.UNKNOWN
}
