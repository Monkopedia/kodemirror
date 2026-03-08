# Kodemirror API Architecture Evaluation

## Summary

Kodemirror is a Kotlin Multiplatform port of CodeMirror 6 targeting Compose Multiplatform. The project
comprises approximately 35 modules spanning core infrastructure (state, view, language, lezer-common,
lezer-highlight, lezer-lr), feature modules (autocomplete, commands, search, lint, collab, merge), a
theme module (theme-one-dark), and 20+ language modules. The overall architecture faithfully mirrors
CM6's design: Facets, StateFields, Extensions, StateEffects, ViewPlugins, Decorations, and the
incremental Lezer parser system are all present and structurally correct. The language modules follow a
consistent shape, and configuration uniformly uses data classes with default parameters -- a natural
Kotlin idiom that serves the same role as CM6's options objects.

The main areas of concern are: (1) type erasure in generic APIs forcing `Any`/`Object` at the JVM
boundary, (2) massive mutable state exposure in the lezer-lr and legacy-modes modules, (3) a few naming
inconsistencies across language modules, and (4) Compose-internal implementation details leaking into the
public API surface.

---

## Naming Consistency

### Highlight Property Naming

Most language modules export their highlighting `NodePropSource` with the pattern `get{Lang}Highlighting`:

| Module        | Property Name         | Pattern Match |
|---------------|-----------------------|---------------|
| lang-java     | `javaHighlighting`    | Yes           |
| lang-python   | `pythonHighlighting`  | Yes           |
| lang-cpp      | `cppHighlighting`     | Yes           |
| lang-go       | `goHighlighting`      | Yes           |
| lang-rust     | `rustHighlighting`    | Yes           |
| lang-html     | `htmlHighlighting`    | Yes           |
| lang-css      | `cssHighlighting`     | Yes           |
| lang-json     | `jsonHighlighting`    | Yes           |
| lang-xml      | `xmlHighlighting`     | Yes           |
| lang-yaml     | `yamlHighlighting`    | Yes           |
| lang-sql      | `sqlHighlighting`     | Yes           |
| lang-wast     | `wastHighlighting`    | Yes           |
| lang-less     | `lessHighlighting`    | Yes           |
| lang-sass     | `sassHighlighting`    | Yes           |
| lang-markdown | `markdownHighlighting`| Yes           |
| lang-grammar  | `lezerHighlighting`   | Yes           |
| **lang-javascript** | **`jsHighlight`** | **NO** -- uses abbreviated name and drops `-ing` suffix |
| lang-vue      | `vueHighlighting`     | Yes           |

**Severity: Low.** `jsHighlight` vs `{lang}Highlighting` is a minor inconsistency. It uses a shortened
name (`js` not `javascript`) and drops the `-ing` suffix.

### Missing Highlighting Exports

Two language modules export no highlighting `NodePropSource` at all:

- **lang-php**: Only exports `phpLanguage` and `parser`. No `phpHighlighting`.
- **lang-angular**: Only exports `angularLanguage` and `angular()`. No `angularHighlighting` and no
  `parser` export.

**Severity: Low.** These may have highlighting baked into the parser configuration rather than exposed
as a separate property, but it breaks the pattern that other modules follow.

### Language Property Naming

All language modules consistently use `get{Lang}Language` returning `LRLanguage`, except:

- **lang-markdown**: Returns `Language` (base class) instead of `LRLanguage` for both
  `markdownLanguage` and `commonmarkLanguage`. This is architecturally correct since markdown uses a
  custom parser rather than `LRParser`, but it is a type-level divergence users may stumble on.

**Severity: Low** (correct behavior, but API consumers need to be aware).

### `tags` Object Naming Convention

The `tags` object in `lezer-highlight` uses a lowercase class name:

```
public final class com/monkopedia/kodemirror/lezer/highlight/tags
```

This violates Kotlin's PascalCase convention for classes/objects. It faithfully mirrors CM6's JavaScript
`tags` export, but in Kotlin this looks like a misplaced variable rather than an object.

