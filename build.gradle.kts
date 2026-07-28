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

// Kotlin 2.4.10 defaults the managed Node.js to v25, which fails to start on
// the CI runners. Pin the JS and wasm Node toolchains to a known-good LTS.
//
// This needs BOTH APIs. The modern EnvSpec.version drives the target-node users
// (`:kotlinWasmNpmInstall` / yarn), but the wasm *npm tooling* node — the one
// `wasmJsBrowser*Webpack` launches to run webpack — is resolved from the legacy
// `WasmNodeJsRootExtension.version` instead, which the EnvSpec does not update.
// Setting only the EnvSpec leaves webpack on the default v25, so the showcase
// production build fails while `:check` (which never runs webpack) passes.
// The RootExtension setter is a scheduled-for-removal deprecation and this build
// compiles with -Werror, hence the suppression; drop it once the tooling node
// honours the EnvSpec.
plugins.withType<org.jetbrains.kotlin.gradle.targets.js.nodejs.NodeJsPlugin> {
    the<org.jetbrains.kotlin.gradle.targets.js.nodejs.NodeJsEnvSpec>().version.set("22.11.0")
}
plugins.withType<org.jetbrains.kotlin.gradle.targets.wasm.nodejs.WasmNodeJsPlugin> {
    the<org.jetbrains.kotlin.gradle.targets.wasm.nodejs.WasmNodeJsEnvSpec>().version.set("22.11.0")
}
plugins.withType<org.jetbrains.kotlin.gradle.targets.wasm.nodejs.WasmNodeJsRootPlugin> {
    @Suppress("DEPRECATION", "DEPRECATION_ERROR")
    fun pinWasmToolingNode() {
        the<org.jetbrains.kotlin.gradle.targets.wasm.nodejs.WasmNodeJsRootExtension>().version =
            "22.11.0"
    }
    pinWasmToolingNode()
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
        // BCV writes per-target dumps as <module>/api/<target>/<module>.api.
        fileTree(rootDir) {
            include("*/api/*/*.api")
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
    // Strip $stable from build API output before apiCheck compares against committed files.
    // Every dumped target needs this, and each check task must confine itself to its own
    // build/api/<target>/ directory: widening the tree to all of build/api would have one
    // target's check rewrite another target's not-yet-compared api build output.
    tasks.matching { it.name == "jvmApiCheck" || it.name == "androidApiCheck" }.configureEach {
        val target = name.removeSuffix("ApiCheck")
        doFirst {
            project.fileTree(project.layout.buildDirectory.dir("api/$target")) {
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
