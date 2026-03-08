# Handling Large Documents

Kodemirror is designed to handle documents with millions of lines
efficiently. This page covers the data structures and techniques that
make this possible.

## Efficient document representation

The `Text` type uses a tree structure (not a flat string) so that
insertions and lookups are logarithmic:

```kotlin
val doc = "...huge content...".asDoc()

// These are all efficient O(log n) operations:
val line = doc.line(500000)       // get line by number
val lineAt = doc.lineAt(1000000)  // get line at character offset
val slice = doc.sliceString(100, 200)  // extract a range
```

Never convert the entire document to a string for processing. Use the
line-based API instead.

## Viewport-aware processing

For decorations and analysis, only process visible content. The
`ViewPlugin` pattern naturally supports this because it has access
to the view's viewport:

```kotlin
class LargeDocPlugin(view: EditorSession) : PluginValue, DecorationSource {
    override var decorations: DecorationSet = buildDecorations(view)
        private set

    override fun update(update: ViewUpdate) {
        if (update.docChanged || update.viewportChanged) {
            decorations = buildDecorations(update.view)
        }
    }

    private fun buildDecorations(view: EditorSession): DecorationSet {
        val builder = RangeSetBuilder<Decoration>()
        // Only process lines in the visible viewport
        val startLine = view.state.doc.lineAt(view.viewport.from)
        val endLine = view.state.doc.lineAt(view.viewport.to)

        for (i in startLine.number..endLine.number) {
            val line = view.state.doc.line(i)
            // ... process only visible lines
        }

        return builder.finish()
    }
}
```

## Compose virtual scrolling

The `EditorSession` composable uses a `LazyColumn` internally for virtual
scrolling. Only visible lines are composed and rendered, so memory usage
stays constant regardless of document size.

## Efficient updates with ChangeSet

When making changes, use `ChangeSet` rather than replacing the entire
document. `ChangeSet` describes only the modified regions:

```kotlin
// Efficient: only describes the insertion
view.dispatch(TransactionSpec(
    changes = ChangeSpec.Single(
        from = pos,
        insert = InsertContent.StringContent(text)
    )
))

// Inefficient: replaces the entire document
// DON'T DO THIS for large documents
```

## Incremental parsing

The Lezer parser is incremental — after an edit, it only re-parses the
changed region. Combined with `ChangeSet`, this means syntax highlighting
stays responsive even for very large files.

## Tips for large documents

- **Avoid `doc.toString()`** — this allocates a single string for the
  entire document. Use `doc.sliceString()` for ranges or iterate with
  `doc.line()`.
- **Use `ViewPlugin`** for visible-range work rather than processing the
  full document in a `StateField`.
- **Batch changes** — combine multiple edits into a single transaction
  rather than dispatching many small ones.
- **Debounce linting** — set a higher `delay` in `LintConfig` for large
  files to avoid running the linter on every keystroke.
- **Lazy language loading** — only load language packages for the
  languages actually in use.

---

*Based on the [CodeMirror Handling Large Documents example](https://codemirror.net/examples/million/).*
