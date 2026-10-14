plugins {
    alias(libs.plugins.pacinglab.android.library)
    alias(libs.plugins.pacinglab.hilt)
    alias(libs.plugins.pacinglab.room)
}

android {
    namespace = "app.pacinglab.core.database"
}

dependencies {
    api(projects.core.model)
    implementation(libs.kotlinx.coroutines.android)

    testImplementation(libs.androidx.room.testing)
}
