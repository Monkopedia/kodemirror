# Gradle Setup

In upstream CodeMirror, you bundle your editor with Rollup or esbuild.
In Kodemirror, you add Gradle dependencies.

## Adding Kodemirror to your project

Kodemirror modules are published under the group
`com.monkopedia.kodemirror`. Add the modules you need to your
`build.gradle.kts`:

```kotlin
kotlin {
    sourceSets {
        commonMain.dependencies {
            // Core (always needed)
            implementation("com.monkopedia.kodemirror:state:0.1.0-SNAPSHOT")
            implementation("com.monkopedia.kodemirror:view:0.1.0-SNAPSHOT")

            // Common features
            implementation("com.monkopedia.kodemirror:commands:0.1.0-SNAPSHOT")
            implementation("com.monkopedia.kodemirror:language:0.1.0-SNAPSHOT")
            implementation("com.monkopedia.kodemirror:search:0.1.0-SNAPSHOT")

            // Language support (pick what you need)
            implementation("com.monkopedia.kodemirror:lang-javascript:0.1.0-SNAPSHOT")
            implementation("com.monkopedia.kodemirror:lang-python:0.1.0-SNAPSHOT")

            // Optional features
            implementation("com.monkopedia.kodemirror:autocomplete:0.1.0-SNAPSHOT")
            implementation("com.monkopedia.kodemirror:lint:0.1.0-SNAPSHOT")

            // Theme
            implementation("com.monkopedia.kodemirror:theme-one-dark:0.1.0-SNAPSHOT")
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

## Choosing extensions

Unlike upstream CodeMirror where `@codemirror/basic-setup` bundles a
predefined set of extensions, Kodemirror lets you pick exactly what you
need. See the [Basic Setup](basic.md) example for a typical set.

---

*Based on the [CodeMirror Bundling example](https://codemirror.net/examples/bundle/).*
