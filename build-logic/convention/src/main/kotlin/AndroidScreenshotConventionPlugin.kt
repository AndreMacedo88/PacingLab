import app.pacinglab.buildlogic.library
import app.pacinglab.buildlogic.libs
import io.github.takahirom.roborazzi.RoborazziExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.tasks.testing.Test
import org.gradle.kotlin.dsl.apply
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.dependencies
import org.gradle.kotlin.dsl.withType

/**
 * Screenshot tests rendered on the JVM with Robolectric and compared by Roborazzi.
 *
 * Reference images live in src/test/screenshots and are committed. Record them with
 * `./gradlew recordRoborazziDebug` and check against them with `./gradlew verifyRoborazziDebug`.
 */
class AndroidScreenshotConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        apply(plugin = "io.github.takahirom.roborazzi")
        extensions.configure<RoborazziExtension> {
            outputDir.set(layout.projectDirectory.dir("src/test/screenshots"))
        }
        tasks.withType<Test>().configureEach {
            systemProperty("robolectric.graphicsMode", "NATIVE")
            systemProperty("robolectric.pixelCopyRenderMode", "hardware")
        }
        dependencies {
            add("testImplementation", libs.library("robolectric"))
            add("testImplementation", libs.library("roborazzi"))
            add("testImplementation", libs.library("roborazzi-compose"))
            add("testImplementation", libs.library("roborazzi-junit-rule"))
            add("testImplementation", libs.library("androidx-compose-ui-test-junit4"))
            // Compose's test artifacts still pull Espresso 3.5, which fails on the newest Robolectric SDKs.
            add("testImplementation", libs.library("androidx-test-espresso-core"))
        }
    }
}
