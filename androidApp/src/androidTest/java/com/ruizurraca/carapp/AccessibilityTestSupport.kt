package com.ruizurraca.carapp

import android.content.Context
import android.content.res.Configuration
import android.os.ParcelFileDescriptor
import android.os.SystemClock
import android.util.Log
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasScrollToNodeAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.tryPerformAccessibilityChecks
import androidx.test.platform.app.InstrumentationRegistry
import com.google.android.apps.common.testing.accessibility.framework.AccessibilityCheckResult.AccessibilityCheckResultType
import com.google.android.apps.common.testing.accessibility.framework.AccessibilityViewCheckResult
import com.google.android.apps.common.testing.accessibility.framework.integrations.espresso.AccessibilityValidator
import com.ruizurraca.carapp.core.common.AppError
import com.ruizurraca.carapp.core.common.Confirmation
import com.ruizurraca.carapp.core.common.DispatcherProvider
import com.ruizurraca.carapp.core.common.Outcome
import com.ruizurraca.carapp.core.common.SyncStatus
import com.ruizurraca.carapp.core.common.UiMessage
import com.ruizurraca.carapp.core.common.UiMessageKind
import com.ruizurraca.carapp.core.model.ConsumptionInvalidReason
import com.ruizurraca.carapp.core.model.ConsumptionReport
import com.ruizurraca.carapp.core.model.DistanceUnit
import com.ruizurraca.carapp.core.model.EntityId
import com.ruizurraca.carapp.core.model.FuelEntry
import com.ruizurraca.carapp.core.model.FuelEntryListItem
import com.ruizurraca.carapp.core.model.FuelType
import com.ruizurraca.carapp.core.model.Vehicle
import com.ruizurraca.carapp.core.model.VolumeUnit
import com.ruizurraca.carapp.feature.fuel.domain.CreateFuelEntryCommand
import com.ruizurraca.carapp.feature.fuel.domain.FuelEntryRepository
import com.ruizurraca.carapp.feature.fuel.domain.UpdateFuelEntryCommand
import com.ruizurraca.carapp.feature.fuel.presentation.FuelEntryFormStateHolder
import com.ruizurraca.carapp.feature.fuel.presentation.FuelEntryListItemUi
import com.ruizurraca.carapp.feature.fuel.presentation.FuelEntryListUiState
import com.ruizurraca.carapp.feature.fuel.presentation.createFuelEntryFormStateHolder
import com.ruizurraca.carapp.feature.vehicle.domain.CreateVehicleCommand
import com.ruizurraca.carapp.feature.vehicle.domain.UpdateVehicleCommand
import com.ruizurraca.carapp.feature.vehicle.domain.VehicleEditFacts
import com.ruizurraca.carapp.feature.vehicle.domain.VehicleRepository
import com.ruizurraca.carapp.feature.vehicle.presentation.VehicleFormStateHolder
import com.ruizurraca.carapp.feature.vehicle.presentation.VehicleListItemUi
import com.ruizurraca.carapp.feature.vehicle.presentation.createVehicleFormStateHolder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flowOf
import java.util.Locale
import java.lang.reflect.Modifier as ReflectModifier

/*
 * Shared support of the `E4-02` accessibility, font-scale and Spanish-locale instrumented tests.
 */

internal const val A11Y_LOG_TAG = "CarAppA11y"
internal const val DOUBLE_FONT_SCALE = 2f
internal const val SPANISH_TAG = "es-ES"
internal const val A11Y_APP_VERSION = "0.0.1"
internal const val A11Y_VEHICLE_ID = "00000000-0000-4000-8000-0000000000a1"
internal const val A11Y_ENTRY_ID = "00000000-0000-4000-8000-0000000000a2"

/** 2025-10-09T08:53:20Z: in the past, so the fake fuel form never reports a future date. */
internal const val A11Y_DATE_MILLIS = 1_760_000_000_000L
internal const val A11Y_ODOMETER_KM = 142_500L

/** The `E4-01` tag on the vehicle-list control that opens Settings. */
internal val SETTINGS_ENTRY_TAG: String = SettingsTestTags.ENTRY

internal val SPANISH: Locale = Locale.forLanguageTag(SPANISH_TAG)

