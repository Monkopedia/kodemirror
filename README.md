# KodeMirror

[![CI](https://github.com/Monkopedia/kodemirror/actions/workflows/ci.yml/badge.svg)](https://github.com/Monkopedia/kodemirror/actions/workflows/ci.yml)
[![Maven Central](https://img.shields.io/maven-central/v/com.monkopedia.kodemirror/view)](https://central.sonatype.com/namespace/com.monkopedia.kodemirror)
[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](LICENSE)

A native [Compose Multiplatform](https://www.jetbrains.com/compose-multiplatform/) code editor — no WebView, no JS bridges. Built as a ground-up Kotlin port of [CodeMirror 6](https://codemirror.net/), the industry-standard web editor.

[Live Demo](https://monkopedia.github.io/kodemirror/showcase/) · [Documentation](https://monkopedia.github.io/kodemirror/) · [API Reference](https://monkopedia.github.io/kodemirror/reference/)

## Why KodeMirror?

Compose Multiplatform doesn't ship a code editor. Your options are embedding a WebView (heavy, hard to integrate) or building from scratch. KodeMirror gives you a real Compose component with the full feature set of CodeMirror 6:

- **Syntax highlighting** for 20+ languages, plus 100+ via legacy modes
- **Vim mode** with 600+ ported upstream tests
- **Search/replace**, **autocompletion**, **linting**, **code folding**
- **Collaborative editing** and **side-by-side diff/merge**
- **Themes** — 17 built-in themes (One Dark, Dracula, Solarized Light, Tomorrow, …) plus Material Design integration
- **Real keyboard input pipeline** — layout-aware key handling, no platform hacks

All from shared Kotlin code across every Compose target.

**[Try the live demo](https://monkopedia.github.io/kodemirror/showcase/)** to see it in action — syntax highlighting, vim mode, autocompletion, and more, all running in the browser.

## Platform Support

| Platform | Status |
|----------|--------|
| wasmJs (Browser) | **Tested** — automated gap tests + manual browser testing |
| JVM Desktop | Unit tests pass, visual rendering lightly tested |
| Android | Unit tests pass via CI, untested on devices |
| iOS / macOS native | Compiles, experimental |

## Quick Start

```kotlin
// build.gradle.kts
kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation(platform("com.monkopedia.kodemirror:kodemirror-bom:0.3.5"))
            implementation("com.monkopedia.kodemirror:view")
            implementation("com.monkopedia.kodemirror:commands")
            implementation("com.monkopedia.kodemirror:basic-setup")
            // Pick your language(s)
            implementation("com.monkopedia.kodemirror:lang-javascript")
        }
    }
}
```

```kotlin
@Composable
fun Editor() {
    val session = rememberEditorSession(
        doc = "function hello() {\n    return 'world';\n}",
        extensions = basicSetup + javascript().extension
    )
    KodeMirror(session = session, modifier = Modifier.fillMaxSize())
}
```

### With Vim mode and a theme

Also add `com.monkopedia.kodemirror:vim`, `:theme-one-dark`, and `:lang-python`:

```kotlin
@Composable
fun VimEditor() {
    val session = rememberEditorSession(
        doc = "print(\"Hello, KodeMirror!\")",
        extensions = basicSetup + python().extension + vim() + oneDark
    )
    KodeMirror(session = session, modifier = Modifier.fillMaxSize())
}
```

## Modules

### Core
| Module | Description |
|--------|-------------|
| **state** | Editor state, transactions, selections, facets |
| **view** | Compose UI component, decorations, key handling |
| **language** | Language support infrastructure, syntax highlighting |
| **commands** | Standard editor commands (cursor movement, delete, indent, undo/redo) |
| **basic-setup** | Convenience bundle combining common extensions |

### Features
| Module | Description |
|--------|-------------|
| **search** | Find/replace panel, search commands |
| **autocomplete** | Completion popup and sources |
| **lint** | Diagnostic display and linting |
| **collab** | Collaborative editing support |
| **merge** | Side-by-side diff view |
| **vim** | Vim keybindings and modal editing |

### Languages

JavaScript, TypeScript, HTML, CSS, Python, Java, Go, Rust, Markdown, JSON, YAML, XML, SQL, C++, PHP, and more — 20+ dedicated modules plus 100+ (including Kotlin) via `legacy-modes`.

### Themes

One Dark, Dracula, Solarized Light, Tomorrow, Cobalt, Espresso, and 11 more, plus Material Design integration.

## Known Limitations

This is v0.3.5 — the API may evolve. Known issues to be aware of:

- **Mobile/native** — Android and iOS targets compile and pass unit tests but are not battle-tested on real devices yet

See [all open issues](https://github.com/Monkopedia/kodemirror/issues) for the full list.

## Contributing

Contributions are welcome! Please [open an issue](https://github.com/Monkopedia/kodemirror/issues/new) to discuss before submitting large changes.

## License

```
Copyright 2026 Jason Monk

Licensed under the Apache License, Version 2.0
```

Originally based on CodeMirror 6 by Marijn Haverbeke, licensed under MIT. See [NOTICE](NOTICE) for details.
