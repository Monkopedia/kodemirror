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
            implementation(project(":language"))
            implementation(project(":lezer-common"))
            implementation(libs.kotlinx.coroutines.core)
            implementation(compose.ui)
            implementation(compose.foundation)
            implementation(compose.runtime)
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
            implementation(project(":state"))
            implementation(project(":view"))
            // A grammar with string nodes, for closeBrackets' triple quotes and prefixes.
            implementation(project(":lang-python"))
        }
    }
}

// wasmJs tests verified green on the headless-browser runner (#202).
kodemirrorLibrary {
    wasmJsTests.set(true)
}
