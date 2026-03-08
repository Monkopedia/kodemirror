# Internationalization

Kodemirror supports translating UI strings through the `phrases` facet
and the `phrase()` method. This lets you localize built-in messages
(tooltips, ARIA labels, panel headings, etc.) without forking the
source.

## The phrases facet

Register translations with `EditorState.phrases`. The facet maps
English source strings to their translations:

```kotlin
import com.monkopedia.kodemirror.state.EditorState
import com.monkopedia.kodemirror.state.EditorStateConfig

val germanPhrases = EditorState.phrases.of(mapOf(
    "Find" to "Suchen",
    "Replace" to "Ersetzen",
    "next" to "nächste",
    "previous" to "vorherige",
    "Replace all" to "Alle ersetzen",
    "close" to "schließen"
))

val state = EditorState.create(EditorStateConfig(
    extensions = germanPhrases
))
```

## Looking up translations

Use `state.phrase()` (or `view.phrase()`) to look up a translated
string. If no translation is registered the original English string is
returned unchanged:

```kotlin
val label = state.phrase("Find")
// Returns "Suchen" with the German phrases above,
// or "Find" if no translation is registered.
```

### Variable substitution

Phrases support positional placeholders (`$1`, `$2`, etc.):

```kotlin
val msg = state.phrase("Change from $1 to $2", "tabs", "spaces")
// With a matching translation:
//   "Änderung von $1 zu $2" -> "Änderung von tabs zu spaces"
// Without:
//   "Change from tabs to spaces"
```

Use `$$` to produce a literal dollar sign in translated text.

## EditorSession.phrase

For convenience, `EditorSession` exposes the same method, delegating to the
current state:

```kotlin
val translated = view.phrase("Replace all")
```

This is useful inside view plugins and commands that have access to the
view but not directly to the state.

## Complete example

```kotlin
import com.monkopedia.kodemirror.state.*
import com.monkopedia.kodemirror.view.*

fun frenchEditor(): EditorSession {
    val french = EditorState.phrases.of(mapOf(
        "Find" to "Chercher",
        "Replace" to "Remplacer",
        "next" to "suivant",
        "previous" to "précédent",
        "all" to "tout",
        "match case" to "respecter la casse",
        "close" to "fermer",
        "Fold line" to "Plier la ligne",
        "Unfold line" to "Déplier la ligne"
    ))

    val state = EditorState.create(EditorStateConfig(
        doc = "Bonjour le monde\n",
        extensions = french
    ))

    return EditorSession(state)
}
```

## Key points

- **`EditorState.phrases`** is a facet mapping English keys to
  translated values.
- **`state.phrase()`** and **`view.phrase()`** perform the lookup.
- Placeholders `$1`, `$2`, ... are replaced with the extra arguments.
- Extensions that display user-facing text should use `phrase()` so
  their strings are translatable.

---

*Based on the [CodeMirror Internationalization example](https://codemirror.net/examples/translate/).*
