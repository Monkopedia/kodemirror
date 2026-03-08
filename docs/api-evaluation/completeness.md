# Kodemirror API Completeness Evaluation

**Date**: 2026-03-06
**Evaluator**: Automated analysis of Kodemirror public API surface vs CodeMirror 6 upstream
**Upstream versions**: Per `docs/codemirror-upstream.md` (recorded 2026-03-05)

---

## Summary

Kodemirror provides a remarkably comprehensive port of CodeMirror 6 to Kotlin Multiplatform /
Jetpack Compose. All 9 core CM6 infrastructure modules are ported, all 3 Lezer parser infrastructure
modules are ported, all 22 upstream `lang-*` packages are ported, and the full set of 103 legacy
stream-parser modes is ported. The state, view, language, and Lezer modules represent faithful
translations of the upstream API surface.

**Overall completeness: ~85-90%**

The main gaps fall into three categories:
1. **Missing convenience/meta packages** -- `basicSetup`, `minimalSetup`, `language-data`
2. **Missing secondary APIs within ported modules** -- ~30 commands, several language module
   features (completion sources, snippets, autoCloseTags), and a handful of view utilities
3. **Entirely unported modules** -- `lsp-client` (upstream is relatively new)

The project has excellent structural coverage with very few stubs or unimplemented code paths
(only 1 `error("Not implemented")` found in test code).

---

## Module Coverage

### Core Infrastructure Modules

| CM6 Package | Kodemirror Module | Status | Completeness |
|---|---|---|---|
| `@codemirror/state` | `:state` (v6.5.4) | Present | ~95% |
| `@codemirror/view` | `:view` (v6.39.16) | Present | ~85% |
| `@codemirror/commands` | `:commands` (v6.10.2) | Present | ~70% |
| `@codemirror/language` | `:language` (v6.12.2) | Present | ~80% |
| `@codemirror/autocomplete` | `:autocomplete` (v6.20.1) | Present | ~85% |
| `@codemirror/search` | `:search` (v6.6.0) | Present | ~90% |
| `@codemirror/lint` | `:lint` (v6.9.5) | Present | ~95% |
| `@codemirror/collab` | `:collab` (v6.1.1) | Present | ~95% |
| `@codemirror/merge` | `:merge` (v6.12.0) | Present | ~95% |

### Lezer Parser Infrastructure

| CM6 Package | Kodemirror Module | Status | Completeness |
|---|---|---|---|
| `@lezer/common` | `:lezer-common` (v1.5.1) | Present | ~95% |
| `@lezer/highlight` | `:lezer-highlight` (v1.2.3) | Present | ~95% |
| `@lezer/lr` | `:lezer-lr` (v1.4.8) | Present | ~95% |

### Convenience / Meta / Theme Packages

| CM6 Package | Kodemirror Module | Status | Completeness |
|---|---|---|---|
| `codemirror` / `@codemirror/basic-setup` | **Missing** | Not ported | 0% |
| `@codemirror/language-data` | **Missing** | Not ported | 0% |
| `@codemirror/theme-one-dark` | `:theme-one-dark` (v6.1.3) | Present | ~95% |
| `@codemirror/legacy-modes` | `:legacy-modes` (v6.5.2) | Present | ~100% |
| `@codemirror/lsp-client` | **Missing** | Not ported | 0% |

### Language Packages (22 of 22 ported)

