plugins {
    alias(libs.plugins.kotlinMultiplatform) apply false
    alias(libs.plugins.jetbrainsCompose) apply false
    alias(libs.plugins.compose.compiler) apply false
    alias(libs.plugins.atomicfu) apply false
    alias(libs.plugins.serialization) apply false
    alias(libs.plugins.ktlint) apply false
    alias(libs.plugins.spotless) apply false
    alias(libs.plugins.roborazzi) apply false
}

tasks.register<Exec>("captureReferenceScreenshots") {
    description = "Capture CodeMirror 6 reference screenshots using Playwright"
    group = "verification"
    workingDir = file("reference-screenshots")
    commandLine("npx", "playwright", "test")
}
