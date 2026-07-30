plugins {
    id("kodemirror.library")
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation(project(":lezer-common"))
            implementation(project(":lezer-highlight"))
            implementation(project(":language"))
            implementation(project(":state"))
        }
    }
}

// wasmJs tests verified green on the headless-browser runner (#202).
kodemirrorLibrary {
    wasmJsTests.set(true)
}
