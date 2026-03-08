# Kodemirror TODO List

*Generated from API Evaluation — 2026-03-08 (Round 2)*
*Source: Five-perspective deep evaluation (completeness, Kotlin ergonomics, frontend/mobile DX, documentation, architecture)*

## Status Key

Each item has a status prefix on its heading line:

- (no prefix) — **Pending**: not yet started
- `[DONE]` — **Complete**: implemented, tested, committed
- `[BLOCKED]` — **Blocked**: cannot proceed; reason noted below the heading
- `[SKIP]` — **Skipped**: intentionally deferred or decided against; reason noted

---

## Previously Completed (Round 1)

<details>
<summary>53 items completed in Round 1 (click to expand)</summary>

- #1 Convert `Text.kt` comments to KDoc
- #2 Filter `ComposableSingletons` from public API
- #3 Make lezer-lr internal state `internal`
- #3b Rename `EditorView` to `EditorSession` + `KodeMirror` composable
- #4 Add `basicSetup` / `minimalSetup` convenience bundle
- #5 Add `kodemirror-bom` Gradle BOM
- #6 Add `extensionListOf(vararg Extension)` factory
- #7 Add DSL builders for `EditorState` and `TransactionSpec`
- #8 Replace `eq()` methods with `equals()` / `hashCode()`
- #9 Add `String.asInsert()` extension
- #10 Remove `StateEffect.is()` method
- #11 Add `keymapOf(List<KeyBinding>)` overload
- #14 Add type-safe `LanguageDataKey<T>`
- #15 Replace CSS class strings with sealed class/enum identifiers
- #17 Prefix `parser` properties across language modules
- #18 Type the `tag` field in `TagStyleSpec`, `TagStyleRule`, and `NodeSpec`
- #19 Add operator overloads where natural
- #20 Add simple `onChange` convenience
- #21 Port `lang-javascript` completion, snippets, and autoCloseTags
- #22 Port `lang-html` autoCloseTags and completion
- #23 Add missing ~30 command variants
- #24 Add missing language parsing context APIs
- #25 Add missing view utilities
- #26 Add API docs link to guide index
- #27 Add API cross-links from example pages to API reference
- #28 Add KDoc to top-level facets in `Extension.kt`
- #29 Expand the-view guide with missing topics
- #30 Create a "Getting Started" step-by-step tutorial
- #31 Create a complete sample project
- #32 Scan for other files with `// /` comment style
- #34 Rename `tags` object to `Tags`
- #35 Make `Rule.next` and `LeafBlock.content`/`parsers` visibility restricted
- #36 Add `StateFieldSpec` DSL builder
- #37 Add Keymap DSL builder
- #38 Add Decoration building DSL
- #39 Add `EditorState` convenience extensions
- #40 Add `SelectionSpec` convenience constructors
- #41 Convert `Prec` lambda properties to functions
- #43 Consider `Completion.type` as enum/sealed class
- #45 Add missing autocomplete minor APIs
- #46 Add `selectSelectionMatches` to search module
- #47 Add `DocSpec` String overload for `EditorStateConfig`
- #48 Add `MaterialTheme` to `EditorTheme` bridge
- #50 Add missing highlighting exports and fix placement
- #51 Fix `getTagLanguage` name collision
- #52 Filter `$stable` fields from API dumps
- #54 Add `Text` convenience extensions
- #55 Add `HighlightStyle` DSL builder
- #56 Add property delegates for `StateField` access
- #58 Add validated factory for `SearchQuery`
- #60 Verify `:state` minor completeness gaps
- #61 Fix known minor parser test issues
- #62 Add platform-specific setup to bundle.md
- #64 Add KDoc to legacy mode entry points
- #65 Document cross-module extension discoverability
- #66 Add missing docs to extending guide
- #67 Add `Text.of()` nuance and iterator docs to data-model guide
- #68 Add troubleshooting section to docs
- #69 Add changelog / version history
- #70 Create a "Migrating from CodeMirror" guide

Skipped: #12, #13 (subsumed by #3b), #53 (bit flags idiomatic), #57 (lambdas sufficient)

