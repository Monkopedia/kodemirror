plugins {
    `java-platform`
}

group = "com.monkopedia.kodemirror"
version = "0.1.0-SNAPSHOT"

dependencies {
    constraints {
        // Core
        api(project(":state"))
        api(project(":view"))
        api(project(":language"))
        api(project(":commands"))
        api(project(":search"))
        api(project(":autocomplete"))
        api(project(":lint"))
        api(project(":collab"))
        api(project(":merge"))

        // Lezer parser infrastructure
        api(project(":lezer-common"))
        api(project(":lezer-highlight"))
        api(project(":lezer-lr"))

        // Language modules
        api(project(":lang-angular"))
        api(project(":lang-cpp"))
        api(project(":lang-css"))
        api(project(":lang-go"))
        api(project(":lang-grammar"))
        api(project(":lang-html"))
        api(project(":lang-java"))
        api(project(":lang-javascript"))
        api(project(":lang-jinja"))
        api(project(":lang-json"))
        api(project(":lang-less"))
        api(project(":lang-liquid"))
        api(project(":lang-markdown"))
        api(project(":lang-php"))
        api(project(":lang-python"))
        api(project(":lang-rust"))
        api(project(":lang-sass"))
        api(project(":lang-sql"))
        api(project(":lang-vue"))
        api(project(":lang-wast"))
        api(project(":lang-xml"))
        api(project(":lang-yaml"))

        // Themes
        api(project(":theme-one-dark"))
        api(project(":theme-github-light"))
        api(project(":theme-dracula"))
        api(project(":material-theme"))

        // Convenience bundles
        api(project(":basic-setup"))
        api(project(":legacy-modes"))
    }
}
