plugins {
    alias(libs.plugins.pacinglab.android.feature)
}

android {
    namespace = "app.pacinglab.feature.sleep"
}

dependencies {
    implementation(projects.core.data)
}
