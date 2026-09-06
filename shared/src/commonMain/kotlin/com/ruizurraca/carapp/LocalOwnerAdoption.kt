package com.ruizurraca.carapp

import app.cash.sqldelight.async.coroutines.awaitAsOne
import com.ruizurraca.carapp.core.auth.AuthState
import com.ruizurraca.carapp.core.database.AppDatabase
import com.ruizurraca.carapp.core.database.DatabaseMutations
import com.ruizurraca.carapp.core.model.LOCAL_OWNER
import com.ruizurraca.carapp.core.model.OwnerId
import com.ruizurraca.carapp.feature.fuel.data.toAdoptionOutboxPayload
import com.ruizurraca.carapp.feature.vehicle.data.toAdoptionOutboxPayload
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * The automatic side of local owner adoption, `docs/CONTRACTS.md §11.2` and `§11.4`.
 *
 * A device that could not reach Firebase Auth on first launch keeps working under the `LOCAL_OWNER`
 * sentinel. This runtime closes that gap without any user action: it acquires the anonymous UID as
 * soon as connectivity returns, and it rewrites the waiting rows to whichever owner authentication
 * produces. [awaitAdoption] is the read gate that keeps a newly authenticated owner's data from
 * looking empty in the window between the authentication and the rewrite.
 */
internal class LocalOwnerAdoption(
    private val dependencies: AppGraphDependencies,
    database: AppDatabase,
) {
    private val queries = database.databaseQueries
    private val mutations = DatabaseMutations(database)

    // Adoption is idempotent, but running two of them concurrently would read the same rows twice
    // and do the same work twice. One at a time is enough, and the transaction stays short.
    private val adoptionLock = Mutex()

    /**
     * Returns once the current owner's local data is the data it will read.
     *
     * For the `LOCAL_OWNER` sentinel that is immediate: an offline session reads its own rows. For
     * an authenticated owner it returns only after the rows written under the sentinel have been
     * rewritten, so an observation started after an authentication cannot publish an empty list
     * that first-run creation would then open over (`D-116`).
     */
    suspend fun awaitAdoption() {
        val owner = dependencies.ownerContext.current
        if (owner != LOCAL_OWNER) adopt(owner)
    }

    /**
     * Wires the two triggers that make adoption automatic; the graph owns the scope. Nothing here
     * decides anything: an owner change calls [awaitAdoption] and returning connectivity calls
     * [acquireAnonymousUidIfWaiting], which are the two functions that carry the behavior.
     */
    fun launchIn(scope: CoroutineScope): Job =
        scope.launch {
            launch { dependencies.ownerContext.observe().collect { awaitAdoption() } }
            launch {
                dependencies.connectivityObserver.isOnline.collect { online ->
                    if (online) acquireAnonymousUidIfWaiting()
                }
            }
        }

    private suspend fun adopt(owner: OwnerId) =
        adoptionLock.withLock {
            if (!hasWaitingRows()) return@withLock
            mutations.adoptLocalOwner(
                newOwnerId = owner.value,
                vehicleOutboxPayload = { row -> row.toAdoptionOutboxPayload() },
                fuelEntryOutboxPayload = { row -> row.toAdoptionOutboxPayload() },
            )
        }

    /**
     * Retries the anonymous acquisition of `§11.2` after connectivity returns. It is gated on there
     * being something to adopt, so returning connectivity never creates an account for a device
     * whose owner has not started using the app.
     */
    suspend fun acquireAnonymousUidIfWaiting() {
        if (dependencies.ownerContext.current != LOCAL_OWNER) return
        if (dependencies.authClient.authState.value !is AuthState.SignedOut) return
        if (!hasWaitingRows()) return
        dependencies.authClient.signInAnonymously()
    }

    private suspend fun hasWaitingRows(): Boolean = queries.countRowsOwnedBy(LOCAL_OWNER.value).awaitAsOne() > 0
}
