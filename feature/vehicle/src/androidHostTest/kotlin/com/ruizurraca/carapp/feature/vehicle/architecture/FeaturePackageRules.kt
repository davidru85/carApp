package com.ruizurraca.carapp.feature.vehicle.architecture

import com.lemonappdev.konsist.api.Konsist

internal object FeaturePackageRules {
    fun assertDomain(directory: String? = null) {
        scope(directory)
    }

    fun assertData(directory: String? = null) {
        scope(directory)
    }

    fun assertPresentation(directory: String? = null) {
        scope(directory)
    }

    private fun scope(directory: String?) =
        if (directory == null) {
            Konsist.scopeFromProduction()
        } else {
            Konsist.scopeFromDirectory(directory)
        }
}
