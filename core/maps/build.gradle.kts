plugins {
    alias(libs.plugins.pacinglab.android.library)
    alias(libs.plugins.pacinglab.android.compose)
    alias(libs.plugins.pacinglab.android.screenshot)
}

android {
    namespace = "app.pacinglab.core.maps"
}

dependencies {
    api(projects.core.model)
    implementation(projects.core.analytics)
    implementation(projects.core.designsystem)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.maplibre.android)
}