</details>

---

## Previously Blocked (Carried Over)

### B1. [BLOCKED] Add `suspend` overloads for linter and completion sources
- **Blocked:** Requires adding kotlinx.coroutines as a new project dependency and significant
  infrastructure (CoroutineScope facets, adapter plugins). Needs architectural decision on
  coroutine integration approach.
- **Effort:** 2–3 days | **Source:** Kotlin Ergonomics

### B2. [BLOCKED] Restrict legacy-mode state class mutability
- **Blocked:** 852 mutable properties across 96 data classes. Constructor-declared `var` properties
  don't support `internal set` in Kotlin. Needs design decision on approach (internal constructors
  vs regular classes with manual `copy()`).
- **Effort:** 3+ days | **Source:** Architecture

### B3. [BLOCKED] Add `kotlinx.serialization` support alongside `toJSON`/`fromJSON`
- **Blocked:** Core types need custom `KSerializer` implementations. Needs design decision on
  serialization strategy (replace vs supplement `toJSON`/`fromJSON`).
- **Effort:** 2–3 days | **Source:** Kotlin Ergonomics

### B4. [BLOCKED] Consider inline value classes for positions
- **Blocked:** Major breaking change affecting virtually every file. Requires careful migration
  strategy to distinguish document positions vs line numbers vs arbitrary ints.
- **Effort:** 2–3 days | **Source:** Kotlin Ergonomics

### B5. [BLOCKED] Consider a `lang-kotlin` module
- **Blocked:** Requires writing a complete Lezer grammar for Kotlin from scratch (3+ days minimum).
  Kotlin has complex syntax that goes well beyond Java grammar extensions.
- **Effort:** 3+ days | **Source:** Frontend DX

### B6. [BLOCKED] Address Java interop type erasure
- **Blocked:** `@JvmName` doesn't solve type erasure. Real solutions add significant API surface
  complexity for a secondary use case (Kotlin-first library).
- **Effort:** 1–2 days | **Source:** Architecture

### B7. [BLOCKED] Add visual screenshots to example pages
- **Blocked:** Requires Compose Desktop GUI environment and screenshot capture pipeline setup
  (Roborazzi or headless Compose test runner).
- **Effort:** 1–2 days | **Source:** Documentation

### B8. [BLOCKED] Consider `@codemirror/language-data` port
- **Blocked:** Lower priority — KMP apps typically know their language at compile time. The
  `LanguageDescription` data class provides the building blocks.
- **Effort:** 2–3 days | **Source:** Completeness

### B9. [BLOCKED] Consider `@codemirror/lsp-client` port
- **Blocked:** Significant effort requiring KMP-compatible transport layer. No upstream KMP LSP
  client exists. Needs design decision on transport abstraction.
- **Effort:** 5+ days | **Source:** Completeness

---

## Priority 1 — High Impact, Core DX

### 1. Add convenience methods on `EditorSession`
- **Effort:** < 1 day | **Source:** Frontend DX, Kotlin Ergonomics
- Common operations require understanding the full transaction model. Add direct methods:
  - `setDoc(text: String)` — replace entire document
  - `insertAt(pos: Int, text: String)` — insert text at position
  - `deleteRange(from: Int, to: Int)` — delete a range
  - `select(from: Int, to: Int)` — set selection
  - `selectAll()` — select entire document
- These wrap `dispatch(TransactionSpec(...))` internally.
- **File:** `view/src/commonMain/kotlin/.../view/EditorSession.kt`

### 2. Add `rememberMaterialEditorTheme()` composable
- **Effort:** < 1 day | **Source:** Frontend DX
- The existing `editorThemeFromColors()` requires manually extracting Material colors.
  Add a `@Composable` function that reads `MaterialTheme.colorScheme` automatically:
  ```kotlin
  @Composable
  fun rememberMaterialEditorTheme(): EditorTheme
  ```
- Must be in a separate module or the view module with an optional Material3 dependency,
  since the view module shouldn't hard-depend on Material3.
