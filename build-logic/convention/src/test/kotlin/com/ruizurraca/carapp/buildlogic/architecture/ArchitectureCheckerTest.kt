package com.ruizurraca.carapp.buildlogic.architecture

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * A failing fixture for every executable module and source architecture rule.
 *
 * The fixtures are fabricated modules rather than real ones. That is the whole point: most of the
 * rules protect `:core:sync`, `:core:auth`, `:core:database`, `:integration:*` and `:feature:*`,
 * and the Phase 0 module set forbids creating any of them — `E0-04` itself requires a rule that
 * fails the build if they appear. A fixture that had to create the offending module could
 * therefore never exist for those rules. Feeding the checker plain data proves each rule fires
 * today, and keeps proving it when the real modules arrive.
 *
 * Each test asserts both directions: the offending shape is rejected with the expected rule name,
 * and the legal shape next to it is accepted.
 */
class ArchitectureCheckerTest {

    private val rules: List<ModuleRule> = DependencyRuleTableParser.parse(technicalPlan())

    private fun technicalPlan(): String {
        var candidate: File? = File(".").absoluteFile
        while (candidate != null) {
            val plan = File(candidate, "docs/TECHNICAL_PLAN.md")
            if (plan.isFile) return plan.readText()
            candidate = candidate.parentFile
        }
        error("Could not locate docs/TECHNICAL_PLAN.md from ${File(".").absolutePath}")
    }

    private fun module(
        path: String,
        projectDependencies: Set<String> = emptySet(),
        externalDependencies: Set<String> = emptySet(),
        imports: Set<String> = emptySet(),
        source: String = "",
        plugins: Set<String> = emptySet(),
    ) = ModuleUnderCheck(
        path = path,
        projectDependencies = projectDependencies,
        externalDependencies = externalDependencies,
        imports = imports,
        sourceLines = source.lines().withIndex()
            .filter { it.value.isNotBlank() }
            .map { SourceLine("Fixture.kt", it.index + 1, it.value) },
        appliedPluginIds = plugins,
    )

    private fun violations(module: ModuleUnderCheck) = ArchitectureChecker.check(module, rules)

    private fun assertRejected(module: ModuleUnderCheck, rule: String) {
        val found = violations(module)
        assertTrue(
            found.any { it.rule == rule },
            "Expected rule '$rule' to fire for ${module.path}, got: ${found.map { it.rule }}",
        )
    }

    /**
     * Asserts that one specific rule does not fire. Used where the fixture module legitimately
     * trips a different rule — `:core:sync` always trips `phase-0-module-set` today, which is the
     * point of that rule, so "no violations at all" would be the wrong assertion.
     */
    private fun assertRuleDoesNotFire(module: ModuleUnderCheck, rule: String) {
        val found = violations(module)
        assertTrue(
            found.none { it.rule == rule },
            "Expected rule '$rule' not to fire for ${module.path}, got: $found",
        )
    }

    private fun assertAccepted(module: ModuleUnderCheck) {
        val found = violations(module)
        assertTrue(found.isEmpty(), "Expected no violation for ${module.path}, got: $found")
    }

    // --- The dependency table is the source of the rules ------------------------------------

    @Test
    fun theRuleTableIsParsedFromTheTechnicalPlanAndCoversEveryArea() {
        val areas = rules.map { it.area }
        listOf(
            ":core:model", ":core:common", ":core:sync", ":core:database", ":core:auth",
            ":core:analytics", ":core:testing", ":core:crash", ":integration:*", ":shared",
            ":shared:testing", ":wiring:firebase",
        ).forEach { area ->
            assertTrue(area in areas, "docs/TECHNICAL_PLAN.md §4 has no row for $area; parsed: $areas")
        }
    }

    @Test
    fun aGlobRowMatchesEveryModuleUnderIt() {
        val rule = ArchitectureChecker.ruleFor(":integration:firebase-auth", rules)
        assertEquals(":integration:*", rule?.area)
    }

    // --- Module-level rules ------------------------------------------------------------------

    @Test
    fun coreModelMayNotDependOnCoreCommon() {
        assertRejected(
            module(":core:model", projectDependencies = setOf(":core:common")),
            "forbidden-module-dependency",
        )
        assertAccepted(module(":core:common", projectDependencies = setOf(":core:model")))
    }

