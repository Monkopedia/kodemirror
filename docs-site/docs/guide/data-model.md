# Data Model

The core data model lives in the `:state` module
(`com.monkopedia.kodemirror.state`). It defines how documents, changes,
selections, and editor state work. Everything here is pure Kotlin — no
Compose dependency.

## Documents

A document is represented by the `Text` class, an immutable tree
structure optimized for large files. `Text` stores content as a list of
lines and supports efficient slicing, replacement, and iteration.

```kotlin
// Create a document from a list of lines
val doc = Text.of(listOf("line 1", "line 2", "line 3"))

doc.length       // total character count (including newlines)
doc.lines        // number of lines (always >= 1)
```

> **Note:** `Text.of()` expects each element to be a single line
> without embedded newline characters. To create a `Text` from a full
> string (with newlines), use `Text.of("content".split("\n"))`.

### Querying lines

`Text` provides two ways to look up line information, both returning a
`Line` value with `from`, `to`, `number`, and `text` properties:

```kotlin
val line = doc.lineAt(pos)   // line containing character position `pos`
val line = doc.line(2)       // line by 1-based line number
```

### Extracting content

```kotlin
doc.sliceString(from, to)        // substring
doc.slice(from, to)              // new Text value
doc.toString()                   // full content as a single string
```

### Iteration

`Text` provides several iterator variants via `TextIterator`:

```kotlin
val iter = doc.iter()              // forward from start
val iter = doc.iter(-1)            // backward from end
val iter = doc.iterRange(10, 50)   // iterator over a character range
val iter = doc.iterLines()         // iterate line by line
val iter = doc.iterLines(3)        // iterate from line 3 onward
```

Each call to `iter.next()` advances the iterator and returns the
next chunk of text. Check `iter.done` to know when iteration is
complete. The `iter.value` property holds the current chunk.

`Text` is serialized with `toJSON()` (returns `List<String>`) and
restored with `Text.of(lines)`.

## Changes

Document modifications are described by `ChangeSet`, which maps an old
document to a new one. A `ChangeSet` records which ranges are kept,
deleted, or replaced with new text.

### Creating changes

The simplest way is through `ChangeSpec`:

```kotlin
// Replace positions 5..10 with "world"
val spec = ChangeSpec.Single(
    from = 5, to = 10,
    insert = InsertContent.StringContent("world")
)

// Build a ChangeSet from a spec
val changes = ChangeSet.of(spec, length = doc.length)
```

You can also specify an insertion-only change (omit `to`) or a
deletion (omit `insert`).

### Applying and composing

```kotlin
val newDoc = changes.apply(doc)
```

Two sequential change sets can be composed into one:

```kotlin
val combined = first.compose(second)
```

And a change set can be inverted relative to the original document to
produce an undo operation:

```kotlin
val undo = changes.invert(doc)
```

### Mapping positions

When a change modifies the document, positions in the old document need
to be mapped to the new document:

```kotlin
val newPos = changes.mapPos(oldPos)
```

The `assoc` parameter controls which side of an insertion the position
sticks to (`-1` = before, `1` = after). For more control, `MapMode`
offers `TrackDel`, `TrackBefore`, and `TrackAfter` — these return `null`
when the surrounding context has been deleted.

### Iterating changes

```kotlin
changes.iterChanges { fromA, toA, fromB, toB, inserted ->
    // fromA..toA in the old doc became fromB..toB in the new doc
    // `inserted` is the Text that was inserted
}
```

## Selections

An `EditorSelection` contains one or more `SelectionRange` values. Each
range has an `anchor` (the fixed end) and a `head` (the end that moves
when the user extends the selection). When `anchor == head` the range is
a cursor.

```kotlin
// Single cursor at position 5
val sel = EditorSelection.single(5)

// Cursor with explicit anchor and head
val range = EditorSelection.range(anchor = 10, head = 20)

// Multiple cursors
val multi = EditorSelection.create(listOf(
    EditorSelection.cursor(5),
    EditorSelection.cursor(15)
))
```

### Useful properties

```kotlin
sel.main          // the primary range (sel.ranges[sel.mainIndex])
range.from        // lower boundary (min of anchor, head)
range.to          // upper boundary
range.empty       // true when it's a cursor
```

### Mapping through changes

Selections map through changes the same way positions do:

```kotlin
val newSel = sel.map(changes)
```

Multiple selections are enabled by the `allowMultipleSelections` facet:

```kotlin
allowMultipleSelections.of(true)
```

## Transactions

A `Transaction` is the mechanism for updating state. It bundles
together changes, selection updates, effects, and annotations into a
single atomic update.

### Creating transactions

The primary API is `EditorState.update()`:

```kotlin
val tr = state.update(
    TransactionSpec(
        changes = ChangeSpec.Single(from = 0, to = 5,
            insert = InsertContent.StringContent("Hello")),
        selection = SelectionSpec.CursorSpec(anchor = 5),
        scrollIntoView = true,
        userEvent = "input"
    )
)
```

`TransactionSpec` fields:

