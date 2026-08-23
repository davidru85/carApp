package com.ruizurraca.carapp.buildlogic.architecture

/**
 * Every architecture rule of `docs/BACKLOG.md` `E0-04`, as pure functions over [ModuleUnderCheck].
 *
 * The rules are pure on purpose. A rule that can only be exercised by creating the offending
 * Gradle module could never have a failing fixture for `:core:sync`, `:core:auth`,
 * `:integration:*` or `:feature:*`, because the Phase 0 module set forbids creating them. Feeding
 * the checker a fabricated module instead proves the rule fires today, without breaking that
 * constraint.
 */
object ArchitectureChecker {

    /** Planned modules whose owning stories have not started yet. */
    val NOT_YET_INTRODUCED_MODULES = setOf(":core:auth", ":core:sync")

    private val PLATFORM_IMPORT_PREFIXES = listOf(
        "android.", "androidx.", "platform.Foundation", "platform.UIKit", "platform.darwin",
        "kotlinx.cinterop",
    )

    private val CAPABILITY_COORDINATES = mapOf(
        Capability.FIREBASE to listOf("com.google.firebase"),
        Capability.GITLIVE to listOf("dev.gitlive"),
        Capability.DATABASE to listOf(
            "app.cash.sqldelight",
            "com.eygraber:sqldelight-androidx-driver",
            "androidx.sqlite",
            "androidx.room3",
            "androidx.room",
        ),
        Capability.KOIN to listOf("io.insert-koin"),
        Capability.KTOR to listOf("io.ktor"),
        Capability.SERIALIZATION to listOf("org.jetbrains.kotlinx:kotlinx-serialization"),
        Capability.DATETIME to listOf("org.jetbrains.kotlinx:kotlinx-datetime"),
        Capability.COROUTINES to listOf("org.jetbrains.kotlinx:kotlinx-coroutines"),
    )

    private val IMAGE_LOADING_COORDINATES = listOf("io.coil-kt", "com.github.bumptech.glide", "com.squareup.picasso")

    private val SYNCHRONIZED_ENTITY_MUTATION_FUNCTIONS =
        setOf(
            "insertVehicleRow",
            "insertFuelEntryRow",
            "updateFuelEntryRow",
            "tombstoneFuelEntryRow",
        )

    fun check(modules: List<ModuleUnderCheck>, rules: List<ModuleRule>): List<Violation> =
        modules.flatMap { module -> check(module, rules) }

    fun check(module: ModuleUnderCheck, rules: List<ModuleRule>): List<Violation> {
        val violations = mutableListOf<Violation>()
        val rule = ruleFor(module.path, rules)

        violations += checkModuleIsIntroducedByItsOwningStory(module)
        violations += checkSkieIsOnlyInShared(module)
        violations += checkNoFeatureToFeatureDependency(module)
        violations += checkCrashHasNoExpectActual(module)
        violations += checkDatabaseTypesStayInTheirModule(module)
        violations += checkNoFloatingPointArithmetic(module)
        violations += checkLoggerFieldValues(module)
        violations += checkDatabaseLogging(module)
        violations += checkOutboxLastErrorReads(module)
        violations += checkReadModelWrites(module)
        violations += checkDatabaseMutationFacade(module)
        violations += checkConsumptionTypesStayInCoreModel(module)
        violations += checkIntegrationsDoNotBuildTheGraph(module)

        if (rule != null) {
            violations += checkForbiddenModules(module, rule)
            violations += checkAllowedModules(module, rule)
            violations += checkForbiddenCapabilities(module, rule)
        }
        return violations
    }

    /**
     * The row whose area matches this module. `:integration:*` and `:feature:*` rows are patterns;
     * feature rows keyed by layer (`feature domain`) are resolved by the source-package rules and
     * are not matched here.
     */
    fun ruleFor(modulePath: String, rules: List<ModuleRule>): ModuleRule? =
        rules.firstOrNull { it.area == modulePath }
            ?: rules.firstOrNull { it.area.endsWith("*") && matchesPattern(modulePath, it.area) }

    /**
     * Glob matching where `*` stands for one or more path segments. `Regex.escape` cannot be used
     * here: it wraps the whole pattern in `\Q…\E`, so the `*` would never be substituted and
     * `:core:*` would silently match nothing — a rule that quietly passes everything.
     */
    private fun matchesPattern(path: String, pattern: String): Boolean {
        val regex = pattern.split("*").joinToString("[A-Za-z0-9:_-]+") { Regex.escape(it) }
        return Regex("^$regex$").matches(path)
    }

