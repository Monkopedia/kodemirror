# Kodemirror TODO List

*Generated from API Evaluation — 2026-03-06*
*Source: [docs/api-evaluation/](api-evaluation/README.md)*

## Status Key

Each item has a status prefix on its heading line:

- (no prefix) — **Pending**: not yet started
- `[DONE]` — **Complete**: implemented, tested, committed
- `[BLOCKED]` — **Blocked**: cannot proceed; reason noted below the heading
- `[SKIP]` — **Skipped**: intentionally deferred or decided against; reason noted

---

## Priority 1 — Critical / Pre-1.0 Blockers

### 1. [DONE] Convert `Text.kt` comments to KDoc
- **Effort:** < 1 hour | **Source:** Documentation
- `Text`, `TextIterator`, and `Line` use `// /` (JS-style) instead of `/** */` KDoc. Dokka generates
  empty pages; IDE shows no quick-docs. Mechanical find-and-replace in one file.
- **File:** `state/src/commonMain/kotlin/.../state/Text.kt`

### 2. [DONE] Filter `ComposableSingletons` from public API
- **Effort:** < 1 day | **Source:** Architecture
- `ComposableSingletons$SearchKt` and `ComposableSingletons$LintKt` appear in `.api` dumps with
  hash-based method names that change with any code edit. Must be excluded from the public API surface
  via `internal` visibility, `@PublishedApi`, or API dump exclusion.
- **Files:** `search/api/search.api`, `lint/api/lint.api`

### 3. [DONE] Make lezer-lr internal state `internal`
- **Effort:** 3+ days | **Source:** Architecture
- `Stack` (10 setters), `Parse` (7), `CachedToken` (7), `InputStream` (6), `SimulatedStack` (3),
  `TokenCache` (3), `StackBufferCursor` (4) all expose public mutable setters. External code can
  corrupt the parser. Also applies to constant objects: `Action`, `Encode`, `Rec`, `Recover`, `Seq`,
  `ParseState`, `StateFlag`, `Term`, `File`, `Lookahead`, `Specialize`.
- May require a `-internal` module split.
- **Directory:** `lezer-lr/src/commonMain/kotlin/...`

---

## Priority 2 — High Impact, Core DX

### 3b. [DONE] Rename `EditorView` to `EditorSession` + `KodeMirror` composable
> **Decision:** Extract `EditorSession` class into `EditorSession` interface (state holder + dispatcher for commands/plugins). Rename `EditorSession` composable to `KodeMirror`. Add `rememberEditorSession(doc, extensions)` and `rememberEditorSession(config)` composable factories. Hard rename (no deprecated aliases — pre-1.0). Subsumes #12 (rememberEditorState) and #13 (simple EditorSession overload).
- **Effort:** 3+ days | **Source:** Architecture, Kotlin Ergonomics
- **Scope:**
  - Extract `EditorSession` interface from `EditorSession` class (state, dispatch, plugin, editable, coords)
  - Internal `EditorSessionImpl` class
  - Rename composable `EditorSession` → `KodeMirror`
  - Add `rememberEditorSession(doc, extensions)` and `rememberEditorSession(config)` composables
  - Update all command signatures `(EditorSession) -> Boolean` → `(EditorSession) -> Boolean`
  - Update all plugin APIs (`ViewPlugin.create`, `ViewUpdate.view`, etc.)
  - Update all modules: commands, autocomplete, search, lint, language, lang-* modules
  - Update `LocalEditorSession` → `LocalEditorSession`
  - Update companion object facets (`EditorSession.updateListener` → `EditorSession.updateListener`)
  - Update docs and examples

### 4. [DONE] Add `basicSetup` / `minimalSetup` convenience bundle
> **Decision:** New `:basic-setup` module. Bundle standard extensions matching upstream CodeMirror basicSetup.
- **Effort:** 1–2 days | **Source:** Completeness, Frontend DX
- Biggest onboarding friction. Every new editor requires manually assembling 10+ extensions.
  CodeMirror upstream provides `basicSetup` as the standard entry point. Bundle: line numbers,
  history, bracket matching, folding, autocompletion, search, default keymap, syntax highlighting.

### 5. [DONE] Add `kodemirror-bom` Gradle BOM
> **Decision:** Create BOM now.
- **Effort:** 1 day | **Source:** Frontend DX
- Users must manage 6+ separate dependency versions for a basic editor. A Bill of Materials
  (`kodemirror-bom`) would let users align all module versions with a single entry.

