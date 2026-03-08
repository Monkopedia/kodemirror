# Frontend Developer Experience Evaluation

## Summary

Kodemirror provides a remarkably clean Compose Multiplatform integration for a code editor library.
The core `EditorSession` composable follows idiomatic Compose patterns (state hoisting via
`state`/`onUpdate`), theming uses `data class` and `CompositionLocal` instead of CSS, and widgets
are `@Composable` lambdas. The extension-based architecture is powerful and composable, though it
inherits CodeMirror 6's learning curve: developers must understand facets, compartments, state
fields, and state effects to go beyond basic usage.

The biggest friction points are (1) excessive wrapping types for simple operations
(`InsertContent.StringContent`, `ExtensionList(listOf(...))`, `ChangeSpec.Single`), (2) the absence
of a `basicSetup` convenience bundle, and (3) the transaction/dispatch model that requires manual
state wiring on every editor.

**Overall grade: B+** -- Excellent architecture, good Compose integration, but needs convenience
layers and reduced boilerplate for common tasks.

---

## Getting Started

### Minimum Viable Editor

The absolute minimum code to get an editor on screen:

```kotlin
@Composable
fun MinimalEditor() {
    var state by remember {
        mutableStateOf(
            EditorState.create(EditorStateConfig(
                doc = "Hello, world!".asDoc()
            ))
        )
    }

    EditorSession(
        state = state,
        onUpdate = { tr -> state = tr.state },
        modifier = Modifier.fillMaxSize()
    )
}
```

**Line count**: ~12 lines (excluding imports)

**Assessment**: This is clean and idiomatic Compose. The `state`/`onUpdate` pattern mirrors how
`TextField` works. The `asDoc()` extension is a nice touch. However, this bare editor has no line
numbers, no keybindings, no undo, and no syntax highlighting -- it is essentially an unstyled
textarea.

### Realistic Minimum Editor

A usable editor requires ~20 lines of extensions:

```kotlin
@Composable
fun FullEditor() {
    var state by remember {
        mutableStateOf(
            EditorState.create(EditorStateConfig(
                doc = "function hello() {\n  return \"world\"\n}\n".asDoc(),
                extensions = ExtensionList(listOf(
                    lineNumbers,
                    highlightActiveLine,
                    highlightSpecialChars,
                    history(),
                    bracketMatching(),
                    highlightSelectionMatches(),
                    defaultKeymapExtension(),
                    keymapOf(*indentWithTab.toTypedArray()),
                    javascript(),
                    syntaxHighlighting(defaultHighlightStyle)
                ))
            ))
        )
    }

    EditorSession(
        state = state,
        onUpdate = { tr -> state = tr.state },
        modifier = Modifier.fillMaxSize()
    )
}
```

**Line count**: ~25 lines
**Imports required**: 6+ imports across `state`, `view`, `commands`, `language`, `search`, and a
`lang-*` module

**Severity: High** -- Every new developer must assemble this extension list manually. CodeMirror
upstream provides `basicSetup` to bundle the common extensions; Kodemirror does not.

### Gradle Setup

Dependencies are well-organized and modular:

```kotlin
kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation("com.monkopedia.kodemirror:state:0.1.0-SNAPSHOT")
            implementation("com.monkopedia.kodemirror:view:0.1.0-SNAPSHOT")
            implementation("com.monkopedia.kodemirror:commands:0.1.0-SNAPSHOT")
            implementation("com.monkopedia.kodemirror:language:0.1.0-SNAPSHOT")
            implementation("com.monkopedia.kodemirror:search:0.1.0-SNAPSHOT")
            implementation("com.monkopedia.kodemirror:lang-javascript:0.1.0-SNAPSHOT")
        }
    }
}
```

This is 6 separate dependencies for a basic editor. Transitive dependencies are handled well
(`:language` pulls in `:state`, `:view`, lezer modules), but the developer still must know which
modules to add for which features. A single `kodemirror-bom` or `kodemirror-bundle` dependency
would help.

---

