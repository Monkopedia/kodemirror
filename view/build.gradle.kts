import org.jetbrains.compose.ExperimentalComposeLibrary

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
        commonTest.dependencies {
            // Needed by the Compose UI interaction tests in `commonTest/.../input`.
            implementation(project(":language"))
            implementation(project(":lezer-common"))
            implementation(project(":lezer-highlight"))
            implementation(project(":lang-javascript"))
            implementation(project(":commands"))
            implementation(project(":basic-setup"))
            @OptIn(ExperimentalComposeLibrary::class)
            implementation(compose.uiTest)
        }
        jvmTest.dependencies {
            implementation(project(":autocomplete"))
            implementation(project(":search"))
            implementation(project(":lezer-lr"))
            implementation(project(":kodemirror-test"))
            implementation(libs.roborazzi.compose.desktop)
            implementation(compose.desktop.uiTestJUnit4)
            implementation(compose.desktop.currentOs)
        }
    }
}

// The Compose UI interaction tests in `commonTest/.../input` compile for Android but do not
// run as Android local unit tests. `runComposeUiTest` on Android launches a real
// ComponentActivity through ActivityScenario, so it needs an Android framework underneath:
// a plain unit-test JVM has none (it dies on a null `android.os.Build.FINGERPRINT`), and
// Robolectric — the usual answer — turns out not to give a trustworthy result here. Under
// `graphicsMode=LEGACY` all text measures to zero width, so every coordinate-based
// assertion is meaningless (a click 30px into a line resolves to the line's last character);
// under `graphicsMode=NATIVE` the metrics are right but a pointer press clears Compose focus,
// which breaks the focus and keyboard suites. Graphics mode cannot plausibly change focus
// semantics, so that divergence is an artifact of the environment rather than a finding about
// the editor. Android coverage for these needs instrumented tests on a device/emulator; see
// #215. Everything else in `commonTest` still runs here.
// `testDebugUnitTest` / `testReleaseUnitTest` are the Android local-unit-test tasks; the JVM
// target's task is `jvmTest` and is deliberately left alone.
tasks.withType<Test>().matching { it.name.endsWith("UnitTest") }.configureEach {
    filter {
        excludeTestsMatching("com.monkopedia.kodemirror.view.input.*")
        isFailOnNoMatchingTests = false
    }
}

// wasmJs tests verified green on the headless-browser runner (#202).
kodemirrorLibrary {
    wasmJsTests.set(true)
}
