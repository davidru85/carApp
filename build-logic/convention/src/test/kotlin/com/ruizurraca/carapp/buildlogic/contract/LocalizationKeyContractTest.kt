package com.ruizurraca.carapp.buildlogic.contract

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

/**
 * `docs/CONTRACTS.md §18` assertion 42 (`D-221`): every key a host references exists in both of
 * its catalogues, the catalogues of each host declare the same keys, and a key rendered without
 * arguments carries no format argument.
 *
 * Each fixture adds a synthetic source or a synthetic catalogue entry, so the fixtures do not depend
 * on which screens exist; the first test runs the real `contractCheck`.
 */
class LocalizationKeyContractTest {
    private val repositoryRoot = File(checkNotNull(System.getProperty("carapp.repoRoot")))
    private val real = HostLocalizationInputs.from(repositoryRoot)

    @Test
    fun contractCheckReportsAssertion42AndTheRepositoryPassesIt() {
        val result = ContractCheck(repositoryRoot, emptyMap()).runAll().singleOrNull { it.id == LocalizationKeyContract.ID }

        assertNotNull(result, "contract-check assertion ${LocalizationKeyContract.ID} is not registered")
        assertEquals(AssertionResult.Status.PASS, result.status, result.detail)
    }

    @Test
    fun anAndroidReferenceMissingFromBothCataloguesIsRejected() {
        val result = validate(real.withAndroidSource(ANDROID_FIXTURE, "val label = R.string.fixture_only_key"))

        assertRejected(
            "$ANDROID_FIXTURE:1 references R.string.fixture_only_key, missing from ${HostLocalizationInputs.ANDROID_STRINGS_EN}",
            result,
        )
        assertRejected(
            "$ANDROID_FIXTURE:1 references R.string.fixture_only_key, missing from ${HostLocalizationInputs.ANDROID_STRINGS_ES}",
            result,
        )
    }

    @Test
    fun anAndroidStringOnlyEnglishDeclaresIsRejected() {
        val english = real.androidStringsEn.withAndroidString("fixture_only_key", "Only English")

        assertRejected(
            "${HostLocalizationInputs.ANDROID_STRINGS_ES} lacks [fixture_only_key]",
            validate(real.withAndroidXml(HostLocalizationInputs.ANDROID_STRINGS_EN, english)),
        )
    }

    @Test
    fun anUntranslatableAndroidStringIsExemptFromTheSpanishCatalogue() {
        val english = real.androidStringsEn.withAndroidString("fixture_brand", "carApp", " translatable=\"false\"")
        val inputs =
            real.withAndroidXml(HostLocalizationInputs.ANDROID_STRINGS_EN, english)
                .withAndroidSource(ANDROID_FIXTURE, "val label = R.string.fixture_brand")

        assertAccepted(validate(inputs))
    }

    @Test
    fun aCommentedOutAndroidReferenceIsIgnored() {
        assertAccepted(validate(real.withAndroidSource(ANDROID_FIXTURE, "// val label = R.string.fixture_only_key")))
    }

    @Test
    fun anAndroidManifestReferenceMissingFromTheCatalogueIsRejected() {
        val manifestPath = "androidApp/src/main/AndroidManifest.xml"
        val manifest = real.androidResourceXml.getValue(manifestPath).replacingExactlyOnce("@string/app_name", "@string/fixture_label")

        assertRejected(
            "$manifestPath references @string/fixture_label, missing from ${HostLocalizationInputs.ANDROID_STRINGS_EN}",
            validate(real.withAndroidXml(manifestPath, manifest)),
        )
    }

    @Test
    fun anAndroidStringRenderedWithoutArgumentsMustNotTakeOne() {
        val inputs =
            real.withAndroidXml(HostLocalizationInputs.ANDROID_STRINGS_EN, real.androidStringsEn.withAndroidString("fixture_format", "%1\$s"))
                .withAndroidXml(HostLocalizationInputs.ANDROID_STRINGS_ES, real.androidStringsEs.withAndroidString("fixture_format", "%1\$s"))
                .withAndroidSource(ANDROID_FIXTURE, "val text = stringResource(R.string.fixture_format)")

        assertRejected(
            "$ANDROID_FIXTURE:1 renders R.string.fixture_format without arguments but " +
                "${HostLocalizationInputs.ANDROID_STRINGS_EN} gives it a format argument",
            validate(inputs),
        )
    }

