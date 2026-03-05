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
            implementation(project(":language"))
            implementation(project(":lezer-highlight"))
            implementation(compose.ui)
            implementation(compose.foundation)
            implementation(compose.runtime)
            implementation(libs.kotlinx.datetime)
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
            implementation(project(":state"))
        }
        jvmTest.dependencies {
            implementation(kotlin("test"))
            implementation(project(":state"))
            implementation(project(":view"))
        }
    }
}

tasks.configureEach {
    if ("wasmJs" in name || "WasmJs" in name) enabled = false
}
