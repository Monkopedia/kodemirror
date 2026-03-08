# Testing Guide

Patterns for testing editors, commands, state fields, and extensions.

## Testing StateField Logic

StateField update logic can be tested without any UI:

```kotlin
@Test
fun testWordCount() {
    val wordCount = StateField.define(StateFieldSpec(
        create = { state -> countWords(state) },
        update = { _, tr -> countWords(tr.state) }
    ))

    val state = EditorState.create(EditorStateConfig(
        doc = DocSpec.StringDoc("hello world"),
        extensions = wordCount
    ))

    assertEquals(2, state.field(wordCount))
}
```

## Testing Commands

Create an `EditorSession` and dispatch commands:

```kotlin
@Test
fun testDeleteLine() {
    val session = EditorSession(
        EditorState.create(EditorStateConfig(
            doc = DocSpec.StringDoc("line 1\nline 2\nline 3"),
            extensions = basicSetup
        ))
    )

    // Place cursor on line 2
    session.dispatch(TransactionSpec(
        selection = SelectionSpec.CursorSpec(7)
    ))

    // Run the command
    deleteLine(session)

    assertEquals("line 1\nline 3", session.state.sliceDoc())
}
```

## Testing with EditorSession Convenience Methods

The convenience methods make test setup concise:

```kotlin
@Test
fun testInsertAt() {
    val session = EditorSession(
        EditorState.create(EditorStateConfig(
            doc = DocSpec.StringDoc("hello world")
        ))
    )

    session.insertAt(5, " beautiful")
    assertEquals("hello beautiful world", session.state.sliceDoc())
}
```

## Testing Custom Completion Sources

```kotlin
@Test
fun testMyCompletionSource() {
    val source: CompletionSource = { ctx ->
        val match = ctx.matchBefore(Regex("[a-z]+"))
        if (match != null) {
            CompletionResult(
                from = match.from,
                options = listOf(
                    Completion(label = "hello"),
                    Completion(label = "help")
                )
            )
        } else null
    }

    val state = EditorState.create(EditorStateConfig(
        doc = DocSpec.StringDoc("hel")
    ))
    val ctx = CompletionContext(state, pos = 3, explicit = true)
    val result = source(ctx)

    assertNotNull(result)
    assertEquals(2, result.options.size)
    assertEquals(0, result.from)
}
```

## Testing Linter Implementations

```kotlin
@Test
fun testMyLinter() {
    val myLinter: (EditorSession) -> List<Diagnostic> = { session ->
        val text = session.state.sliceDoc()
        val diagnostics = mutableListOf<Diagnostic>()
        val pattern = Regex("TODO")
        for (match in pattern.findAll(text)) {
            diagnostics.add(Diagnostic(
                from = match.range.first,
                to = match.range.last + 1,
                message = "Unresolved TODO",
                severity = Severity.WARNING
            ))
        }
        diagnostics
    }

    val session = EditorSession(
        EditorState.create(EditorStateConfig(
            doc = DocSpec.StringDoc("// TODO fix this")
        ))
    )
    val diagnostics = myLinter(session)
    assertEquals(1, diagnostics.size)
    assertEquals("Unresolved TODO", diagnostics[0].message)
}
```

## Test Fixtures

Create reusable helpers for common test setups:

```kotlin
fun testSession(
    doc: String = "",
    extensions: Extension? = null
): EditorSession = EditorSession(
    EditorState.create(EditorStateConfig(
        doc = DocSpec.StringDoc(doc),
        extensions = extensions
    ))
)

// Usage:
val session = testSession("hello world", extensions = basicSetup)
```

## Integration Testing with KodeMirror

For Compose UI tests, use `ComposeTestRule`:

```kotlin
@Test
fun testEditorRenders() = runComposeUiTest {
    setContent {
        val session = rememberEditorSession(doc = "test content")
        KodeMirror(session, modifier = Modifier.fillMaxSize())
    }

    // Verify the editor rendered
    onNodeWithText("test content").assertExists()
}
```

## Tips

- **Prefer unit tests** for StateField logic and commands — they're
  fast and don't need UI.
- **Use `EditorSession` directly** (not `rememberEditorSession`) in
  tests — it doesn't require a Compose context.
- **Test edge cases**: empty documents, single characters, very long
  lines, multi-byte characters, and multiple selections.