## Common Workflows

### Adding a Language

**Code required**: 1 line + 1 dependency

```kotlin
// Add `:lang-javascript` dependency, then:
javascript()  // returns Extension
```

For languages not in the 22 `lang-*` modules, use `StreamLanguage.define()` with a `StreamParser` or
one of the 100+ legacy modes:

```kotlin
import com.monkopedia.kodemirror.legacy.modes.python
val pythonLanguage = StreamLanguage.define(python)
```

**Assessment**: Excellent. Language modules follow a consistent pattern -- each exports a top-level
function (e.g., `javascript()`, `python()`, `html()`) that returns `LanguageSupport` or
`Extension`. The `StreamParser` interface is well-documented for custom languages.

**Available languages**: 22 tree-sitter-style `lang-*` modules (JavaScript, Python, Rust, Go, Java,
C++, HTML, CSS, JSON, YAML, SQL, PHP, Markdown, XML, Vue, Angular, Sass, Less, Liquid, Jinja, Wast,
Grammar) plus 100+ legacy modes.

**Severity: Low** -- No issues found. The language ecosystem is comprehensive.

### Custom Theme

**Code required**: ~20 lines for a complete theme

```kotlin
val myTheme = EditorTheme(
    background = Color(0xFF1E1E2E),
    foreground = Color(0xFFCDD6F4),
    cursor = Color(0xFFF5E0DC),
    selection = Color(0xFF45475A),
    activeLineBackground = Color(0x15CDD6F4),
    gutterBackground = Color(0xFF1E1E2E),
    gutterForeground = Color(0xFF6C7086),
    gutterActiveForeground = Color(0xFFCDD6F4),
    contentTextStyle = TextStyle(
        fontFamily = FontFamily.Monospace,
        fontSize = 14.sp,
        lineHeight = (14 * 1.5).sp,
        color = Color(0xFFCDD6F4)
    ),
    dark = true
)

// Apply it:
editorTheme.of(myTheme)
```

For syntax highlighting colors (separate from editor chrome):

```kotlin
val myHighlighting = HighlightStyle.define(listOf(
    TagStyleSpec(tags.keyword, SpanStyle(color = Color(0xFFCBA6F7))),
    TagStyleSpec(tags.string, SpanStyle(color = Color(0xFFA6E3A1))),
    TagStyleSpec(tags.comment, SpanStyle(color = Color(0xFF6C7086))),
))

// Complete theme = editor theme + syntax highlighting
val catppuccin = ExtensionList(listOf(
    editorTheme.of(myTheme),
    syntaxHighlighting(myHighlighting)
))
```

**Assessment**: Very good. The `EditorTheme` data class is well-designed with sensible defaults --
you only need to override the colors you want to change. Using `Color` and `SpanStyle` instead of
CSS is a massive improvement for Compose developers. The separation of editor theme vs. syntax
highlighting is logical but adds a step.

The `EditorTheme` class has 24 color properties, all well-documented with KDoc comments. Themes are
accessible via `LocalEditorTheme` CompositionLocal, which is idiomatic Compose.

Runtime theme switching uses the Compartment pattern, which works but requires understanding
compartments:

```kotlin
val themeCompartment = Compartment()
// ... set up with themeCompartment.of(oneDark)
// ... switch with themeCompartment.reconfigure(editorTheme.of(lightEditorTheme))
```

**Severity: Low** -- The theming system is well-designed. Only minor friction in needing two
separate concepts (EditorTheme + HighlightStyle) for a complete theme.

### Custom Completion

**Code required**: ~15 lines

```kotlin
val myCompletions: CompletionSource = { context ->
    val word = context.matchBefore(Regex("\\w*"))
    if (word == null || (word.from == word.to && !context.explicit)) {
        null
    } else {
        CompletionResult(
            from = word.from,
            options = listOf(
                Completion(label = "hello", type = "keyword"),
                Completion(label = "world", type = "variable", detail = "a greeting"),
            )
        )
    }
}

// Register it:
autocompletion(CompletionConfig(override = listOf(myCompletions)))
```

