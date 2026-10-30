plugins {
    alias(libs.plugins.pacinglab.android.library)
    alias(libs.plugins.pacinglab.android.compose)
}

android {
    namespace = "app.pacinglab.core.ui"
}

// Composables that show analytics results and are shared by several screens.
dependencies {
    api(projects.core.analytics)
    implementation(projects.core.designsystem)
}