**Severity: Medium.** IDE warnings, surprises for Kotlin developers expecting `Tags`.

### Top-Level `getParser` Name Collision

Every LR-based language module exports a top-level property:

```kotlin
val parser: LRParser  // in e.g. JsParserKt, JavaParserKt, CppParserKt, etc.
```

While these live in different packages (e.g., `com.monkopedia.kodemirror.lang.java`,
`com.monkopedia.kodemirror.lang.cpp`), the identical name `parser` across ~15 modules creates confusion
when multiple language modules are imported. A star-import of two parser packages would create an
ambiguity. The naming should be prefixed: `javaParser`, `cppParser`, etc.

**Severity: Medium.** Collision risk with star imports; confusing when multiple languages are in scope.

### `getTagLanguage` Name Collision

Both `lang-jinja` and `lang-liquid` export a top-level property named `tagLanguage`:

```
lang-jinja:  fun getTagLanguage(): LRLanguage
lang-liquid: fun getTagLanguage(): LRLanguage
```

These are in different packages but the identical name is confusing if both are imported.

**Severity: Low.** Different packages mitigate the issue, but the generic name is not descriptive.

---

## Pattern Adherence

### Language Module Structure

The standard language module follows this three-file pattern:

1. **`{Lang}ParserKt`**: Exports `val parser: LRParser`
2. **`{Lang}HighlightKt`**: Exports `val {lang}Highlighting: NodePropSource`
3. **`{Lang}Kt`**: Exports `val {lang}Language: LRLanguage` and `fun {lang}(): LanguageSupport`

Modules following this pattern exactly: java, python, cpp, go, rust, html, css, json, xml, yaml, wast,
less, sass.

Variations:
- **lang-javascript**: Has `jsHighlight` instead of `javascriptHighlighting`. Exports 4 language
  variants (javascript, typescript, jsx, tsx) plus a `javascript(jsx, typescript)` factory function with
  Boolean parameters.
- **lang-sql**: Significantly more complex -- exports multiple SQL dialects (StandardSQL, MySQL,
  PostgreSQL, etc.), a `SQLDialect` class, and completion configuration. The entry function is
  `sql(config)`.
- **lang-markdown**: Has its own parser type (`MarkdownParser` not `LRParser`), exports a large
  lezer-markdown sub-API within the same module.
- **lang-php**: Missing highlighting export.
- **lang-angular**: Missing both highlighting and parser exports.
- **lang-vue**: Highlighting is in `VueParserKt` instead of a separate `VueHighlightKt`.
- **lang-liquid** / **lang-jinja**: Template languages with completion configs and `make{Lang}`
  factories.
- **lang-grammar**: Uses `lezerGrammarLanguage` / `lezerGrammar()` / `lezerHighlighting` -- consistent
  but the `lezer` prefix for what's a "grammar" module is slightly confusing.

**Severity: Low overall.** The variations are mostly justified by the underlying complexity of each
language. The few inconsistencies (vue highlighting location, php missing highlighting) could be cleaned
up.

### Configuration Pattern

Feature modules consistently use data classes with default parameters for configuration:

| Module       | Config Type                 | Entry Function                  |
|--------------|-----------------------------|---------------------------------|
| commands     | `HistoryConfig`             | `history(config)`               |
| autocomplete | `CompletionConfig`          | `autocompletion(config)`        |
| autocomplete | `CloseBracketsConfig`       | `closeBrackets(config)`         |
| search       | `HighlightSelectionMatchConfig` | `highlightSelectionMatches(config)` |
| lint         | `LintConfig`                | `linter(source, config)`        |
| lint         | `LintGutterConfig`          | `lintGutter(config)`            |
| collab       | `CollabConfig`              | `collab(config)`                |
| merge        | `MergeViewConfig`           | `MergeView(config)` (constructor) |
| merge        | `UnifiedMergeConfig`        | `unifiedMergeView(config)`      |
| language     | `BracketMatchingConfig`     | `bracketMatching(config)`       |

