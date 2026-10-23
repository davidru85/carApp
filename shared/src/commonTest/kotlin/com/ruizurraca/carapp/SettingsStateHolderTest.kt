package com.ruizurraca.carapp

import com.ruizurraca.carapp.core.common.AppError
import com.ruizurraca.carapp.core.common.Outcome
import com.ruizurraca.carapp.core.common.PersistenceError
import com.ruizurraca.carapp.core.common.STATE_HOLDER_TIMEOUT_MS
import com.ruizurraca.carapp.core.common.SUPPORTED_CURRENCY_CODES
import com.ruizurraca.carapp.core.common.UiMessage
import com.ruizurraca.carapp.core.common.UiMessageKind
import com.ruizurraca.carapp.core.model.CurrencyCode
import com.ruizurraca.carapp.core.model.DistanceUnit
import com.ruizurraca.carapp.core.model.UserSettings
import com.ruizurraca.carapp.core.model.VolumeUnit
import com.ruizurraca.carapp.core.testing.TestDispatcherProvider
import com.ruizurraca.carapp.feature.session.domain.SettingsRepository
import com.ruizurraca.carapp.feature.session.domain.UpdateSettingsCommand
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * `E4-01` (`D-211`): the Settings state holder over a recording repository.
 *
 * The holder is a relay with three rules of its own: what it publishes before and after the
 * repository answers, which intents become exactly which `UpdateSettingsCommand`, and how failures
 * and its own lifecycle are reported. Each rule is one test. The behaviour over the real SQLDelight
 * repository and the analytics gate is `SettingsGraphTest`.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SettingsStateHolderTest {
    @Test
    fun theCurrencyIsUnknownUntilTheRepositoryAnswers() =
        runTest {
            val holder = collectedHolder(RecordingSettingsRepository(initial = null))
            advanceUntilIdle()

            assertNull(holder.state.value.currencyCode, "no settings have been emitted yet")
        }

    @Test
    fun theSupportedCurrencyCodesFollowTheContractOrder() =
        runTest {
            val holder = collectedHolder(RecordingSettingsRepository())
            advanceUntilIdle()

            assertEquals(SUPPORTED_CURRENCY_CODES.toList(), holder.state.value.supportedCurrencyCodes)
        }

    @Test
    fun theRepositorySettingsArePublishedAsTypedFields() =
        runTest {
            val repository =
                RecordingSettingsRepository(
                    initial = Outcome.Ok(userSettings(analyticsEnabled = true, currency = "GBP")),
                )
            val holder = collectedHolder(repository)
            advanceUntilIdle()

            assertEquals(
                SettingsUiState(
                    currencyCode = "GBP",
                    supportedCurrencyCodes = SUPPORTED_CURRENCY_CODES.toList(),
                    distanceUnit = DistanceUnit.KM,
                    volumeUnit = VolumeUnit.LITER,
                    analyticsEnabled = true,
                    isSaving = false,
                    message = null,
                ),
                holder.state.value,
            )
        }

    @Test
    fun selectingAnotherSupportedCurrencyWritesOnlyThatCurrency() =
        runTest {
            val repository = RecordingSettingsRepository()
            val holder = collectedHolder(repository)
            advanceUntilIdle()

            holder.selectCurrency("USD")
            advanceUntilIdle()

            assertEquals(
                listOf(UpdateSettingsCommand(currency = CurrencyCode("USD"), analyticsEnabled = null)),
                repository.commands,
            )
        }

    @Test
    fun thePublishedCurrencyFollowsTheRepositoryRatherThanTheIntent() =
        runTest {
            val repository = RecordingSettingsRepository(publishWrites = false)
            val holder = collectedHolder(repository)
            advanceUntilIdle()
            holder.assertPublishedEur()

            holder.selectCurrency("USD")
            advanceUntilIdle()
            assertEquals("EUR", holder.state.value.currencyCode, "an accepted write publishes nothing by itself")

            repository.emit(Outcome.Ok(userSettings(analyticsEnabled = false, currency = "USD")))
            advanceUntilIdle()
            assertEquals("USD", holder.state.value.currencyCode, "the state follows the repository emission")
        }

    @Test
    fun selectingThePublishedCurrencyWritesNothing() =
        runTest {
            val repository = RecordingSettingsRepository()
            val holder = collectedHolder(repository)
            advanceUntilIdle()
            holder.assertPublishedEur()

            holder.selectCurrency("EUR")
            advanceUntilIdle()

            assertEquals(emptyList(), repository.commands)
        }

    @Test
    fun selectingAnUnsupportedCurrencyPublishesTheTypedValidationMessageAndWritesNothing() =
        runTest {
            val repository = RecordingSettingsRepository()
            val holder = collectedHolder(repository)
            advanceUntilIdle()

            holder.selectCurrency("JPY")
            advanceUntilIdle()

            assertEquals(
                UiMessage(
                    id = WRITE_ERROR_MESSAGE_ID,
                    kind = UiMessageKind.ERROR,
                    code = "VALIDATION.INVALID_UNIT",
                    confirmation = null,
                ),
                holder.state.value.message,
            )
            assertEquals(emptyList(), repository.commands, "a rejected currency mutates nothing")
        }

    @Test
    fun enablingAnalyticsWritesOnlyTheOptIn() =
        runTest {
            val repository = RecordingSettingsRepository()
            val holder = collectedHolder(repository)
            advanceUntilIdle()

            holder.setAnalyticsEnabled(true)
            advanceUntilIdle()

            assertEquals(
                listOf(UpdateSettingsCommand(currency = null, analyticsEnabled = true)),
                repository.commands,
            )
        }

    @Test
    fun settingAnalyticsToThePublishedValueWritesNothing() =
        runTest {
            val repository =
                RecordingSettingsRepository(initial = Outcome.Ok(userSettings(analyticsEnabled = true)))
            val holder = collectedHolder(repository)
            advanceUntilIdle()
            assertTrue(holder.state.value.analyticsEnabled, "precondition: the opt-in is published")

            holder.setAnalyticsEnabled(true)
            advanceUntilIdle()

            assertEquals(emptyList(), repository.commands)
        }

    @Test
    fun aSettingsReadFailurePublishesItsTypedErrorCode() =
        runTest {
            val repository =
                RecordingSettingsRepository(initial = Outcome.Err(PersistenceError.DatabaseUnavailable))
            val holder = collectedHolder(repository)
            advanceUntilIdle()

            assertEquals(
                UiMessage(
                    id = READ_ERROR_MESSAGE_ID,
                    kind = UiMessageKind.ERROR,
                    code = "PERSISTENCE.DATABASE_UNAVAILABLE",
                    confirmation = null,
                ),
                holder.state.value.message,
            )
        }

    @Test
    fun aFailedWritePublishesItsTypedErrorCode() =
        runTest {
            val repository = RecordingSettingsRepository()
            repository.updateResult = Outcome.Err(PersistenceError.TransactionFailed)
            val holder = collectedHolder(repository)
            advanceUntilIdle()

            holder.selectCurrency("USD")
            advanceUntilIdle()

            assertEquals(
                UiMessage(
                    id = WRITE_ERROR_MESSAGE_ID,
                    kind = UiMessageKind.ERROR,
                    code = "PERSISTENCE.TRANSACTION_FAILED",
                    confirmation = null,
                ),
                holder.state.value.message,
            )
        }

    @Test
    fun anAcceptedWriteIsPublishedAsSavingUntilTheRepositoryAnswers() =
        runTest {
            val repository = RecordingSettingsRepository()
            val gate = CompletableDeferred<Unit>()
            repository.updateGate = gate
            val holder = collectedHolder(repository)
            advanceUntilIdle()

            holder.setAnalyticsEnabled(true)
            runCurrent()
            assertTrue(holder.state.value.isSaving, "the write is in flight")

            gate.complete(Unit)
            advanceUntilIdle()
            assertFalse(holder.state.value.isSaving, "the repository has answered")
        }

    @Test
    fun anIntentWhileAWriteIsInFlightWritesNothing() =
        runTest {
            val repository = RecordingSettingsRepository()
            repository.updateGate = CompletableDeferred()
            val holder = collectedHolder(repository)
            advanceUntilIdle()

            holder.setAnalyticsEnabled(true)
            runCurrent()
            holder.selectCurrency("USD")
            runCurrent()

            assertEquals(
                listOf(UpdateSettingsCommand(currency = null, analyticsEnabled = true)),
                repository.commands,
                "only the write accepted first reaches the repository",
            )
        }

    @Test
    fun clearMessageWithdrawsTheRejectedSelection() =
        runTest {
            val holder = collectedHolder(RecordingSettingsRepository())
            advanceUntilIdle()
            holder.selectCurrency("JPY")
            advanceUntilIdle()
            assertEquals(
                "VALIDATION.INVALID_UNIT",
                holder.state.value.message
                    ?.code,
                "precondition: the rejection is published",
            )

            holder.clearMessage()
            advanceUntilIdle()

            assertNull(holder.state.value.message)
        }

    @Test
    fun closeCancelsTheHolderJob() =
        runTest {
            val parent = Job()
            val dispatcher = StandardTestDispatcher(testScheduler)
            val holder =
                SettingsStateHolder(
                    scope = CoroutineScope(parent + dispatcher),
                    repository = RecordingSettingsRepository(),
                    dispatchers = TestDispatcherProvider(dispatcher),
                )
            val holderJobs = parent.children.toList()
            assertEquals(1, holderJobs.size, "the holder owns one child job of the caller's scope")

            holder.close()
            holder.close()
            runCurrent()

            assertFalse(
                holderJobs.single().isActive,
                "close() cancels the holder's job, and a second close is a no-op",
            )
            parent.cancel()
        }

    @Test
    fun closeCancelsAWriteInFlight() =
        runTest {
            val repository = RecordingSettingsRepository()
            repository.updateGate = CompletableDeferred()
            val holder = collectedHolder(repository)
            advanceUntilIdle()
            holder.setAnalyticsEnabled(true)
            runCurrent()
            assertEquals(1, repository.commands.size, "precondition: the write is in flight")

            holder.close()
            runCurrent()

            assertEquals(1, repository.updateCancellations)
        }

    @Test
    fun intentsAfterCloseWriteNothing() =
        runTest {
            val repository = RecordingSettingsRepository()
            val holder = collectedHolder(repository)
            advanceUntilIdle()
            holder.assertPublishedEur()

            holder.close()
            holder.selectCurrency("USD")
            holder.setAnalyticsEnabled(true)
            holder.clearMessage()
            advanceUntilIdle()

            assertEquals(emptyList(), repository.commands)
        }

    @Test
    fun aRenewedObservationKeepsTheKnownSettingsUntilTheRepositoryAnswersAgain() =
        runTest {
            val holder =
                SettingsStateHolder(
                    scope = backgroundScope,
                    repository = RecordingSettingsRepository(),
                    // `io` is a standard dispatcher so the repository answers only on `runCurrent()`, after
                    // anything the holder publishes on its own when the observation restarts.
                    dispatchers =
                        TestDispatcherProvider(
                            UnconfinedTestDispatcher(testScheduler),
                            StandardTestDispatcher(testScheduler),
                        ),
                )
            val firstObserver =
                backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { holder.state.collect {} }
            runCurrent()
            holder.assertPublishedEur()

            firstObserver.cancel()
            advanceTimeBy(STATE_HOLDER_TIMEOUT_MS + 1)
            runCurrent()

            val published = mutableListOf<SettingsUiState>()
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { holder.state.collect { published += it } }
            runCurrent()

            assertEquals(
                listOf<String?>("EUR"),
                published.map { it.currencyCode }.distinct(),
                "a renewed observation republished the settings as unknown: $published",
            )
        }

    @Test
    fun aReadFailureKeepsTheKnownSettingsUntilTheNextSuccessfulRead() =
        runTest {
            val repository = RecordingSettingsRepository()
            val holder = collectedHolder(repository)
            advanceUntilIdle()
            holder.assertPublishedEur()

            repository.emit(Outcome.Err(PersistenceError.DatabaseUnavailable))
            advanceUntilIdle()
            assertEquals("EUR", holder.state.value.currencyCode, "a read failure keeps the settings it followed")
            val readFailure = holder.state.value.message
            assertEquals("PERSISTENCE.DATABASE_UNAVAILABLE", readFailure?.code)

            repository.emit(Outcome.Ok(userSettings(analyticsEnabled = false)))
            advanceUntilIdle()
            assertNull(holder.state.value.message, "a later successful read withdraws the read failure")
        }

    @Test
    fun aFailedWriteReleasesTheSavingGuard() =
        runTest {
            val repository = RecordingSettingsRepository()
            repository.updateResult = Outcome.Err(PersistenceError.TransactionFailed)
            val holder = collectedHolder(repository)
            advanceUntilIdle()

            holder.selectCurrency("USD")
            advanceUntilIdle()
            assertFalse(holder.state.value.isSaving, "a failed write must release the saving guard")

            repository.updateResult = Outcome.Ok(Unit)
            holder.setAnalyticsEnabled(true)
            advanceUntilIdle()
            assertEquals(
                listOf(
                    UpdateSettingsCommand(currency = CurrencyCode("USD"), analyticsEnabled = null),
                    UpdateSettingsCommand(currency = null, analyticsEnabled = true),
                ),
                repository.commands,
            )
        }

    private fun TestScope.collectedHolder(repository: RecordingSettingsRepository): SettingsStateHolder {
        val holder =
            SettingsStateHolder(
                scope = backgroundScope,
                repository = repository,
                // An unconfined test dispatcher, because `backgroundScope` marks its work as background and
                // `advanceUntilIdle()` returns as soon as only background events remain
                // (TestCoroutineScheduler.kt:110): a standard dispatcher would never run the holder.
                dispatchers = TestDispatcherProvider(UnconfinedTestDispatcher(testScheduler)),
            )
        // `WhileSubscribed` observes the repository only while something collects the state (§14).
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { holder.state.collect {} }
        return holder
    }

    private fun SettingsStateHolder.assertPublishedEur() {
        assertEquals("EUR", state.value.currencyCode, "precondition: the repository settings are published")
    }
}

