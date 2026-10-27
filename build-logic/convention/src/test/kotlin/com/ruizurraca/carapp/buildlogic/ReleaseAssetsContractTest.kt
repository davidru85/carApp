package com.ruizurraca.carapp.buildlogic

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * `E4-04` (`D-244`): the release assets the app actually ships — the adaptive launcher icon with its
 * three layers, the light-only launch background, and the three iOS icon appearances — plus the
 * decisions that must not drift back: no splash library, and a `MainActivity` that leaves the launch
 * theme before it draws.
 *
 * Native resources are exempt from the TDD order (`docs/SPECIFICATION.md §11`); this suite is the check
 * that keeps them present and consistent.
 */
class ReleaseAssetsContractTest {
    private val repositoryRoot = File(checkNotNull(System.getProperty("carapp.repoRoot")))

    @Test
    fun theAdaptiveLauncherIconHasItsThreeLayersAndTheManifestNamesIt() {
        val manifest = read("androidApp/src/main/AndroidManifest.xml")
        val adaptiveIcon = read("androidApp/src/main/res/mipmap-anydpi-v26/ic_launcher.xml")
        val roundIcon = read("androidApp/src/main/res/mipmap-anydpi-v26/ic_launcher_round.xml")
        val colors = read("androidApp/src/main/res/values/colors.xml")

        assertTrue(manifest.contains("android:icon=\"@mipmap/ic_launcher\""))
        assertTrue(manifest.contains("android:roundIcon=\"@mipmap/ic_launcher_round\""))
        listOf(adaptiveIcon, roundIcon).forEach { icon ->
            assertTrue(icon.contains("android:drawable=\"@color/ic_launcher_background\""), icon)
            assertTrue(icon.contains("android:drawable=\"@drawable/ic_launcher_foreground\""), icon)
            assertTrue(icon.contains("android:drawable=\"@drawable/ic_launcher_monochrome\""), icon)
        }
        assertTrue(colors.contains("ic_launcher_background"))
        assertTrue(read("androidApp/src/main/res/drawable/ic_launcher_foreground.xml").contains("android:viewportWidth=\"432\""))
        assertTrue(read("androidApp/src/main/res/drawable/ic_launcher_monochrome.xml").isNotEmpty())
    }

    @Test
    fun theLaunchScreenIsTheLightFirstScreenWithTheCircleLogo() {
        val launchTheme = read("androidApp/src/main/res/values/themes.xml")
        val launchThemeV31 = read("androidApp/src/main/res/values-v31/themes.xml")
        val launchBackground = read("androidApp/src/main/res/drawable/launch_background.xml")
        val mainActivity =
            read("androidApp/src/main/java/com/ruizurraca/carapp/MainActivity.kt")
        val infoPlist = read("iosApp/Info.plist")

        assertTrue(launchTheme.contains("launch_background"))
        assertTrue(launchThemeV31.contains("#FEF7FF"))
        assertTrue(launchThemeV31.contains("@mipmap/ic_launcher"))
        // The layer list names the colour resource, whose value is the light surface the app draws.
        assertTrue(launchBackground.contains("@color/launch_background"))
        assertTrue(read("androidApp/src/main/res/values/colors.xml").contains("#FEF7FF"))
        assertTrue(launchBackground.contains("launch_logo"))
        // The generated resource id is the Kotlin spelling of `Theme.CarApp`.
        assertTrue(mainActivity.contains("R.style.Theme_CarApp"), mainActivity.lines().first())
        // `D-244`: iOS keeps the system default background, so no colour set is named.
        assertTrue(infoPlist.contains("UIImageName"))
        assertTrue(infoPlist.contains("LaunchLogo"))
        assertFalse(infoPlist.substringAfter("<key>UILaunchScreen</key>").substringBefore("</dict>").contains("UIColorName"))
        assertFalse(File(repositoryRoot, "androidApp/src/main/res/values-night/themes.xml").exists())
    }

    @Test
    fun theIosIconCarriesTheThreeOpaqueAppearances() {
        val catalogue = read("iosApp/Assets.xcassets/AppIcon.appiconset/Contents.json")

        assertTrue(catalogue.contains("\"size\" : \"1024x1024\"") || catalogue.contains("\"size\": \"1024x1024\""))
        listOf("AppIcon-Any.png", "AppIcon-Dark.png", "AppIcon-Tinted.png").forEach { name ->
            val file = File(repositoryRoot, "iosApp/Assets.xcassets/AppIcon.appiconset/$name")
            assertTrue(file.isFile, "$name must exist")
        }
        assertEquals(
            2,
            Regex("\"appearances\"").findAll(catalogue).count(),
            "the dark and tinted appearances must be declared beside the universal one",
        )
        assertTrue(catalogue.contains("\"universal\""))
        assertTrue(
            File(repositoryRoot, "iosApp/Assets.xcassets/LaunchLogo.imageset/LaunchLogo.svg").isFile,
            "the launch logo must be present as a vector",
        )
    }

    @Test
    fun noSplashLibraryIsAdded() {
        val catalog = read("gradle/libs.versions.toml")
        val androidBuild = read("androidApp/build.gradle.kts")

        assertFalse(catalog.contains("core-splashscreen"))
        assertFalse(androidBuild.contains("splashscreen"))
    }

    private fun read(path: String): String {
        val file = repositoryRoot.resolve(path)
        assertTrue(file.isFile, "$path must exist")
        return file.readText()
    }
}