### 6. [DONE] Add `extensionListOf(vararg Extension)` factory
- **Effort:** < 1 hour | **Source:** Frontend DX, Kotlin Ergonomics
- `ExtensionList(listOf(...))` double-wrapping appears in every editor, every compartment
  reconfiguration, every theme bundle. Add `extensionListOf(vararg)` or make
  `EditorStateConfig.extensions` accept `List<Extension>` directly.
- **File:** `state/src/commonMain/kotlin/.../state/Extension.kt`

### 7. [DONE] Add DSL builders for `EditorState` and `TransactionSpec`
> **Decision:** Operator overloading DSL style (`+lineNumbers`). Use `@DslMarker`.
- **Effort:** 2–3 days | **Source:** Kotlin Ergonomics
- The two most common API entry points have significant ceremony:
  - `EditorState.create(EditorStateConfig(..., extensions = ExtensionList(listOf(...))))`
  - `view.dispatch(TransactionSpec(changes = ChangeSpec.Single(from = 0, insert = InsertContent.StringContent("..."))))`
- Target: `editorState { doc("..."); extensions { +lineNumbers } }` and
  `view.dispatch { insert(0, "Hello") }`
- Use `@DslMarker` annotation on builder scopes to prevent accidental scope leaking.

### 8. [DONE] Replace `eq()` methods with `equals()` / `hashCode()`
> **Decision:** `equals()` uses `includeAssoc=false`. Keep `eq(other, includeAssoc)` as secondary method for the `includeAssoc=true` case.
- **Effort:** 1–2 days | **Source:** Kotlin Ergonomics
- `SelectionRange`, `EditorSelection`, `Text`, `RangeValue`, `WidgetType` define `eq()` instead of
  Kotlin's `equals()`. Breaks `==`, collections, and surprises every Kotlin developer.
- For `SelectionRange.eq(other, includeAssoc)`, keep as secondary method; `equals()` for common case.
- **Files:** `state/.../Selection.kt`, `Text.kt`, `RangeSet.kt`; `view/.../Decoration.kt`

### 9. [DONE] Add `String.asInsert()` extension / String overload for `ChangeSpec.Single.insert`
- **Effort:** < 1 hour | **Source:** Kotlin Ergonomics, Frontend DX
- Every programmatic text change requires `InsertContent.StringContent("...")`. Add implicit
  `String` to `InsertContent` conversion.
- **File:** `state/src/commonMain/kotlin/.../state/Change.kt`

### 10. [DONE] Remove `StateEffect.is()` method
- **Effort:** < 1 hour | **Source:** Kotlin Ergonomics
- `is` is a reserved keyword requiring backtick-escaping. Has `@Suppress("ktlint:standard:function-naming")`.
  Replace all call sites with the existing `asType()` method.
- **File:** `state/src/commonMain/kotlin/.../state/Transaction.kt`

### 11. [DONE] Add `keymapOf(List<KeyBinding>)` overload
- **Effort:** < 1 hour | **Source:** Frontend DX
- Eliminates `keymapOf(*indentWithTab.toTypedArray())` spread pattern in almost every editor setup.
- **File:** `view/src/commonMain/kotlin/.../view/Keymap.kt`

---

## Priority 3 — Medium Impact, Ergonomics & Compose Integration

### 12. [SKIP] Add `rememberEditorState` composable + `@Immutable` annotations
> **Skip reason:** Subsumed by #3b — `rememberEditorSession` provides this functionality as part of the EditorSession refactor. `@Immutable` annotations can be added as part of #3b or separately.
- **Effort:** 1–2 days | **Source:** Frontend DX, Kotlin Ergonomics
- Every editor repeats: `var state by remember { mutableStateOf(EditorState.create(config)) }` +
  `onUpdate = { tr -> state = tr.state }`. A `rememberEditorState` composable eliminates this.
  Also annotate `EditorState`, `EditorTheme`, etc. with `@Immutable` to enable Compose skip
  optimizations.

### 13. [SKIP] Add simple `EditorSession` overload with `initialDoc` and `onChange`
> **Skip reason:** Subsumed by #3b — `rememberEditorSession(doc, extensions)` + `KodeMirror(session)` provides the simple API. #20 (standalone onChange extension) handles text change callbacks.
- **Effort:** 1 day | **Source:** Frontend DX
- Provide a convenience `EditorSession` overload that accepts `initialDoc: String`,
  `extensions: List<Extension>`, and `onChange: (String) -> Unit`, managing its own state internally.
  Covers the 80% use case where developers just want text in/text out without understanding the
  full transaction model.

