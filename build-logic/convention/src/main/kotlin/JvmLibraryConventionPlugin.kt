import app.pacinglab.buildlogic.configureKotlinJvm
import app.pacinglab.buildlogic.library
import app.pacinglab.buildlogic.libs
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.tasks.testing.Test
import org.gradle.kotlin.dsl.apply
import org.gradle.kotlin.dsl.dependencies
import org.gradle.kotlin.dsl.withType

/** A plain Kotlin/JVM module with no Android dependencies, tested with JUnit Jupiter. */
class JvmLibraryConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        apply(plugin = "org.jetbrains.kotlin.jvm")
        configureKotlinJvm()
        tasks.withType<Test>().configureEach {
            useJUnitPlatform()
        }
        dependencies {
            add("testImplementation", platform(libs.library("junit-bom")))
            add("testImplementation", libs.library("junit-jupiter"))
            add("testRuntimeOnly", libs.library("junit-platform-launcher"))
            add("testImplementation", libs.library("kotest-property"))
            add("testImplementation", libs.library("assertk"))
            add("testImplementation", libs.library("kotlinx-coroutines-test"))
        }
    }
}