All configuration types have no-arg constructors with sensible defaults. This is idiomatic Kotlin.

**Severity: None.** Consistent and well-designed.

### Extension Composition Model

All feature entry points return `Extension`, maintaining the CM6 composition model:

```kotlin
fun history(config: HistoryConfig = HistoryConfig()): Extension
fun autocompletion(config: CompletionConfig = CompletionConfig()): Extension
fun search(): Extension
fun linter(source: ..., config: LintConfig = LintConfig()): Extension
fun bracketMatching(config: BracketMatchingConfig = BracketMatchingConfig()): Extension
fun syntaxHighlighting(highlighter: Highlighter): Extension
fun closeBrackets(config: CloseBracketsConfig = CloseBracketsConfig()): Extension
```

Language support returns `LanguageSupport` (which has a `.extension` property):

```kotlin
fun java(): LanguageSupport
fun python(): LanguageSupport
fun javascript(jsx: Boolean = false, typescript: Boolean = false): LanguageSupport
```

**Severity: None.** Faithfully models CM6's composition architecture.

---

## Language Module Uniformity

Summary table of what each language module exports:

| Module         | `{lang}Language` | `{lang}()` | `{lang}Highlighting` | `parser` |
|----------------|:---:|:---:|:---:|:---:|
| lang-java      | Y | Y | Y | Y |
| lang-python    | Y | Y | Y | Y |
| lang-cpp       | Y | Y | Y | Y |
| lang-go        | Y | Y | Y | Y |
| lang-rust      | Y | Y | Y | Y |
| lang-html      | Y | Y | Y | Y |
| lang-css       | Y | Y | Y | Y |
| lang-json      | Y | Y | Y | Y |
| lang-xml       | Y | Y | Y | Y |
| lang-yaml      | Y | Y | Y | Y |
| lang-wast      | Y | Y | Y | Y |
| lang-less      | Y | Y | Y | Y |
| lang-sass      | Y | Y | Y | Y |
| lang-javascript| Y | Y | `jsHighlight` (naming) | Y |
| lang-sql       | Y | Y | Y | Y (+ dialect system) |
| lang-markdown  | Y | Y | Y | Y (MarkdownParser) |
| lang-php       | Y | Y | **NO** | Y |
| lang-angular   | Y | Y | **NO** | **NO** |
| lang-vue       | Y | Y | Y (wrong file) | **NO** |
| lang-grammar   | Y | Y | Y | Y |
| lang-liquid    | Y | Y | **NO** | **NO** |
| lang-jinja     | Y | Y | **NO** | **NO** |

The template/wrapping languages (angular, vue, liquid, jinja) diverge because they wrap other languages
rather than defining standalone parsers. This is architecturally justified but should be documented.

**Severity: Low.**

---

## Facet/Extension Model

### Architecture Fidelity

The Facet/Extension model faithfully reproduces CM6's architecture:

- **`Extension`**: An interface implemented by `Facet`, `StateField`, `Compartment`, `ExtensionList`,
  `ExtensionHolder`, and `ViewPlugin` (via `.asExtension()`).
- **`Facet`**: Has `define()`, `of()`, `compute()`, `computeN()`, `from()` -- matching CM6's API.
- **`StateField`**: Has `define(StateFieldSpec)` and `.init()`.
- **`StateEffect`** / **`StateEffectType`**: Has `define()` and `.of()`.
- **`Compartment`**: Supports `of()` and `reconfigure()`.
- **`ViewPlugin`**: Has `define()` and `fromClass()`.

The `StateFieldSpec` uses named function parameters (`create`, `update`, `compare`, `provide`,
`toJSON`, `fromJSON`) which is more readable than CM6's object literal approach.

### Type Erasure at Boundaries

Due to JVM type erasure, `Facet`, `StateField`, `StateEffect`, and `StateEffectType` lose their generic
type parameters at the public API boundary:

```
Facet.of(Object): Extension       // should be Facet<Input>.of(Input): Extension
StateEffect.getValue(): Object    // should be StateEffect<T>.getValue(): T
StateEffectType.of(Object): StateEffect  // should be StateEffectType<T>.of(T): StateEffect<T>
EditorState.facet(Facet): Object  // should be EditorState.facet(Facet<Output>): Output
EditorState.field(StateField): Object  // should be EditorState.field(StateField<T>): T
```

The `.api` dump shows these as `Object` because the Binary Compatibility Validator strips generics. In
Kotlin source code, these are properly typed with generics. However, Java callers will see raw types.

**Severity: Medium for Java interop.** Kotlin callers get full type safety via source-level generics.
Java callers must cast manually.

---

## API Safety

### Mutable State Exposure in lezer-lr

The `lezer-lr` module exposes extensive mutable state through its public API. The following classes have
public setters:

**`CachedToken`** -- 7 public setters:
```
setContext(Int), setEnd(Int), setExtended(Int), setLookAhead(Int),
setMask(Int), setStart(Int), setValue(Int)
```

**`InputStream`** -- 6 public setters:
```
setChunk(String), setChunkOff(Int), setChunkPos(Int),
setEnd(Int), setNext(Int), setPos(Int), setToken(CachedToken)
```

**`Parse`** -- 7 public setters:
```
setBigReductionCount(Int), setLastBigReductionSize(Int), setLastBigReductionStart(Int),
setMinStackPos(Int), setNextStackID(Int), setRecovering(Int), setStacks(List)
```

**`Stack`** -- 10 public setters:
```
setBuffer(List), setBufferBase(Int), setCurContext(StackContext), setLookAhead(Int),
setParent(Stack), setPos(Int), setReducePos(Int), setScore(Int), setState(Int)
```

**`SimulatedStack`** -- 3 public setters, **`TokenCache`** -- 3 public setters,
**`StackBufferCursor`** -- 4 public setters.

This is a direct consequence of porting CM6's mutable internal parser state. In JavaScript, these are
module-private implementation details; in Kotlin, they are fully public.

**Severity: High.** External code can corrupt parser state by calling any setter. These should be
`internal` visibility.

### Mutable State Exposure in legacy-modes

The legacy-modes module exposes every state class with public mutable setters (hundreds of them across
~100 mode state classes). Examples from just the first few:

```
AplState: setEscape, setFunc, setOp, setPrev, setString
AsciiArmorState: setState, setType
Asn1State: setContext, setCurPunc, setIndented, setStartOfLine, setTokenize
```

This is because `StreamParser.copyState()` and `StreamParser.token()` need to mutate these states. The
states themselves are implementation details of each legacy mode.

**Severity: Medium.** These state types should ideally not be public, or their mutability should be
restricted to `internal`. However, the `StreamParser` interface's `copyState` and `token` methods use
generic `Object` types, forcing the state classes to be public.

### `StringStream` Mutable Position

```kotlin
class StringStream {
    var pos: Int    // public getter + setter
    var start: Int  // public getter + setter
}
```

`StringStream` is used by legacy mode `token()` functions. The mutable `pos` and `start` are part of
the contract (modes advance `pos` during tokenization). This is acceptable as design intent, but it
means callers can corrupt the stream position.

**Severity: Low.** Mutability is by design for the streaming tokenization API.

### `Rule.setNext()` in lezer-highlight

```kotlin
class Rule {
    var next: Rule?  // public getter + setter
}
```

A linked-list node with a public mutable `next` pointer. External code can break the highlight rule
chain.

**Severity: Medium.** Should be `internal` or private setter.

### `LeafBlock` Mutable Content

```kotlin
class LeafBlock {
    var content: String   // public
    var parsers: List<*>  // public
}
```

Markdown parser internal state exposed mutably.

**Severity: Medium.** Should be restricted visibility.

---

## JavaScript Leakage

### `toJSON()` / `fromJSON()` Naming

Several classes retain JavaScript-era serialization naming:

