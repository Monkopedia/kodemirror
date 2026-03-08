# Kodemirror Documentation Evaluation

## Summary

Kodemirror's documentation is **significantly above average for a Kotlin Multiplatform project**
at this stage of development. The project has comprehensive guide documentation covering all
core concepts, 24 example pages adapted from upstream CodeMirror, and reasonably thorough KDoc
coverage on public APIs in core modules. Dokka-generated API docs are configured and integrated
into the CI/CD pipeline. The main gaps are in KDoc coverage of internal/utility code, the
Text.kt file using non-KDoc comment style, and the absence of published API docs at a
discoverable URL.

**Overall Grade: B+**

---

## KDoc Coverage

### Methodology

Sampled 3-5 key files from each core module and 2-3 files from feature modules. For each
public member (class, function, property, companion object member), assessed whether KDoc was
present and its quality.

### Module-by-Module Breakdown

#### `:state` module

| File | Public Members | With KDoc | Coverage | Quality |
|------|---------------|-----------|----------|---------|
| `State.kt` | ~25 | ~22 | 88% | High -- clear descriptions of immutable state pattern, field access, facet access |
| `Facet.kt` | ~20 | ~15 | 75% | High -- `Facet`, `StateField`, `Compartment`, `Prec` all documented |
| `Transaction.kt` | ~18 | ~16 | 89% | High -- `Transaction`, `TransactionSpec`, annotations, effects all covered |
| `Change.kt` | ~30 | ~25 | 83% | High -- `ChangeDesc`, `ChangeSet`, `MapMode` thoroughly documented |
| `Selection.kt` | ~20 | ~18 | 90% | High -- `SelectionRange`, `EditorSelection` well-documented |
| `Text.kt` | ~15 | 0 (KDoc) | **0%** | **CRITICAL** -- Uses `// /` comment style instead of `/** */` KDoc |

**State Module Assessment**: Excellent KDoc on most files. The critical exception is `Text.kt`,
which uses JavaScript-style `// /` comments (carried over from the CodeMirror port) instead of
proper KDoc `/** */` syntax. These comments contain the right content but are **invisible to
Dokka** and IDE documentation tools.

**Severity: Critical** -- `Text.kt` defines the core document data structure (`Text`,
`TextIterator`, `Line`). This is one of the most fundamental APIs and has zero working KDoc.

- File: `/home/jmonk/git/kodemirror/state/src/commonMain/kotlin/com/monkopedia/kodemirror/state/Text.kt`
- Lines 53-72: `TextIterator` interface -- 4 members with `// /` comments, 0 with KDoc
- Lines 74-80: `Text` class -- `length`, `lines` with `// /` comments, not KDoc
- Lines 82-172: All public methods (`lineAt`, `line`, `replace`, `append`, `slice`, etc.) use `// /`

Additionally, `Extension.kt` (line 19-35) has top-level facets (`languageData`,
`allowMultipleSelections`, `lineSeparator`, `readOnly`, `changeFilter`, etc.) with minimal
or no KDoc. These are public API surface exposed to extension authors.

- File: `/home/jmonk/git/kodemirror/state/src/commonMain/kotlin/com/monkopedia/kodemirror/state/Extension.kt`
- Lines 21-24: `languageData` facet -- no KDoc
- Lines 38-42: `allowMultipleSelections` -- no KDoc
- Lines 44-49: `lineSeparator` -- no KDoc
- Lines 57-60: `changeFilter` -- no KDoc
- Lines 62-65: `transactionFilter` -- no KDoc
- Lines 72-75: `transactionExtender` -- no KDoc

**Severity: Medium** -- These facets are important but their usage is documented in guides.

#### `:view` module

