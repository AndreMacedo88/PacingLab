plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.hilt) apply false
    alias(libs.plugins.room) apply false
    alias(libs.plugins.roborazzi) apply false
    alias(libs.plugins.spotless)
}

// Directories Spotless must never walk into: generated output is written there during builds.
val formatExcludes = listOf("**/build/**", ".toolchain/**", ".gradle/**", ".kotlin/**", "fixtures-private/**")

fun sources(vararg patterns: String) = fileTree(rootDir) {
    include(*patterns)
    exclude(formatExcludes)
}

spotless {
    val ktlintVersion = libs.versions.ktlint.get()
    val composeRules = "io.nlopez.compose.rules:ktlint:${libs.versions.composeRules.get()}"
    // Kotlin's official coding conventions. Spotless does not pick the style up from .editorconfig.
    val ktlintStyle = mapOf("ktlint_code_style" to "intellij_idea")
    kotlin {
        target(sources("**/*.kt"))
        ktlint(ktlintVersion)
            .setEditorConfigPath(rootProject.file(".editorconfig"))
            .editorConfigOverride(ktlintStyle)
            .customRuleSets(listOf(composeRules))
    }
    kotlinGradle {
        target(sources("**/*.gradle.kts"))
        ktlint(ktlintVersion)
            .setEditorConfigPath(rootProject.file(".editorconfig"))
            .editorConfigOverride(ktlintStyle)
    }
    format("misc") {
        target(sources("**/*.md", "**/*.toml", "**/*.properties", "**/*.yml", ".gitignore", ".gitattributes", ".editorconfig"))
        trimTrailingWhitespace()
        endWithNewline()
    }
}
