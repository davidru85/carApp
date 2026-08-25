package com.ruizurraca.carapp.wiring.firebase

import com.ruizurraca.carapp.SwiftAppGraph
import com.ruizurraca.carapp.buildAppGraph
import kotlin.test.Test
import kotlin.test.assertIs

class FirebaseAppProvidersTest {
    @Test
    fun providerFactoryBuildsTheSharedGraphWithoutGlobalRegistration() {
        val graph = buildAppGraph(isDebugBuild = true, providers = firebaseAppProviders())

        assertIs<SwiftAppGraph>(graph)
    }
}
