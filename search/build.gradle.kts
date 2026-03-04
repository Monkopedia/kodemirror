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
            implementation(compose.ui)
            implementation(compose.foundation)
            implementation(compose.runtime)
        }
        commonTest.dependencies {
            implementation(project(":state"))
        }
        jvmTest.dependencies {
            implementation(kotlin("test"))
            implementation(project(":view"))
        }
    }
}

tasks.named("wasmJsNodeTest") { enabled = false }
