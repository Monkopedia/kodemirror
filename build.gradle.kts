plugins {
    alias(libs.plugins.androidLibrary) apply false
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
    alias(libs.plugins.vanniktech.publish) apply false
}

dependencies {
    subprojects.forEach { subproject ->
        kover(subproject)
    }
}

// Kotlin 2.4.10 defaults the managed Node.js to v25, which breaks
// :kotlinWasmNpmInstall / :kotlinNpmInstall. Pin the JS and wasm Node
// toolchains to a known-good LTS. Must use the EnvSpec API + version.set()
// — the deprecated NodeJsRootExtension.version= setter fails under -Werror.
plugins.withType<org.jetbrains.kotlin.gradle.targets.js.nodejs.NodeJsPlugin> {
    the<org.jetbrains.kotlin.gradle.targets.js.nodejs.NodeJsEnvSpec>().version.set("22.11.0")
}
plugins.withType<org.jetbrains.kotlin.gradle.targets.wasm.nodejs.WasmNodeJsPlugin> {
    the<org.jetbrains.kotlin.gradle.targets.wasm.nodejs.WasmNodeJsEnvSpec>().version.set("22.11.0")
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
            stabilityConfigurationFiles.add(
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

tasks.register<Exec>("runGapAnalysis") {
    description = "Run Playwright gap analysis tests against CM6 and Kodemirror"
    group = "verification"
    dependsOn(":samples:showcase:wasmJsBrowserDevelopmentWebpack")
    workingDir = file("gap-analysis")
    commandLine("npx", "playwright", "test")
}

tasks.register<Exec>("generateGapReport") {
    description = "Generate gap report from Playwright test results"
    group = "verification"
    workingDir = file("gap-analysis")
    commandLine("npx", "ts-node", "report/generate-report.ts")
}