Or for simple static lists:

```kotlin
val source = completeFromList(listOf(
    Completion(label = "fun", type = "keyword"),
    Completion(label = "val", type = "keyword"),
))
autocompletion(CompletionConfig(override = listOf(source)))
```

**Assessment**: Good. `CompletionSource` as a function type is flexible. `completeFromList` is a
nice shortcut. The `Completion` data class has sensible defaults. `CompletionConfig` uses named
parameters well.

One friction point: `Completion.type` is a `String` rather than an enum or sealed class, so there is
no discoverability for valid type values ("keyword", "function", "variable", etc.) without reading
docs.

**Severity: Low** -- Clean API. Minor discoverability issue with string-typed `type` field.

### Search & Replace

**Code required**: 1 line

```kotlin
search()  // returns Extension
```

This installs the search panel (Ctrl+F / Cmd+F), find next/previous, replace, regex search, and all
associated keybindings.

For programmatic search:

```kotlin
val query = SearchQuery(
    search = "hello",
    caseSensitive = false,
    regexp = false,
    wholeWord = true,
    replace = "world"
)
```

**Assessment**: Excellent. The `search()` function is a one-liner that gives you a complete search
experience. The `SearchQuery` data class has good defaults. Commands like `openSearchPanel`,
`findNext`, `findPrevious`, `replaceAll` are all available as top-level functions.

**Severity: None** -- No issues.

### Line Numbers, Folding, Gutters

**Code required**: 1-2 lines each

```kotlin
// Line numbers
lineNumbers

// Code folding
codeFolding()
foldGutter()

// Active line gutter highlight
highlightActiveLineGutter

// Custom gutter (e.g. breakpoints) -- more involved
gutter(GutterConfig(
    cssClass = "cm-breakpoints",
    lineMarker = { view, lineFrom -> /* ... */ }
))
```

**Assessment**: Good. `lineNumbers` is a simple top-level property. `codeFolding()` and
`foldGutter()` are clean function calls. Custom gutters are more involved but well-documented.

One naming issue: `GutterConfig.cssClass` refers to CSS, which has no meaning in a Compose
environment. This is a leftover from the CodeMirror port.

**Severity: Low** -- `cssClass` naming is confusing in a Compose context.

### Read-Only Mode

**Code required**: 1 line

```kotlin
// Immutable document, but cursor/selection still work:
readOnly.of(true)

// Fully non-interactive:
editable.of(false)
```

Runtime toggling via Compartment:

```kotlin
val readOnlyCompartment = Compartment()
// ... set up with readOnlyCompartment.of(readOnly.of(false))
// ... toggle with readOnlyCompartment.reconfigure(readOnly.of(isReadOnly))
```

**Assessment**: Clean. Two distinct modes (`readOnly` vs `editable`) with clear semantic
differences. The facet API (`readOnly.of(true)`) reads naturally.

**Severity: None** -- No issues.

### State Management

The `EditorSession` composable uses state hoisting -- the canonical Compose pattern:

```kotlin
@Composable
fun EditorSession(
    state: EditorState,
    onUpdate: (Transaction) -> Unit,
    modifier: Modifier = Modifier
)
```

State is held via `mutableStateOf`:

```kotlin
var state by remember { mutableStateOf(EditorState.create(config)) }
```

`EditorState` is immutable. Every change produces a new `EditorState` via `Transaction`. The
`onUpdate` callback receives the transaction, and the caller extracts the new state:

```kotlin
onUpdate = { tr -> state = tr.state }
```

**Assessment**: The state hoisting pattern is correct and idiomatic. However:

1. **Every editor requires the same boilerplate**: `var state by remember { mutableStateOf(...) }`
   plus `onUpdate = { tr -> state = tr.state }`. This 3-line pattern is repeated identically every
   time.

2. **No `rememberEditorState` helper**: Compose provides `rememberTextFieldValue` for text fields.
   Kodemirror could provide `rememberEditorState` to eliminate the boilerplate.