    @Test
    fun coreSyncMayNotDependOnAuthIntegrationsOrFeatures() {
        listOf(":core:auth", ":integration:firebase-firestore").forEach {
            assertRejected(
                module(":core:sync", projectDependencies = setOf(it)),
                "forbidden-module-dependency",
            )
        }
    }

    @Test
    fun sharedMayNotDependOnAnIntegration() {
        assertRejected(
            module(":shared", projectDependencies = setOf(":integration:firebase-auth")),
            "forbidden-module-dependency",
        )
        assertAccepted(module(":shared", projectDependencies = setOf(":core:model", ":feature:fuel")))
    }

    @Test
    fun coreModulesMayNotDependOnShared() {
        listOf(
            ":core:model",
            ":core:common",
            ":core:database",
            ":core:auth",
            ":core:sync",
            ":core:analytics",
            ":core:crash",
            ":core:testing",
        ).forEach { path ->
            assertRejected(
                module(path, projectDependencies = setOf(":shared")),
                "undeclared-module-dependency",
            )
        }
    }

    @Test
    fun featureDataMayNotDependOnCoreAuthOrAnIntegration() {
        assertRejected(
            module(":core:database", projectDependencies = setOf(":integration:firebase-firestore")),
            "forbidden-module-dependency",
        )
    }

    @Test
    fun aFeatureMayNotDependOnAnotherFeature() {
        assertRejected(
            module(":feature:fuel", projectDependencies = setOf(":feature:vehicle")),
            "feature-to-feature-dependency",
        )
    }

    @Test
    fun anUndeclaredEdgeIsRejectedEvenWhenItIsNotExplicitlyForbidden() {
        assertRejected(
            module(":core:crash", projectDependencies = setOf(":core:model")),
            "undeclared-module-dependency",
        )
    }

    // --- Capability rules --------------------------------------------------------------------

    @Test
    fun providerFreeModulesMayNotDependOnFirebaseDatabaseKoinOrKtor() {
        mapOf(
            ":core:auth" to "com.google.firebase:firebase-auth",
            ":core:analytics" to "io.insert-koin:koin-core",
            ":core:crash" to "dev.gitlive:firebase-crashlytics",
            ":core:common" to "app.cash.sqldelight:runtime",
            ":core:model" to "io.ktor:ktor-client-core",
        ).forEach { (path, coordinate) ->
            assertRejected(
                module(path, externalDependencies = setOf(coordinate)),
                "forbidden-library-dependency",
            )
        }
    }

    @Test
    fun aPlatformApiImportIsRejectedInCoreCrash() {
        assertRejected(
            module(":core:crash", imports = setOf("android.content.Context")),
            "forbidden-platform-api",
        )
        assertAccepted(module(":core:crash", imports = setOf("kotlin.time.Instant")))
    }

    @Test
    fun aPlatformApiImportIsRejectedInCoreTesting() {
        assertRejected(
            module(":core:testing", imports = setOf("platform.UIKit.UIDevice")),
            "forbidden-platform-api",
        )
    }

    // --- Source-level rules ------------------------------------------------------------------

    @Test
    fun storiesMayNotCreateUnownedProviderModulesEarly() {
        ArchitectureChecker.NOT_YET_INTRODUCED_MODULES.forEach {
            assertRejected(module(it), "module-before-owning-story")
        }
        assertAccepted(module(":core:model"))
        assertAccepted(module(":core:database"))
        assertAccepted(module(":core:auth"))
        assertAccepted(module(":core:sync"))
        assertAccepted(module(":feature:vehicle"))
    }

    @Test
    fun skieMayNotBeAppliedOutsideShared() {
        assertRejected(
            module(":core:model", plugins = setOf("co.touchlab.skie")),
            "skie-outside-shared",
        )
        assertAccepted(module(":shared", plugins = setOf("co.touchlab.skie")))
    }

    @Test
    fun coreCrashMayNotDeclareExpectOrActual() {
        assertRejected(
            module(":core:crash", source = "expect class PlatformCrashReporter"),
            "expect-actual-in-core-crash",
        )
        assertAccepted(module(":core:crash", source = "interface CrashReporter"))
    }