```kotlin
ChangeDesc.toJSON(): List<*>
ChangeDesc.Companion.fromJSON(List<*>): ChangeDesc
ChangeSet.toChangeSetJSON(): List<*>
ChangeSet.Companion.changeSetFromJSON(List<*>): ChangeSet
Text.toJSON(): List<String>
StateFieldSpec.toJSON, .fromJSON  // function parameters
```

In Kotlin/JVM, the convention would be `serialize()`/`deserialize()` or integration with
kotlinx.serialization.

**Severity: Low.** These work fine but look foreign to Kotlin developers.

### Action Constants as Object Singletons

The lezer-lr module exposes JS-style constant objects:

```kotlin
object Action { const val GOTO_FLAG, REDUCE_FLAG, REPEAT_FLAG, STAY_FLAG, VALUE_MASK, ... }
object Encode { const val BASE, BIG_VAL, GAP1, GAP2, START, ... }
object Rec { const val CUT_DEPTH, DISTANCE, FORCE_REDUCE_LIMIT, ... }
object Recover { const val DELETE, INSERT, REDUCE, ... }
object Seq { const val DONE, END, NEXT, OTHER }
object ParseState { const val ACTIONS, DEFAULT_REDUCE, FLAGS, SIZE, SKIP, ... }
object StateFlag { const val ACCEPTING, SKIPPED }
object Term { const val ERR }
object File { const val VERSION }
object Lookahead { const val MARGIN }
object Specialize { const val EXTEND, SPECIALIZE }
object IterMode { const val ENTER_BRACKETED, EXCLUDE_BUFFERS, ... }
```

These are parser-internal constants that would be `internal` in idiomatic Kotlin. Exposing them as
public singleton objects with `Int` constants rather than enums is a JS pattern leak.

**Severity: Medium.** These are implementation details that should not be in the public API.

### `IterMode` as Bit Flags Rather Than Enum

```kotlin
object IterMode {
    const val INCLUDE_ANONYMOUS = ...
    const val EXCLUDE_BUFFERS = ...
    const val IGNORE_MOUNTS = ...
    const val IGNORE_OVERLAYS = ...
    const val ENTER_BRACKETED = ...
}
```

These are used as integer bit flags in method parameters (e.g., `Tree.cursor(mode: Int)`). In Kotlin,
this would be better modeled as an `EnumSet` or sealed class.

**Severity: Low.** Functional but un-Kotlinic.

### `phrase(String, vararg Any)` on EditorSession

```kotlin
class EditorSession {
    fun phrase(phrase: String, vararg insert: Any): String
}
```

The `vararg Any` parameter accepts anything without type checking. This mirrors CM6's dynamic
JavaScript `phrase` function.

**Severity: Low.** Standard for i18n templating.

---

## Type Safety

### `Any`/`Object` in Core APIs

The following public API methods use erased `Object` (appearing as `Any` in Kotlin):

| Location | Method | Type Issue |
|----------|--------|------------|
| `Annotation` | `getValue(): Object` | Type-erased `T` |
| `AnnotationType` | `of(Object): Annotation` | Type-erased `T` |
| `Facet` | `of(Object): Extension` | Type-erased `Input` |
| `EditorState` | `facet(Facet): Object` | Type-erased `Output` |
| `EditorState` | `field(StateField): Object` | Type-erased `T` |
| `StateEffect` | `getValue(): Object` | Type-erased `T` |
| `StateEffectType` | `of(Object): StateEffect` | Type-erased `T` |
| `FacetReader` | `getDefault(): Object` | Type-erased `T` |
| `Configuration` | `staticFacet(Facet): Object` | Type-erased `Output` |
| `TagStyleSpec` | `getTag(): Object` | Should be `Tag` or `(Tag) -> Tag` |
| `TagStyleRule` | `getTag(): Object` | Should be `Tag` or `(Tag) -> Tag` |
| `NodeProp` | `prop(NodeProp): Object` | Type-erased `T` |
| `TreeBuildSpec` | `getBuffer(): Object` | Should be `List<Int>` or `IntArray` |
| `NestedParse` | `getOverlay(): Object` | Should be typed overlay representation |
| `NodeSpec` | `getStyle(): Object` | Should be `Tag` or tag-related type |

