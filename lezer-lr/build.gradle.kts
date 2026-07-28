plugins {
    id("kodemirror.library")
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation(project(":lezer-common"))
        }
    }
}

// Compose-free all the way down, so wasmJs tests actually run here (#197).
kodemirrorLibrary {
    wasmJsTests.set(true)
}
