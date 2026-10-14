import app.pacinglab.buildlogic.library
import app.pacinglab.buildlogic.libs
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.apply
import org.gradle.kotlin.dsl.dependencies

/** A feature module: one area of the UI with its screens and view models. */
class AndroidFeatureConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        apply(plugin = "pacinglab.android.library")
        apply(plugin = "pacinglab.android.compose")
        apply(plugin = "pacinglab.android.screenshot")
        apply(plugin = "pacinglab.hilt")
        apply(plugin = "org.jetbrains.kotlin.plugin.serialization")
        dependencies {
            add("implementation", project(":core:designsystem"))
            add("implementation", project(":core:model"))
            add("implementation", libs.library("androidx-activity-compose"))
            add("implementation", libs.library("androidx-lifecycle-runtime-compose"))
            add("implementation", libs.library("androidx-lifecycle-viewmodel-compose"))
            add("implementation", libs.library("androidx-hilt-lifecycle-viewmodel-compose"))
            add("implementation", libs.library("androidx-navigation3-runtime"))
            add("testImplementation", project(":core:testing"))
        }
    }
}