- Consider also: `isSystemInDarkTheme()` awareness for automatic dark/light switching.

### 3. Add `rangeSetOf { }` DSL builder
- **Effort:** < 1 hour | **Source:** Kotlin Ergonomics
- RangeSet construction is verbose. Add:
  ```kotlin
  inline fun <T : RangeValue> rangeSetOf(block: RangeSetBuilder<T>.() -> Unit): RangeSet<T>
  ```
- **File:** `state/src/commonMain/kotlin/.../state/RangeSet.kt`

### 4. Add `onSelection` callback convenience
- **Effort:** < 1 hour | **Source:** Kotlin Ergonomics, Frontend DX
- Parallel to the existing `onChange(callback: (String) -> Unit)`:
  ```kotlin
  fun onSelection(callback: (EditorSelection) -> Unit): Extension
  ```
- Uses `EditorSession.updateListener` with `update.selectionSet` check.
- **File:** `view/src/commonMain/kotlin/.../view/EditorSession.kt`

### 5. Standardize extension composition in docs and examples
- **Effort:** < 1 day | **Source:** Frontend DX, Documentation
- Three ways to combine extensions exist (`+` operator, `extensionListOf()`,
  `ExtensionList(listOf(...))`). Examples mix all three, causing confusion.
- Make `+` operator the canonical documented pattern.
- Update all example pages and guides to use `+` consistently.
- Add a note in the getting-started guide explaining the other forms exist but `+` is preferred.
- **Files:** `docs-site/docs/examples/*.md`, `docs-site/docs/guide/getting-started.md`

### 6. Fix `Rule.setNext()` mutability
- **Effort:** < 1 day | **Source:** Architecture
- `Rule.next` has a public setter in `lezer-highlight`. Rule objects are used in highlighting
  chains and should be immutable after creation. Mutation could cause thread-safety issues
  or subtle bugs when rules are shared across sessions.
- Make `Rule.next` immutable. If internal mutation is needed, use a builder or factory pattern.
- **File:** `lezer-highlight/src/commonMain/kotlin/.../highlight/Highlight.kt`

### 7. Add validation/warnings for common configuration mistakes
- **Effort:** 1 day | **Source:** Frontend DX
- Silent failures make debugging hard. Add optional development-mode warnings for:
  - No language extension added (editor works but no syntax highlighting — not obvious why)
  - Conflicting extensions (e.g., two language facets)
  - Missing required facets accessed via `CompositionLocal`
- Could be a `developmentChecks()` extension that logs warnings, disabled in production.
- Consider: `logException` / `exceptionSink` facet already exists — use it for warnings.

### 8. Add `@Immutable` annotations for Compose skip optimizations
- **Effort:** < 1 day | **Source:** Architecture, Kotlin Ergonomics
- Annotate `EditorState`, `EditorSelection`, `EditorTheme`, `Text`, `ChangeSet` etc.
  with `@Immutable` to enable Compose recomposition skipping.
- These are all immutable persistent data structures — the annotation is truthful.
- **Files:** `state/src/commonMain/kotlin/.../state/`, `view/src/commonMain/kotlin/.../view/`

---

## Priority 2 — Medium Impact, Ergonomics

### 9. Simplify `StateField`/`StateEffect` boilerplate for common patterns
- **Effort:** 1 day | **Source:** Kotlin Ergonomics, Frontend DX
- Common patterns like "toggle a boolean", "maintain a set of values by effect", or "count
  occurrences" require 10+ lines of StateField + StateEffect wiring. Add helpers:
  ```kotlin
  // Toggle field driven by effect
  fun toggleField(default: Boolean = false): Pair<StateField<Boolean>, StateEffectType<Unit>>

  // Set field driven by add/remove effects
  fun <T> setField(default: Set<T> = emptySet()): SetFieldHandle<T>
  // where SetFieldHandle has .field, .add, .remove, .clear effect types
  ```
- **File:** `state/src/commonMain/kotlin/.../state/Facet.kt` or new `FieldHelpers.kt`

