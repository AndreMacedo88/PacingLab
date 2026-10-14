import app.pacinglab.buildlogic.configureAndroidCommon
import app.pacinglab.buildlogic.library
import app.pacinglab.buildlogic.libs
import com.android.build.api.dsl.LibraryExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.apply
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.dependencies

class AndroidLibraryConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        apply(plugin = "com.android.library")
        extensions.configure<LibraryExtension> {
            configureAndroidCommon(this)
        }
        dependencies {
            add("testImplementation", libs.library("junit4"))
            add("testImplementation", libs.library("assertk"))
            add("testImplementation", libs.library("kotlinx-coroutines-test"))
            add("testImplementation", libs.library("turbine"))
            add("testImplementation", libs.library("robolectric"))
            add("testImplementation", libs.library("kotest-property"))
            add("testImplementation", libs.library("androidx-test-core"))
        }
    }
}
