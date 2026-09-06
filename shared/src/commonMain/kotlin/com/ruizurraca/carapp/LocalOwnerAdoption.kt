package com.ruizurraca.carapp

import com.ruizurraca.carapp.core.database.AppDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/**
 * Owns the automatic side of local owner adoption (`docs/CONTRACTS.md §11.2` and `§11.4`): it
 * acquires the anonymous UID a device could not obtain offline, adopts the rows that were written
 * under the `LOCAL_OWNER` sentinel, and reports whether the authenticated owner's local data is
 * settled. Story `E2-06` implements the behavior; this declaration exists so its failing tests
 * compile and execute.
 */
internal class LocalOwnerAdoption(
    internal val dependencies: AppGraphDependencies,
    internal val database: AppDatabase,
) {
    private val settled = MutableStateFlow(false)

    /** False while rows written under `LOCAL_OWNER` still wait for the authenticated owner. */
    val isSettled: StateFlow<Boolean> = settled

    fun launchIn(scope: CoroutineScope): Job = scope.launch { }
}
