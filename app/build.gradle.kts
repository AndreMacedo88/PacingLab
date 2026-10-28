import java.util.Properties

plugins {
    alias(libs.plugins.pacinglab.android.application)
    alias(libs.plugins.pacinglab.android.compose)
    alias(libs.plugins.pacinglab.hilt)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.baselineprofile)
}

// The release key and its passwords live outside git, in .secrets/. Without them (as on CI) the release
// build is left unsigned.
val releaseSigning = rootProject.file(".secrets/release-signing.properties").takeIf { it.exists() }?.let { file ->
    Properties().apply { file.inputStream().use(::load) }
}

android {
    namespace = "app.pacinglab"

    signingConfigs {
        if (releaseSigning != null) {
            create("release") {
                storeFile = rootProject.file(releaseSigning.getProperty("storeFile"))
                storePassword = releaseSigning.getProperty("storePassword")
                keyAlias = releaseSigning.getProperty("keyAlias")
                keyPassword = releaseSigning.getProperty("keyPassword")
            }
        }
    }

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
            signingConfig = signingConfigs.findByName("release")
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
    implementation(projects.feature.metrics)
    implementation(projects.feature.performance)
    implementation(projects.feature.settings)
    implementation(projects.feature.sleep)
    implementation(projects.feature.today)
    implementation(projects.feature.trends)
    implementation(projects.feature.workouts)

    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.core.splashscreen)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.navigation3)
    implementation(libs.androidx.navigation3.runtime)
    implementation(libs.androidx.navigation3.ui)
    implementation(libs.androidx.profileinstaller)
    baselineProfile(projects.baselineprofile)
    implementation(libs.androidx.work.runtime)
    implementation(libs.androidx.hilt.work)
    implementation(libs.kotlinx.serialization.json)

    testImplementation(libs.junit4)
    testImplementation(libs.robolectric)
}