3. **`EditorState` is not `Stable`/`Immutable`**: If `EditorState` is not annotated with
   `@Immutable`, Compose may perform unnecessary recompositions. The class is immutable in practice
   (new state is always a new instance), but without the annotation, Compose cannot know this.

4. **Transaction vs. state**: The `onUpdate` callback provides a `Transaction`, but 99% of callers
   only need `tr.state`. This is a minor ergonomic issue.

**Severity: Medium** -- Repetitive boilerplate; missing convenience functions.

### Event Handling

Responding to document changes:

```kotlin
onUpdate = { tr ->
    state = tr.state
    if (tr.docChanged) {
        // Document was modified
        val newText = tr.state.doc.toString()
    }
}
```

Reading cursor position:

```kotlin
val sel = state.selection
val main = sel.main
if (main.empty) {
    // cursor at main.head
} else {
    // selection from main.from to main.to
}
```

Observing specific changes via `ViewPlugin`:

```kotlin
class MyPlugin(state: EditorState) : PluginValue {
    override fun update(update: ViewUpdate) {
        if (update.docChanged) { /* ... */ }
        if (update.focusChanged) { /* ... */ }
        if (update.viewportChanged) { /* ... */ }
    }
}
```

**Assessment**: The `Transaction` provides rich information (`docChanged`, `changes`, `effects`,
`annotations`). The `ViewUpdate` class has convenient boolean flags. However, there is no simple
`onChange: (String) -> Unit` callback for developers who just want the text -- they must understand
the transaction model.

**Severity: Medium** -- No simple `onChange` callback for common use cases.

---

## Compose Integration

### Strengths

1. **State hoisting**: `EditorSession(state, onUpdate, modifier)` follows the standard Compose
   unidirectional data flow pattern.

2. **CompositionLocal for themes**: `LocalEditorTheme` provides the current theme to all nested
   composables (panels, tooltips, widgets, gutters) without prop drilling.

3. **Composable widgets**: `WidgetType.Content()`, `GutterMarker.Content()`, `Panel.content`,
   `Tooltip.content` are all `@Composable`, enabling full Compose UI in decorations.

4. **LazyColumn virtualization**: The editor uses `LazyColumn` for virtual scrolling, which is the
   correct Compose approach for large lists.

5. **SpanStyle instead of CSS**: Decorations use `SpanStyle` and `TextStyle` instead of CSS
   class names. This is native Compose and works cross-platform.

6. **Modifier support**: The `EditorSession` composable accepts a `Modifier`, enabling standard Compose
   layout composition.

### Gaps

1. **No `rememberEditorState`**: Every call site repeats the `remember { mutableStateOf(...) }`
   pattern.

2. **No `@Stable`/`@Immutable` annotations**: `EditorState`, `EditorTheme`, `Completion`, and other
   data classes that are effectively immutable are not annotated, potentially causing unnecessary
   recompositions.

3. **`cssClass` in `GutterConfig`**: This CSS concept has leaked into the Compose API. It appears in
   `GutterConfig` and `Diagnostic.markClass`.

4. **No integration with `MaterialTheme`**: There is no automatic bridging between
   `MaterialTheme.colorScheme` and `EditorTheme`. Developers must manually map Material colors to
   editor colors.

5. **`ExtensionList(listOf(...))` wrapping**: Every extension list must be wrapped in
   `ExtensionList(listOf(...))`. This is the most frequent boilerplate pattern in the API.

---

## Boilerplate Analysis

### ExtensionList Wrapping (appears in every editor setup)

**Current**:
```kotlin
extensions = ExtensionList(listOf(
    lineNumbers,
    highlightActiveLine,
    history(),
    // ...
))
```

**Ideal**:
```kotlin
extensions = listOf(
    lineNumbers,
    highlightActiveLine,
    history(),
    // ...
)
// or even:
extensions = extensionListOf(
    lineNumbers,
    highlightActiveLine,
    history(),
)
```

