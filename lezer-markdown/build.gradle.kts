plugins {
    id("kodemirror.library")
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation(project(":lezer-common"))
            implementation(project(":lezer-highlight"))
        }
    }
}

tasks.configureEach {
    if ("wasmJs" in name || "WasmJs" in name) enabled = false
}
