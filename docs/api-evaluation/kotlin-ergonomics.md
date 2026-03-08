# Kodemirror Kotlin Ergonomics Evaluation

## Summary

Kodemirror is a faithful Kotlin Multiplatform port of CodeMirror 6, and the translation is
structurally solid. The project makes good use of several Kotlin features -- sealed interfaces
for variant types, data classes for value objects, extension functions for top-level API
surface, and Compose integration for widgets. However, many APIs still closely mirror the
JavaScript original's patterns rather than leveraging idiomatic Kotlin. The biggest ergonomic
pain points are: verbose extension list construction, JS-style `eq()` methods instead of
`equals()`, untyped JSON serialization, bare function types where named interfaces would
clarify intent, and a complete absence of coroutine/Flow integration. The API is usable but
would benefit significantly from Kotlin-native DSLs, operator overloads, and type-safe
builders.

**Overall rating: B-** -- Structurally competent but with clear friction for Kotlin developers
accustomed to idiomatic APIs like those in Jetpack Compose, Ktor, or kotlinx.serialization.

---

## Strengths

### 1. Good use of sealed interfaces for variant types

The project uses sealed interfaces effectively for discriminated unions that would be
object-literal unions in JS:

```kotlin
// state/Change.kt
sealed interface ChangeSpec {
    data class Single(val from: Int, val to: Int? = null, val insert: InsertContent? = null) : ChangeSpec
    data class Multi(val specs: List<ChangeSpec>) : ChangeSpec
    class Set(val changeSet: ChangeSet) : ChangeSpec
}

sealed interface InsertContent {
    data class StringContent(val value: String) : InsertContent
    data class TextContent(val value: Text) : InsertContent
}

// state/Config.kt
sealed interface ChangeFilterResult {
    data object Accept : ChangeFilterResult
    data object Reject : ChangeFilterResult
    data class Ranges(val ranges: IntArray) : ChangeFilterResult
}
```

These provide exhaustive `when` matching and clear type discrimination. This is
notably better than the JS original's use of union types and boolean flags.

### 2. Data classes for configuration specs

Configuration objects are modeled as data classes with default parameters, which is
idiomatic Kotlin:

```kotlin
// commands/HistoryKt
data class HistoryConfig(val groupDelay: Long = 500, val minDepth: Int = 100)

// view/Keymap.kt
data class KeyBinding(
    val key: String? = null,
    val mac: String? = null,
    val run: ((EditorSession) -> Boolean)? = null,
    // ...
)

// view/Decoration.kt
data class MarkDecorationSpec(
    val inclusive: Boolean = false,
    val style: SpanStyle? = null,
    val paragraphStyle: ParagraphStyle? = null,
    // ...
)
```

This gives copy semantics, destructuring, and named parameters for free.

### 3. Extension functions as API surface

Top-level Kotlin extension functions replace JS module-level functions cleanly:

```kotlin
// state/State.kt
fun String.asDoc(): DocSpec = DocSpec.StringDoc(this)

// view/EditorTheme.kt
fun EditorTheme.selectionStyle(): SpanStyle = SpanStyle(background = selection)
fun EditorTheme.activeLineStyle(): SpanStyle = SpanStyle(background = activeLineBackground)
```

### 4. Compose-native widget system

The `WidgetType` base class replaces CodeMirror's DOM-based `toDOM()` with
`@Composable Content()`, which is a clean adaptation:

```kotlin
abstract class WidgetType {
    @Composable
    abstract fun Content()
    open fun eq(other: WidgetType): Boolean = this === other
    open val estimatedHeight: Int get() = -1
}
```

### 5. Facet and StateField system is well-ported

The facet/extension/state-field architecture is the core of CodeMirror's design and the
port retains its power while using Kotlin generics effectively:

