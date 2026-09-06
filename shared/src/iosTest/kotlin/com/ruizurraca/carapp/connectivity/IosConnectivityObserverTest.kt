package com.ruizurraca.carapp.connectivity

import kotlin.test.Test
import kotlin.test.assertEquals

class IosConnectivityObserverTest {
    @Test
    fun publishesThePlatformStateItStartsFrom() {
        assertEquals(false, IosConnectivityObserver(initiallyOnline = false) {}.isOnline.value)
        assertEquals(true, IosConnectivityObserver(initiallyOnline = true) {}.isOnline.value)
    }

    @Test
    fun publishesEveryLaterPlatformTransition() {
        var publish: ((Boolean) -> Unit)? = null
        val observer = IosConnectivityObserver(initiallyOnline = false) { listener -> publish = listener }
        val emit = checkNotNull(publish) { "the observer must register its platform listener eagerly" }

        emit(true)
        assertEquals(true, observer.isOnline.value, "an offline-to-online transition reaches the contract")

        emit(false)
        assertEquals(false, observer.isOnline.value, "and so does the transition back")
    }
}