    private fun matchesAny(path: String, patterns: Set<String>): Boolean =
        patterns.any { it == path || (it.contains('*') && matchesPattern(path, it)) }

    /**
     * A KMP module declares dependencies on itself across its own source sets and targets. Those
     * are an artifact of the Gradle model, not an architectural edge.
     */
    private fun declaredEdges(module: ModuleUnderCheck): Set<String> =
        module.projectDependencies.filterNot { it == module.path }.toSet()

    private fun checkForbiddenModules(module: ModuleUnderCheck, rule: ModuleRule) =
        declaredEdges(module).filter { matchesAny(it, rule.forbiddenModules) }.map {
            Violation(
                module.path,
                "forbidden-module-dependency",
                "depends on $it, which docs/TECHNICAL_PLAN.md §4 forbids for ${rule.area}.",
            )
        }

    private fun checkAllowedModules(module: ModuleUnderCheck, rule: ModuleRule): List<Violation> {
        if (rule.allowedModules.isEmpty()) return emptyList()
        return declaredEdges(module)
            .filterNot { matchesAny(it, rule.allowedModules) }
            .filterNot { matchesAny(it, rule.forbiddenModules) }
            .map {
                Violation(
                    module.path,
                    "undeclared-module-dependency",
                    "depends on $it, which is not in the allowed list for ${rule.area} in " +
                        "docs/TECHNICAL_PLAN.md §4. Add it to the table or remove the dependency.",
                )
            }
    }

    private fun checkForbiddenCapabilities(module: ModuleUnderCheck, rule: ModuleRule): List<Violation> {
        val violations = mutableListOf<Violation>()
        rule.forbiddenCapabilities.forEach { capability ->
            if (capability == Capability.PLATFORM_API) {
                module.imports.filter { import -> PLATFORM_IMPORT_PREFIXES.any { import.startsWith(it) } }
                    .forEach {
                        violations += Violation(
                            module.path,
                            "forbidden-platform-api",
                            "imports $it. docs/TECHNICAL_PLAN.md §4 forbids platform APIs for ${rule.area}.",
                        )
                    }
            } else {
                val coordinates = CAPABILITY_COORDINATES[capability].orEmpty()
                module.externalDependencies
                    .filter { dependency -> coordinates.any { dependency.startsWith(it) } }
                    .forEach {
                        violations += Violation(
                            module.path,
                            "forbidden-library-dependency",
                            "depends on $it ($capability), which docs/TECHNICAL_PLAN.md §4 forbids for ${rule.area}.",
                        )
                    }
            }
        }
        return violations
    }

    /** A planned module becomes legal only when its owning backlog story starts. */
    private fun checkModuleIsIntroducedByItsOwningStory(module: ModuleUnderCheck): List<Violation> =
        if (module.path in NOT_YET_INTRODUCED_MODULES) {
            listOf(
                Violation(
                    module.path,
                    "module-before-owning-story",
                    "${module.path} MUST NOT be created before its owning story (docs/BACKLOG.md).",
                ),
            )
        } else {
            emptyList()
        }

    /** `D-2`: SKIE is applied only to `:shared`. */
    private fun checkSkieIsOnlyInShared(module: ModuleUnderCheck): List<Violation> =
        if ("co.touchlab.skie" in module.appliedPluginIds && module.path != ":shared") {
            listOf(
                Violation(
                    module.path,
                    "skie-outside-shared",
                    "applies SKIE. D-2 restricts SKIE to :shared.",
                ),
            )
        } else {
            emptyList()
        }

    private fun checkNoFeatureToFeatureDependency(module: ModuleUnderCheck): List<Violation> {
        if (!module.path.startsWith(":feature:")) return emptyList()
        return module.projectDependencies
            .filter { it.startsWith(":feature:") && it != module.path }
            .map {
                Violation(
                    module.path,
                    "feature-to-feature-dependency",
                    "depends on $it. Features never depend on each other (docs/TECHNICAL_PLAN.md §4).",
                )
            }
    }

