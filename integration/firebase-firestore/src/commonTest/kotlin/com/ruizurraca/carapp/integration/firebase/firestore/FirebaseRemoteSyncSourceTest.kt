package com.ruizurraca.carapp.integration.firebase.firestore

import kotlin.test.Test
import kotlin.test.assertEquals

class FirebaseRemoteSyncSourceTest {
    @Test
    fun constructionDisablesPersistentFirestoreCaching() {
        val gateway = RecordingFirestoreGateway()

        FirebaseRemoteSyncSource(gateway)

        assertEquals(1, gateway.memoryOnlyConfigurationCount)
    }
}

private class RecordingFirestoreGateway : FirestoreGateway {
    var memoryOnlyConfigurationCount = 0

    override fun configureMemoryOnlyCache() {
        memoryOnlyConfigurationCount += 1
    }
}
