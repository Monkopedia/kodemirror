plugins {
    id("kodemirror.library")
    alias(libs.plugins.jetbrainsCompose)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.serialization)
    alias(libs.plugins.roborazzi)
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation(project(":state"))
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.kotlinx.serialization.json)
            implementation(compose.ui)
            implementation(compose.foundation)
            implementation(compose.runtime)
        }
        jvmTest.dependencies {
            implementation(project(":language"))
            implementation(project(":search"))
            implementation(project(":lezer-common"))
            implementation(project(":lezer-highlight"))
            implementation(project(":lezer-lr"))
            implementation(project(":lang-javascript"))
            implementation(project(":kodemirror-test"))
            implementation(project(":commands"))
            implementation(project(":basic-setup"))
            implementation(libs.roborazzi.compose.desktop)
            implementation(compose.desktop.uiTestJUnit4)
            implementation(compose.desktop.currentOs)
        }
    }
}
