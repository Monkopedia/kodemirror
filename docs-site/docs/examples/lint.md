# Linting

The `:lint` module displays diagnostics (errors, warnings, hints) as
inline decorations and in an optional panel.

## Basic setup

Create a `linter` extension with a function that returns diagnostics:

```kotlin
import com.monkopedia.kodemirror.lint.*

val myLinter = linter { view ->
    val diagnostics = mutableListOf<Diagnostic>()
    val doc = view.state.doc

    // Example: flag lines longer than 80 characters
    for (i in 1..doc.lines) {
        val line = doc.line(i)
        if (line.length > 80) {
            diagnostics.add(Diagnostic(
                from = line.from + 80,
                to = line.to,
                severity = Severity.WARNING,
                message = "Line exceeds 80 characters"
            ))
        }
    }

    diagnostics
}

val state = EditorState.create(EditorStateConfig(
    extensions = ExtensionList(listOf(
        myLinter,
        // ...
    ))
))
```

## Diagnostic properties

Each `Diagnostic` has:

| Property | Description |
|----------|-------------|
| `from` | Start position |
| `to` | End position |
| `severity` | `Severity.HINT`, `INFO`, `WARNING`, or `ERROR` |
| `message` | The diagnostic message |
| `source` | Optional source identifier (e.g. "eslint") |
| `actions` | Optional list of fix actions |
| `markClass` | Optional CSS class override for the mark decoration |

## Severity levels

```kotlin
enum class Severity { HINT, INFO, WARNING, ERROR }
```

Each severity level gets a different underline style in the editor.

## Lint configuration

`LintConfig` controls how the linter runs:

```kotlin
linter(myLintSource, LintConfig(
    delay = 750,        // milliseconds before re-running after changes
    autoPanel = false   // automatically open the diagnostics panel
))
```

## Lint gutter

Show severity icons in the gutter:

```kotlin
lintGutter()
```

## Diagnostic actions

Actions offer quick fixes that users can apply:

```kotlin
Diagnostic(
    from = pos,
    to = pos + word.length,
    severity = Severity.ERROR,
    message = "'$word' is misspelled",
    actions = listOf(
        Action(name = "Fix") { view ->
            view.dispatch(TransactionSpec(
                changes = ChangeSpec.Single(
                    pos, pos + word.length,
                    insert = InsertContent.StringContent(corrected)
                )
            ))
        }
    )
)
```

## Programmatic diagnostics

You can also push diagnostics without a linter function:

```kotlin
forceLinting(view)
```

## Commands

| Command | Default binding | Action |
|---------|----------------|--------|
| `openLintPanel` | — | Open the diagnostics panel |
| `closeLintPanel` | — | Close the diagnostics panel |
| `nextDiagnostic` | F8 | Jump to next diagnostic |

## Related API

- [`Diagnostic`](/api/lint/com.monkopedia.kodemirror.lint/-diagnostic/) — diagnostic data class
- [`Severity`](/api/lint/com.monkopedia.kodemirror.lint/-severity/) — diagnostic severity enum
- [`Action`](/api/lint/com.monkopedia.kodemirror.lint/-action/) — quick fix action
- [`LintConfig`](/api/lint/com.monkopedia.kodemirror.lint/-lint-config/) — linter configuration
- [`linter`](/api/lint/com.monkopedia.kodemirror.lint/linter.html) — create a linter extension
- [`lintGutter`](/api/lint/com.monkopedia.kodemirror.lint/lint-gutter.html) — show severity icons in gutter

---

*Based on the [CodeMirror Lint example](https://codemirror.net/examples/lint/).*
