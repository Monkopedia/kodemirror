plugins {
    alias(libs.plugins.kotlinMultiplatform) apply false
    alias(libs.plugins.jetbrainsCompose) apply false
    alias(libs.plugins.compose.compiler) apply false
    alias(libs.plugins.atomicfu) apply false
    alias(libs.plugins.serialization) apply false
    alias(libs.plugins.ktlint) apply false
    alias(libs.plugins.spotless) apply false
    alias(libs.plugins.roborazzi) apply false
    alias(libs.plugins.kover)
    alias(libs.plugins.dokka)
}

dependencies {
    subprojects.forEach { subproject ->
        kover(subproject)
        dokka(subproject)
    }
}

tasks.register<Copy>("copyApiDocs") {
    dependsOn(":dokkaGenerate")
    from(layout.buildDirectory.dir("dokka/html"))
    into(layout.projectDirectory.dir("docs-site/docs/api"))
}

tasks.register<Exec>("captureReferenceScreenshots") {
    description = "Capture CodeMirror 6 reference screenshots using Playwright"
    group = "verification"
    workingDir = file("reference-screenshots")
    commandLine("npx", "playwright", "test")
}
