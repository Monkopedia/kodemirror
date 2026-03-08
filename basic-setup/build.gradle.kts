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
            api(project(":language"))
            api(project(":commands"))
            api(project(":search"))
            api(project(":autocomplete"))
            api(project(":lint"))
            implementation(project(":lezer-highlight"))
            implementation(compose.runtime)
        }
    }
}
