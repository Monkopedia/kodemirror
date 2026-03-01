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
            implementation(compose.runtime)
        }
        jvmTest.dependencies {
            implementation(kotlin("test"))
        }
    }
}
