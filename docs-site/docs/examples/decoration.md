# Decorations

Decorations add visual annotations to the editor — highlighting ranges,
inserting widgets, styling lines — without modifying the document text.

## Mark decorations

A mark decoration applies a `SpanStyle` to a range of text:

```kotlin
import com.monkopedia.kodemirror.view.*
import com.monkopedia.kodemirror.state.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.font.FontWeight

val highlightDeco = Decoration.mark(MarkDecorationSpec(
    style = SpanStyle(
        background = Color(0x40FFFF00),
        fontWeight = FontWeight.Bold
    )
))
```

In upstream CodeMirror, marks add CSS classes. In Kodemirror, marks
apply Compose `SpanStyle` values via `AnnotatedString`.

## Widget decorations

A widget decoration inserts a `@Composable` at a document position:

```kotlin
import androidx.compose.runtime.Composable
import androidx.compose.foundation.text.BasicText
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.sp

class CheckmarkWidget : WidgetType() {
    @Composable
    override fun Content() {
        BasicText(
            text = "\u2713",
            style = TextStyle(color = Color.Green, fontSize = 12.sp)
        )
    }

    override fun eq(other: WidgetType): Boolean =
        other is CheckmarkWidget
}

val widgetDeco = Decoration.widget(WidgetDecorationSpec(
    widget = CheckmarkWidget(),
    side = 1  // 1 = after the position, -1 = before
))
```

Upstream CodeMirror widgets implement `toDOM()` returning an
`HTMLElement`. Kodemirror widgets implement `@Composable Content()`.

## Line decorations

A line decoration styles an entire line:

```kotlin
val errorLine = Decoration.line(LineDecorationSpec(
    style = SpanStyle(background = Color(0x20FF0000))
))
```

## Replace decorations

A replace decoration hides a range, optionally replacing it with a
widget:

```kotlin
val foldDeco = Decoration.replace(ReplaceDecorationSpec(
    widget = FoldPlaceholderWidget()  // optional
))
```

## Building decoration sets

Decorations are collected into a `DecorationSet` using a builder.
Ranges must be added in ascending `from` order:

```kotlin
val builder = RangeSetBuilder<Decoration>()
builder.add(from = 5, to = 10, value = highlightDeco)
builder.add(from = 20, to = 20, value = widgetDeco)
val decoSet: DecorationSet = builder.finish()
```

## Providing decorations via a state field

To make decorations persistent and reactive, store them in a
`StateField` and provide them through the `decorations` facet:

```kotlin
val highlightField = StateField.define(StateFieldSpec(
    create = { state -> buildHighlights(state) },
    update = { decos, tr ->
        if (tr.docChanged) buildHighlights(tr.state)
        else decos
    },
    provide = { field ->
        decorations.from(field)
    }
))

fun buildHighlights(state: EditorState): DecorationSet {
    val builder = RangeSetBuilder<Decoration>()
    // Find ranges to highlight and add them...
    return builder.finish()
}
```

Include `highlightField` in your extensions list — the `provide`
function feeds the field's value into the `decorations` facet
automatically.

## Providing decorations via a view plugin

For decorations that depend on view state (like viewport), use a
`ViewPlugin`:

```kotlin
class HighlightPlugin(state: EditorState) : PluginValue {
    var decos: DecorationSet = buildDecos(state)
        private set

    override fun update(update: ViewUpdate) {
        if (update.docChanged) {
            decos = buildDecos(update.state)
        }
    }

    private fun buildDecos(state: EditorState): DecorationSet {
        val builder = RangeSetBuilder<Decoration>()
        // ... add decorations
        return builder.finish()
    }
}

val highlightPlugin = ViewPlugin.define(
    create = { view -> HighlightPlugin(view.state) },
    configure = {
        copy(decorations = { plugin ->
            (plugin as? HighlightPlugin)?.decos ?: RangeSet.empty()
        })
    }
)
```

Include `highlightPlugin.asExtension()` in your extensions.

---

*Based on the [CodeMirror Decoration example](https://codemirror.net/examples/decoration/).*
