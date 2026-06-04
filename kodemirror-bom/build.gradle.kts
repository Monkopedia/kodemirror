plugins {
    `java-platform`
    id("com.vanniktech.maven.publish")
    signing
}

group = "com.monkopedia.kodemirror"
version = "0.3.3"

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
        api(project(":theme-dracula"))
        api(project(":theme-amy"))
        api(project(":theme-ayu-light"))
        api(project(":theme-barf"))
        api(project(":theme-bespin"))
        api(project(":theme-birds-of-paradise"))
        api(project(":theme-boys-and-girls"))
        api(project(":theme-clouds"))
        api(project(":theme-cobalt"))
        api(project(":theme-cool-glow"))
        api(project(":theme-espresso"))
        api(project(":theme-noctis-lilac"))
        api(project(":theme-rose-pine-dawn"))
        api(project(":theme-smoothy"))
        api(project(":theme-solarized-light"))
        api(project(":theme-tomorrow"))
        api(project(":material-theme"))

        // Convenience bundles
        api(project(":basic-setup"))
        api(project(":legacy-modes"))
    }
}

mavenPublishing {
    pom {
        name.set("kodemirror-bom")
        description.set("Bill of Materials for Kodemirror — Kotlin Multiplatform port of CodeMirror 6")
        url.set("https://github.com/Monkopedia/kodemirror")
        licenses {
            license {
                name.set("The Apache License, Version 2.0")
                url.set("https://www.apache.org/licenses/LICENSE-2.0.txt")
            }
        }
        developers {
            developer {
                id.set("monkopedia")
                name.set("Jason Monk")
                email.set("monkopedia@gmail.com")
            }
        }
        scm {
            connection.set("scm:git:git://github.com/Monkopedia/kodemirror.git")
            developerConnection.set("scm:git:ssh://github.com/Monkopedia/kodemirror.git")
            url.set("https://github.com/Monkopedia/kodemirror/")
        }
    }
    // automaticRelease = true matches the library convention plugin so the BOM
    // module also contributes the Publish/Validate end-of-build actions. In the
    // single-invocation publish (deploy.yml) the shared MavenCentralBuildService
    // bundles every module — incl. this BOM — into one deployment that the other
    // modules already mark for auto-release; setting it here too makes the BOM
    // self-sufficient and avoids the 0.3.2 "Skipping deployment validation!" bug
    // where the BOM's isolated deployment never released.
    publishToMavenCentral(automaticRelease = true)
    signAllPublications()
}

signing {
    useGpgCmd()
    sign(extensions.getByType<PublishingExtension>().publications)
}