```kotlin
class Facet<Input, Output> private constructor(...) : Extension {
    fun of(value: Input): Extension
    fun compute(deps: List<Slot>, get: (EditorState) -> Input): Extension
    fun <T> from(field: StateField<T>, get: ((T) -> Input)? = null): Extension
}
```

---

## JavaScript-isms

### JS-1: Custom `eq()` methods instead of `equals()` / `==` -- Severity: **High**

Multiple classes define `eq()` methods instead of overriding Kotlin's `equals()`. This
forces callers to remember to call `.eq()` instead of using `==`, breaking Kotlin
conventions and making the API surprising.

```kotlin
// state/Selection.kt
class SelectionRange {
    fun eq(other: SelectionRange, includeAssoc: Boolean = false): Boolean { ... }
}

class EditorSelection {
    fun eq(other: EditorSelection, includeAssoc: Boolean = false): Boolean { ... }
}

// state/Text.kt
abstract class Text {
    fun eq(other: Text): Boolean { ... }
}

// state/RangeSet.kt
abstract class RangeValue {
    open fun eq(other: RangeValue): Boolean = this === other
}

// view/Decoration.kt
abstract class WidgetType {
    open fun eq(other: WidgetType): Boolean = this === other
}
```

In JS, `===` is identity and `==` is coercion, so a custom `.eq()` method is the norm.
In Kotlin, `equals()` and `==` are the standard structural equality mechanism.

**Recommendation:** Override `equals()` (and `hashCode()`) on these classes. For the
`includeAssoc` parameter case on `SelectionRange`, provide `equals()` for the common case
and keep `eq(includeAssoc = true)` as a secondary method.

### JS-2: The `is` method on `StateEffect` -- Severity: **Medium**

```kotlin
// state/Transaction.kt
class StateEffect<Value> {
    @Suppress("ktlint:standard:function-naming")
    fun `is`(type: StateEffectType<T>): Boolean = this.type === type
}
```

`is` is a reserved keyword in Kotlin, requiring backtick-escaping. The `@Suppress`
annotation is already a code smell. The `asType()` method alongside it is the correct
Kotlin pattern, but `is` should not exist at all in the public API.

**Recommendation:** Remove the `is` method. Replace all call sites with `asType()` which
already provides the same functionality plus a safe cast. Alternatively, name it `isType()`.

### JS-3: CSS class strings as identifiers in Compose code -- Severity: **Medium**

The gutter system uses CSS class names (`"cm-lineNumbers"`, `"cm-activeLineGutter"`) as
string identifiers to discriminate gutter types, even though there is no CSS in a Compose
application:

```kotlin
// view/Gutter.kt
val lineNumbers: Extension = gutter(GutterConfig(
    cssClass = "cm-lineNumbers",
    lineMarker = { _, _ -> null }
))

// Used as string comparison in rendering:
if (config.cssClass == "cm-lineNumbers") { ... }
if (config.cssClass == "cm-activeLineGutter") { ... }
```

Similarly, decoration specs carry `cssClass` fields:

```kotlin
data class MarkDecorationSpec(
    val cssClass: String? = null,  // no CSS in Compose
    val attributes: Map<String, String>? = null  // no DOM attributes in Compose
)
```

**Recommendation:** Replace CSS class strings with a sealed class or enum for gutter
types. Remove `cssClass` and `attributes` from decoration specs that have no DOM, or
clearly document them as compatibility stubs.

### JS-4: Untyped JSON serialization with `Map<String, Any?>` -- Severity: **Medium**

```kotlin
// state/Selection.kt
fun toJSON(): Map<String, Int> = mapOf("anchor" to anchor, "head" to head)
fun fromJSON(json: Map<String, Any?>): SelectionRange { ... }

// state/State.kt
fun toJSON(fields: Map<String, StateField<*>>? = null): Map<String, Any?> { ... }
fun fromJSON(json: Map<String, Any?>, ...): EditorState { ... }
```

This is a direct port of JS's untyped object serialization. Kotlin has
`kotlinx.serialization` which provides type-safe, multiplatform serialization.

