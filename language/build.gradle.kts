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
            implementation(project(":lezer-common"))
            implementation(project(":lezer-highlight"))
            implementation(compose.ui)
            implementation(compose.foundation)
            implementation(compose.runtime)
        }
    }
}

tasks.configureEach {
    if ("wasmJs" in name || "WasmJs" in name) enabled = false
}
