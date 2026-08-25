package com.ruizurraca.carapp

import com.ruizurraca.carapp.wiring.firebase.firebaseAppProviders

/** Builds the exported graph by delegating all provider construction to Firebase wiring. */
fun createSwiftAppGraph(isDebugBuild: Boolean): SwiftAppGraph {
    val providers = firebaseAppProviders()
    return buildAppGraph(isDebugBuild, providers)
}
