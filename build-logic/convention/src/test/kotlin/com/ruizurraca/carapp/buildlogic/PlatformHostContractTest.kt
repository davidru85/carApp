package com.ruizurraca.carapp.buildlogic

import java.io.File
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PlatformHostContractTest {
    private val repositoryRoot = File(checkNotNull(System.getProperty("carapp.repoRoot")))

    @Test
    fun androidHostBindsThePersistentGraphToSharedStateHolders() {
        val host =
            repositoryRoot
                .resolve("androidApp/src/main/java/com/ruizurraca/carapp/MainActivity.kt")
                .readText()
        val english = repositoryRoot.resolve("androidApp/src/main/res/values/strings.xml").readText()
        val spanish = repositoryRoot.resolve("androidApp/src/main/res/values-es/strings.xml").readText()

        assertTrue(host.contains("firebaseAppProviders("))
        assertTrue(host.contains("getDatabasePath(DATABASE_FILE_NAME).absolutePath"))
        assertTrue(host.contains("buildAppGraph("))
        assertTrue(host.contains("sessionStateHolder()"))
        assertTrue(host.contains("vehicleFormStateHolder(vehicleId = null)"))
        assertTrue(host.contains("vehicleListStateHolder()"))
        assertTrue(host.contains("startAnonymousSignIn()"))
        assertTrue(host.contains("setName"))
        assertTrue(host.contains("save()"))
        assertTrue(host.contains("refresh()"))
        assertTrue(host.contains("graph.close()"))
        assertFalse(host.contains("Greeting"))
        assertTrue(english.contains("name=\"walking_skeleton_title\""))
        assertTrue(spanish.contains("name=\"walking_skeleton_title\""))
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
        assertTrue(model.contains("graph.close()"))
        assertFalse(view.contains("Greeting"))
        assertTrue(english.contains("\"walking_skeleton_title\""))
        assertTrue(spanish.contains("\"walking_skeleton_title\""))
        assertTrue(xcodeProject.contains("Localizable.strings in Resources"))
    }
}
