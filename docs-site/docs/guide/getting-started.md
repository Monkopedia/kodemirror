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
}
```

Replace `<version>` with the current release version.

## 3. Create a minimal editor

```kotlin
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import com.monkopedia.kodemirror.commands.*
import com.monkopedia.kodemirror.lang.javascript.javascript
import com.monkopedia.kodemirror.state.*
import com.monkopedia.kodemirror.view.*

@Composable
fun MyEditor() {
    var editorState by remember {
        mutableStateOf(
            EditorState.create(
                doc = "console.log(\"Hello, Kodemirror!\")",
                extensions = extensionListOf(
                    lineNumbers(),
                    history(),
                    javascript(),
                    keymapOf(defaultKeymap + historyKeymap)
                )
            )
        )
    }

    EditorSession(
        state = editorState,
        onUpdate = { tr -> editorState = tr.state },
        modifier = Modifier.fillMaxSize()
    )
}
```

This gives you:
- A JavaScript-aware editor with syntax highlighting
- Line numbers in the gutter
- Undo/redo with Ctrl-Z / Ctrl-Shift-Z
- Standard cursor movement and selection commands

## 4. Understanding the pattern

Kodemirror follows the standard Compose state-hoisting pattern:

1. **State** — `EditorState` holds the document, selection, and all
   extension state. It's immutable.
2. **View** — `EditorSession` renders the state as a Compose UI.
3. **Transactions** — when the user types or triggers a command, a
   `Transaction` is created with the changes.
4. **Update** — the `onUpdate` callback receives the transaction.
   You apply the new state, Compose recomposes, and the editor updates.

```
User action → Transaction → onUpdate callback → new EditorState → recomposition
```

## 5. Adding more features

### Bracket matching

```kotlin
import com.monkopedia.kodemirror.language.bracketMatching

extensionListOf(
    // ... other extensions
    bracketMatching()
)
```

### Autocompletion

```kotlin
import com.monkopedia.kodemirror.autocomplete.*

extensionListOf(
    // ... other extensions
    autocompletion(CompletionConfig(
        override = listOf(
            completeFromList(listOf(
                Completion(label = "console", type = "variable"),
                Completion(label = "document", type = "variable"),
                Completion(label = "window", type = "variable")
            ))
        )
    ))
)
```

### Search

```kotlin
import com.monkopedia.kodemirror.search.*

extensionListOf(
    // ... other extensions
    search()
)
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

extensionListOf(
    // ... other extensions
    editorTheme.of(myTheme)
)
```

Or use the Material Design bridge:

```kotlin
val colors = MaterialTheme.colorScheme
val theme = editorThemeFromColors(
    background = colors.surface,
    foreground = colors.onSurface,
    primary = colors.primary,
    surface = colors.surfaceVariant,
    outline = colors.outline,
    dark = true
)
```

### Read-only mode

```kotlin
extensionListOf(
    // ... other extensions
    EditorState.readOnly.of(true)
)
```

## 6. Reacting to changes

To get notified when the document text changes:

```kotlin
EditorSession(
    state = editorState,
    onUpdate = { tr ->
        editorState = tr.state
        if (tr.docChanged) {
            val newText = tr.state.doc.toString()
            onTextChanged(newText)
        }
    }
)
```

## 7. Programmatic changes

To update the document from outside the editor:

```kotlin
fun insertText(view: EditorSession, text: String) {
    val pos = view.state.selection.main.head
    view.dispatch(TransactionSpec(
        changes = ChangeSpec.Single(
            from = pos,
            insert = text.asInsert()
        )
    ))
}
```

## Next steps

- [Architecture](architecture.md) — understand the module structure
- [Data Model](data-model.md) — deep dive into documents and state
- [Extending](extending.md) — build custom extensions
- [Extension Index](extensions-index.md) — find available extensions
- [Examples](../examples/index.md) — see specific feature examples
