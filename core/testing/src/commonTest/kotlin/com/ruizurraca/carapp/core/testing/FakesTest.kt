package com.ruizurraca.carapp.core.testing

import com.ruizurraca.carapp.core.common.LogLevel
import com.ruizurraca.carapp.core.common.SyncTrigger
import com.ruizurraca.carapp.core.model.LOCAL_OWNER
import com.ruizurraca.carapp.core.model.OwnerId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The fakes exist so that tests never depend on a real clock, a real UUID source or real
 * connectivity. These assertions pin the determinism itself.
 */
class FakesTest {
    @Test
    fun theClockOnlyMovesWhenTheTestMovesIt() {
        val clock = FakeAppClock()
        val start = clock.now()

        assertEquals(start, clock.now())

        clock.advanceBy(1_500L)
        assertEquals(start.toEpochMilliseconds() + 1_500L, clock.now().toEpochMilliseconds())
    }

    @Test
    fun generatedIdsAreStableOrderedAndDistinct() {
        val generator = FakeUuidGenerator()
        val ids = List(3) { generator.newId() }

        assertEquals(
            listOf(
                "00000000-0000-4000-8000-000000000001",
                "00000000-0000-4000-8000-000000000002",
                "00000000-0000-4000-8000-000000000003",
            ),
            ids,
        )
        assertEquals(ids.size, ids.toSet().size)
    }

    @Test
    fun theOwnerContextStartsAtTheLocalOwnerSentinel() {
        val context = FakeOwnerContext()
        assertEquals(LOCAL_OWNER, context.current)

        context.set(OwnerId("firebase-uid"))
        assertEquals(OwnerId("firebase-uid"), context.current)
    }

    @Test
    fun connectivityIsOnlineByDefaultAndDrivenByTheTest() {
        val observer = FakeConnectivityObserver()
        assertTrue(observer.isOnline.value)

        observer.set(false)
        assertEquals(false, observer.isOnline.value)
    }

    @Test
    fun theLoggerRecordsWhatItWasGivenWithoutRedactingOnTheCallerSide() {
        val logger = RecordingLogger()
        logger.log(LogLevel.WARN, "sync", "cycle failed", mapOf("code" to "SYNC.AUTH_EXPIRED"), null)

        assertEquals(1, logger.entries.size)
        assertEquals(LogLevel.WARN, logger.entries.single().level)
        assertEquals(mapOf("code" to "SYNC.AUTH_EXPIRED"), logger.entries.single().fields)
    }

    @Test
    fun theSyncTriggerAdapterRecordsInsteadOfScheduling() {
        val adapter = RecordingSyncTriggerAdapter()
        adapter.schedule(SyncTrigger.AppForeground)
        adapter.schedule(SyncTrigger.PullToRefresh)

        assertEquals(listOf(SyncTrigger.AppForeground, SyncTrigger.PullToRefresh), adapter.scheduled)
    }

    @Test
    fun theLocaleProviderDefaultsToTheInitialMarket() {
        val provider = FakeLocaleProvider()
        assertEquals("es-ES", provider.current().languageTag)
        assertEquals("EUR", provider.current().suggestedCurrency.value)
    }
}
