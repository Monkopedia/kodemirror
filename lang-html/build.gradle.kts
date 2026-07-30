plugins {
    id("kodemirror.library")
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation(project(":lezer-common"))
            implementation(project(":lezer-highlight"))
            implementation(project(":lezer-lr"))
            implementation(project(":language"))
            implementation(project(":state"))
            implementation(project(":autocomplete"))
            implementation(project(":lang-javascript"))
            implementation(project(":lang-css"))
        }
    }
}

// wasmJs tests verified green on the headless-browser runner (#202).
kodemirrorLibrary {
    wasmJsTests.set(true)
}
