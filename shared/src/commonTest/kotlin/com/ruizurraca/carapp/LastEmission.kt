package com.ruizurraca.carapp

import kotlin.concurrent.Volatile

/** Mutable diagnostic value shared by the expectation collector and its caller. */
internal class LastEmission {
    @Volatile
    var value: String = "<no emissions>"
}