| File | Public Members | With KDoc | Coverage | Quality |
|------|---------------|-----------|----------|---------|
| `EditorSession.kt` | ~15 | ~14 | 93% | High -- class, methods, companion facets all documented |
| `Decoration.kt` | ~15 | ~14 | 93% | High -- all decoration types and specs documented |
| `ViewPlugin.kt` | ~10 | ~10 | 100% | Excellent -- includes code example in class KDoc |
| `Keymap.kt` | ~8 | ~8 | 100% | Excellent -- `KeyBinding` params documented, `normalizeKeyName` has examples |
| `KodeMirror.kt` | ~3 | ~3 | 100% | Good -- composable function params documented |
| `EditorTheme.kt` | ~30 | ~28 | 93% | Excellent -- every theme color property documented |
| `Gutter.kt` | ~8 | ~8 | 100% | Good -- `GutterConfig`, `GutterMarker`, `lineNumbers` all documented |
| `ViewUpdate.kt` | ~8 | ~8 | 100% | Excellent -- all properties documented with clear descriptions |
| `Tooltip.kt` | ~6 | ~6 | 100% | Good -- `Tooltip` data class, facets, `hoverTooltip` all documented |
| `ViewExtensions.kt` | ~8 | ~7 | 88% | Good -- facets documented; `perLineTextDirection` name is slightly misleading |

**View Module Assessment**: Outstanding KDoc coverage. Nearly every public API has documentation.
The `ViewPlugin` class KDoc even includes a usage code example (line 59-69 in `ViewPlugin.kt`).

**Severity: None** -- This module sets the standard for the project.

#### `:language` module

| File | Public Members | With KDoc | Coverage | Quality |
|------|---------------|-----------|----------|---------|
| `Language.kt` | ~12 | ~10 | 83% | Good -- `Language`, `LanguageSupport`, `LRLanguage` documented |
| `StreamParser.kt` | ~15 | ~10 | 67% | Moderate -- `StreamParser` interface documented but internal helpers not |
| `StringStream.kt` | not sampled | - | - | - |
| `Indent.kt` | not sampled | - | - | - |
| `Fold.kt` | not sampled | - | - | - |

**Language Module Assessment**: Good coverage on primary API types. The `StreamParser` interface
has KDoc on all members (lines 42-81 of `StreamParser.kt`). Internal implementation classes
like `TokenTable`, `StreamParse` are marked internal and lack KDoc, which is acceptable.

**Severity: Low** -- `IndentContext`, `TreeIndentContext` referenced in guides but not directly sampled.

#### `:commands` module

| File | Public Members | With KDoc | Coverage | Quality |
|------|---------------|-----------|----------|---------|
| `Commands.kt` | ~20 commands | ~20 | 100% | Good -- each command has a one-line KDoc |

**Commands Module Assessment**: Every public command (`cursorCharLeft`, `cursorCharRight`,
`cursorLineUp`, etc.) has a brief KDoc comment. Format is consistent: `/** Move cursor one
character to the left. */`.

**Severity: None**

#### `:autocomplete` module

| File | Public Members | With KDoc | Coverage | Quality |
|------|---------------|-----------|----------|---------|
| `Completion.kt` | ~5 | ~5 | 100% | Excellent -- `Completion`, `CompletionResult`, all params documented |

**Autocomplete Module Assessment**: The primary API types are well-documented with parameter
descriptions in KDoc.

**Severity: None**

#### `:search` module

| File | Public Members | With KDoc | Coverage | Quality |
|------|---------------|-----------|----------|---------|
| `Search.kt` | ~3 | ~2 | 67% | Good -- `search()` function documented; internal plugin is internal |

**Severity: Low**

#### `:lint` module

| File | Public Members | With KDoc | Coverage | Quality |
|------|---------------|-----------|----------|---------|
| `Lint.kt` | ~3 | ~2 | 67% | Good -- `linter()` function has KDoc with params |

**Severity: Low**

#### Language modules (`:lang-javascript`, `:lang-python`)

| File | Public Members | With KDoc | Coverage | Quality |
|------|---------------|-----------|----------|---------|
| `Javascript.kt` | ~6 | ~6 | 100% | Good -- each language variant and `javascript()` function documented |
| `Python.kt` | ~2 | ~2 | 100% | Good -- `pythonLanguage` and `python()` documented |

**Language Module Assessment**: Public API types are documented. Internal indentation and
folding helpers lack KDoc but are private/internal.

