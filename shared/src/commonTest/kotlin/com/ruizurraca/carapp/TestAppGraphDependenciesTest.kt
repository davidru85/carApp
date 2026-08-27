package com.ruizurraca.carapp

import com.ruizurraca.carapp.shared.testing.testAppGraphDependencies
import kotlin.test.Test
import kotlin.test.assertTrue

class TestAppGraphDependenciesTest {
    @Test
    fun defaultFactoryBuildsWithoutKoin() {
        val dependencies = testAppGraphDependencies()

        assertTrue(dependencies.isDebugBuild)
    }
}
