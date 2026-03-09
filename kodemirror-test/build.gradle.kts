plugins {
    id("kodemirror.library")
    alias(libs.plugins.jetbrainsCompose)
    alias(libs.plugins.compose.compiler)
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            api(project(":state"))
            api(project(":view"))
            api(kotlin("test"))
            implementation(compose.runtime)
        }
    }
}
