package com.ruizurraca.carapp.feature.sample.data

import com.ruizurraca.carapp.feature.vehicle.domain.VehicleRepository

interface SampleDataSource

class DataViolation(
    val repository: VehicleRepository,
)