In Kotlin source, many of these are generic (`Facet<Input, Output>`, `StateField<T>`, etc.). The
`Object` appearance in `.api` files is due to JVM type erasure in the Binary Compatibility Validator
output. For Kotlin callers, these are type-safe at the source level.

The truly problematic cases are `TagStyleSpec.tag`, `TagStyleRule.tag`, `TreeBuildSpec.buffer`,
`NestedParse.overlay`, and `NodeSpec.style` -- these appear to genuinely use `Any` rather than a
specific type, carrying over JS's dynamic typing.

**Severity: Medium.** The generic erasure is unavoidable on JVM but the truly untyped fields (tag,
buffer, overlay, style) should have proper types.

### `ComposableSingletons` Leakage

Two modules expose Compose compiler-generated `ComposableSingletons` classes in their public API:

```
search/api/search.api: ComposableSingletons$SearchKt
    getLambda$2132847180$search(): Function2
    getLambda$692970211$search(): Function2

lint/api/lint.api: ComposableSingletons$LintKt
    getLambda$-372749016$lint(): Function2
```

These are Compose compiler implementation details with generated/unstable names (hash-based). They
should never appear in a public API.

**Severity: High.** These are implementation details with unstable names that will change with code
modifications. They should be excluded from the public API via `@PublishedApi`, `internal` visibility,
or API dump filtering.

### `$stable` Field Exposure

82 classes across 8 modules expose the `$stable` field in their public API:

```
public static final field $stable I   // on many data classes
```

This is a Compose compiler stability annotation. While it does not affect functionality, it pollutes
the public API surface and is an implementation detail.

**Severity: Low.** Cosmetic but should be filtered from API dumps if possible.

---

## CodeMirror Fidelity Assessment

### Core Concepts Present

| CM6 Concept | Kodemirror Equivalent | Status |
|-------------|----------------------|--------|
| Extension | `Extension` interface | Present |
| Facet | `Facet` class | Present, with `define`, `of`, `compute`, `computeN`, `from` |
| StateField | `StateField` class | Present, with `define`, `init` |
| StateEffect | `StateEffect` / `StateEffectType` | Present |
| Compartment | `Compartment` class | Present |
| EditorState | `EditorState` class | Present |
| EditorSession | `EditorSession` class | Present (Compose-based) |
| ViewPlugin | `ViewPlugin` class | Present, with `define`, `fromClass` |
| Decoration | `Decoration` class (mark, replace, widget, line) | Present |
| ChangeSet / ChangeDesc | Both present | Present |
| Transaction / TransactionSpec | Both present | Present |
| Text | `Text` abstract class | Present |
| LRParser | `LRParser` class | Present |
| Tree / TreeCursor / SyntaxNode | All present | Present |
| Language / LanguageSupport | Both present | Present |
| StreamParser / StreamLanguage | Both present | Present |
| HighlightStyle / syntaxHighlighting | Both present | Present |
| Tag / tags | Both present | Present |
| KeyBinding / keymap | Present | Present |

### Compose Adaptation

The project replaces CM6's DOM-based view with Compose Multiplatform:

- `EditorSession` is a Composable function and a state holder
- `EditorTheme` replaces CSS theming with Compose `Color` and `TextStyle` values
- `Panel` uses Composable content instead of DOM elements
- `Tooltip` uses Composable content
- `GutterView` is a Composable
- `WidgetType` integrates with Compose rendering

This is a significant and architecturally sound adaptation. The use of `ProvidableCompositionLocal`
(`LocalEditorSession`) for propagating the view context is idiomatic Compose.

---

## Findings Summary

