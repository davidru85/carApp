package com.ruizurraca.carapp.core.database

import app.cash.sqldelight.async.coroutines.awaitAsOneOrNull
import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import kotlinx.coroutines.flow.Flow
import kotlin.coroutines.EmptyCoroutineContext

fun DatabaseQueries.observeVehicles(): Flow<List<Vehicle>> =
    selectAllVehicles().asFlow().mapToList(EmptyCoroutineContext)

suspend fun DatabaseQueries.vehicleById(id: String): Vehicle? = selectVehicleById(id).awaitAsOneOrNull()
