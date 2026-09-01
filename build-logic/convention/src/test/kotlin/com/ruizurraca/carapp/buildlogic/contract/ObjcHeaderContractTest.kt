package com.ruizurraca.carapp.buildlogic.contract

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ObjcHeaderContractTest {
    @Test
    fun acceptsTheCompleteSwiftAllowlist() {
        val result = validateObjcHeader(completeAllowedHeader())

        assertTrue(result.missing.isEmpty())
        assertTrue(result.forbidden.isEmpty())
        assertEquals(1, result.swiftFactoryDeclarationCount)
    }

    @Test
    fun rejectsForbiddenKotlinConstructionTypes() {
        val result =
            validateObjcHeader(
                completeAllowedHeader() +
                    """
                    @interface SharedAppProviders
                    @interface SharedAppGraphDependencies
                    @interface SharedAppGraph
                    @interface SharedSyncController
                    + (void)buildAppGraph;
                    """.trimIndent(),
            )

        assertEquals(
            setOf(
                "AppProviders",
                "AppGraphDependencies",
                "AppGraph",
                "SyncController",
                "buildAppGraph",
            ),
            result.forbidden,
        )
    }

    @Test
    fun ignoresForbiddenWordsInsideDocumentationComments() {
        val result =
            validateObjcHeader(
                completeAllowedHeader() +
                    "/** Swift-facing factories do not expose CoroutineScope or AppGraph. */",
            )

        assertTrue(result.forbidden.isEmpty())
    }

    @Test
    fun rejectsFuelDomainDataAndUseCaseDeclarations() {
        val result =
            validateObjcHeader(
                completeAllowedHeader() +
                    """
                    @interface SharedMoneyInput
                    @interface SharedCreateFuelEntryCommand
                    @interface SharedFuelEntryValidationContext
                    @interface SharedCalculateConsumption
                    @interface SharedSqlDelightFuelEntryRepository
                    - (void)observeSaveCompletions;
                    """.trimIndent(),
            )

        assertEquals(
            setOf(
                "FuelConsumptionUseCase",
                "FuelEntryCommand",
                "FuelEntryRepository",
                "FuelEntryValidation",
                "FuelSaveCompletion",
                "MoneyInput",
                "SqlDelightFuelEntryRepository",
            ),
            result.forbidden,
        )
    }

    private fun completeAllowedHeader(): String =
        REQUIRED_SWIFT_HEADER_SYMBOLS.joinToString("\n") { symbol ->
            when (symbol) {
                "createSwiftAppGraph" -> "+ (void)createSwiftAppGraph;"
                else -> "@interface Shared$symbol"
            }
        }
}