### 14. [DONE] Add type-safe `LanguageDataKey<T>`
> **Decision:** Replace string-based `languageDataAt` entirely. Define standard keys like `LanguageDataKey.AUTOCOMPLETE`, `LanguageDataKey.FOLD`, etc.
- **Effort:** 1–2 days | **Source:** Kotlin Ergonomics
- `EditorState.languageDataAt(name: String, pos: Int)` uses `Map<String, Any?>` + unchecked cast.
  Replace with typed key class similar to `AnnotationType<T>`.
- **File:** `state/src/commonMain/kotlin/.../state/State.kt`

### 15. [DONE] Replace CSS class strings with sealed class/enum identifiers
> **Decision:** Sealed class everywhere — `GutterType`, `DecorationClass`, `DiagnosticSeverity`. Breaking change across view/lint modules.
- **Effort:** 1–2 days | **Source:** Kotlin Ergonomics, Architecture
- `GutterConfig.cssClass`, `Diagnostic.markClass`, `MarkDecorationSpec.cssClass`, and
  `MarkDecorationSpec.attributes` all reference CSS/DOM concepts meaningless in Compose.
  Gutter types use string comparison (`"cm-lineNumbers"`, `"cm-activeLineGutter"`).
  Replace with sealed class or enum; remove or replace DOM attribute fields.
- **Files:** `view/.../Gutter.kt`, `view/.../Decoration.kt`, `lint/.../Lint.kt`

### 16. Add `suspend` overloads for linter and completion sources
> **Decision:** CoroutineScope parameter for explicit control, plus a composable option/default that auto-remembers a scope.
- **Effort:** 2–3 days | **Source:** Kotlin Ergonomics
- Zero `suspend fun` or `Flow` usage in core modules. Async linting and network-backed completions
  need coroutine support. This is essential for a coroutine-native Kotlin API.

### 17. Prefix `parser` properties across language modules
> **Decision:** Rename all now — breaking change. `javaParser`, `pythonParser`, `cppParser`, etc.
- **Effort:** < 1 day | **Source:** Architecture
- `jsHighlight` is the only language module not matching `{lang}Highlighting` pattern.
- Top-level `parser` property across ~15 modules causes star-import collisions. Rename to
  `javaParser`, `pythonParser`, `cppParser`, etc.

### 18. Type the `tag` field in `TagStyleSpec`, `TagStyleRule`, and `NodeSpec`
> **Decision:** Sealed interface `TagInput { data class Single(val tag: Tag); data class Modified(val modifier: (Tag) -> Tag) }`. Also type `TreeBuildSpec.buffer` and `NestedParse.overlay`.
- **Effort:** 1 day | **Source:** Architecture
- `TagStyleSpec.tag`, `TagStyleRule.tag`, and `NodeSpec.style` are currently `Any` (from JS dynamic
  typing). Should accept `Tag` or `(Tag) -> Tag` for modifiers.
  Also type `TreeBuildSpec.buffer` as `List<Int>`/`IntArray` and `NestedParse.overlay`.

### 19. [DONE] Add operator overloads where natural
- **Effort:** < 1 day | **Source:** Kotlin Ergonomics
- `operator fun plus` on `Extension`: `ext1 + ext2 + ext3` instead of `ExtensionList(listOf(...))`
- `operator fun get` on `Text`: `doc[from..to]` instead of `doc.sliceString(from, to)`
- `operator fun get` on `EditorState`: `state[myField]` instead of `state.field(myField)`
- `invoke` operator on `Facet`: `editable(false)` instead of `editable.of(false)`

### 20. Add simple `onChange: (String) -> Unit` convenience
> **Decision:** Standalone extension — `EditorSession.onChange(callback: (String) -> Unit): Extension` that can be added to any editor setup, independent of #13.
- **Effort:** < 1 day | **Source:** Frontend DX
- Many developers just want document text changes. The transaction model is powerful but overkill
  for simple cases. Provide an `UpdateListener` extension or helper that extracts document text
  on change.

---

## Priority 4 — Medium Impact, Completeness Gaps

### 21. [DONE] Port `lang-javascript` completion, snippets, and autoCloseTags
> Added `localCompletionSource`, `scopeCompletionSource`, `completionPath`, `snippets`, `typescriptSnippets`, and `autoCloseTags`. Wired into `javascript()` function with autocompletion config.
- **Effort:** 2–3 days | **Source:** Completeness
- Missing: `localCompletionSource`, `scopeCompletionSource`, `completionPath`, `snippets`,
  `typescriptSnippets`, `autoCloseTags`. JS is the most commonly used language in code editors.

