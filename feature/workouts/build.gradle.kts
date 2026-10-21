plugins {
    alias(libs.plugins.pacinglab.android.feature)
}

android {
    namespace = "app.pacinglab.feature.workouts"
}

dependencies {
    implementation(projects.core.data)
    implementation(projects.core.maps)
}
