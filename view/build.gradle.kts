plugins {
    id("kodemirror.library")
    alias(libs.plugins.jetbrainsCompose)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.roborazzi)
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation(project(":state"))
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
            implementation(project(":lezer-javascript"))
            implementation(libs.roborazzi.compose.desktop)
            implementation(compose.desktop.uiTestJUnit4)
            implementation(compose.desktop.currentOs)
        }
    }
}
