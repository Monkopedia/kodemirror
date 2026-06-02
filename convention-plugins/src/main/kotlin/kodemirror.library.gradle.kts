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
version = "0.3.2"

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
        nodejs()
    }

    // Native targets — compile but largely untested.
    macosArm64()
    macosX64()
    iosArm64()
    iosSimulatorArm64()
    iosX64()

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

tasks.configureEach {
    if (name.startsWith("wasmJsTest") || name == "wasmJsNodeTest") enabled = false
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
    signAllPublications()
}

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
