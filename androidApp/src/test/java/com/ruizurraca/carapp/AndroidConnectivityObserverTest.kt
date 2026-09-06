package com.ruizurraca.carapp

import kotlin.test.Test
import kotlin.test.assertEquals

class AndroidConnectivityObserverTest {
    @Test
    fun publishesThePlatformStateItStartsFrom() {
        assertEquals(false, AndroidConnectivityObserver(initiallyOnline = false) {}.isOnline.value)
        assertEquals(true, AndroidConnectivityObserver(initiallyOnline = true) {}.isOnline.value)
    }

    @Test
    fun publishesEveryLaterPlatformTransition() {
        var publish: ((Boolean) -> Unit)? = null
        val observer = AndroidConnectivityObserver(initiallyOnline = false) { listener -> publish = listener }
        val emit = checkNotNull(publish) { "the observer must register its platform listener eagerly" }

        emit(true)
        assertEquals(true, observer.isOnline.value, "an offline-to-online transition reaches the contract")

        emit(false)
        assertEquals(false, observer.isOnline.value, "and so does the transition back")
    }
}