**Severity: None**

#### Legacy modes (`:legacy-modes`)

| File | Public Members | With KDoc | Coverage |
|------|---------------|-----------|----------|
| `GoLang.kt` | ~2 | 0 | 0% |

**Legacy Modes Assessment**: The 103 legacy mode files are ports of CodeMirror 5 modes and
generally have no KDoc. Given these are mechanical ports with a uniform structure
(`StreamParser` implementations), this is a lower priority.

**Severity: Low** -- Users primarily interact through `StreamLanguage.define(GoLang)` pattern.

### KDoc Coverage Summary

| Module | Estimated Coverage | Quality |
|--------|-------------------|---------|
| `:state` (excl. Text.kt) | 85% | High |
| `:state` Text.kt | **0%** (wrong comment style) | N/A |
| `:view` | **95%** | Excellent |
| `:language` | 75% | Good |
| `:commands` | 100% | Good |
| `:autocomplete` | 100% | Excellent |
| `:search` | 67% | Good |
| `:lint` | 67% | Good |
| `:lang-*` | 90% | Good |
| `:legacy-modes` | 5% | Low priority |

---

## Guide Quality

### Overview

The guide documentation lives in `/home/jmonk/git/kodemirror/docs-site/docs/guide/` and
consists of 5 files: `index.md`, `architecture.md`, `data-model.md`, `the-view.md`, and
`extending.md`.

### Assessment by Guide

#### `index.md` -- Guide Index
**Rating: Good**

Clear, concise index with descriptive summaries for each guide page. Provides a logical
learning path: Architecture -> Data Model -> The View -> Extending.

#### `architecture.md` -- Architecture
**Rating: Excellent**

- Covers all 36 modules organized into 6 dependency layers with tables
- Explains platform targets (JVM, wasmJs) with actual Gradle config
- Clearly articulates core design principles: immutable state, extension-driven, functional
  core / composable shell
- Includes a comparison table of upstream CodeMirror vs. Kodemirror differences
- Has working code examples for state creation and extension composition

**Strengths**: The layered module table and upstream comparison table are exceptionally useful
for developers coming from CodeMirror.

**Missing**: No dependency diagram (visual). No mention of the `build.gradle.kts` convention
plugin system beyond the platform config snippet.

#### `data-model.md` -- Data Model
**Rating: Excellent**

- Covers Documents (`Text`), Changes (`ChangeSet`), Selections (`EditorSelection`),
  Transactions, Effects, Facets, and EditorState
- Every concept has working code examples
- Tables summarize `TransactionSpec` fields and built-in facets
- Correctly explains immutability model and mapping through changes

**Strengths**: Very thorough coverage of the state layer. Code examples are accurate and match
the actual API (verified against source).

**Minor issues**:
- The `Text.of()` example uses `listOf("line 1", "line 2")` which is correct but doesn't
  mention that `Text.of()` expects split lines (no embedded newlines)
- No mention of `Text.iter()` variants or `RawTextCursor`

#### `the-view.md` -- The View
**Rating: Excellent**

- Covers `EditorSession` composable, the `EditorSession` class, theming, panels, tooltips, gutters,
  commands/keybindings, coordinate queries, and `ViewUpdate`
- Includes comparison tables with upstream CodeMirror
- Code examples for all major features
- Explains the Compose state-hoisting pattern clearly

**Strengths**: The theming section is particularly well-done, with a clear explanation of how
CSS-based theming was replaced with a Kotlin data class + CompositionLocal pattern.

**Missing**:
- No discussion of selection/cursor drawing mechanism (the `drawWithContent` overlay)
- No mention of `InputHandling.kt` or how text input flows through the system
- No discussion of the `LazyColumn` virtualization and its implications for large documents

#### `extending.md` -- Extending
**Rating: Excellent**

- Covers all four extension primitives: Facets, StateFields, ViewPlugins, Compartments
- Also covers StateEffects, Decorations (all 4 types), Precedence
- Ends with a realistic "putting it together" example showing how the search extension
  composes all primitives
- Slot dependency system clearly explained with a table

