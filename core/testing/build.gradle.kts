plugins {
    alias(libs.plugins.pacinglab.android.library)
}

android {
    namespace = "app.pacinglab.core.testing"
}

dependencies {
    api(projects.core.model)
    api(libs.junit4)
    api(libs.kotlinx.coroutines.test)
}
