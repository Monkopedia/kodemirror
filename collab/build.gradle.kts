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

tasks.named("wasmJsNodeTest") { enabled = false }
