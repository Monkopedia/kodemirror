# Kodemirror Samples

Complete working sample projects demonstrating Kodemirror usage.

## Sample Editor

The `editor/` directory contains a Compose Desktop application with:

- JavaScript and Markdown language support
- Tab switching between languages and themes
- Dark theme (One Dark) and Material theme integration
- Basic setup with line numbers, undo/redo, bracket matching, etc.

### Running

```bash
JAVA_HOME=/usr/lib/jvm/java-21-openjdk ./gradlew :samples:editor:run
```

### Key files

- [`Main.kt`](editor/src/desktopMain/kotlin/com/monkopedia/kodemirror/samples/editor/Main.kt) — Application entry point with tab bar and editor panes
- [`build.gradle.kts`](editor/build.gradle.kts) — Dependencies and configuration

### Patterns demonstrated

- `rememberEditorSession(doc, extensions)` — Creating an editor session
- `KodeMirror(session, modifier)` — Rendering the editor
- `basicSetup + language()` — Combining extensions with `+`
- `rememberMaterialEditorTheme()` — Material Design integration
- `onChange { }` — Reacting to document changes
