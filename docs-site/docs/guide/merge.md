# Merge and Diff Views

The `:merge` module provides side-by-side and unified diff views for
comparing and merging documents.

## Side-by-Side Merge

`MergeView` displays two editors side by side with change highlighting:

```kotlin
val mergeView = MergeView(MergeViewConfig(
    a = EditorStateConfig(doc = DocSpec.StringDoc(originalDoc)),
    b = EditorStateConfig(doc = DocSpec.StringDoc(modifiedDoc)),
    highlightChanges = true,
    gutter = true
))

// Access the two editors
val editorA = mergeView.a  // EditorSession for side A
val editorB = mergeView.b  // EditorSession for side B

// Get the list of changed chunks
val chunks = mergeView.chunks
```

### MergeViewConfig

| Property | Type | Default | Description |
|----------|------|---------|-------------|
| `a` | `EditorStateConfig` | required | Configuration for editor A (typically the original) |
| `b` | `EditorStateConfig` | required | Configuration for editor B (typically the modified) |
| `orientation` | `Orientation` | `A_B` | Layout order (`A_B` or `B_A`) |
| `revertControls` | `RevertDirection?` | `null` | Show revert buttons (`A_TO_B` or `B_TO_A`) |
| `highlightChanges` | `Boolean` | `true` | Highlight inserted/deleted text within chunks |
| `gutter` | `Boolean` | `true` | Show gutter markers for changes |
| `collapseUnchanged` | `CollapseConfig?` | `null` | Collapse unchanged sections |
| `diffConfig` | `DiffConfig` | default | Diff algorithm options |

### Reverting Chunks

With `revertControls` enabled, users can click to revert individual
changes. You can also revert programmatically:

```kotlin
for (chunk in mergeView.chunks) {
    mergeView.revertChunk(chunk)
}
```

### Cleanup

Call `dispose()` when you're done with the merge view:

```kotlin
mergeView.dispose()
```

## Unified Merge View

The unified view shows changes in a single editor, with deleted lines
displayed inline:

```kotlin
val session = rememberEditorSession(
    doc = modifiedDoc,
    extensions = basicSetup + unifiedMergeView(UnifiedMergeConfig(
        original = Text.of(originalDoc.split("\n")),
        highlightChanges = true,
        mergeControls = true
    ))
)
KodeMirror(session)
```

### UnifiedMergeConfig

| Property | Type | Default | Description |
|----------|------|---------|-------------|
| `original` | `Text` | required | The original document to compare against |
| `highlightChanges` | `Boolean` | `true` | Mark changed text inline |
| `gutter` | `Boolean` | `true` | Show gutter markers |
| `syntaxHighlightDeletions` | `Boolean` | `true` | Syntax-highlight deleted lines |
| `mergeControls` | `Boolean` | `true` | Show accept/reject buttons |
| `allowInlineDiffs` | `Boolean` | `false` | Show inline diffs for small changes |
| `collapseUnchanged` | `CollapseConfig?` | `null` | Collapse unchanged sections |
| `diffConfig` | `DiffConfig` | default | Diff algorithm options |

### Accepting and Rejecting Changes

```kotlin
// Accept the change at the cursor position
acceptChunk(session)

// Reject (revert to original)
rejectChunk(session)

// Or specify a position explicitly
acceptChunk(session, pos = 42)
```

### Updating the Original Document

If the original document changes (e.g., a new version is pulled from
version control):

```kotlin
val effect = originalDocChangeEffect(session.state, changes)
session.dispatch(TransactionSpec(effects = listOf(effect)))
```

## Chunks

A `Chunk` represents a contiguous region of changes between two
documents:

```kotlin
data class Chunk(
    val changes: List<Change>,  // Individual changes within this chunk
    val fromA: Int,             // Start line in document A
    val toA: Int,               // End line in document A
    val fromB: Int,             // Start line in document B
    val toB: Int                // End line in document B
)
```

Build chunks from two documents:

```kotlin
val chunks = Chunk.build(textA, textB)
```

Query chunks from editor state:

```kotlin
val result = getChunks(session.state)  // ChunkResult?
val chunks = result?.chunks ?: emptyList()
```

## Diff Algorithm

The module includes a built-in diff algorithm. You can use it directly:

```kotlin
// Basic diff
val changes = diff(textA, textB)

// User-friendly diff (aligned to word boundaries)
val changes = presentableDiff(textA, textB)

// With custom configuration
val changes = diff(textA, textB, DiffConfig(
    scanLimit = 500_000,
    timeout = 5000  // 5 seconds max
))
```

### DiffConfig

| Property | Type | Default | Description |
|----------|------|---------|-------------|
| `scanLimit` | `Int` | `1_000_000_000` | Algorithm optimization limit |
| `timeout` | `Long` | `0` | Timeout in milliseconds (0 = unlimited) |
| `override` | `((String, String) -> List<Change>)?` | `null` | Custom diff function |

## Collapsing Unchanged Sections

For large documents, collapse sections with no changes:

```kotlin
val config = CollapseConfig(
    margin = 3,   // Lines to show around changes
    minSize = 4   // Minimum lines to collapse
)

// In unified view:
unifiedMergeView(UnifiedMergeConfig(
    original = originalText,
    collapseUnchanged = config
))

// Or as a standalone extension:
collapseUnchanged(margin = 3, minSize = 4)
```

## Navigation Commands

Navigate between changes with keyboard commands:

```kotlin
goToNextChunk      // Move to the next change
goToPreviousChunk  // Move to the previous change
```

Add them to a keymap:

```kotlin
keymapOf(
    KeyBinding(key = "Alt-ArrowDown", run = goToNextChunk),
    KeyBinding(key = "Alt-ArrowUp", run = goToPreviousChunk)
)
```

---

*Based on the [CodeMirror Merge](https://codemirror.net/docs/ref/#merge)
module.*
