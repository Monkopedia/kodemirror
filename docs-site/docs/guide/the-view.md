# The View

The `:view` module (`com.monkopedia.kodemirror.view`) renders the editor
using Jetpack Compose. This is the layer that diverges most from
upstream CodeMirror, which uses the browser DOM.

## The EditorView composable

The entry point for embedding an editor is the `EditorView` composable
function:

```kotlin
@Composable
fun EditorView(
    state: EditorState,
    onUpdate: (Transaction) -> Unit,
    modifier: Modifier = Modifier
)
```

Usage follows the standard Compose state-hoisting pattern:

```kotlin
var editorState by remember {
    mutableStateOf(
        EditorState.create(EditorStateConfig(
            doc = "fun main() {}".asDoc(),
            extensions = ExtensionList(listOf(javascript(), oneDark))
        ))
    )
}

EditorView(
    state = editorState,
    onUpdate = { tr -> editorState = tr.state },
    modifier = Modifier.fillMaxSize()
)
```

When a user types, clicks, or triggers a command, the composable
dispatches a `Transaction` through the `onUpdate` callback. Your code
applies the new state, Compose recomposes, and the editor reflects the
change.

## Inside the composable

The `EditorView` composable manages several internal pieces:

1. **EditorView instance** — a non-Composable class that holds state,
   coordinates plugins, and exposes APIs like `dispatch()` and
   `coordsAtPos()`. It is created once via `remember` and retained
   across recompositions.

2. **ViewPluginHost** — initializes and synchronizes view plugins when
   state changes. Calls `PluginValue.update()` on each plugin after
   every transaction.

3. **LineLayoutCache** — tracks `TextLayoutResult` values from Compose
   text measurement to support coordinate queries.

4. **Layout** — a `Column` containing:
    - Top panels
    - A `Row` with gutters + a `LazyColumn` of document lines
    - Bottom panels
    - A tooltip overlay

5. **Input handling** — Compose modifiers for keyboard (`onKeyEvent`),
   pointer (`pointerInput` with `detectTapGestures` and
   `detectDragGestures`), and focus (`onFocusChanged`).

6. **Selection drawing** — a `drawWithContent` modifier that paints
   selection highlights and cursors onto a Canvas overlay per line.

## The EditorView class

The `EditorView` class (not a composable) is the stateful data holder
that plugins and commands interact with:

```kotlin
class EditorView(
    initialState: EditorState,
    val onUpdate: (Transaction) -> Unit = {}
) {
    var state: EditorState   // current state (updated internally)

    fun dispatch(vararg specs: TransactionSpec)
    fun <V : PluginValue> plugin(plugin: ViewPlugin<V>): V?
    fun coordsAtPos(pos: Int, side: Int = 1): Rect?
    fun posAtCoords(x: Float, y: Float): Int?
    val editable: Boolean
}
```

It is available to nested composables (panels, tooltips, widgets) via a
`CompositionLocal`:

```kotlin
val view = LocalEditorView.current
```

## Theming

Kodemirror replaces CSS-based theming with a Kotlin data class:

```kotlin
data class EditorTheme(
    val background: Color,
    val foreground: Color,
    val cursor: Color,
    val selection: Color,
    val activeLineBackground: Color,
    val gutterBackground: Color,
    val gutterForeground: Color,
    val contentTextStyle: TextStyle,
    val searchMatchBackground: Color,
    val panelBackground: Color,
    val tooltipBackground: Color,
    // ... and more
    val dark: Boolean
)
```

Two built-in themes are provided:

- `defaultEditorTheme` — dark theme (One Dark-inspired defaults)
- `lightEditorTheme` — light theme

### Applying a theme

Themes are applied through the `editorTheme` facet:

```kotlin
editorTheme.of(lightEditorTheme)
```

The composable reads the theme from state and distributes it via
`CompositionLocal`:

```kotlin
val theme = LocalEditorTheme.current
```

### Comparison with upstream

| Upstream CodeMirror | Kodemirror |
|---------------------|-----------|
| CSS custom properties (`--cm-background`, etc.) | `EditorTheme` data class properties |
| CSS class selectors on DOM elements | `CompositionLocal` provided to all composables |
| String-based, untyped | Strongly typed `Color`, `TextStyle` |
| Runtime: DOM class swap + CSS recalculation | Runtime: Compose recomposition with new theme |

The `:theme-one-dark` module provides a pre-built `oneDark` extension
that sets both the `EditorTheme` and syntax highlighting colors.

## Panels