    /** `docs/CONTRACTS.md §11.6`: `:core:crash` MUST NOT contain `expect`/`actual` declarations. */
    private fun checkCrashHasNoExpectActual(module: ModuleUnderCheck): List<Violation> {
        if (module.path != ":core:crash") return emptyList()
        return module.sourceLines
            .filter { Regex("""\b(expect|actual)\s+(class|fun|val|var|object|interface)\b""").containsMatchIn(it.text) }
            .map {
                Violation(
                    module.path,
                    "expect-actual-in-core-crash",
                    "${it.file}:${it.number} declares expect/actual. docs/CONTRACTS.md §11.6 forbids it here.",
                )
            }
    }

    /**
     * `docs/CONTRACTS.md §20.3.2`: `AppDatabase` and `DatabaseFactory` are owned by
     * `:core:database` and may appear only there, in `:core:testing` fakes and in the
     * `AppGraphDependencies` field of `:shared`.
     */
    private fun checkDatabaseTypesStayInTheirModule(module: ModuleUnderCheck): List<Violation> {
        val allowed = setOf(":core:database", ":core:testing", ":shared")
        if (module.path in allowed) return emptyList()
        return module.sourceLines
            .filter { Regex("""\b(AppDatabase|DatabaseFactory)\b""").containsMatchIn(it.text) }
            .map {
                Violation(
                    module.path,
                    "database-type-outside-core-database",
                    "${it.file}:${it.number} references a :core:database type. docs/CONTRACTS.md §20.3.2 " +
                        "allows it only in :core:database, :core:testing fakes and the :shared AppGraphDependencies field.",
                )
            }
    }

    /** `docs/CONTRACTS.md §2`: `Float` and `Double` are forbidden in arithmetic paths. */
    private fun checkNoFloatingPointArithmetic(module: ModuleUnderCheck): List<Violation> {
        val guarded = module.path.startsWith(":core:") || module.path.startsWith(":feature:") ||
            module.path == ":shared"
        if (!guarded) return emptyList()
        return module.sourceLines
            .filter { Regex("""\b(Float|Double|toFloat|toDouble)\b""").containsMatchIn(it.text) }
            .map {
                Violation(
                    module.path,
                    "floating-point-arithmetic",
                    "${it.file}:${it.number} mentions Float or Double. docs/CONTRACTS.md §2 forbids them " +
                        "for money, volume, price and consumption in every layer.",
                )
            }
    }

    /**
     * `docs/CONTRACTS.md §17`: `Logger.log` field values are limited to stable codes, enum names
     * and `cycleId`. A string literal that is none of those is raw data reaching a log line.
     */
    private fun checkLoggerFieldValues(module: ModuleUnderCheck): List<Violation> {
        val stableValue = Regex("""^([A-Z][A-Z0-9_]*\.[A-Z0-9_]+|[A-Za-z][A-Za-z0-9]*|cycleId)$""")
        return module.sourceLines
            .filter { it.text.contains("logger.log(") || it.text.contains("Logger.log(") }
            .flatMap { line ->
                Regex(""""([^"]*)"\s+to\s+"([^"]*)"""").findAll(line.text).mapNotNull { match ->
                    val value = match.groupValues[2]
                    if (stableValue.matches(value)) {
                        null
                    } else {
                        Violation(
                            module.path,
                            "logger-free-text-field",
                            "${line.file}:${line.number} logs the literal \"$value\". docs/CONTRACTS.md §17 " +
                                "limits field values to stable codes, enum names and cycleId.",
                        )
                    }
                }
            }
    }

    /** `E0-04`: `Logger.log` calls from `:core:database` are rejected except local-database failures. */
    private fun checkDatabaseLogging(module: ModuleUnderCheck): List<Violation> {
        if (module.path != ":core:database") return emptyList()
        return module.sourceLines
            .filter { it.text.contains("logger.log(") }
            .filterNot { it.text.contains("PERSISTENCE.") }
            .map {
                Violation(
                    module.path,
                    "database-logging",
                    "${it.file}:${it.number} logs from :core:database. Only local-database failures " +
                        "(PERSISTENCE.* codes) may be logged here.",
                )
            }
    }

    /** `E0-04`: sync logic may read only `lastErrorCode`; `outbox.lastError` is for logging and debug UI. */
    private fun checkOutboxLastErrorReads(module: ModuleUnderCheck): List<Violation> {
        if (module.path != ":core:sync") return emptyList()
        return module.sourceLines
            .filter { Regex("""\blastError\b""").containsMatchIn(it.text) }
            .map {
                Violation(
                    module.path,
                    "outbox-last-error-read",
                    "${it.file}:${it.number} reads outbox.lastError. Sync logic may read only lastErrorCode.",
                )
            }
    }

