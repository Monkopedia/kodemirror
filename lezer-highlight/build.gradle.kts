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

// wasmJs tests verified green on the headless-browser runner (#202).
kodemirrorLibrary {
    wasmJsTests.set(true)
}
