package com.ruizurraca.carapp.buildlogic.contract

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

/**
 * `docs/CONTRACTS.md §18` assertion 41 (`D-220`): every `UiMessage.code` has an explicit,
 * single-code mapping on both hosts and a row in the `§14.1` table, and the three agree.
 *
 * `D-16` requires a failing fixture per rejected shape. Every fixture mutates the real repository
 * inputs through `replacingExactlyOnce`, so a fixture whose anchor disappears fails loudly instead of
 * passing vacuously, and the first test runs the real `contractCheck`.
 */
class UiMessageMappingContractTest {
    private val repositoryRoot = File(checkNotNull(System.getProperty("carapp.repoRoot")))
    private val real = HostLocalizationInputs.from(repositoryRoot)
    private val androidMapping = real.androidMainSources.getValue(HostLocalizationInputs.ANDROID_MAPPING)
    private val iosMapping = real.iosAppSources.getValue(HostLocalizationInputs.IOS_MAPPING)

    @Test
    fun contractCheckReportsAssertion41AndTheRepositoryPassesIt() {
        val result =
            ContractCheck(repositoryRoot, emptyMap()).runAll().singleOrNull { it.id == UiMessageMappingContract.ID }

        assertNotNull(result, "contract-check assertion ${UiMessageMappingContract.ID} is not registered")
        assertEquals(AssertionResult.Status.PASS, result.status, result.detail)
    }

    @Test
    fun aCodeMissingFromTheAndroidMappingIsRejected() {
        val mapping = androidMapping.replacingExactlyOnce(ANDROID_TOKEN_EXPIRED, "")

        assertRejected(
            "${HostLocalizationInputs.ANDROID_MAPPING} has no explicit entry for [AUTH.TOKEN_EXPIRED]",
            validate(real.withAndroidSource(HostLocalizationInputs.ANDROID_MAPPING, mapping)),
        )
    }

    @Test
    fun aCodeMissingFromTheIosMappingIsRejected() {
        val mapping = iosMapping.replacingExactlyOnce(IOS_TOKEN_EXPIRED, "")

        assertRejected(
            "${HostLocalizationInputs.IOS_MAPPING} has no explicit entry for [AUTH.TOKEN_EXPIRED]",
            validate(real.withIosSource(HostLocalizationInputs.IOS_MAPPING, mapping)),
        )
    }

    @Test
    fun aCodeMissingFromTheContractTableIsRejected() {
        val lines = real.contract.lines()
        val kept = lines.filterNot { line -> line.startsWith(TABLE_TOKEN_EXPIRED_ROW) }
        assertEquals(lines.size - 1, kept.size, "the §14.1 row of AUTH.TOKEN_EXPIRED is the fixture anchor")

        assertRejected(
            "docs/CONTRACTS.md §14.1 has no explicit entry for [AUTH.TOKEN_EXPIRED]",
            validate(real.copy(contract = kept.joinToString("\n"))),
        )
    }

    @Test
    fun aMissingContractTableIsRejected() {
        val contract = real.contract.replacingExactlyOnce(UiMessageMappingContract.TABLE_HEADING, "### 14.1 Removed")

        assertRejected(
            "docs/CONTRACTS.md has no '${UiMessageMappingContract.TABLE_HEADING}' section",
            validate(real.copy(contract = contract)),
        )
    }

    @Test
    fun aCommentedOutAndroidBranchDoesNotCount() {
        val mapping = androidMapping.replacingExactlyOnce(ANDROID_TOKEN_EXPIRED, "        // " + ANDROID_TOKEN_EXPIRED.trimStart())

        assertRejected(
            "${HostLocalizationInputs.ANDROID_MAPPING} has no explicit entry for [AUTH.TOKEN_EXPIRED]",
            validate(real.withAndroidSource(HostLocalizationInputs.ANDROID_MAPPING, mapping)),
        )
    }

