# Basic Setup

A minimal Kodemirror editor with common features enabled.

## Minimal editor

The simplest possible editor needs an `EditorState` and the `EditorView`
composable:

```kotlin
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import com.monkopedia.kodemirror.state.*
import com.monkopedia.kodemirror.view.EditorView

@Composable
fun MinimalEditor() {
    var state by remember {
        mutableStateOf(
            EditorState.create(EditorStateConfig(
                doc = "Hello, world!".asDoc()
            ))
        )
    }

    EditorView(
        state = state,
        onUpdate = { tr -> state = tr.state },
        modifier = Modifier.fillMaxSize()
    )
}
```

This gives you an editable text area, but without line numbers, syntax
highlighting, or keybindings.

## Adding common extensions

A more useful editor adds line numbers, a keymap, undo history,
bracket matching, and syntax highlighting:

```kotlin
import com.monkopedia.kodemirror.commands.*
import com.monkopedia.kodemirror.language.*
import com.monkopedia.kodemirror.lang.javascript.javascript
import com.monkopedia.kodemirror.search.highlightSelectionMatches
import com.monkopedia.kodemirror.view.*

@Composable
fun FullEditor() {
    var state by remember {
        mutableStateOf(
            EditorState.create(EditorStateConfig(
                doc = "function hello() {\n  return \"world\"\n}\n".asDoc(),
                extensions = ExtensionList(listOf(
                    lineNumbers,
                    highlightActiveLine,
                    highlightSpecialChars,
                    history(),
                    bracketMatching(),
                    highlightSelectionMatches(),
                    defaultKeymapExtension(),
                    keymapOf(*indentWithTab.toTypedArray()),
                    javascript(),
                    syntaxHighlighting(defaultHighlightStyle)
                ))
            ))
        )
    }

    EditorView(
        state = state,
        onUpdate = { tr -> state = tr.state },
        modifier = Modifier.fillMaxSize()
    )
}
```

Each function call returns an `Extension` that plugs into the editor's
configuration. Extensions compose freely — you can add or remove any of
them independently.

## What each extension does

| Extension | Module | Purpose |
|-----------|--------|---------|
| `lineNumbers` | `:view` | Shows line numbers in the gutter |
| `highlightActiveLine` | `:view` | Highlights the line the cursor is on |
| `highlightSpecialChars` | `:view` | Makes control characters visible |
| `history()` | `:commands` | Enables undo/redo (Ctrl-Z / Ctrl-Shift-Z) |
| `bracketMatching()` | `:language` | Highlights matching brackets |
| `highlightSelectionMatches()` | `:search` | Highlights other occurrences of the selected text |
| `defaultKeymapExtension()` | `:commands` | Standard cursor movement and editing bindings |
| `keymapOf(*indentWithTab.toTypedArray())` | `:commands` | Tab/Shift-Tab for indentation |
| `javascript()` | `:lang-javascript` | JavaScript language support (parsing, highlighting, indentation) |
| `syntaxHighlighting(defaultHighlightStyle)` | `:language` | Applies colors to syntax tokens |

---

*Based on the [CodeMirror Basic Setup example](https://codemirror.net/examples/bundle/).*
