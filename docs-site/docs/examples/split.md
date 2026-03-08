# Split View

A split editor shows the same document in two side-by-side panes. Each
pane is a `KodeMirror` composable sharing the same `EditorSession`.

## Basic split view

```kotlin
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import com.monkopedia.kodemirror.state.plus
import com.monkopedia.kodemirror.view.*

@Composable
fun SplitEditor(initialDoc: String) {
    val session = rememberEditorSession(
        doc = initialDoc,
        extensions = lineNumbers + // ... other extensions
    )

    Row(Modifier.fillMaxSize()) {
        KodeMirror(
            session = session,
            modifier = Modifier.weight(1f)
        )
        KodeMirror(
            session = session,
            modifier = Modifier.weight(1f)
        )
    }
}
```

Both editors render the same session. When either pane dispatches a
transaction, the resulting state is applied to both views through
recomposition.

## How it works

The `KodeMirror` composable signature:

```kotlin
@Composable
fun KodeMirror(
    session: EditorSession,
    modifier: Modifier = Modifier
)
```

Each composable call creates its own internal rendering state with
independent scroll position. The shared `EditorSession` keeps the
document content synchronized.

## Independent extensions per pane

You can give each pane different extensions by creating separate sessions
that share the same document content:

```kotlin
@Composable
fun SplitEditorWithDifferentConfigs(doc: String) {
    val leftSession = rememberEditorSession(
        doc = doc,
        extensions = lineNumbers + // left pane extensions
    )

    val rightSession = rememberEditorSession(
        doc = doc,
        // right pane extensions (no line numbers)
    )

    Row(Modifier.fillMaxSize()) {
        KodeMirror(
            session = leftSession,
            modifier = Modifier.weight(1f)
        )
        KodeMirror(
            session = rightSession,
            modifier = Modifier.weight(1f)
        )
    }
}
```

!!! note
    In this pattern, each pane has its own `EditorSession` so selections
    and view-layer state are fully independent. The document content
    is not automatically synchronized — changes in one pane won't appear
    in the other. For true shared-document editing, use a single session
    or the `:collab` module.

---

*Based on the [CodeMirror Split View example](https://codemirror.net/examples/split/).*
