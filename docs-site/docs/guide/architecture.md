# Architecture

Kodemirror is a Kotlin Multiplatform port of
[CodeMirror 6](https://codemirror.net/). It preserves the same modular,
extension-driven architecture while replacing the browser DOM rendering
layer with Jetpack Compose.

This page describes how the modules fit together and the core design
principles that run through the system.

## Modules

The project is split into 35 Gradle modules. They fall into six layers,
each building on the one below.

### Layer 1 — Foundation

| Module | Package | Purpose |
|--------|---------|---------|
| `:state` | `com.monkopedia.kodemirror.state` | Immutable editor state, transactions, extensions, facets |
| `:lezer-common` | `com.monkopedia.kodemirror.lezer.common` | Shared syntax-tree types (`Tree`, `NodeType`, `NodeSet`) |

These two modules have no dependency on each other or on Compose.

### Layer 2 — Parsing

| Module | Package | Purpose |
|--------|---------|---------|
| `:lezer-highlight` | `com.monkopedia.kodemirror.lezer.highlight` | Tag-based syntax highlighting |
| `:lezer-lr` | `com.monkopedia.kodemirror.lezer.lr` | LR parser runtime for Lezer grammars |

Both depend only on `:lezer-common`.

### Layer 3 — Language infrastructure

| Module | Package | Purpose |
|--------|---------|---------|
| `:language` | `com.monkopedia.kodemirror.language` | `Language`, `LanguageSupport`, `StreamParser`, indentation, folding, bracket matching |

Depends on `:state`, `:view`, `:lezer-common`, and `:lezer-highlight`.

### Layer 4 — View

| Module | Package | Purpose |
|--------|---------|---------|
| `:view` | `com.monkopedia.kodemirror.view` | Compose rendering, `EditorView`, `ViewPlugin`, decorations, gutters, panels, tooltips |

Depends on `:state`, `:language`, `:search`, and the Lezer modules.
This is the main Compose integration layer.

### Layer 5 — Features

| Module | Package | Purpose |
|--------|---------|---------|
| `:commands` | `com.monkopedia.kodemirror.commands` | Built-in editor commands (cursor movement, deletion, indentation) |
| `:search` | `com.monkopedia.kodemirror.search` | Find & replace |
| `:autocomplete` | `com.monkopedia.kodemirror.autocomplete` | Code completion |
| `:lint` | `com.monkopedia.kodemirror.lint` | Diagnostics and linting |
| `:merge` | `com.monkopedia.kodemirror.merge` | Diff/merge view |
| `:collab` | `com.monkopedia.kodemirror.collab` | Collaborative editing (state-only, no Compose dependency) |

### Layer 6 — Languages & themes

| Module | Purpose |
|--------|---------|
| `:lang-javascript`, `:lang-python`, `:lang-java`, etc. | Language support packages (24 total) |
| `:legacy-modes` | 103 ported CodeMirror 5 stream-based modes |
| `:theme-one-dark` | One Dark color theme |

Each language module exports a factory function (e.g. `javascript()`) that
returns a `LanguageSupport` extension.

## Platforms

All modules target **JVM** and **wasmJs** (WebAssembly with Node.js
runtime). Targets are configured in the shared convention plugin
`kodemirror.library.gradle.kts`:

```kotlin
kotlin {
    jvm()

    @OptIn(ExperimentalWasmDsl::class)
    wasmJs {
        nodejs()
    }
}
```

The group ID for all modules is `com.monkopedia.kodemirror`.

## Core design principles

### Immutable state

`EditorState` is a persistent (immutable) data structure. You never
mutate state directly — instead, you create a `Transaction` that
produces a new state:

```kotlin
val tr = state.update(
    TransactionSpec(
        changes = ChangeSpec.Single(from = 0, to = 5,
            insert = InsertContent.StringContent("Hello"))
    )
)
val newState = tr.state
```

This makes undo/redo, collaborative editing, and time-travel debugging
straightforward.

### Everything is an extension

Behavior is added to the editor through `Extension` values passed to
`EditorStateConfig`. Extensions compose arbitrarily — you can nest lists
of extensions, and the system flattens them during configuration:

```kotlin
val state = EditorState.create(EditorStateConfig(
    doc = "fun main() {}".asDoc(),
    extensions = ExtensionList(listOf(
        javascript(),
        oneDark,
        search(),
        keymap.of(defaultKeymap)
    ))
))
```

The four main extension primitives are:

- **Facet** — aggregates configuration values from multiple providers
- **StateField** — stores per-state data that updates on every transaction
- **ViewPlugin** — contributes behavior and decorations to the view
- **Compartment** — enables dynamic reconfiguration of extension subsets

### Functional core, Composable shell

The state layer (`:state`) is pure Kotlin with no UI dependency.
Transactions flow in, new states come out. The view layer (`:view`)
wraps this in a `@Composable` function that:

1. Holds the `EditorView` instance across recompositions
2. Syncs plugin lifecycle via `ViewPluginHost`
3. Renders the document using `LazyColumn` + `BasicText`
4. Draws selections and cursors on a Canvas overlay
5. Handles keyboard and pointer input via Compose modifiers

This separation means you can test state logic without any UI framework.

### Transaction-driven updates

All mutations — user input, programmatic changes, plugin effects — go
through the transaction system. A transaction carries:

- **Changes** to the document (`ChangeSet`)
- **Selection** updates
- **Effects** (typed side-channel values, e.g. "open search panel")
- **Annotations** (metadata like `userEvent` or `addToHistory`)

Filters and extenders registered via facets can inspect, modify, or
reject transactions before they are applied.

## How it differs from upstream CodeMirror

| Aspect | CodeMirror 6 (TypeScript) | Kodemirror (Kotlin) |
|--------|---------------------------|---------------------|
| Rendering | DOM elements, CSS classes | Compose `LazyColumn`, `BasicText`, Canvas |
| Theming | CSS custom properties | `EditorTheme` data class via `CompositionLocal` |
| Widgets | `toDOM()` returning `HTMLElement` | `@Composable Content()` on `WidgetType` |
| Selections/cursors | DOM selection API + positioned divs | Canvas drawing via `drawWithContent` |
| Key handling | `addEventListener` on DOM | Compose `onKeyEvent` modifier |
| Tooltips | Positioned DOM divs | Compose `Popup` composable |
| Bundling | Rollup / esbuild | Gradle with Kotlin Multiplatform |

The state and extension layers are nearly identical in structure.
The view layer is where the port diverges most significantly.