/** `D-216`: the default ATF validator, reporting every ERROR and WARNING to logcat for the handoff. */
internal fun carAppAccessibilityValidator(): AccessibilityValidator =
    AccessibilityValidator()
        .setRunChecksFromRootView(true)
        .setThrowExceptionFor(AccessibilityCheckResultType.ERROR)
        .addCheckListener(AccessibilityResultLogger)

private object AccessibilityResultLogger : AccessibilityValidator.AccessibilityCheckListener {
    override fun onResults(
        context: Context,
        results: List<AccessibilityViewCheckResult>,
    ) {
        results
            .filter { result ->
                result.type == AccessibilityCheckResultType.ERROR || result.type == AccessibilityCheckResultType.WARNING
            }.forEach { result ->
                Log.i(
                    A11Y_LOG_TAG,
                    "${result.type} ${result.sourceCheckClass.simpleName}: ${result.getMessage(Locale.ENGLISH)}",
                )
            }
    }
}

/** Runs ATF over every Compose root, dialogs included; `onRoot()` would fail once a dialog is open. */
internal fun ComposeTestRule.checkAccessibilityOfEveryWindow() {
    waitForIdle()
    onAllNodes(isRoot()).onFirst().tryPerformAccessibilityChecks()
}

/** Scrolls the node into view when it sits in a scrolling container, then asserts it is on screen. */
internal fun ComposeTestRule.scrollToAndAssertDisplayed(matcher: SemanticsMatcher) {
    if (onAllNodes(matcher).fetchSemanticsNodes().isEmpty()) {
        onAllNodes(hasScrollToNodeAction()).onFirst().performScrollToNode(matcher)
    }
    if (onAllNodes(matcher and hasAnyAncestor(hasScrollAction())).fetchSemanticsNodes().isNotEmpty()) {
        onNode(matcher).performScrollTo()
    }
    onNode(matcher).assertIsDisplayed()
}

internal fun ComposeTestRule.scrollToAndAssertDisplayed(tag: String) = scrollToAndAssertDisplayed(hasTestTag(tag))

/**
 * Every candidate tag the screen renders for the state under test is reachable and displayed.
 * Returns the tags that were rendered, so a test can assert that the screen rendered enough of them.
 */
internal fun ComposeTestRule.assertRenderedTagsReachable(candidateTags: List<String>): List<String> =
    candidateTags.filter { tag -> isRendered(tag) }.onEach { tag -> scrollToAndAssertDisplayed(tag) }

private fun ComposeTestRule.isRendered(tag: String): Boolean {
    if (hasNodeWithTag(tag)) return true
    if (onAllNodes(hasScrollToNodeAction()).fetchSemanticsNodes().isEmpty()) return false
    return runCatching { onAllNodes(hasScrollToNodeAction()).onFirst().performScrollToNode(hasTestTag(tag)) }.isSuccess
}

/** The `const val` strings of a test-tag object such as `SettingsTestTags`. */
internal fun stringConstantsOf(holder: Class<*>): List<String> =
    holder.declaredFields
        .filter { field -> ReflectModifier.isStatic(field.modifiers) && field.type == String::class.java }
        .map { field ->
            field.isAccessible = true
            field.get(null) as String
        }

internal fun hasTestTagPrefix(prefix: String): SemanticsMatcher =
    SemanticsMatcher("test tag starts with $prefix") { node ->
        node.config.getOrNull(SemanticsProperties.TestTag)?.startsWith(prefix) == true
    }

internal fun hasTextContaining(text: String): SemanticsMatcher = hasText(text, substring = true)

/** The same `MaterialTheme { Surface }` the activity renders (`MainActivity.kt:96-105`), so contrast matches. */
@Composable
internal fun CarAppTestTheme(content: @Composable () -> Unit) {
    MaterialTheme {
        Surface(modifier = Modifier.fillMaxSize()) {
            content()
        }
    }
}

internal fun targetString(resource: Int): String =
    InstrumentationRegistry.getInstrumentation().targetContext.getString(resource)

internal fun localizedString(
    languageTag: String,
    resource: Int,
    vararg formatArgs: Any,
): String {
    val context = InstrumentationRegistry.getInstrumentation().targetContext
    val configuration =
        Configuration(context.resources.configuration).apply { setLocale(Locale.forLanguageTag(languageTag)) }
    return context.createConfigurationContext(configuration).getString(resource, *formatArgs)
}

/** The locale the app under test renders with on this device. */
internal fun deviceLocale(): Locale =
    InstrumentationRegistry
        .getInstrumentation()
        .targetContext.resources.configuration.locales
        .get(0)

