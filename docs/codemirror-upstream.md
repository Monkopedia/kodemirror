# CodeMirror 6 - Upstream Project Reference

## Overall Architecture

CodeMirror 6 is a modular code editor for the web, authored primarily by Marijn Haverbeke. It follows a layered architecture:

1. **State Layer** (`@codemirror/state`) -- Immutable data structures representing the editor state, documents, transactions, selections, and a facet-based extension system.
2. **View Layer** (`@codemirror/view`) -- The DOM rendering component that displays the editor state and translates user interactions into state updates.
3. **Command Layer** (`@codemirror/commands`) -- Editing commands and keymaps that operate on the view.
4. **Infrastructure Packages** -- `@codemirror/language`, `@codemirror/autocomplete`, `@codemirror/search`, `@codemirror/lint`, `@codemirror/collab` provide cross-cutting features.
5. **Language Packages** (`@codemirror/lang-*`) -- Each language has its own package providing parsing (via Lezer grammars), syntax highlighting, and language-specific features.
6. **Convenience Package** (`codemirror` / `@codemirror/basic-setup`) -- Re-exports common packages as a single import.

Extensions compose via a **facet-based system** that deduplicates values and respects precedence ordering.

## Test Infrastructure

| Component | Tool |
|-----------|------|
| Test runner | `cm-runtests` from `@codemirror/buildhelper` |
| Underlying test tool | `@marijn/testtool` |
| Test framework | Mocha-style `describe`/`it` blocks |
| Assertion library | [`ist`](https://github.com/marijnh/ist) by Marijn Haverbeke |
| Node tests | Files matching `test/test-*.ts` |
| Browser tests | Files matching `test/webtest-*.ts` |
| Build tool | `cm-buildhelper` from `@codemirror/buildhelper` |

## All Repositories

All active packages are **MIT licensed** unless noted otherwise.

### Core Packages

| Repo | npm Package | Description |
|------|-------------|-------------|
| [`state`](https://github.com/codemirror/state) | `@codemirror/state` | Immutable editor state (EditorState, Transaction, Selection, Facets, StateFields) |
| [`view`](https://github.com/codemirror/view) | `@codemirror/view` | DOM view component (EditorView, decorations, DOM sync) |
| [`commands`](https://github.com/codemirror/commands) | `@codemirror/commands` | Editing commands and key bindings (defaultKeymap, history, commenting) |
| [`language`](https://github.com/codemirror/language) | `@codemirror/language` | Language support infrastructure (syntax trees, highlighting, folding, indentation) |
| [`autocomplete`](https://github.com/codemirror/autocomplete) | `@codemirror/autocomplete` | Autocompletion system |
| [`search`](https://github.com/codemirror/search) | `@codemirror/search` | Search and replace |
| [`lint`](https://github.com/codemirror/lint) | `@codemirror/lint` | Linting infrastructure |
| [`collab`](https://github.com/codemirror/collab) | `@codemirror/collab` | Collaborative editing (OT-based) |
| [`merge`](https://github.com/codemirror/merge) | `@codemirror/merge` | Side-by-side merge/diff view |
| [`lsp-client`](https://github.com/codemirror/lsp-client) | `@codemirror/lsp-client` | Language Server Protocol client integration |

### Convenience / Meta Packages

| Repo | npm Package | Description |
|------|-------------|-------------|
| [`basic-setup`](https://github.com/codemirror/basic-setup) | `codemirror` | Convenience package bundling common extensions |
| [`theme-one-dark`](https://github.com/codemirror/theme-one-dark) | `@codemirror/theme-one-dark` | One Dark editor theme |
| [`language-data`](https://github.com/codemirror/language-data) | `@codemirror/language-data` | Language metadata and dynamic loading for all lang-* packages |
| [`legacy-modes`](https://github.com/codemirror/legacy-modes) | `@codemirror/legacy-modes` | Ported legacy CM5 language modes |

### Language Packages

| Repo | npm Package | Description |
|------|-------------|-------------|
| [`lang-javascript`](https://github.com/codemirror/lang-javascript) | `@codemirror/lang-javascript` | JavaScript/TypeScript/JSX/TSX |
| [`lang-html`](https://github.com/codemirror/lang-html) | `@codemirror/lang-html` | HTML (with embedded JS/CSS) |
| [`lang-css`](https://github.com/codemirror/lang-css) | `@codemirror/lang-css` | CSS |
| [`lang-json`](https://github.com/codemirror/lang-json) | `@codemirror/lang-json` | JSON |
| [`lang-python`](https://github.com/codemirror/lang-python) | `@codemirror/lang-python` | Python |
| [`lang-markdown`](https://github.com/codemirror/lang-markdown) | `@codemirror/lang-markdown` | Markdown |
| [`lang-xml`](https://github.com/codemirror/lang-xml) | `@codemirror/lang-xml` | XML |
| [`lang-sql`](https://github.com/codemirror/lang-sql) | `@codemirror/lang-sql` | SQL (multiple dialects) |
| [`lang-rust`](https://github.com/codemirror/lang-rust) | `@codemirror/lang-rust` | Rust |
| [`lang-cpp`](https://github.com/codemirror/lang-cpp) | `@codemirror/lang-cpp` | C/C++ |
| [`lang-java`](https://github.com/codemirror/lang-java) | `@codemirror/lang-java` | Java |
| [`lang-php`](https://github.com/codemirror/lang-php) | `@codemirror/lang-php` | PHP (with embedded HTML) |
| [`lang-go`](https://github.com/codemirror/lang-go) | `@codemirror/lang-go` | Go |
| [`lang-sass`](https://github.com/codemirror/lang-sass) | `@codemirror/lang-sass` | Sass/SCSS |
| [`lang-less`](https://github.com/codemirror/lang-less) | `@codemirror/lang-less` | Less |
| [`lang-vue`](https://github.com/codemirror/lang-vue) | `@codemirror/lang-vue` | Vue templates |
| [`lang-angular`](https://github.com/codemirror/lang-angular) | `@codemirror/lang-angular` | Angular templates |
| [`lang-yaml`](https://github.com/codemirror/lang-yaml) | `@codemirror/lang-yaml` | YAML |
| [`lang-liquid`](https://github.com/codemirror/lang-liquid) | `@codemirror/lang-liquid` | Liquid templates |
| [`lang-jinja`](https://github.com/codemirror/lang-jinja) | `@codemirror/lang-jinja` | Jinja templates |
| [`lang-wast`](https://github.com/codemirror/lang-wast) | `@codemirror/lang-wast` | WebAssembly Text Format |
| [`lang-lezer`](https://github.com/codemirror/lang-lezer) | `@codemirror/lang-lezer` | Lezer grammar language |

### Tooling / Development

| Repo | Description |
|------|-------------|
| [`dev`](https://github.com/codemirror/dev) | Monorepo dev environment; clones all packages, dev server on port 8090 |
| [`buildhelper`](https://github.com/codemirror/buildhelper) | Build utility providing `cm-buildhelper` and `cm-runtests` binaries |
| [`lang-example`](https://github.com/codemirror/lang-example) | Template repo for building a language package (uses Mocha directly + Rollup) |
| [`website`](https://github.com/codemirror/website) | Source for codemirror.net |

### Legacy

| Repo | Description |
|------|-------------|
| [`codemirror5`](https://github.com/codemirror/codemirror5) | CodeMirror version 5 (legacy) |
| [`CodeMirror-v1`](https://github.com/codemirror/CodeMirror-v1) | Historical CodeMirror version 1 |

### Archived (Merged into Core)

These were separate packages in early CM6 but have been merged:

| Repo | Merged Into |
|------|-------------|
| `text` | `@codemirror/state` |
| `rangeset` | `@codemirror/state` |
| `history` | `@codemirror/commands` |
| `comment` | `@codemirror/commands` |
| `fold` | `@codemirror/language` |
| `closebrackets` | `@codemirror/autocomplete` |
| `rectangular-selection` | `@codemirror/view` |
| `stream-parser` | `@codemirror/language` |
| `highlight` | `@codemirror/language` (and `@lezer/highlight`) |
| `tooltip` | `@codemirror/view` |
| `matchbrackets` | `@codemirror/language` |
| `gutter` | `@codemirror/view` |
| `panel` | `@codemirror/view` |

## Dependency Graph (Simplified)

```
codemirror (basic-setup)
  |-- @codemirror/view
  |     |-- @codemirror/state
  |-- @codemirror/commands
  |     |-- @codemirror/state, @codemirror/view, @codemirror/language
  |-- @codemirror/language
  |     |-- @codemirror/state, @codemirror/view
  |     |-- @lezer/common, @lezer/highlight, @lezer/lr
  |-- @codemirror/autocomplete
  |     |-- @codemirror/state, @codemirror/view, @codemirror/language
  |-- @codemirror/search
  |     |-- @codemirror/state, @codemirror/view
  |-- @codemirror/lint
  |     |-- @codemirror/state, @codemirror/view

@codemirror/lang-* packages
  |-- @codemirror/language
  |-- @lezer/* (language-specific parser)

@codemirror/collab
  |-- @codemirror/state

@codemirror/merge
  |-- @codemirror/state, @codemirror/view, @codemirror/language
```
