# Selection Handling

Selections in Kodemirror can be cursors (empty ranges), single ranges,
or multiple ranges for multi-cursor editing.

## Setting the cursor

Dispatch a transaction with a `SelectionSpec.CursorSpec` to move the
cursor:

```kotlin
import com.monkopedia.kodemirror.state.*

// Move cursor to position 10
view.dispatch(TransactionSpec(
    selection = SelectionSpec.CursorSpec(anchor = 10)
))
```

## Selecting a range

Use `EditorSelection.range()` to create a range with an anchor and head:

```kotlin
// Select characters 5..15
view.dispatch(TransactionSpec(
    selection = SelectionSpec.EditorSelectionSpec(
        EditorSelection.single(anchor = 5, head = 15)
    )
))
```

## Multiple selections

Enable multi-cursor support with the `allowMultipleSelections` facet,
then create selections with `EditorSelection.create()`:

```kotlin
// Enable in state config
val state = EditorState.create(EditorStateConfig(
    extensions = ExtensionList(listOf(
        allowMultipleSelections.of(true),
        // ...
    ))
))

// Set multiple cursors
view.dispatch(TransactionSpec(
    selection = SelectionSpec.EditorSelectionSpec(
        EditorSelection.create(listOf(
            EditorSelection.cursor(5),
            EditorSelection.cursor(20),
            EditorSelection.cursor(35)
        ))
    )
))
```

## Reading the selection

The current selection is available on the state:

```kotlin
val sel = state.selection
val main = sel.main           // the primary range
val allRanges = sel.ranges    // all ranges

// Check if the main selection is a cursor or a range
if (main.empty) {
    println("Cursor at ${main.head}")
} else {
    println("Selected ${main.from}..${main.to}")
}
```

## Selection properties

Each `SelectionRange` has:

| Property | Description |
|----------|-------------|
| `anchor` | The fixed end of the selection |
| `head` | The end that moves when extending |
| `from` | The lower boundary (min of anchor, head) |
| `to` | The upper boundary (max of anchor, head) |
| `empty` | `true` when it's a cursor (`from == to`) |

## Mapping through changes

Selections map through document changes automatically when you use
transactions. If you need to map a selection manually:

```kotlin
val newSel = sel.map(changes)
```

## Finding a word

`state.wordAt(pos)` returns a `SelectionRange` covering the word at
the given position, useful for double-click selection or word-based
operations:

```kotlin
val word = state.wordAt(pos)
if (word != null) {
    view.dispatch(TransactionSpec(
        selection = SelectionSpec.EditorSelectionSpec(
            EditorSelection.single(word.from, word.to)
        )
    ))
}
```

## Related API

- [`EditorSelection`](/api/state/com.monkopedia.kodemirror.state/-editor-selection/) — selection state
- [`SelectionRange`](/api/state/com.monkopedia.kodemirror.state/-selection-range/) — a single selection range
- [`SelectionSpec`](/api/state/com.monkopedia.kodemirror.state/-selection-spec/) — selection specification for transactions

---

*Based on the [CodeMirror Selection example](https://codemirror.net/examples/selection/).*
