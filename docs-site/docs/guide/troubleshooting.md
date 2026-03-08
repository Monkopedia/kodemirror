# Troubleshooting

Common issues and solutions when working with Kodemirror.

## Build issues

### Java version errors

Kodemirror requires Java 11 or higher. If you see errors about
unsupported class file versions or missing APIs, check your Java
version:

```bash
java -version
```

For building from source, Java 21 is recommended:

```bash
export JAVA_HOME=/path/to/java-21
./gradlew build
```

### Compose compiler version mismatch

If you see errors about incompatible Compose compiler versions, make
sure your project's Kotlin and Compose plugin versions are compatible
with the versions Kodemirror was built against. Check the
`gradle/libs.versions.toml` in the Kodemirror repository for the
expected versions.

### wasmJs test failures

The wasmJs test environment requires `skiko.mjs` which may not be
available in all setups. wasmJs tests are disabled by default in
Kodemirror's build. If you're adding a new module, include this in
your `build.gradle.kts`:

```kotlin
tasks.configureEach {
    if ("wasmJs" in name || "WasmJs" in name) enabled = false
}
```

## Runtime issues

### "Field is not present in this state"

This error occurs when you try to read a `StateField` that wasn't
included in the editor's extensions. Make sure the field (or the
extension that provides it) is in your `EditorStateConfig.extensions`:

```kotlin
val myField = StateField.define(StateFieldSpec(
    create = { 0 },
    update = { v, tr -> v }
))

// Include the field in extensions
val session = rememberEditorSession(
    doc = "...",
    extensions = myField + // ... other extensions
)

// Now this works
state.field(myField)
```

### Editor not rendering

If the editor composable appears blank:

1. Verify you have at least `:state` and `:view` dependencies
2. Check that your `EditorState` is created before the composable
   renders
3. Make sure the editor has non-zero size constraints in your layout

### Decorations not appearing

Decorations must be added in ascending position order. If you build
a `RangeSetBuilder` with positions out of order, the decorations
will be silently dropped:

```kotlin
val builder = RangeSetBuilder<Decoration>()
// Positions must be ascending
builder.add(from = 5, to = 10, value = deco1)
builder.add(from = 15, to = 20, value = deco2)  // OK: 15 > 10
// builder.add(from = 3, to = 8, value = deco3) // BAD: 3 < 15
```

### Changes not being applied

If `session.dispatch(TransactionSpec(...))` doesn't seem to do anything:

1. Check that your `ChangeSpec` positions are within the document
   length
2. Verify the editor is not in read-only mode
   (`readOnly.of(true)` in extensions)
3. Ensure change filters aren't rejecting the change

### No syntax highlighting

The editor works without a language extension, but won't highlight
anything. Make sure you include a language:

```kotlin
val session = rememberEditorSession(
    doc = code,
    extensions = basicSetup + javascript().extension
)
```

If using `basicSetup`, syntax highlighting infrastructure is included
but you still need a language extension. Without one, all text renders
in the default foreground color.

### Editor not responding to key events

If typing works but shortcuts (Ctrl+Z, Ctrl+C, etc.) don't:

1. Make sure you include a keymap extension. `basicSetup` includes
   `defaultKeymap` — if you're building your own setup, add:
   ```kotlin
   keymapOf(defaultKeymap)
   ```
2. Check that another composable isn't intercepting key events before
   the editor.

### Theme colors not applying

There are two separate styling systems:

- **`EditorTheme`** controls UI colors (background, gutter, cursor,
  panels, etc.) via the `editorTheme` facet.
- **`HighlightStyle`** controls syntax token colors (keywords,
  strings, comments, etc.) via `syntaxHighlighting()`.

A theme module like `oneDark` bundles both. If you only set
`editorTheme.of(myTheme)`, syntax tokens use the default highlight
style. Add `syntaxHighlighting(myHighlightStyle)` as well:

```kotlin
val session = rememberEditorSession(
    doc = code,
    extensions = basicSetup + editorTheme.of(myTheme) +
        syntaxHighlighting(myHighlightStyle)
)
```

### Completion not showing

If autocompletion doesn't appear when you type:

1. Add the `autocompletion()` extension (included in `basicSetup`).
2. Provide a completion source — either language-specific (e.g.,
   `javascript().extension` includes JS completions) or custom:
   ```kotlin
   autocompletion(AutocompletionConfig(
       override = listOf { context ->
           // Return CompletionResult or null
       }
   ))
   ```
3. Check `activateOnTyping` — by default, completions activate on
   typing. If disabled, use Ctrl+Space.

### readOnly vs editable

Two extensions control editing, but they behave differently:

| Extension | Typing blocked | Selection allowed | Copy allowed | Focus allowed |
|-----------|:-:|:-:|:-:|:-:|
| `readOnly.of(true)` | Yes | Yes | Yes | Yes |
| `editable.of(false)` | Yes | No | No | No |

Use `readOnly` for display-with-interaction (users can select and
copy). Use `editable.of(false)` for fully inert display.

### Extensions not taking effect

If an extension seems to have no effect:

1. **Check registration.** The extension must be included in
   `EditorStateConfig.extensions` (or via `rememberEditorSession`'s
   `extensions` parameter).
2. **Check precedence.** Later extensions override earlier ones for
   facets using `"last wins"` combine (like `editorTheme`). Use
   `Prec.highest(ext)` to force priority.
3. **Check compartments.** If an extension is inside a
   `Compartment`, changes require `compartment.reconfigure(newExt)`.
4. **Check that it's the right facet.** Some names are similar
   (e.g., `readOnly` vs `editable`, `onChange` vs `updateListener`).

### State not updating after dispatch

`EditorState` is immutable. Dispatching a transaction creates a
*new* state — it doesn't mutate the existing one:

```kotlin
val oldState = session.state
session.dispatch(TransactionSpec(
    changes = ChangeSpec.Single(0, 0, InsertContent.StringContent("hi"))
))
val newState = session.state  // Different from oldState
```

If you're holding a reference to the state, it becomes stale after
dispatch. Always read `session.state` for the current state.

### Undo not working

Undo/redo requires the `history()` extension:

```kotlin
val session = rememberEditorSession(
    doc = "...",
    extensions = basicSetup  // Includes history()
)
```

If building your own setup without `basicSetup`, add `history()`
explicitly. Without it, `undo` and `redo` commands do nothing.

### Editor blank on first render

If the editor flashes blank before showing content:

1. Make sure the editor has explicit size constraints. A
   `Modifier.fillMaxSize()` or fixed height prevents zero-size
   issues.
2. The first render may occur before the layout pass completes.
   This is normal — the content should appear within one frame.