### 22. [DONE] Port `lang-html` autoCloseTags and completion
> Added `autoCloseTags`, `htmlCompletionSource`, `htmlCompletionSourceWith()`, `HtmlSchema` with full tag/attribute data, `HtmlCompletionConfig`, and `TagSpec`. Wired into `html()` function.
- **Effort:** 1–2 days | **Source:** Completeness
- Missing: `autoCloseTags`, `htmlCompletion`, `htmlCompletionSourceWith()`.
  Auto-close HTML tags on `>` is one of the most expected editor behaviors.

### 23. [DONE] Add missing ~30 command variants
> Added 30+ commands: cursor forward/backward/logical/syntax/lineBoundary variants, group forward/backward/win, selection equivalents, selectPageUp/Down, deleteCharBackwardStrict, deleteGroupForwardWin, deleteLineBoundaryBackward/Forward, deleteTrailingWhitespace, addCursorAbove/Below, newlineAndIndent alias, undoSelection/redoSelection, public historyField. `toggleTabFocusMode`/`temporarilySetTabFocusMode` omitted (DOM-specific, no Compose equivalent).
- **Effort:** 2 days | **Source:** Completeness
- Missing cursor commands: `cursorCharForward/Backward`, `cursorCharForwardLogical/BackwardLogical`,
  `cursorGroupForward/Backward`, `cursorGroupForwardWin`, `cursorSyntaxLeft/Right`,
  `cursorLineBoundaryForward/Backward`, `cursorLineBoundaryLeft/Right`
- Missing selection commands: `selectCharForward/Backward`, `selectCharForwardLogical/BackwardLogical`,
  `selectGroupForward/Backward`, `selectGroupForwardWin`, `selectSyntaxLeft/Right`,
  `selectLineBoundaryForward/Backward`, `selectLineBoundaryLeft/Right`, `selectPageUp/Down`
- Missing editing commands: `deleteCharBackwardStrict`, `deleteGroupForwardWin`,
  `deleteLineBoundaryBackward/Forward`, `deleteTrailingWhitespace`, `newlineAndIndent` (distinct export),
  `addCursorAbove/Below`, `toggleTabFocusMode`, `temporarilySetTabFocusMode`
- Missing history: `undoSelection`, `redoSelection`, `historyField` (make public)
- **File:** `commands/src/commonMain/kotlin/.../commands/Commands.kt`

### 24. [DONE] Add missing language parsing context APIs
> Added `ensureSyntaxTree`, `syntaxTreeAvailable`, `syntaxParserRunning`, `forceParsing`, `LanguageDescription`, `highlightingFor`, `bracketMatchingHandle`. Note: `ParseContext` omitted (sync model makes it redundant), `bidiIsolates` omitted (DOM/CSS-specific concept), `Sublanguage`/`sublanguageProp` omitted (significant nesting infrastructure needing design decision).
- **Effort:** 2–3 days | **Source:** Completeness
- Missing: `ensureSyntaxTree`, `ParseContext`, `LanguageDescription`, `syntaxTreeAvailable`,
  `syntaxParserRunning`, `forceParsing`, `highlightingFor`, `bracketMatchingHandle`,
  `bidiIsolates`, `Sublanguage`/`sublanguageProp`
- **Module:** `:language`

### 25. [DONE] Add missing view utilities
> Added `highlightWhitespace`, `highlightTrailingWhitespace`, `hasHoverTooltips`, `closeHoverTooltips`, `getTooltips`, `repositionTooltips`, `panels` facet, `getPanel`, `lineNumberMarkers` facet, `exceptionSink` facet, `logException`. Omitted: `layer`/`LayerMarker`/`RectangleMarker` (need Compose rendering layer design), `showDialog`/`getDialog` (new dialog infrastructure), `runScopeHandlers` (DOM scope concept), `getDrawSelectionConfig` (low value), `gutterWidgetClass`/`lineNumberWidgetMarker` (CSS-specific).
- **Effort:** 2–3 days | **Source:** Completeness
- Missing: `highlightWhitespace`, `highlightTrailingWhitespace`, `layer`/`LayerMarker`,
  `RectangleMarker`, `getDrawSelectionConfig`, `getTooltip`, `repositionTooltips`,
  `hasHoverTooltips`, `closeHoverTooltips`, `showDialog`/`getDialog`, `getPanel`, `panels` facet,
  `gutterWidgetClass`, `lineNumberMarkers`, `lineNumberWidgetMarker`, `logException`,
  `runScopeHandlers`
- **Module:** `:view`

---

## Priority 5 — Medium Impact, Documentation

### 26. [DONE] Add API docs link to guide index
- **Effort:** < 1 hour | **Source:** Documentation
- No path from guides to generated API reference. Add link to `docs-site/docs/guide/index.md`
  and `docs-site/docs/examples/index.md`.

