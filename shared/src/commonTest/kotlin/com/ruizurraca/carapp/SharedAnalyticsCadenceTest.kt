package com.ruizurraca.carapp
import com.ruizurraca.carapp.core.analytics.AnalyticsEvent
import com.ruizurraca.carapp.core.analytics.AnalyticsUserProperties
import com.ruizurraca.carapp.core.analytics.CountBucket
import com.ruizurraca.carapp.core.analytics.SyncStatusCategory
import com.ruizurraca.carapp.core.auth.AuthSession
import com.ruizurraca.carapp.core.auth.AuthState
import com.ruizurraca.carapp.core.common.AppError
import com.ruizurraca.carapp.core.common.AuthProvider
import com.ruizurraca.carapp.core.common.Outcome
import com.ruizurraca.carapp.core.common.SyncStatus
import com.ruizurraca.carapp.core.database.OwnerActiveRowCountDatabaseAccess
import com.ruizurraca.carapp.core.model.CurrencyCode
import com.ruizurraca.carapp.core.model.DistanceUnit
import com.ruizurraca.carapp.core.model.UserSettings
import com.ruizurraca.carapp.core.model.VolumeUnit
import com.ruizurraca.carapp.core.testing.FakeAuthClient
import com.ruizurraca.carapp.core.testing.FakeOwnerContext
import com.ruizurraca.carapp.core.testing.InMemoryDatabaseFactory
import com.ruizurraca.carapp.core.testing.RecordingAnalyticsTracker
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest

/**
 * `E3-09` (`D-196`, ADR-0196): the `docs/CONTRACTS.md §16.1` opt-in gate, sync-status edge and the
 * session/onboarding events, each asserted on the real orchestration rather than on a helper.
 */
class SharedAnalyticsCadenceTest {
    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun theFirstOptInEnablesCollectionAndRefreshesTheBucketsOnce() =
        runTest {
            withEmissions { emissions, tracker, settings ->
                tracker.clear()

                settings.value = Outcome.Ok(settings(analyticsEnabled = true))
                advanceUntilIdle()

                assertEquals(listOf(true), tracker.enableCommands, "opt-in enables collection")
                assertEquals(
                    listOf(AnalyticsUserProperties(CountBucket.ZERO, CountBucket.ZERO)),
                    tracker.userProperties,
                    "opt-in itself is the cadence's first setUserProperties call",
                )
            }
        }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun aRepeatedEnabledEmissionRepeatsNothing() =
        runTest {
            withEmissions { _, tracker, settings ->
                settings.value = Outcome.Ok(settings(analyticsEnabled = true))
                advanceUntilIdle()
                tracker.clear()

                settings.value = Outcome.Ok(settings(analyticsEnabled = true, currency = "USD"))
                advanceUntilIdle()

                assertEquals(emptyList(), tracker.enableCommands, "an unchanged opt-in is not a transition")
                assertEquals(emptyList(), tracker.userProperties)
            }
        }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun disablingStopsCollectionAndDoesNotTouchTheBuckets() =
        runTest {
            withEmissions { _, tracker, settings ->
                settings.value = Outcome.Ok(settings(analyticsEnabled = true))
                advanceUntilIdle()
                tracker.clear()

                settings.value = Outcome.Ok(settings(analyticsEnabled = false))
                advanceUntilIdle()

                assertEquals(listOf(false), tracker.enableCommands)
                assertEquals(emptyList(), tracker.userProperties, "the disabled half of the cadence is silent")
            }
        }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun aSecondOptInAfterADisableRepeatsThePair() =
        runTest {
            withEmissions { _, tracker, settings ->
                settings.value = Outcome.Ok(settings(analyticsEnabled = true))
                advanceUntilIdle()
                settings.value = Outcome.Ok(settings(analyticsEnabled = false))
                advanceUntilIdle()
                tracker.clear()

                settings.value = Outcome.Ok(settings(analyticsEnabled = true))
                advanceUntilIdle()

                assertEquals(listOf(true), tracker.enableCommands)
                assertEquals(1, tracker.userProperties.size, "each opt-in transition carries its own refresh")
            }
        }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun onlyCategoryChangesProduceSyncStatusEvents() =
        runTest {
            withEmissions { _, tracker, _, status ->
                tracker.setEnabled(true)
                tracker.clear()

                listOf(
                    SyncStatus.Idle,
                    SyncStatus.Idle,
                    SyncStatus.Pending(1),
                    SyncStatus.Pending(2),
                    SyncStatus.Idle,
                ).forEach { value ->
                    status.value = value
                    advanceUntilIdle()
                }

                assertEquals(
                    listOf(
                        AnalyticsEvent.SyncStatusChanged(SyncStatusCategory.IDLE),
                        AnalyticsEvent.SyncStatusChanged(SyncStatusCategory.PENDING),
                        AnalyticsEvent.SyncStatusChanged(SyncStatusCategory.IDLE),
                    ),
                    tracker.events,
                    "the first resolved category is the baseline and is emitted; repeats are not new events",
                )
            }
        }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun anonymousSignInSelectionEmitsItsEvent() =
        runTest {
            val tracker = RecordingAnalyticsTracker(initiallyEnabled = true)
            val session =
                AuthSession(uid = "anonymous-owner", isAnonymous = true, providers = setOf(AuthProvider.ANONYMOUS))
            val sessionHolder = sessionHolder(FakeAuthClient(sessionResult = Outcome.Ok(session)), tracker)

            sessionHolder.startAnonymousSignIn()
            advanceUntilIdle()

            assertEquals(
                listOf<AnalyticsEvent>(AnalyticsEvent.AnonymousSignInSelected),
                tracker.events,
                "the selection is the user's choice, so it is emitted when the intent is invoked",
            )
            sessionHolder.close()
        }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun permanentSignInSelectionCarriesTheProvider() =
        runTest {
            val tracker = RecordingAnalyticsTracker(initiallyEnabled = true)
            val sessionHolder = sessionHolder(FakeAuthClient(), tracker)

            sessionHolder.startPermanentSignIn(AuthProvider.GOOGLE)
            advanceUntilIdle()

            assertEquals(
                listOf<AnalyticsEvent>(AnalyticsEvent.PermanentSignInSelected(AuthProvider.GOOGLE)),
                tracker.events,
            )
            sessionHolder.close()
        }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun onboardingCompletesOnTheFirstSignedInTransition() =
        runTest {
            val tracker = RecordingAnalyticsTracker(initiallyEnabled = true)
            val authClient = FakeAuthClient(initialState = AuthState.SignedOut)
            val sessionHolder = sessionHolder(authClient, tracker)
            advanceUntilIdle()
            tracker.clear()

            authClient.state.value = anonymousSession()
            advanceUntilIdle()

            assertEquals(
                listOf<AnalyticsEvent>(AnalyticsEvent.OnboardingCompleted),
                tracker.events,
                "the welcome-to-signed-in transition completes onboarding exactly once",
            )
            sessionHolder.close()
        }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun aSessionThatWasAlreadyResolvedEmitsNeitherOnboardingEvent() =
        runTest {
            val tracker = RecordingAnalyticsTracker(initiallyEnabled = true)
            val authClient = FakeAuthClient(initialState = anonymousSession())
            val sessionHolder = sessionHolder(authClient, tracker)
            advanceUntilIdle()

            assertEquals(
                emptyList(),
                tracker.events,
                "a device whose session was already restored did not go through onboarding in this run",
            )
            sessionHolder.close()
        }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun aFlippingPhaseEmitsNeitherOnboardingEventTwice() =
        runTest {
            val tracker = RecordingAnalyticsTracker(initiallyEnabled = true)
            val authClient = FakeAuthClient(initialState = AuthState.SignedOut)
            val sessionHolder = sessionHolder(authClient, tracker)
            advanceUntilIdle()

            authClient.state.value = anonymousSession()
            advanceUntilIdle()
            authClient.state.value = AuthState.SignedOut
            advanceUntilIdle()
            authClient.state.value = anonymousSession()
            advanceUntilIdle()

            assertEquals(
                listOf<AnalyticsEvent>(AnalyticsEvent.OnboardingStarted, AnalyticsEvent.OnboardingCompleted),
                tracker.events,
                "each onboarding event fires at most once per holder, whatever the phase does afterwards",
            )
            sessionHolder.close()
        }

