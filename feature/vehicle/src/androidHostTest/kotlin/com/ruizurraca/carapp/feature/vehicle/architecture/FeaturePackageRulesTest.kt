package com.ruizurraca.carapp.feature.vehicle.architecture

import kotlin.test.Test
import kotlin.test.assertFailsWith

class FeaturePackageRulesTest {
    @Test
    fun featureDomainPackagesObeyTheProductionBoundary() {
        FeaturePackageRules.assertDomain()
    }

    @Test
    fun domainRuleRejectsItsOwnDataPackageFixture() {
        assertFailsWith<AssertionError> {
            FeaturePackageRules.assertDomain(fixtureDirectory("domain"))
        }
    }

    @Test
    fun featureDataPackagesObeyTheProductionBoundary() {
        FeaturePackageRules.assertData()
    }

    @Test
    fun dataRuleRejectsAnotherFeatureFixture() {
        assertFailsWith<AssertionError> {
            FeaturePackageRules.assertData(fixtureDirectory("data"))
        }
    }

    @Test
    fun featurePresentationPackagesObeyTheProductionBoundary() {
        FeaturePackageRules.assertPresentation()
    }

    @Test
    fun presentationRuleRejectsItsOwnDataPackageFixture() {
        assertFailsWith<AssertionError> {
            FeaturePackageRules.assertPresentation(fixtureDirectory("presentation"))
        }
    }

    private fun fixtureDirectory(layer: String): String =
        "feature/vehicle/src/androidHostTest/resources/architecture-fixtures/$layer"
}
