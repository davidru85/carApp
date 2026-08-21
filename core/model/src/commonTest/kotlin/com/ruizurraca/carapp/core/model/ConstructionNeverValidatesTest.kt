package com.ruizurraca.carapp.core.model

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * `docs/CONTRACTS.md §20.0`: these types have no `init` block, throw nothing and reject nothing.
 *
 * This is load-bearing rather than cosmetic. `§5` requires that a pull transaction MUST NOT fail
 * because of a domain constraint or a malformed remote payload. A throwing constructor would turn
 * one malformed remote document into an exception inside the pull transaction and stall the cursor
 * permanently — the exact failure mode `§5` exists to prevent. The MVP ships without App Check, so
 * such a document is reachable, and Firestore rules cannot verify that a string is a real ISO-4217
 * code.
 */
class ConstructionNeverValidatesTest {
    @Test
    fun wrappingAMalformedUuidSucceeds() {
        assertEquals("not-a-uuid", EntityId("not-a-uuid").value)
        assertEquals("", EntityId("").value)
    }

    @Test
    fun wrappingAnUnsupportedCurrencyCodeSucceeds() {
        assertEquals("XXX", CurrencyCode("XXX").value)
        assertEquals("not a code", CurrencyCode("not a code").value)
    }

    @Test
    fun wrappingAnArbitraryOwnerIdSucceeds() {
        assertEquals("", OwnerId("").value)
        assertEquals("LOCAL_OWNER", LOCAL_OWNER.value)
    }

    @Test
    fun scaledTypesAcceptAnyLongIncludingNegativeAndExtremeValues() {
        assertEquals(-1L, FuelVolume(-1L).scaled)
        assertEquals(Long.MAX_VALUE, PricePerLiter(Long.MAX_VALUE).scaled)
        assertEquals(Long.MIN_VALUE, ConsumptionL100Km(Long.MIN_VALUE).scaled)
    }
}
