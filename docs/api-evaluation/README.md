# Kodemirror API Evaluation

*Generated: 2026-03-06*

## Overall Assessment

| Perspective | Grade | Status |
|---|---|---|
| Completeness | ~85-90% | 🟡 |
| Kotlin Ergonomics | B- | 🟡 |
| Frontend DX | B+ | 🟢 |
| Documentation | B+ | 🟢 |
| Architecture | (see findings) | 🟡 |

Kodemirror achieves broad structural coverage of CodeMirror 6: all 9 core infrastructure modules, all 3 Lezer parser modules, all 22 `lang-*` language packages, and all 103 legacy stream-parser modes are present. The Compose integration is architecturally sound — state hoisting, `CompositionLocal` theming, and composable widgets are all idiomatic. The project is functional and usable today.

The main concerns are friction rather than gaps: excessive wrapping types for common operations (`ExtensionList(listOf(...))`, `InsertContent.StringContent`), a missing `basicSetup` convenience bundle, and a collection of JS-isms that were carried over from the upstream port (custom `eq()` methods, `cssClass` strings, mutable lezer-lr parser internals exposed as public API). A handful of high-severity architectural issues — `ComposableSingletons` leaking into the public API surface and public mutable setters on internal parser state — should be addressed before a stable 1.0 release.

## Top 10 Highest-Priority Findings

**1. ComposableSingletons leaked into public API** (Source: Architecture, Severity: High)
- Compose compiler-generated `ComposableSingletons$SearchKt` and `ComposableSingletons$LintKt` appear in the public API dumps of `:search` and `:lint` with hash-based method names that will change with any code modification. These are implementation details that must never be part of a published API surface.
- Files: `search/api/search.api`, `lint/api/lint.api`

**2. Mutable state exposure in lezer-lr** (Source: Architecture, Severity: High)
- Internal parser state classes (`Stack`, `Parse`, `CachedToken`, `InputStream`, `SimulatedStack`) expose public setters — 10 on `Stack`, 7 on `Parse`, 6 on `InputStream`, etc. External code can corrupt the parser by calling any of these. They should be `internal` or moved to a `-internal` module.
- Files: `lezer-lr/src/commonMain/kotlin/...`

**3. No `basicSetup` convenience bundle** (Source: Completeness + Frontend DX, Severity: High)
- The single biggest onboarding friction: every new editor requires manually assembling 10+ extensions. CodeMirror upstream provides `basicSetup` as the standard entry point. Without it, developers must know which extensions to pick and which modules they live in before they can get a usable editor on screen.

**4. `ExtensionList(listOf(...))` double-wrapping** (Source: Frontend DX + Kotlin Ergonomics, Severity: High)
- This pattern appears in every editor instance, every compartment reconfiguration, and every theme bundle. `EditorStateConfig.extensions` is typed as `Extension?`, forcing the double-wrap. A `extensionListOf(vararg Extension)` factory or `List<Extension>` overload would eliminate the most common boilerplate in the API.

**5. No DSL builders for `EditorState` or `TransactionSpec`** (Source: Kotlin Ergonomics, Severity: High)
- State construction requires `EditorState.create(EditorStateConfig(..., extensions = ExtensionList(listOf(...))))` and transaction dispatch requires `view.dispatch(TransactionSpec(changes = ChangeSpec.Single(from = 0, insert = InsertContent.StringContent("..."))))`. These are the two most common API entry points and both have significant ceremony that Kotlin builder DSLs would eliminate entirely.

**6. `Text.kt` uses `// /` comment style instead of KDoc** (Source: Documentation, Severity: Critical)
- The core document data structure — `Text`, `TextIterator`, and `Line` — has zero working KDoc because all comments use `// /` (JavaScript-style) rather than `/** */`. Dokka generates empty pages for these types and the IDE shows no quick-documentation. This is a mechanical fix in one file.
- File: `/home/jmonk/git/kodemirror/state/src/commonMain/kotlin/com/monkopedia/kodemirror/state/Text.kt`

**7. Custom `eq()` methods instead of `equals()`** (Source: Kotlin Ergonomics, Severity: High)
- `SelectionRange`, `EditorSelection`, `Text`, `RangeValue`, and `WidgetType` all define `eq()` methods rather than overriding Kotlin's `equals()`/`hashCode()`. This breaks `==` operator usage, prevents these types from working correctly in collections, and surprises every Kotlin developer who uses the API.
- Files: `state/src/commonMain/kotlin/.../Selection.kt`, `Text.kt`, `RangeSet.kt`; `view/src/commonMain/kotlin/.../Decoration.kt`

**8. Missing JS completion, snippets, and autoCloseTags in `lang-javascript` and `lang-html`** (Source: Completeness, Severity: Medium)
- `:lang-javascript` is missing `localCompletionSource`, `scopeCompletionSource`, `snippets`, `typescriptSnippets`, and `autoCloseTags`. `:lang-html` is missing `autoCloseTags`, `htmlCompletion`, and `htmlCompletionSourceWith()`. JavaScript and HTML are the most common languages in code editors; these are widely expected IDE-like features.

**9. Mutable legacy-mode state classes exposed publicly** (Source: Architecture, Severity: Medium)
- All 100+ stream-parser state classes (`AplState`, `AsciiArmorState`, etc.) have public mutable setters, exposing internal tokenizer state to the outside world. These should be `internal`; the `StreamParser<State>` generic interface may need revisiting to enable this.
- Directory: `legacy-modes/src/commonMain/kotlin/.../modes/`

