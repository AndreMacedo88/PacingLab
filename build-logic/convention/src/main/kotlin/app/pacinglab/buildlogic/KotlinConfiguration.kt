package app.pacinglab.buildlogic

import com.android.build.api.dsl.CommonExtension
import org.gradle.api.JavaVersion
import org.gradle.api.Project
import org.gradle.api.plugins.JavaPluginExtension
import org.gradle.api.tasks.testing.Test
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.withType
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinJvmCompile

// Bytecode level shared by every module. Android desugaring fully supports Java 17.
private val PROJECT_JAVA_VERSION = JavaVersion.VERSION_17
private val PROJECT_JVM_TARGET = JvmTarget.JVM_17

/** Settings shared by every Android module, application or library. */
internal fun Project.configureAndroidCommon(extension: CommonExtension) {
    extension.apply {
        compileSdk = libs.intVersion("androidCompileSdk")
        defaultConfig.minSdk = libs.intVersion("androidMinSdk")
        defaultConfig.testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        compileOptions.sourceCompatibility = PROJECT_JAVA_VERSION
        compileOptions.targetCompatibility = PROJECT_JAVA_VERSION
        testOptions.unitTests.isIncludeAndroidResources = true
    }
    tasks.withType<Test>().configureEach {
        // Robolectric reaches into JDK internals that Java 17+ no longer exposes by default.
        jvmArgs(
            "--add-opens=java.base/java.io=ALL-UNNAMED",
            "--add-exports=java.base/jdk.internal.access=ALL-UNNAMED",
        )
    }
    configureKotlinCompiler()
}

internal fun Project.configureKotlinJvm() {
    extensions.configure<JavaPluginExtension> {
        sourceCompatibility = PROJECT_JAVA_VERSION
        targetCompatibility = PROJECT_JAVA_VERSION
    }
    configureKotlinCompiler()
}

private fun Project.configureKotlinCompiler() {
    // Warnings fail the build unless -Ppacinglab.warningsAsErrors=false is passed.
    val warningsAsErrors = providers
        .gradleProperty("pacinglab.warningsAsErrors")
        .map(String::toBoolean)
        .orElse(true)
    tasks.withType<KotlinJvmCompile>().configureEach {
        compilerOptions {
            jvmTarget.set(PROJECT_JVM_TARGET)
            allWarningsAsErrors.set(warningsAsErrors)
        }
    }
}
