plugins {
    alias(libs.plugins.pacinglab.android.library)
    alias(libs.plugins.pacinglab.hilt)
}

android {
    namespace = "app.pacinglab.core.googlehealth"
}

dependencies {
    api(projects.core.model)
    implementation(libs.play.services.auth)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.coroutines.play.services)
}
