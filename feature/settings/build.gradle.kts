plugins {
    alias(libs.plugins.pacinglab.android.feature)
}

android {
    namespace = "app.pacinglab.feature.settings"
}

dependencies {
    implementation(projects.core.googlehealth)
    implementation(projects.core.healthconnect)
    implementation(projects.core.data)
}