### 10. Add `ViewPlugin` convenience factory overloads
- **Effort:** < 1 day | **Source:** Kotlin Ergonomics
- The current `ViewPlugin.define(PluginSpec(...))` pattern requires wrapping in a spec.
  Add direct parameter overloads:
  ```kotlin
  fun <V : PluginValue> ViewPlugin.define(
      create: (EditorSession) -> V,
      provide: ((ViewPlugin<V>) -> Extension)? = null,
      decorations: ((V) -> DecorationSet)? = null
  ): ViewPlugin<V>
  ```
- **File:** `view/src/commonMain/kotlin/.../view/ViewPlugin.kt`

### 11. Add more built-in themes
- **Effort:** 2–3 days | **Source:** Frontend DX
- Only `oneDark` exists as a complete theme (editor theme + syntax highlighting).
  Port or create 2–3 additional popular themes:
  - GitHub Light — popular light theme
  - Dracula — popular dark theme
  - Material — matches Material Design colors
- Each theme should be a separate module (`:theme-github-light`, etc.) following the
  `:theme-one-dark` pattern.

### 12. Add `RangeSet` functional operators
- **Effort:** < 1 day | **Source:** Kotlin Ergonomics
- `RangeSet` lacks standard collection-like operations. Add:
  - `fun <T : RangeValue> RangeSet<T>.forEach(action: (Range<T>) -> Unit)`
  - `fun <T : RangeValue> RangeSet<T>.asSequence(): Sequence<Range<T>>`
  - `fun <T : RangeValue> RangeSet<T>.filter(predicate: (Range<T>) -> Boolean): RangeSet<T>`
- **File:** `state/src/commonMain/kotlin/.../state/RangeSet.kt`

### 13. Use dedicated data class for `Completion.applyFn` parameters
- **Effort:** < 1 hour | **Source:** Architecture
- `Completion.applyFn` accepts `(EditorSession, Completion, Int, Int) -> Unit` where the two
  `Int` parameters are `from` and `to` positions but this isn't clear from the signature.
  Replace with:
  ```kotlin
  data class CompletionApplyContext(
      val session: EditorSession,
      val completion: Completion,
      val from: Int,
      val to: Int
  )
  typealias CompletionApplyFn = (CompletionApplyContext) -> Unit
  ```
- **Files:** `autocomplete/src/commonMain/kotlin/.../autocomplete/Completion.kt`

### 14. Typed `LanguageData` wrapper
- **Effort:** 1 day | **Source:** Kotlin Ergonomics
- The `languageData` facet still uses `Map<String, Any?>` internally even though
  `LanguageDataKey<T>` exists. Add a type-safe wrapper:
  ```kotlin
  class LanguageDataMap(private val data: Map<String, Any?>) {
      operator fun <T> get(key: LanguageDataKey<T>): T?
  }
  ```
- Update `EditorState.languageDataAt` to return `LanguageDataMap` instead of raw map.
- **File:** `state/src/commonMain/kotlin/.../state/State.kt`

---

## Priority 3 — Documentation

### 15. Document the `:collab` module
- **Effort:** 1 day | **Source:** Documentation
- The collab module exists and has examples but no dedicated guide. Create
  `docs-site/docs/guide/collaboration.md` covering:
  - Protocol overview (operational transformation)
  - `receiveUpdates()` / `sendableUpdates()` API
  - Server integration patterns
  - Error handling and conflict resolution
- Also add a "Related API" section to the existing `collab.md` example.

### 16. Document the `:merge` module
- **Effort:** 1 day | **Source:** Documentation
- The merge module is undocumented. Create `docs-site/docs/guide/merge.md` covering:
  - `MergeView` configuration and usage
  - Unified vs side-by-side diff view
  - Integration with version control workflows
- Add an example page `docs-site/docs/examples/merge.md`.

