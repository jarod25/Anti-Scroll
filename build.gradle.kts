import com.diffplug.spotless.LineEnding

// Top-level build file where you can add configuration options common to all sub-projects/modules.
plugins {
    alias(libs.plugins.spotless)
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.hilt) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.room) apply false
}

val kotlinSourceTrees = subprojects.map { module ->
    module.fileTree("src") {
        include("**/*.kt")
    }
}

val kotlinGradleScripts = files(
    file("build.gradle.kts"),
    file("settings.gradle.kts"),
    subprojects.map { module -> module.file("build.gradle.kts") },
)

spotless {
    lineEndings = LineEnding.UNIX

    kotlin {
        target(kotlinSourceTrees)
        ktlint(libs.versions.ktlint.get())
        trimTrailingWhitespace()
        endWithNewline()
    }

    kotlinGradle {
        target(kotlinGradleScripts)
        ktlint(libs.versions.ktlint.get())
        trimTrailingWhitespace()
        endWithNewline()
    }
}
