# Bidirectional Text

Kodemirror supports bidirectional (BiDi) text for languages written
right-to-left, such as Arabic and Hebrew.

## Direction

The `Direction` enum represents text direction:

```kotlin
enum class Direction { LTR, RTL }
```

## Per-line text direction

Enable per-line direction detection so each line can independently be
LTR or RTL:

```kotlin
import com.monkopedia.kodemirror.view.perLineTextDirection

val state = EditorState.create(EditorStateConfig(
    extensions = perLineTextDirection.of(true)
))
```

When enabled, the editor detects the dominant direction of each line
and lays it out accordingly.

## Detecting direction

The `autoDirection` function detects the dominant direction of a text
range:

```kotlin
import com.monkopedia.kodemirror.view.autoDirection

val dir = autoDirection(text, from = 0, to = text.length)
// Returns Direction.LTR or Direction.RTL
```

## Computing bidi spans

For fine-grained layout, `computeOrder` splits a line into directional
spans following the Unicode Bidirectional Algorithm:

```kotlin
import com.monkopedia.kodemirror.view.computeOrder

val spans: List<BidiSpan> = computeOrder(
    line = "Hello مرحبا World",
    direction = Direction.LTR
)
```

Each `BidiSpan` represents a contiguous run of text with the same
direction:

```kotlin
data class BidiSpan(
    val from: Int,
    val to: Int,
    val level: Int
) {
    val dir: Direction get() = if ((level % 2) == 0) Direction.LTR else Direction.RTL
}
```

The bidi level follows the Unicode Bidi Algorithm: even levels are LTR,
odd levels are RTL. Level 0 is the base LTR direction.

## Isolates

Use `Isolate` to mark regions that should be treated as directional
isolates (preventing their direction from affecting surrounding text):

```kotlin
import com.monkopedia.kodemirror.view.Isolate

val spans = computeOrder(
    line = lineText,
    direction = Direction.LTR,
    isolates = listOf(
        Isolate(from = 10, to = 20, direction = Direction.RTL)
    )
)
```

## Key points

- **Base direction** is typically LTR for code editors. Enable
  `perLineTextDirection` for documents mixing LTR and RTL content.
- **`computeOrder`** implements a simplified version of UAX#9 (Unicode
  Bidirectional Algorithm) in pure Kotlin.
- The editor's line layout uses bidi spans internally to correctly
  position characters and handle cursor movement across direction
  boundaries.

---

*Based on the [CodeMirror Right-to-Left example](https://codemirror.net/examples/bidi/).*
