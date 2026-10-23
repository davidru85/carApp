package com.ruizurraca.carapp.buildlogic.contract

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/**
 * Assertion 45 (`D-230`, ADR-0231): the `carAppUITests` shards are complete by construction only
 * while the manifest names real tests, assigns each identifier once, and every shard it defines,
 * plus the implicit `remainder`, runs in exactly one `ios-ui-tests-<shard>` job.
 */
class IosUiTestShardContractTest {
    private val repositoryRoot = File(checkNotNull(System.getProperty("carapp.repoRoot")))
    private val workflow = repositoryRoot.resolve(".github/workflows/ci.yml").readText()
    private val manifest = repositoryRoot.resolve("scripts/ios-ui-test-shards.txt").readText()
    private val sources = repositoryRoot.resolve("iosApp/UITests")
        .listFiles { file -> file.extension == "swift" }
        .orEmpty()
        .associate { it.name to it.readText() }

    private fun validate(
        workflow: String = this.workflow,
        manifest: String = this.manifest,
        sources: Map<String, String> = this.sources,
    ): AssertionResult = IosUiTestShardContract(workflow, manifest, sources).validate()

    @Test
    fun repositoryShardsPass() {
        val result = validate()
        assertEquals(45, result.id)
        assertEquals(AssertionResult.Status.PASS, result.status, result.detail)
    }

    /** A renamed test leaves a stale manifest entry, which would silently run nothing. */
    @Test
    fun anEntryNamingAMissingMethodFails() {
        val file = "AccessibilityAuditUITests.swift"
        val renamed = sources.getValue(file).replace(
            "func testPendingSyncPassesTheAccessibilityAudit(",
            "func testPendingSyncPromptPassesTheAccessibilityAudit(",
        )
        assertNotEquals(sources.getValue(file), renamed, "The fixture mutation must rename the test")
        assertEquals(AssertionResult.Status.FAIL, validate(sources = sources + (file to renamed)).status)
    }

    @Test
    fun anEntryNamingAMissingClassFails() {
        assertEquals(AssertionResult.Status.FAIL, validate(manifest = manifest + "audit-2 MissingUITests\n").status)
    }

    @Test
    fun anIdentifierAssignedTwiceFails() {
        val duplicated = manifest + "audit-2 AccessibilityAuditUITests/testCollisionPassesTheAccessibilityAudit\n"
        assertEquals(AssertionResult.Status.FAIL, validate(manifest = duplicated).status)
    }

    @Test
    fun aMalformedManifestLineFails() {
        assertEquals(AssertionResult.Status.FAIL, validate(manifest = manifest + "audit-2\n").status)
    }

    /** The remainder is defined by what the manifest does not list, so it cannot be listed itself. */
    @Test
    fun listingTheImplicitRemainderShardFails() {
        val listed = manifest + "remainder SettingsFlowUITests\n"
        assertEquals(AssertionResult.Status.FAIL, validate(manifest = listed).status)
    }

    /** Its tests are skipped by the remainder and claimed by no job, so they would never run. */
    @Test
    fun aManifestShardWithoutAJobFails() {
        val orphan = manifest + "ax3-3 LargeContentSizeUITests/testWelcomeActionsAreReachableAtAx3\n"
        assertEquals(AssertionResult.Status.FAIL, validate(manifest = orphan).status)
    }

    @Test
    fun aJobRunningAShardTheManifestDoesNotDefineFails() {
        val withoutAx32 = manifest.replace("ax3-2 LargeContentSizeUITests\n", "")
        assertNotEquals(manifest, withoutAx32, "The fixture mutation must remove the ax3-2 shard")
        assertEquals(AssertionResult.Status.FAIL, validate(manifest = withoutAx32).status)
    }

    @Test
    fun aMissingRemainderJobFails() {
        val mutated = workflow.replace("scripts/ios-ui-test-shard.sh remainder", "true")
        assertNotEquals(workflow, mutated, "The fixture mutation must stop running the remainder shard")
        assertEquals(AssertionResult.Status.FAIL, validate(workflow = mutated).status)
    }

    @Test
    fun aShardRunInTwoJobsFails() {
        val copy = "\n  ios-ui-tests-audit-1-copy:\n    name: ios-ui-tests-audit-1-copy\n    runs-on: macos-latest\n" +
            "    timeout-minutes: 40\n    steps:\n      - name: Run the iOS UI test shard\n" +
            "        run: scripts/ios-ui-test-shard.sh audit-1\n"
        assertEquals(AssertionResult.Status.FAIL, validate(workflow = workflow + copy).status)
    }

    /** The check name is what branch protection requires, so it MUST say which shard it reports. */
    @Test
    fun aShardJobNamedAfterAnotherShardFails() {
        val mutated = workflow.replace("    name: ios-ui-tests-audit-1\n", "    name: ios-ui-tests-a1\n")
        assertNotEquals(workflow, mutated, "The fixture mutation must rename the audit-1 check")
        assertEquals(AssertionResult.Status.FAIL, validate(workflow = mutated).status)
    }

    /** A shard whose check is not protected would stop gating merges while still running (ADR-0231). */
    @Test
    fun aShardWhoseCheckIsNotProtectedFails() {
        val manifestWithAudit3 = manifest + "audit-3 AccessibilityAuditUITests/testWelcomePassesTheAccessibilityAudit\n"
        val audit3Job = "\n  ios-ui-tests-audit-3:\n    name: ios-ui-tests-audit-3\n    runs-on: macos-latest\n" +
            "    timeout-minutes: 40\n    steps:\n      - name: Run the iOS UI test shard\n" +
            "        run: scripts/ios-ui-test-shard.sh audit-3\n"
        val result = validate(workflow = workflow + audit3Job, manifest = manifestWithAudit3)

        assertEquals(AssertionResult.Status.FAIL, result.status, result.detail)
        assertTrue("ios-ui-tests-audit-3 is not in PROTECTED_CHECK_NAMES" in result.detail, result.detail)
    }

    /** A job that selects UI tests by hand bypasses the manifest and its completeness guarantee. */
    @Test
    fun selectingUiTestsOutsideTheManifestFails() {
        val mutated = workflow.replace("-only-testing:carAppTests", "-only-testing:carAppTests -only-testing:carAppUITests")
        assertNotEquals(workflow, mutated, "The fixture mutation must add a direct UI test selection")
        assertEquals(AssertionResult.Status.FAIL, validate(workflow = mutated).status)
    }
}