**Recommendation:** Implement `@Serializable` data classes or provide
`kotlinx.serialization` serializers alongside the raw map-based API. At minimum, consider
making `toJSON`/`fromJSON` return/accept typed intermediate representations.

### JS-5: `languageDataAt` returns untyped `List<T>` via unsafe cast -- Severity: **High**

```kotlin
// state/State.kt
@Suppress("UNCHECKED_CAST")
fun <T> languageDataAt(name: String, pos: Int, side: Int = -1): List<T> {
    val values = mutableListOf<T>()
    for (provider in facet(languageData)) {
        for (result in provider(this, pos, side)) {
            if (result.containsKey(name)) {
                values.add(result[name] as T)
            }
        }
    }
    return values
}
```

The language data system uses `Map<String, Any?>` with string keys and unchecked casts.
This is directly from JS where objects are untyped bags of properties.

**Recommendation:** Define a `LanguageDataKey<T>` type-safe key class (similar to how
`AnnotationType<T>` works) so callers get compile-time type safety.

---

## Missing Kotlin Idioms

### KI-1: No DSL builders for complex construction -- Severity: **High**

Creating an editor requires verbose nested construction:

```kotlin
// From docs-site/docs/examples/basic.md
val state = EditorState.create(EditorStateConfig(
    doc = "function hello() {}\n".asDoc(),
    extensions = ExtensionList(listOf(
        lineNumbers,
        highlightActiveLine,
        history(),
        bracketMatching(),
        defaultKeymapExtension(),
        javascript(),
        syntaxHighlighting(defaultHighlightStyle)
    ))
))
```

The `ExtensionList(listOf(...))` wrapper is pure boilerplate. Compare what a Kotlin DSL
builder could look like:

```kotlin
// Proposed DSL
val state = editorState {
    doc("function hello() {}\n")
    extensions {
        +lineNumbers
        +highlightActiveLine
        +history()
        +bracketMatching()
        +defaultKeymapExtension()
        +javascript()
        +syntaxHighlighting(defaultHighlightStyle)
    }
}
```

Similarly, `TransactionSpec` construction is verbose:

```kotlin
// Current
view.dispatch(TransactionSpec(
    changes = ChangeSpec.Single(
        from = 0,
        insert = InsertContent.StringContent("Hello ")
    )
))

// Proposed DSL
view.dispatch {
    insert(0, "Hello ")
}
```

### KI-2: No operator overloads where natural -- Severity: **Medium**

The `Extension` interface and `ExtensionList` would benefit from `operator fun plus`:

```kotlin
// Currently required:
ExtensionList(listOf(ext1, ext2, ext3))

// With operator overload:
ext1 + ext2 + ext3
```

`Text` would benefit from `operator fun get` (indexing):

```kotlin
// Currently:
doc.sliceString(from, to)
// Could also support:
doc[from..to]
```

`EditorState.field()` could be `operator fun get`:

```kotlin
// Currently:
state.field(myField)
// Could also support:
state[myField]
```

`Facet.of()` could be an `invoke` operator or infix function:

```kotlin
// Currently:
editable.of(false)
// Possible:
editable(false)  // invoke operator
```

### KI-3: `Prec` precedence wrappers are function values, not functions -- Severity: **Low**

```kotlin
// state/Extension.kt
object Prec {
    val highest: (Extension) -> Extension = { ext -> PrecExtension(ext, PrecValue.HIGHEST) }
    val high: (Extension) -> Extension = { ext -> PrecExtension(ext, PrecValue.HIGH) }
    // ...
}
```

These are stored as lambda properties rather than simple functions, which is a JS pattern
(exporting function values). In Kotlin, these should be regular functions.

**Recommendation:** Convert to `fun highest(ext: Extension): Extension = ...`

### KI-4: No `@DslMarker` annotation for builder scopes -- Severity: **Low**