    @Test
    fun databaseTypesMayNotLeakOutOfTheirModule() {
        listOf(":core:common", ":core:sync", ":feature:fuel").forEach {
            assertRejected(
                module(it, source = "fun open(): AppDatabase = error(\"x\")"),
                "database-type-outside-core-database",
            )
        }
        assertAccepted(module(":core:testing", source = "class FakeDatabaseFactory : DatabaseFactory"))
    }

    @Test
    fun floatAndDoubleAreRejectedInCoreFeatureAndShared() {
        listOf(":core:model", ":feature:fuel", ":shared").forEach {
            assertRejected(module(it, source = "val litres: Double = 1.0"), "floating-point-arithmetic")
        }
        assertAccepted(module(":core:model", source = "val litresScaled: Long = 1_000L"))
    }

    @Test
    fun loggerFieldsMayNotCarryFreeText() {
        assertRejected(
            module(
                ":core:sync",
                source = """logger.log(LogLevel.WARN, "sync", "failed", mapOf("note" to "user typed this"), null)""",
            ),
            "logger-free-text-field",
        )
        assertAccepted(
            module(
                ":shared",
                source = """logger.log(LogLevel.WARN, "sync", "failed", mapOf("code" to "SYNC.AUTH_EXPIRED"), null)""",
            ),
        )
    }

    @Test
    fun coreDatabaseMayOnlyLogLocalDatabaseFailures() {
        assertRejected(
            module(":core:database", source = """logger.log(LogLevel.INFO, "db", "opened", emptyMap(), null)"""),
            "database-logging",
        )
    }

    @Test
    fun syncLogicMayReadOnlyLastErrorCode() {
        assertRejected(
            module(":core:sync", source = "val message = row.lastError"),
            "outbox-last-error-read",
        )
        assertRuleDoesNotFire(module(":core:sync", source = "val code = row.lastErrorCode"), "outbox-last-error-read")
    }

    @Test
    fun readModelFieldsAreWrittenOnlyByCoreDatabase() {
        listOf(":feature:vehicle", ":core:sync").forEach {
            assertRejected(
                module(it, source = "vehicle.currentOdometerKm = 120_000L"),
                "read-model-write-outside-database",
            )
        }
    }

    @Test
    fun generatedEntityMutationsAreCalledOnlyByCoreDatabase() {
        listOf(
            "insertVehicleRow",
            "insertFuelEntryRow",
            "updateFuelEntryRow",
            "tombstoneFuelEntryRow",
        ).forEach { mutation ->
            assertRejected(
                module(":feature:vehicle", source = "databaseQueries.$mutation()"),
                "database-mutation-facade",
            )
        }

        assertAccepted(
            module(":feature:vehicle", source = "databaseMutations.tombstoneFuelEntry(command)"),
        )
        assertAccepted(
            module(":core:database", source = "databaseQueries.insertVehicleRow()"),
        )
    }

    @Test
    fun consumptionTypesMayNotMoveOutOfCoreModel() {
        assertRejected(
            module(":core:common", source = "enum class ConsumptionInvalidReason { NonPositiveDistance }"),
            "consumption-type-outside-core-model",
        )
        assertRejected(
            module(":core:common", source = "sealed interface SegmentResult"),
            "consumption-type-outside-core-model",
        )
        assertAccepted(module(":core:model", source = "enum class ConsumptionInvalidReason { NonPositiveDistance }"))
    }

    @Test
    fun integrationsMayDeclareKoinModulesButMayNotBuildTheGraph() {
        assertRejected(
            module(":integration:firebase-auth", source = "val graph = createAppGraph(dependencies)"),
            "integration-builds-app-graph",
        )
        assertAccepted(module(":integration:firebase-auth", source = "val authModule = module { single<AuthClient> { FirebaseAuthClient() } }"))
    }

    @Test
    fun anImageLoadingDependencyWithoutAStoryReferenceIsRejected() {
        val withoutStory = ArchitectureChecker.checkImageLoading("""coil = { module = "io.coil-kt:coil", version = "3.0.0" }""")
        assertEquals(1, withoutStory.size)
        assertEquals("image-loading-dependency", withoutStory.single().rule)

        val withStory = ArchitectureChecker.checkImageLoading(
            """coil = { module = "io.coil-kt:coil", version = "3.0.0" } # E1-08, D-12 decision path""",
        )
        assertTrue(withStory.isEmpty())
    }
}
