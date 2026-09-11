package com.ruizurraca.carapp

import com.ruizurraca.carapp.core.testing.TestDispatcherProvider
import com.ruizurraca.carapp.shared.testing.testAppGraphDependencies
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope

/**
 * Shared fixture for graph-backed editor tests. All injected work uses the caller's scheduler;
 * customized database, owner and provider doubles are preserved. Default and customized calls
 * share the scheduling regressions in GraphTestDependenciesTest.
 */
internal fun TestScope.confinedGraphDependencies(
    dependencies: AppGraphDependencies = testAppGraphDependencies(),
): AppGraphDependencies = dependencies.copy(dispatchers = TestDispatcherProvider(StandardTestDispatcher(testScheduler)))