    @Test
    fun anAndroidStringRenderedWithArgumentsMayTakeOne() {
        val inputs =
            real.withAndroidXml(HostLocalizationInputs.ANDROID_STRINGS_EN, real.androidStringsEn.withAndroidString("fixture_format", "%1\$s"))
                .withAndroidXml(HostLocalizationInputs.ANDROID_STRINGS_ES, real.androidStringsEs.withAndroidString("fixture_format", "%1\$s"))
                .withAndroidSource(ANDROID_FIXTURE, "val text = stringResource(R.string.fixture_format, value)")

        assertAccepted(validate(inputs))
    }

    @Test
    fun anIosReferenceMissingFromTheSpanishCatalogueIsRejected() {
        val inputs =
            real.copy(iosStringsEn = real.iosStringsEn.withIosString("fixture_only_key", "Only English"))
                .withIosSource(IOS_FIXTURE, "Text(\"fixture_only_key\")")

        assertRejected(
            "$IOS_FIXTURE:1 references 'fixture_only_key', missing from ${HostLocalizationInputs.IOS_STRINGS_ES}",
            validate(inputs),
        )
    }

    @Test
    fun anIosKeyOnlyEnglishDeclaresIsRejected() {
        val inputs = real.copy(iosStringsEn = real.iosStringsEn.withIosString("fixture_only_key", "Only English"))

        assertRejected("${HostLocalizationInputs.IOS_STRINGS_ES} lacks [fixture_only_key]", validate(inputs))
    }

    @Test
    fun everyIosReferenceShapeIsChecked() {
        val shapes =
            listOf(
                "Text(\"fixture_missing_key\")",
                "Button(\"fixture_missing_key\") {}",
                "Label(\"fixture_missing_key\", systemImage: \"trash\")",
                "Toggle(\"fixture_missing_key\", isOn: \$isOn)",
                "LocalizedStringKey(\"fixture_missing_key\")",
                ".navigationTitle(\"fixture_missing_key\")",
                ".accessibilityLabel(\"fixture_missing_key\")",
                ".alert(\"fixture_missing_key\", isPresented: \$isOn) {}",
                "String(localized: \"fixture_missing_key\")",
                "String.LocalizationValue(\"fixture_missing_key\")",
                "NSLocalizedString(\"fixture_missing_key\", comment: \"\")",
                "func fixtureKey() -> String { return \"fixture_missing_key\" }",
            )

        shapes.forEach { shape ->
            assertRejected(
                "$IOS_FIXTURE:1 references 'fixture_missing_key', missing from ${HostLocalizationInputs.IOS_STRINGS_EN}",
                validate(real.withIosSource(IOS_FIXTURE, shape)),
            )
        }
    }

    @Test
    fun anAccessibilityIdentifierIsNotAKeyReference() {
        assertAccepted(validate(real.withIosSource(IOS_FIXTURE, ".accessibilityIdentifier(\"fixture_missing_key\")")))
    }

    @Test
    fun aSnakeCaseReturnOutsideAKeyFunctionIsNotAKeyReference() {
        assertAccepted(
            validate(real.withIosSource(IOS_FIXTURE, "func fixtureIdentifier() -> String { return \"fixture_missing_key\" }")),
        )
    }

    @Test
    fun aCommentedOutIosReferenceIsIgnored() {
        assertAccepted(validate(real.withIosSource(IOS_FIXTURE, "// Text(\"fixture_missing_key\")")))
    }

    @Test
    fun aSwiftUiKeyRenderedWithoutArgumentsMustNotTakeOne() {
        val inputs =
            real.copy(
                iosStringsEn = real.iosStringsEn.withIosString("fixture_format", "Date: %@"),
                iosStringsEs = real.iosStringsEs.withIosString("fixture_format", "Fecha: %@"),
            ).withIosSource(IOS_FIXTURE, "Text(\"fixture_format\")")

        assertRejected(
            "$IOS_FIXTURE:1 renders 'fixture_format' without arguments but " +
                "${HostLocalizationInputs.IOS_STRINGS_EN} gives it a format argument",
            validate(inputs),
        )
    }

    @Test
    fun aFormattedLocalizedStringMayTakeArguments() {
        val inputs =
            real.copy(
                iosStringsEn = real.iosStringsEn.withIosString("fixture_format", "Date: %@"),
                iosStringsEs = real.iosStringsEs.withIosString("fixture_format", "Fecha: %@"),
            ).withIosSource(IOS_FIXTURE, "Text(String(format: String(localized: \"fixture_format\"), value))")

        assertAccepted(validate(inputs))
    }

    private fun validate(inputs: HostLocalizationInputs): AssertionResult = LocalizationKeyContract(inputs).validate()
}