### 17. Expand troubleshooting guide
- **Effort:** < 1 day | **Source:** Documentation
- Current troubleshooting has only 3 sections. Add 10+ more common issues:
  - "No syntax highlighting" (missing language extension)
  - "Editor not responding to key events" (missing keymap)
  - "Theme colors not applying" (EditorTheme vs HighlightStyle confusion)
  - "Completion not showing" (missing completion source)
  - "Read-only vs non-editable" (which to use when)
  - "Extensions not taking effect" (precedence issues)
  - "State not updating after dispatch" (immutable state pattern)
  - "Memory usage growing" (decoration/plugin lifecycle)
  - "Editor blank on first render" (timing/layout issues)
  - "Undo not working" (missing `history()` extension)
- **File:** `docs-site/docs/guide/troubleshooting.md`

### 18. Fill KDoc gaps in search, merge, lint, and autocomplete modules
- **Effort:** 1–2 days | **Source:** Documentation
- These modules have lower KDoc coverage than core modules. Add KDoc to:
  - All public classes and their primary methods
  - All public configuration data classes and their fields
  - All public facets and state fields
  - Key callback/lambda parameter semantics
- Focus on: what does each parameter mean? When is `null` returned? What are valid values?
- **Modules:** `:search`, `:merge`, `:lint`, `:autocomplete`

### 19. Add `@see` and `@sample` cross-references to KDoc
- **Effort:** 1 day | **Source:** Documentation
- Public API KDoc lacks cross-references. Add:
  - `@see` links between related types (e.g., `StateField` ↔ `StateFieldSpec`)
  - `@sample` references to example code in tests or samples
  - `@see` links from commands to their configuration facets
- Focus on the most-used APIs in `:state`, `:view`, `:commands`.

### 20. Document nullability contracts
- **Effort:** < 1 day | **Source:** Documentation, Kotlin Ergonomics
- Several APIs return nullable types without explaining when/why null is returned:
  - `EditorSession.coordsAtPos()` → `Rect?` — when does this return null?
  - `EditorSession.posAtCoords()` → `Int?` — when?
  - `CompletionResult.to: Int?` — defaults to cursor position when null
  - `RangeValue.startSide` / `endSide` — what do the integer values mean?
- Add clarifying KDoc to all nullable returns and ambiguous numeric parameters.
- **Files:** `view/.../EditorSession.kt`, `autocomplete/.../Completion.kt`, `state/.../RangeSet.kt`

### 21. Add architecture guide for extension system
- **Effort:** 1 day | **Source:** Documentation
- Create `docs-site/docs/guide/extension-architecture.md` explaining:
  - When to use `StateField` vs `Facet` vs `StateEffect`
  - Extension ordering guarantees and precedence
  - How facet combine functions work and their requirements
  - Extension conflict detection strategies
  - How to build reusable, composable extensions (library quality)
  - Transaction filter and extender patterns

### 22. Add performance and large document guide
- **Effort:** 1 day | **Source:** Documentation
- Create `docs-site/docs/guide/performance.md` covering:
  - How `LazyColumn` virtualization works for large documents
  - Decoration performance (many decorations vs few)
  - Parser performance and `ensureSyntaxTree` / `forceParsing`
  - ViewPlugin update optimization patterns
  - When to use `StateField` vs `ViewPlugin` for performance

### 23. Add testing guide for editors
- **Effort:** 1 day | **Source:** Documentation
- Create `docs-site/docs/guide/testing.md` covering:
  - Unit testing `StateField` update logic
  - Testing commands with `EditorSession`
  - Integration testing with `KodeMirror` composable
  - Testing custom completion sources
  - Testing linter implementations
  - Patterns for test fixtures (creating pre-configured states)

### 24. Document `readOnly` vs `editable` clearly
- **Effort:** < 1 hour | **Source:** Frontend DX, Documentation
- Two APIs for "making the editor non-editable" confuse developers:
  - `readOnly.of(true)` — prevents editing, allows selection/copy
  - `editable.of(false)` — blocks all interaction
- Add a clear comparison table in the getting-started guide and troubleshooting guide.
- **File:** `docs-site/docs/guide/getting-started.md`, `docs-site/docs/guide/troubleshooting.md`

---

## Priority 4 — Polish & Nice-to-Have

