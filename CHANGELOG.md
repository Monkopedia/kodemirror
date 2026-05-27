# Changelog

All notable changes to Kodemirror will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/).

## [Unreleased]

### Added
- Horizontal scrollbar for long lines in no-wrap mode. The editor renders on a Skiko canvas with no native scrollbar, so long lines clipped at the right edge with horizontal scroll only reachable via shift+wheel/trackpad. A custom commonMain scrollbar (thin track + draggable thumb) is now shown whenever there is horizontal overflow and line wrapping is off; it is hidden when content fits or `lineWrapping` is enabled (#65)

### Changed
- Bumped `com.monkopedia.lsp` (`lsp` + `lsp-ksrpc`) from `1.0.0-RC3` to `1.0.0-RC4`. RC4 tightens several `LanguageServer` method *result* types from untyped `JsonElement` to strict sealed unions, so `:lsp-client` now consumes the typed results directly instead of hand-parsing `JsonElement`: `textDocument/completion` reads `TextDocumentCompletionResult` (`CompletionListValue` / `CompletionItemArray`) and the go-to-definition family (`textDocument/{definition,declaration,typeDefinition,implementation}`) reads the strict `TextDocument{Definition,Declaration,TypeDefinition,Implementation}Result` (`DefinitionValue`/`DeclarationValue` wrapping `SingleOrArray<Location>`, and `DefinitionLinkArray`/`DeclarationLinkArray` wrapping `List<LocationLink>`). Internal parsing only; no public API change. The `:lsp-client` module remains pre-stable.
- Bumped `com.monkopedia.lsp` (`lsp` + `lsp-ksrpc`) from `1.0.0-RC2` to `1.0.0-RC3`. RC3 tightens several `Hover.contents` / `ServerCapabilities.textDocumentSync` fields from untyped `JsonElement` to strict union types, so `:lsp-client`'s `ServerHover` and `DocumentSync` now read the typed `HoverContents` and `ServerCapabilitiesTextDocumentSync` unions directly instead of hand-parsing `JsonElement` (internal parsing only; no public API change). The `:lsp-client` module remains pre-stable.

### Fixed
- `KodeMirror` placed under an unbounded vertical constraint (a parent with `Modifier.verticalScroll(...)`, `wrapContentHeight()`, or another scrolling container) no longer collapses to zero height or crashes the inner `LazyColumn` (which disallows an infinite `maxHeight`). The editor now grows to its content height so the surrounding scroll container can scroll it. The `KodeMirror` KDoc documents the height contract: callers should give the editor a bounded height for in-editor scrolling and caret reveal (#33)
- Cursor now scrolls horizontally into view in no-wrap mode (#69)
- Horizontal scroll/scrollbar now works for documents with mixed line lengths (#67)
- Clicking on a long line clipped the cursor toward the end of the line (you could not click past a certain column and had to use the keyboard). Lines were soft-wrapped but pinned to a single visual-row height, so only the first visible region resolved to an offset. Matching CodeMirror 6, the editor now does NOT wrap by default — long lines lay out at full width and the content scrolls horizontally, so clicks resolve to the correct offset across the whole line. The `lineWrapping` extension (previously a dead CSS attribute) now actually enables soft wrapping (#20)
- wasmJs editor no longer swallows browser-reserved keyboard shortcuts (Ctrl+Tab, Ctrl+Shift+Tab, Ctrl+PgUp/PgDn, Ctrl+1–9, Ctrl+W/T/L, …) — the document keydown listener now calls `preventDefault()` only when the editor's keymap actually handled the key, so unbound modified/special keys propagate to the browser (#49)
- Copy/cut/paste did not reach the system clipboard on the Compose/wasm target (vim `y`, emacs, and default keymaps) — the canvas render has no DOM `contenteditable`, so the copy/cut commands now bridge to `navigator.clipboard.writeText` synchronously within the originating key gesture (with a hidden-textarea `execCommand` fallback) and paste primes its buffer from `navigator.clipboard.readText`; the in-editor buffer from #16 remains the immediate fallback. JVM/desktop continues to use Compose's `ClipboardManager` (#34)
- Transactions with `scrollIntoView` now actually scroll the view: the editor was never reacting to the flag, so selection jumps that landed off-screen (vim `n`/`N` search-repeat, goto-line, history/undo, etc.) left the target invisible. The line list now scrolls the primary selection head into view, matching CodeMirror 6's `scrollIntoView` "nearest" behavior (#58, #33)
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
