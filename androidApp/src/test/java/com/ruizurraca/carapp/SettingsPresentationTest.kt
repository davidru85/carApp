package com.ruizurraca.carapp

import com.ruizurraca.carapp.core.model.DistanceUnit
import com.ruizurraca.carapp.core.model.VolumeUnit
import java.util.Currency
import java.util.Locale
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

/**
 * The host presentation of `docs/SPECIFICATION.md §3.1`: which session rows each phase offers (B1-D5),
 * the read-only unit labels and the platform currency name. None of it is shared state; it is how the
 * Android host draws typed values it receives.
 */
class SettingsPresentationTest {
    @Test
    fun aPermanentSessionOffersSignOutAndAccountDeletion() {
        assertEquals(
            listOf(SettingsSessionRow.SIGN_OUT, SettingsSessionRow.DELETE_ACCOUNT),
            settingsSessionRows(SessionPhase.PERMANENT),
        )
    }

    @Test
    fun anAnonymousSessionOffersOnlyLocalDataDeletion() {
        assertEquals(
            listOf(SettingsSessionRow.DELETE_LOCAL_DATA),
            settingsSessionRows(SessionPhase.ANONYMOUS),
        )
    }

    @Test
    fun aLocalSessionOffersOnlyLocalDataDeletion() {
        assertEquals(
            listOf(SettingsSessionRow.DELETE_LOCAL_DATA),
            settingsSessionRows(SessionPhase.LOCAL),
        )
    }

    @Test
    fun aPhaseWithoutAnOwnerOffersNoSessionRow() {
        listOf(SessionPhase.UNKNOWN, SessionPhase.SIGNED_OUT, SessionPhase.DELETING).forEach { phase ->
            assertEquals(emptyList<SettingsSessionRow>(), settingsSessionRows(phase), "phase $phase")
        }
    }

    @Test
    fun everyDistanceUnitMapsToItsOwnLabel() {
        assertEquals(R.string.unit_distance_km, distanceUnitLabelResource(DistanceUnit.KM))
        assertEquals(R.string.unit_distance_miles, distanceUnitLabelResource(DistanceUnit.MILES))
    }

    @Test
    fun everyVolumeUnitMapsToItsOwnLabel() {
        assertEquals(R.string.unit_volume_liter, volumeUnitLabelResource(VolumeUnit.LITER))
        assertEquals(R.string.unit_volume_gallon, volumeUnitLabelResource(VolumeUnit.GALLON))
    }

    @Test
    fun theCurrencyDisplayNameComesFromThePlatformCurrencyApi() {
        val platformName = Currency.getInstance("EUR").getDisplayName(Locale.US)

        assertEquals(platformName, currencyDisplayName("EUR", Locale.US))
        assertNotEquals("EUR", platformName, "the platform name, not the bare code")
    }

    @Test
    fun anUnknownCurrencyCodeIsDisplayedAsItsCode() {
        assertEquals("QQQ", currencyDisplayName("QQQ", Locale.US))
    }
}
