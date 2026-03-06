# Dynamic Configuration

Extensions are normally fixed when you create an `EditorState`. To
change configuration at runtime — switch languages, toggle features,
change themes — use `Compartment`.

## Compartments

A `Compartment` wraps part of your extension configuration so it can be
replaced later via an effect:

```kotlin
import com.monkopedia.kodemirror.state.*
import com.monkopedia.kodemirror.lang.javascript.javascript
import com.monkopedia.kodemirror.lang.python.python

val languageCompartment = Compartment()

val state = EditorState.create(EditorStateConfig(
    doc = "print('hello')".asDoc(),
    extensions = ExtensionList(listOf(
        languageCompartment.of(python()),
        // ... other extensions
    ))
))
```

## Switching configuration

To reconfigure at runtime, dispatch a transaction with the compartment's
`reconfigure` effect:

```kotlin
// Switch from Python to JavaScript
view.dispatch(TransactionSpec(
    effects = listOf(
        languageCompartment.reconfigure(javascript())
    )
))
```

The editor re-parses the document with the new language and updates
highlighting immediately.

## Toggle example

A compartment can also wrap a feature you want to enable/disable:

```kotlin
val lineNumberCompartment = Compartment()

// Start with line numbers on
val state = EditorState.create(EditorStateConfig(
    extensions = ExtensionList(listOf(
        lineNumberCompartment.of(lineNumbers),
        // ...
    ))
))

// Toggle off — replace with an empty extension list
fun toggleLineNumbers(view: EditorView, enabled: Boolean) {
    view.dispatch(TransactionSpec(
        effects = listOf(
            lineNumberCompartment.reconfigure(
                if (enabled) lineNumbers
                else ExtensionList(emptyList())
            )
        )
    ))
}
```

## Theme switching

Themes work the same way:

```kotlin
import com.monkopedia.kodemirror.view.*
import com.monkopedia.kodemirror.themonedark.oneDark

val themeCompartment = Compartment()

val state = EditorState.create(EditorStateConfig(
    extensions = ExtensionList(listOf(
        themeCompartment.of(oneDark),
        // ...
    ))
))

// Switch to light theme
fun switchToLight(view: EditorView) {
    view.dispatch(TransactionSpec(
        effects = listOf(
            themeCompartment.reconfigure(editorTheme.of(lightEditorTheme))
        )
    ))
}
```

## Reading current configuration

You can query a compartment's current content:

```kotlin
val currentLanguage = languageCompartment.get(state)
```

## Related API

- [`Compartment`](/api/state/com.monkopedia.kodemirror.state/-compartment/) — dynamic extension reconfiguration
- [`Extension`](/api/state/com.monkopedia.kodemirror.state/-extension/) — base extension type
- [`Facet`](/api/state/com.monkopedia.kodemirror.state/-facet/) — facet system for extension composition
- [`StateEffect`](/api/state/com.monkopedia.kodemirror.state/-state-effect/) — state effect for reconfiguration

---

*Based on the [CodeMirror Configuration example](https://codemirror.net/examples/config/).*
