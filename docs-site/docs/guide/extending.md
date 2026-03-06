# Extending

Kodemirror's extension system lets you add behavior, state, and visual
elements to the editor. Extensions compose freely — you can combine any
number of them by nesting them in lists, and the system flattens and
deduplicates them during configuration.

## Extension basics

The `Extension` interface is implemented by facets, state fields, view
plugins, compartments, and wrapper types. You pass extensions to
`EditorStateConfig` when creating state:

```kotlin
val state = EditorState.create(EditorStateConfig(
    doc = "hello".asDoc(),
    extensions = ExtensionList(listOf(
        EditorState.tabSize.of(2),
        myStateField,
        myViewPlugin.asExtension()
    ))
))
```

`ExtensionList` wraps a list of extensions. Extensions can be nested
arbitrarily — the system flattens them.

## Facets

A `Facet<Input, Output>` collects values from multiple extension
providers and combines them into a single output. It is the primary way
to expose configuration.

### Defining a facet

```kotlin
// Collect all values into a list
val myFacet = Facet.define<String, List<String>>(
    combine = { values -> values }
)

// Take the first value
val tabSize = Facet.define<Int, Int>(
    combine = { values -> values.firstOrNull() ?: 4 }
)

// Boolean "any" combiner
val allowMulti = Facet.define<Boolean, Boolean>(
    combine = { values -> values.any { it } },
    static = true
)
```

The `static` flag means the facet cannot depend on dynamically computed
state.

### Providing values

```kotlin
// Static value
myFacet.of("hello")

// Computed from state (recomputed when dependencies change)
myFacet.compute(listOf(Slot.Doc)) { state ->
    "Document has ${state.doc.lines} lines"
}

// Multiple values from one provider
myFacet.computeN(listOf(Slot.Doc)) { state ->
    listOf("line count: ${state.doc.lines}", "length: ${state.doc.length}")
}

// Derived from a state field
myFacet.from(myField) { fieldValue -> fieldValue.toString() }
```

Dependencies are expressed as `Slot` values:

| Slot | Triggers recomputation when... |
|------|-------------------------------|
| `Slot.Doc` | the document changes |
| `Slot.Selection` | the selection changes |
| `Slot.FacetSlot(reader)` | a facet value changes |
| `Slot.FieldSlot(field)` | a state field changes |

### Reading facets

```kotlin
val value = state.facet(myFacet)
```

### FacetReader

`FacetReader` is the read-side interface of a `Facet`. Use
`FacetReader` when code only needs to _read_ from a facet, not
provide values to it. This makes APIs more flexible — callers can pass
either a `Facet` or a computed facet reader.

```kotlin
// A function that reads a facet without needing to know its input type
fun readAnyFacet(state: EditorState, reader: FacetReader<Int>) {
    val value: Int = state.facet(reader)
}
```

`Facet<I, O>` implements `FacetReader<O>`, so you can pass a facet
anywhere a reader is expected.

## State fields

A `StateField<Value>` stores a value that persists across transactions.
On every transaction, its `update` function produces the next value.

### Defining a state field

```kotlin
val editCount = StateField.define(StateFieldSpec(
    create = { _ -> 0 },
    update = { value, tr ->
        if (tr.docChanged) value + 1 else value
    }
))
```

The field itself is an `Extension`, so you include it in your extensions
list.

### Reading a state field

```kotlin
val count = state.field(editCount)
```

### Providing to a facet

A state field can automatically feed its value into a facet:

```kotlin
val decorationField = StateField.define(StateFieldSpec(
    create = { state -> computeDecorations(state) },
    update = { decos, tr ->
        if (tr.docChanged) computeDecorations(tr.state) else decos
    },
    provide = { field ->
        decorations.from(field)
    }
))
```

## State effects

`StateEffect<Value>` values carry typed data through transactions
without modifying the document. They are the mechanism for inter-extension
communication.

### Defining and dispatching

```kotlin
val togglePanel = StateEffect.define<Boolean>()

// Dispatch
view.dispatch(TransactionSpec(
    effects = listOf(togglePanel.of(true))
))
```

### Reading effects in a state field

```kotlin
val panelOpen = StateField.define(StateFieldSpec(
    create = { false },
    update = { value, tr ->
        var result = value
        for (effect in tr.effects) {
            val toggle = effect.asType(togglePanel)
            if (toggle != null) result = toggle.value
        }
        result
    }
))
```

### Position-dependent effects

If your effect contains document positions, provide a `map` function so
positions update when the document changes:

```kotlin
val addBookmark = StateEffect.define<Int>(
    map = { pos, changes -> changes.mapPos(pos) }
)
```

### Built-in effects

- `StateEffect.reconfigure` — replace a compartment's extensions
- `StateEffect.appendConfig` — add extensions to the configuration

## View plugins

A `ViewPlugin<V>` creates a `PluginValue` instance that receives
`ViewUpdate` notifications on every state change. View plugins are used
for behavior that needs to react to the view — computing decorations,
responding to focus changes, etc.

### Defining a view plugin

```kotlin
class MyPlugin(private val view: EditorView) : PluginValue {
    override fun update(update: ViewUpdate) {
        if (update.docChanged) {
            // react to document changes
        }
    }

    override fun destroy() {
        // cleanup
    }
}

val myPlugin = ViewPlugin.define { view -> MyPlugin(view) }
```