    private fun anonymousSession(): AuthState.SignedIn =
        AuthState.SignedIn(
            AuthSession(uid = "anonymous-owner", isAnonymous = true, providers = setOf(AuthProvider.ANONYMOUS)),
        )

    private fun sessionHolder(
        authClient: FakeAuthClient,
        tracker: RecordingAnalyticsTracker,
    ): SessionStateHolder =
        SessionStateHolder(
            scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined),
            authClient = authClient,
            analyticsTracker = tracker,
        )

    private fun settings(
        analyticsEnabled: Boolean,
        currency: String = "EUR",
    ) = UserSettings(
        currency = CurrencyCode(currency),
        distanceUnit = DistanceUnit.KM,
        volumeUnit = VolumeUnit.LITER,
        analyticsEnabled = analyticsEnabled,
    )

    @OptIn(ExperimentalCoroutinesApi::class)
    private suspend fun TestScope.withEmissions(
        block: suspend (
            AnalyticsEmissions,
            RecordingAnalyticsTracker,
            MutableStateFlow<Outcome<UserSettings, AppError>>,
            MutableStateFlow<SyncStatus>,
        ) -> Unit,
    ) {
        val tracker = RecordingAnalyticsTracker(initiallyEnabled = false)
        val factory = InMemoryDatabaseFactory()
        val handle = factory.create()
        val settings = MutableStateFlow<Outcome<UserSettings, AppError>>(
            Outcome.Err(AppError.ValidationError.InvalidUnit(detail = "none")),
        )
        val status = MutableStateFlow<SyncStatus>(SyncStatus.Idle)
        val emissions =
            AnalyticsEmissions(
                tracker = tracker,
                ownerContext = FakeOwnerContext(),
                counts = OwnerActiveRowCountDatabaseAccess(handle.database),
            )
        emissions.launchIn(scope = this, settings = settings, status = status)
        advanceUntilIdle()
        try {
            block(emissions, tracker, settings, status)
        } finally {
            handle.close()
            factory.close()
        }
    }
}