| CM6 Package | Kodemirror Module | Status | Completeness |
|---|---|---|---|
| `@codemirror/lang-javascript` | `:lang-javascript` | Present | ~50% |
| `@codemirror/lang-html` | `:lang-html` | Present | ~50% |
| `@codemirror/lang-css` | `:lang-css` | Present | ~90% |
| `@codemirror/lang-json` | `:lang-json` | Present | ~90% |
| `@codemirror/lang-xml` | `:lang-xml` | Present | ~90% |
| `@codemirror/lang-python` | `:lang-python` | Present | ~90% |
| `@codemirror/lang-rust` | `:lang-rust` | Present | ~90% |
| `@codemirror/lang-cpp` | `:lang-cpp` | Present | ~90% |
| `@codemirror/lang-java` | `:lang-java` | Present | ~90% |
| `@codemirror/lang-php` | `:lang-php` | Present | ~90% |
| `@codemirror/lang-go` | `:lang-go` | Present | ~90% |
| `@codemirror/lang-sass` | `:lang-sass` | Present | ~90% |
| `@codemirror/lang-yaml` | `:lang-yaml` | Present | ~90% |
| `@codemirror/lang-markdown` | `:lang-markdown` | Present | ~90% |
| `@codemirror/lang-sql` | `:lang-sql` | Present | ~95% |
| `@codemirror/lang-less` | `:lang-less` | Present | ~90% |
| `@codemirror/lang-vue` | `:lang-vue` | Present | ~90% |
| `@codemirror/lang-angular` | `:lang-angular` | Present | ~90% |
| `@codemirror/lang-wast` | `:lang-wast` | Present | ~90% |
| `@codemirror/lang-liquid` | `:lang-liquid` | Present | ~90% |
| `@codemirror/lang-jinja` | `:lang-jinja` | Present | ~90% |
| `@codemirror/lang-lezer` | `:lang-grammar` | Present | ~90% |

---

## Missing APIs by Module

### `:commands` -- Missing ~30 Commands (Severity: Medium)

The commands module is missing a significant number of directional/logical variant commands that
CM6 provides. The Kodemirror API exposes ~48 commands; CM6 exports ~80+.

**Missing cursor movement commands:**
- `cursorCharForward`, `cursorCharBackward` (logical/bidi-aware variants of left/right)
- `cursorCharForwardLogical`, `cursorCharBackwardLogical`
- `cursorGroupForward`, `cursorGroupBackward`
- `cursorGroupForwardWin`
- `cursorSyntaxLeft`, `cursorSyntaxRight`
- `cursorLineBoundaryForward`, `cursorLineBoundaryBackward`
- `cursorLineBoundaryLeft`, `cursorLineBoundaryRight`

**Missing selection extension commands:**
- `selectCharForward`, `selectCharBackward`
- `selectCharForwardLogical`, `selectCharBackwardLogical`
- `selectGroupForward`, `selectGroupBackward`
- `selectGroupForwardWin`
- `selectSyntaxLeft`, `selectSyntaxRight`
- `selectLineBoundaryForward`, `selectLineBoundaryBackward`
- `selectLineBoundaryLeft`, `selectLineBoundaryRight`
- `selectPageUp`, `selectPageDown`

**Missing editing commands:**
- `deleteCharBackwardStrict`
- `deleteGroupForwardWin`
- `deleteLineBoundaryBackward`, `deleteLineBoundaryForward`
- `deleteTrailingWhitespace`
- `newlineAndIndent` (the distinct export; `insertNewlineAndIndent` exists but may differ)
- `toggleTabFocusMode`, `temporarilySetTabFocusMode`
- `addCursorAbove`, `addCursorBelow`

**Missing history commands:**
- `undoSelection`, `redoSelection` (undo/redo that restores selection)
- `historyField` (exported as internal in Kodemirror but not public)

### `:view` -- Missing Utilities (Severity: Medium)

**Missing exported functions/extensions:**
- `logException` -- error logging utility
- `runScopeHandlers` -- run keybindings in a given scope
- `highlightWhitespace` -- extension to visually mark whitespace
- `highlightTrailingWhitespace` -- extension to mark trailing whitespace
- `layer` / `LayerMarker` -- low-level layer API for custom overlays
- `RectangleMarker` -- rectangle visual marker
- `getDrawSelectionConfig` -- access draw-selection configuration
- `getTooltip` -- retrieve a specific tooltip from state
- `repositionTooltips` -- force tooltip repositioning
- `hasHoverTooltips` -- check if hover tooltips exist
- `closeHoverTooltips` -- programmatically close hover tooltips
- `showDialog` / `getDialog` -- dialog API (newer CM6 addition)
- `getPanel` -- retrieve a specific panel from state
- `panels` -- the panels facet
- `gutterWidgetClass` -- gutter widget styling
- `lineNumberMarkers` -- facet for custom line number markers
- `lineNumberWidgetMarker` -- widget-based line number markers

