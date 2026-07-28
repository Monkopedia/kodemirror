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

// Compose-free all the way down, so wasmJs tests actually run here (#197).
kodemirrorLibrary {
    wasmJsTests.set(true)
}
