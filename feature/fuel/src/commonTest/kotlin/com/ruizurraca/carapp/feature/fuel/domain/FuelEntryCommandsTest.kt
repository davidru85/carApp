package com.ruizurraca.carapp.feature.fuel.domain

import com.ruizurraca.carapp.core.common.Confirmation
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class FuelEntryCommandsTest {
    @Test
    fun moneyInputHasExactlyTheThreeCanonicalPairs() {
        val inputs =
            listOf(
                MoneyInput.LitersAndPrice(40_000L, 1_500L),
                MoneyInput.LitersAndTotal(40_000L, 6_000L),
                MoneyInput.PriceAndTotal(1_500L, 6_000L),
            )

        assertEquals(3, inputs.size)
        assertIs<MoneyInput.LitersAndPrice>(inputs[0])
        assertIs<MoneyInput.LitersAndTotal>(inputs[1])
        assertIs<MoneyInput.PriceAndTotal>(inputs[2])
    }

    @Test
    fun createCommandCarriesNoIdentityOrPersistenceMetadata() {
        val command =
            createFuelEntryCommand(
                confirmations = setOf(Confirmation.OdometerInconsistent),
            )

        assertEquals("11111111-1111-4111-8111-111111111111", command.vehicleId.value)
        assertEquals(setOf(Confirmation.OdometerInconsistent), command.confirmations)
        assertIs<MoneyInput.LitersAndPrice>(command.money)
    }

    @Test
    fun updateCommandCarriesOnlyItsCanonicalTargetIdentity() {
        val command = updateFuelEntryCommand()

        assertEquals("22222222-2222-4222-8222-222222222222", command.id.value)
        assertEquals("11111111-1111-4111-8111-111111111111", command.vehicleId.value)
        assertIs<MoneyInput.LitersAndPrice>(command.money)
    }
}
