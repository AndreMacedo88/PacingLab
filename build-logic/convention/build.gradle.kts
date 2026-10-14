plugins {
    `kotlin-dsl`
}

group = "app.pacinglab.buildlogic"

kotlin {
    jvmToolchain(21)
}

dependencies {
    compileOnly(libs.android.gradle.plugin)
    compileOnly(libs.kotlin.gradle.plugin)
    compileOnly(libs.compose.compiler.gradle.plugin)
    compileOnly(libs.ksp.gradle.plugin)
    compileOnly(libs.hilt.gradle.plugin)
    compileOnly(libs.room.gradle.plugin)
    compileOnly(libs.roborazzi.gradle.plugin)
}

tasks.validatePlugins {
    enableStricterValidation = true
    failOnWarning = true
}

gradlePlugin {
    plugins {
        register("androidApplication") {
            id = libs.plugins.pacinglab.android.application
                .get()
                .pluginId
            implementationClass = "AndroidApplicationConventionPlugin"
        }
        register("androidLibrary") {
            id = libs.plugins.pacinglab.android.library
                .get()
                .pluginId
            implementationClass = "AndroidLibraryConventionPlugin"
        }
        register("androidCompose") {
            id = libs.plugins.pacinglab.android.compose
                .get()
                .pluginId
            implementationClass = "AndroidComposeConventionPlugin"
        }
        register("androidFeature") {
            id = libs.plugins.pacinglab.android.feature
                .get()
                .pluginId
            implementationClass = "AndroidFeatureConventionPlugin"
        }
        register("androidScreenshot") {
            id = libs.plugins.pacinglab.android.screenshot
                .get()
                .pluginId
            implementationClass = "AndroidScreenshotConventionPlugin"
        }
        register("jvmLibrary") {
            id = libs.plugins.pacinglab.jvm.library
                .get()
                .pluginId
            implementationClass = "JvmLibraryConventionPlugin"
        }
        register("hilt") {
            id = libs.plugins.pacinglab.hilt
                .get()
                .pluginId
            implementationClass = "HiltConventionPlugin"
        }
        register("room") {
            id = libs.plugins.pacinglab.room
                .get()
                .pluginId
            implementationClass = "RoomConventionPlugin"
        }
    }
}
