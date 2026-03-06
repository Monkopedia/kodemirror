# Changelog

All notable changes to Kodemirror will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/).

## [Unreleased]

### Added
- `extensionListOf(vararg Extension)` factory function
- `operator fun Extension.plus(other: Extension)` for combining extensions
- `operator fun Text.get(range: IntRange)` for slicing text
- `operator fun EditorState.get(field: StateField<T>)` for field access
- `String.asInsert()` extension for converting strings to `InsertContent`
- `Int.asCursor()` extension for creating cursor `SelectionSpec`
- `EditorSelection.asSpec()` extension for converting to `SelectionSpec`
- `EditorState.create(doc: String, ...)` convenience overload
- `EditorState.currentLine`, `.selectedText`, `.cursorPosition` properties
- `Text.isEmpty`, `.isNotEmpty`, `.lineSequence()` properties
- `keymapOf(bindings: List<KeyBinding>)` overload accepting a list
- `keymapOf { }` DSL builder for key bindings
- `selectSelectionMatches` search command
- `SearchQuery.validOrNull()` factory method
- `CompletionType` enum for standard completion types
- `pickedCompletion` annotation for tracking accepted completions
- `ifIn` / `ifNotIn` context-aware completion source wrappers
- `hasNextSnippetField` / `hasPrevSnippetField` snippet state queries
- `StateField.define<T> { create { }; update { } }` DSL builder
- `HighlightStyle.define { Tags.keyword styles SpanStyle(...) }` DSL builder
- `Decoration.mark(style: SpanStyle, ...)` convenience overload
- `Decoration.line(style: SpanStyle, ...)` convenience overload
- `operator fun StateField<T>.getValue(EditorState, KProperty)` property delegate
- KDoc for top-level facets in `Extension.kt`
- `editorThemeFromColors()` factory for Material Design color scheme integration
- KDoc for all 137 legacy-mode StreamParser entry points
- CodeMirror 6 migration guide
- Getting Started tutorial
- Expanded the-view guide with input pipeline, virtualization, and selection drawing
- Extension index guide page
- Troubleshooting guide page
- Platform-specific setup snippets in bundle guide
- FacetReader and PluginSpec.configure documentation

### Changed
- Renamed `tags` object to `Tags` (PascalCase convention)
- Renamed `jsHighlight` to `jsHighlighting` (consistent with other modules)
- Renamed `tagLanguage` to `jinjaTagLanguage` / `liquidTagLanguage`
- Converted `Prec` lambda properties to functions
- Made `phpHighlighting` and `angularHighlighting` public
- Moved `vueHighlighting` from `VueParser.kt` to separate `VueHighlight.kt`
- Converted `// /` comments to KDoc in `Text.kt` and `Column.kt`
- Made `LeafBlock.content` and `LeafBlock.parsers` setters internal

### Removed
- `StateEffect.is()` method (use `asType()` instead)

### Internal
- Made lezer-lr internal state (`Stack`, `Parse`, `CachedToken`,
  `InputStream`, `SimulatedStack`, `TokenCache`, `StackBufferCursor`,
  and constant objects) `internal`
- Filtered `ComposableSingletons` from public API dumps