/**
 * An in-memory `SettingsRepository` that records every command, so a test asserts exactly what the
 * holder wrote. A successful write becomes the next emission when [publishWrites] is `true`, as the
 * SQLDelight repository's query notification does; `false` lets a test publish it with [emit].
 */
private class RecordingSettingsRepository(
    initial: Outcome<UserSettings, AppError>? = Outcome.Ok(userSettings(analyticsEnabled = false)),
    private val publishWrites: Boolean = true,
) : SettingsRepository {
    private val latest = MutableStateFlow(initial)

    val commands = mutableListOf<UpdateSettingsCommand>()
    var updateResult: Outcome<Unit, AppError> = Outcome.Ok(Unit)

    /** Holds `updateSettings` open after it has recorded its command. */
    var updateGate: CompletableDeferred<Unit>? = null
    var updateCancellations = 0
        private set

    override val settings: Flow<Outcome<UserSettings, AppError>> = latest.filterNotNull()

    fun emit(result: Outcome<UserSettings, AppError>) {
        latest.value = result
    }

    override suspend fun updateSettings(command: UpdateSettingsCommand): Outcome<Unit, AppError> {
        commands += command
        try {
            updateGate?.await()
        } catch (cancellation: CancellationException) {
            updateCancellations += 1
            throw cancellation
        }
        val result = updateResult
        val current = latest.value
        if (publishWrites && result is Outcome.Ok && current is Outcome.Ok) {
            latest.value =
                Outcome.Ok(
                    current.value.copy(
                        currency = command.currency ?: current.value.currency,
                        analyticsEnabled = command.analyticsEnabled ?: current.value.analyticsEnabled,
                    ),
                )
        }
        return result
    }
}

/** The stable ids the holder publishes (`SettingsStateHolder.kt`). */
private const val READ_ERROR_MESSAGE_ID = 8L
private const val WRITE_ERROR_MESSAGE_ID = 9L