### 25. Add `inline`/`reified` alternative for `ViewPlugin.fromClass`
- **Effort:** < 1 hour | **Source:** Kotlin Ergonomics
- `ViewPlugin.fromClass(factory)` uses `@Suppress("UNCHECKED_CAST")` and erases the
  specific plugin type. Add a `reified` alternative:
  ```kotlin
  inline fun <reified V> fromClass(noinline factory: () -> V): ViewPlugin<V>
      where V : PluginValue, V : DecorationSource
  ```
- **File:** `view/src/commonMain/kotlin/.../view/ViewPlugin.kt`

### 26. Document extension ordering guarantees
- **Effort:** < 1 day | **Source:** Architecture
- When multiple providers supply values to a facet, order matters for combine functions.
  Current behavior: order based on extension registration order. This needs to be
  explicitly documented as a guarantee or explicitly noted as undefined.
- Add to extending guide and KDoc on `Facet.define()`.

### 27. Add API stability markers
- **Effort:** < 1 day | **Source:** Documentation, Architecture
- No marking of experimental vs stable APIs. Consider:
  - `@ExperimentalKodemirrorApi` annotation for APIs that may change
  - `@DelicateKodemirrorApi` for APIs that are easy to misuse
  - Document stability guarantees in the architecture guide

### 28. Create lang module template/guide for contributors
- **Effort:** < 1 day | **Source:** Documentation, Architecture
- Formalize the expected structure for `:lang-*` modules:
  - Required exports: `xxxLanguage`, `xxx()`, `xxxHighlighting`
  - Optional exports: completion sources, folding, autoCloseTags
  - Test structure: parser tests, highlighting tests
  - Build configuration template
- Create as a contributor guide or template directory.

### 29. Add extension conflict detection (development mode)
- **Effort:** 1–2 days | **Source:** Architecture
- When multiple extensions provide conflicting configurations (e.g., two language modules),
  the "last one wins" behavior is silent. Add an optional diagnostic extension:
  ```kotlin
  val extensionDiagnostics: Extension  // Logs conflicts and warnings
  ```
- Only for development — not for production builds.

### 30. Consider Android-specific guidance
- **Effort:** 1 day | **Source:** Frontend DX, Documentation
- No Android-specific documentation exists. Cover:
  - IME (Input Method Editor) integration behavior
  - Keyboard handling differences from Desktop
  - Touch interaction (tap-to-place cursor, drag selection)
  - Performance considerations on mobile
  - Sample Android project in `samples/` directory

### 31. Add editor testing utilities
- **Effort:** 1–2 days | **Source:** Kotlin Ergonomics, Frontend DX
- Testing editors requires creating full `EditorSession` instances with extensions.
  Add test utilities:
  ```kotlin
  // kodemirror-test module
  fun testEditorSession(doc: String = "", extensions: Extension = Extension.empty): EditorSession
  fun EditorSession.typeText(text: String)
  fun EditorSession.pressKey(key: String)
  fun EditorSession.assertDoc(expected: String)
  fun EditorSession.assertSelection(anchor: Int, head: Int)
  ```

### 32. Add `StateField` serialization sealed type
- **Effort:** < 1 day | **Source:** Architecture
- `StateFieldSpec` allows optional `toJSON`/`fromJSON` functions. If not provided, the
  field silently becomes non-serializable. Consider a sealed type:
  ```kotlin
  sealed interface FieldSerialization<V> {
      data object None : FieldSerialization<Nothing>
      data class Custom<V>(val toJSON: (V) -> Any?, val fromJSON: (Any?) -> V) : FieldSerialization<V>
  }
  ```
- Makes the serialization contract explicit.

---

## Summary

| Priority | Pending | Blocked | Description |
|----------|---------|---------|-------------|
| 1 | 8 | — | High impact core DX improvements |
| 2 | 6 | — | Medium impact ergonomics |
| 3 | 10 | — | Documentation gaps |
| 4 | 8 | — | Polish and nice-to-have |
| Blocked | — | 9 | Carried over, need design decisions |
| **Total** | **32** | **9** | |
