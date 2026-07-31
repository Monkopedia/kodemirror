import org.jetbrains.compose.ExperimentalComposeLibrary

plugins {
    id("kodemirror.library")
    alias(libs.plugins.jetbrainsCompose)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.serialization)
    alias(libs.plugins.roborazzi)
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation(project(":state"))
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.kotlinx.serialization.json)
            implementation(compose.ui)
            implementation(compose.foundation)
            implementation(compose.runtime)
        }
        commonTest.dependencies {
            // Needed by the Compose UI interaction tests in `commonTest/.../input`.
            implementation(project(":language"))
            implementation(project(":lezer-highlight"))
            implementation(project(":lang-javascript"))
            implementation(project(":commands"))
            implementation(project(":basic-setup"))
            @OptIn(ExperimentalComposeLibrary::class)
            implementation(compose.uiTest)
        }
        jvmTest.dependencies {
            implementation(project(":autocomplete"))
            implementation(project(":search"))
            implementation(project(":lezer-common"))
            implementation(project(":lezer-lr"))
            implementation(project(":kodemirror-test"))
            implementation(libs.roborazzi.compose.desktop)
            implementation(compose.desktop.uiTestJUnit4)
            implementation(compose.desktop.currentOs)
        }
    }
}

// wasmJs tests verified green on the headless-browser runner (#202).
kodemirrorLibrary {
    wasmJsTests.set(true)
}
