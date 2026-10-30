// E4-03 (D-252, ADR-0253): Macrobenchmark against the :androidApp `benchmark` build type.
plugins {
    id("carapp.android.benchmark")
}

android {
    targetProjectPath = ":androidApp"
    // The test APK instruments itself, so it can kill and restart the measured app between iterations.
    experimentalProperties["android.experimental.self-instrumenting"] = true

    buildTypes {
        // Pairs with the :androidApp build type of the same name. Only the test APK is debuggable.
        create("benchmark") {
            isDebuggable = true
            signingConfig = signingConfigs.getByName("debug")
            matchingFallbacks += listOf("release")
        }
    }
}

androidComponents {
    // Only the benchmark variant is built: a debug variant would measure the debuggable app.
    beforeVariants(selector().all()) { variantBuilder ->
        variantBuilder.enable = variantBuilder.buildType == "benchmark"
    }
}

dependencies {
    implementation(libs.androidx.benchmark.macro.junit4)
    implementation(libs.androidx.test.uiautomator)
    implementation(libs.androidx.test.ext.junit)
    implementation(libs.androidx.test.runner)
}
