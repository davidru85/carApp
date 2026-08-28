package com.ruizurraca.carapp.feature.fuel.domain

import com.ruizurraca.carapp.core.common.Outcome
import com.ruizurraca.carapp.core.common.ValidationError
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class ValidateFuelEntryMoneyTest {
    private val validateCreate = ValidateCreateFuelEntry()
    private val context = fuelEntryValidationContext()

    @Test
    fun litersAndPriceDerivesTheGoldenTotal() {
        val values =
            validateCreate(
                createFuelEntryCommand(money = MoneyInput.LitersAndPrice(45_123L, 1_789L)),
                context,
            ).okValue()

        assertEquals(45_123L, values.litersScaled)
        assertEquals(1_789L, values.pricePerLiterScaled)
        assertEquals(8_073L, values.totalCostMinor)
    }

    @Test
    fun litersAndTotalDerivesTheGoldenPrice() {
        val values =
            validateCreate(
                createFuelEntryCommand(money = MoneyInput.LitersAndTotal(40_000L, 6_000L)),
                context,
            ).okValue()

        assertEquals(40_000L, values.litersScaled)
        assertEquals(1_500L, values.pricePerLiterScaled)
        assertEquals(6_000L, values.totalCostMinor)
    }

    @Test
    fun priceAndTotalDerivesTheGoldenLiters() {
        val values =
            validateCreate(
                createFuelEntryCommand(money = MoneyInput.PriceAndTotal(1_500L, 6_000L)),
                context,
            ).okValue()

        assertEquals(40_000L, values.litersScaled)
        assertEquals(1_500L, values.pricePerLiterScaled)
        assertEquals(6_000L, values.totalCostMinor)
    }

    @Test
    fun largestLitersAndPriceInputsUseLongIntermediates() {
        val values =
            validateCreate(
                createFuelEntryCommand(money = MoneyInput.LitersAndPrice(500_000L, 999_999L)),
                context,
            ).okValue()

        assertEquals(500_000L, values.litersScaled)
        assertEquals(999_999L, values.pricePerLiterScaled)
        assertEquals(49_999_950L, values.totalCostMinor)
    }

    @Test
    fun everyIndividuallyReachableClosedMoneyBoundarySucceeds() {
        val cases =
            listOf(
                MoneyInput.LitersAndPrice(1L, 10_000L),
                MoneyInput.LitersAndPrice(500_000L, 1_000L),
                MoneyInput.LitersAndPrice(500_000L, 1L),
                MoneyInput.LitersAndPrice(1L, 999_999L),
                MoneyInput.LitersAndTotal(1_000L, 1L),
            )

        cases.forEach { money ->
            assertIs<Outcome.Ok<ValidatedFuelEntryValues>>(
                validateCreate(createFuelEntryCommand(money = money), context),
                "Expected the reachable boundary to succeed for $money",
            )
        }
    }

    @Test
    fun suppliedLitersOutsideEitherClosedBoundReturnsOutOfRangeWithoutArithmetic() {
        listOf(Long.MIN_VALUE, 0L, 500_001L, Long.MAX_VALUE).forEach { liters ->
            val result =
                validateCreate(
                    createFuelEntryCommand(money = MoneyInput.LitersAndPrice(liters, 1_500L)),
                    context,
                )

            assertEquals(
                Outcome.Err(ValidationError.OutOfRange("litersScaled", 1L, 500_000L)),
                result,
            )
        }
    }

    @Test
    fun suppliedPriceOutsideEitherClosedBoundReturnsOutOfRangeWithoutArithmetic() {
        listOf(Long.MIN_VALUE, 0L, 1_000_000L, Long.MAX_VALUE).forEach { price ->
            val result =
                validateCreate(
                    createFuelEntryCommand(money = MoneyInput.LitersAndPrice(40_000L, price)),
                    context,
                )

            assertEquals(
                Outcome.Err(ValidationError.OutOfRange("pricePerLiterScaled", 1L, 999_999L)),
                result,
            )
        }
    }

    @Test
    fun suppliedTotalOutsideEitherClosedBoundReturnsOutOfRangeWithoutArithmetic() {
        listOf(Long.MIN_VALUE, 0L, 100_000_000L, Long.MAX_VALUE).forEach { total ->
            val result =
                validateCreate(
                    createFuelEntryCommand(money = MoneyInput.LitersAndTotal(40_000L, total)),
                    context,
                )

            assertEquals(
                Outcome.Err(ValidationError.OutOfRange("totalCostMinor", 1L, 99_999_999L)),
                result,
            )
        }
    }

    @Test
    fun everyDerivedValueIsRangeCheckedBeforeSuccess() {
        val cases =
            listOf(
                MoneyInput.LitersAndPrice(1L, 1L) to
                    ValidationError.OutOfRange("totalCostMinor", 1L, 99_999_999L),
                MoneyInput.LitersAndTotal(1L, 99_999_999L) to
                    ValidationError.OutOfRange("pricePerLiterScaled", 1L, 999_999L),
                MoneyInput.PriceAndTotal(1L, 99_999_999L) to
                    ValidationError.OutOfRange("litersScaled", 1L, 500_000L),
            )

        cases.forEach { (money, error) ->
            assertEquals(
                Outcome.Err(error),
                validateCreate(createFuelEntryCommand(money = money), context),
            )
        }
    }

    @Test
    fun updateUsesTheSameCanonicalMoneyDerivation() {
        val values =
            ValidateUpdateFuelEntry()(
                updateFuelEntryCommand(money = MoneyInput.LitersAndTotal(40_000L, 6_000L)),
                context,
            ).okValue()

        assertEquals(1_500L, values.pricePerLiterScaled)
    }
}
