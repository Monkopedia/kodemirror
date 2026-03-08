# Extension Architecture

This guide covers when to use each extension mechanism and how to
compose them.

## Choosing the Right Extension Type

### StateField

Use `StateField` when you need to store computed data that updates
with every transaction. The field value is available to all extensions.

**Good for:**

- Counters, flags, or derived data from the document
- Data that multiple plugins need to read
- State that should survive reconfiguration

```kotlin
val wordCount = StateField.define(StateFieldSpec(
    create = { state -> state.doc.sliceString(0).split("\\s+".toRegex()).size },
    update = { _, tr -> tr.state.doc.sliceString(0).split("\\s+".toRegex()).size }
))
```

### Facet

Use `Facet` when multiple extensions need to contribute values that
are combined into a single result.

**Good for:**

- Configuration that can come from multiple sources
- Registering event handlers, keymaps, or plugins
- Values where "last wins" or "merge all" semantics make sense

```kotlin
val myConfig: Facet<Int, Int> = Facet.define(combine = { values -> values.sum() })
```

### StateEffect

Use `StateEffect` to signal events in transactions. Effects are
processed by `StateField.update` functions.

**Good for:**

- Triggering state changes from commands or plugins
- Communication between extensions via transactions
- Representing user actions (toggle, set, add, remove)

```kotlin
val highlight = StateEffect.define<IntRange>()
// Dispatch:
session.dispatch(TransactionSpec(effects = listOf(highlight.of(0..10))))
```

### ViewPlugin

Use `ViewPlugin` when you need to react to view updates or contribute
decorations that depend on the visible viewport.

**Good for:**

- Decorations that change based on viewport or selection
- Side effects when the view updates (logging, analytics)
- Anything that needs access to `EditorSession`

```kotlin
val myPlugin = ViewPlugin.define(
    create = { session -> MyPluginValue(session) },
    decorations = { plugin -> plugin.decorations }
)
```

## Decision Table

| Need | Use |
|------|-----|
| Store data derived from document | `StateField` |
| Collect config from multiple extensions | `Facet` |
| Trigger state changes | `StateEffect` |
| React to view updates | `ViewPlugin` |
| Provide decorations | `ViewPlugin` with `decorations` |
| Style syntax tokens | `HighlightStyle` + `syntaxHighlighting()` |
| Add keyboard shortcuts | `keymap.of(...)` |
| Add a UI panel | `showPanel` facet |
| Add a tooltip | `showTooltip` facet |
| Add gutter markers | `gutter(GutterConfig(...))` |

## Extension Ordering and Precedence

Extensions are processed in registration order. For facets with
a "last wins" combine function (like `editorTheme`), later extensions
override earlier ones.

Use `Prec` to control ordering:

```kotlin
Prec.highest(myExtension)  // Runs first / highest priority
Prec.high(myExtension)
Prec.default(myExtension)  // Normal priority (the default)
Prec.low(myExtension)
Prec.lowest(myExtension)   // Runs last / lowest priority
```

For keymaps, the first matching binding wins. Place more specific
keymaps at higher precedence.

## How Facet Combine Functions Work

When defining a facet, the `combine` function merges all contributed
values:

```kotlin
// "Last wins" — for single-value config like theme
val theme: Facet<EditorTheme, EditorTheme> = Facet.define(
    combine = { values -> values.lastOrNull() ?: defaultTheme }
)

// "Collect all" — for registries like plugins
val plugins: Facet<ViewPlugin<*>, List<ViewPlugin<*>>> = Facet.define()
// Default combine: collects into a list

// "Merge" — for additive config
val extraKeys: Facet<List<KeyBinding>, List<KeyBinding>> = Facet.define(
    combine = { values -> values.flatten() }
)
```

## Building Reusable Extensions

A well-designed extension:

1. **Exposes configuration via a facet** so users can customize it
2. **Uses `StateField` for internal state** rather than mutable variables
3. **Composes from smaller extensions** using `ExtensionList` or `+`
4. **Documents its facets and effects** so others can extend it

```kotlin
fun myFeature(config: MyConfig = MyConfig()): Extension {
    val field = StateField.define(StateFieldSpec(
        create = { config.initialValue },
        update = { value, tr -> /* update logic */ value }
    ))
    val plugin = ViewPlugin.define(
        create = { session -> MyPlugin(session) },
        decorations = { it.decorations }
    )
    return field + plugin.asExtension() + myFacet.of(config)
}
```

## Transaction Filters and Extenders

**Transaction filters** can modify or cancel transactions before they
are applied:

```kotlin
transactionFilter.of { tr ->
    // Return modified TransactionSpec or the original
    tr
}
```

**Transaction extenders** add effects or annotations after the
transaction is built but before it is applied:

```kotlin
transactionExtender.of { tr ->
    // Return additional effects/annotations
    null  // or a TransactionSpec with extra effects
}
```

Use filters sparingly — they run on every transaction and can be
confusing to debug.
