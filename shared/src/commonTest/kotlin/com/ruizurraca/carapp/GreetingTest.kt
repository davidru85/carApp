package com.ruizurraca.carapp

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class GreetingTest {
    @Test
    fun greetReturnsNonBlankMessageFromCommonMain() {
        val greeting = Greeting()
        val message = greeting.greet()
        assertTrue(message.isNotBlank(), "Greeting from commonMain must not be blank")
    }

    @Test
    fun greetIncludesTheProductTokenCarApp() {
        val greeting = Greeting()
        val message = greeting.greet()
        assertTrue(
            message.contains("carApp", ignoreCase = true),
            "Greeting must include the product token 'carApp'"
        )
    }

    @Test
    fun greetIncludesThePlatformTagWhenProvided() {
        val greeting = Greeting()
        val message = greeting.greet(platform = "Android")
        assertEquals("carApp on Android", message)
    }
}