Include it in extensions with `myPlugin.asExtension()`.

### Configuring a plugin with `PluginSpec`

`ViewPlugin.define` accepts an optional `configure` lambda that
customizes the `PluginSpec`. This is how you attach decoration
providers, event handlers, or other plugin-level options:

```kotlin
val myPlugin = ViewPlugin.define(
    create = { view -> MyPlugin(view) },
    configure = {
        // `this` is a PluginSpec — copy it with new properties
        copy(
            decorations = { plugin -> (plugin as MyPlugin).decorations },
            eventHandlers = mapOf("keydown" to { plugin, event -> false })
        )
    }
)
```

### Plugins with decorations

To contribute decorations, implement `DecorationSource`:

```kotlin
class HighlightPlugin(state: EditorState) : DecorationSource {
    override var decorations: DecorationSet = buildDecos(state)
        private set

    override fun update(update: ViewUpdate) {
        if (update.docChanged) {
            decorations = buildDecos(update.state)
        }
    }

    private fun buildDecos(state: EditorState): DecorationSet {
        val builder = RangeSetBuilder<Decoration>()
        // add decorations...
        return builder.finish()
    }
}

val highlightPlugin = ViewPlugin.define(
    create = { view -> HighlightPlugin(view.state) },
    configure = {
        copy(decorations = { plugin ->
            (plugin as? HighlightPlugin)?.decorations ?: RangeSet.empty()
        })
    }
)
```

### Accessing a plugin instance

```kotlin
val pluginValue = view.plugin(myPlugin)
```

## Decorations

Decorations add visual annotations to the editor without modifying the
document. There are four types:

### Mark decorations

Apply inline styling to a range of text:

```kotlin
Decoration.mark(MarkDecorationSpec(
    style = SpanStyle(
        color = Color.Red,
        fontWeight = FontWeight.Bold
    )
))
```

In upstream CodeMirror, marks add CSS classes. In Kodemirror, marks apply
Compose `SpanStyle` values through `AnnotatedString`.

### Widget decorations

Insert a composable at a position:

```kotlin
class InfoWidget(private val message: String) : WidgetType() {
    @Composable
    override fun Content() {
        Text(message, style = TextStyle(color = Color.Gray, fontSize = 10.sp))
    }
}

Decoration.widget(WidgetDecorationSpec(
    widget = InfoWidget("note"),
    side = 1  // after the position
))
```

Upstream CodeMirror widgets implement `toDOM()` returning an
`HTMLElement`. Kodemirror widgets implement `@Composable Content()`.

### Line decorations

Style an entire line:

```kotlin
Decoration.line(LineDecorationSpec(
    style = SpanStyle(background = Color(0x20FF0000))
))
```

### Replace decorations

Hide or replace a range, optionally with a widget:

```kotlin
Decoration.replace(ReplaceDecorationSpec(
    widget = FoldWidget()  // optional replacement widget
))
```

### Building decoration sets

Decorations are collected into a `DecorationSet` (alias for
`RangeSet<Decoration>`) using a builder:

```kotlin
val builder = RangeSetBuilder<Decoration>()
builder.add(from = 5, to = 10, value = myMarkDecoration)
builder.add(from = 15, to = 15, value = myWidgetDecoration)
val decoSet: DecorationSet = builder.finish()
```

Positions must be added in order (ascending `from`).

## Compartments

A `Compartment` wraps a subset of extensions that can be dynamically
replaced at runtime without recreating the entire state.

```kotlin
val themeCompartment = Compartment()

// Initial configuration
val state = EditorState.create(EditorStateConfig(
    extensions = themeCompartment.of(oneDark)
))

// Later, switch theme
val tr = state.update(TransactionSpec(
    effects = listOf(
        themeCompartment.reconfigure(lightEditorTheme.let { editorTheme.of(it) })
    )
))
```

This is the recommended way to handle configuration that changes at
runtime — language switching, theme toggling, enabling/disabling features.

## Precedence

By default, extensions are processed in the order they appear.
You can override this with `Prec`:

```kotlin
Prec.highest(myExtension)   // runs first
Prec.high(myExtension)
Prec.default(myExtension)   // default level
Prec.low(myExtension)
Prec.lowest(myExtension)    // runs last
```

This affects how facet values are ordered when combined. For example,
keymaps at higher precedence get first chance to handle a key event.

## Putting it together

Here is how the search extension composes these primitives:

```kotlin
fun search(): Extension {
    return ExtensionList(listOf(
        // State fields to track panel and query state
        searchQueryField,
        searchPanelOpenField,

        // Computed facet — show/hide the search panel
        showPanels.compute(
            listOf(Slot.FieldSlot(searchPanelOpenField))
        ) { state ->
            if (state.field(searchPanelOpenField))
                listOf(Panel(top = true, content = @Composable { SearchPanel() }))
            else emptyList()
        },

        // View plugin with decorations for match highlighting
        ViewPlugin.define(
            create = { view -> SearchHighlightPlugin(view.state) },
            configure = {
                copy(decorations = { plugin ->
                    (plugin as? SearchHighlightPlugin)?.decorations
                        ?: RangeSet.empty()
                })
            }
        ).asExtension(),

        // Keybindings
        keymap.of(searchKeymap)
    ))
}
```

This single `search()` call provides state management, a UI panel,
decorations, and key bindings — all composed from the same extension
primitives available to any user code.
