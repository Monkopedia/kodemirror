# Guide

This guide covers the core concepts behind Kodemirror, a Kotlin
Multiplatform port of CodeMirror 6.

- [Getting Started](getting-started.md) — Step-by-step tutorial for
  creating your first Kodemirror editor
- [Architecture](architecture.md) — Module structure, dependency layers,
  platform targets, and how the design differs from upstream CodeMirror
- [Data Model](data-model.md) — Documents, changes, selections, facets,
  transactions, and editor state
- [The View](the-view.md) — The Compose rendering layer, theming,
  panels, tooltips, gutters, and input handling
- [Extending](extending.md) — State fields, view plugins, facets,
  compartments, decorations, and precedence
- [Extension Index](extensions-index.md) — Which module provides each
  common extension
- [Migrating from CodeMirror 6](migration.md) — Porting patterns,
  module mapping, and key API differences for JS developers
- [Extension Architecture](extension-architecture.md) — When to use
  StateField vs Facet vs ViewPlugin, ordering, and composition
- [Collaborative Editing](collaboration.md) — Real-time collaboration
  with operational transformation
- [Merge and Diff Views](merge.md) — Side-by-side and unified diff
  views for comparing documents
- [Performance](performance.md) — Virtualization, decorations, parser
  performance, and large document tips
- [Testing](testing.md) — Patterns for testing commands, state fields,
  completions, and linters
- [Language Module Guide](lang-module-guide.md) — How to create a
  `:lang-*` module for a new language
- [Troubleshooting](troubleshooting.md) — Common issues and solutions

See also the [API Reference](../reference/index.md) for generated
KDoc documentation of every module.