**Strengths**: The `search()` decomposition example is the best piece of documentation in the
project -- it shows real code and explains how each piece fits together.

**Missing**:
- No discussion of `FacetReader` and when to use it vs. direct `Facet`
- No mention of `PluginSpec.configure` pattern (used in the code but not explained)

### Guide Summary

| Guide | Rating | Severity of Gaps |
|-------|--------|-----------------|
| `index.md` | Good | None |
| `architecture.md` | Excellent | Low |
| `data-model.md` | Excellent | Low |
| `the-view.md` | Excellent | Medium -- missing input handling explanation |
| `extending.md` | Excellent | Low |

---

## Example Coverage

### Overview

24 example pages in `/home/jmonk/git/kodemirror/docs-site/docs/examples/`. Each is adapted
from the upstream CodeMirror examples with Kotlin code.

### Coverage Assessment

| Category | Examples | Assessment |
|----------|----------|------------|
| **Getting Started** | `basic.md`, `bundle.md` | Complete -- minimal editor, full setup, dependency table |
| **Configuration** | `config.md`, `styling.md`, `tab.md`, `readonly.md` | Complete -- compartments, themes, tab handling |
| **Content** | `change.md`, `selection.md`, `decoration.md` | Complete -- all decoration types with code |
| **Features** | `autocompletion.md`, `lint.md`, `gutter.md`, `panel.md`, `tooltip.md` | Complete -- all major features covered |
| **Advanced** | `zebra.md`, `lang-package.md`, `mixed-language.md`, `collab.md`, `split.md`, `million.md`, `bidi.md`, `inverted-effect.md`, `translate.md` | Complete -- covers advanced use cases |

### Quality of Sampled Examples

**`basic.md`**: Excellent. Shows minimal editor and fully-loaded editor with extension table.
Every extension is listed with its module and purpose. This is a good first landing page.

**`decoration.md`**: Excellent. Covers all 4 decoration types with code, plus both state-field
and view-plugin approaches to providing decorations. Correctly notes Kodemirror differences
from upstream (SpanStyle vs CSS classes, `@Composable Content()` vs `toDOM()`).

**`autocompletion.md`**: Excellent. Shows custom completion source, `completeFromList` helper,
configuration, and command bindings. All code examples are accurate.

**`lint.md`**: Excellent. Full linter example with diagnostic properties table, severity
levels, configuration, gutter integration, and quick-fix actions.

**`collab.md`**: Excellent. Complete collaboration example with polling architecture, full API
summary table, and rebase support.

**`zebra.md`**: Excellent. Progressive example starting simple and adding configurability via
facets. Ends with a "Key concepts" section explaining each pattern used.

**`config.md`**: Good. Covers compartments for language switching, feature toggling, and theme
switching. Code examples are accurate.

**`bundle.md`**: Good. Clear Gradle dependency examples with module dependency table.

### What's Missing

1. **No runnable examples** -- Examples are code snippets only. There is no playground or
   runnable sample project that users can clone and run.
   **Severity: Medium**

2. **No screenshot/visual output** -- None of the examples show what the result looks like.
   **Severity: Medium**

3. **No migration guide from CodeMirror** -- While differences are noted inline, there is no
   dedicated "migrating from CodeMirror" guide for JavaScript developers.
   **Severity: Low** (the audience is primarily Kotlin developers)

4. **No Android/Desktop-specific setup** -- The bundle example shows generic KMP deps but
   does not show a complete `build.gradle.kts` for an Android app or Desktop app.
   **Severity: Medium**

---

## API Reference

### Dokka Configuration

Dokka is **configured and integrated**:

- The convention plugin at `/home/jmonk/git/kodemirror/convention-plugins/src/main/kotlin/kodemirror.library.gradle.kts`
  (line 28) includes `id("org.jetbrains.dokka")`
- The CI workflow at `/home/jmonk/git/kodemirror/.github/workflows/docs.yml` (line 32) runs
  `./gradlew :dokkaGenerate` and uploads the output
- API docs are merged into the docs site at `docs-site/docs/api/`
- The docs site uses MkDocs with a Python requirements file

