package com.ruizurraca.carapp

import java.lang.reflect.Modifier
import kotlin.test.Test
import kotlin.test.assertTrue

class LastEmissionVisibilityTest {
    @Test
    fun diagnosticValueIsPublishedAcrossCollectorThreads() {
        val field = LastEmission::class.java.getDeclaredField("value")

        assertTrue(Modifier.isVolatile(field.modifiers))
    }
}
