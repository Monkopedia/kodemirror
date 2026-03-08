# Tab Handling

By default, the Tab key moves focus to the next UI element (standard
Compose behavior). You can override this to indent code instead.

## Indent with Tab

The `:commands` module provides `indentWithTab`, a key binding list
that maps Tab to `indentMore` and Shift-Tab to `indentLess`:

```kotlin
import com.monkopedia.kodemirror.commands.indentWithTab
import com.monkopedia.kodemirror.view.keymapOf

// Add to your extensions:
basicSetup + keymapOf(*indentWithTab.toTypedArray())
```

## What indentWithTab does

`indentWithTab` is defined as:

```kotlin
val indentWithTab: List<KeyBinding> = listOf(
    KeyBinding(key = "Tab", run = indentMore),
    KeyBinding(key = "Shift-Tab", run = indentLess)
)
```

- **Tab** — calls `indentMore`, which increases indentation of the
  selected lines
- **Shift-Tab** — calls `indentLess`, which decreases indentation

## Insert literal tab

If you want Tab to insert a tab character instead of adjusting
indentation, use `insertTab`:

```kotlin
import com.monkopedia.kodemirror.commands.insertTab

keymapOf(
    KeyBinding(key = "Tab", run = insertTab)
)
```

## Accessibility note

Capturing Tab prevents keyboard-only users from tabbing out of the
editor. Consider providing an alternative escape mechanism (e.g., Escape
to release focus) if your editor is part of a larger form.

## Related API

- [`KeyBinding`](/api/view/com.monkopedia.kodemirror.view/-key-binding/) — key binding data class
- [`keymapOf`](/api/view/com.monkopedia.kodemirror.view/keymap-of.html) — create keymap extension
- [`indentWithTab`](/api/commands/com.monkopedia.kodemirror.commands/indent-with-tab.html) — tab indentation binding

---

*Based on the [CodeMirror Tab Handling example](https://codemirror.net/examples/tab/).*