Panels are composable regions that appear above or below the editor
content. They are registered via facets:

```kotlin
val myPanel = Panel(
    top = true,
    content = @Composable {
        Text("Header panel", color = LocalEditorTheme.current.foreground)
    }
)

// Register via facet
showPanel.of(myPanel)
```

The `showPanel` facet provides a single panel; `showPanels` collects
multiple panels. The search module uses panels for the find/replace UI.

Panels render inside the editor's `Column` layout with a 1px border
separator in the theme's `panelBorderColor`.

## Tooltips

Tooltips are positioned relative to a document position and rendered
using Compose's `Popup` composable:

```kotlin
data class Tooltip(
    val pos: Int,                          // document position
    val above: Boolean = false,            // show above the position
    val strictSide: Boolean = false,       // don't flip if clipped
    val content: @Composable () -> Unit    // tooltip UI
)
```

Register a tooltip through the `showTooltip` or `showTooltips` facets.
For hover-based tooltips, use the `hoverTooltip` helper:

```kotlin
hoverTooltip { view, pos ->
    Tooltip(
        pos = pos,
        content = @Composable {
            Text("Info at position $pos")
        }
    )
}
```

The autocomplete module uses tooltips for the completion dropdown.

## Gutters

Gutters appear as a column to the left of the editor content. The most
common gutter is line numbers. Custom gutters are registered via the
`gutters` facet with a `GutterConfig`:

```kotlin
data class GutterConfig(
    val cssClass: String? = null,
    val lineMarker: ((EditorView, Int) -> GutterMarker?)? = null,
    val lineMarkerChange: ((ViewUpdate) -> Boolean)? = null,
    val renderEmptyElements: Boolean = false,
    val initialSpacer: ((EditorView) -> GutterMarker)? = null,
    val updateSpacer: ((GutterMarker, ViewUpdate) -> GutterMarker)? = null
)
```

Custom gutter markers implement `GutterMarker` and provide a
`@Composable Content(theme: EditorTheme)` method.

## Commands and key handling

Commands are functions with the signature `(EditorView) -> Boolean`.
They return `true` if they handled the event, `false` to let it
propagate.

Key bindings are registered through the `keymap` facet:

```kotlin
data class KeyBinding(
    val key: String? = null,       // e.g. "Ctrl-s", "Mod-z"
    val run: ((EditorView) -> Boolean)? = null,
    val shift: ((EditorView) -> Boolean)? = null,
    val any: ((EditorView, KeyEvent) -> Boolean)? = null,
    val preventDefault: Boolean = true
)

keymap.of(listOf(
    KeyBinding(key = "Mod-z", run = undo),
    KeyBinding(key = "Mod-Shift-z", run = redo)
))
```

Key names use Compose `KeyEvent` processing. `Mod` is translated to
`Ctrl` on Linux/Windows and `Meta` on macOS.

Unlike upstream CodeMirror where commands can manipulate the DOM,
Kodemirror commands only dispatch transactions. All UI updates happen
through Compose recomposition:

```kotlin
val cursorCharLeft: (EditorView) -> Boolean = { view ->
    val newSel = view.state.selection.ranges.map { range ->
        moveByChar(view.state, range, forward = false)
    }
    view.dispatch(TransactionSpec(
        selection = SelectionSpec.EditorSelectionSpec(
            EditorSelection.create(newSel, view.state.selection.mainIndex)
        ),
        scrollIntoView = true
    ))
    true
}
```

## Coordinate queries

The `EditorView` class provides methods for mapping between document
positions and pixel coordinates:

```kotlin
view.coordsAtPos(pos)          // Rect? — pixel rectangle at position
view.posAtCoords(x, y)         // Int? — document position at point
```

These work by consulting the `LineLayoutCache`, which stores
`TextLayoutResult` values produced by Compose's text measurement. In
upstream CodeMirror these queries go through DOM measurement APIs like
`getBoundingClientRect`.

## ViewUpdate

When state changes, every view plugin receives a `ViewUpdate`:

```kotlin
class ViewUpdate(
    val view: EditorView,
    val state: EditorState,
    val transactions: List<Transaction>,
    val viewportChanged: Boolean,
    val heightChanged: Boolean,
    val focusChanged: Boolean
) {
    val startState: EditorState
    val docChanged: Boolean
    val selectionSet: Boolean
    val changes: ChangeSet
    val reconfigured: Boolean
}
```

Plugins use this to react to changes — for example, recomputing
decorations when the document changes. See
[Extending](extending.md) for how to define view plugins.
