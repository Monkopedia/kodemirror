import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.jetbrainsCompose)
    alias(libs.plugins.compose.compiler)
}

kotlin {
    jvm("desktop")

    sourceSets {
        val desktopMain by getting

        commonMain.dependencies {
            implementation(project(":state"))
            implementation(project(":view"))
            implementation(project(":basic-setup"))
            implementation(project(":commands"))
            implementation(project(":language"))
            implementation(project(":search"))
            implementation(project(":autocomplete"))
            implementation(project(":lint"))
            implementation(project(":collab"))
            implementation(project(":merge"))
            implementation(project(":lezer-common"))
            implementation(project(":lezer-highlight"))
            implementation(project(":legacy-modes"))

            implementation(project(":lang-javascript"))
            implementation(project(":lang-python"))
            implementation(project(":lang-rust"))
            implementation(project(":lang-html"))
            implementation(project(":lang-css"))
            implementation(project(":lang-json"))
            implementation(project(":lang-yaml"))
            implementation(project(":lang-markdown"))
            implementation(project(":lang-go"))
            implementation(project(":lang-java"))

            implementation(project(":theme-one-dark"))
            implementation(project(":theme-github-light"))
            implementation(project(":theme-dracula"))
            implementation(project(":material-theme"))

            implementation(compose.runtime)
            implementation(compose.foundation)
            implementation(compose.ui)
            implementation(compose.material3)
        }
        desktopMain.dependencies {
            implementation(compose.desktop.currentOs)
        }
    }
}

compose.desktop {
    application {
        mainClass = "com.monkopedia.kodemirror.samples.showcase.MainKt"

        nativeDistributions {
            targetFormats(TargetFormat.Dmg, TargetFormat.Msi, TargetFormat.Deb)
            packageName = "kodemirror-showcase"
            packageVersion = "1.0.0"
        }
    }
}
