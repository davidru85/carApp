package com.ruizurraca.carapp.buildlogic.contract

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

/**
 * `docs/CONTRACTS.md §18` assertion 44 (`{D-B1-D11}`): no host UI source renders literal text, except
 * the explicitly allowlisted Debug-only source, which must itself be reachable only from `#if DEBUG`.
 */
class HostLiteralTextContractTest {
    private val repositoryRoot = File(checkNotNull(System.getProperty("carapp.repoRoot")))
    private val real = HostLocalizationInputs.from(repositoryRoot)

    @Test
    fun contractCheckReportsAssertion44AndTheRepositoryPassesIt() {
        val result = ContractCheck(repositoryRoot, emptyMap()).runAll().singleOrNull { it.id == HostLiteralTextContract.ID }

        assertNotNull(result, "contract-check assertion ${HostLiteralTextContract.ID} is not registered")
        assertEquals(AssertionResult.Status.PASS, result.status, result.detail)
    }

    @Test
    fun aComposeTextLiteralIsRejected() {
        assertRejected("$ANDROID_FIXTURE:1: literal text passed to Text", android("Text(\"Hello\")"))
    }

    @Test
    fun aNamedComposeTextLiteralIsRejected() {
        assertRejected("$ANDROID_FIXTURE:1: literal text passed to Text", android("Text(text = \"Hello\")"))
    }

    @Test
    fun everyComposeSemanticsLiteralIsRejected() {
        listOf("contentDescription", "stateDescription", "onClickLabel", "onLongClickLabel", "paneTitle").forEach { name ->
            assertRejected(
                "$ANDROID_FIXTURE:1: literal text passed to $name",
                android("Modifier.semantics { $name = \"Back\" }"),
            )
        }
    }

    @Test
    fun aSnackbarLiteralIsRejected() {
        assertRejected(
            "$ANDROID_FIXTURE:1: literal text passed to showSnackbar",
            android("scope.launch { snackbarHostState.showSnackbar(\"Saved\") }"),
        )
    }

    @Test
    fun aComposeTextFromAResourceIsAccepted() {
        assertAccepted(android("Text(stringResource(R.string.back))"))
    }

    @Test
    fun aCommentedOutComposeLiteralIsIgnored() {
        assertAccepted(android("// Text(\"Hello\")"))
    }

    @Test
    fun aSwiftUiVerbatimTextIsRejected() {
        assertRejected("$IOS_FIXTURE:1: Text(verbatim:) renders text that no catalogue backs", ios("Text(verbatim: name)"))
    }

    @Test
    fun anInterpolatedSwiftUiLiteralIsRejected() {
        assertRejected(
            """$IOS_FIXTURE:1: '\(value) L/100 km' passed to Text is not a catalogue key""",
            ios("""Text("\(value) L/100 km")"""),
        )
    }

    @Test
    fun aSwiftUiLiteralThatIsNotAKeyIsRejected() {
        assertRejected("$IOS_FIXTURE:1: 'Save' passed to Button is not a catalogue key", ios("Button(\"Save\") {}"))
    }

    @Test
    fun aLocalizedStringLiteralThatIsNotAKeyIsRejected() {
        assertRejected("$IOS_FIXTURE:1: 'Save' passed to String is not a catalogue key", ios("String(localized: \"Save\")"))
    }

    @Test
    fun aSwiftUiKeyLiteralIsAccepted() {
        assertAccepted(ios("Text(\"vehicle_list_title\")"))
    }

    @Test
    fun theDebugOnlyAllowlistExemptsItsSource() {
        val diagnostics = real.iosAppSources.getValue(DIAGNOSTICS) + "\nlet debugLine = Text(verbatim: line)\n"

        assertAccepted(validate(real.withIosSource(DIAGNOSTICS, diagnostics)))
    }

    @Test
    fun aMissingAllowlistedSourceIsRejected() {
        assertRejected(
            "allowlisted Debug-only source $DIAGNOSTICS does not exist",
            validate(real.withoutIosSource(DIAGNOSTICS)),
        )
    }

    @Test
    fun aDebugOnlyViewUsedOutsideDebugIsRejected() {
        assertRejected(
            "$IOS_FIXTURE:1 uses Debug-only DiagnosticsView outside #if DEBUG",
            ios("let view = DiagnosticsView(model: model)"),
        )
    }

    @Test
    fun aDebugOnlyViewInsideDebugIsAccepted() {
        assertAccepted(ios("#if DEBUG\nlet view = DiagnosticsView(model: model)\n#endif"))
    }

    @Test
    fun aNegatedDebugBlockIsNotDebugOnly() {
        assertRejected(
            "$IOS_FIXTURE:2 uses Debug-only DiagnosticsView outside #if DEBUG",
            ios("#if !DEBUG\nlet view = DiagnosticsView(model: model)\n#endif"),
        )
    }

    @Test
    fun theElseBranchOfADebugBlockIsNotDebugOnly() {
        assertRejected(
            "$IOS_FIXTURE:3 uses Debug-only DiagnosticsView outside #if DEBUG",
            ios("#if DEBUG\n#else\nlet view = DiagnosticsView(model: model)\n#endif"),
        )
    }

    private fun android(source: String): AssertionResult = validate(real.withAndroidSource(ANDROID_FIXTURE, source))

    private fun ios(source: String): AssertionResult = validate(real.withIosSource(IOS_FIXTURE, source))

    private fun validate(inputs: HostLocalizationInputs): AssertionResult = HostLiteralTextContract(inputs).validate()

    private companion object {
        const val DIAGNOSTICS = "iosApp/DiagnosticsView.swift"
    }
}
