plugins {
    alias(libs.plugins.pacinglab.android.feature)
}

android {
    namespace = "app.pacinglab.feature.today"
}

dependencies {
    implementation(projects.core.ui)
    implementation(projects.core.data)
    implementation(projects.core.maps)
}
