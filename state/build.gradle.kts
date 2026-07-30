plugins {
    id("kodemirror.library")
    alias(libs.plugins.serialization)
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation(libs.kotlinx.serialization.json)
        }
    }
}

// wasmJs tests verified green on the headless-browser runner (#202).
kodemirrorLibrary {
    wasmJsTests.set(true)
}
