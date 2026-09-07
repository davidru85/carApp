package com.ruizurraca.carapp

import com.ruizurraca.carapp.core.auth.AuthSession
import com.ruizurraca.carapp.core.auth.AuthState
import com.ruizurraca.carapp.core.common.AppError
import com.ruizurraca.carapp.core.common.AuthProvider
import com.ruizurraca.carapp.core.common.Outcome
import com.ruizurraca.carapp.core.testing.FakeAppClock
import com.ruizurraca.carapp.core.testing.FakeAuthClient
import com.ruizurraca.carapp.feature.session.domain.AnonymousReminderRepository
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
import kotlin.time.Instant

/**
 * The cold-start and session-change boundaries of the `docs/CONTRACTS.md §11.3` evaluation. Launch
 * and foreground return remain the only moments a host asks for an evaluation; these tests pin what
 * happens when the answer is not available yet, or stops being valid while it is being produced.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class AnonymousReminderEvaluationRaceTest {
    @Test
    fun anEvaluationRequestedBeforeTheSessionIsRestoredCompletesWhenItResolves() =
        runTest {
            val reminders = RecordingReminders()
            val authClient = FakeAuthClient(initialState = AuthState.Unknown)
            val stateHolder = stateHolder(authClient, reminders, elapsed = 1.days)

            stateHolder.evaluateAnonymousReminder()
            advanceUntilIdle()
            assertNull(
                stateHolder.state.value.anonymousReminderIndex,
                "An undetermined auth state has no session to evaluate yet.",
            )

            authClient.setAuthState(AuthState.SignedIn(anonymousSession()))
            advanceUntilIdle()

            assertEquals(
                0,
                stateHolder.state.value.anonymousReminderIndex,
                "The launch evaluation must not be lost until the next foreground return.",
            )
            stateHolder.close()
        }

    @Test
    fun aSessionThatResolvesWithoutAnyRequestedEvaluationRemindsNobody() =
        runTest {
            val reminders = RecordingReminders()
            val authClient = FakeAuthClient(initialState = AuthState.Unknown)
            val stateHolder = stateHolder(authClient, reminders, elapsed = 20.days)

            authClient.setAuthState(AuthState.SignedIn(anonymousSession()))
            advanceUntilIdle()

            assertNull(stateHolder.state.value.anonymousReminderIndex)
            assertEquals(
                0,
                reminders.lookups,
                "A restored session is not an evaluation trigger of its own.",
            )
            stateHolder.close()
        }

    @Test
    fun aPendingEvaluationIsConsumedByAPermanentResolution() =
        runTest {
            val reminders = RecordingReminders()
            val authClient = FakeAuthClient(initialState = AuthState.Unknown)
            val stateHolder = stateHolder(authClient, reminders, elapsed = 20.days)

            stateHolder.evaluateAnonymousReminder()
            advanceUntilIdle()
            authClient.setAuthState(AuthState.SignedIn(permanentSession()))
            advanceUntilIdle()

            assertNull(stateHolder.state.value.anonymousReminderIndex)
            assertTrue(reminders.recorded.isEmpty())
            stateHolder.close()
        }

    @Test
    fun aResolvedPendingEvaluationIsNotRunAgainByALaterSessionChange() =
        runTest {
            val reminders = RecordingReminders()
            val authClient = FakeAuthClient(initialState = AuthState.Unknown)
            val stateHolder = stateHolder(authClient, reminders, elapsed = 1.days)
            stateHolder.evaluateAnonymousReminder()
            advanceUntilIdle()

            authClient.setAuthState(AuthState.SignedIn(anonymousSession()))
            advanceUntilIdle()
            authClient.setAuthState(AuthState.SignedOut)
            advanceUntilIdle()
            authClient.setAuthState(AuthState.SignedIn(anonymousSession()))
            advanceUntilIdle()

            assertEquals(
                1,
                reminders.lookups,
                "The pending evaluation is one-shot; later sessions are not new triggers.",
            )
            stateHolder.close()
        }

    @Test
    fun aPermanentSignInWhileAnEvaluationIsInFlightPublishesNoReminder() =
        runTest {
            val reminders = RecordingReminders()
            val authClient = FakeAuthClient(initialState = AuthState.SignedIn(anonymousSession()))
            val stateHolder = stateHolder(authClient, reminders, elapsed = 20.days)

            stateHolder.evaluateAnonymousReminder()
            advanceUntilIdle()
            authClient.setAuthState(AuthState.SignedIn(permanentSession()))
            advanceUntilIdle()
            reminders.releaseRecord()
            advanceUntilIdle()

            assertNull(
                stateHolder.state.value.anonymousReminderIndex,
                "A permanently signed-in owner must never be handed a retention notice.",
            )
            stateHolder.close()
        }

    @Test
    fun aPermanentSignInWhileAnEvaluationIsInFlightLeavesTheScheduleStateCleared() =
        runTest {
            val reminders = RecordingReminders()
            val authClient = FakeAuthClient(initialState = AuthState.SignedIn(anonymousSession()))
            val stateHolder = stateHolder(authClient, reminders, elapsed = 20.days)

            stateHolder.evaluateAnonymousReminder()
            advanceUntilIdle()
            authClient.setAuthState(AuthState.SignedIn(permanentSession()))
            advanceUntilIdle()
            reminders.releaseRecord()
            advanceUntilIdle()

            assertTrue(reminders.cleared)
            assertTrue(
                reminders.recorded.isEmpty(),
                "An index written after the clear would outlive the schedule it belonged to.",
            )
            stateHolder.close()
        }

    @Test
    fun aSwitchToADifferentAnonymousIdentityWhileAnEvaluationIsInFlightPublishesNoReminder() =
        runTest {
            val reminders = RecordingReminders()
            val authClient = FakeAuthClient(initialState = AuthState.SignedIn(anonymousSession()))
            val stateHolder = stateHolder(authClient, reminders, elapsed = 20.days)

            stateHolder.evaluateAnonymousReminder()
            advanceUntilIdle()
            authClient.setAuthState(AuthState.SignedIn(anonymousSession(uid = "other-anonymous-uid")))
            advanceUntilIdle()
            reminders.releaseRecord()
            advanceUntilIdle()

            assertNull(
                stateHolder.state.value.anonymousReminderIndex,
                "The index was computed for an identity that is no longer the current one.",
            )
            stateHolder.close()
        }

    private fun TestScope.stateHolder(
        authClient: FakeAuthClient,
        reminders: AnonymousReminderRepository,
        elapsed: Duration,
    ): SessionStateHolder =
        SessionStateHolder(
            scope = this,
            authClient = authClient,
            clock = FakeAppClock(CREATED_AT + elapsed),
            anonymousReminders = reminders,
        )

    private fun anonymousSession(uid: String = ANONYMOUS_UID): AuthSession =
        AuthSession(
            uid = uid,
            isAnonymous = true,
            providers = setOf(AuthProvider.ANONYMOUS),
            createdAt = CREATED_AT,
        )

    private fun permanentSession(): AuthSession =
        AuthSession(
            uid = ANONYMOUS_UID,
            isAnonymous = false,
            providers = setOf(AuthProvider.GOOGLE),
            createdAt = CREATED_AT,
        )

    private companion object {
        const val ANONYMOUS_UID = "anonymous-uid"

        /** 2026-01-01T00:00:00Z. */
        val CREATED_AT: Instant = Instant.fromEpochMilliseconds(1_767_225_600_000L)
    }
}

/**
 * Counts reads and holds the write open, so a test can place a session change exactly inside an
 * evaluation that has already decided what to show but has not published it yet.
 */
private class RecordingReminders : AnonymousReminderRepository {
    val recorded = mutableListOf<Pair<String, Int>>()
    var lookups: Int = 0
        private set
    var cleared: Boolean = false
        private set

    private val recordGate = CompletableDeferred<Unit>()

    fun releaseRecord() {
        recordGate.complete(Unit)
    }

    override suspend fun lastShownIndex(anonymousUid: String): Outcome<Int?, AppError> {
        lookups += 1
        return Outcome.Ok(recorded.lastOrNull { (uid, _) -> uid == anonymousUid }?.second)
    }

    override suspend fun recordShown(
        anonymousUid: String,
        index: Int,
    ): Outcome<Unit, AppError> {
        recordGate.await()
        recorded += anonymousUid to index
        return Outcome.Ok(Unit)
    }

    override suspend fun clear(): Outcome<Unit, AppError> {
        recorded.clear()
        cleared = true
        return Outcome.Ok(Unit)
    }
}
