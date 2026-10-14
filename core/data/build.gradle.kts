plugins {
    alias(libs.plugins.pacinglab.android.library)
    alias(libs.plugins.pacinglab.hilt)
}

android {
    namespace = "app.pacinglab.core.data"
}

dependencies {
    api(projects.core.model)
    api(projects.core.analytics)
    implementation(projects.core.database)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.lifecycle.process)
    implementation(libs.androidx.work.runtime)
    implementation(libs.androidx.hilt.work)
    ksp(libs.androidx.hilt.compiler)
    implementation(libs.kotlinx.coroutines.android)

    testImplementation(projects.core.testing)
    testImplementation(libs.androidx.work.testing)
    testImplementation(libs.androidx.sqlite.framework)
}
