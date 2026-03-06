# Autocompletion

The `:autocomplete` module provides a completion dropdown that appears
as the user types.

## Basic setup

Enable autocompletion by adding the `autocompletion()` extension:

```kotlin
import com.monkopedia.kodemirror.autocomplete.*

val state = EditorState.create(EditorStateConfig(
    extensions = ExtensionList(listOf(
        autocompletion(),
        // ...
    ))
))
```

## Custom completion source

A `CompletionSource` is a function that takes a `CompletionContext` and
returns a `CompletionResult` (or `null` if no completions apply):

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
                Completion(
                    label = "println",
                    type = "function",
                    info = "Print to standard output"
                )
            )
        )
    }
}
```

## Registering a completion source

Pass your sources through `CompletionConfig.override`:

```kotlin
autocompletion(CompletionConfig(
    override = listOf(myCompletions)
))
```

## Completing from a list

The `completeFromList` helper creates a source from a static list of
completions:

```kotlin
val source = completeFromList(listOf(
    Completion(label = "fun", type = "keyword"),
    Completion(label = "val", type = "keyword"),
    Completion(label = "var", type = "keyword"),
    Completion(label = "class", type = "keyword"),
    Completion(label = "object", type = "keyword")
))

autocompletion(CompletionConfig(override = listOf(source)))
```

## Completion properties

Each `Completion` has:

| Property | Description |
|----------|-------------|
| `label` | The text shown in the dropdown and inserted on accept |
| `displayLabel` | Alternative display text (optional) |
| `detail` | Short detail text shown next to the label |
| `info` | Longer description |
| `type` | Category string (e.g. "keyword", "function", "variable") |
| `boost` | Priority adjustment (higher = shown first) |
| `apply` | Override text to insert (if different from label) |
| `section` | Group completions under a section header |

## Configuration

`CompletionConfig` controls behavior:

```kotlin
CompletionConfig(
    activateOnTyping = true,     // show completions as user types
    selectOnOpen = true,         // pre-select first option
    maxRenderedOptions = 100,    // max items in dropdown
    override = listOf(source)   // custom completion sources
)
```

## Commands

| Command | Default binding | Action |
|---------|----------------|--------|
| `startCompletion` | Ctrl-Space | Open completion dropdown |
| `acceptCompletion` | Enter | Accept selected completion |
| `closeCompletion` | Escape | Close dropdown |

---

*Based on the [CodeMirror Autocompletion example](https://codemirror.net/examples/autocompletion/).*
