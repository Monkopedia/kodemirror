# Merge View

The `:merge` module provides diff and merge views for comparing documents.

## Side-by-side diff

```kotlin
import com.monkopedia.kodemirror.merge.*

val mergeView = MergeView(MergeViewConfig(
    a = EditorStateConfig(doc = DocSpec.StringDoc(original)),
    b = EditorStateConfig(doc = DocSpec.StringDoc(modified)),
    highlightChanges = true,
    gutter = true,
    revertControls = RevertDirection.B_TO_A
))
```

## Unified diff

Show changes in a single editor with deleted lines displayed inline:

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

## Accept and reject changes

In the unified view, accept or reject individual changes:

```kotlin
acceptChunk(session)  // Accept the change at cursor
rejectChunk(session)  // Revert to original at cursor
```

## Collapse unchanged sections

For large files, hide unchanged regions to focus on changes:

```kotlin
unifiedMergeView(UnifiedMergeConfig(
    original = originalText,
    collapseUnchanged = CollapseConfig(margin = 3, minSize = 4)
))
```

## Related API

- [`MergeView`](../reference/merge/index.md) — Side-by-side merge view
- [`unifiedMergeView`](../reference/merge/index.md) — Unified diff view
- [`Chunk`](../reference/merge/index.md) — Changed region representation
- [`diff`](../reference/merge/index.md) / [`presentableDiff`](../reference/merge/index.md) — Diff algorithm

See the [Merge and Diff Views guide](../guide/merge.md) for full
documentation.