If DSL builders are added (see KI-1), they should use `@DslMarker` to prevent accidental
scope leaking, following Kotlin DSL best practices.

### KI-5: No inline value classes for type-safe integer positions -- Severity: **Medium**

Document positions, line numbers, and column numbers are all bare `Int` values throughout
the API:

```kotlin
fun lineAt(pos: Int): Line
fun line(n: Int): Line
fun mapPos(pos: Int, assoc: Int = -1): Int
```

A `@JvmInline value class DocPosition(val value: Int)` and
`@JvmInline value class LineNumber(val value: Int)` would prevent mixing up positions
and line numbers at compile time, with zero runtime cost.

### KI-6: No property delegates for StateField access -- Severity: **Low**

```kotlin
// Currently:
val count = state.field(counterField)

// With a delegate:
val EditorState.counter by counterField  // hypothetical
```

A `ReadOnlyProperty` delegate for `StateField` would be natural for frequently-accessed
fields.

### KI-7: `StateFieldSpec` uses function types where a builder or interface is clearer -- Severity: **Medium**

```kotlin
data class StateFieldSpec<Value>(
    val create: (EditorState) -> Value,
    val update: (Value, Transaction) -> Value,
    val compare: ((Value, Value) -> Boolean)? = null,
    val provide: ((StateField<Value>) -> Extension)? = null,
    val toJSON: ((Value, EditorState) -> Any?)? = null,
    val fromJSON: ((Any?, EditorState) -> Value)? = null
)
```

Six function-type parameters in a data class is hard to read at call sites. A builder
DSL or named interface methods would be clearer:

```kotlin
// Proposed
val myField = StateField.define<Int> {
    create { state -> 0 }
    update { value, tr -> value + 1 }
    provide { field -> decorations.from(field) }
}
```

---

## DSL Opportunities

### DSL-1: Editor state builder -- Severity: **High**

As shown in KI-1 above. The `EditorStateConfig` data class + `ExtensionList(listOf(...))`
pattern is the most common API surface and would benefit the most from a DSL.

### DSL-2: Transaction dispatch builder -- Severity: **High**

Transaction dispatch is the second most common operation:

```kotlin
// Current (from docs-site/docs/examples/change.md):
view.dispatch(TransactionSpec(
    changes = ChangeSpec.Multi(listOf(
        ChangeSpec.Single(from = 0, insert = InsertContent.StringContent("// header\n")),
        ChangeSpec.Single(from = 20, to = 25, insert = InsertContent.StringContent("new"))
    ))
))

// Proposed DSL:
view.dispatch {
    insert(0, "// header\n")
    replace(20, 25, "new")
}
```

### DSL-3: KeyBinding builder -- Severity: **Medium**

```kotlin
// Current:
keymapOf(
    KeyBinding(key = "Ctrl-s", run = { view -> save(view); true }),
    KeyBinding(key = "Ctrl-z", run = { view -> undo(view); true })
)

// Proposed DSL:
keymap {
    "Ctrl-s" { save(it); true }
    "Ctrl-z" { undo(it); true }
}
```

### DSL-4: HighlightStyle definition builder -- Severity: **Medium**

```kotlin
// Current (from language API):
HighlightStyle.define(listOf(
    TagStyleSpec(tags.keyword, SpanStyle(color = Color(0xFFC678DD))),
    TagStyleSpec(tags.string, SpanStyle(color = Color(0xFF98C379)))
))

// Proposed DSL:
highlightStyle {
    tags.keyword { color = Color(0xFFC678DD) }
    tags.string { color = Color(0xFF98C379) }
}
```

### DSL-5: Decoration building -- Severity: **Low**

```kotlin
// Current:
val deco = Decoration.mark(MarkDecorationSpec(
    style = SpanStyle(background = Color(0x40FFFF00), fontWeight = FontWeight.Bold)
))

// Proposed:
val deco = Decoration.mark {
    style { background = Color(0x40FFFF00); fontWeight = FontWeight.Bold }
}
```

