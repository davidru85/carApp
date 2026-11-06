package com.ruizurraca.carapp

import android.app.Activity
import android.os.Handler
import android.os.Looper
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import leakcanary.NoLeakAssertionFailedError
import org.junit.After
import org.junit.Assert.assertThrows
import org.junit.Test
import org.junit.rules.TestRule
import org.junit.runner.Description
import org.junit.runner.RunWith
import org.junit.runners.model.Statement

/**
 * `E5-09` (`D-269`, ADR-0270): firing fixtures for the leak check of [leakDetecting], one per direction.
 *
 * Each fixture runs the real rule chain. An Android framework message held a destroyed activity for about
 * 8 seconds on CI, which must pass; an activity that application code keeps must still fail.
 */
@RunWith(AndroidJUnit4::class)
class LeakDetectionTest {
    @After
    fun releaseTheRetainedActivity() {
        retainedByApplicationCode = null
    }

    @Test
    fun anActivityHeldByADelayedMessageForEightSecondsPassesTheLeakCheck() {
        runThroughTheLeakCheck {
            Handler(Looper.getMainLooper()).postDelayed(HoldActivity(destroyedActivity()), HOLD_MILLIS)
        }
    }

    @Test
    fun anActivityRetainedByApplicationCodeFailsTheLeakCheck() {
        assertThrows(NoLeakAssertionFailedError::class.java) {
            runThroughTheLeakCheck {
                retainedByApplicationCode = destroyedActivity()
            }
        }
    }

    private fun runThroughTheLeakCheck(fixture: () -> Unit) {
        leakDetecting(TestRule { base, _ -> base })
            .apply(
                object : Statement() {
                    override fun evaluate() = fixture()
                },
                Description.createTestDescription(javaClass, "leakCheckFixture"),
            ).evaluate()
    }

    private fun destroyedActivity(): Activity {
        lateinit var activity: Activity
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { launched -> activity = launched }
        }
        return activity
    }

    /** Holds the destroyed activity until the main looper runs it, the shape of the framework message. */
    private class HoldActivity(
        private val activity: Activity,
    ) : Runnable {
        override fun run() {
            check(activity.isDestroyed)
        }
    }

    private companion object {
        const val HOLD_MILLIS = 8_000L
        var retainedByApplicationCode: Activity? = null
    }
}
