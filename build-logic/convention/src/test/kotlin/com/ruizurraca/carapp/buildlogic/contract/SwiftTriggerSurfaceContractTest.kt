package com.ruizurraca.carapp.buildlogic.contract

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The source rule required by `docs/CONTRACTS.md §20.10` declares: `OwnerChanged`,
 * `PostWriteDebounce`, `ConnectivityRecovered` and `Periodic` MUST NOT be invoked from Swift UI
 * code, because firing them there would duplicate the `WorkManager` / `BGTaskScheduler` wiring and
 * bypass the single-`SyncController` invariant of `§9.1`.
 *
 * The rule is a pure function over source text so it has a *failing* fixture, which is what
 * `AGENTS.md` requires of every architecture rule: a rule whose only exercise is the real repository
 * cannot be shown to fire at all. The fixture mutates a real host file in memory and asserts the
 * violation, so the rule is proved to fire today and keeps firing when the real files change.
 *
 * The scanned surface is the whole iOS platform boundary — Kotlin `iosMain` and Swift `iosApp` — and
 * the banned set is the platform-owned quartet, `OwnerChanged` included. `AppForeground` and
 * `PullToRefresh` are the two triggers a UI layer *is* allowed to request, so banning them would
 * reject the documented surface.
 */
class SwiftTriggerSurfaceContractTest {
    private val repositoryRoot = File(checkNotNull(System.getProperty("carapp.repoRoot")))

    @Test
    fun theRepositoryDoesNotFirePlatformOwnedTriggersFromTheIosUiSurface() {
        assertEquals(emptyList(), SwiftTriggerSurfaceRule.violations(presentSources()))
    }

    @Test
    fun anOwnerChangedRequestFromSwiftIsRejected() {
        // `D-188`: the graph is the only caller. A Swift call site would run a recovery cycle the
        // graph never asked for and would bypass the gate that keeps an unrecovered empty list
        // unresolved, which is the exact state `SPECIFICATION.md` F-1 acts on.
        assertRejected("OwnerChanged")
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

    @Test
    fun aNestedBlockCommentDoesNotMakeCommentedTextLookLikeACallSite() {
        // Swift and Kotlin both allow a block comment inside a block comment. A masker that stops at
        // the first `*/` treats the tail of the outer comment as code and reports a violation that
        // does not exist.
        val source =
            """
            /* outer /* inner */
            syncStateHolder.requestSync(reason: .periodic)
            */
            """.trimIndent()

        assertEquals(
            emptyList(),
            SwiftTriggerSurfaceRule.violations(mapOf(FIXTURE_PATH to source)),
            "a fully commented-out call site is not a violation",
        )
    }

    @Test
    fun aSlashSlashInsideAStringLiteralDoesNotHideARealCallSite() {
        // A masker that treats `//` inside a string as a line comment blanks the rest of the line and
        // stops seeing the violation that follows it.
        val source =
            """let endpoint = "https://example.invalid"; syncStateHolder.requestSync(reason: .periodic)"""

        assertEquals(
            listOf("$FIXTURE_PATH:1 requests Periodic"),
            SwiftTriggerSurfaceRule.violations(mapOf(FIXTURE_PATH to source)),
            "§20.10 bans Periodic even on a line that also contains a URL string",
        )
    }

    @Test
    fun aPlatformOwnedTriggerBehindAnAliasedReceiverIsRejected() {
        // A one-line alias is all it takes to hide the receiver name from the call site. The ban is
        // about the trigger and the route, so the receiver test is an allowlist rather than a search
        // for the word `StateHolder`.
        // The alias is separated from the call by more than the receiver window, which is what makes
        // this fixture reproduce the defect: a denylist that searches backwards for `StateHolder`
        // stops seeing the name at all, while the call is still on a holder.
        val source =
            """
            let holder = model.syncStateHolder
            let unrelatedValue = someObject.somePropertyNameThatTakesUpSpaceInTheWindow
            let anotherValue = someObject.anotherPropertyNameThatAlsoTakesUpSpaceHere
            holder.requestSync(reason: .periodic)
            """.trimIndent()

        assertEquals(
            listOf("$FIXTURE_PATH:4 requests Periodic"),
            SwiftTriggerSurfaceRule.violations(mapOf(FIXTURE_PATH to source)),
            "§20.10 bans Periodic however the holder is named at the call site",
        )
    }

    @Test
    fun anAliasWhoseNameContainsSyncControllerIsRejected() {
        // The substring allowlist accepted this: the identifier `syncControllerAlias` contains
        // `syncController`, so the receiver looked permitted while the call was on a holder. The
        // allowlist MUST match the member-access shape, not a token inside a longer name.
        val source =
            """
            let syncControllerAlias = model.syncStateHolder
            syncControllerAlias.requestSync(reason: .ownerChanged)
            """.trimIndent()

        assertEquals(
            listOf("$FIXTURE_PATH:2 requests OwnerChanged"),
            SwiftTriggerSurfaceRule.violations(mapOf(FIXTURE_PATH to source)),
        )
    }

    @Test
    fun anUnrelatedSyncControllerTokenDoesNotPermitAHolderCall() {
        // The real route earlier in the window must not license a later call on a different receiver.
        val source =
            """
            let controller = graph.syncController()
            holder.requestSync(reason: .periodic)
            """.trimIndent()

        assertEquals(
            listOf("$FIXTURE_PATH:2 requests Periodic"),
            SwiftTriggerSurfaceRule.violations(mapOf(FIXTURE_PATH to source)),
        )
    }

    @Test
    fun thePlatformControllerRouteIsAccepted() {
        // `§9.1` makes `AppGraph.syncController()` the single in-process controller, so platform
        // wiring requesting a cycle there is obeying the invariant. Rejecting it would reject the
        // correct implementation.
        val source = "graph.syncController().requestSync(SyncTrigger.Periodic)"

        assertEquals(
            emptyList(),
            SwiftTriggerSurfaceRule.violations(mapOf(FIXTURE_PATH to source)),
            "§9.1 requires the platform route through the single SyncController",
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
