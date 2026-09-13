package com.ruizurraca.carapp.integration.firebase.firestore

import dev.gitlive.firebase.firestore.FirestoreExceptionCode

/**
 * Exhaustive translation of a provider error code into the internal failure vocabulary.
 *
 * The `when (this)` names every mapped constant on purpose: a future GitLive release that renames
 * one makes the build fail instead of degrading silently to [FirestoreGatewayFailure.UNKNOWN].
 * `UNAVAILABLE` and `DEADLINE_EXCEEDED` are the connectivity codes that MUST NOT consume the
 * poison budget, so a silent fallback would violate `docs/CONTRACTS.md §9.7`.
 *
 * This lives in its own file so its generated enum-mapping table is initialised only when the
 * function is actually called. On the Android host test runtime `FirestoreExceptionCode` is a
 * typealias to the Google SDK enum, whose constants cannot be initialised; keeping this `when` in
 * the same file as the `FirestoreGatewayFailure` switches would initialise both mapping tables
 * together and fail the host tests. The function is reached only on a real device.
 */
internal fun FirestoreExceptionCode.toGatewayFailure(): FirestoreGatewayFailure =
    when (this) {
        FirestoreExceptionCode.UNAVAILABLE -> FirestoreGatewayFailure.UNAVAILABLE
        FirestoreExceptionCode.DEADLINE_EXCEEDED -> FirestoreGatewayFailure.DEADLINE_EXCEEDED
        FirestoreExceptionCode.PERMISSION_DENIED -> FirestoreGatewayFailure.PERMISSION_DENIED
        FirestoreExceptionCode.UNAUTHENTICATED -> FirestoreGatewayFailure.UNAUTHENTICATED
        FirestoreExceptionCode.INVALID_ARGUMENT -> FirestoreGatewayFailure.INVALID_ARGUMENT
        FirestoreExceptionCode.NOT_FOUND -> FirestoreGatewayFailure.NOT_FOUND
        else -> FirestoreGatewayFailure.UNKNOWN
    }
