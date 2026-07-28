# Extension Index

Quick reference showing which module provides each common extension.

## Core extensions

| Extension | Module | Import |
|-----------|--------|--------|
| `lineNumbers` | `:view` | `com.monkopedia.kodemirror.view.lineNumbers` |
| `highlightActiveLine` | `:view` | `com.monkopedia.kodemirror.view.highlightActiveLine` |
| `highlightActiveLineGutter` | `:view` | `com.monkopedia.kodemirror.view.highlightActiveLineGutter` |
| `keymap` | `:view` | `com.monkopedia.kodemirror.view.keymap` |
| `keymapOf(...)` | `:view` | `com.monkopedia.kodemirror.view.keymapOf` |
| `editorTheme` | `:view` | `com.monkopedia.kodemirror.view.editorTheme` |

## Commands & history

| Extension | Module | Import |
|-----------|--------|--------|
| `history()` | `:commands` | `com.monkopedia.kodemirror.commands.history` |
| `defaultKeymap` | `:commands` | `com.monkopedia.kodemirror.commands.defaultKeymap` |
| `indentWithTab` | `:commands` | `com.monkopedia.kodemirror.commands.indentWithTab` |

`history()` already installs its own undo/redo key bindings, so there is no
separate history keymap to add.

## Language

| Extension | Module | Import |
|-----------|--------|--------|
| `bracketMatching()` | `:language` | `com.monkopedia.kodemirror.language.bracketMatching` |
| `codeFolding()` | `:language` | `com.monkopedia.kodemirror.language.codeFolding` |
| `syntaxHighlighting(...)` | `:language` | `com.monkopedia.kodemirror.language.syntaxHighlighting` |
| `indentOnInput` | `:language` | `com.monkopedia.kodemirror.language.indentOnInput` |
| Language functions (e.g. `javascript().extension`) | `:lang-*` | `com.monkopedia.kodemirror.lang.javascript.javascript` |

## Features

| Extension | Module | Import |
|-----------|--------|--------|
| `autocompletion()` | `:autocomplete` | `com.monkopedia.kodemirror.autocomplete.autocompletion` |
| `closeBracketsKeymap` | `:autocomplete` | `com.monkopedia.kodemirror.autocomplete.closeBracketsKeymap` |
| `search()` | `:search` | `com.monkopedia.kodemirror.search.search` |
| `searchKeymap` | `:search` | `com.monkopedia.kodemirror.search.searchKeymap` |
| `linter(...)` | `:lint` | `com.monkopedia.kodemirror.lint.linter` |
| `lintGutter()` | `:lint` | `com.monkopedia.kodemirror.lint.lintGutter` |

## Themes

| Extension | Module | Import |
|-----------|--------|--------|
| `oneDark` | `:theme-one-dark` | `com.monkopedia.kodemirror.themonedark.oneDark` |
| `dracula` | `:theme-dracula` | `com.monkopedia.kodemirror.themedracula.dracula` |

The remaining 15 themes follow the same pattern: module `:theme-<name>`
exposes an `Extension` named after the theme in package
`com.monkopedia.kodemirror.theme<name>` (e.g. `:theme-solarized-light` →
`com.monkopedia.kodemirror.themesolarizedlight.solarizedLight`).
`:theme-one-dark` is the one exception — its package is `themonedark`, not
`themeonedark`.

## Gradle dependencies

All modules are published under group `com.monkopedia.kodemirror`:

```kotlin
dependencies {
    implementation("com.monkopedia.kodemirror:state:$version")
    implementation("com.monkopedia.kodemirror:view:$version")
    implementation("com.monkopedia.kodemirror:commands:$version")
    implementation("com.monkopedia.kodemirror:language:$version")
    implementation("com.monkopedia.kodemirror:autocomplete:$version")
    implementation("com.monkopedia.kodemirror:search:$version")
    implementation("com.monkopedia.kodemirror:lint:$version")
    implementation("com.monkopedia.kodemirror:lang-javascript:$version")
    // ... add language modules as needed
}
```
