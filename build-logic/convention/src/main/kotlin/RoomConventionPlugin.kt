import androidx.room3.gradle.RoomExtension
import app.pacinglab.buildlogic.library
import app.pacinglab.buildlogic.libs
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.apply
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.dependencies

/** Room 3 with the bundled SQLite driver. Exported schemas are committed for migration tests. */
class RoomConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        apply(plugin = "com.google.devtools.ksp")
        apply(plugin = "androidx.room3")
        extensions.configure<RoomExtension> {
            schemaDirectory("$projectDir/schemas")
        }
        dependencies {
            add("implementation", libs.library("androidx-room-runtime"))
            add("implementation", libs.library("androidx-sqlite-bundled"))
            add("ksp", libs.library("androidx-room-compiler"))
        }
    }
}
