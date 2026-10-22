package com.ruizurraca.carapp.buildlogic.contract

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The closed set of `UiMessage.code` values (`docs/CONTRACTS.md §20.10`) that assertion 41 and the
 * `AppErrorCodesTest` guard both read. `build-logic` cannot depend on `:core:common`, so the codes are
 * parsed from their declarations; each test pins one parsing rule.
 */
class UiMessageCodeInventoryTest {
    private val repositoryRoot = File(checkNotNull(System.getProperty("carapp.repoRoot")))
    private val appError = repositoryRoot.resolve(UiMessageCodeInventory.APP_ERROR_PATH).readText()

    @Test
    fun everyAppErrorLeafCodeIsReadInDeclarationOrder() {
        val codes = UiMessageCodeInventory.appErrorCodes(appError)

        assertEquals(46, codes.size, codes.toString())
        assertEquals("VALIDATION.REQUIRED_FIELD", codes.first())
        assertEquals("UNEXPECTED", codes.last())
    }

    @Test
    fun theLeafTheCodesTestMissedIsRead() {
        assertTrue("AUTH.NO_ACCOUNT_AVAILABLE" in UiMessageCodeInventory.appErrorCodes(appError))
    }

    @Test
    fun aCommentedOutLeafIsNotRead() {
        val source =
            """
            // data object Ghost : AuthError { override val code = "AUTH.GHOST" }
            /* data object Shadow : AuthError { override val code = "AUTH.SHADOW" } */
            data object Real : AuthError { override val code = "AUTH.REAL" }
            """.trimIndent()

        assertEquals(listOf("AUTH.REAL"), UiMessageCodeInventory.appErrorCodes(source))
    }

    @Test
    fun aLeafWithAnExplicitStringTypeIsRead() {
        val source = """data object Typed : AuthError { override val code: String = "AUTH.TYPED" }"""

        assertEquals(listOf("AUTH.TYPED"), UiMessageCodeInventory.appErrorCodes(source))
    }

    @Test
    fun everyConfirmationEntryBecomesAConfirmationCode() {
        assertEquals(
            listOf(
                "CONFIRMATION.OdometerInconsistent",
                "CONFIRMATION.DiscardPendingChanges",
                "CONFIRMATION.DeleteAccount",
                "CONFIRMATION.DeleteLocalData",
                "CONFIRMATION.AdoptExistingAccount",
            ),
            UiMessageCodeInventory.confirmationCodes(appError),
        )
    }

    @Test
    fun infoCodesAreReadFromEveryProducerStringLiteral() {
        val sources =
            mapOf(
                "feature/a/src/commonMain/kotlin/A.kt" to "private const val CODE = \"INFO.BETA\"",
                "shared/src/commonMain/kotlin/B.kt" to
                    "UiMessage(id = 1L, kind = UiMessageKind.INFO, code = \"INFO.ALPHA\", confirmation = null)",
            )

        assertEquals(listOf("INFO.ALPHA", "INFO.BETA"), UiMessageCodeInventory.infoCodes(sources))
    }

    @Test
    fun theRealProducersPublishBothDeletionConfirmations() {
        val codes = UiMessageCodeInventory.infoCodes(UiMessageCodeInventory.producerSources(repositoryRoot))

        assertTrue(
            codes.containsAll(listOf("INFO.CONFIRM_DELETE_FUEL_ENTRY", "INFO.CONFIRM_DELETE_VEHICLE")),
            codes.toString(),
        )
    }

    @Test
    fun producerSourcesAreTheProductionCommonSourcesOnly() {
        val paths = UiMessageCodeInventory.producerSources(repositoryRoot).keys
        val outside =
            paths.filterNot { path ->
                "/src/commonMain/" in path &&
                    (path.startsWith("shared/") || path.startsWith("feature/") || path.startsWith("core/"))
            }

        assertTrue(paths.isNotEmpty(), "No producer source was read; every rule built on it would pass vacuously.")
        assertEquals(emptyList(), outside)
    }
}
