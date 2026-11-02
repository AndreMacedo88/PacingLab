plugins {
    alias(libs.plugins.pacinglab.android.library)
    alias(libs.plugins.pacinglab.hilt)
}

android {
    namespace = "app.pacinglab.core.homeassistant"
}

// The athlete's own Home Assistant server: REST for states and webhooks, the WebSocket API for statistics.
dependencies {
    api(projects.core.model)
    implementation(libs.okhttp)
    implementation(libs.kotlinx.coroutines.android)
}
