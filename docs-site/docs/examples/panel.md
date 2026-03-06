# Panels

Panels are UI strips displayed above or below the editor content. The
search bar is a common example of a panel.

## Showing a panel

Create a `Panel` with a composable lambda and register it through the
`showPanel` facet:

```kotlin
import com.monkopedia.kodemirror.view.*

val myPanel = Panel(
    top = true,
    content = {
        Text("Hello from the top panel!")
    }
)

val state = EditorState.create(EditorStateConfig(
    extensions = showPanel.of(myPanel)
))
```

Set `top = true` for a panel above the editor, or `top = false` (the
default) for one below.

## Panel data class

```kotlin
data class Panel(
    val top: Boolean = false,
    val content: @Composable () -> Unit
)
```

The `content` lambda is a `@Composable` function — unlike upstream
CodeMirror which uses `toDOM()`, you build your panel UI with standard
Compose components.

## Multiple panels

Use the `showPanels` facet to display several panels at once:

```kotlin
val topPanel = Panel(top = true) {
    Row {
        Text("File: ")
        Text("untitled.kt", fontWeight = FontWeight.Bold)
    }
}

val bottomPanel = Panel(top = false) {
    Text("Line 1, Col 1")
}

val state = EditorState.create(EditorStateConfig(
    extensions = showPanels.of(listOf(topPanel, bottomPanel))
))
```

## Dynamic panels with StateField

Toggle a panel on and off using a `StateEffect` and `StateField`:

```kotlin
val togglePanel = StateEffect.define<Boolean>()

val panelState = StateField.define(
    create = { false },
    update = { visible, tr ->
        var result = visible
        for (effect in tr.effects) {
            effect.asType(togglePanel)?.let { e ->
                result = e.value
            }
        }
        result
    }
)

fun createPanelExtension(): Extension {
    return panelState.extension
}

// In your composable, conditionally show the panel based on state:
// val isVisible = view.state.field(panelState)
```

Toggle with:

```kotlin
view.dispatch(TransactionSpec(
    effects = listOf(togglePanel.of(!view.state.field(panelState)))
))
```

## Facets

| Facet | Type | Description |
|-------|------|-------------|
| `showPanel` | `Facet<Panel?, Panel?>` | Show a single panel (first non-null wins) |
| `showPanels` | `Facet<List<Panel>, List<Panel>>` | Show multiple panels simultaneously |

---

*Based on the [CodeMirror Panel example](https://codemirror.net/examples/panel/).*
