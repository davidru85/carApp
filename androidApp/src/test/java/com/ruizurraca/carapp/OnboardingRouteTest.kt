package com.ruizurraca.carapp

import kotlin.test.Test
import kotlin.test.assertEquals

class OnboardingRouteTest {
    @Test
    fun unknownAuthenticationNeverRoutes() {
        assertEquals(
            OnboardingDestination.WAITING,
            resolveOnboardingDestination(SessionPhase.UNKNOWN, vehicleCount = 0),
        )
        assertEquals(
            OnboardingDestination.WAITING,
            resolveOnboardingDestination(SessionPhase.UNKNOWN, vehicleCount = 3),
        )
    }

    @Test
    fun signedOutAuthenticationRoutesToWelcome() {
        assertEquals(
            OnboardingDestination.WELCOME,
            resolveOnboardingDestination(SessionPhase.SIGNED_OUT, vehicleCount = 0),
        )
    }

    @Test
    fun authenticatedOwnerWithoutVehiclesRoutesToFirstVehicleCreation() {
        listOf(SessionPhase.LOCAL, SessionPhase.ANONYMOUS, SessionPhase.PERMANENT).forEach { phase ->
            assertEquals(
                OnboardingDestination.FIRST_VEHICLE,
                resolveOnboardingDestination(phase, vehicleCount = 0),
            )
        }
    }

    @Test
    fun authenticatedOwnerWithVehiclesRoutesToVehicleList() {
        listOf(SessionPhase.LOCAL, SessionPhase.ANONYMOUS, SessionPhase.PERMANENT).forEach { phase ->
            assertEquals(
                OnboardingDestination.VEHICLE_LIST,
                resolveOnboardingDestination(phase, vehicleCount = 2),
            )
        }
    }
}
