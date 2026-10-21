plugins {
    alias(libs.plugins.pacinglab.android.application)
    alias(libs.plugins.pacinglab.android.compose)
    alias(libs.plugins.pacinglab.hilt)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "app.pacinglab"

    defaultConfig {
        applicationId = "app.pacinglab"
        versionCode = 1
        versionName = "0.1.0"
        manifestPlaceholders["appLabel"] = "PacingLab"
        // MapLibre ships native code for four ABIs; the S25 needs arm64, the emulator x86_64.
        ndk {
            abiFilters += listOf("arm64-v8a", "x86_64")
        }
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
            manifestPlaceholders["appLabel"] = "PacingLab Dev"
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
}

dependencies {
    implementation(projects.core.designsystem)
    implementation(projects.core.healthconnect)
    implementation(projects.core.maps)
    implementation(projects.core.model)
    implementation(projects.core.data)
    implementation(projects.feature.settings)
    implementation(projects.feature.workouts)

    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.core.splashscreen)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.navigation3)
    implementation(libs.androidx.navigation3.runtime)
    implementation(libs.androidx.navigation3.ui)
    implementation(libs.androidx.profileinstaller)
    implementation(libs.androidx.work.runtime)
    implementation(libs.androidx.hilt.work)
    implementation(libs.kotlinx.serialization.json)

    testImplementation(libs.junit4)
    testImplementation(libs.robolectric)
}
