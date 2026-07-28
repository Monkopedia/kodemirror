plugins {
    id("kodemirror.library")
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation(project(":state"))
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
            implementation(project(":state"))
        }
        jvmTest.dependencies {
            implementation(kotlin("test"))
            implementation(project(":state"))
            implementation(project(":view"))
            implementation(project(":commands"))
        }
    }
}

// Compose-free all the way down, so wasmJs tests actually run here (#197).
kodemirrorLibrary {
    wasmJsTests.set(true)
}
