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
            implementation(project(":commands"))
            implementation(project(":search"))
            implementation(project(":language"))
            implementation(project(":autocomplete"))
            implementation(project(":lezer-common"))
            implementation(compose.ui)
            implementation(compose.foundation)
            implementation(compose.runtime)
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
            implementation(project(":state"))
            implementation(project(":view"))
            implementation(project(":commands"))
            implementation(project(":search"))
            implementation(project(":language"))
        }
    }
}
