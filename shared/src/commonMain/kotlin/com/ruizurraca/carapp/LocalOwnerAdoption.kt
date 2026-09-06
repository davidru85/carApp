package com.ruizurraca.carapp

import app.cash.sqldelight.async.coroutines.awaitAsOne
import com.ruizurraca.carapp.core.auth.AuthState
import com.ruizurraca.carapp.core.common.AppError
import com.ruizurraca.carapp.core.common.LogLevel
import com.ruizurraca.carapp.core.common.Outcome
import com.ruizurraca.carapp.core.common.PersistenceError
import com.ruizurraca.carapp.core.database.AppDatabase
import com.ruizurraca.carapp.core.database.DatabaseMutations
import com.ruizurraca.carapp.core.model.LOCAL_OWNER
import com.ruizurraca.carapp.core.model.OwnerId
import com.ruizurraca.carapp.feature.fuel.data.toAdoptionOutboxPayload
import com.ruizurraca.carapp.feature.vehicle.data.toAdoptionOutboxPayload
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.concurrent.Volatile

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
    // Seamed so the failure path required by `D-125` is exercisable. Production always passes null
    // and adopts through the real transaction.
    injectedAdoptRows: (suspend (String) -> Unit)? = null,
) {
    private val queries = database.databaseQueries
    private val mutations = DatabaseMutations(database)
    private val adoptRows: suspend (String) -> Unit = injectedAdoptRows ?: ::adoptThroughDatabase

    // Adoption is idempotent, but running two of them concurrently would read the same rows twice
    // and do the same work twice. One at a time is enough, and the transaction stays short.
    private val adoptionLock = Mutex()

    // Held with tryLock, so a trigger arriving mid-flight is dropped instead of queueing a second
    // acquisition behind the first one.
    private val acquisitionLock = Mutex()

    @Volatile
    private var localStartAccepted = false

    @Volatile
    private var triggerScope: CoroutineScope? = null

    /**
     * Returns once the current owner's local data is the data it will read, or a typed error if the
     * adoption transaction failed.
     *
     * For the `LOCAL_OWNER` sentinel it succeeds immediately: an offline session reads its own rows.
     * For an authenticated owner it succeeds only after the rows written under the sentinel have
     * been rewritten, so an observation started after an authentication cannot publish an empty list
     * that first-run creation would then open over (`D-116`).
     *
     * A failure is reported, never thrown and never a silent indefinite wait (`D-125`): the caller
     * turns it into an unreadable state that the owner can retry. Cancellation is rethrown, so the
     * caller's own cancellation still behaves like cancellation.
     */
    suspend fun awaitAdoption(): Outcome<Unit, AppError> {
        val owner = dependencies.ownerContext.current
        if (owner == LOCAL_OWNER) return Outcome.Ok(Unit)
        return try {
            adopt(owner)
            Outcome.Ok(Unit)
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (_: Throwable) {
            Outcome.Err(PersistenceError.TransactionFailed)
        }
    }

    /**
     * Records that the owner explicitly chose "continue without an account" and that the choice fell
     * back to a local session (`D-124`).
     *
     * This is the only signal that distinguishes a device whose owner started locally from one that
     * has never left the welcome screen, and it is the reason returning connectivity can retry for a
     * device that has not written anything yet. It lives for the life of the process; after a
     * restart the rows left under the sentinel are the durable evidence instead.
     */
    fun onLocalStartAccepted() {
        localStartAccepted = true
    }

    /**
     * Re-evaluates anonymous acquisition after a write committed under the sentinel (`D-124`).
     *
     * A device that was already online when it started locally has no later connectivity edge to
     * react to, so without this it would stay under the sentinel until the next time the network
     * changed. The first write is the moment it gains something to adopt.
     *
     * The evaluation is launched rather than awaited: a local write must not wait on a network
     * round trip to report that it succeeded.
     */
    fun onLocalOwnerWriteCommitted() {
        val scope = triggerScope ?: return
        if (!dependencies.connectivityObserver.isOnline.value) return
        scope.launch { acquireAnonymousUidIfWaiting() }
    }

    /**
     * Wires the triggers that make adoption automatic; the graph owns the scope. Nothing here
     * decides anything: an owner change calls [awaitAdoption] and returning connectivity calls
     * [acquireAnonymousUidIfWaiting], which are the two functions that carry the behavior.
     *
     * The two observers are siblings under a supervisor, so one of them failing cannot take the
     * other down with it (`D-125`). Each also survives its own failure, because losing a trigger
     * permanently would leave the device under the sentinel with no way back.
     */
    fun launchIn(scope: CoroutineScope): Job {
        triggerScope = scope
        return scope.launch {
            supervisorScope {
                launch { observeSafely { dependencies.ownerContext.observe().collect { awaitAdoption() } } }
                launch {
                    observeSafely {
                        dependencies.connectivityObserver.isOnline.collect { online ->
                            if (online) acquireAnonymousUidIfWaiting()
                        }
                    }
                }
            }
        }
    }

    // A trigger has to survive whatever its source throws, so the catch is deliberately total. The
    // throwable is logged rather than swallowed, which is what makes the breadth acceptable here.
    @Suppress("TooGenericExceptionCaught")
    private suspend fun observeSafely(block: suspend () -> Unit) {
        try {
            block()
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (throwable: Throwable) {
            dependencies.logger.log(
                level = LogLevel.WARN,
                tag = LOG_TAG,
                message = "adoption trigger stopped",
                fields = mapOf("code" to PersistenceError.TransactionFailed.code),
                throwable = throwable,
            )
        }
    }

    private suspend fun adopt(owner: OwnerId) =
        adoptionLock.withLock {
            // Check-then-act is safe here: the transaction re-reads the rows under the write lock and
            // is idempotent, so this only avoids opening a transaction that would do nothing.
            if (!hasWaitingRows()) return@withLock
            adoptRows(owner.value)
        }

    private suspend fun adoptThroughDatabase(newOwnerId: String) =
        mutations.adoptLocalOwner(
            newOwnerId = newOwnerId,
            vehicleOutboxPayload = { row -> row.toAdoptionOutboxPayload() },
            fuelEntryOutboxPayload = { row -> row.toAdoptionOutboxPayload() },
        )

    /**
     * Retries the anonymous acquisition of `§11.2`. It runs only for a device whose owner asked for
     * a local session: either the explicit choice is still remembered in this process, or rows left
     * under the sentinel prove it across a restart (`D-124`). A device whose owner never chose to
     * continue without an account is never given one.
     *
     * A trigger that arrives while an acquisition is already in flight is dropped rather than
     * queued, so concurrent triggers produce one attempt and not two.
     */
    suspend fun acquireAnonymousUidIfWaiting() {
        if (!acquisitionLock.tryLock()) return
        try {
            if (dependencies.ownerContext.current != LOCAL_OWNER) return
            if (dependencies.authClient.authState.value !is AuthState.SignedOut) return
            if (!localStartAccepted && !hasWaitingRows()) return
            dependencies.authClient.signInAnonymously()
        } finally {
            acquisitionLock.unlock()
        }
    }

    private suspend fun hasWaitingRows(): Boolean = queries.countRowsOwnedBy(LOCAL_OWNER.value).awaitAsOne() > 0

    private companion object {
        const val LOG_TAG = "LocalOwnerAdoption"
    }
}
