plugins {
    alias(libs.plugins.pacinglab.android.library)
    alias(libs.plugins.pacinglab.hilt)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "app.pacinglab.core.healthconnect"
}

dependencies {
    api(projects.core.model)
    api(libs.androidx.health.connect.client)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.serialization.json)
}
