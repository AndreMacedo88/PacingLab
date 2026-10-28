plugins {
    alias(libs.plugins.pacinglab.android.library)
    alias(libs.plugins.pacinglab.android.compose)
    alias(libs.plugins.pacinglab.android.screenshot)
}

android {
    namespace = "app.pacinglab.core.designsystem"
}

dependencies {
    implementation(libs.reorderable)
    api(libs.androidx.compose.foundation)
    api(libs.androidx.compose.material3)
    api(libs.androidx.compose.ui)
    implementation(projects.core.model)
}
