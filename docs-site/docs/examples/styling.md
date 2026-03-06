# Themes and Styling

Kodemirror replaces CSS-based theming with Kotlin data classes and
Compose styling primitives.

## Editor theme

The `EditorTheme` data class controls the editor's visual appearance —
background, foreground, cursor, gutter, selection colors, and the text
style:

```kotlin
import com.monkopedia.kodemirror.view.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.sp

val myTheme = EditorTheme(
    background = Color(0xFF1E1E2E),
    foreground = Color(0xFFCDD6F4),
    cursor = Color(0xFFF5E0DC),
    selection = Color(0xFF45475A),
    activeLineBackground = Color(0x15CDD6F4),
    gutterBackground = Color(0xFF1E1E2E),
    gutterForeground = Color(0xFF6C7086),
    gutterActiveForeground = Color(0xFFCDD6F4),
    contentTextStyle = TextStyle(
        fontFamily = FontFamily.Monospace,
        fontSize = 14.sp,
        lineHeight = (14 * 1.5).sp,
        color = Color(0xFFCDD6F4)
    ),
    dark = true
)
```

Apply it as an extension:

```kotlin
editorTheme.of(myTheme)
```

## Built-in themes

Two themes are provided out of the box:

- `defaultEditorTheme` — dark theme (One Dark-inspired)
- `lightEditorTheme` — light theme

The `:theme-one-dark` module provides a complete `oneDark` extension
that bundles both the editor theme and syntax highlighting colors.

## Syntax highlighting

Syntax highlighting colors are separate from the editor theme. They are
defined with `HighlightStyle`, which maps syntax tags to `SpanStyle`
values:

```kotlin
import com.monkopedia.kodemirror.language.*
import com.monkopedia.kodemirror.lezer.highlight.Tags
import androidx.compose.ui.text.SpanStyle

val myHighlighting = HighlightStyle.define(listOf(
    TagStyleSpec(Tags.keyword, SpanStyle(color = Color(0xFFCBA6F7))),
    TagStyleSpec(Tags.string, SpanStyle(color = Color(0xFFA6E3A1))),
    TagStyleSpec(Tags.comment, SpanStyle(color = Color(0xFF6C7086))),
    TagStyleSpec(Tags.number, SpanStyle(color = Color(0xFFFAB387))),
    TagStyleSpec(Tags.variableName, SpanStyle(color = Color(0xFFCDD6F4))),
    TagStyleSpec(Tags.typeName, SpanStyle(color = Color(0xFFF9E2AF))),
    TagStyleSpec(Tags.function(Tags.variableName), SpanStyle(color = Color(0xFF89B4FA)))
))
```

Apply it with:

```kotlin
syntaxHighlighting(myHighlighting)
```

## Combining theme and highlighting

A complete theme extension bundles both:

```kotlin
val catppuccin = ExtensionList(listOf(
    editorTheme.of(myTheme),
    syntaxHighlighting(myHighlighting)
))
```

This is the same pattern the `oneDark` extension uses.

## Accessing the theme in composables

The theme is distributed via `CompositionLocal`, so panels, tooltips,
and widgets can read it:

```kotlin
@Composable
fun MyPanel() {
    val theme = LocalEditorTheme.current
    Text("Panel", color = theme.foreground)
}
```

## Comparison with upstream CodeMirror

| Upstream (CSS) | Kodemirror (Compose) |
|----------------|---------------------|
| `EditorView.theme({...})` with CSS selectors | `editorTheme.of(EditorTheme(...))` |
| `HighlightStyle.define([{tag, color}])` | `HighlightStyle.define(listOf(TagStyleSpec(...)))` |
| CSS class names | `SpanStyle` values |
| `--cm-editor-background` | `EditorTheme.background: Color` |
| Applied via DOM class swap | Applied via Compose recomposition |

## Related API

- [`EditorTheme`](/api/view/com.monkopedia.kodemirror.view/-editor-theme/) — editor theme data class
- [`HighlightStyle`](/api/language/com.monkopedia.kodemirror.language/-highlight-style/) — tag-to-style mapping
- [`TagStyleSpec`](/api/language/com.monkopedia.kodemirror.language/-tag-style-spec/) — tag style specification
- [`Tags`](/api/lezer-highlight/com.monkopedia.kodemirror.lezer.highlight/-tags/) — standard syntax tag definitions

---

*Based on the [CodeMirror Styling example](https://codemirror.net/examples/styling/).*