### 27. [DONE] Add API cross-links from example pages to API reference
> Added "Related API" sections to 10 example pages: autocompletion, lint, decoration, gutter, tooltip, styling, selection, change, config, lang-package, tab.
- **Effort:** 1 day | **Source:** Documentation
- Examples reference types like `RangeSetBuilder`, `Completion`, `Diagnostic` without linking to
  API docs. Add inline links or a "Related API" section to each example page.

### 28. [DONE] Add KDoc to top-level facets in `Extension.kt`
- **Effort:** < 1 hour | **Source:** Documentation
- `languageData`, `allowMultipleSelections`, `lineSeparator`, `changeFilter`, `transactionFilter`,
  `transactionExtender`, `invertedEffects`, `readOnly` all lack documentation.
- **File:** `state/src/commonMain/kotlin/.../state/Extension.kt`

### 29. [DONE] Expand the-view guide with missing topics
> Added three sections: Text input pipeline (keyEventToName → keymap → command → dispatch), Document rendering and large documents (LazyColumn virtualization), Selection and cursor drawing (drawWithContent overlay).
- **Effort:** 1–2 days | **Source:** Documentation
- Text input pipeline (Compose `onKeyEvent` -> command dispatch -> Transaction -> state update) is
  undocumented. This is where Kodemirror differs most from upstream (Compose vs DOM ContentEditable).
- Also missing: discussion of `LazyColumn` virtualization and its implications for large documents,
  and the selection/cursor drawing mechanism (`drawWithContent` overlay).

### 30. [DONE] Create a "Getting Started" step-by-step tutorial
> Created `docs-site/docs/guide/getting-started.md` — covers project setup, dependencies, minimal editor, state pattern, adding features (completion, search, themes, read-only), programmatic changes.
- **Effort:** 1–2 days | **Source:** Documentation
- No step-by-step tutorial walks through creating a project from scratch: project setup, adding
  dependencies, writing a minimal editor composable, running it. Distinct from the existing
  `basic.md` example which shows code but not the full project setup process.

### 31. Create a complete sample project
> **Decision:** Desktop + wasm targets in-repo `samples/` directory. Will add other platforms later.
- **Effort:** 2–3 days | **Source:** Documentation, Frontend DX
- No runnable examples. Create a minimal Android or Desktop app in `samples/` that users can clone
  and run. Include platform-specific `build.gradle.kts` setup.

### 32. [DONE] Scan for other files with `// /` comment style
- **Effort:** < 1 hour | **Source:** Documentation
- `Text.kt` is the most critical, but other files from the upstream port may have the same issue.

---

## Priority 6 — Lower Impact, Polish

### 33. Restrict legacy-mode state class mutability
> **Decision:** Make setters `internal` on all state classes. They stay in the same module.
- **Effort:** 3+ days | **Source:** Architecture
- All 100+ stream-parser state classes (`AplState`, `AsciiArmorState`, etc.) have public mutable
  setters. The `StreamParser<State>` generic interface may need revisiting to enable `internal`
  visibility. Consider making just the setters `internal` while keeping the types public.
- **Directory:** `legacy-modes/src/commonMain/kotlin/.../modes/`

### 34. [DONE] Rename `tags` object to `Tags`
- **Effort:** < 1 hour (+ migration) | **Source:** Architecture
- `tags` uses lowercase class name, violating Kotlin PascalCase convention. IDE warnings.
- **File:** `lezer-highlight/src/commonMain/kotlin/.../highlight/Tags.kt`

### 35. [DONE] Make `Rule.next` and `LeafBlock.content`/`parsers` visibility restricted
- **Effort:** < 1 day | **Source:** Architecture
- Mutable linked-list pointer and markdown parser internals exposed publicly.

### 36. [DONE] Add `StateFieldSpec` DSL builder
- **Effort:** 1 day | **Source:** Kotlin Ergonomics
- Six function-type parameters in a data class is hard to read. Target:
  `StateField.define<Int> { create { 0 }; update { v, tr -> v + 1 } }`

### 37. [DONE] Add Keymap DSL builder
- **Effort:** 1 day | **Source:** Kotlin Ergonomics
- Target: `keymap { "Ctrl-s" { save(it); true }; "Ctrl-z" { undo(it); true } }`
- More ergonomic than constructing `KeyBinding` data classes manually.

### 38. [DONE] Add Decoration building DSL
> Added `Decoration.mark(style, ...)` and `Decoration.line(style, ...)` convenience overloads that accept parameters directly, eliminating the spec wrapper.
- **Effort:** 1 day | **Source:** Kotlin Ergonomics
- Target: `Decoration.mark { style { background = Color(...); fontWeight = FontWeight.Bold } }`
- Eliminates `MarkDecorationSpec(style = SpanStyle(...))` ceremony.

