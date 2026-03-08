# Migrating from CodeMirror 6

This guide is for JavaScript developers familiar with
[CodeMirror 6](https://codemirror.net/) who want to use Kodemirror in
Kotlin Multiplatform / Compose Multiplatform projects.

## What stays the same

The core conceptual model is identical:

- **State / Transaction / Extension** — immutable state, functional updates,
  composable extensions
- **Facets** — the same `Facet.define` / `facet.of` / `compute` pattern
- **State fields** — `StateField.define` with `create` and `update`
- **View plugins** — `ViewPlugin.define` with `create` and `update`
- **Decorations** — mark, widget, line, replace (same four types)
- **Commands** — `(EditorSession) -> Boolean`
- **Key bindings** — `KeyBinding(key = "Ctrl-s", run = ...)` (same shape)
- **Syntax trees** — Lezer parser infrastructure, `Tree`, `SyntaxNode`, `Tag`

If you've built CodeMirror 6 extensions, the same mental model applies.

## Module mapping

| npm package | Kodemirror module | Notes |
|---|---|---|
| `@codemirror/state` | `:state` | Same API shape |
| `@codemirror/view` | `:view` | Compose rendering |
| `@codemirror/language` | `:language` | Same API |
| `@codemirror/commands` | `:commands` | Same commands |
| `@codemirror/autocomplete` | `:autocomplete` | Same API |
| `@codemirror/search` | `:search` | Same API |
| `@codemirror/lint` | `:lint` | Same API |
| `@codemirror/collab` | `:collab` | Same API |
| `@codemirror/lang-*` | `:lang-*` | Per-language modules |
| `@codemirror/legacy-modes` | `:legacy-modes` | 103 stream parsers |
| `@lezer/common` | `:lezer-common` | Internal |
| `@lezer/lr` | `:lezer-lr` | Internal |
| `@lezer/highlight` | `:lezer-highlight` | `Tags` object |
| `@codemirror/basic-setup` | *(not yet ported)* | Assemble manually |

## Key differences

### Rendering: DOM → Compose

| CodeMirror 6 | Kodemirror |
|---|---|
| DOM `ContentEditable` | Compose `BasicTextField` / Canvas |
| `toDOM()` → `HTMLElement` | `@Composable Content()` |
| CSS classes for styling | `SpanStyle` / `EditorTheme` |
| `document.createElement` | Compose composables |
| `requestAnimationFrame` | `LaunchedEffect` / recomposition |

### Widgets

**CodeMirror 6:**
```javascript
class MyWidget extends WidgetType {
  toDOM() {
    const span = document.createElement("span")
    span.textContent = "✓"
    return span
  }
}
```

**Kodemirror:**
```kotlin
class MyWidget : WidgetType() {
    @Composable
    override fun Content() {
        BasicText("✓")
    }
}
```

### Theming

**CodeMirror 6:**
```javascript
const myTheme = EditorSession.theme({
  "&": { backgroundColor: "#1e1e1e" },
  ".cm-content": { color: "#d4d4d4" },
  ".cm-cursor": { borderLeftColor: "#ffffff" }
})
```

**Kodemirror:**
```kotlin
val myTheme = EditorTheme(
    background = Color(0xFF1E1E1E),
    foreground = Color(0xFFD4D4D4),
    cursor = Color(0xFFFFFFFF)
)
```

| CodeMirror 6 | Kodemirror |
|---|---|
| CSS custom properties | `EditorTheme` data class |
| CSS class selectors | `CompositionLocal` |
| String-based, untyped | Strongly typed |
| DOM class swap | Compose recomposition |

### Syntax highlighting

**CodeMirror 6:**
```javascript
const myHighlighting = HighlightStyle.define([
  { tag: tags.keyword, color: "#0000ff" },
  { tag: tags.string, color: "#008000" }
])
```

**Kodemirror:**
```kotlin
val myHighlighting = HighlightStyle.define {
    Tags.keyword styles SpanStyle(color = Color.Blue)
    Tags.string styles SpanStyle(color = Color.Green)
}
```

Note: The `tags` object is named `Tags` (PascalCase) in Kodemirror.

### Panels and tooltips

**CodeMirror 6** panels and tooltips return DOM elements via `dom` property.
**Kodemirror** uses `@Composable` content lambdas:

```kotlin
// Tooltip
Tooltip(pos = cursorPos) {
    Text("Info at cursor")
}

// Panel
showPanel.of(Panel { Text("Status bar") })
```

### Mark decorations

**CodeMirror 6** marks add CSS classes:
```javascript
Decoration.mark({ class: "cm-highlight" })
```

**Kodemirror** marks apply `SpanStyle`:
```kotlin
Decoration.mark(style = SpanStyle(
    background = Color(0x40FFFF00),
    fontWeight = FontWeight.Bold
))
```

### State creation

**CodeMirror 6:**
```javascript
const state = EditorState.create({
  doc: "Hello",
  extensions: [basicSetup, javascript()]
})
```

**Kodemirror:**
```kotlin
val state = EditorState.create(
    doc = "Hello",
    extensions = extensionListOf(lineNumbers(), history(), javascript())
)
```

### Dependencies

**CodeMirror 6** uses npm with `package.json`.
**Kodemirror** uses Gradle:

```kotlin
// build.gradle.kts
dependencies {
    implementation("com.monkopedia.kodemirror:state:0.1.0-SNAPSHOT")
    implementation("com.monkopedia.kodemirror:view:0.1.0-SNAPSHOT")
    implementation("com.monkopedia.kodemirror:commands:0.1.0-SNAPSHOT")
    implementation("com.monkopedia.kodemirror:lang-javascript:0.1.0-SNAPSHOT")
}
```

## Porting patterns cheat sheet

| JavaScript pattern | Kotlin equivalent |
|---|---|
| `EditorSession.theme({...})` | `EditorTheme(...)` |
| `WidgetType.toDOM()` | `WidgetType.Content()` (composable) |
| `Decoration.mark({class: "..."})` | `Decoration.mark(style = SpanStyle(...))` |
| `tags.keyword` | `Tags.keyword` |
| `StateEffect.define()` | `StateEffect.define<T>()` |
| `state.field(myField)` | `state[myField]` or `state.field(myField)` |
| `Prec.highest(ext)` | `Prec.highest(ext)` (same) |
| `keymap.of([...])` | `keymapOf { "Ctrl-s" { ... } }` |
| `new ChangeSet(...)` | `ChangeSpec.Single(from, to, insert)` |
| `state.doc.sliceString(a, b)` | `state.doc[a..b]` |
| `basicSetup` | `extensionListOf(lineNumbers(), ...)` |

## What's not yet ported

- `basicSetup` / `minimalSetup` convenience bundles
- `@codemirror/language-data` (language metadata for dynamic loading)
- Some `@codemirror/view` utilities (`highlightWhitespace`, `layer`, etc.)
- Some cursor/selection command variants (~30 missing)
- LSP client integration

See the [Extension Index](extensions-index.md) for a complete list of
available extensions.
