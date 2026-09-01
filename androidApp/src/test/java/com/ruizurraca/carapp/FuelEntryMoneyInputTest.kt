package com.ruizurraca.carapp

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class FuelEntryMoneyInputTest {
    @Test
    fun scaledEditingAcceptsDigitsAndEitherDecimalSeparator() {
        assertEquals("1", acceptScaledInput(previous = "", candidate = "1", scale = 3))
        assertEquals("1.", acceptScaledInput(previous = "1", candidate = "1.", scale = 3))
        assertEquals("1.549", acceptScaledInput(previous = "1.54", candidate = "1.549", scale = 3))
        assertEquals("40,001", acceptScaledInput(previous = "40,00", candidate = "40,001", scale = 3))
    }

    @Test
    fun scaledEditingRejectsInvalidSuffixWithoutDestroyingAcceptedPrefix() {
        assertEquals("1.549", acceptScaledInput(previous = "1.549", candidate = "1.5499", scale = 3))
        assertEquals("1.5", acceptScaledInput(previous = "1.5", candidate = "1.5,", scale = 3))
        assertEquals("1.5", acceptScaledInput(previous = "1.5", candidate = "1.5x", scale = 3))
    }

    @Test
    fun scaledEditingParsesIncompleteAndCanonicalValuesWithoutFloatingPoint() {
        assertNull(parseScaled("", scale = 3))
        assertEquals(1_000L, parseScaled("1.", scale = 3))
        assertEquals(1_000L, parseScaled("1,", scale = 3))
        assertEquals(1_549L, parseScaled("1.549", scale = 3))
        assertEquals("1.549", formatScaled(1_549L, scale = 3))
    }

    @Test
    fun odometerTextAcceptsEmptyAndDigitsOnly() {
        assertTrue(isValidOdometerText(""))
        assertTrue(isValidOdometerText("123"))
        assertTrue(isValidOdometerText("1234567890"))
        assertFalse(isValidOdometerText("12a"))
        assertFalse(isValidOdometerText("12.5"))
        assertFalse(isValidOdometerText("12345678901"))
    }
}
