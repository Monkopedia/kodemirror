# Split View

A split editor shows the same document in two side-by-side panes. Each
pane is an independent `EditorView` composable sharing the same
`EditorState`.

## Basic split view

```kotlin
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import com.monkopedia.kodemirror.state.*
import com.monkopedia.kodemirror.view.EditorView

@Composable
fun SplitEditor(initialDoc: String) {
    var state by remember {
        mutableStateOf(
            EditorState.create(EditorStateConfig(
                doc = initialDoc.asDoc(),
                extensions = ExtensionList(listOf(/* ... */))
            ))
        )
    }

    val onUpdate: (Transaction) -> Unit = { tr ->
        state = tr.state
    }

    Row(Modifier.fillMaxSize()) {
        EditorView(
            state = state,
            onUpdate = onUpdate,
            modifier = Modifier.weight(1f)
        )
        EditorView(
            state = state,
            onUpdate = onUpdate,
            modifier = Modifier.weight(1f)
        )
    }
}
```

Both editors render the same `state`. When either pane dispatches a
transaction (via `onUpdate`), the resulting state is applied to both
views through recomposition.

## How it works

The `EditorView` composable signature:

```kotlin
@Composable
fun EditorView(
    state: EditorState,
    onUpdate: (Transaction) -> Unit,
    modifier: Modifier = Modifier
)
```

Each composable call creates its own internal `EditorView` instance
with independent scroll position, cursor, and selection. The shared
`EditorState` keeps the document content synchronized.

## Independent extensions per pane

You can give each pane different extensions by using separate states
that share the same document content:

```kotlin
@Composable
fun SplitEditorWithDifferentConfigs(doc: String) {
    var sharedDoc by remember { mutableStateOf(doc.asDoc()) }

    val leftState = remember(sharedDoc) {
        EditorState.create(EditorStateConfig(
            doc = sharedDoc,
            extensions = ExtensionList(listOf(
                lineNumbers,
                // left pane extensions
            ))
        ))
    }

    val rightState = remember(sharedDoc) {
        EditorState.create(EditorStateConfig(
            doc = sharedDoc,
            extensions = ExtensionList(listOf(
                // right pane extensions (no line numbers)
            ))
        ))
    }

    Row(Modifier.fillMaxSize()) {
        EditorView(
            state = leftState,
            onUpdate = { tr ->
                if (tr.docChanged) sharedDoc = tr.newDoc
            },
            modifier = Modifier.weight(1f)
        )
        EditorView(
            state = rightState,
            onUpdate = { tr ->
                if (tr.docChanged) sharedDoc = tr.newDoc
            },
            modifier = Modifier.weight(1f)
        )
    }
}
```

!!! note
    In this pattern, each pane has its own `EditorState` so selections
    and view-layer state are fully independent. Only the document
    content is shared.

---

*Based on the [CodeMirror Split View example](https://codemirror.net/examples/split/).*
