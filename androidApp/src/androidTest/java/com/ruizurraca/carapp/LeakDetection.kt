package com.ruizurraca.carapp

import android.app.Application
import androidx.test.platform.app.InstrumentationRegistry
import leakcanary.AppWatcher
import leakcanary.DetectLeaksAfterTestSuccess
import org.junit.rules.RuleChain
import org.junit.rules.TestRule
import org.junit.runner.Description
import org.junit.runners.model.Statement

/**
 * `E4-03` (`D-256`, `D-263`, ADR-0257, ADR-0264): wraps a Compose test rule so the test fails when it
 * leaves an application leak behind.
 *
 * The leak check runs outside [inner], after the activity it launched is destroyed, so a leaked
 * activity, view model or view is still reachable and caught. `AppWatcher` is normally installed by the
 * `MainProcessAppWatcherInstaller` provider that `debugImplementation` brings into the application
 * process; installing it here only covers a build where that provider did not run, so the chain never
 * depends on resources that only the test APK carries (`D-263`).
 */
internal fun leakDetecting(inner: TestRule): RuleChain =
    RuleChain
        .outerRule(AppWatcherInstallation())
        .around(DetectLeaksAfterTestSuccess())
        .around(inner)

private class AppWatcherInstallation : TestRule {
    override fun apply(
        base: Statement,
        description: Description,
    ): Statement =
        object : Statement() {
            override fun evaluate() {
                val instrumentation = InstrumentationRegistry.getInstrumentation()
                instrumentation.runOnMainSync {
                    if (!AppWatcher.isInstalled) {
                        AppWatcher.manualInstall(instrumentation.targetContext.applicationContext as Application)
                    }
                }
                base.evaluate()
            }
        }
}
