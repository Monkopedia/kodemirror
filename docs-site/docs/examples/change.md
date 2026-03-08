# Programmatic Changes

You can modify the document programmatically by dispatching transactions
with change specifications.

## Inserting text

To insert text at a position, create a `ChangeSpec.Single` with `from`
set and no `to`:

```kotlin
import com.monkopedia.kodemirror.state.*

// Insert "Hello " at position 0
view.dispatch(TransactionSpec(
    changes = ChangeSpec.Single(
        from = 0,
        insert = InsertContent.StringContent("Hello ")
    )
))
```

## Replacing a range

Set both `from` and `to` to replace text:

```kotlin
// Replace characters 5..10 with "world"
view.dispatch(TransactionSpec(
    changes = ChangeSpec.Single(
        from = 5,
        to = 10,
        insert = InsertContent.StringContent("world")
    )
))
```

## Deleting text

Omit `insert` to delete a range:

```kotlin
// Delete characters 0..5
view.dispatch(TransactionSpec(
    changes = ChangeSpec.Single(from = 0, to = 5)
))
```

## Multiple changes

Use `ChangeSpec.Multi` to apply several changes at once. Positions refer
to the original document — the system handles offsetting:

```kotlin
view.dispatch(TransactionSpec(
    changes = ChangeSpec.Multi(listOf(
        ChangeSpec.Single(from = 0, insert = InsertContent.StringContent("// header\n")),
        ChangeSpec.Single(from = 20, to = 25, insert = InsertContent.StringContent("new"))
    ))
))
```

## Replacing the selection

`state.replaceSelection()` builds a `TransactionSpec` that replaces
every selection range with the given text:

```kotlin
val spec = state.replaceSelection("inserted text")
view.dispatch(spec)
```

## Per-range changes

`state.changeByRange` applies a function to each selection range
independently, correctly handling multi-cursor edits:

```kotlin
val spec = state.changeByRange { range ->
    ChangeByRangeResult(
        changes = ChangeSpec.Single(
            range.from, range.to,
            insert = InsertContent.StringContent(
                state.sliceDoc(range.from, range.to).uppercase()
            )
        ),
        range = EditorSelection.range(
            range.from,
            range.from + (range.to - range.from)
        )
    )
}
view.dispatch(spec)
```

## Composing and inverting changes

`ChangeSet` values support composition and inversion:

```kotlin
// Build a ChangeSet
val changes = state.changes(
    ChangeSpec.Single(from = 0, to = 5, insert = InsertContent.StringContent("Hi"))
)

// Apply to get a new document
val newDoc = changes.apply(state.doc)

// Create an undo operation
val undo = changes.invert(state.doc)

// Compose two sequential change sets
val combined = first.compose(second)
```

## Mapping positions

When changes modify the document, use `mapPos` to translate positions
from the old document to the new one:

```kotlin
val newPos = changes.mapPos(oldPos)

// Control which side of an insertion to stick to
val newPos = changes.mapPos(oldPos, assoc = 1) // after insertion
```

## Related API

- [`TransactionSpec`](/api/state/com.monkopedia.kodemirror.state/-transaction-spec/) — transaction specification
- [`ChangeSpec`](/api/state/com.monkopedia.kodemirror.state/-change-spec/) — change specification
- [`ChangeSet`](/api/state/com.monkopedia.kodemirror.state/-change-set/) — immutable change set
- [`EditorSession.dispatch`](/api/view/com.monkopedia.kodemirror.view/-editor-view/dispatch.html) — apply transactions

---

*Based on the [CodeMirror Changes example](https://codemirror.net/examples/change/).*
