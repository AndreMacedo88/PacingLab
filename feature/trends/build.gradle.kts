plugins {
    alias(libs.plugins.pacinglab.android.feature)
}

android {
    namespace = "app.pacinglab.feature.trends"
}

dependencies {
    implementation(projects.core.ui)
    implementation(projects.core.data)
}
