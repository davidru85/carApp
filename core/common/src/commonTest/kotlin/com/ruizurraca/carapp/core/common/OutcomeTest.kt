package com.ruizurraca.carapp.core.common

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class OutcomeTest {
    private val ok: Outcome<Int, AppError> = Outcome.Ok(2)
    private val err: Outcome<Int, AppError> = Outcome.Err(ValidationError.NoOp)

    @Test
    fun mapTransformsOnlyTheSuccessSide() {
        assertEquals(Outcome.Ok(4), ok.map { it * 2 })
        assertEquals(err, err.map { it * 2 })
    }

    @Test
    fun mapErrorTransformsOnlyTheFailureSide() {
        assertEquals(ok, ok.mapError { ValidationError.EntityNotFound })
        assertEquals(Outcome.Err(ValidationError.EntityNotFound), err.mapError { ValidationError.EntityNotFound })
    }

    @Test
    fun flatMapChainsAndShortCircuits() {
        assertEquals(Outcome.Ok(3), ok.flatMap { Outcome.Ok(it + 1) })
        assertEquals(
            Outcome.Err(ValidationError.EntityDeleted),
            ok.flatMap { Outcome.Err(ValidationError.EntityDeleted) },
        )
        assertEquals(err, err.flatMap { Outcome.Ok(it + 1) })
    }

    @Test
    fun getOrNullReturnsTheValueOrNull() {
        assertEquals(2, ok.getOrNull())
        assertNull(err.getOrNull())
    }

    @Test
    fun foldPicksTheMatchingBranch() {
        assertEquals("ok:2", ok.fold(onOk = { "ok:$it" }, onErr = { "err:${it.code}" }))
        assertEquals("err:VALIDATION.NO_OP", err.fold(onOk = { "ok:$it" }, onErr = { "err:${it.code}" }))
    }
}