/** Changes the device-wide font scale through the shell and waits until the app sees it. */
internal fun setSystemFontScale(scale: Float) {
    val instrumentation = InstrumentationRegistry.getInstrumentation()
    ParcelFileDescriptor
        .AutoCloseInputStream(instrumentation.uiAutomation.executeShellCommand("settings put system font_scale $scale"))
        .use { stream -> stream.readBytes() }
    val deadline = SystemClock.uptimeMillis() + FONT_SCALE_TIMEOUT_MS
    while (
        instrumentation.targetContext.resources.configuration.fontScale != scale &&
        SystemClock.uptimeMillis() < deadline
    ) {
        SystemClock.sleep(FONT_SCALE_POLL_MS)
    }
}

private const val FONT_SCALE_TIMEOUT_MS = 5_000L
private const val FONT_SCALE_POLL_MS = 100L

internal fun a11yMessage(
    code: String,
    kind: UiMessageKind = UiMessageKind.ERROR,
    confirmation: Confirmation? = null,
): UiMessage = UiMessage(id = 1L, kind = kind, code = code, confirmation = confirmation)

internal fun a11ySession(
    phase: SessionPhase,
    isBusy: Boolean = false,
    message: UiMessage? = null,
    pendingSyncCount: Int? = null,
    pendingDepartureRetry: DepartureRetry? = null,
): SessionUiState =
    SessionUiState(
        phase = phase,
        providers = emptyList(),
        isBusy = isBusy,
        message = message,
        anonymousReminderIndex = null,
        pendingSyncCount = pendingSyncCount,
        pendingDepartureRetry = pendingDepartureRetry,
    )

internal fun a11ySync(status: SyncStatus = SyncStatus.Idle): SyncUiState =
    SyncUiState(status = status, isOnline = true, message = null)

internal fun a11ySettings(): SettingsUiState =
    SettingsUiState(
        currencyCode = "EUR",
        supportedCurrencyCodes = listOf("EUR", "USD"),
        distanceUnit = DistanceUnit.KM,
        volumeUnit = VolumeUnit.LITER,
        analyticsEnabled = false,
        isSaving = false,
        message = null,
    )

/** Callbacks that do nothing observable, from the `E4-01` fixtures (`SettingsTestFixtures.kt`). */
internal fun a11ySettingsCallbacks(): SettingsCallbacks = recordingSettingsCallbacks(mutableListOf())

internal fun a11yPromptCallbacks(): SessionPromptCallbacks = recordingSessionPromptCallbacks(mutableListOf())

/** The `B1-D4` departure screen with inert actions (signature of `DepartureScreen.kt`, `E4-01`). */
@Composable
internal fun A11yDepartureScreen(session: SessionUiState) {
    DepartureScreen(
        session = session,
        onRetry = {},
        onDismissMessage = {},
        onReauthenticate = {},
        onCancelReauthentication = {},
    )
}

internal fun pendingSyncSession(): SessionUiState =
    a11ySession(
        phase = SessionPhase.PERMANENT,
        message = a11yMessage("WARNING.PENDING_SYNC", UiMessageKind.WARNING, Confirmation.DiscardPendingChanges),
        pendingSyncCount = 3,
    )

internal fun deleteLocalDataSession(): SessionUiState =
    a11ySession(
        phase = SessionPhase.LOCAL,
        message = a11yMessage("CONFIRMATION.DeleteLocalData", UiMessageKind.WARNING, Confirmation.DeleteLocalData),
    )

internal fun failedDepartureSession(): SessionUiState =
    a11ySession(
        phase = SessionPhase.DELETING,
        message = a11yMessage("PERSISTENCE.TRANSACTION_FAILED"),
        pendingDepartureRetry = DepartureRetry.LOCAL_CLEAR,
    )

internal fun conversionCollisionSession(): SessionUiState =
    a11ySession(
        phase = SessionPhase.ANONYMOUS,
        message =
            a11yMessage(
                code = "CONFIRMATION.AdoptExistingAccount",
                kind = UiMessageKind.WARNING,
                confirmation = Confirmation.AdoptExistingAccount,
            ),
    )

internal val A11Y_VEHICLE =
    VehicleListItemUi(
        id = A11Y_VEHICLE_ID,
        name = "Golf",
        currentOdometerKm = A11Y_ODOMETER_KM,
        fuelType = FuelType.DIESEL,
        deleted = false,
    )

