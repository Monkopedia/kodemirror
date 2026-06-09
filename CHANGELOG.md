# Changelog

All notable changes to Kodemirror will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/).

## [Unreleased]

### Added
- Minimal Android sample app (`samples/android`) that embeds and builds a `KodeMirror` editor with `basicSetup` plus a Markdown language extension, unblocking the "running on Android" prerequisite (#29).
- `indentSelection` command (`:commands`): re-indents every line touched by the selection to the indentation computed by the language indent service / tree strategy (`getIndentation`), mirroring upstream `@codemirror/commands`. Skips lines where `getIndentation` returns null, applies all line changes in one transaction, and moves the cursor ahead of the indentation when it sat within the old indent. Bound in `defaultKeymap` to `Ctrl-Alt-\` (`Meta-Alt-\` on macOS) (#135).
- `CompletionConfig.asyncOverride`: suspend completion sources that the autocomplete framework launches on the editor's coroutine scope and dispatches when they resolve. This is the wasmJs-safe way to drive an async source (e.g. a language server) — the previous `asyncCompletionSource` blocking bridge throws on wasmJs (#109).

### Fixed
- `moveLineUp`/`moveLineDown` now operate on every selection range, not just the primary one. Ranges are grouped into contiguous line-blocks (matching upstream `@codemirror/commands` `moveLine`): each block is swapped with the adjacent line in one transaction, all cursors/selections are remapped to follow their moved lines, edge blocks that can't move are skipped, and the command no-ops only when nothing can move (#130).
- **LSP completion now works on wasmJs (#109).** Several issues stacked: (a) the completion source was registered via `asyncCompletionSource`, whose wasmJs actual throws, so the popup never opened — `serverCompletion` now registers via the new `asyncOverride` path; (b) two `autocompletion()` extensions (e.g. `basicSetup` + `languageServerSupport`) no longer shadow each other — the `completionConfig` facet now merges completion sources across all configs instead of taking only the last; (c) the completion popup is rendered with a plain `Column` + bounded width and a `weight`-sized label so it lays out and paints on Compose-wasmJs (a `LazyColumn`/width-less `BasicText` collapsed to nothing); (d) completion labels display (and insert) correctly for servers that use lazy completion — `insertionText()` now treats an empty `textEdit.newText`/`insertText` as absent and falls back to the item label, and `mapCompletionItem` sets `displayLabel` to the item label. (Bold prefix-match highlighting is temporarily dropped on this path — #111; `completionItem/resolve` for auto-imports is tracked in #112.)
- Completion trigger characters (e.g. `.`) are no longer inserted twice on wasmJs: the hidden text field's input-echo suppression is now reset per keydown and survives the extra `onValueChange` that a completion-popup recomposition can fire (#109).
- Accepting a completion no longer crashes (`IllegalArgumentException: Invalid position …`) when the document shrank while the popup was open (e.g. backspacing before accept): `applyCompletion` now clamps the completion result's stored `from`/`to` to the current document length (#124).
- LSP completion lists now filter by the typed prefix as you type after a trigger character (e.g. typing `to` after `Cylinder(...).` narrows to `to…` instead of leaving the full member list). The server result now always carries a `validFor` prefix regex — previously it was withheld for `isIncomplete` lists, but many servers (e.g. kotlin-lsp) mark every member list incomplete, so the list never filtered and the accept range went stale (#114).
- Arrow-navigating a completion list longer than the popup now scrolls the selected row into view; the `verticalScroll` column previously did not follow the selection, so the highlight could move out of sight below the fold (#115).
- Editing no longer crashes (`IllegalArgumentException: Invalid position …`) when a coordinate query (tooltip positioning, caret reveal) holds a document position that briefly outlives a shrinking edit. `coordsAtPos`/`blockAtPos` now return null for a position outside the current document instead of throwing `lineAt` mid-render — on a delete that removes the last character a stale position is exactly `length + 1` (#127).
- Hover (and other) tooltips no longer render offscreen near the viewport edges. `TooltipLayer` now positions tooltips through a `PopupPositionProvider` that clamps the tooltip to the window and flips it above/below the anchor (respecting `strictSide`), instead of placing it at the raw caret coordinate — which landed offscreen near an edge, or for a multi-line hover range whose end sits low in the viewport (#110).

### Tests
- Ported upstream `:commands` coverage for `insertNewlineKeepIndent` and the bracket-explosion behaviour of `insertNewlineAndIndent` (both were ported but untested), including its use of a registered `indentService` for language-aware indentation (#116).
- Added `:autocomplete` pipeline coverage for unfiltered (`filter = false`) results, accept replacing a range that extends past the cursor, first-non-empty source selection, and `completeFromList` word-match/explicit gating (#117).
- Added the upstream `selectNextOccurrence` subword case — selecting a subword (`one` inside `onetwo`) also matches it inside another word (`onethree`) (#118).
- The `:view` Roborazzi screenshot tests are now a CI gate. They previously ran in capture-only mode, so visual regressions (e.g. the active-line highlight no longer filling the viewport width, #86) passed CI unnoticed. The tests now render with a bundled, pinned **JetBrains Mono** font (OFL-1.1, loaded from the test classpath via Skiko so the rasterised pixels are reproducible across machines instead of drifting with the host's system monospace font), compare with a 0.1% pixel tolerance as a safety margin against incidental antialiasing noise, and CI runs `:view:verifyRoborazziJvm` in the `check` job so UI regressions fail the build. Regenerate baselines after intentional rendering changes with `./gradlew :view:recordRoborazziJvm` (#89).

### Changed
- Bumped the lsp dependency to 1.2.0 (was 1.0.1) (#108). 1.2.0 tightens several `LanguageServer`/`LanguageClient` return types to be nullable, matching the LSP spec's optional results (`textDocument/hover`, `textDocument/formatting`, `textDocument/references`, `textDocument/rename`, `workspace/workspaceFolders`). The client now treats a null result as "no result" (no-op / empty), preserving prior behaviour.
- Release artifacts are signed only for non-SNAPSHOT versions, so a local `publishToMavenLocal` of a `-SNAPSHOT` build no longer requires a GPG key.

## [0.3.3] - 2026-06-04

### Changed
- Built on Kotlin 2.4.0 (was 2.3.20). Consumers of the wasmJs/native klibs must be on Kotlin 2.4.0+ (klib ABI is forward-incompatible). Also bumped the lsp dependency to 1.0.1 (the Kotlin-2.4.0 build; identical public API) (#105).

### Fixed
- updateListener facet (and the onChange/onSelection extensions built on it) now fire on every transaction; previously they were registered but never dispatched (#103)

## [0.3.2] - 2026-06-02

### Fixed
- Editor no longer crashes on mount ("EditorSession is not attached") when a hover-tooltip or lint extension is used; the session coroutine scope is now attached before plugins are constructed (#92). (First complete release of this fix — 0.3.1 contained it but failed to publish completely to Maven Central.)

## [0.3.1] - 2026-06-01

### Fixed
- Editor no longer crashes on mount ("EditorSession is not attached") when a hover-tooltip or lint extension is used; the session coroutine scope is now attached before plugins are constructed (#92)

## [0.3.0] - 2026-05-29

### Tests
- Add LSPClient-core and LSPPlugin test coverage (#80)

### Added
- Horizontal scrollbar for long lines in no-wrap mode. The editor renders on a Skiko canvas with no native scrollbar, so long lines clipped at the right edge with horizontal scroll only reachable via shift+wheel/trackpad. A custom commonMain scrollbar (thin track + draggable thumb) is now shown whenever there is horizontal overflow and line wrapping is off; it is hidden when content fits or `lineWrapping` is enabled (#65)

### Changed
- Bump lsp dependency to stable 1.0.0 (#82)
- Bumped `com.monkopedia.lsp` (`lsp` + `lsp-ksrpc`) from `1.0.0-RC3` to `1.0.0-RC4`. RC4 tightens several `LanguageServer` method *result* types from untyped `JsonElement` to strict sealed unions, so `:lsp-client` now consumes the typed results directly instead of hand-parsing `JsonElement`: `textDocument/completion` reads `TextDocumentCompletionResult` (`CompletionListValue` / `CompletionItemArray`) and the go-to-definition family (`textDocument/{definition,declaration,typeDefinition,implementation}`) reads the strict `TextDocument{Definition,Declaration,TypeDefinition,Implementation}Result` (`DefinitionValue`/`DeclarationValue` wrapping `SingleOrArray<Location>`, and `DefinitionLinkArray`/`DeclarationLinkArray` wrapping `List<LocationLink>`). Internal parsing only; no public API change. The `:lsp-client` module remains pre-stable.
- Bumped `com.monkopedia.lsp` (`lsp` + `lsp-ksrpc`) from `1.0.0-RC2` to `1.0.0-RC3`. RC3 tightens several `Hover.contents` / `ServerCapabilities.textDocumentSync` fields from untyped `JsonElement` to strict union types, so `:lsp-client`'s `ServerHover` and `DocumentSync` now read the typed `HoverContents` and `ServerCapabilitiesTextDocumentSync` unions directly instead of hand-parsing `JsonElement` (internal parsing only; no public API change). The `:lsp-client` module remains pre-stable.

### Fixed
- Goal/sticky-column ("column memory") is preserved across vertical cursor moves again (regressed by the visual-row motion change) (#87)
- Vim gj/gk now move by visual (wrapped) row (#77)
- Caret height and gutter alignment on wrapped lines (#75)
- `KodeMirror` placed under an unbounded vertical constraint (a parent with `Modifier.verticalScroll(...)`, `wrapContentHeight()`, or another scrolling container) no longer collapses to zero height or crashes the inner `LazyColumn` (which disallows an infinite `maxHeight`). The editor now grows to its content height so the surrounding scroll container can scroll it. The `KodeMirror` KDoc documents the height contract: callers should give the editor a bounded height for in-editor scrolling and caret reveal (#33)
- Cursor now scrolls horizontally into view in no-wrap mode (#69)
- Horizontal scroll/scrollbar now works for documents with mixed line lengths (#67)
- Clicking on a long line clipped the cursor toward the end of the line (you could not click past a certain column and had to use the keyboard). Lines were soft-wrapped but pinned to a single visual-row height, so only the first visible region resolved to an offset. Matching CodeMirror 6, the editor now does NOT wrap by default — long lines lay out at full width and the content scrolls horizontally, so clicks resolve to the correct offset across the whole line. The `lineWrapping` extension (previously a dead CSS attribute) now actually enables soft wrapping (#20)
- wasmJs editor no longer swallows browser-reserved keyboard shortcuts (Ctrl+Tab, Ctrl+Shift+Tab, Ctrl+PgUp/PgDn, Ctrl+1–9, Ctrl+W/T/L, …) — the document keydown listener now calls `preventDefault()` only when the editor's keymap actually handled the key, so unbound modified/special keys propagate to the browser (#49)
- Copy/cut/paste did not reach the system clipboard on the Compose/wasm target (vim `y`, emacs, and default keymaps) — the canvas render has no DOM `contenteditable`, so the copy/cut commands now bridge to `navigator.clipboard.writeText` synchronously within the originating key gesture (with a hidden-textarea `execCommand` fallback) and paste primes its buffer from `navigator.clipboard.readText`; the in-editor buffer from #16 remains the immediate fallback. JVM/desktop continues to use Compose's `ClipboardManager` (#34)
- Transactions with `scrollIntoView` now actually scroll the view: the editor was never reacting to the flag, so selection jumps that landed off-screen (vim `n`/`N` search-repeat, goto-line, history/undo, etc.) left the target invisible. The line list now scrolls the primary selection head into view, matching CodeMirror 6's `scrollIntoView` "nearest" behavior (#58, #33)
- Vim dot-repeat (`.`) dropped the inserted text of change/insert commands (`c`, `cw`, `s`, `i`, `a`, `o`, …) — the insert-mode change event was never wired up, so `lastInsertModeChanges` stayed empty and `.` replayed only the operator/delete (#21)
- Vim change/insert commands (`c`, `cw`, `s`, …) were not a single undo unit — `u` took two steps to revert because the insert-entry cursor selection stamped a history boundary on the operator-delete event (#23)
- Active-line and line-decoration highlight backgrounds again fill the full editor width in no-wrap mode (regressed by the horizontal-scroll layout work) (#85)

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
