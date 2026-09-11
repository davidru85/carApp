package com.ruizurraca.carapp

import com.ruizurraca.carapp.core.testing.TestDispatcherProvider
import com.ruizurraca.carapp.shared.testing.testAppGraphDependencies
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope

internal fun TestScope.confinedGraphDependencies(
    dependencies: AppGraphDependencies = testAppGraphDependencies(),
): AppGraphDependencies = dependencies.copy(dispatchers = TestDispatcherProvider(StandardTestDispatcher(testScheduler)))