    @Test
    fun aBranchNamingTwoCodesIsRejected() {
        val mapping =
            androidMapping.replacingExactlyOnce(
                ANDROID_REMOTE_PAIR,
                "        \"REMOTE.UNAVAILABLE\", \"REMOTE.DEADLINE_EXCEEDED\" -> R.string.error_backup_offline\n",
            )

        assertRejected(
            "${HostLocalizationInputs.ANDROID_MAPPING} has no explicit entry for [REMOTE.UNAVAILABLE]",
            validate(real.withAndroidSource(HostLocalizationInputs.ANDROID_MAPPING, mapping)),
        )
    }

    @Test
    fun aNewAppErrorLeafWithoutMappingsIsRejected() {
        val source = real.appErrorSource + "\ndata object Ghost : AuthError { override val code = \"AUTH.GHOST\" }\n"

        assertRejected(
            "${HostLocalizationInputs.ANDROID_MAPPING} has no explicit entry for [AUTH.GHOST]",
            validate(real.copy(appErrorSource = source)),
        )
    }

    @Test
    fun aNewInfoCodeWithoutMappingsIsRejected() {
        val producers =
            real.producerSources + ("feature/fixture/src/commonMain/kotlin/Fixture.kt" to "private const val CODE = \"INFO.GHOST\"")

        assertRejected(
            "${HostLocalizationInputs.IOS_MAPPING} has no explicit entry for [INFO.GHOST]",
            validate(real.copy(producerSources = producers)),
        )
    }

    @Test
    fun aMappingForACodeNoSourceDefinesIsRejected() {
        val mapping =
            androidMapping.replacingExactlyOnce(
                ANDROID_FALLBACK,
                "        \"AUTH.GHOST\" -> R.string.error_unexpected\n$ANDROID_FALLBACK",
            )

        assertRejected(
            "${HostLocalizationInputs.ANDROID_MAPPING} maps codes no source defines: [AUTH.GHOST]",
            validate(real.withAndroidSource(HostLocalizationInputs.ANDROID_MAPPING, mapping)),
        )
    }

    @Test
    fun aCodeMappedTwiceIsRejected() {
        val mapping = androidMapping.replacingExactlyOnce(ANDROID_UNEXPECTED, ANDROID_UNEXPECTED + ANDROID_UNEXPECTED)

        assertRejected(
            "${HostLocalizationInputs.ANDROID_MAPPING} maps UNEXPECTED more than once",
            validate(real.withAndroidSource(HostLocalizationInputs.ANDROID_MAPPING, mapping)),
        )
    }

    @Test
    fun hostsThatDisagreeOnAKeyAreRejected() {
        val mapping =
            iosMapping.replacingExactlyOnce(
                IOS_REMOTE_UNAVAILABLE,
                "    case \"REMOTE.UNAVAILABLE\":\n        return \"error_restore\"\n",
            )

        assertRejected(
            "REMOTE.UNAVAILABLE: Android maps to 'error_backup_offline', iOS to 'error_restore', " +
                "docs/CONTRACTS.md §14.1 to 'error_backup_offline'",
            validate(real.withIosSource(HostLocalizationInputs.IOS_MAPPING, mapping)),
        )
    }

    @Test
    fun anAndroidFallbackOtherThanTheGenericCopyIsRejected() {
        val mapping = androidMapping.replacingExactlyOnce(ANDROID_FALLBACK, "        else -> R.string.error_restore\n")

        assertRejected(
            "${HostLocalizationInputs.ANDROID_MAPPING} falls back to 'error_restore' instead of 'error_unexpected'",
            validate(real.withAndroidSource(HostLocalizationInputs.ANDROID_MAPPING, mapping)),
        )
    }

    @Test
    fun anIosFallbackOtherThanTheGenericCopyIsRejected() {
        val mapping = iosMapping.replacingExactlyOnce(IOS_FALLBACK, "    default:\n        return \"error_restore\"\n")

        assertRejected(
            "${HostLocalizationInputs.IOS_MAPPING} falls back to 'error_restore' instead of 'error_unexpected'",
            validate(real.withIosSource(HostLocalizationInputs.IOS_MAPPING, mapping)),
        )
    }