`EditorStateConfig.extensions` is typed as `Extension?`, so passing a raw `List<Extension>` does not
work. The developer must remember to wrap it in `ExtensionList(listOf(...))`. This double-wrapping
(`ExtensionList` + `listOf`) is the single most common source of ceremony.

**Severity: High** -- Affects every editor instance and every reconfiguration call.

### InsertContent.StringContent Wrapping

**Current**:
```kotlin
ChangeSpec.Single(
    from = 0,
    insert = InsertContent.StringContent("Hello ")
)
```

**Ideal**:
```kotlin
ChangeSpec.Single(from = 0, insert = "Hello ")
```

Every text insertion requires wrapping the string in `InsertContent.StringContent(...)`. A String
overload or implicit conversion would eliminate this.

**Severity: Medium** -- Affects every programmatic text change.

### keymapOf Spread

**Current**:
```kotlin
keymapOf(*indentWithTab.toTypedArray())
```

**Ideal**:
```kotlin
keymapOf(indentWithTab)
// or just:
indentWithTab.asExtension()
```

The `keymapOf` function takes `vararg KeyBinding`, but key binding lists are `List<KeyBinding>`.
Developers must spread with `*list.toTypedArray()` every time. A `List<KeyBinding>` overload would
fix this.

**Severity: Medium** -- Appears in most editor setups; the spread syntax is unfamiliar to many
developers.

### State + onUpdate Boilerplate

**Current** (repeated every time):
```kotlin
var state by remember {
    mutableStateOf(EditorState.create(config))
}
EditorSession(
    state = state,
    onUpdate = { tr -> state = tr.state },
    modifier = Modifier.fillMaxSize()
)
```

**Ideal**:
```kotlin
val state = rememberEditorState(config)
EditorSession(
    state = state,
    modifier = Modifier.fillMaxSize()
)
```

**Severity: Medium** -- Repeated identically in every editor instance.

---

## Naming & Discoverability

### Strengths

1. **Extension functions follow CodeMirror naming**: `lineNumbers`, `highlightActiveLine`,
   `history()`, `bracketMatching()` -- developers familiar with CodeMirror will find these
   immediately.

2. **Consistent pattern**: Properties for no-config features (`lineNumbers`, `highlightActiveLine`),
   functions for configurable features (`history()`, `autocompletion()`, `linter(...)`).

3. **Module naming is clear**: `:state`, `:view`, `:commands`, `:language`, `:search`,
   `:autocomplete`, `:lint` -- each module's purpose is obvious.

4. **Data class configs**: `CompletionConfig`, `LintConfig`, `HistoryConfig`,
   `BracketMatchingConfig` -- all follow `*Config` naming and use named parameters with defaults.

### Issues

1. **`asDoc()` vs `DocSpec`**: The developer must call `"text".asDoc()` rather than passing a string
   directly to `EditorStateConfig.doc`. The `DocSpec` sealed interface
   (`DocSpec.StringDoc`/`DocSpec.TextDoc`) is an implementation detail leaking into the API.

2. **`Completion.type` is a raw String**: Valid values ("keyword", "function", "variable",
   "property", "method", "class") are not discoverable from the type system.

3. **`SelectionSpec` variants**: `SelectionSpec.CursorSpec` and
   `SelectionSpec.EditorSelectionSpec` are verbose. Compare:
   ```kotlin
   // Current:
   selection = SelectionSpec.CursorSpec(anchor = 10)
   // vs:
   selection = SelectionSpec.EditorSelectionSpec(EditorSelection.single(anchor = 5, head = 15))
   ```
   These could be simplified with factory functions directly on `TransactionSpec`.

4. **`cssClass` in Compose APIs**: `GutterConfig.cssClass` and `Diagnostic.markClass` reference CSS
   concepts that do not apply in Compose. These should be renamed or deprecated.

5. **Module import discoverability**: Finding where `lineNumbers` lives (`:view`), where `history()`
   lives (`:commands`), or where `bracketMatching()` lives (`:language`) requires documentation.
   IDE auto-import helps, but the cross-module scattering is not intuitive.