**Present but potentially incomplete:**
- `GutterMarker` -- class exists, but `gutterLineClass` facet not found in API dump
- `Tooltip` -- present, but missing some accessor utilities
- `Panel` -- present with `showPanel`/`showPanels` facets

### `:language` -- Missing APIs (Severity: Medium)

**Missing exports:**
- `ensureSyntaxTree` -- synchronous tree accessor with timeout
- `ParseContext` -- parsing context for async parsing
- `LanguageDescription` -- language metadata for dynamic loading
- `syntaxTreeAvailable` -- check if tree is available
- `syntaxParserRunning` -- check if parser is currently running
- `forceParsing` -- force synchronous parsing
- `highlightingFor` -- resolve highlight style for a given state
- `bracketMatchingHandle` -- programmatic bracket match access
- `bidiIsolates` -- bidirectional text isolation extension
- `Sublanguage` / `sublanguageProp` -- sublanguage nesting

### `:autocomplete` -- Minor Gaps (Severity: Low)

**Missing exports:**
- `pickedCompletion` -- annotation for picked completions
- `ifIn` / `ifNotIn` -- context-aware completion source wrappers
- `hasNextSnippetField` / `hasPrevSnippetField` -- snippet field navigation checks
- `CompletionInfo` type -- detailed completion info type
- `CompletionSource` type alias (functionality exists via lambda types)

### `:search` -- Minor Gap (Severity: Low)

**Missing export:**
- `selectSelectionMatches` -- select all instances of current selection text

### `:state` -- Nearly Complete (Severity: Low)

The state module appears highly complete. Minor potential gaps:
- `TextIterator` interface (may be handled differently in Kotlin)
- `wordAt` on `EditorState` (need to verify presence)
- `Prec` utility (need to verify all methods: `highest`, `high`, `default`, `low`, `lowest`)

### `:lint`, `:collab`, `:merge` -- Essentially Complete (Severity: None/Low)

These modules appear to have complete API coverage based on the API dump analysis.

---

## Missing Modules

### `basicSetup` / `minimalSetup` (Severity: Medium)

**Description**: The `codemirror` npm package provides `basicSetup` and `minimalSetup` -- convenience
extension bundles that compose common extensions into a single import. This is the most common entry
point for new CM6 users.

**Impact**: Users must manually compose their own extension arrays. Not a functional blocker since
all individual extensions are available, but it's a convenience and discoverability issue.

**Equivalent in Kodemirror**: None. Users must manually assemble extensions.

### `@codemirror/language-data` (Severity: Low)

**Description**: Provides language metadata (file extensions, MIME types, aliases) and a mechanism
for lazy-loading language packages. Used for features like "detect language from filename" or
"language picker dropdown".

**Impact**: Low for most use cases. KMP apps typically know their language at compile time. Dynamic
language loading is less common in native/Compose applications.

### `@codemirror/lsp-client` (Severity: Low)

**Description**: Language Server Protocol client for CodeMirror, providing autocompletion, hover
tooltips, go-to-definition, signature hints, etc. via an LSP server connection.

**Impact**: Low. This is a relatively new upstream addition (v6.1.0). LSP integration in a
KMP/Compose context would require a different transport layer anyway.

---

## Stubs & TODOs

The codebase is remarkably clean of unimplemented functionality:

| Location | Pattern | Context |
|---|---|---|
| `lezer-common/src/commonTest/.../TreeTest.kt:792` | `error("Not implemented")` | Test helper code only, not production |
| `lang-yaml/src/commonTest/.../YamlParserTest.kt:111` | `// TODO: Parser produces minor error node` | Known minor parser issue |
| `lang-python/src/commonTest/.../PythonParserTest.kt:59` | `// TODO: Parser produces a minor error node` | Known minor parser issue |

**Finding**: No `TODO()`, `error("not implemented")`, or stub patterns found in production source
code. The two TODO comments in test files note known minor parser behavior differences, not missing
functionality.

---

## Language Support Coverage

### Lezer-based Language Packages (22/22 = 100%)

All official CM6 `lang-*` packages are ported:

