package com.ruizurraca.carapp.buildlogic.contract

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The Konsist fixture `docs/CONTRACTS.md §20.10` declares: `PostWriteDebounce`, `ConnectivityRecovered`
 * and `Periodic` MUST NOT be invoked from Swift UI code, because firing them there would duplicate the
 * `WorkManager` / `BGTaskScheduler` wiring and bypass the single-`SyncController` invariant of `§9.1`.
 *
 * The rule is a pure function over source text so it has a *failing* fixture, which is what
 * `AGENTS.md` requires of every architecture rule: a rule whose only exercise is the real repository
 * cannot be shown to fire at all. The fixture mutates a real host file in memory and asserts the
 * violation, so the rule is proved to fire today and keeps firing when the real files change.
 *
 * The scanned surface is the whole iOS platform boundary — Kotlin `iosMain` and Swift `iosApp` — and
 * the banned set is the platform-owned trio. `AppForeground` and `PullToRefresh` are the two triggers
 * a UI layer *is* allowed to request, so banning them would reject the documented surface.
 */
class SwiftTriggerSurfaceContractTest {
    private val repositoryRoot = File(checkNotNull(System.getProperty("carapp.repoRoot")))

    @Test
    fun theRepositoryDoesNotFirePlatformOwnedTriggersFromTheIosUiSurface() {
        assertEquals(emptyList(), SwiftTriggerSurfaceRule.violations(presentSources()))
    }

    @Test
    fun aPostWriteDebounceRequestFromSwiftIsRejected() {
        assertRejected("PostWriteDebounce")
    }

    @Test
    fun aConnectivityRecoveredRequestFromSwiftIsRejected() {
        assertRejected("ConnectivityRecovered")
    }

    @Test
    fun aPeriodicRequestFromSwiftIsRejected() {
        assertRejected("Periodic")
    }

    @Test
    fun anAppForegroundRequestFromSwiftIsAccepted() {
        // The positive direction. Without it the rule could reject every trigger and still pass the
        // three tests above, which is exactly how a rule silently becomes broader than the contract.
        assertTrue(
            SwiftTriggerSurfaceRule
                .violations(mapOf(FIXTURE_PATH to "syncStateHolder.requestSync(reason: .appForeground)"))
                .isEmpty(),
            "§9.8 permits a lifecycle hook to request AppForeground",
        )
    }

    private fun assertRejected(trigger: String) {
        val violations =
            SwiftTriggerSurfaceRule.violations(
                mapOf(FIXTURE_PATH to "syncStateHolder.requestSync(reason: .${trigger.lowercase()})"),
            )
        assertEquals(
            listOf("$FIXTURE_PATH:1 requests $trigger"),
            violations,
            "§20.10 bans $trigger from the iOS UI surface",
        )
    }

    /** The real iOS platform boundary: Kotlin `iosMain` plus the Swift host sources. */
    private fun presentSources(): Map<String, String> =
        listOf(
            repositoryRoot.resolve("composition/ios/src/iosMain"),
            repositoryRoot.resolve("iosApp"),
        ).flatMap { root ->
            root
                .walkTopDown()
                .filter { it.isFile && it.extension in setOf("kt", "swift") }
                .map { it.relativeTo(repositoryRoot).invariantSeparatorsPath to it.readText() }
                .toList()
        }.toMap()

    private companion object {
        const val FIXTURE_PATH = "iosApp/Fixture.swift"
    }
}
