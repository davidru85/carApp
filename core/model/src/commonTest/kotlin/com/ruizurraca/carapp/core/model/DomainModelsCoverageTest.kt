package com.ruizurraca.carapp.core.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant

class DomainModelsCoverageTest {
    private val id = EntityId("018f00d2-3ef4-7e02-8a01-4f78e21f9a10")
    private val ownerId = OwnerId("owner-1")
    private val timestamp = Instant.fromEpochMilliseconds(1_000)

    @Test
    fun vehiclePreservesItsCanonicalFields() {
        val vehicle =
            Vehicle(
                id = id,
                ownerId = ownerId,
                name = "Roadster",
                initialOdometerKm = 10_000,
                currentOdometerKm = 12_000,
                brand = "Example",
                model = null,
                fuelType = FuelType.GASOLINE,
                createdAt = timestamp,
                updatedAt = timestamp,
                deletedAt = null,
            )

        assertEquals(id, vehicle.id)
        assertEquals(ownerId, vehicle.ownerId)
        assertEquals("Roadster", vehicle.name)
        assertEquals(10_000, vehicle.initialOdometerKm)
        assertEquals(12_000, vehicle.currentOdometerKm)
        assertEquals("Example", vehicle.brand)
        assertNull(vehicle.model)
        assertEquals(FuelType.GASOLINE, vehicle.fuelType)
        assertEquals(timestamp, vehicle.createdAt)
        assertEquals(timestamp, vehicle.updatedAt)
        assertNull(vehicle.deletedAt)
        assertEquals(
            listOf(FuelType.GASOLINE, FuelType.DIESEL, FuelType.LPG, FuelType.CNG, FuelType.OTHER),
            FuelType.entries,
        )
    }

    @Test
    fun fuelEntryPreservesItsCanonicalFields() {
        val entry =
            FuelEntry(
                id = id,
                ownerId = ownerId,
                vehicleId = EntityId("018f00d2-3ef4-7e02-8a01-4f78e21f9a11"),
                date = timestamp,
                odometerKm = 12_000,
                litersScaled = 45_500,
                pricePerLiterScaled = 1_650,
                totalCostMinor = 7_508,
                currency = CurrencyCode("EUR"),
                isFullTank = true,
                hasMissedEntries = false,
                odometerInconsistent = false,
                notes = "Highway",
                createdAt = timestamp,
                updatedAt = timestamp,
                deletedAt = null,
            )

        assertEquals(id, entry.id)
        assertEquals(ownerId, entry.ownerId)
        assertEquals("018f00d2-3ef4-7e02-8a01-4f78e21f9a11", entry.vehicleId.value)
        assertEquals(timestamp, entry.date)
        assertEquals(12_000, entry.odometerKm)
        assertEquals(45_500, entry.litersScaled)
        assertEquals(1_650, entry.pricePerLiterScaled)
        assertEquals(7_508, entry.totalCostMinor)
        assertEquals(CurrencyCode("EUR"), entry.currency)
        assertTrue(entry.isFullTank)
        assertFalse(entry.hasMissedEntries)
        assertFalse(entry.odometerInconsistent)
        assertEquals("Highway", entry.notes)
        assertEquals(timestamp, entry.createdAt)
        assertEquals(timestamp, entry.updatedAt)
        assertNull(entry.deletedAt)
    }

    @Test
    fun fuelEntryListItemPreservesItsDerivedFields() {
        val item =
            FuelEntryListItem(
                id = id,
                date = timestamp,
                odometerKm = 12_000,
                litersScaled = 45_500,
                totalCostMinor = 7_508,
                currency = CurrencyCode("EUR"),
                isFullTank = true,
                consumption = ConsumptionL100Km(655),
                invalidReason = null,
                hasMissedEntries = true,
                odometerInconsistent = true,
            )

        assertEquals(id, item.id)
        assertEquals(timestamp, item.date)
        assertEquals(12_000, item.odometerKm)
        assertEquals(45_500, item.litersScaled)
        assertEquals(7_508, item.totalCostMinor)
        assertEquals(CurrencyCode("EUR"), item.currency)
        assertTrue(item.isFullTank)
        assertEquals(ConsumptionL100Km(655), item.consumption)
        assertNull(item.invalidReason)
        assertTrue(item.hasMissedEntries)
        assertTrue(item.odometerInconsistent)
    }

    @Test
    fun userSettingsPreserveUnitsAndAnalyticsConsent() {
        val settings =
            UserSettings(
                currency = CurrencyCode("EUR"),
                distanceUnit = DistanceUnit.MILES,
                volumeUnit = VolumeUnit.GALLON,
                analyticsEnabled = false,
            )

        assertEquals(CurrencyCode("EUR"), settings.currency)
        assertEquals(DistanceUnit.MILES, settings.distanceUnit)
        assertEquals(VolumeUnit.GALLON, settings.volumeUnit)
        assertFalse(settings.analyticsEnabled)
        assertEquals(listOf(DistanceUnit.KM, DistanceUnit.MILES), DistanceUnit.entries)
        assertEquals(listOf(VolumeUnit.LITER, VolumeUnit.GALLON), VolumeUnit.entries)
    }

    @Test
    fun segmentResultsPreserveValidAndInvalidOutcomes() {
        val toEntryId = EntityId("018f00d2-3ef4-7e02-8a01-4f78e21f9a12")
        val valid =
            SegmentResult.Valid(
                fromEntryId = id,
                toEntryId = toEntryId,
                litersScaled = 45_500,
                distanceKm = 700,
                consumption = ConsumptionL100Km(650),
            )
        val invalid =
            SegmentResult.Invalid(
                toEntryId = toEntryId,
                reason = ConsumptionInvalidReason.MissedEntriesInSegment,
            )

        assertEquals(id, valid.fromEntryId)
        assertEquals(toEntryId, valid.toEntryId)
        assertEquals(45_500, valid.litersScaled)
        assertEquals(700, valid.distanceKm)
        assertEquals(ConsumptionL100Km(650), valid.consumption)
        assertEquals(toEntryId, invalid.toEntryId)
        assertEquals(ConsumptionInvalidReason.MissedEntriesInSegment, invalid.reason)
        assertEquals(6, ConsumptionInvalidReason.entries.size)
    }

    @Test
    fun consumptionReportPreservesSummaryAndReliability() {
        val invalid =
            SegmentResult.Invalid(
                toEntryId = id,
                reason = ConsumptionInvalidReason.NoPreviousFullTank,
            )
        val report =
            ConsumptionReport(
                segments = listOf(invalid),
                validSegmentCount = 0,
                average = null,
                isReliable = false,
            )

        assertEquals(listOf(invalid), report.segments)
        assertEquals(0, report.validSegmentCount)
        assertNull(report.average)
        assertFalse(report.isReliable)
    }
}
