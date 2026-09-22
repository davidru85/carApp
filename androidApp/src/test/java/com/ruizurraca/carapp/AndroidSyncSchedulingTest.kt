package com.ruizurraca.carapp

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The Android execution-lease ordering of `D-187`.
 *
 * `WorkManager` holds the process alive only while `doWork()` executes, so the worker MUST NOT report
 * success before the periodic cycle it triggered has finished. The test drives the pure helper the
 * worker delegates to, which is what makes the ordering provable without `work-testing`.
 */
class AndroidSyncSchedulingTest {
    @Test
    fun periodicWorkDoesNotCompleteBeforeTheControllerCall() =
        runBlocking {
            val release = CompletableDeferred<Unit>()
            val work = async { runPeriodicWork { release.await() } }

            // Give the worker every chance to return early. It must still be suspended on the
            // controller call, because that call is the work the lease exists to cover.
            yield()
            assertFalse(work.isCompleted)

            release.complete(Unit)
            work.await()
            assertTrue(work.isCompleted)
        }
}
