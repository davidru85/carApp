package com.ruizurraca.carapp.buildlogic.contract

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull

/**
 * `docs/CONTRACTS.md §18` assertion 43 (`D-221`): a `UiState` carries no user-facing text. Its
 * text-typed fields are exactly the `§14.2` table, each with one of four kinds.
 */
class UiStateTextContractTest {
    private val repositoryRoot = File(checkNotNull(System.getProperty("carapp.repoRoot")))
    private val real = UiStateTextContract.Inputs.from(repositoryRoot)

    @Test
    fun contractCheckReportsAssertion43AndTheRepositoryPassesIt() {
        val result = ContractCheck(repositoryRoot, emptyMap()).runAll().singleOrNull { it.id == UiStateTextContract.ID }

        assertNotNull(result, "contract-check assertion ${UiStateTextContract.ID} is not registered")
        assertEquals(AssertionResult.Status.PASS, result.status, result.detail)
    }

    @Test
    fun aTextFieldAbsentFromTheAllowlistIsRejected() {
        assertRejected(
            "$FIXTURE_UI: FixtureUiState.title: String carries text but is not in the §14.2 allowlist",
            validate(withSource("data class FixtureUiState(\n    val count: Int,\n    val title: String,\n)")),
        )
    }

    @Test
    fun aNullableCollectionOfTextIsRejected() {
        assertRejected(
            "$FIXTURE_UI: FixtureUiState.titles: List<String>? carries text but is not in the §14.2 allowlist",
            validate(withSource("data class FixtureUiState(val titles: List<String>?)")),
        )
    }

    @Test
    fun aCharSequenceFieldIsRejected() {
        assertRejected(
            "$FIXTURE_UI: FixtureUiState.title: CharSequence carries text but is not in the §14.2 allowlist",
            validate(withSource("data class FixtureUiState(val title: CharSequence)")),
        )
    }

    @Test
    fun anItemModelIsScannedLikeAUiState() {
        assertRejected(
            "$FIXTURE_UI: FixtureRowUi.caption: String carries text but is not in the §14.2 allowlist",
            validate(withSource("data class FixtureRowUi(val caption: String)")),
        )
    }

    @Test
    fun uiMessageIsScanned() {
        val path = "core/common/src/commonMain/kotlin/com/ruizurraca/carapp/core/common/PresentationTypes.kt"
        val source = real.sources.getValue(path).replacingExactlyOnce("    val code: String,\n", "    val code: String,\n    val text: String,\n")

        assertRejected(
            "$path: UiMessage.text: String carries text but is not in the §14.2 allowlist",
            validate(real.copy(sources = real.sources + (path to source))),
        )
    }

    @Test
    fun aDefaultTextValueDoesNotHideTheType() {
        assertRejected(
            "$FIXTURE_UI: FixtureUiState.title: String carries text but is not in the §14.2 allowlist",
            validate(withSource("data class FixtureUiState(val title: String = \"Hello\")")),
        )
    }

    @Test
    fun aNonTextFieldIsAccepted() {
        assertAccepted(validate(withSource("data class FixtureUiState(val count: Int, val kind: UiMessageKind?)")))
    }

    @Test
    fun aKdocMentioningStringDoesNotCount() {
        assertAccepted(validate(withSource("data class FixtureUiState(\n    /** Not a String: a count. */\n    val count: Int,\n)")))
    }

    @Test
    fun aLambdaParameterDoesNotBreakTheParser() {
        val result = validate(withSource("data class FixtureUiState(val onTap: () -> Unit, val title: String)"))

        assertRejected("$FIXTURE_UI: FixtureUiState.title: String carries text but is not in the §14.2 allowlist", result)
        assertFalse("onTap" in result.detail, result.detail)
    }

    @Test
    fun aStaleAllowlistEntryIsRejected() {
        val path = "feature/fuel/src/commonMain/kotlin/com/ruizurraca/carapp/feature/fuel/presentation/FuelEntryUiModels.kt"
        val source = real.sources.getValue(path).replacingExactlyOnce("    val notes: String?,\n", "")

        assertRejected(
            "§14.2 allowlists FuelEntryFormUiState.notes, which is not a text field of a UiState class",
            validate(real.copy(sources = real.sources + (path to source))),
        )
    }

    @Test
    fun anUnknownKindIsRejected() {
        val contract =
            real.contract.replacingExactlyOnce(
                "| `FuelEntryFormUiState.notes` | User-entered |",
                "| `FuelEntryFormUiState.notes` | Display copy |",
            )

        assertRejected(
            "§14.2 classifies FuelEntryFormUiState.notes as 'Display copy', which is not one of " +
                UiStateTextContract.KINDS,
            validate(real.copy(contract = contract)),
        )
    }

    @Test
    fun aMissingAllowlistTableIsRejected() {
        val contract = real.contract.replacingExactlyOnce(UiStateTextContract.ALLOWLIST_HEADING, "### 14.2 Removed")

        assertRejected(
            "docs/CONTRACTS.md has no '${UiStateTextContract.ALLOWLIST_HEADING}' table",
            validate(real.copy(contract = contract)),
        )
    }

    @Test
    fun noParsedUiStateClassIsRejected() {
        assertRejected(
            "no UiState class parsed from the production Kotlin sources",
            validate(real.copy(sources = emptyMap())),
        )
    }

    private fun withSource(text: String): UiStateTextContract.Inputs = real.copy(sources = real.sources + (FIXTURE_UI to text))

    private fun validate(inputs: UiStateTextContract.Inputs): AssertionResult = UiStateTextContract(inputs).validate()

    private companion object {
        const val FIXTURE_UI =
            "feature/fixture/src/commonMain/kotlin/com/ruizurraca/carapp/feature/fixture/presentation/FixtureUiModels.kt"
    }
}
