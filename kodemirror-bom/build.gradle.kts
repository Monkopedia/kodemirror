import org.gradle.api.publish.maven.tasks.GenerateMavenPom

plugins {
    `java-platform`
    id("com.vanniktech.maven.publish")
    signing
}

group = "com.monkopedia.kodemirror"
version = "0.3.6-SNAPSHOT"

// Published coordinates deliberately kept OUT of the BOM. A BOM entry is a statement that the
// coordinate is supported and intended for consumption, so anything whose status is unsettled is
// parked here with its reason rather than being swept in by the mechanical rule below. An
// exclusion mechanism is needed regardless: `theme-github-light` was published at 0.1.0 and then
// deliberately dropped (#14, CHANGELOG.md:189,196) — it fell out of the module list entirely, so
// it needs no entry here, but it is the precedent for why this map exists.
//
// Remove an entry — one line — once the decision it is waiting on has been made.
val bomExclusions = mapOf(
    // #240: the published `kodemirror-test` fixture bypasses the keymap and has no tests and no
    // consumers; the open question there is whether to delete the coordinate outright or rebuild
    // the fixture. Constraining it in the BOM would answer that by the back door, so it is parked
    // until #240 is decided. This is a deliberate parking place, not an oversight.
    "kodemirror-test" to "#240 — undecided whether to delete or rebuild the published coordinate"
)

// The constraint list is DERIVED from the set of projects that actually publish to Maven Central
// (minus `bomExclusions`) rather than hand-maintained. A hand-written list drifts from the
// published set the moment a module is added and nothing catches it: every BOM released up to
// 0.3.5 declared 54 constraints for 57 published modules, so a consumer following the README's
// BOM pattern with `implementation("com.monkopedia.kodemirror:vim")` hit a hard resolution
// failure — the BOM supplied no version for `vim` or `lsp-client` (#244).
//
// `com.vanniktech.maven.publish` is the marker that a module publishes to Central (the
// `kodemirror.library` convention plugin applies it). `pluginManager.withPlugin` is
// order-independent: it fires immediately for modules already configured and on application for
// those configured later, so the result does not depend on the order `settings.gradle.kts`
// includes projects. `:kodemirror-bom` excludes itself.
dependencies {
    val platformConstraints = constraints
    rootProject.subprojects
        .filter { it != project && it.name !in bomExclusions }
        .forEach { module ->
            module.pluginManager.withPlugin("com.vanniktech.maven.publish") {
                platformConstraints.api(module)
            }
        }
}

// Drift guard. This reads the POM that actually ships — the artifact #244 was demonstrated
// against — instead of re-deriving the constraint list, so it also fails if the derivation above
// silently produces nothing (e.g. the publish plugin id changes). The expected set is every
// module applying the `kodemirror.library` convention plugin, minus the documented exclusions;
// that is a deliberately different criterion from the one the derivation uses.
val expectedBomModules = provider {
    rootProject.subprojects
        .filter {
            it != project &&
                it.name !in bomExclusions &&
                it.pluginManager.hasPlugin("kodemirror.library")
        }
        .map { it.name }
        .toSortedSet()
}
// `tasks.withType` rather than `tasks.named(...)`: the publication (and therefore its
// `generatePomFile…` task) is created by the publish plugin in an `afterEvaluate`, so naming it
// eagerly here would fail. The live collection also keeps working if the publication is ever
// renamed.
val pomTasks = tasks.withType<GenerateMavenPom>()
val verifyBomCoverage = tasks.register("verifyBomCoverage") {
    group = "verification"
    description =
        "Fails if the generated BOM POM does not constrain exactly the published modules."
    dependsOn(pomTasks)
    val pomFiles = provider { pomTasks.map { it.destination } }
    val expected = expectedBomModules
    inputs.files(pomFiles)
    inputs.property("expectedModules", expected)
    doLast {
        val pom = pomFiles.get().single().readText()
        val managed = Regex(
            "<dependencyManagement>(.*?)</dependencyManagement>",
            RegexOption.DOT_MATCHES_ALL
        ).find(pom)?.groupValues?.get(1).orEmpty()
        val declared = Regex("<artifactId>([^<]+)</artifactId>")
            .findAll(managed)
            .map { it.groupValues[1] }
            .toSortedSet()
        val expectedModules = expected.get()
        check(expectedModules.isNotEmpty()) {
            "No published modules were detected — the BOM constraint derivation is broken."
        }
        val missing = expectedModules - declared
        val unexpected = declared - expectedModules
        check(missing.isEmpty() && unexpected.isEmpty()) {
            buildString {
                appendLine("kodemirror-bom does not match the set of published modules (#244).")
                appendLine("  expected constraints: ${expectedModules.size}")
                appendLine("  BOM constraints:      ${declared.size}")
                if (missing.isNotEmpty()) {
                    appendLine("  published but NOT in the BOM: ${missing.joinToString()}")
                }
                if (unexpected.isNotEmpty()) {
                    appendLine("  in the BOM but NOT published: ${unexpected.joinToString()}")
                }
                appendLine(
                    "  deliberately excluded: " +
                        bomExclusions.entries.joinToString { "${it.key} (${it.value})" }
                )
            }
        }
        logger.lifecycle("kodemirror-bom constrains all ${declared.size} published modules.")
    }
}

// `java-platform` brings the `base` lifecycle tasks, so local `./gradlew check` picks the guard
// up. CI does not run `check` — it names its tasks explicitly (`ci.yml`) — so
// `:kodemirror-bom:verifyBomCoverage` is listed there too.
tasks.named("check") {
    dependsOn(verifyBomCoverage)
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
    // Sign released artifacts only; SNAPSHOT builds (local mavenLocal iteration)
    // skip signing. See the matching guard in the kodemirror.library convention plugin.
    if (!version.toString().endsWith("SNAPSHOT")) {
        signAllPublications()
    }
}

// Sign released artifacts only; SNAPSHOT builds skip signing (local mavenLocal loop).
if (!version.toString().endsWith("SNAPSHOT")) {
    signing {
        useGpgCmd()
        sign(extensions.getByType<PublishingExtension>().publications)
    }
}