| Language | Module | Parser | Highlighting | Completion | Snippets | autoCloseTags |
|---|---|---|---|---|---|---|
| JavaScript/TS/JSX/TSX | `:lang-javascript` | Yes | Yes | **No** | **No** | **No** |
| HTML | `:lang-html` | Yes | Yes | **No** | N/A | **No** |
| CSS | `:lang-css` | Yes | Yes | Partial | N/A | N/A |
| JSON | `:lang-json` | Yes | Yes | N/A | N/A | N/A |
| XML | `:lang-xml` | Yes | Yes | N/A | N/A | N/A |
| Python | `:lang-python` | Yes | Yes | N/A | N/A | N/A |
| Rust | `:lang-rust` | Yes | Yes | N/A | N/A | N/A |
| C/C++ | `:lang-cpp` | Yes | Yes | N/A | N/A | N/A |
| Java | `:lang-java` | Yes | Yes | N/A | N/A | N/A |
| PHP | `:lang-php` | Yes | Yes | N/A | N/A | N/A |
| Go | `:lang-go` | Yes | Yes | N/A | N/A | N/A |
| Sass/SCSS | `:lang-sass` | Yes | Yes | N/A | N/A | N/A |
| YAML | `:lang-yaml` | Yes | Yes | N/A | N/A | N/A |
| Markdown | `:lang-markdown` | Yes | Yes | N/A | N/A | N/A |
| SQL (8 dialects) | `:lang-sql` | Yes | Yes | Yes | N/A | N/A |
| Less | `:lang-less` | Yes | Yes | N/A | N/A | N/A |
| Vue | `:lang-vue` | Yes | Yes | N/A | N/A | N/A |
| Angular | `:lang-angular` | Yes | Yes | N/A | N/A | N/A |
| WAST | `:lang-wast` | Yes | Yes | N/A | N/A | N/A |
| Liquid | `:lang-liquid` | Yes | Yes | Yes | N/A | N/A |
| Jinja | `:lang-jinja` | Yes | Yes | Yes | N/A | N/A |
| Lezer Grammar | `:lang-grammar` | Yes | Yes | N/A | N/A | N/A |

**Key gaps in language packages:**

1. **`lang-javascript`**: Missing `localCompletionSource`, `completionPath`,
   `scopeCompletionSource` (JS-aware autocompletion), `snippets`/`typescriptSnippets`
   (code snippet templates), and `autoCloseTags` (JSX tag closing). These are the most
   significant missing language-level features.

2. **`lang-html`**: Missing `autoCloseTags` (auto-close HTML tags on `>`), `htmlCompletion`
   (tag/attribute completion), and `htmlCompletionSourceWith()` (custom completion).

3. **Most other `lang-*` packages**: Provide parser + highlighting but may lack language-specific
   completion sources where the upstream has them.

### Legacy Stream-Parser Modes (103/103 = 100%)

All 103 legacy modes from `@codemirror/legacy-modes` v6.5.2 are ported. Verified file-by-file
match between upstream mode names and Kodemirror Kotlin files:

APL, ASCII Armor, ASN.1, Asterisk, Brainfuck, C-like, Clojure, CMake, COBOL, CoffeeScript,
Common Lisp, Crystal, CSS, Cypher, D, Diff, Dockerfile, DTD, Dylan, EBNF, ECL, Eiffel, Elm,
Erlang, Factor, FCL, Forth, Fortran, GAS, Gherkin, Go, Groovy, Haskell, Haxe, HTTP, IDL,
JavaScript, Jinja2, Julia, LiveScript, Lua, Mathematica, Mbox, mIRC, ML-like, Modelica, MSCgen,
MUMPS, Nginx, NSIS, N-Triples, Octave, Oz, Pascal, PEG.js, Perl, Pig, PowerShell, Properties,
Protocol Buffers, Pug, Puppet, Python, Q, R, RPM, Ruby, Rust, SAS, Sass, Scheme, Shell, Sieve,
Simple Mode, Smalltalk, Solr, SPARQL, Spreadsheet, SQL, STeX, Stylus, Swift, Tcl, Textile,
TiddlyWiki, Tiki, TOML, Troff, TTCN, TTCN-Cfg, Turtle, VB, VBScript, Velocity, Verilog, VHDL,
WAST, WebIDL, XML, XQuery, YACAS, YAML, Z80.

---

