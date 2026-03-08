# Zebra Stripes

This example shows how to build a `ViewPlugin` that applies alternating
line decorations — a common pattern for line-level styling.

## The approach

Use `Decoration.line()` with a `SpanStyle` background color, applied
through a `ViewPlugin` that rebuilds decorations when the document
changes.

## Full implementation

```kotlin
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import com.monkopedia.kodemirror.state.*
import com.monkopedia.kodemirror.view.*

val stripeDecoration = Decoration.line(LineDecorationSpec(
    style = SpanStyle(background = Color(0x10808080))
))

class ZebraPlugin(view: EditorSession) : PluginValue, DecorationSource {
    override var decorations: DecorationSet = computeDecorations(view)
        private set

    override fun update(update: ViewUpdate) {
        if (update.docChanged || update.viewportChanged) {
            decorations = computeDecorations(update.view)
        }
    }

    private fun computeDecorations(view: EditorSession): DecorationSet {
        val builder = RangeSetBuilder<Decoration>()
        val doc = view.state.doc

        for (i in 1..doc.lines) {
            if (i % 2 == 0) {
                val line = doc.line(i)
                builder.add(line.from, line.from, stripeDecoration)
            }
        }

        return builder.finish()
    }
}

val zebraStripes: Extension = ViewPlugin.fromClass(::ZebraPlugin)
    .asExtension()
```

## Using it

```kotlin
val state = EditorState.create(EditorStateConfig(
    doc = "Line 1\nLine 2\nLine 3\nLine 4\nLine 5\nLine 6".asDoc(),
    extensions = ExtensionList(listOf(
        zebraStripes,
        // ...
    ))
))
```

Even-numbered lines get a subtle gray background.

## Configurable step

Make the stripe interval configurable using a `Facet`:

```kotlin
val stripeFacet = Facet.define(
    combine = { values: List<Int> -> values.firstOrNull() ?: 2 }
)

fun zebraStripes(step: Int = 2): Extension = ExtensionList(listOf(
    stripeFacet.of(step),
    ViewPlugin.fromClass(::ConfigurableZebraPlugin).asExtension()
))

class ConfigurableZebraPlugin(view: EditorSession) : PluginValue, DecorationSource {
    private var step = view.state.facet(stripeFacet)
    override var decorations: DecorationSet = computeDecorations(view)
        private set

    override fun update(update: ViewUpdate) {
        val newStep = update.state.facet(stripeFacet)
        if (update.docChanged || newStep != step) {
            step = newStep
            decorations = computeDecorations(update.view)
        }
    }

    private fun computeDecorations(view: EditorSession): DecorationSet {
        val builder = RangeSetBuilder<Decoration>()
        val doc = view.state.doc

        for (i in 1..doc.lines) {
            if (i % step == 0) {
                val line = doc.line(i)
                builder.add(line.from, line.from, stripeDecoration)
            }
        }

        return builder.finish()
    }
}
```

## Key concepts

- **`Decoration.line()`** creates a line-level decoration (applied to the
  whole line, not a character range). In Kodemirror, use `SpanStyle` for
  styling instead of CSS classes.
- **`RangeSetBuilder`** builds a sorted range set. Ranges must be added in
  ascending `from` order.
- **`ViewPlugin.fromClass()`** wraps a class that implements both
  `PluginValue` and `DecorationSource`, automatically wiring up the
  `decorations` property.
- **`DecorationSource`** is the Compose alternative to the upstream
  `decorations` plugin spec option.

---

*Based on the [CodeMirror Zebra Stripes example](https://codemirror.net/examples/zebra/).*