    @Test
    fun aMappedKeyMissingFromTheSpanishAndroidCatalogueIsRejected() {
        val spanish = real.androidStringsEs.withoutAndroidString("error_backup_offline")

        assertRejected(
            "${HostLocalizationInputs.ANDROID_STRINGS_ES} has no key 'error_backup_offline' mapped by " +
                HostLocalizationInputs.ANDROID_MAPPING,
            validate(real.withAndroidXml(HostLocalizationInputs.ANDROID_STRINGS_ES, spanish)),
        )
    }

    @Test
    fun aMappedKeyMissingFromTheSpanishIosCatalogueIsRejected() {
        val spanish = real.iosStringsEs.withoutIosString("error_backup_offline")

        assertRejected(
            "${HostLocalizationInputs.IOS_STRINGS_ES} has no key 'error_backup_offline' mapped by " +
                HostLocalizationInputs.IOS_MAPPING,
            validate(real.copy(iosStringsEs = spanish)),
        )
    }

    @Test
    fun aMappedKeyWithAFormatArgumentIsRejected() {
        val english =
            real.androidStringsEn.replacingExactlyOnce(
                "<string name=\"error_backup_offline\">",
                "<string name=\"error_backup_offline\">%1\$s ",
            )

        assertRejected(
            "${HostLocalizationInputs.ANDROID_STRINGS_EN} key 'error_backup_offline' mapped by " +
                "${HostLocalizationInputs.ANDROID_MAPPING} carries a format argument",
            validate(real.withAndroidXml(HostLocalizationInputs.ANDROID_STRINGS_EN, english)),
        )
    }

    @Test
    fun anAndroidTestListThatDropsACodeIsRejected() {
        val test = real.androidMappingTest.replacingExactlyOnce("\"AUTH.TOKEN_EXPIRED\"", "\"AUTH.RENAMED\"")

        assertRejected(
            "${HostLocalizationInputs.ANDROID_MAPPING_TEST} does not name [AUTH.TOKEN_EXPIRED]",
            validate(real.copy(androidMappingTest = test)),
        )
    }

    @Test
    fun anIosTestListThatDropsACodeIsRejected() {
        val test = real.iosMappingTest.replacingExactlyOnce("\"AUTH.TOKEN_EXPIRED\"", "\"AUTH.RENAMED\"")

        assertRejected(
            "${HostLocalizationInputs.IOS_MAPPING_TEST} does not name [AUTH.TOKEN_EXPIRED]",
            validate(real.copy(iosMappingTest = test)),
        )
    }

    @Test
    fun anUnparseableErrorTaxonomyIsRejected() {
        assertRejected(
            "no AppError leaf code parsed from ${UiMessageCodeInventory.APP_ERROR_PATH}",
            validate(real.copy(appErrorSource = "")),
        )
    }

    private fun validate(inputs: HostLocalizationInputs): AssertionResult = UiMessageMappingContract(inputs).validate()

    private companion object {
        const val ANDROID_TOKEN_EXPIRED = "        \"AUTH.TOKEN_EXPIRED\" -> R.string.error_auth_session_expired\n"
        const val IOS_TOKEN_EXPIRED = "    case \"AUTH.TOKEN_EXPIRED\":\n        return \"error_auth_session_expired\"\n"
        const val ANDROID_REMOTE_PAIR =
            "        \"REMOTE.UNAVAILABLE\" -> R.string.error_backup_offline\n" +
                "        \"REMOTE.DEADLINE_EXCEEDED\" -> R.string.error_backup_offline\n"
        const val ANDROID_UNEXPECTED = "        \"UNEXPECTED\" -> R.string.error_unexpected\n"
        const val ANDROID_FALLBACK = "        else -> R.string.error_unexpected\n"
        const val IOS_REMOTE_UNAVAILABLE = "    case \"REMOTE.UNAVAILABLE\":\n        return \"error_backup_offline\"\n"
        const val IOS_FALLBACK = "    default:\n        return \"error_unexpected\"\n"
        const val TABLE_TOKEN_EXPIRED_ROW = "| `AUTH.TOKEN_EXPIRED` |"
    }
}
