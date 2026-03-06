# Gutters

The gutter system displays markers (line numbers, breakpoint indicators, etc.)
alongside the editor content.

## Line numbers

The simplest gutter is the built-in line number gutter:

```kotlin
import com.monkopedia.kodemirror.view.lineNumbers

val state = EditorState.create(EditorStateConfig(
    extensions = ExtensionList(listOf(
        lineNumbers,
        // ...
    ))
))
```

`lineNumbers` is a top-level `Extension` property — just add it to your
extensions list.

## Custom gutter markers

Create a custom gutter by implementing `GutterMarker` and providing a
`GutterConfig`:

```kotlin
import com.monkopedia.kodemirror.view.*

class BreakpointMarker : GutterMarker() {
    @Composable
    override fun Content(theme: EditorTheme) {
        Text("●", color = Color.Red)
    }
}

val breakpointGutter = gutter(GutterConfig(
    cssClass = "cm-breakpoints",
    lineMarker = { view, lineFrom ->
        if (isBreakpointLine(view.state, lineFrom)) BreakpointMarker()
        else null
    }
))
```

The `lineMarker` callback receives the `EditorView` and the **character
offset** of the line start (`line.from`), not the line number. Return a
`GutterMarker` to display something, or `null` for no marker.

## GutterMarker

`GutterMarker` is an abstract class with a Compose `Content()` method
(replacing `toDOM()` in upstream CodeMirror):

```kotlin
abstract class GutterMarker : RangeValue() {
    @Composable
    abstract fun Content(theme: EditorTheme)

    open val elementClass: String? get() = null
}
```

## GutterConfig

| Property | Type | Description |
|----------|------|-------------|
| `cssClass` | `String?` | CSS class for the gutter column |
| `lineMarker` | `(EditorView, Int) -> GutterMarker?` | Called per line with `(view, lineFrom)` |
| `lineMarkerChange` | `(ViewUpdate) -> Boolean` | When to re-call `lineMarker` |
| `renderEmptyElements` | `Boolean` | Render markers even when `null` |
| `initialSpacer` | `(EditorView) -> GutterMarker` | Spacer for initial width measurement |
| `updateSpacer` | `(GutterMarker, ViewUpdate) -> GutterMarker` | Update the spacer |

## Tracking breakpoints with a StateField

A complete pattern storing breakpoints in a `StateField`:

```kotlin
val toggleBreakpoint = StateEffect.define<Int>()

val breakpointState = StateField.define(
    create = { emptySet<Int>() },
    update = { breakpoints, tr ->
        var result = breakpoints
        for (effect in tr.effects) {
            effect.asType(toggleBreakpoint)?.let { e ->
                val line = e.value
                result = if (line in result) result - line else result + line
            }
        }
        result
    }
)

val breakpointGutter = gutter(GutterConfig(
    cssClass = "cm-breakpoints",
    lineMarker = { view, lineFrom ->
        val lineNumber = view.state.doc.lineAt(lineFrom).number
        if (lineNumber in view.state.field(breakpointState)) {
            BreakpointMarker()
        } else null
    },
    lineMarkerChange = { update ->
        update.effects.any { it.`is`(toggleBreakpoint) }
    }
))
```

Toggle a breakpoint by dispatching:

```kotlin
view.dispatch(TransactionSpec(
    effects = listOf(toggleBreakpoint.of(lineNumber))
))
```

## Active line gutter

Highlight the gutter for the current line:

```kotlin
import com.monkopedia.kodemirror.view.highlightActiveLineGutter

val extensions = ExtensionList(listOf(
    lineNumbers,
    highlightActiveLineGutter,
    // ...
))
```

---

*Based on the [CodeMirror Gutter example](https://codemirror.net/examples/gutter/).*