---

## Pain Points

### 1. No `basicSetup` Bundle

**Severity: High**

Every CodeMirror tutorial starts with `basicSetup`. Kodemirror forces developers to assemble 10+
extensions manually for a usable editor. This is the biggest onboarding friction.

**Impact**: Every new developer. Every new project.

**Recommendation**: Provide a `basicSetup()` function (or `basicSetup` property) that bundles
the common extensions:

```kotlin
fun basicSetup(): Extension = ExtensionList(listOf(
    lineNumbers,
    highlightActiveLine,
    highlightActiveLineGutter,
    highlightSpecialChars,
    history(),
    foldGutter(),
    bracketMatching(),
    closeBrackets(),
    autocompletion(),
    highlightSelectionMatches(),
    search(),
    defaultKeymapExtension(),
    keymapOf(*indentWithTab.toTypedArray()),
    syntaxHighlighting(defaultHighlightStyle),
))
```

### 2. ExtensionList(listOf(...)) Double-Wrapping

**Severity: High**

The `ExtensionList(listOf(...))` wrapping appears everywhere -- state config, compartment
reconfiguration, theme bundling. It should accept varargs or have a factory function.

**Recommendation**: Add `extensionListOf(vararg Extension)` or make `EditorStateConfig.extensions`
accept `List<Extension>` directly.

### 3. InsertContent.StringContent Wrapping

**Severity: Medium**

Every text insertion requires `InsertContent.StringContent("text")` instead of just `"text"`.

**Recommendation**: Add a `String` overload for `insert` parameter in `ChangeSpec.Single`, or
provide a `String.asInsert()` extension.

### 4. Missing rememberEditorState

**Severity: Medium**

The state hoisting boilerplate is identical across every editor:

```kotlin
var state by remember { mutableStateOf(EditorState.create(config)) }
EditorSession(state = state, onUpdate = { tr -> state = tr.state }, modifier = ...)
```

**Recommendation**: Provide:

```kotlin
@Composable
fun rememberEditorState(config: EditorStateConfig): MutableState<EditorState>
```

or an overload of `EditorSession` that manages its own state:

```kotlin
@Composable
fun EditorSession(
    initialDoc: String,
    extensions: List<Extension> = emptyList(),
    onChange: ((String) -> Unit)? = null,
    modifier: Modifier = Modifier
)
```

### 5. keymapOf Spread Pattern

**Severity: Medium**

`keymapOf(*indentWithTab.toTypedArray())` is awkward. Developers must remember the spread
trick.

**Recommendation**: Add a `keymapOf(bindings: List<KeyBinding>)` overload.

### 6. No Simple onChange Callback

**Severity: Medium**

Many developers just want `onChange: (String) -> Unit`. The transaction model is powerful but
overkill for simple use cases.

**Recommendation**: Provide a convenience composable or a helper that extracts the document text:

```kotlin
EditorSession(
    state = state,
    onUpdate = { tr ->
        state = tr.state
        if (tr.docChanged) onTextChanged(tr.state.doc.toString())
    },
    modifier = modifier
)
```

### 7. CSS Naming Leaks

**Severity: Low**

`GutterConfig.cssClass` and `Diagnostic.markClass` reference CSS, which has no meaning in Compose.

**Recommendation**: Rename to `id` or `tag`, or deprecate and add Compose-appropriate replacements.

### 8. DocSpec Sealed Interface Leaks

**Severity: Low**

`EditorStateConfig.doc` is typed as `DocSpec?`, requiring `"text".asDoc()`. A `String` overload on
`EditorStateConfig` would be more ergonomic.

**Recommendation**: Add a secondary constructor or factory that accepts `String` directly:

```kotlin
EditorState.create(doc = "Hello, world!", extensions = ...)
```

---

## Severity Ratings Summary

