package com.ruizurraca.carapp

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

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

    @Test
    fun firstVehicleCreationWaitsUntilTheVehicleListIsKnown() {
        assertFalse(
            shouldPresentFirstVehicleCreation(
                isVehicleListKnown = false,
                vehicleCount = 0,
                alreadyPresented = false,
            ),
        )
        assertTrue(
            shouldPresentFirstVehicleCreation(
                isVehicleListKnown = true,
                vehicleCount = 0,
                alreadyPresented = false,
            ),
        )
    }

    @Test
    fun anUnreadableListIsDistinguishedFromAListThatIsStillArriving() {
        assertEquals(VehicleListGate.WAITING, vehicleListGate(isLoading = true, hasMessage = false))
        assertEquals(VehicleListGate.UNREADABLE, vehicleListGate(isLoading = true, hasMessage = true))
    }

    @Test
    fun aKnownListIsResolvedEvenWhileItCarriesATransientMessage() {
        assertEquals(VehicleListGate.RESOLVED, vehicleListGate(isLoading = false, hasMessage = false))
        assertEquals(VehicleListGate.RESOLVED, vehicleListGate(isLoading = false, hasMessage = true))
    }

    @Test
    fun ownerScopedNavigationIsResetWhenAKnownListBecomesUnknownWithoutAnError() {
        assertTrue(
            shouldResetOwnerScopedNavigation(
                gate = VehicleListGate.WAITING,
                previousGate = VehicleListGate.RESOLVED,
            ),
        )
    }

    @Test
    fun ownerScopedNavigationSurvivesAReadFailureAndAnUnchangedGate() {
        assertFalse(
            shouldResetOwnerScopedNavigation(
                gate = VehicleListGate.UNREADABLE,
                previousGate = VehicleListGate.RESOLVED,
            ),
        )
        assertFalse(
            shouldResetOwnerScopedNavigation(
                gate = VehicleListGate.RESOLVED,
                previousGate = VehicleListGate.RESOLVED,
            ),
        )
        assertFalse(
            shouldResetOwnerScopedNavigation(
                gate = VehicleListGate.WAITING,
                previousGate = VehicleListGate.WAITING,
            ),
        )
    }

    @Test
    fun firstVehicleCreationIsPresentedOnceAndNeverForAnOwnerThatHasVehicles() {
        assertFalse(
            shouldPresentFirstVehicleCreation(
                isVehicleListKnown = true,
                vehicleCount = 1,
                alreadyPresented = false,
            ),
        )
        assertFalse(
            shouldPresentFirstVehicleCreation(
                isVehicleListKnown = true,
                vehicleCount = 0,
                alreadyPresented = true,
            ),
        )
    }
}
