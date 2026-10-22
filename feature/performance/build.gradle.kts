plugins {
    alias(libs.plugins.pacinglab.android.feature)
}

android {
    namespace = "app.pacinglab.feature.performance"
}

dependencies {
    implementation(projects.core.data)
}