| Finding | Severity | Category |
|---------|----------|----------|
| No `basicSetup` bundle | **High** | Missing convenience |
| `ExtensionList(listOf(...))` double-wrapping | **High** | Excessive boilerplate |
| `InsertContent.StringContent` wrapping | **Medium** | Excessive boilerplate |
| Missing `rememberEditorState` | **Medium** | Compose integration gap |
| `keymapOf(*list.toTypedArray())` spread | **Medium** | Excessive boilerplate |
| No simple `onChange` callback | **Medium** | Compose integration gap |
| State + onUpdate boilerplate repetition | **Medium** | Excessive boilerplate |
| No `@Stable`/`@Immutable` annotations | **Medium** | Compose integration gap |
| `Completion.type` is untyped String | **Low** | Naming/discoverability |
| CSS naming leaks (`cssClass`, `markClass`) | **Low** | Naming |
| `DocSpec` sealed interface leaks into API | **Low** | Excessive wrapping |
| No `MaterialTheme` bridge | **Low** | Compose integration gap |
| `SelectionSpec` variant verbosity | **Low** | Naming/discoverability |
| Cross-module extension discoverability | **Low** | Naming/discoverability |

---

## Recommendations (Prioritized)

### Priority 1: Reduce Setup Friction

1. **Add `basicSetup()` function** -- A single function that returns a sensible default extension
   set. This is the single highest-impact improvement for onboarding.

2. **Add `extensionListOf(vararg Extension)`** -- Eliminate `ExtensionList(listOf(...))` ceremony
   everywhere.

3. **Add `keymapOf(bindings: List<KeyBinding>)` overload** -- Eliminate the spread pattern.

### Priority 2: Compose Conveniences

4. **Add `rememberEditorState(config)`** -- Compose helper that encapsulates the
   `remember/mutableStateOf/onUpdate` boilerplate.

5. **Add simple `EditorSession` overload** -- Accept `initialDoc: String` and `onChange: (String) ->
   Unit` for the 80% use case.

6. **Add `@Immutable` annotation to `EditorState`** -- Enable Compose skip optimization.

### Priority 3: API Polish

7. **Add String overload for `ChangeSpec.Single.insert`** -- Accept `String` directly alongside
   `InsertContent`.

8. **Add String overload for `EditorStateConfig.doc`** -- Accept `String` directly alongside
   `DocSpec`.

9. **Rename `cssClass` to `tag` or `id`** in `GutterConfig` and remove CSS references.

10. **Consider an enum or sealed class for `Completion.type`** -- Improve discoverability of valid
    completion categories.

### Priority 4: Ecosystem

11. **Add `MaterialTheme` bridge** -- A function that converts `MaterialTheme.colorScheme` to
    `EditorTheme` for easy integration with Material 3 apps.

12. **Add `kodemirror-bom` Gradle BOM** -- A bill of materials to align all module versions
    automatically.

---

## Appendix: Language Module Coverage

22 tree-sitter-style `lang-*` modules:

| Module | Language |
|--------|----------|
| `lang-angular` | Angular templates |
| `lang-cpp` | C/C++ |
| `lang-css` | CSS |
| `lang-go` | Go |
| `lang-grammar` | Lezer grammar files |
| `lang-html` | HTML |
| `lang-java` | Java |
| `lang-javascript` | JavaScript/TypeScript |
| `lang-jinja` | Jinja2 templates |
| `lang-json` | JSON |
| `lang-less` | Less |
| `lang-liquid` | Liquid templates |
| `lang-markdown` | Markdown |
| `lang-php` | PHP |
| `lang-python` | Python |
| `lang-rust` | Rust |
| `lang-sass` | Sass |
| `lang-sql` | SQL |
| `lang-vue` | Vue SFC |
| `lang-wast` | WebAssembly Text |
| `lang-xml` | XML |
| `lang-yaml` | YAML |

Plus 100+ legacy modes in `:legacy-modes` covering Kodemirror's port of CodeMirror 5 modes.

Notable absence: **Kotlin** -- somewhat ironic for a Kotlin-first library. A `lang-kotlin` module
(even if based on the Java grammar with Kotlin extensions) would be a high-value addition.