| Field | Type | Purpose |
|-------|------|---------|
| `changes` | `ChangeSpec?` | Document modifications |
| `selection` | `SelectionSpec?` | New selection (cursor or explicit) |
| `effects` | `List<StateEffect<*>>?` | Typed side-channel values |
| `annotations` | `List<Annotation<*>>?` | Metadata (user event, history) |
| `scrollIntoView` | `Boolean` | Request scroll to selection |
| `filter` | `Boolean?` | Whether to run filters |
| `sequential` | `Boolean` | Apply after the previous spec |

### Reading transaction data

```kotlin
tr.changes          // the ChangeSet
tr.newDoc           // document after changes
tr.newSelection     // selection after changes
tr.state            // the resulting EditorState (lazily computed)
tr.docChanged       // shorthand for !changes.empty
tr.reconfigured     // true if extensions changed
```

### Annotations

Annotations attach metadata to a transaction:

```kotlin
tr.annotation(Transaction.userEvent)    // "input", "delete", etc.
tr.annotation(Transaction.addToHistory) // Boolean
tr.isUserEvent("input")                 // true for "input" and "input.type" etc.
```

### Convenience methods on EditorState

```kotlin
// Replace all selection ranges with text
state.replaceSelection("inserted text")

// Apply a function to each selection range independently
state.changeByRange { range ->
    ChangeByRangeResult(
        changes = ChangeSpec.Single(range.from, range.to,
            insert = InsertContent.StringContent("x")),
        range = EditorSelection.cursor(range.from + 1)
    )
}
```

## Effects

`StateEffect` values carry typed data through a transaction without
modifying the document. They are used to trigger side effects like
opening a panel or updating plugin state.

```kotlin
// Define an effect type
val myEffect = StateEffect.define<String>()

// Dispatch it
val tr = state.update(
    TransactionSpec(effects = listOf(myEffect.of("hello")))
)

// Read it from a transaction
for (effect in tr.effects) {
    val value = effect.asType(myEffect)
    if (value != null) {
        println(value.value) // "hello"
    }
}
```

Two built-in effect types handle dynamic reconfiguration:

- `StateEffect.reconfigure` — replace all extensions from a compartment
- `StateEffect.appendConfig` — add extensions to the configuration

Effects can define a `map` function so their values update correctly
when the document changes.

## Facets

A `Facet<Input, Output>` collects values from multiple extensions and
combines them into a single output. This is the primary configuration
mechanism.

```kotlin
// Define a facet that collects strings
val myFacet = Facet.define<String, List<String>>(
    combine = { values -> values }
)

// Provide values
val ext1 = myFacet.of("alpha")
val ext2 = myFacet.of("beta")

// Read from state
val values: List<String> = state.facet(myFacet)
// ["alpha", "beta"]
```

### Built-in facets

The `:state` module defines several core facets:

| Facet | Type | Combine strategy |
|-------|------|------------------|
| `EditorState.tabSize` | `Facet<Int, Int>` | First value (default 4) |
| `EditorState.lineSeparator` | `Facet<String, String?>` | First value |
| `EditorState.readOnly` | `Facet<Boolean, Boolean>` | First value (default false) |
| `allowMultipleSelections` | `Facet<Boolean, Boolean>` | Any true |
| `changeFilter` | `Facet<..., List<...>>` | Collect all |
| `transactionFilter` | `Facet<..., List<...>>` | Collect all |

### Computed facets

Facets can derive their value from state:

```kotlin
val lineCount = myFacet.compute(listOf(Slot.Doc)) { state ->
    "Document has ${state.doc.lines} lines"
}
```

Dependencies (`Slot.Doc`, `Slot.Selection`, `Slot.FacetSlot(...)`,
`Slot.FieldSlot(...)`) tell the system when to recompute.

## EditorState

`EditorState` ties everything together. It holds the document, selection,
and all extension-provided values.

### Creating state

```kotlin
val state = EditorState.create(EditorStateConfig(
    doc = "fun main() {}".asDoc(),
    selection = SelectionSpec.CursorSpec(0),
    extensions = ExtensionList(listOf(
        EditorState.tabSize.of(2),
        javascript(),
        oneDark
    ))
))
```

### Accessing data

```kotlin
state.doc                  // the Text document
state.selection            // current EditorSelection
state.field(myStateField)  // read a state field value
state.facet(myFacet)       // read a facet value
state.tabSize              // shorthand for state.facet(EditorState.tabSize)
state.readOnly             // shorthand for readOnly facet
state.sliceDoc(from, to)   // get a substring from the document
state.wordAt(pos)          // find the word at a position
```

### Updating state

```kotlin
val tr = state.update(TransactionSpec(
    changes = ChangeSpec.Single(0, 0,
        insert = InsertContent.StringContent("// header\n"))
))
val newState = tr.state
```

### Serialization

State can be serialized to and from JSON for persistence:

```kotlin
val json = state.toJSON()
val restored = EditorState.fromJSON(json, EditorStateConfig(
    extensions = myExtensions
))
```
