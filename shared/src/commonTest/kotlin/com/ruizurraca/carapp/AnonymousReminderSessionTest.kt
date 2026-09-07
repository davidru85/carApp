package com.ruizurraca.carapp

import com.ruizurraca.carapp.core.auth.AuthSession
import com.ruizurraca.carapp.core.auth.AuthState
import com.ruizurraca.carapp.core.common.AppError
import com.ruizurraca.carapp.core.common.AuthProvider
import com.ruizurraca.carapp.core.common.Outcome
import com.ruizurraca.carapp.core.testing.FakeAppClock
import com.ruizurraca.carapp.core.testing.FakeAuthClient
import com.ruizurraca.carapp.feature.session.domain.AnonymousReminderRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Instant

/**
 * The foreground-only retention notices of `docs/CONTRACTS.md §11.3`. Evaluation is driven by the
 * host lifecycle through an intent; no scheduler, alarm or operating-system notification exists.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class AnonymousReminderSessionTest {
    @Test
    fun aForegroundEvaluationPublishesTheDueReminderForAnAnonymousSession() =
        runTest {
            val reminders = RecordingAnonymousReminderRepository()
            val stateHolder = anonymousStateHolder(elapsed = 1.days, reminders = reminders)

            stateHolder.evaluateAnonymousReminder()
            advanceUntilIdle()

            assertEquals(0, stateHolder.state.value.anonymousReminderIndex)
            stateHolder.close()
        }

    @Test
    fun theEmittedReminderIndexIsPersistedForTheAnonymousIdentityThatSawIt() =
        runTest {
            val reminders = RecordingAnonymousReminderRepository()
            val stateHolder = anonymousStateHolder(elapsed = 20.days, reminders = reminders)

            stateHolder.evaluateAnonymousReminder()
            advanceUntilIdle()

            assertEquals(
                listOf(ANONYMOUS_UID to 3),
                reminders.recorded,
                "Persisting the highest due index is what consumes every lower pending reminder.",
            )
            stateHolder.close()
        }

    @Test
    fun anEvaluationWithNothingDueEmitsNoReminder() =
        runTest {
            val reminders = RecordingAnonymousReminderRepository()
            val stateHolder = anonymousStateHolder(elapsed = 12.hours, reminders = reminders)

            stateHolder.evaluateAnonymousReminder()
            advanceUntilIdle()

            assertNull(stateHolder.state.value.anonymousReminderIndex)
            assertTrue(reminders.recorded.isEmpty())
            stateHolder.close()
        }

    @Test
    fun aPermanentSessionIsNeverReminded() =
        runTest {
            val reminders = RecordingAnonymousReminderRepository()
            val session =
                AuthSession(
                    uid = "permanent-uid",
                    isAnonymous = false,
                    providers = setOf(AuthProvider.GOOGLE),
                    createdAt = CREATED_AT,
                )
            val stateHolder =
                SessionStateHolder(
                    scope = this,
                    authClient = FakeAuthClient(initialState = AuthState.SignedIn(session)),
                    clock = FakeAppClock(CREATED_AT + 20.days),
                    anonymousReminders = reminders,
                )

            stateHolder.evaluateAnonymousReminder()
            advanceUntilIdle()

            assertNull(stateHolder.state.value.anonymousReminderIndex)
            assertTrue(reminders.recorded.isEmpty())
            stateHolder.close()
        }

    @Test
    fun aSignedOutDeviceIsNeverReminded() =
        runTest {
            val reminders = RecordingAnonymousReminderRepository()
            val stateHolder =
                SessionStateHolder(
                    scope = this,
                    authClient = FakeAuthClient(initialState = AuthState.SignedOut),
                    clock = FakeAppClock(CREATED_AT + 20.days),
                    anonymousReminders = reminders,
                )

            stateHolder.evaluateAnonymousReminder()
            advanceUntilIdle()

            assertNull(stateHolder.state.value.anonymousReminderIndex)
            assertTrue(reminders.recorded.isEmpty())
            stateHolder.close()
        }

    @Test
    fun dismissingTheReminderRemovesItFromTheStateWithoutReopeningIt() =
        runTest {
            val reminders = RecordingAnonymousReminderRepository()
            val stateHolder = anonymousStateHolder(elapsed = 4.days, reminders = reminders)
            stateHolder.evaluateAnonymousReminder()
            advanceUntilIdle()

            stateHolder.dismissAnonymousReminder()
            advanceUntilIdle()

            assertNull(stateHolder.state.value.anonymousReminderIndex)
            assertEquals(listOf(ANONYMOUS_UID to 1), reminders.recorded)
            stateHolder.close()
        }

    @Test
    fun aForegroundReturnWithNothingNewDueKeepsTheReminderAlreadyShown() =
        runTest {
            val reminders = RecordingAnonymousReminderRepository()
            val stateHolder = anonymousStateHolder(elapsed = 1.days, reminders = reminders)
            stateHolder.evaluateAnonymousReminder()
            advanceUntilIdle()

            stateHolder.evaluateAnonymousReminder()
            advanceUntilIdle()

            assertEquals(0, stateHolder.state.value.anonymousReminderIndex)
            assertEquals(1, reminders.recorded.size)
            stateHolder.close()
        }

    @Test
    fun signingInPermanentlyClearsThePendingReminderState() =
        runTest {
            val reminders = RecordingAnonymousReminderRepository()
            val authClient = FakeAuthClient(initialState = AuthState.SignedIn(anonymousSession()))
            val stateHolder =
                SessionStateHolder(
                    scope = this,
                    authClient = authClient,
                    clock = FakeAppClock(CREATED_AT + 9.days),
                    anonymousReminders = reminders,
                )
            stateHolder.evaluateAnonymousReminder()
            advanceUntilIdle()

            authClient.setAuthState(
                AuthState.SignedIn(
                    AuthSession(
                        uid = ANONYMOUS_UID,
                        isAnonymous = false,
                        providers = setOf(AuthProvider.GOOGLE),
                        createdAt = CREATED_AT,
                    ),
                ),
            )
            advanceUntilIdle()

            assertNull(stateHolder.state.value.anonymousReminderIndex)
            assertTrue(
                reminders.cleared,
                "Linking a permanent provider ends the schedule, so its device-local state goes with it.",
            )
            stateHolder.close()
        }

    private fun kotlinx.coroutines.test.TestScope.anonymousStateHolder(
        elapsed: Duration,
        reminders: AnonymousReminderRepository,
    ): SessionStateHolder =
        SessionStateHolder(
            scope = this,
            authClient = FakeAuthClient(initialState = AuthState.SignedIn(anonymousSession())),
            clock = FakeAppClock(CREATED_AT + elapsed),
            anonymousReminders = reminders,
        )

    private fun anonymousSession(): AuthSession =
        AuthSession(
            uid = ANONYMOUS_UID,
            isAnonymous = true,
            providers = setOf(AuthProvider.ANONYMOUS),
            createdAt = CREATED_AT,
        )

    private companion object {
        const val ANONYMOUS_UID = "anonymous-uid"

        /** 2026-01-01T00:00:00Z. */
        val CREATED_AT: Instant = Instant.fromEpochMilliseconds(1_767_225_600_000L)
    }
}

private class RecordingAnonymousReminderRepository : AnonymousReminderRepository {
    val recorded = mutableListOf<Pair<String, Int>>()
    var cleared: Boolean = false
        private set

    override suspend fun lastShownIndex(anonymousUid: String): Outcome<Int?, AppError> =
        Outcome.Ok(recorded.lastOrNull { (uid, _) -> uid == anonymousUid }?.second)

    override suspend fun recordShown(
        anonymousUid: String,
        index: Int,
    ): Outcome<Unit, AppError> {
        recorded += anonymousUid to index
        return Outcome.Ok(Unit)
    }

    override suspend fun clear(): Outcome<Unit, AppError> {
        recorded.clear()
        cleared = true
        return Outcome.Ok(Unit)
    }
}
