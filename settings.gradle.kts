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
include(":lang-json")
include(":lang-css")
include(":lang-grammar")
include(":lang-go")
include(":lang-javascript")
include(":lang-java")
include(":lang-xml")
include(":lang-cpp")
include(":lang-html")
include(":lang-markdown")
include(":lang-php")
include(":lang-python")
include(":lang-rust")
include(":lang-sass")
include(":lang-yaml")
include(":commands")
include(":search")
include(":lint")
include(":autocomplete")
include(":collab")
include(":merge")
include(":lang-wast")
include(":lang-sql")
include(":lang-less")
include(":lang-vue")
include(":lang-angular")
include(":lang-liquid")
include(":lang-jinja")
include(":theme-one-dark")
include(":theme-github-light")
include(":theme-dracula")
include(":material-theme")
include(":basic-setup")
include(":kodemirror-bom")
include(":kodemirror-test")
include(":legacy-modes")
include(":samples:editor")
include(":samples:showcase")
