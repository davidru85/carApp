package com.ruizurraca.carapp.buildlogic

import java.io.File
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PlatformHostContractTest {
    private val repositoryRoot = File(checkNotNull(System.getProperty("carapp.repoRoot")))

    @Test
    fun androidHostBindsThePersistentGraphToSharedStateHolders() {
        // The graph is process-scoped (`§9.1`): a WorkManager worker has no Activity to borrow one
        // from, so construction, the real platform adapters and the release path are asserted against
        // the files that now own them instead of against the Activity alone.
        val host =
            repositoryRoot
                .resolve("androidApp/src/main/java/com/ruizurraca/carapp/MainActivity.kt")
                .readText()
        val graphOwner =
            repositoryRoot
                .resolve("androidApp/src/main/java/com/ruizurraca/carapp/AndroidAppGraph.kt")
                .readText()
        val application =
            repositoryRoot
                .resolve("androidApp/src/main/java/com/ruizurraca/carapp/CarAppApplication.kt")
                .readText()
        val foregroundReturn =
            repositoryRoot
                .resolve("androidApp/src/main/java/com/ruizurraca/carapp/AnonymousReminderCopy.kt")
                .readText()
        val foregroundDuration =
            repositoryRoot
                .resolve("androidApp/src/main/java/com/ruizurraca/carapp/AndroidForegroundDuration.kt")
                .readText()
        val english = repositoryRoot.resolve("androidApp/src/main/res/values/strings.xml").readText()
        val spanish = repositoryRoot.resolve("androidApp/src/main/res/values-es/strings.xml").readText()

        assertTrue(graphOwner.contains("firebaseAppProviders("))
        assertTrue(graphOwner.contains("getDatabasePath(DATABASE_FILE_NAME).absolutePath"))
        assertTrue(graphOwner.contains("buildAppGraph("))
        assertTrue(application.contains("AndroidAppGraph.install(this)"))
        assertTrue(host.contains("graph.vehicleListStateHolder(scope = viewModelScope)"))
        assertTrue(host.contains("graph.vehicleFormStateHolder(scope = viewModelScope, vehicleId = vehicleId)"))
        assertTrue(host.contains("NavHost("))
        assertTrue(host.contains("VehicleRoutes.LIST"))
        assertTrue(host.contains("VehicleRoutes.CREATE"))
        assertTrue(host.contains("VehicleRoutes.EDIT"))
        assertTrue(host.contains("VehicleRoutes.DETAIL"))
        assertTrue(host.contains("setName"))
        assertTrue(host.contains("stateHolder::save"))
        assertTrue(host.contains("stateHolder::refresh"))
        // The graph outlives the Activity, so the Activity MUST NOT close it: releasing it on
        // `onCleared` would leave the process-scoped graph holding a released driver.
        assertFalse(host.contains("graph.close()"))
        // `§9.8` fires the foreground trigger on a cold start or after more than
        // `FOREGROUND_RESUME_THRESHOLD_MS`, never on an Activity recreation. The measurement is
        // therefore process-scoped; holding it in the composition made a recreation report `null`,
        // which the shared holder reads as a cold start.
        assertTrue(foregroundDuration.contains("internal object AndroidForegroundTracking"))
        assertTrue(foregroundReturn.contains("AndroidForegroundTracking.duration"))
        assertFalse(foregroundReturn.contains("remember { AndroidForegroundDuration() }"))
        assertFalse(host.contains("setFuelType"))
        assertFalse(host.contains("Greeting"))
        assertTrue(english.contains("name=\"vehicle_list_title\""))
        assertTrue(spanish.contains("name=\"vehicle_list_title\""))
    }

    @Test
    fun iosHostBindsThePersistentGraphToSkieStateFlows() {
        val app = repositoryRoot.resolve("iosApp/carAppApp.swift").readText()
        val model = repositoryRoot.resolve("iosApp/WalkingSkeletonModel.swift").readText()
        val view = repositoryRoot.resolve("iosApp/ContentView.swift").readText()
        val english = repositoryRoot.resolve("iosApp/en.lproj/Localizable.strings").readText()
        val spanish = repositoryRoot.resolve("iosApp/es.lproj/Localizable.strings").readText()
        val xcodeProject = repositoryRoot.resolve("iosApp/carApp.xcodeproj/project.pbxproj").readText()

        assertTrue(app.contains("createSwiftAppGraph(isDebugBuild:"))
        assertTrue(model.contains("sessionStateHolder()"))
        assertTrue(model.contains("vehicleFormStateHolder(vehicleId: nil)"))
        assertTrue(model.contains("vehicleListStateHolder()"))
        assertTrue(model.contains("for await state in sessionStateHolder.state"))
        assertTrue(model.contains("for await state in vehicleFormStateHolder.state"))
        assertTrue(model.contains("for await state in vehicleListStateHolder.state"))
        assertTrue(model.contains("startAnonymousSignIn()"))
        assertTrue(model.contains("setName(value:"))
        assertTrue(model.contains("save()"))
        assertTrue(model.contains("refresh()"))
        assertTrue(app.contains("private let graph: SwiftAppGraph"))
        assertFalse(view.contains("Greeting"))
        assertTrue(english.contains("\"walking_skeleton_title\""))
        assertTrue(spanish.contains("\"walking_skeleton_title\""))
        assertTrue(xcodeProject.contains("en.lproj in Resources"))
        assertTrue(xcodeProject.contains("es.lproj in Resources"))
    }
}