### Current Status

- Dokka is configured but generated API docs were not checked locally (would require a
  Gradle build)
- The CI pipeline generates and publishes API docs on push to main
- API docs are available at the project's GitHub Pages URL under `/api/`

### Gaps

1. **No API docs link in the guide index** -- The guide `index.md` does not link to the
   generated API reference
   **Severity: High**

2. **Text.kt `// /` comments are invisible to Dokka** -- The most important data structure
   in the project will have empty API docs
   **Severity: Critical**

3. **No API navigation from examples** -- Examples reference types like `RangeSetBuilder`,
   `Completion`, `Diagnostic` without linking to API docs
   **Severity: Medium**

---

## Onboarding Experience

### Assessment

The new-developer journey for Kodemirror is **well-structured**:

1. **Entry point**: `docs-site/docs/examples/basic.md` provides a minimal working editor
2. **Dependencies**: `docs-site/docs/examples/bundle.md` shows how to add Gradle deps
3. **Concepts**: The guide covers Architecture -> Data Model -> View -> Extending in a logical
   progression
4. **Advanced topics**: 24 examples cover progressively complex use cases
5. **API reference**: Dokka-generated docs are available (CI pipeline configured)

### Strengths

- Clear separation between conceptual guides and practical examples
- Code examples are idiomatic Kotlin (not mechanical JS-to-Kotlin translations)
- Comparison tables with upstream CodeMirror help developers with prior CM6 experience
- The state-hoisting pattern is correctly presented as the standard Compose integration

### Gaps

1. **No "Getting Started" tutorial** -- There is no step-by-step tutorial that walks through
   creating a project from scratch, adding dependencies, and building a working editor. The
   `basic.md` example shows code but not the project setup process.
   **Severity: Medium**

2. **No sample project repository** -- No pointer to a minimal runnable sample project
   **Severity: Medium**

3. **No troubleshooting section** -- Common issues like Java version requirements (project
   needs Java 11+, as noted in CLAUDE.md) are not documented for users.
   **Severity: Low** (affects contributors more than users)

4. **No changelog or version history** -- As a pre-1.0 project at version `0.1.0-SNAPSHOT`,
   there is no changelog documenting breaking changes between versions.
   **Severity: Low** (pre-release)

---

## Critical Gaps

### 1. `Text.kt` uses non-KDoc comment style [CRITICAL]

**File**: `/home/jmonk/git/kodemirror/state/src/commonMain/kotlin/com/monkopedia/kodemirror/state/Text.kt`

The `Text` class, `TextIterator` interface, and `Line` class use `// /` comments (from the
upstream JavaScript port) instead of `/** */` KDoc comments. This means:

- Dokka generates no documentation for these types
- IDE quick-documentation (Ctrl+Q / F1) shows nothing
- The `Text` class is the single most important data structure in the project

Affected members include:
- `TextIterator.next()`, `TextIterator.value`, `TextIterator.done`, `TextIterator.lineBreak`
- `Text.length`, `Text.lines`, `Text.lineAt()`, `Text.line()`, `Text.replace()`,
  `Text.append()`, `Text.slice()`, `Text.sliceString()`, `Text.eq()`, `Text.iter()`,
  `Text.iterRange()`, `Text.iterLines()`
- `Line.from`, `Line.to`, `Line.number`, `Line.text`, `Line.length`

### 2. Top-level facets in `Extension.kt` lack KDoc [MEDIUM]

**File**: `/home/jmonk/git/kodemirror/state/src/commonMain/kotlin/com/monkopedia/kodemirror/state/Extension.kt`

Seven top-level facets are exported without KDoc:
- `languageData` (line 21)
- `allowMultipleSelections` (line 38)
- `lineSeparator` (line 44)
- `changeFilter` (line 57)
- `transactionFilter` (line 67)
- `transactionExtender` (line 72)
- `invertedEffects` (line 77)
- `readOnly` (line 82)

These are referenced in the `EditorState.Companion` with brief KDoc (`/** A facet for
allowing multiple selections. */` etc.) but the actual top-level definitions lack docs.

