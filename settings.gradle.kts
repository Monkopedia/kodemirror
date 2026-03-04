rootProject.name = "Kodemirror"
enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")

pluginManagement {
    includeBuild("convention-plugins")
    repositories {
        mavenCentral()
        gradlePluginPortal()
        google()
    }
}

dependencyResolutionManagement {
    repositories {
        mavenCentral()
        google()
    }
}

include(":state")
include(":view")
include(":lezer-common")
include(":lezer-highlight")
include(":language")
include(":lezer-lr")
include(":lezer-json")
include(":lezer-css")
include(":lezer-grammar")
include(":lezer-javascript")
include(":lezer-java")
include(":lezer-xml")
include(":commands")
include(":search")
include(":lint")
include(":autocomplete")
include(":collab")
include(":merge")
include(":theme-one-dark")
