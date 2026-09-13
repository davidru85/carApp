package com.ruizurraca.carapp.buildlogic.contract

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The awaiting-confirmation summary is required by assertion 4 to list every unresolved decision,
 * and its rows start with a decision ID. Reading those rows as registry rows makes assertions 2 and
 * 4 mutually unsatisfiable the moment the repository has its first `Proposed` decision.
 */
class DecisionRegistryTest {
    @Test
    fun theAwaitingSummaryDoesNotOverrideARegistryStatus() {
        val statuses = DecisionRegistry.decisionsWithStatus(
            DecisionRegistry.registryOf(BOARD),
            statusColumn = 5,
        )

        assertEquals(
            mapOf("D-1" to "Accepted", "D-2" to "Proposed"),
            statuses,
            "The consequence column of the awaiting summary is not a decision status.",
        )
    }

    @Test
    fun theAwaitingSummaryStillListsTheUnresolvedDecision() {
        val awaiting = BOARD.substringAfter("## Decisions Awaiting Owner Confirmation")

        assertEquals(true, awaiting.contains("D-2"))
    }

    /**
     * A repeated ID inside one table is a data-entry defect: the later row silently overwrites the
     * earlier one in [DecisionRegistry.decisionsWithStatus]. The detector MUST name it instead.
     */
    @Test
    fun aDuplicatedIdWithinOneTableIsReported() {
        assertEquals(
            listOf("D-2"),
            DecisionRegistry.duplicatedIds(BOARD_WITH_DUPLICATE_AWAITING_ROW),
        )
    }

    /**
     * The same ID appearing once in the registry and once in the awaiting summary is required, not
     * a duplicate: the two tables say different things about the same decision.
     */
    @Test
    fun anIdInBothTheRegistryAndTheAwaitingTableIsNotADuplicate() {
        assertEquals(emptyList(), DecisionRegistry.duplicatedIds(BOARD))
    }

    private companion object {
        val BOARD =
            """
            ## Decision Registry

            | ID | Area | Choice | Alternatives Reviewed | Status | Guardrail |
            |----|------|--------|-----------------------|--------|-----------|
            | D-1 | First | Chosen | None | Accepted | Guarded |
            | D-2 | Second | Undecided | Another | Proposed | Guarded |

            ## Decisions Awaiting Owner Confirmation

            | ID | Area | Recommendation | Needed by | Consequence if unresolved |
            |----|------|----------------|-----------|---------------------------|
            | D-2 | Second | Take option A | `E9-99` | The guarantee stays conditional |
            """.trimIndent()

        val BOARD_WITH_DUPLICATE_AWAITING_ROW =
            """
            ## Decision Registry

            | ID | Area | Choice | Alternatives Reviewed | Status | Guardrail |
            |----|------|--------|-----------------------|--------|-----------|
            | D-1 | First | Chosen | None | Accepted | Guarded |
            | D-2 | Second | Undecided | Another | Proposed | Guarded |

            ## Decisions Awaiting Owner Confirmation

            | ID | Area | Recommendation | Needed by | Consequence if unresolved |
            |----|------|----------------|-----------|---------------------------|
            | D-2 | Second | Take option A | `E9-99` | The guarantee stays conditional |
            | D-2 | Second | Take option A again | `E9-99` | The guarantee stays conditional |
            """.trimIndent()
    }
}
