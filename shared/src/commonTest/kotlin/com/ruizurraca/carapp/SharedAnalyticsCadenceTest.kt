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
import com.ruizurraca.carapp.core.common.ValidationError
import com.ruizurraca.carapp.core.database.OwnerActiveRowCounts
import com.ruizurraca.carapp.core.model.CurrencyCode
import com.ruizurraca.carapp.core.model.DistanceUnit
import com.ruizurraca.carapp.core.model.UserSettings
import com.ruizurraca.carapp.core.model.VolumeUnit
import com.ruizurraca.carapp.core.testing.FakeAuthClient
import com.ruizurraca.carapp.core.testing.FakeOwnerContext
import com.ruizurraca.carapp.core.testing.RecordingAnalyticsTracker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * `E3-09` (`D-196`, ADR-0196): the `docs/CONTRACTS.md §16.1` opt-in gate, sync-status edge and the
 * session/onboarding events, each asserted on the real orchestration rather than on a helper.
 */
class SharedAnalyticsCadenceTest {
    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun theFirstOptInEnablesCollectionAndRefreshesTheBucketsOnce() =
        runTest {
            withEmissions { emissions, tracker, settings, _, _ ->
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
            withEmissions { _, tracker, settings, _, _ ->
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
            withEmissions { _, tracker, settings, _, _ ->
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
            withEmissions { _, tracker, settings, _, _ ->
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
            // The wiring baseline (`IDLE`) is itself the first emitted category; this test asserts
            // that a same-category re-emission is not a second event, and that a change is.
            withEmissions(initialStatus = SyncStatus.Syncing) { _, tracker, _, status, _ ->
                // The wiring baseline (`SYNCING`) is consumed while collection is still off, so §16.1
                // drops it; enabling afterwards must not replay it either.
                tracker.setEnabled(true)

                listOf(
                    SyncStatus.Syncing,
                    SyncStatus.Syncing,
                    SyncStatus.Pending(1),
                    SyncStatus.Pending(2),
                    SyncStatus.Syncing,
                ).forEach { value ->
                    status.value = value
                }
                advanceUntilIdle()

                assertEquals(
                    listOf(
                        AnalyticsEvent.SyncStatusChanged(SyncStatusCategory.PENDING),
                        AnalyticsEvent.SyncStatusChanged(SyncStatusCategory.SYNCING),
                    ),
                    tracker.events,
                    "only a category change is an event, and the disabled baseline is not replayed",
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
            advanceUntilIdle()
            tracker.clear()

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
            advanceUntilIdle()
            tracker.clear()

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
            val authClient = FakeAuthClient(initialState = AuthState.Unknown)
            val sessionHolder = sessionHolder(authClient, tracker)
            advanceUntilIdle()

            authClient.setAuthState(anonymousSession())
            advanceUntilIdle()

            assertEquals(
                listOf<AnalyticsEvent>(AnalyticsEvent.OnboardingStarted, AnalyticsEvent.OnboardingCompleted),
                tracker.events,
                "the welcome-to-signed-in transition starts and completes onboarding, once each",
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
            val authClient = FakeAuthClient(initialState = AuthState.Unknown)
            val sessionHolder = sessionHolder(authClient, tracker)
            advanceUntilIdle()

            authClient.setAuthState(anonymousSession())
            advanceUntilIdle()
            authClient.setAuthState(AuthState.SignedOut)
            advanceUntilIdle()
            authClient.setAuthState(anonymousSession())
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
        initialStatus: SyncStatus = SyncStatus.Idle,
        block: suspend (
            AnalyticsEmissions,
            RecordingAnalyticsTracker,
            MutableStateFlow<Outcome<UserSettings, AppError>>,
            MutableStateFlow<SyncStatus>,
            (Int, Int) -> Unit,
        ) -> Unit,
    ) {
        val tracker = RecordingAnalyticsTracker(initiallyEnabled = false)
        val settings =
            MutableStateFlow<Outcome<UserSettings, AppError>>(
                Outcome.Err(ValidationError.InvalidUnit(detail = "none")),
            )
        val status = MutableStateFlow(initialStatus)
        var counts = OwnerActiveRowCounts(vehicleCount = 0, entryCount = 0)
        val emissions =
            AnalyticsEmissions(
                tracker = tracker,
                ownerContext = FakeOwnerContext(),
                // A pure count source, so the cadence is observed without a database round trip.
                // Which rows that count describes is the accessor's own contract, pinned by
                // OwnerActiveRowCountDatabaseAccessTest.
                activeRowCounts = { counts },
            )
        // `backgroundScope`: these collectors never complete, and a `runTest` that waited for them
        // would fail with UncompletedCoroutinesError.
        // The graph's own `dispatchers.default` is confined in tests; an unconfined test dispatcher
        // makes each emission observable as it happens, which is what these cadence assertions
        // describe. Production timing is the graph's concern, not this rule's.
        val collectorScope =
            CoroutineScope(backgroundScope.coroutineContext + UnconfinedTestDispatcher(testScheduler))
        emissions.launchIn(scope = collectorScope, settings = settings, status = status)
        advanceUntilIdle()
        try {
            block(emissions, tracker, settings, status, { vehicle, entry ->
                counts =
                    OwnerActiveRowCounts(vehicle.toLong(), entry.toLong())
            })
        } finally {
            Unit
        }
    }
}
