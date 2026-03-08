# Plan: Kodemirror Documentation Site

## Context

Kodemirror has no public documentation. The upstream CodeMirror 6 has comprehensive docs at codemirror.net (guide, examples, API reference) sourced from [codemirror/website](https://github.com/codemirror/website). Goal: create a user-facing docs site for kodemirror with a lightweight system guide, all 23 ported examples, and generated API reference. Deploy to GitHub Pages.

## Tooling

- **MkDocs Material** — guide pages, examples, landing page
- **Dokka** — API reference generated from KDoc comments
- **GitHub Actions** — build + deploy to GitHub Pages

Dokka can't create custom pages, so Dokka HTML is served as static files alongside the MkDocs output. The MkDocs `reference/index.md` page links into the Dokka output.

## Directory Structure

```
docs-site/
  mkdocs.yml
  requirements.txt              # mkdocs-material>=9.5, mkdocs-minify-plugin
  docs/
    index.md                    # Landing page
    guide/
      index.md                  # Overview with links
      architecture.md           # Architecture (modularity, state+updates, extensions)
      data-model.md             # Text, changes, selection, facets, transactions
      the-view.md               # Compose view layer (diverges most from upstream)
      extending.md              # StateField, ViewPlugin, decorations, extension arch
    examples/
      index.md                  # Example listing
      basic.md                  # Minimal editor setup
      bundle.md                 # Gradle setup (replaces upstream Rollup bundling)
      config.md                 # Dynamic configuration
      styling.md                # Themes and styling
      tab.md                    # Tab handling
      readonly.md               # Read-only mode
      bidi.md                   # Bidirectional text
      lang-package.md           # Writing a language package
      mixed-language.md         # Mixed-language parsing
      change.md                 # Programmatic document changes
      selection.md              # Selection handling
      decoration.md             # Decorations
      gutter.md                 # Gutters
      panel.md                  # Editor panels
      tooltip.md                # Tooltips
      inverted-effect.md        # Undoable effects
      autocompletion.md         # Autocompletion
      lint.md                   # Linting
      collab.md                 # Collaborative editing
      split.md                  # Split view
      million.md                # Huge documents
      translate.md              # Internationalization
      zebra.md                  # Zebra stripes extension
    reference/
      index.md                  # Links to Dokka output per module
    api/                        # Dokka HTML output (gitignored, generated at build)
```

Upstream `ie11` example dropped (irrelevant for Compose). Total: 23 examples.

## Files to Modify

### `gradle/libs.versions.toml`
Add Dokka version + plugin:
```toml
dokka = "2.1.0"
# ...
dokka = { id = "org.jetbrains.dokka", version.ref = "dokka" }
```

### `convention-plugins/build.gradle.kts`
Add Dokka plugin classpath (same pattern as existing plugins):
```kotlin
implementation(libs.plugins.dokka.get().let {
    "${it.pluginId}:${it.pluginId}.gradle.plugin:${it.version}"
})
```

### `convention-plugins/src/main/kotlin/kodemirror.library.gradle.kts`
Apply Dokka to all library modules:
```kotlin
plugins {
    // ... existing ...
    id("org.jetbrains.dokka")
}
```

### `build.gradle.kts` (root)
Add Dokka aggregation + copy task:
```kotlin
plugins {
    // ... existing ...
    alias(libs.plugins.dokka)
}

dependencies {
    subprojects.forEach { subproject ->
        kover(subproject)
        dokka(subproject)
    }
}

tasks.register<Copy>("copyApiDocs") {
    dependsOn(":dokkaGenerate")
    from(layout.buildDirectory.dir("dokka/html"))
    into(layout.projectDirectory.dir("docs-site/docs/api"))
}
```

### `.gitignore`
Add `docs-site/docs/api/`

### New: `.github/workflows/docs.yml`
Three-job pipeline:
1. **build-api-docs**: `./gradlew :dokkaGenerate`, upload artifact
2. **build-site**: download Dokka artifact into `docs-site/docs/api/`, run `mkdocs build`
3. **deploy**: deploy to GitHub Pages

Triggers: push to `main` (paths: `docs-site/**`, `**/src/commonMain/**`, workflow file), plus `workflow_dispatch`.

## Guide Content (Adapted for Kotlin/Compose)

Each section is a lightweight rewrite of the upstream guide, replacing TypeScript/DOM with Kotlin/Compose:

| Section | Key adaptations |
|---------|----------------|
| **Architecture** | Same module structure. "Composable Shell" replaces "Imperative Shell". Gradle coordinates. KMP targets. |
| **Data Model** | Nearly identical to upstream. Kotlin code examples for `Text`, `ChangeSet`, `EditorSelection`, `Facet`, `Transaction`. |
| **The View** | Most divergent. `KodeMirror` as `@Composable` entry point. `SpanStyle`/`EditorTheme` instead of CSS. |
| **Extending** | `StateField.define()`, `ViewPlugin.define()`, decorations via facets. Extension composition. |

## Example Porting Strategy

Each example is a markdown page with:
1. Brief intro (adapted from upstream prose)
2. Complete Kotlin code snippets
3. Explanatory text between code blocks
4. Attribution note linking to upstream equivalent

No runnable embedded examples (wasmJs is disabled in several modules). Code snippets only.

**Porting difficulty tiers:**
- **Straightforward** (API usage, minimal DOM): basic, config, tab, readonly, change, selection, inverted-effect, autocompletion, lint, collab, million, zebra, bundle
- **Moderate** (theme/styling adaptation): styling, decoration, gutter, panel, tooltip, translate
- **Significant rewrite** (DOM-heavy): bidi, split, lang-package, mixed-language

## Phasing

This is a large body of work. Recommended execution order:

### Phase 1: Infrastructure
Get the build pipeline working end-to-end with placeholder content.
1. Add Dokka to version catalog, convention plugins, root build
2. Verify `./gradlew :dokkaGenerate` works
3. Create `docs-site/` with `mkdocs.yml`, `requirements.txt`, minimal `index.md`
4. Create `reference/index.md` with per-module links
5. Add `copyApiDocs` task, `.gitignore` entry
6. Create `.github/workflows/docs.yml`
7. Verify: `./gradlew copyApiDocs && cd docs-site && mkdocs serve`

### Phase 2: System Guide (4 markdown files)
Port the guide sections with Kotlin/Compose examples.

### Phase 3: Core Examples (11 examples)
basic, config, styling, change, selection, decoration, autocompletion, lint, bundle, tab, readonly

### Phase 4: Remaining Examples (12 examples)
gutter, panel, tooltip, inverted-effect, zebra, lang-package, mixed-language, collab, split, million, bidi, translate

### Phase 5: KDoc Polish
Audit and improve KDoc coverage across public APIs for better Dokka output.

## Verification

```bash
# Dokka generates
./gradlew :dokkaGenerate

# MkDocs builds with Dokka output
./gradlew copyApiDocs
cd docs-site && mkdocs build

# Local preview
cd docs-site && mkdocs serve
# Open http://localhost:8000

# Full CI pipeline
# Push to main, verify GitHub Pages deployment
```
