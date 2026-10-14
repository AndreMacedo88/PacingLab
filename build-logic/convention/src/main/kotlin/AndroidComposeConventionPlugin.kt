import app.pacinglab.buildlogic.library
import app.pacinglab.buildlogic.libs
import com.android.build.api.dsl.CommonExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.apply
import org.gradle.kotlin.dsl.dependencies
import org.gradle.kotlin.dsl.getByType

/** Enables Jetpack Compose. Apply after an Android application or library plugin. */
class AndroidComposeConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        apply(plugin = "org.jetbrains.kotlin.plugin.compose")
        extensions.getByType<CommonExtension>().buildFeatures.compose = true
        dependencies {
            val bom = platform(libs.library("androidx-compose-bom"))
            add("implementation", bom)
            add("testImplementation", bom)
            add("androidTestImplementation", bom)
            add("implementation", libs.library("androidx-compose-ui-tooling-preview"))
            add("debugImplementation", libs.library("androidx-compose-ui-tooling"))
            add("debugImplementation", libs.library("androidx-compose-ui-test-manifest"))
            add("testImplementation", libs.library("androidx-compose-ui-test-junit4"))
        }
    }
}
