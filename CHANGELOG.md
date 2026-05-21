# Changelog

All notable changes to Kodemirror will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/).

## [Unreleased]

### Fixed
- Vim dot-repeat (`.`) dropped the inserted text of change/insert commands (`c`, `cw`, `s`, `i`, `a`, `o`, …) — the insert-mode change event was never wired up, so `lastInsertModeChanges` stayed empty and `.` replayed only the operator/delete (#21)
- Vim change/insert commands (`c`, `cw`, `s`, …) were not a single undo unit — `u` took two steps to revert because the insert-entry cursor selection stamped a history boundary on the operator-delete event (#23)

## [0.2.0] - 2026-04-10

### Highlights
- 16 new editor themes ported from [thememirror](https://github.com/vadimdemedes/thememirror) (MIT)
- Async incremental parsing — large documents no longer block the UI
- Multiple editor instances now work correctly (vim and platform handlers)
- Fixed syntax highlighting colors being wrong when themes were applied

### Added
- 15 new theme modules: Amy, Ayu Light, Barf, Bespin, Birds of Paradise, Boys and Girls, Clouds, Cobalt, Cool Glow, Espresso, Noctis Lilac, Rose Pine Dawn, Smoothy, Solarized Light, Tomorrow
- `ParseWorker` ViewPlugin — coroutine-based async parsing with 25ms time-sliced `advance()` and `yield()` between chunks
- `TreeFragment` reuse via `applyChanges()` — edits only reparse changed regions
- `syntaxParserRunning()` now returns actual parsing state
- `forceParsing()` for synchronous parse completion in tests
- `VimContext` StateField — all vim mutable state is now per-editor
- `PlatformKeyHandlerToken` — platform key handlers support multiple registrations with disposal tokens
- `ThemeTestPage` in showcase for theme comparison testing

### Changed
- **Breaking:** `vimGlobalState` removed — vim registers, jump lists, search history, macro state, and key stacks are now per-editor via `VimContext` StateField
- **Breaking:** `platformRegisterKeyHandler` returns a `PlatformKeyHandlerToken` for disposal instead of being fire-and-forget
- **Breaking:** `:theme-github-light` module removed (replaced by thememirror light themes)
- `:theme-dracula` replaced with thememirror port (different tag-to-color mappings)
- Decoration sets applied in reverse precedence order so theme styles override fallback styles
- Pointer input refactored from three competing `pointerInput` modifiers to a single unified tap/drag handler
- Session internals (`pluginHost`, `lineLayoutCache`, etc.) assigned before first composition instead of in `DisposableEffect`

### Removed
- `:theme-github-light` module
- `lastVimPlugin` global singleton
- `vimGlobalState` global singleton
- `recentlyDragged` flag in pointer input handling

### Fixed
- Theme syntax highlighting colors overridden by fallback `defaultHighlightStyle` (decoration precedence bug) (#13)
- Dracula active line background fully opaque, washing out comment text (#13)
- Tooltips not appearing until first state change (session internals not initialized on first frame) (#1)
- Canvas click breaking keyboard input due to tap/drag gesture race condition (#2)
- Multiple editor instances stealing keyboard/paste handlers from each other (#6)
- Vim mode sharing registers, macro state, and search history across editors (#6)
- Clipboard paste broken on wasmJs due to async browser clipboard API (#16)
- Vim visual mode reporting "visual-block" instead of "visual" (#17)
- Bracket auto-delete broken by wrong keymap priority (#18)
- Async parsing race dispatching stale tree for changed document (#19)

## [0.1.0] - 2026-04-03

Initial release of Kodemirror — a Kotlin Multiplatform port of CodeMirror 6.

### Highlights
- Full Compose Multiplatform code editor component
- Vim mode with 600+ ported upstream tests
- Real browser keyboard input pipeline (no JS bridges)
- 183 automated gap tests verifying CM6 parity
- 57 keymap command tests covering all standard/emacs bindings
- 20+ language modules plus 100+ legacy modes
- Live interactive documentation with embedded demos

### Platform Support
- **wasmJs**: Fully tested with automated gap tests and manual browser testing
- **JVM Desktop**: Unit tests pass, visual rendering lightly tested
- **Android**: Unit tests pass via CI
- **iOS / macOS native**: Compiles, experimental (CI on PR/manual trigger)

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
- `editorThemeFromColors()` factory for Material Design color scheme integration
- `inputSuppressor` facet for blocking text input (used by vim normal mode)
- `blockCursorProvider` facet for overlay-based block cursor rendering
- `keyEventLayoutKey()` public API for keyboard layout-aware key names
- Tab character rendering via column-aware expansion in `buildLineContentWithTabs`
- Vim mode status bar with `StateField`-based mode tracking
- Vim `:` / `/` / `?` command prompt panel
- Polished search panel with rounded corners and proper styling

### Changed
- Renamed `tags` object to `Tags` (PascalCase convention)
- Renamed `jsHighlight` to `jsHighlighting` (consistent with other modules)
- Renamed `tagLanguage` to `jinjaTagLanguage` / `liquidTagLanguage`
- Converted `Prec` lambda properties to functions
- Made `phpHighlighting` and `angularHighlighting` public
- Block cursor rendered via selection overlay (`DrawScope`) instead of decorations
- Keymap precedence: later extensions override earlier ones (matching CM6)
- `defaultEditorFontFamily` changed to `FontFamily.Monospace` (cross-platform)

### Removed
- `StateEffect.is()` method (use `asType()` instead)

### Fixed
- Fixed `HistoryConfig` being silently ignored — config values are now stored via facet
- Fixed `StreamParser` using hardcoded `tabSize=4`/`indentUnit=2` instead of reading from `EditorState`
- Fixed `UpdateRange` argument order bug in merge module that corrupted chunk boundaries
- Fixed `asciiWordChar` excluding digit '0' in merge diff algorithm
- Fixed diff algorithm using mutable global state (now thread-safe via local `DiffContext`)
- Reduced public API surface in `:view` and `:vim` modules — internal implementation types are now `internal`

### Internal
- Made lezer-lr internal state `internal`
- Filtered `ComposableSingletons` from public API dumps
- Document-level key handler for wasmJs keyboard routing
- `platformFocusInput()` / `platformRegisterKeyHandler()` expect/actual APIs