### 39. [DONE] Add `EditorState` convenience extensions
- **Effort:** < 1 day | **Source:** Kotlin Ergonomics
- `val EditorState.currentLine`, `.selectedText`, `.cursorPosition`, `.isEmpty`

### 40. [DONE] Add `SelectionSpec` convenience constructors
- **Effort:** < 1 hour | **Source:** Kotlin Ergonomics
- `EditorSelection.asSpec()`, `Int.asCursor()` to reduce `SelectionSpec.EditorSelectionSpec(...)` verbosity.

### 41. [DONE] Convert `Prec` lambda properties to functions
- **Effort:** < 1 hour | **Source:** Kotlin Ergonomics
- `Prec.highest`, `.high`, `.default`, `.low`, `.lowest` are stored as lambda properties (JS pattern).
  Should be `fun highest(ext: Extension): Extension`.

### 42. Add `kotlinx.serialization` support alongside `toJSON`/`fromJSON`
> **Decision:** Add `@Serializable` to core types (`EditorState`, `EditorSelection`, `SelectionRange`, `Text`). Adds kotlinx.serialization dependency to `:state` module.
- **Effort:** 2–3 days | **Source:** Kotlin Ergonomics
- `toJSON()`/`fromJSON()` use `Map<String, Any?>`. Add `@Serializable` data classes or serializers.
  Also consider renaming to `serialize()`/`deserialize()`.

### 43. [DONE] Consider `Completion.type` as enum/sealed class
> Added `CompletionType` enum with standard type values. `Completion.type` remains `String?` for compatibility; use `CompletionType.FUNCTION.value` etc.
- **Effort:** 1 day | **Source:** Kotlin Ergonomics, Frontend DX
- Currently `String?` ("keyword", "function", "variable", etc.) with no discoverability.
  An enum would provide exhaustive matching and prevent typos.

### 44. Consider inline value classes for positions
> **Decision:** Introduce `@JvmInline value class DocPosition(val value: Int)` and `LineNumber(val value: Int)` now. Major breaking change touching all modules.
- **Effort:** 2–3 days | **Source:** Kotlin Ergonomics
- `@JvmInline value class DocPosition(val value: Int)` and `LineNumber(val value: Int)` would
  prevent mixing up positions and line numbers at zero runtime cost.

### 45. [DONE] Add missing autocomplete minor APIs
> Added `pickedCompletion` annotation, `ifIn`/`ifNotIn` wrappers, `hasNextSnippetField`/`hasPrevSnippetField` predicates. `CompletionSource` type alias already existed.
- **Effort:** < 1 day | **Source:** Completeness
- Missing: `pickedCompletion`, `ifIn`/`ifNotIn`, `hasNextSnippetField`/`hasPrevSnippetField`,
  `CompletionInfo` type, `CompletionSource` type alias.

### 46. [DONE] Add `selectSelectionMatches` to search module
- **Effort:** < 1 hour | **Source:** Completeness
- Select all instances of current selection text. Single missing command.

### 47. [DONE] Add `DocSpec` String overload for `EditorStateConfig`
- **Effort:** < 1 hour | **Source:** Frontend DX
- Accept `String` directly: `EditorState.create(doc = "Hello", extensions = ...)` instead of
  requiring `.asDoc()`.

### 48. [DONE] Add `MaterialTheme` to `EditorTheme` bridge
> Added `editorThemeFromColors(background, foreground, primary, surface, outline, dark)` factory function that maps semantic colors to all EditorTheme fields. Works with Material3 ColorScheme without requiring it as a dependency.
- **Effort:** 1 day | **Source:** Frontend DX
- No automatic bridging between `MaterialTheme.colorScheme` and `EditorTheme`.

### 49. Consider a `lang-kotlin` module
> **Decision:** New Lezer grammar from scratch. Significant effort but cleaner than extending Java grammar.
- **Effort:** 3+ days | **Source:** Frontend DX
- Ironic absence for a Kotlin-first library. Even a Java grammar with Kotlin extensions would be
  high value.

### 50. [DONE] Add missing highlighting exports and fix placement
- **Effort:** < 1 day | **Source:** Architecture
- `lang-php`: No `phpHighlighting` export. `lang-angular`: No `angularHighlighting` or `parser`.
- `lang-vue`: Move highlighting from `VueParserKt` to a separate `VueHighlightKt` for consistency
  with all other language modules.

