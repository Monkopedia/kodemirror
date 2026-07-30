plugins {
    id("kodemirror.library")
    alias(libs.plugins.jetbrainsCompose)
    alias(libs.plugins.compose.compiler)
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation(project(":state"))
            implementation(project(":view"))
            implementation(project(":lezer-common"))
            implementation(project(":lezer-highlight"))
            implementation(libs.kotlinx.coroutines.core)
            implementation(compose.ui)
            implementation(compose.foundation)
            implementation(compose.runtime)
        }
    }
}

// wasmJs tests verified green on the headless-browser runner (#202).
kodemirrorLibrary {
    wasmJsTests.set(true)
}
