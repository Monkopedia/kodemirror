# Read-Only Mode

There are two ways to make an editor read-only: the `readOnly` facet
on the state layer, and the `editable` facet on the view layer.

## Using readOnly

The `readOnly` facet prevents the document from being modified through
transactions:

```kotlin
import com.monkopedia.kodemirror.state.*

val state = EditorState.create(EditorStateConfig(
    doc = "This text cannot be changed.".asDoc(),
    extensions = readOnly.of(true)
))
```

With `readOnly`, the document is immutable but the cursor can still
move and text can be selected and copied.

## Using editable

The `editable` facet in the `:view` module disables all interaction
with the editor — no cursor, no selection, no input:

```kotlin
import com.monkopedia.kodemirror.view.editable

val state = EditorState.create(EditorStateConfig(
    doc = "Display only.".asDoc(),
    extensions = editable.of(false)
))
```

## Toggling at runtime

Use a `Compartment` to switch between read-only and editable at
runtime:

```kotlin
val readOnlyCompartment = Compartment()

val state = EditorState.create(EditorStateConfig(
    doc = "Toggle me.".asDoc(),
    extensions = readOnlyCompartment.of(readOnly.of(false))
))

// Make read-only
fun setReadOnly(view: EditorSession, isReadOnly: Boolean) {
    view.dispatch(TransactionSpec(
        effects = listOf(
            readOnlyCompartment.reconfigure(readOnly.of(isReadOnly))
        )
    ))
}
```

## Checking read-only status

```kotlin
val isReadOnly = state.readOnly  // shorthand for state.facet(readOnly)
```

---

*Based on the [CodeMirror Read-Only example](https://codemirror.net/examples/config/).*
