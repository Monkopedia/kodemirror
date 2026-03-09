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
    alias(libs.plugins.bcv) apply false
    alias(libs.plugins.dokka)
}

dependencies {
    subprojects.forEach { subproject ->
        kover(subproject)
    }
}

// Add Dokka aggregation only for subprojects that apply the Dokka plugin
// (excludes :kodemirror-bom, :samples:editor, :kodemirror-test, etc.)
subprojects {
    pluginManager.withPlugin("org.jetbrains.dokka") {
        rootProject.dependencies.add("dokka", this@subprojects)
    }
}

tasks.register<Copy>("copyApiDocs") {
    dependsOn(":dokkaGenerate")
    from(layout.buildDirectory.dir("dokka/html"))
    into(layout.projectDirectory.dir("docs-site/docs/api"))
}

tasks.register("stripStableFromApiDumps") {
    description = "Strip Compose compiler \$stable fields from .api dump files"
    group = "verification"
    doLast {
        fileTree(rootDir) {
            include("*/api/*.api")
        }.forEach { file ->
            val original = file.readText()
            val filtered = original.lineSequence()
                .filter { !it.contains("\$stable") }
                .joinToString("\n")
            if (filtered != original) {
                file.writeText(filtered)
                logger.lifecycle("Stripped \$stable from ${file.relativeTo(rootDir)}")
            }
        }
    }
}

subprojects {
    pluginManager.withPlugin("org.jetbrains.kotlin.plugin.compose") {
        extensions.configure<org.jetbrains.kotlin.compose.compiler.gradle.ComposeCompilerGradlePluginExtension> {
            stabilityConfigurationFile.set(
                rootProject.layout.projectDirectory.file("compose-stability.conf")
            )
        }
    }
    tasks.matching { it.name == "apiDump" }.configureEach {
        finalizedBy(rootProject.tasks.named("stripStableFromApiDumps"))
    }
    // Strip $stable from build API output before apiCheck compares against committed files
    tasks.matching { it.name == "jvmApiCheck" }.configureEach {
        doFirst {
            project.fileTree(project.layout.buildDirectory.dir("api")) {
                include("*.api")
            }.forEach { file ->
                val original = file.readText()
                val filtered = original.lineSequence()
                    .filter { !it.contains("\$stable") }
                    .joinToString("\n")
                if (filtered != original) {
                    file.writeText(filtered)
                }
            }
        }
    }
}

tasks.register<Exec>("captureReferenceScreenshots") {
    description = "Capture CodeMirror 6 reference screenshots using Playwright"
    group = "verification"
    workingDir = file("reference-screenshots")
    commandLine("npx", "playwright", "test")
}