---

## Type Safety Issues

### TS-1: `Completion.type` is `String?` instead of an enum or sealed class -- Severity: **Medium**

```kotlin
// autocomplete
data class Completion(
    val label: String,
    val type: String? = null,  // "keyword", "function", "variable", etc.
    // ...
)
```

This follows the JS convention of arbitrary strings. A sealed class or enum would
provide exhaustive matching and prevent typos:

```kotlin
enum class CompletionType { KEYWORD, FUNCTION, VARIABLE, CLASS, METHOD, PROPERTY, ... }
```

### TS-2: `SearchQuery.valid` is a computed property but not enforced -- Severity: **Low**

The `SearchQuery` class has a `valid` property but invalid queries can still be
constructed and used. Consider a validated factory method or builder.

### TS-3: `Diagnostic.markClass` is a CSS class string -- Severity: **Low**

```kotlin
data class Diagnostic(
    // ...
    val markClass: String? = null  // CSS class - meaningless in Compose
)
```

This should be a `SpanStyle` or removed from the Compose API.

### TS-4: `GutterConfig` uses generic function types -- Severity: **Low**

```kotlin
data class GutterConfig(
    val lineMarker: ((EditorSession, Int) -> GutterMarker?)? = null,
    val lineMarkerChange: ((ViewUpdate) -> Boolean)? = null,
    // ...
)
```

Named functional interfaces (`fun interface`) would improve readability at call sites
and allow SAM conversion.

---

## Extension Function Opportunities

### EF-1: `String` to `InsertContent` conversion -- Severity: **High**

The most common insert operation wraps a string:

```kotlin
// Current:
InsertContent.StringContent("Hello")

// Should be:
"Hello".asInsert()
// or implicit conversion via:
fun ChangeSpec.Companion.insert(from: Int, text: String) = ...
```

### EF-2: `Extension` list combination -- Severity: **High**

```kotlin
// Current:
ExtensionList(listOf(ext1, ext2, ext3))

// Should be:
listOf(ext1, ext2, ext3).asExtension()
// or:
fun extensionOf(vararg extensions: Extension): Extension = ExtensionList(extensions.toList())
```

### EF-3: `EditorState` convenience extensions -- Severity: **Medium**

```kotlin
// Proposed extensions:
val EditorState.currentLine: Line get() = doc.lineAt(selection.main.head)
val EditorState.selectedText: String get() = sliceDoc(selection.main.from, selection.main.to)
val EditorState.cursorPosition: Int get() = selection.main.head
val EditorState.isEmpty: Boolean get() = doc.length == 0
```

### EF-4: `SelectionSpec` convenience constructors -- Severity: **Medium**

The current API requires wrapping in verbose spec types:

```kotlin
// Current (from docs-site/docs/examples/selection.md):
selection = SelectionSpec.EditorSelectionSpec(
    EditorSelection.single(anchor = 5, head = 15)
)

// Should have:
fun EditorSelection.asSpec(): SelectionSpec = SelectionSpec.EditorSelectionSpec(this)
fun Int.asCursor(): SelectionSpec = SelectionSpec.CursorSpec(anchor = this)
```

### EF-5: `Text` convenience extensions -- Severity: **Low**

```kotlin
val Text.isEmpty: Boolean get() = length == 0
val Text.isNotEmpty: Boolean get() = length > 0
fun Text.lineSequence(): Sequence<Line> = sequence {
    for (i in 1..lines) yield(line(i))
}
```

---

## Coroutine and Flow Integration

### CF-1: No coroutine integration at all -- Severity: **High**

The entire codebase has zero `suspend fun` declarations and zero `Flow`/`StateFlow`
usage in the core state and view modules. For a Kotlin Multiplatform library targeting
Compose (which is deeply coroutine-integrated), this is a significant gap.