## Severity Ratings Summary

| Finding | Severity | Impact |
|---|---|---|
| Missing `basicSetup`/`minimalSetup` convenience bundles | **Medium** | Discoverability; users must manually compose extensions |
| Missing ~30 commands (directional/logical variants) | **Medium** | BiDi and platform-specific editing affected |
| Missing `lang-javascript` completion/snippets/autoCloseTags | **Medium** | JS is the most popular language; users expect IDE-like features |
| Missing `lang-html` autoCloseTags/completion | **Medium** | HTML auto-close is a widely expected feature |
| Missing `@codemirror/language` parsing context APIs | **Medium** | Affects advanced async parsing use cases |
| Missing view utilities (whitespace highlighting, layer API, dialog) | **Medium** | Secondary but useful features |
| Missing `@codemirror/language-data` | **Low** | Dynamic language loading less relevant for KMP |
| Missing `@codemirror/lsp-client` | **Low** | New upstream addition; transport differs for KMP |
| Missing autocomplete minor APIs (`pickedCompletion`, `ifIn`/`ifNotIn`) | **Low** | Edge-case functionality |
| Missing search `selectSelectionMatches` | **Low** | Single missing command |
| Minor parser issues in Python/YAML tests | **Low** | Cosmetic error nodes; not user-facing |

---

## Recommendations (Prioritized)

### Priority 1: High Value, Moderate Effort

1. **Add `basicSetup` / `minimalSetup` convenience functions** -- Create a simple module that
   composes common extensions (line numbers, highlight active line, bracket matching, folding,
   autocompletion, search, history, default keymap, etc.) into a single `Extension`. This is the
   #1 entry point for new users. Effort: ~1 day.

2. **Port `lang-javascript` completion and snippets** -- Add `localCompletionSource`,
   `scopeCompletionSource`, `snippets`, `typescriptSnippets`, and `autoCloseTags` to the
   JavaScript language module. JS is the most commonly used language in code editors. Effort: ~2-3 days.

3. **Port `lang-html` autoCloseTags and completion** -- Add `autoCloseTags`, `htmlCompletion`,
   and `htmlCompletionSourceWith()`. These are among the most expected editor behaviors for HTML.
   Effort: ~1-2 days.

### Priority 2: Medium Value, Moderate Effort

4. **Add missing directional/logical command variants** -- Port the ~30 missing commands
   (`cursorCharForward/Backward`, `selectSyntaxLeft/Right`, `addCursorAbove/Below`, etc.).
   Many of these are thin wrappers over existing cursor motion primitives. Effort: ~2 days.

5. **Add `undoSelection` / `redoSelection`** -- These history commands that restore selection
   state are important for power users. Effort: ~0.5 days.

6. **Port `highlightWhitespace` / `highlightTrailingWhitespace`** -- Commonly requested features
   for code editors. Effort: ~1 day.

7. **Add `ensureSyntaxTree` / `forceParsing` / `syntaxTreeAvailable`** -- These language module
   APIs are important for extensions that need synchronous access to the parse tree.
   Effort: ~1-2 days.

### Priority 3: Lower Value or Higher Effort

8. **Add `bidiIsolates`** from `@codemirror/language` -- Important for international users
   working with mixed LTR/RTL text. Effort: ~1 day.

9. **Port `layer` / `LayerMarker` / `RectangleMarker`** from view -- Low-level APIs used by
   advanced extensions. Effort: ~2 days.

10. **Consider `@codemirror/language-data`** -- Only if there is a use case for dynamic language
    switching in the target applications. Effort: ~2-3 days.

11. **Consider `@codemirror/lsp-client`** -- Only relevant if Kodemirror users need LSP integration.
    Would require a KMP-compatible transport layer. Effort: ~5+ days.

---

## Appendix: API Surface Size

Lines in `.api` dump files (proxy for public API surface size):

| Module | API Lines |
|---|---|
| `:state` | 953 |
| `:view` | 825 |
| `:language` | 304 |
| `:merge` | 285 |
| `:autocomplete` | 207 |
| `:search` | 130 |
| `:lint` | 119 |
| `:commands` | 107 |
| `:collab` | 61 |
| **Total core** | **2,991** |