### 51. [DONE] Fix `getTagLanguage` name collision
- **Effort:** < 1 hour | **Source:** Architecture
- Both `lang-jinja` and `lang-liquid` export a top-level `tagLanguage` property. Rename to
  `jinjaTagLanguage` and `liquidTagLanguage` to prevent confusion.

### 52. Filter `$stable` fields from API dumps
> **Decision:** Post-processing Gradle task that strips `$stable` lines from `.api` files after `apiDump` runs.
- **Effort:** < 1 hour | **Source:** Architecture
- 82 classes expose Compose compiler `$stable` field. Cosmetic but adds noise.

### 53. [SKIP] Consider `IterMode` as `EnumSet` instead of bit flags
> **Skipped:** Deeply integrated with bitwise operations throughout tree traversal code (15+ call sites). Bit flags are idiomatic for parser internals and the conversion would add overhead without meaningful API improvement.
- **Effort:** 1 day | **Source:** Architecture
- `IterMode.INCLUDE_ANONYMOUS`, `.EXCLUDE_BUFFERS`, etc. are integer bit flags. Kotlin idiom is
  `EnumSet` or sealed class.

### 54. [DONE] Add `Text` convenience extensions
- **Effort:** < 1 hour | **Source:** Kotlin Ergonomics
- `Text.isEmpty`, `.isNotEmpty`, `.lineSequence()`, `operator fun get(range: IntRange)`

### 55. [DONE] Add `HighlightStyle` DSL builder
> Added `HighlightStyle.define { Tags.keyword styles SpanStyle(...) }` DSL with `HighlightStyleBuilder`.
- **Effort:** 1 day | **Source:** Kotlin Ergonomics
- Target: `highlightStyle { tags.keyword { color = Color(...) } }`

### 56. [DONE] Add property delegates for `StateField` access
> Added `operator fun StateField<T>.getValue(EditorState, KProperty<*>)` enabling `val EditorState.counter by counterField`.
- **Effort:** 1 day | **Source:** Kotlin Ergonomics
- `val EditorState.counter by counterField` via `ReadOnlyProperty` delegate.

### 57. [SKIP] Use named `fun interface` for `GutterConfig` function parameters
> **Skipped:** Kotlin lambda syntax already works well at call sites. Named `fun interface` types would add API surface without clear benefit — SAM conversion is only needed for Java interop.
- **Effort:** < 1 day | **Source:** Kotlin Ergonomics
- `lineMarker`, `lineMarkerChange`, etc. are generic function types. Named functional interfaces
  would improve readability at call sites and allow SAM conversion.

### 58. [DONE] Add validated factory for `SearchQuery`
- **Effort:** < 1 day | **Source:** Kotlin Ergonomics
- `SearchQuery.valid` is a computed property but invalid queries can still be constructed and used.
  Consider a validated factory method or builder that returns `null`/throws on invalid input.

### 59. Address Java interop type erasure
> **Decision:** Add `@JvmName` variants for key APIs to improve Java caller experience.
- **Effort:** 1–2 days | **Source:** Architecture
- `Facet`, `StateField`, `StateEffect`, `EditorState.facet()`, `EditorState.field()` lose generic
  type parameters at the JVM boundary due to type erasure. Java callers see raw `Object` types.
  Consider adding `@JvmName` variants or documenting the Java interop story.

### 60. [DONE] Verify `:state` minor completeness gaps
> All verified present: `TextIterator` interface, `wordAt` on `EditorState`, all 5 `Prec` methods.
- **Effort:** < 1 day | **Source:** Completeness
- Verify presence/absence of: `TextIterator` interface (may be handled differently in Kotlin),
  `wordAt` on `EditorState`, full coverage of `Prec` methods (`highest`, `high`, `default`,
  `low`, `lowest`).

### 61. [DONE] Fix known minor parser test issues
> Both tests already pass. The error nodes (`⚠`) are included in expected test output — these are upstream parser fidelity issues, not Kodemirror bugs.
- **Effort:** < 1 day | **Source:** Completeness
- `lang-yaml/src/commonTest/.../YamlParserTest.kt:111` — TODO: parser produces minor error node.
- `lang-python/src/commonTest/.../PythonParserTest.kt:59` — TODO: parser produces minor error node.
- Investigate whether these are upstream fidelity issues or Kodemirror-specific.

### 62. [DONE] Add platform-specific setup to bundle.md
- **Effort:** < 1 day | **Source:** Documentation
- Show complete Android and Desktop `build.gradle.kts` snippets.

### 63. Add visual screenshots to example pages
> **Note:** Depends on #31 (sample project) being completed first. Unblocked once #31 is done.
- **Effort:** 1–2 days | **Source:** Documentation
- At least for `basic.md`, `decoration.md`, `styling.md`.