Opportunities:
- `EditorState` could expose a `StateFlow<EditorState>` for reactive observation
- The linter source function (`(EditorSession) -> List<Diagnostic>`) could be
  `suspend (EditorSession) -> List<Diagnostic>` for async linting
- Completion sources could be `suspend` functions for network-backed completions
- The `ViewUpdate` listener facet could be a `Flow<ViewUpdate>`

### CF-2: No `rememberEditorState` Compose integration -- Severity: **Medium**

The docs show manual state management with `mutableStateOf`:

```kotlin
var state by remember { mutableStateOf(EditorState.create(...)) }
EditorSession(
    state = state,
    onUpdate = { tr -> state = tr.state },
    modifier = Modifier.fillMaxSize()
)
```

A `rememberEditorState` composable function would encapsulate this pattern:

```kotlin
// Proposed:
@Composable
fun rememberEditorState(
    initialDoc: String = "",
    vararg extensions: Extension
): MutableState<EditorState> = remember {
    mutableStateOf(EditorState.create(EditorStateConfig(
        doc = initialDoc.asDoc(),
        extensions = ExtensionList(extensions.toList())
    )))
}
```

---

## Recommendations (Prioritized)

### Critical Priority

None of the findings are truly critical -- the API is functional and usable.

### High Priority

| # | Finding | Impact | Effort |
|---|---------|--------|--------|
| 1 | **DSL-1/DSL-2**: Add builder DSLs for `EditorState` and `TransactionSpec` | Drastically reduces boilerplate in the most common usage patterns | Medium |
| 2 | **EF-1/EF-2**: Add `String.asInsert()` and `List<Extension>.asExtension()` | Eliminates the two most common wrapper types users must write | Low |
| 3 | **JS-1**: Replace `eq()` methods with `equals()`/`hashCode()` | Aligns with Kotlin equality conventions; prevents subtle bugs | Medium |
| 4 | **JS-5/KI-5**: Type-safe language data keys and document position types | Prevents runtime class cast errors and position/line-number confusion | Medium |
| 5 | **CF-1**: Add `suspend` overloads for linter and completion sources | Enables async linting and network-backed completions | Medium |

### Medium Priority

| # | Finding | Impact | Effort |
|---|---------|--------|--------|
| 6 | **JS-2**: Remove backtick `is()` method from `StateEffect` | Eliminates confusing API surface | Low |
| 7 | **JS-3**: Replace CSS class strings with sealed class identifiers | Removes dead DOM concepts from Compose API | Low |
| 8 | **JS-4**: Add kotlinx.serialization support alongside `toJSON`/`fromJSON` | Type-safe serialization | Medium |
| 9 | **KI-2**: Add `operator fun plus` for Extension combination | Natural Kotlin syntax for composing extensions | Low |
| 10 | **KI-7/DSL-3**: Builder DSLs for `StateFieldSpec` and keymaps | Reduces function-type parameter soup | Medium |
| 11 | **TS-1**: Replace `Completion.type: String?` with enum | Compile-time validation of completion categories | Low |
| 12 | **EF-3/EF-4**: `EditorState` and `SelectionSpec` convenience extensions | Common operations become one-liners | Low |
| 13 | **CF-2**: Add `rememberEditorState` Compose helper | Eliminates boilerplate in every Compose usage | Low |

### Low Priority

| # | Finding | Impact | Effort |
|---|---------|--------|--------|
| 14 | **KI-3**: Convert `Prec` lambda properties to functions | Minor readability improvement | Low |
| 15 | **KI-6**: Property delegates for StateField | Convenience for power users | Low |
| 16 | **TS-3**: Remove `Diagnostic.markClass` CSS string | Cleanup dead field | Low |
| 17 | **EF-5**: `Text` convenience extensions | Minor usability improvement | Low |
| 18 | **DSL-4/DSL-5**: Highlight style and decoration DSLs | Nice-to-have for advanced usage | Medium |

