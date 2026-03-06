# Tooltips

Tooltips display floating UI anchored to a document position.

## Basic tooltip

Create a `Tooltip` and register it through the `showTooltip` facet:

```kotlin
import com.monkopedia.kodemirror.view.*

val myTooltip = Tooltip(
    pos = 42,
    above = true,
    content = {
        Text("Information at position 42")
    }
)

val state = EditorState.create(EditorStateConfig(
    extensions = showTooltip.of(myTooltip)
))
```

## Tooltip data class

```kotlin
data class Tooltip(
    val pos: Int,
    val above: Boolean = false,
    val strictSide: Boolean = false,
    val content: @Composable () -> Unit
)
```

| Property | Description |
|----------|-------------|
| `pos` | Document position to anchor the tooltip to |
| `above` | Show above the anchor position (default: below) |
| `strictSide` | Don't flip to the other side even if off-screen |
| `content` | Composable content (replaces `toDOM()` in upstream) |

## Cursor tooltip

Show a tooltip that follows the cursor using a `StateField`:

```kotlin
val cursorTooltipField = StateField.define(
    create = { state -> getCursorTooltips(state) },
    update = { _, tr -> getCursorTooltips(tr.state) },
    provide = { field -> showTooltips.from(field) }
)

fun getCursorTooltips(state: EditorState): List<Tooltip> {
    return state.selection.ranges.map { range ->
        Tooltip(
            pos = range.head,
            above = true,
            content = {
                val line = state.doc.lineAt(range.head)
                Text("Line ${line.number}, Col ${range.head - line.from + 1}")
            }
        )
    }
}
```

## Hover tooltip

Show a tooltip when the user hovers over text:

```kotlin
val myHoverTooltip = hoverTooltip { view, pos ->
    val word = view.state.wordAt(pos) ?: return@hoverTooltip null

    val text = view.state.sliceDoc(word.from, word.to)
    Tooltip(
        pos = word.from,
        above = true,
        content = {
            Text("Word: $text")
        }
    )
}

val state = EditorState.create(EditorStateConfig(
    extensions = myHoverTooltip
))
```

`hoverTooltip` takes a function `(EditorView, Int) -> Tooltip?` that
receives the view and the document position under the pointer. Return
`null` for no tooltip.

## Multiple tooltips

Use the `showTooltips` facet to display several tooltips at once:

```kotlin
val state = EditorState.create(EditorStateConfig(
    extensions = showTooltips.of(listOf(tooltip1, tooltip2))
))
```

## Facets

| Facet | Type | Description |
|-------|------|-------------|
| `showTooltip` | `Facet<Tooltip?, Tooltip?>` | Single tooltip (first non-null wins) |
| `showTooltips` | `Facet<List<Tooltip>, List<Tooltip>>` | Multiple simultaneous tooltips |

## Related API

- [`Tooltip`](/api/view/com.monkopedia.kodemirror.view/-tooltip/) — tooltip data class
- [`showTooltip`](/api/view/com.monkopedia.kodemirror.view/show-tooltip.html) — facet for single tooltips
- [`showTooltips`](/api/view/com.monkopedia.kodemirror.view/show-tooltips.html) — facet for multiple tooltips

---

*Based on the [CodeMirror Tooltip example](https://codemirror.net/examples/tooltip/).*
