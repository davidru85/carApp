package com.ruizurraca.carapp.core.database

import app.cash.sqldelight.async.coroutines.await

internal suspend fun TestDatabase.insertVehicleForMutationTest(
    id: String = "vehicle-1",
    initialOdometerKm: Long = 100,
    currentOdometerKm: Long = initialOdometerKm,
) {
    database.databaseQueries.insertVehicleRow(
        id = id,
        ownerId = "LOCAL_OWNER",
        name = "Car",
        nameFold = "car",
        initialOdometerKm = initialOdometerKm,
        currentOdometerKm = currentOdometerKm,
        brand = null,
        model = null,
        fuelType = "GASOLINE",
        createdAt = 1,
        updatedAt = 1,
        serverUpdatedAt = null,
        deleted = 0,
        deletedAt = null,
        syncState = "PENDING",
        localRevision = 1,
        localMutationSeq = 1,
        schemaVersion = 1,
    )
}

internal suspend fun TestDatabase.insertFuelEntryForMutationTest(
    id: String,
    date: Long,
    createdAt: Long,
    odometerKm: Long,
    vehicleId: String = "vehicle-1",
    odometerInconsistent: Long = 0,
) {
    driver
        .execute(
            identifier = null,
            sql =
                """
                INSERT INTO fuel_entry(
                  id, ownerId, vehicleId, date, odometerKm, litersScaled, pricePerLiterScaled,
                  totalCostMinor, currency, isFullTank, hasMissedEntries, odometerInconsistent,
                  createdAt, updatedAt, serverUpdatedAt, deleted, deletedAt, syncState,
                  localRevision, localMutationSeq, schemaVersion
                ) VALUES (?, 'LOCAL_OWNER', ?, ?, ?, 1000, 1000,
                  100, 'EUR', 1, 0, ?, ?, 1, NULL, 0, NULL, 'PENDING', 1, 1, 1)
                """.trimIndent(),
            parameters = 6,
        ) {
            bindString(0, id)
            bindString(1, vehicleId)
            bindLong(2, date)
            bindLong(3, odometerKm)
            bindLong(4, odometerInconsistent)
            bindLong(5, createdAt)
        }.await()
}

internal suspend fun DatabaseMutations.insertFuelEntryForTest(
    id: String,
    date: Long,
    createdAt: Long,
    odometerKm: Long,
    vehicleId: String = "vehicle-1",
) {
    insertFuelEntry(
        id = id,
        ownerId = "LOCAL_OWNER",
        vehicleId = vehicleId,
        date = date,
        odometerKm = odometerKm,
        litersScaled = 1000,
        pricePerLiterScaled = 1000,
        totalCostMinor = 100,
        currency = "EUR",
        isFullTank = 1,
        hasMissedEntries = 0,
        notes = null,
        createdAt = createdAt,
        updatedAt = 1,
        serverUpdatedAt = null,
        deletedAt = null,
        syncState = "PENDING",
        localRevision = 1,
        localMutationSeq = 1,
        schemaVersion = 1,
    )
}

internal suspend fun TestDatabase.fuelEntryInconsistent(id: String): Long? =
    driver.nullableLong("SELECT odometerInconsistent FROM fuel_entry WHERE id = '$id'")

internal suspend fun TestDatabase.vehicleCurrentOdometer(id: String = "vehicle-1"): Long? =
    driver.nullableLong("SELECT currentOdometerKm FROM vehicle WHERE id = '$id'")
