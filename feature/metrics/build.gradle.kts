plugins {
    alias(libs.plugins.pacinglab.android.feature)
}

android {
    namespace = "app.pacinglab.feature.metrics"
}

dependencies {
    implementation(projects.core.data)
}