| # | Finding | Severity | Category |
|---|---------|----------|----------|
| 1 | Mutable state exposure in lezer-lr (Stack, Parse, InputStream, etc.) | **High** | API Safety |
| 2 | ComposableSingletons leaked in search and lint public APIs | **High** | JS Leakage / Compose Internals |
| 3 | `tags` object uses lowercase class name | **Medium** | Naming |
| 4 | Top-level `parser` name collision across 15 language modules | **Medium** | Naming |
| 5 | Type-erased `Any` in TagStyleSpec, TagStyleRule, TreeBuildSpec, NestedParse, NodeSpec | **Medium** | Type Safety |
| 6 | Parser-internal constants exposed as public objects (Action, Encode, Rec, etc.) | **Medium** | JS Leakage |
| 7 | Rule.next, LeafBlock.content/parsers publicly mutable | **Medium** | API Safety |
| 8 | Legacy-mode state classes publicly mutable (hundreds of setters) | **Medium** | API Safety |
| 9 | `jsHighlight` vs `{lang}Highlighting` naming inconsistency | **Low** | Naming |
| 10 | Missing highlighting exports in lang-php, lang-angular | **Low** | Pattern Adherence |
| 11 | `toJSON`/`fromJSON` naming convention from JavaScript | **Low** | JS Leakage |
| 12 | `$stable` fields in public API (82 occurrences) | **Low** | Compose Internals |
| 13 | `getTagLanguage` name collision between lang-jinja and lang-liquid | **Low** | Naming |
| 14 | IterMode as integer bit flags instead of EnumSet | **Low** | JS Leakage |

---

## Recommendations (Prioritized)

### Priority 1 -- API Safety

1. **Make lezer-lr internal state `internal`.** Classes like `Parse`, `Stack`, `CachedToken`,
   `InputStream`, `SimulatedStack`, `StackBufferCursor`, `TokenCache` are parser implementation
   details. Their mutable setters should not be in the public API. If they must remain public for
   cross-module access, consider a `@PublishedApi internal` pattern or a separate `-internal` module.

2. **Make lezer-lr constant objects `internal`.** `Action`, `Encode`, `Rec`, `Recover`, `Seq`,
   `ParseState`, `StateFlag`, `Term`, `File`, `Lookahead`, `Specialize` are internal parser constants.

3. **Restrict legacy-mode state class visibility.** The 100+ state classes with public mutable setters
   should be `internal` or package-private. If the `StreamParser<State>` generic requires them to be
   public, consider making the setters `internal`.

### Priority 2 -- Compose Internals Leakage

4. **Filter ComposableSingletons from public API.** Add `ComposableSingletons` to the API dump
   exclusion list or restructure the code so these compiler-generated classes are not exposed.

5. **Consider filtering `$stable` fields.** While harmless, they add noise to the API surface.

### Priority 3 -- Naming Consistency

6. **Rename `jsHighlight` to `jsHighlighting`** (or `javascriptHighlighting`) to match all other
   language modules.

7. **Prefix top-level `parser` properties** with the language name: `javaParser`, `pythonParser`, etc.
   This prevents confusion when multiple language parsers are in scope.

8. **Rename `tags` to `Tags`** to follow Kotlin naming conventions.

### Priority 4 -- Type Safety Improvements

9. **Type the `tag` field in TagStyleSpec and TagStyleRule.** These should accept `Tag` or
   `(Tag) -> Tag` (for modifiers) rather than `Any`.

10. **Type `TreeBuildSpec.buffer`** as `List<Int>` or `IntArray` instead of `Any`.

11. **Type `NestedParse.overlay`** with a proper sealed class or typed representation.

### Priority 5 -- Polish

12. **Add missing exports** for lang-php (highlighting) and lang-angular (highlighting, parser).

13. **Move vue highlighting** from `VueParserKt` to a separate `VueHighlightKt` for consistency.

14. **Consider Kotlin-idiomatic serialization names** (`serialize`/`deserialize`) as aliases alongside
    `toJSON`/`fromJSON`, or document the JS heritage.
