# Getting Started

This tutorial walks through creating a minimal Kodemirror editor
from scratch in a Kotlin Multiplatform / Compose Multiplatform project.

## Prerequisites

- JDK 11 or later (JDK 21 recommended)
- Android Studio or IntelliJ IDEA with Kotlin plugin
- Gradle 8.x

## 1. Create a Compose Multiplatform project

Use the [Kotlin Multiplatform Wizard](https://kmp.jetbrains.com/) or
create a new Compose Multiplatform project in your IDE.

## 2. Add dependencies

In your module's `build.gradle.kts`, add the Kodemirror dependencies:

```kotlin
dependencies {
    // Core
    implementation("com.monkopedia.kodemirror:state:<version>")
    implementation("com.monkopedia.kodemirror:view:<version>")

    // Commands (undo, redo, indentation, etc.)
    implementation("com.monkopedia.kodemirror:commands:<version>")

    // Language support (pick the languages you need)
    implementation("com.monkopedia.kodemirror:lang-javascript:<version>")

    // Optional: theme
    implementation("com.monkopedia.kodemirror:theme-one-dark:<version>")

    // Optional: all-in-one setup bundle
    // implementation("com.monkopedia.kodemirror:basic-setup:<version>")
}
```

Replace `<version>` with the current release version.

## 3. Create a minimal editor

```kotlin
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.monkopedia.kodemirror.commands.*
import com.monkopedia.kodemirror.lang.javascript.javascript
import com.monkopedia.kodemirror.state.plus
import com.monkopedia.kodemirror.view.*

@Composable
fun MyEditor() {
    val session = rememberEditorSession(
        doc = "console.log(\"Hello, Kodemirror!\")",
        extensions = lineNumbers() +
            history() +
            javascript() +
            keymapOf(defaultKeymap + historyKeymap)
    )

    KodeMirror(
        session = session,
        modifier = Modifier.fillMaxSize()
    )
}
```

This gives you:
- A JavaScript-aware editor with syntax highlighting
- Line numbers in the gutter
- Undo/redo with Ctrl-Z / Ctrl-Shift-Z
- Standard cursor movement and selection commands

Or use `basicSetup` for a batteries-included experience:

```kotlin
import com.monkopedia.kodemirror.basicsetup.basicSetup
import com.monkopedia.kodemirror.lang.javascript.javascript
import com.monkopedia.kodemirror.state.plus
import com.monkopedia.kodemirror.view.*

@Composable
fun MyEditor() {
    val session = rememberEditorSession(
        doc = "console.log(\"Hello!\")",
        extensions = basicSetup + javascript()
    )
    KodeMirror(session = session, modifier = Modifier.fillMaxSize())
}
```

## 4. Understanding the pattern

Kodemirror uses `rememberEditorSession` to manage editor state internally:

1. **Session** — `rememberEditorSession` creates an `EditorSession` that holds the
   `EditorState` (document, selection, extension state) and handles updates.
2. **View** — `KodeMirror` renders the session as a Compose UI.
3. **Transactions** — when the user types or triggers a command, a `Transaction`
   is created with the changes and applied automatically.

```
User action → Transaction → EditorSession updates state → recomposition
```

Extensions are combined using the `+` operator:

```kotlin
val extensions = lineNumbers() + history() + javascript()
```

## 5. Adding more features

### Bracket matching

```kotlin
import com.monkopedia.kodemirror.language.bracketMatching

// Add to your extensions:
basicSetup + javascript() + bracketMatching()
```

### Autocompletion

```kotlin
import com.monkopedia.kodemirror.autocomplete.*

basicSetup + autocompletion(CompletionConfig(
    override = listOf(
        completeFromList(listOf(
            Completion(label = "console", type = "variable"),
            Completion(label = "document", type = "variable"),
            Completion(label = "window", type = "variable")
        ))
    )
))
```

### Search

```kotlin
import com.monkopedia.kodemirror.search.*

basicSetup + search()
```

### Custom theme

```kotlin
import com.monkopedia.kodemirror.view.*

val myTheme = EditorTheme(
    background = Color(0xFF1E1E1E),
    foreground = Color(0xFFD4D4D4),
    cursor = Color.White,
    selection = Color(0xFF264F78),
    dark = true
)

basicSetup + editorTheme.of(myTheme)
```

Or use the Material Design bridge for automatic theme integration:

```kotlin
import com.monkopedia.kodemirror.materialtheme.rememberMaterialEditorTheme

@Composable
fun MyEditor() {
    val materialTheme = rememberMaterialEditorTheme()
    val session = rememberEditorSession(
        doc = "Hello",
        extensions = basicSetup + materialTheme
    )
    KodeMirror(session = session)
}
```

This reads colors from `MaterialTheme.colorScheme` automatically and
adapts when switching between light and dark mode. Requires the
`com.monkopedia.kodemirror:material-theme` dependency.

### Read-only mode

```kotlin
basicSetup + EditorState.readOnly.of(true)
```

### `readOnly` vs `editable`

| | `EditorState.readOnly.of(true)` | `EditorSession.editable` (set to `false`) |
|---|---|---|
| **Editing** | Blocked | Blocked |
| **Selection** | Allowed | Blocked |
| **Copy** | Allowed | Blocked |
| **Use case** | Display code that users can select/copy | Fully inert display |

## 6. Reacting to changes

Use the `onChange` convenience to get notified when the document changes:

```kotlin
val session = rememberEditorSession(
    doc = "Hello",
    extensions = basicSetup + onChange { newText ->
        println("Document changed: $newText")
    }
)
```

For selection changes:

```kotlin
val session = rememberEditorSession(
    doc = "Hello",
    extensions = basicSetup + onSelection { selection ->
        println("Cursor at: ${selection.main.head}")
    }
)
```

## 7. Programmatic changes

Use the convenience methods on `EditorSession`:

```kotlin
// Replace entire document
session.setDoc("new content")

// Insert text at a position
session.insertAt(0, "// Header\n")

// Delete a range
session.deleteRange(from = 0, to = 10)

// Set cursor position
session.select(anchor = 5)

// Set selection range
session.select(anchor = 0, head = 10)

// Select all
session.selectAll()
```

For more complex changes, use `dispatch` with a transaction spec:

```kotlin
session.dispatch {
    insert(0, "Hello")
    selection(5)
    scrollIntoView()
}
```

## 8. Sample project

A complete working sample editor is available at
[`samples/editor/`](https://github.com/nicemonk/kodemirror/tree/main/samples/editor)
with tab switching between languages and themes.

## Next steps

- [Architecture](architecture.md) — understand the module structure
- [Data Model](data-model.md) — deep dive into documents and state
- [Extending](extending.md) — build custom extensions
- [Extension Index](extensions-index.md) — find available extensions
- [Examples](../examples/index.md) — see specific feature examples