/** One partial refuel carrying all three indicators, and a reliable 7.24 L/100 km average. */
internal fun a11yFuelState(): FuelEntryListUiState =
    FuelEntryListUiState(
        vehicleId = A11Y_VEHICLE_ID,
        isLoading = false,
        entries =
            listOf(
                FuelEntryListItemUi(
                    id = A11Y_ENTRY_ID,
                    dateEpochMillis = A11Y_DATE_MILLIS,
                    odometerKm = A11Y_ODOMETER_KM,
                    litersScaled = 45_200L,
                    totalCostMinor = 7_363L,
                    currencyCode = "EUR",
                    isFullTank = false,
                    consumptionScaled = null,
                    invalidReason = ConsumptionInvalidReason.EndEntryNotFullTank,
                    hasMissedEntries = true,
                    odometerInconsistent = true,
                ),
            ),
        consumptionAverageScaled = 724L,
        validConsumptionSegmentCount = 2,
        isConsumptionReliable = true,
        syncStatus = SyncStatus.Idle,
        message = null,
    )

internal fun newA11yVehicleFormHolder(scope: CoroutineScope): VehicleFormStateHolder =
    createVehicleFormStateHolder(
        scope = scope,
        vehicleId = null,
        repository = A11yVehicleRepository(),
        dispatchers = A11yDispatchers,
        createVehicle = { Outcome.Ok(EntityId(A11Y_VEHICLE_ID)) },
        updateVehicle = { Outcome.Ok(Unit) },
    )

internal fun newA11yFuelEntryFormHolder(scope: CoroutineScope): FuelEntryFormStateHolder =
    createFuelEntryFormStateHolder(
        scope = scope,
        vehicleId = A11Y_VEHICLE_ID,
        entryId = null,
        initialDateEpochMillis = A11Y_DATE_MILLIS,
        initialOdometerKm = flowOf(A11Y_ODOMETER_KM),
        initialCurrencyCode = "EUR",
        settingsCurrencyCode = flowOf("EUR"),
        repository = A11yFuelEntryRepository(),
        dispatchers = A11yDispatchers,
    )

internal object A11yDispatchers : DispatcherProvider {
    override val main = Dispatchers.Main.immediate
    override val default = Dispatchers.Default
    override val io = Dispatchers.Main.immediate
}

private class A11yVehicleRepository : VehicleRepository {
    override fun observeVehicles(includeDeleted: Boolean): Flow<Outcome<List<Vehicle>, AppError>> =
        flowOf(Outcome.Ok(emptyList()))

    override fun observeVehicle(id: EntityId): Flow<Outcome<Vehicle?, AppError>> = flowOf(Outcome.Ok(null))

    override fun observeVehicleEditFacts(id: EntityId): Flow<Outcome<VehicleEditFacts?, AppError>> =
        flowOf(Outcome.Ok(null))

    override suspend fun createVehicle(command: CreateVehicleCommand): Outcome<EntityId, AppError> =
        Outcome.Ok(EntityId(A11Y_VEHICLE_ID))

    override suspend fun updateVehicle(command: UpdateVehicleCommand): Outcome<Unit, AppError> = Outcome.Ok(Unit)

    override suspend fun deleteVehicle(id: EntityId): Outcome<Unit, AppError> = Outcome.Ok(Unit)
}

private class A11yFuelEntryRepository : FuelEntryRepository {
    override fun observeFuelEntries(
        vehicleId: EntityId,
        includeDeleted: Boolean,
    ): Flow<Outcome<List<FuelEntryListItem>, AppError>> = flowOf(Outcome.Ok(emptyList()))

    override suspend fun getFuelEntry(id: EntityId): Outcome<FuelEntry?, AppError> = Outcome.Ok(null)

    override suspend fun createFuelEntry(command: CreateFuelEntryCommand): Outcome<EntityId, AppError> =
        Outcome.Ok(EntityId(A11Y_ENTRY_ID))

    override suspend fun updateFuelEntry(command: UpdateFuelEntryCommand): Outcome<Unit, AppError> = Outcome.Ok(Unit)

    override suspend fun deleteFuelEntry(id: EntityId): Outcome<Unit, AppError> = Outcome.Ok(Unit)

    override fun observeConsumption(vehicleId: EntityId): Flow<Outcome<ConsumptionReport, AppError>> = emptyFlow()
}