**10. No `rememberEditorState` Compose helper and missing `@Immutable` annotations** (Source: Frontend DX + Kotlin Ergonomics, Severity: Medium)
- Every editor repeats the identical three-line `var state by remember { mutableStateOf(EditorState.create(config)) }` + `onUpdate = { tr -> state = tr.state }` boilerplate. A `rememberEditorState` composable would eliminate this. Additionally, `EditorState` and other effectively-immutable data classes are not annotated `@Immutable`, preventing Compose from skipping recompositions.

## Suggested Next Steps

### Quick Wins (< 1 day each)

- **Convert `Text.kt` comments to KDoc** — mechanical find-and-replace in one file; fixes Dokka output, IDE hover docs, and the most fundamental API being invisible to tooling
- **Add API docs link to guide index** — one line in `docs-site/docs/guide/index.md` and `docs-site/docs/examples/index.md`; currently there is no path from the guides to the generated API reference
- **Remove `StateEffect.is()` method** — backtick-escaped `is` keyword in the public API; replace all call sites with the existing `asType()` method
- **Add `extensionListOf(vararg Extension)` factory** — eliminates the most common boilerplate pattern with a one-line addition; or add a `List<Extension>` overload to `EditorStateConfig`
- **Add `keymapOf(List<KeyBinding>)` overload** — eliminates the `*list.toTypedArray()` spread pattern that appears in almost every editor setup
- **Rename `jsHighlight` to `jsHighlighting`** to match all other language modules; rename top-level `parser` properties to `javaParser`, `pythonParser`, etc. to prevent star-import collisions

### Medium Effort (1–3 days each)

- **Add `basicSetup()` convenience function** — highest-impact improvement for onboarding; bundles common extensions (line numbers, history, bracket matching, default keymap, syntax highlighting) into a single import
- **Add `rememberEditorState` composable + `@Immutable` annotations** — eliminates repeated boilerplate; enables Compose skip optimizations
- **Replace `eq()` methods with `equals()`/`hashCode()`** on `SelectionRange`, `EditorSelection`, `Text`, `RangeValue`, and `WidgetType`
- **Filter `ComposableSingletons` from public API** — add to API dump exclusion list or mark as `internal`; these hash-named methods must not be part of a stable API surface
- **Add DSL builders for `EditorState` and `TransactionSpec`** — drastically reduces boilerplate in the two most common usage patterns; `editorState { doc("..."); extensions { +lineNumbers } }` and `view.dispatch { insert(0, "Hello") }`
- **Add KDoc to top-level facets in `Extension.kt`** — `languageData`, `allowMultipleSelections`, `lineSeparator`, `changeFilter`, `transactionFilter`, `transactionExtender`, `readOnly` all lack documentation
- **Add `String.asInsert()` extension** or `String` overload for `ChangeSpec.Single.insert` — every programmatic text change currently requires `InsertContent.StringContent("...")`
- **Port `lang-javascript` completion + autoCloseTags** — `localCompletionSource`, `scopeCompletionSource`, `snippets`, `typescriptSnippets`, `autoCloseTags`; JS is the most-used language

### Larger Projects (3+ days each)

- **Make lezer-lr internal state `internal`** — restrict visibility on `Stack`, `Parse`, `CachedToken`, `InputStream`, and the parser-internal constant objects (`Action`, `Encode`, `Rec`, `Recover`, etc.); may require a `-internal` module split
- **Restrict legacy-mode state class mutability** — 100+ public mutable state classes should not be part of the public API; review `StreamParser<State>` interface constraints and use `internal` visibility where possible
- **Add `suspend` overloads for linter and completion sources** — enables async linting and network-backed completions, which is essential for a coroutine-native Kotlin API
- **Add `basicSetup`, `minimalSetup` module** — also a good time to add a Gradle BOM (`kodemirror-bom`) so users can align all module versions with a single entry
- **Add type-safe `LanguageDataKey<T>`** — replaces `Map<String, Any?>` + unchecked cast in `EditorState.languageDataAt()`; similar to how `AnnotationType<T>` works
- **Create a complete sample project** — a minimal runnable Android or Desktop app in a `samples/` directory; currently there are no runnable examples, only code snippets
- **Add missing language-level parsing context APIs** — `ensureSyntaxTree`, `ParseContext`, `LanguageDescription`, `syntaxTreeAvailable`, `forceParsing`, `highlightingFor` from `:language`
- **Add missing view utilities** — `highlightWhitespace`, `highlightTrailingWhitespace`, `layer`/`LayerMarker`, `getPanel`, `getTooltip`

## Individual Reports

- [Completeness](completeness.md) — Comprehensive coverage of all 9 core modules, 22 language packages, and 103 legacy modes (~85-90% overall); main gaps are `basicSetup`, ~30 missing commands, and JS/HTML completion features
- [Kotlin Ergonomics](kotlin-ergonomics.md) — Structurally solid port with good use of sealed interfaces and data classes, but significant friction from JS-isms (custom `eq()`, CSS strings, untyped JSON), absent DSL builders, and zero coroutine/Flow integration
- [Frontend DX](frontend-dx.md) — Excellent Compose architecture with state hoisting, composable widgets, and `CompositionLocal` theming; held back by the missing `basicSetup` bundle and pervasive `ExtensionList(listOf(...))` boilerplate
- [Documentation](documentation.md) — Above-average for a KMP project at this stage: 24 example pages, 5 excellent concept guides, good KDoc on most modules; critical gap is `Text.kt` using the wrong comment syntax, making the core data structure invisible to Dokka
- [Architecture](architecture.md) — Faithful CM6 port with sound Compose adaptation; high-severity issues are `ComposableSingletons` leaking into public API and mutable parser internals exposed as public setters in lezer-lr