    /** `E0-04`: `currentOdometerKm` and `odometerInconsistent` are written only by `:core:database`. */
    private fun checkReadModelWrites(module: ModuleUnderCheck): List<Violation> {
        if (module.path == ":core:database") return emptyList()
        return module.sourceLines
            .filter { Regex("""\b(currentOdometerKm|odometerInconsistent)\s*=""").containsMatchIn(it.text) }
            .map {
                Violation(
                    module.path,
                    "read-model-write-outside-database",
                    "${it.file}:${it.number} assigns a database-owned read-model field. " +
                        "docs/CONTRACTS.md §3.1 makes :core:database its only writer.",
                )
            }
    }

    /** `D-38`: generated synchronized-entity mutations are implementation details of `:core:database`. */
    private fun checkDatabaseMutationFacade(module: ModuleUnderCheck): List<Violation> {
        if (module.path == ":core:database") return emptyList()
        val generatedMutation =
            SYNCHRONIZED_ENTITY_MUTATION_FUNCTIONS
                .joinToString(prefix = "\\b(", separator = "|", postfix = ")\\s*\\(") { Regex.escape(it) }
                .toRegex()
        return module.sourceLines
            .filter { generatedMutation.containsMatchIn(it.text) }
            .map {
                Violation(
                    module.path,
                    "database-mutation-facade",
                    "${it.file}:${it.number} calls a generated synchronized-entity mutation. " +
                        "D-38 requires callers outside :core:database to use DatabaseMutations.",
                )
            }
    }

    /**
     * `E0-04`: moving `ConsumptionInvalidReason` or `SegmentResult` out of `:core:model` into
     * `:core:common` fails the build. They are domain vocabulary (`docs/CONTRACTS.md §20.6`), and
     * `:core:common` is plumbing that speaks the vocabulary rather than owning it.
     */
    private fun checkConsumptionTypesStayInCoreModel(module: ModuleUnderCheck): List<Violation> {
        if (module.path == ":core:model") return emptyList()
        val declaration = Regex("""\b(enum class|sealed interface|sealed class|data class|class|interface)\s+(ConsumptionInvalidReason|SegmentResult)\b""")
        return module.sourceLines
            .mapNotNull { line -> declaration.find(line.text)?.let { line to it.groupValues[2] } }
            .map { (line, type) ->
                Violation(
                    module.path,
                    "consumption-type-outside-core-model",
                    "${line.file}:${line.number} declares $type. docs/CONTRACTS.md §20.6 puts it in :core:model; " +
                        "moving it into :core:common inverts the vocabulary-to-plumbing direction of docs/TECHNICAL_PLAN.md §4.",
                )
            }
    }

    /**
     * `docs/CONTRACTS.md §11.6`: `:integration:*` modules MAY declare Koin `Module` bindings but
     * MUST NOT reference `createAppGraph`; only `:wiring:firebase` aggregates them into the graph.
     */
    private fun checkIntegrationsDoNotBuildTheGraph(module: ModuleUnderCheck): List<Violation> {
        if (!module.path.startsWith(":integration:")) return emptyList()
        return module.sourceLines
            .filter { it.text.contains("createAppGraph") }
            .map {
                Violation(
                    module.path,
                    "integration-builds-app-graph",
                    "${it.file}:${it.number} references createAppGraph. Only :wiring:firebase may aggregate " +
                        "bindings into the graph (docs/CONTRACTS.md §11.6).",
                )
            }
    }

    /** `E0-04`: image-loading dependencies require a story reference and the Coil decision path. */
    fun checkImageLoading(versionCatalog: String): List<Violation> =
        versionCatalog.lines().withIndex()
            .filter { (_, line) -> IMAGE_LOADING_COORDINATES.any { line.contains(it) } }
            .filterNot { (_, line) -> line.contains("E1-") || line.contains("E2-") || line.contains("E3-") || line.contains("E4-") }
            .map { (index, line) ->
                Violation(
                    "gradle/libs.versions.toml",
                    "image-loading-dependency",
                    "line ${index + 1} adds an image-loading dependency without a story reference: ${line.trim()}. " +
                        "docs/CONTRACTS.md §15.5 requires the Coil decision path (D-12).",
                )
            }
}
