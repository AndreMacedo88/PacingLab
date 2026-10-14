import app.pacinglab.buildlogic.configureAndroidCommon
import app.pacinglab.buildlogic.intVersion
import app.pacinglab.buildlogic.libs
import com.android.build.api.dsl.ApplicationExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.apply
import org.gradle.kotlin.dsl.configure

class AndroidApplicationConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        apply(plugin = "com.android.application")
        extensions.configure<ApplicationExtension> {
            configureAndroidCommon(this)
            defaultConfig.targetSdk = libs.intVersion("androidTargetSdk")
            lint.apply {
                checkDependencies = true
                abortOnError = true
                warningsAsErrors = false
            }
        }
    }
}