### 3. No API docs link in guide navigation [HIGH]

The guide index page (`docs-site/docs/guide/index.md`) does not mention or link to the
Dokka-generated API reference. Users reading the guides have no way to discover that
per-class/per-method documentation exists.

### 4. Input handling undocumented [MEDIUM]

The text input pipeline -- how keystrokes become characters in the document, how IME
composition works, how text is committed -- is not described in any guide or example.

- File: `/home/jmonk/git/kodemirror/view/src/commonMain/kotlin/com/monkopedia/kodemirror/view/InputHandling.kt`
- This is an area where Kodemirror differs significantly from upstream CodeMirror (Compose
  text input vs. DOM `ContentEditable`)

### 5. No platform-specific setup guides [MEDIUM]

The bundle example shows KMP dependencies but not how to set up a complete Android app or
Desktop app with Kodemirror. Given that the primary use case is embedding an editor in a
Compose application, this is a notable gap.

---

## Severity Ratings Summary

| Finding | Severity | Category |
|---------|----------|----------|
| `Text.kt` uses `// /` instead of KDoc | **Critical** | KDoc |
| No API docs link in guide index | **High** | Navigation |
| Top-level facets in `Extension.kt` lack KDoc | Medium | KDoc |
| Input handling pipeline undocumented | Medium | Guide |
| No runnable sample project | Medium | Onboarding |
| No platform-specific setup (Android/Desktop) | Medium | Examples |
| No visual screenshots in examples | Medium | Examples |
| No API cross-links from examples | Medium | Navigation |
| Missing `FacetReader` docs in extending guide | Low | Guide |
| Legacy modes lack KDoc | Low | KDoc |
| No migration guide from upstream CodeMirror | Low | Guide |
| No changelog/version history | Low | Onboarding |
| `IndentContext` / `TreeIndentContext` KDoc sparse | Low | KDoc |

---

## Recommendations (Prioritized)

### P0 -- Critical

1. **Convert `Text.kt` comments from `// /` to `/** */` KDoc**
   - Impact: Fixes Dokka output, IDE documentation, and addresses the project's most
     fundamental API being undocumented
   - Effort: Low (mechanical find-and-replace within one file)
   - Files: `/home/jmonk/git/kodemirror/state/src/commonMain/kotlin/com/monkopedia/kodemirror/state/Text.kt`

### P1 -- High

2. **Add API reference link to guide index and examples index**
   - Add a link like "- [API Reference](../api/) -- Generated API documentation for all modules"
     to `docs-site/docs/guide/index.md` and `docs-site/docs/examples/index.md`
   - Effort: Minimal

3. **Add KDoc to top-level facets in `Extension.kt`**
   - The `EditorState.Companion` duplicates these with brief KDoc; consolidate to the
     top-level definition
   - Effort: Low

### P2 -- Medium

4. **Add input handling documentation to the-view guide**
   - Explain how text input flows: Compose `onKeyEvent` -> `handleKeyEvent()` -> command
     dispatch -> `Transaction` -> state update
   - Effort: Moderate

5. **Create a complete sample project**
   - A minimal Android or Desktop app in a separate `samples/` directory or linked repository
   - Effort: Moderate

6. **Add platform-specific setup to bundle.md**
   - Show Android `build.gradle.kts` and Desktop `build.gradle.kts` snippets
   - Effort: Low

7. **Scan for other files with `// /` comment style**
   - `Text.kt` is the most important, but other files may have the same issue from the
     upstream port
   - Effort: Low (grep + mechanical fix)

### P3 -- Low

8. **Add KDoc to legacy modes' public API entry points**
   - Each legacy mode has a top-level `val` or function; a one-line KDoc would suffice
   - Effort: Low but spread across 103 files

9. **Add visual screenshots to examples**
   - At least for `basic.md`, `decoration.md`, and `styling.md`
   - Effort: Moderate

10. **Create a "Migrating from CodeMirror" page**
    - Consolidate the comparison tables from guides into a dedicated migration reference
    - Effort: Moderate