---

## Appendix: File References

Key source files examined in this evaluation:

- `/home/jmonk/git/kodemirror/state/api/state.api` -- State module public API
- `/home/jmonk/git/kodemirror/view/api/view.api` -- View module public API
- `/home/jmonk/git/kodemirror/language/api/language.api` -- Language module public API
- `/home/jmonk/git/kodemirror/commands/api/commands.api` -- Commands module public API
- `/home/jmonk/git/kodemirror/autocomplete/api/autocomplete.api` -- Autocomplete module public API
- `/home/jmonk/git/kodemirror/lint/api/lint.api` -- Lint module public API
- `/home/jmonk/git/kodemirror/search/api/search.api` -- Search module public API
- `/home/jmonk/git/kodemirror/state/src/commonMain/kotlin/com/monkopedia/kodemirror/state/State.kt`
- `/home/jmonk/git/kodemirror/state/src/commonMain/kotlin/com/monkopedia/kodemirror/state/Transaction.kt`
- `/home/jmonk/git/kodemirror/state/src/commonMain/kotlin/com/monkopedia/kodemirror/state/Extension.kt`
- `/home/jmonk/git/kodemirror/state/src/commonMain/kotlin/com/monkopedia/kodemirror/state/Facet.kt`
- `/home/jmonk/git/kodemirror/state/src/commonMain/kotlin/com/monkopedia/kodemirror/state/Change.kt`
- `/home/jmonk/git/kodemirror/state/src/commonMain/kotlin/com/monkopedia/kodemirror/state/Selection.kt`
- `/home/jmonk/git/kodemirror/state/src/commonMain/kotlin/com/monkopedia/kodemirror/state/Text.kt`
- `/home/jmonk/git/kodemirror/state/src/commonMain/kotlin/com/monkopedia/kodemirror/state/RangeSet.kt`
- `/home/jmonk/git/kodemirror/view/src/commonMain/kotlin/com/monkopedia/kodemirror/view/EditorSession.kt`
- `/home/jmonk/git/kodemirror/view/src/commonMain/kotlin/com/monkopedia/kodemirror/view/KodeMirror.kt`
- `/home/jmonk/git/kodemirror/view/src/commonMain/kotlin/com/monkopedia/kodemirror/view/Decoration.kt`
- `/home/jmonk/git/kodemirror/view/src/commonMain/kotlin/com/monkopedia/kodemirror/view/ViewPlugin.kt`
- `/home/jmonk/git/kodemirror/view/src/commonMain/kotlin/com/monkopedia/kodemirror/view/ViewUpdate.kt`
- `/home/jmonk/git/kodemirror/view/src/commonMain/kotlin/com/monkopedia/kodemirror/view/Keymap.kt`
- `/home/jmonk/git/kodemirror/view/src/commonMain/kotlin/com/monkopedia/kodemirror/view/Gutter.kt`
- `/home/jmonk/git/kodemirror/view/src/commonMain/kotlin/com/monkopedia/kodemirror/view/EditorTheme.kt`
- `/home/jmonk/git/kodemirror/view/src/commonMain/kotlin/com/monkopedia/kodemirror/view/ViewExtensions.kt`
- `/home/jmonk/git/kodemirror/docs-site/docs/examples/basic.md`
- `/home/jmonk/git/kodemirror/docs-site/docs/examples/config.md`
- `/home/jmonk/git/kodemirror/docs-site/docs/examples/change.md`
- `/home/jmonk/git/kodemirror/docs-site/docs/examples/decoration.md`
- `/home/jmonk/git/kodemirror/docs-site/docs/examples/selection.md`
- `/home/jmonk/git/kodemirror/docs-site/docs/examples/zebra.md`
- `/home/jmonk/git/kodemirror/docs-site/docs/examples/autocompletion.md`
- `/home/jmonk/git/kodemirror/docs-site/docs/examples/lint.md`
