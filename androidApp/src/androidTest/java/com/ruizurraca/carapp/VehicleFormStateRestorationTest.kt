package com.ruizurraca.carapp

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import com.ruizurraca.carapp.core.common.AppError
import com.ruizurraca.carapp.core.common.DispatcherProvider
import com.ruizurraca.carapp.core.common.Outcome
import com.ruizurraca.carapp.core.model.EntityId
import com.ruizurraca.carapp.core.model.Vehicle
import com.ruizurraca.carapp.feature.vehicle.domain.CreateVehicleCommand
import com.ruizurraca.carapp.feature.vehicle.domain.UpdateVehicleCommand
import com.ruizurraca.carapp.feature.vehicle.domain.VehicleEditFacts
import com.ruizurraca.carapp.feature.vehicle.domain.VehicleRepository
import com.ruizurraca.carapp.feature.vehicle.presentation.createVehicleFormStateHolder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class VehicleFormStateRestorationTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun restoredDraftIsRepublishedIntoAFreshHolderBeforeSave() {
        val commands = mutableListOf<CreateVehicleCommand>()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        val repository = EmptyVehicleRepository()
        fun newHolder() =
            createVehicleFormStateHolder(
                scope = scope,
                vehicleId = null,
                repository = repository,
                dispatchers = AndroidTestDispatcherProvider,
                createVehicle = { command ->
                    commands += command
                    Outcome.Ok(EntityId(VEHICLE_ID))
                },
                updateVehicle = { Outcome.Ok(Unit) },
            )

        var holder = newHolder()
        val restorationTester = StateRestorationTester(composeRule)
        restorationTester.setContent {
            MaterialTheme {
                VehicleFormScreen(
                    stateHolder = holder,
                    originalVehicleId = null,
                    onBack = {},
                    onSaved = {},
                )
            }
        }
        composeRule.onNodeWithTag(VehicleTestTags.NAME).performTextReplacement(DRAFT_NAME)
        composeRule.onNodeWithTag(VehicleTestTags.ODOMETER).performTextReplacement(DRAFT_ODOMETER.toString())
        composeRule.waitForIdle()

        holder.close()
        holder = newHolder()
        restorationTester.emulateSavedInstanceStateRestore()
        composeRule.waitForIdle()

        composeRule.onNodeWithTag(VehicleTestTags.NAME).assertTextContains(DRAFT_NAME)
        composeRule.onNodeWithTag(VehicleTestTags.ODOMETER).assertTextContains(DRAFT_ODOMETER.toString())
        composeRule.onNodeWithTag(VehicleTestTags.SAVE).performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) { commands.isNotEmpty() }

        assertEquals(DRAFT_NAME, commands.single().name)
        assertEquals(DRAFT_ODOMETER, commands.single().initialOdometerKm)
        holder.close()
        scope.cancel()
    }
}

private object AndroidTestDispatcherProvider : DispatcherProvider {
    override val main = Dispatchers.Main.immediate
    override val default = Dispatchers.Default
    override val io = Dispatchers.Main.immediate
}

private class EmptyVehicleRepository : VehicleRepository {
    override fun observeVehicles(includeDeleted: Boolean): Flow<Outcome<List<Vehicle>, AppError>> =
        flowOf(Outcome.Ok(emptyList()))

    override fun observeVehicle(id: EntityId): Flow<Outcome<Vehicle?, AppError>> = flowOf(Outcome.Ok(null))

    override fun observeVehicleEditFacts(id: EntityId): Flow<Outcome<VehicleEditFacts?, AppError>> =
        flowOf(Outcome.Ok(null))

    override suspend fun createVehicle(command: CreateVehicleCommand): Outcome<EntityId, AppError> =
        Outcome.Ok(EntityId(VEHICLE_ID))

    override suspend fun updateVehicle(command: UpdateVehicleCommand): Outcome<Unit, AppError> = Outcome.Ok(Unit)

    override suspend fun deleteVehicle(id: EntityId): Outcome<Unit, AppError> = Outcome.Ok(Unit)
}

private const val VEHICLE_ID = "00000000-0000-4000-8000-000000000099"
private const val DRAFT_NAME = "Restored draft"
private const val DRAFT_ODOMETER = 321L