### 64. [DONE] Add KDoc to legacy mode entry points
> Added KDoc to all 137 StreamParser entry points across 99 files.
- **Effort:** 1 day | **Source:** Documentation
- 103 files, one-line KDoc each for the top-level `StreamParser` val.

### 65. [DONE] Document cross-module extension discoverability
- **Effort:** < 1 day | **Source:** Frontend DX, Documentation
- Finding where `lineNumbers` lives (`:view`), `history()` (`:commands`), `bracketMatching()`
  (`:language`) is non-obvious. Add a module-to-extension index page or table in the docs.

### 66. [DONE] Add missing docs to extending guide
- **Effort:** < 1 day | **Source:** Documentation
- No discussion of `FacetReader` and when to use it vs. direct `Facet`.
- No mention of `PluginSpec.configure` pattern (used in code but not explained).

### 67. [DONE] Add `Text.of()` nuance and iterator docs to data-model guide
- **Effort:** < 1 hour | **Source:** Documentation
- Guide doesn't mention that `Text.of()` expects split lines (no embedded newlines).
- Missing coverage of `Text.iter()` variants and `RawTextCursor`.

### 68. [DONE] Add troubleshooting section to docs
- **Effort:** < 1 day | **Source:** Documentation
- Common issues like Java version requirements (project needs Java 11+), wasmJs test environment
  setup, and common build errors are not documented for users.

### 69. [DONE] Add changelog / version history
- **Effort:** < 1 day | **Source:** Documentation
- As a pre-1.0 project at version `0.1.0-SNAPSHOT`, there is no changelog documenting breaking
  changes between versions. Start a `CHANGELOG.md` to track changes as the API stabilizes.

### 70. [DONE] Create a "Migrating from CodeMirror" guide
> Created `docs-site/docs/guide/migration.md` with module mapping, porting cheat sheet, before/after code comparisons for widgets, themes, decorations, state creation, etc.
- **Effort:** 1 day | **Source:** Documentation
- Comparison tables exist inline across multiple guides. Consolidate into a dedicated migration
  reference for JavaScript developers familiar with CM6 upstream.

### 71. [BLOCKED] Consider `@codemirror/language-data` port
> **Blocked:** Lower priority — KMP apps typically know their language at compile time. The `LanguageDescription` data class was added in #24 which provides the building blocks. A full port would require registering descriptions for all ~30 language modules.
- **Effort:** 2–3 days | **Source:** Completeness
- Language metadata for dynamic loading. Lower priority since KMP apps typically know language at
  compile time.

### 72. [BLOCKED] Consider `@codemirror/lsp-client` port
> **Blocked:** Significant effort requiring KMP-compatible transport layer (WebSocket, stdio). No upstream KMP LSP client exists. Needs design decision on transport abstraction and coroutine integration.
- **Effort:** 5+ days | **Source:** Completeness
- LSP client for autocompletion, hover, go-to-definition. New upstream addition; would need
  KMP-compatible transport.

---

## Summary by Effort

| Effort | Count | Items |
|--------|-------|-------|
| < 1 hour | 15 | #1, #6, #9, #10, #11, #26, #28, #32, #34, #40, #41, #46, #47, #51, #52, #54, #67 |
| < 1 day | 17 | #2, #5, #17, #19, #20, #35, #39, #45, #50, #57, #58, #60, #61, #62, #65, #66, #68, #69 |
| 1–2 days | 17 | #4, #8, #12, #13, #14, #15, #18, #22, #27, #29, #30, #36, #37, #38, #43, #48, #55, #56, #59, #63, #70 |
| 2–3 days | 9 | #7, #16, #21, #24, #25, #31, #42, #44, #53, #71 |
| 3+ days | 4 | #3, #33, #49, #72 |
| Varies | 2 | #23, #64 |

## Summary by Source

| Source | Items |
|--------|-------|
| Architecture | #2, #3, #15, #17, #18, #33, #34, #35, #50, #51, #52, #53, #59 |
| Completeness | #4, #21, #22, #23, #24, #25, #45, #46, #60, #61, #71, #72 |
| Documentation | #1, #26, #27, #28, #29, #30, #31, #32, #62, #63, #64, #65, #66, #67, #68, #69, #70 |
| Frontend DX | #5, #6, #11, #12, #13, #20, #47, #48, #49, #65 |
| Kotlin Ergonomics | #7, #8, #9, #10, #14, #16, #19, #36, #37, #38, #39, #40, #41, #42, #43, #44, #54, #55, #56, #57, #58 |
| Multiple | #4, #6, #8, #12, #13, #15, #18, #19, #31, #43, #45, #50 |
