# Performance Guide

Tips for keeping Kodemirror editors responsive, especially with large
documents.

## How Virtualization Works

Kodemirror uses `LazyColumn` for virtualized rendering — only visible
lines are composed. This means:

- Documents with millions of lines are feasible
- Memory usage scales with viewport size, not document size
- Scrolling performance depends on line complexity, not document length

## Decoration Performance

Decorations (syntax highlighting, markers, search matches) are stored
in `RangeSet` — a sorted, immutable tree structure. Performance tips:

- **Keep decoration counts reasonable.** Hundreds of decorations per
  viewport are fine. Tens of thousands may cause slowdowns.
- **Use `RangeSetBuilder`** to create decorations in sorted order.
  Out-of-order insertions are rejected.
- **Reuse decoration instances.** Create `Decoration.mark(...)` once
  and add the same instance to multiple ranges.
- **Update incrementally.** In `ViewPlugin.update`, only rebuild
  decorations when `update.docChanged` or `update.viewportChanged`.

```kotlin
override fun update(update: ViewUpdate) {
    if (update.docChanged || update.viewportChanged) {
        decorations = buildDecorations(update.session)
    }
}
```

## Parser Performance

The syntax parser runs incrementally — it only parses what's needed
for the visible viewport plus a buffer zone.

- **`ensureSyntaxTree`** parses up to a given position. Don't call
  this for the entire document unless necessary.
- **`forceParsing`** blocks until parsing completes up to a position.
  Avoid calling this from the main thread for large ranges.
- **Language nesting** (e.g., JavaScript inside HTML) adds overhead
  proportional to the number of nested regions.

## StateField vs ViewPlugin

| Concern | `StateField` | `ViewPlugin` |
|---------|:---:|:---:|
| Runs on every transaction | Yes | Yes (update) |
| Has access to viewport | No | Yes |
| Can skip work when offscreen | No | Yes |
| Good for decorations | No (runs even when hidden) | Yes |
| Good for derived data | Yes | No |

**Rule of thumb:** If you need decorations that depend on the viewport,
use `ViewPlugin`. If you need derived data from the document, use
`StateField`.

## Minimizing Recomposition

- **Avoid reading `session.state` in composables** that don't need
  it — this triggers recomposition on every transaction.
- **Use `onChange` or `onSelection`** for callbacks instead of
  observing state directly.
- **`@Immutable` on `EditorTheme`** enables Compose to skip
  recomposition of theme-dependent composables when the theme hasn't
  changed.

## Large Document Checklist

For documents over 100K lines:

1. Use `basicSetup` or a minimal extension set — avoid unnecessary
   extensions.
2. Ensure syntax highlighting uses an incremental parser (all
   built-in languages do).
3. Avoid decorations that span the entire document.
4. Use `collapseUnchanged` in merge views to hide unchanged regions.
5. Test on target platforms — mobile devices have less memory and
   slower CPUs.
