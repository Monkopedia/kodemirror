# Gradle Setup

In upstream CodeMirror, you bundle your editor with Rollup or esbuild.
In Kodemirror, you add Gradle dependencies.

<div class="demo-embed">
<iframe src="../../showcase/index.html?demo=bundle" loading="lazy"></iframe>
</div>

## Adding Kodemirror to your project

Kodemirror modules are published under the group
`com.monkopedia.kodemirror`. Add the modules you need to your
`build.gradle.kts`:

```kotlin
kotlin {
    sourceSets {
        commonMain.dependencies {
            // Core (always needed)
            implementation("com.monkopedia.kodemirror:state:0.3.5")
            implementation("com.monkopedia.kodemirror:view:0.3.5")

            // Common features
            implementation("com.monkopedia.kodemirror:commands:0.3.5")
            implementation("com.monkopedia.kodemirror:language:0.3.5")
            implementation("com.monkopedia.kodemirror:search:0.3.5")

            // Language support (pick what you need)
            implementation("com.monkopedia.kodemirror:lang-javascript:0.3.5")
            implementation("com.monkopedia.kodemirror:lang-python:0.3.5")

            // Optional features
            implementation("com.monkopedia.kodemirror:autocomplete:0.3.5")
            implementation("com.monkopedia.kodemirror:lint:0.3.5")

            // Theme
            implementation("com.monkopedia.kodemirror:theme-one-dark:0.3.5")
        }
    }
}
```

You also need Compose Multiplatform set up in your project. Kodemirror
uses `compose.ui`, `compose.foundation`, and `compose.runtime`.

## Module dependency rules

The modules form a layered dependency graph. You only need to declare
the modules you use directly — Gradle pulls in transitive dependencies
automatically.

| If you use... | You also get... |
|---------------|-----------------|
| Any `lang-*` module | `:language`, `:state`, lezer modules |
| `:language` | `:state`, `:view`, `:lezer-common`, `:lezer-highlight` |
| `:commands` | `:state`, `:view` |
| `:search` | `:state`, `:view` |
| `:autocomplete` | `:state`, `:view`, `:language` |
| `:view` | `:state` |

## Platform-specific setup

### Android

For Android projects, add the Compose Multiplatform plugin and
Kodemirror dependencies:

```kotlin
// build.gradle.kts (app module)
plugins {
    id("com.android.application")
    kotlin("multiplatform")
    id("org.jetbrains.compose")
    id("org.jetbrains.kotlin.plugin.compose")
}

kotlin {
    androidTarget()
    sourceSets {
        androidMain.dependencies {
            implementation("com.monkopedia.kodemirror:state:0.3.5")
            implementation("com.monkopedia.kodemirror:view:0.3.5")
            implementation("com.monkopedia.kodemirror:commands:0.3.5")
            implementation("com.monkopedia.kodemirror:language:0.3.5")
            implementation("com.monkopedia.kodemirror:lang-javascript:0.3.5")
        }
    }
}
```

### Desktop (JVM)

For Compose Desktop applications:

```kotlin
// build.gradle.kts
plugins {
    kotlin("multiplatform")
    id("org.jetbrains.compose")
    id("org.jetbrains.kotlin.plugin.compose")
}

kotlin {
    jvm()
    sourceSets {
        jvmMain.dependencies {
            implementation(compose.desktop.currentOs)
            implementation("com.monkopedia.kodemirror:state:0.3.5")
            implementation("com.monkopedia.kodemirror:view:0.3.5")
            implementation("com.monkopedia.kodemirror:commands:0.3.5")
            implementation("com.monkopedia.kodemirror:language:0.3.5")
            implementation("com.monkopedia.kodemirror:lang-javascript:0.3.5")
        }
    }
}
```

## Choosing extensions

Unlike upstream CodeMirror where `@codemirror/basic-setup` bundles a
predefined set of extensions, Kodemirror lets you pick exactly what you
need. See the [Basic Setup](basic.md) example for a typical set.

---

*Based on the [CodeMirror Bundling example](https://codemirror.net/examples/bundle/).*
