package com.ruizurraca.carapp.feature.vehicle.architecture

import com.lemonappdev.konsist.api.Konsist

internal object FeaturePackageRules {
    fun assertDomain(directory: String? = null) {
        assertLayer(Layer.DOMAIN, directory)
    }

    fun assertData(directory: String? = null) {
        assertLayer(Layer.DATA, directory)
    }

    fun assertPresentation(directory: String? = null) {
        assertLayer(Layer.PRESENTATION, directory)
    }

    private fun assertLayer(
        layer: Layer,
        directory: String?,
    ) {
        val violations = violationsFor(layer, directory)

        if (violations.isNotEmpty()) {
            throw AssertionError(
                buildString {
                    append("D-28 feature ")
                    append(layer.packageSegment)
                    append(" package boundary failed:\n")
                    violations.forEach { violation -> append("- $violation\n") }
                }.trimEnd(),
            )
        }
    }

    private fun violationsFor(
        layer: Layer,
        directory: String?,
    ): List<String> =
        scopeFor(directory)
            .files
            .filter { file -> file.packageName().isFeatureLayer(layer) }
            .flatMap { file ->
                val ownFeaturePrefix = file.packageName().ownFeaturePrefix()
                file.imports
                    .map { declaration -> declaration.name }
                    .filterNot { importName -> layer.allows(importName, ownFeaturePrefix) }
                    .map { importName -> "${file.name}: $importName" }
            }

    private fun scopeFor(directory: String?) =
        if (directory == null) {
            Konsist.scopeFromProduction()
        } else {
            Konsist.scopeFromDirectory(directory)
        }

    private fun com.lemonappdev.konsist.api.declaration.KoFileDeclaration.packageName(): String =
        PACKAGE_DECLARATION
            .find(text)
            ?.groupValues
            ?.get(1)
            .orEmpty()

    private fun String.isFeatureLayer(layer: Layer): Boolean {
        val segments = split('.')
        val featureIndex = segments.indexOf("feature")
        return featureIndex >= 0 && segments.getOrNull(featureIndex + 2) == layer.packageSegment
    }

    private fun String.ownFeaturePrefix(): String {
        val featureName = substringAfter(".feature.").substringBefore('.')
        return "$FEATURE_PACKAGE_PREFIX$featureName."
    }

    private enum class Layer(
        val packageSegment: String,
        private val allowedSharedPrefixes: Set<String>,
        private val allowedOwnLayers: Set<String>,
    ) {
        DOMAIN(
            packageSegment = "domain",
            allowedSharedPrefixes =
                setOf(
                    CORE_COMMON_PREFIX,
                    CORE_MODEL_PREFIX,
                    COROUTINES_PREFIX,
                ),
            allowedOwnLayers = setOf("domain"),
        ),
        DATA(
            packageSegment = "data",
            allowedSharedPrefixes =
                setOf(
                    CORE_COMMON_PREFIX,
                    CORE_DATABASE_PREFIX,
                    CORE_MODEL_PREFIX,
                    CORE_SYNC_PREFIX,
                    COROUTINES_PREFIX,
                    SERIALIZATION_PREFIX,
                ),
            allowedOwnLayers = setOf("domain", "data"),
        ),
        PRESENTATION(
            packageSegment = "presentation",
            allowedSharedPrefixes =
                setOf(
                    CORE_COMMON_PREFIX,
                    CORE_MODEL_PREFIX,
                    COROUTINES_PREFIX,
                ),
            allowedOwnLayers = setOf("domain", "presentation"),
        ),
        ;

        fun allows(
            importName: String,
            ownFeaturePrefix: String,
        ): Boolean {
            if (importName.startsWith(KOTLIN_PREFIX)) return true
            if (allowedSharedPrefixes.any(importName::startsWith)) return true
            if (!importName.startsWith(ownFeaturePrefix)) return false

            val importedLayer =
                importName
                    .removePrefix(ownFeaturePrefix)
                    .substringBefore('.')
            return importedLayer in allowedOwnLayers
        }
    }

    private const val FEATURE_PACKAGE_PREFIX = "com.ruizurraca.carapp.feature."
    private const val CORE_COMMON_PREFIX = "com.ruizurraca.carapp.core.common."
    private const val CORE_DATABASE_PREFIX = "com.ruizurraca.carapp.core.database."
    private const val CORE_MODEL_PREFIX = "com.ruizurraca.carapp.core.model."
    private const val CORE_SYNC_PREFIX = "com.ruizurraca.carapp.core.sync."
    private const val KOTLIN_PREFIX = "kotlin."
    private const val COROUTINES_PREFIX = "kotlinx.coroutines."
    private const val SERIALIZATION_PREFIX = "kotlinx.serialization."
    private val PACKAGE_DECLARATION = Regex("(?m)^\\s*package\\s+([^\\s;]+)")
}
