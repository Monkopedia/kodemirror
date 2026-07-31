/*
 * Copyright 2025 Jason Monk
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * Originally based on CodeMirror 6 by Marijn Haverbeke, licensed under MIT.
 * See NOTICE file for details.
 */

import java.util.concurrent.Callable
import org.gradle.api.tasks.testing.logging.TestExceptionFormat
import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl

plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.multiplatform")
    id("org.jlleitschuh.gradle.ktlint")
    id("com.diffplug.spotless")
    id("org.jetbrains.kotlinx.atomicfu")
    id("org.jetbrains.kotlinx.kover")
    id("org.jetbrains.dokka")
    id("org.jetbrains.kotlinx.binary-compatibility-validator")
    id("com.vanniktech.maven.publish")
    signing
}

group = "com.monkopedia.kodemirror"
version = "0.3.6-SNAPSHOT"

android {
    namespace = "com.monkopedia.kodemirror.${project.name.replace("-", ".")}"
    compileSdk = 36
    defaultConfig {
        minSdk = 24
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

kotlin {
    jvm()
    androidTarget()

    @OptIn(ExperimentalWasmDsl::class)
    wasmJs {
        browser {
            testTask {
                useKarma {
                    useChromeHeadless()
                }
            }
        }
    }

    // Native targets — compile but largely untested.
    macosArm64()
    iosArm64()
    iosSimulatorArm64()

    applyDefaultHierarchyTemplate()

    sourceSets {
        commonMain.dependencies {
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
        }
        // Android shares platform code with JVM
        androidMain.get().dependsOn(jvmMain.get())
    }
}

apiValidation {
    @OptIn(kotlinx.validation.ExperimentalBCVApi::class)
    klib {
        enabled = false
    }
    // Filter Compose compiler-generated ComposableSingletons from public API.
    // These have hash-based method names that change with any code modification.
    ignoredClasses.addAll(
        listOf(
            "com.monkopedia.kodemirror.search.ComposableSingletons\$SearchKt",
            "com.monkopedia.kodemirror.lint.ComposableSingletons\$LintKt"
        )
    )
}

// wasmJs tests are opt-in per module: a module only gets a wasm test run once someone has
// confirmed it actually passes there. The flag is read in `afterEvaluate` so the module's own
// build script has already run by the time the decision is made. See #197 (the flag) and #202
// (moving the runner from Node to a headless browser). The runner is Karma/Chrome-headless
// rather than Node because skiko ships a browser-only emscripten build of `skiko.mjs`: under
// Node it aborts with "both async and sync fetching of the wasm failed", so every
// Compose-reaching module needs a real browser environment.
val kodemirrorLibrary = extensions.create<KodemirrorLibraryExtension>("kodemirrorLibrary")
kodemirrorLibrary.wasmJsTests.convention(false)

afterEvaluate {
    val wasmJsTestsEnabled = kodemirrorLibrary.wasmJsTests.get()
    tasks.configureEach {
        if (name.startsWith("wasmJsTest") || name == "wasmJsBrowserTest") {
            enabled = wasmJsTestsEnabled
        }
    }

    // A Compose-reaching wasm test bundle imports `./skiko.mjs` from its own package
    // directory. The Compose Gradle plugin puts that file there (`processSkikoRuntimeForKWasm`)
    // only for the modules that apply it directly, so modules that reach Compose transitively
    // — every `:lang-*`, `:legacy-modes` — have to unpack the same artifact themselves or
    // webpack fails with "Can't resolve './skiko.mjs'". The skiko version is taken from the
    // module's own resolved wasm test classpath, so Compose-free modules add nothing.
    if (wasmJsTestsEnabled && !pluginManager.hasPlugin("org.jetbrains.compose")) {
        val unpackSkiko = tasks.register<Copy>("unpackSkikoWasmRuntimeForWasmJsTest") {
            description = "Unpacks the skiko web runtime into the wasmJs test resources."
            from(
                Callable {
                    val skikoVersion = configurations.getByName("wasmJsTestRuntimeClasspath")
                        .incoming.resolutionResult.allComponents
                        .firstNotNullOfOrNull { component ->
                            component.moduleVersion
                                ?.takeIf { it.group == "org.jetbrains.skiko" }
                                ?.version
                        }
                    skikoVersion?.let { version ->
                        zipTree(
                            configurations.detachedConfiguration(
                                dependencies.create(
                                    "org.jetbrains.skiko:skiko-js-wasm-runtime:$version"
                                )
                            ).singleFile
                        )
                    } ?: emptyList<Any>()
                }
            )
            into(layout.buildDirectory.dir("kodemirror/skiko-wasm-test-runtime"))
        }
        tasks.named<ProcessResources>("wasmJsTestProcessResources") {
            from(unpackSkiko)
        }
    }
}

// Print the exception — message included — for every failed test. Gradle's default listener
// prints only `<exception class> at <file>:<line>`, which on Kotlin/Native reads
// `kotlin.AssertionError at null:-1` and throws away the assertion message the test author
// wrote. That makes a CI-only failure on a host you cannot reproduce locally (the Apple
// targets, from a Linux box) essentially undiagnosable without re-running the job.
tasks.withType<AbstractTestTask>().configureEach {
    testLogging {
        events("failed")
        exceptionFormat = TestExceptionFormat.FULL
        showExceptions = true
        showCauses = true
        showStackTraces = true
    }
}

ktlint {
    android.set(true)
}

spotless {
    kotlin {
        target("src/**/*.kt")
        licenseHeaderFile(rootProject.file("spotless/license-header.kt"))
    }
}

mavenPublishing {
    pom {
        name.set(project.name)
        description.set("Kotlin Multiplatform port of CodeMirror 6 for Compose Multiplatform")
        url.set("https://github.com/Monkopedia/kodemirror")
        licenses {
            license {
                name.set("The Apache License, Version 2.0")
                url.set("https://www.apache.org/licenses/LICENSE-2.0.txt")
            }
        }
        developers {
            developer {
                id.set("monkopedia")
                name.set("Jason Monk")
                email.set("monkopedia@gmail.com")
            }
        }
        scm {
            connection.set("scm:git:git://github.com/Monkopedia/kodemirror.git")
            developerConnection.set("scm:git:ssh://github.com/Monkopedia/kodemirror.git")
            url.set("https://github.com/Monkopedia/kodemirror/")
        }
    }
    // automaticRelease = true publishes straight to the Central Portal on a successful
    // deploy, skipping the manual "Publish" click in the Sonatype Central Portal UI.
    publishToMavenCentral(automaticRelease = true)
    // Sign released artifacts only. SNAPSHOT builds (e.g. local `publishToMavenLocal`
    // iteration) skip signing: the Central Portal does not require signatures for
    // snapshots, and skipping avoids needing a GPG key for fast local loops.
    if (!version.toString().endsWith("SNAPSHOT")) {
        signAllPublications()
    }
}

// Sign released artifacts only. SNAPSHOT builds (local `publishToMavenLocal`
// iteration) skip signing entirely — no GPG key needed for the fast local loop.
if (!version.toString().endsWith("SNAPSHOT")) {
    signing {
        useGpgCmd()
        sign(extensions.getByType<PublishingExtension>().publications)
    }

    afterEvaluate {
        tasks.withType<Sign> {
            val signingTask = this
            tasks.withType<AbstractPublishToMaven> {
                dependsOn(signingTask)
            }
        }
    }
}
