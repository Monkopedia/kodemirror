# Writing a Language Package

This example shows how to define a language for Kodemirror, either using
a Lezer grammar (for tree-sitter-style parsing) or a `StreamParser` (for
simpler line-by-line tokenization).

## Language and LanguageSupport

A language package typically exports a `LanguageSupport` that bundles the
language definition with supporting extensions (autocompletion, folding,
etc.):

```kotlin
import com.monkopedia.kodemirror.language.*

fun myLanguage(): LanguageSupport {
    return LanguageSupport(
        language = myLang,
        support = myCompletions  // optional supporting extension
    )
}
```

Install it with:

```kotlin
val state = EditorState.create(EditorStateConfig(
    extensions = myLanguage().extension
))
```

## Defining a language with a Lezer parser

If you have a Lezer grammar, create an `LRLanguage`:

```kotlin
import com.monkopedia.kodemirror.language.LRLanguage

val myLang = LRLanguage.define(
    parser = myParser,  // a com.monkopedia.kodemirror.lezer.common.Parser
    name = "myLang"
)
```

## Defining a language with StreamParser

For simpler languages, implement `StreamParser` for line-by-line
tokenization:

```kotlin
import com.monkopedia.kodemirror.language.*

data class MyState(
    val inString: Boolean = false
)

val myStreamParser = object : StreamParser<MyState> {
    override val name = "myLang"

    override fun startState(indentUnit: Int) = MyState()

    override fun token(stream: StringStream, state: MyState): String? {
        if (stream.eatSpace()) return null

        // Keywords
        val word = stream.match(Regex("\\w+"))
        if (word != null) {
            return when (word.value) {
                "fun", "val", "var", "class" -> "keyword"
                "true", "false" -> "bool"
                else -> "variableName"
            }
        }

        // String literals
        if (stream.match("\"") != null) {
            while (!stream.eol()) {
                if (stream.next() == "\"") break
            }
            return "string"
        }

        // Comments
        if (stream.match("//") != null) {
            stream.skipToEnd()
            return "comment"
        }

        stream.next()
        return null
    }

    override fun copyState(state: MyState) = state.copy()
}

val myLang = StreamLanguage.define(myStreamParser)
```

## StreamParser interface

| Method | Description |
|--------|-------------|
| `startState(indentUnit)` | Create the initial parser state |
| `token(stream, state)` | Consume one token, return its type (or `null`) |
| `copyState(state)` | Deep-copy the parser state |
| `blankLine(state, indentUnit)` | Called for empty lines |
| `indent(state, textAfter, context)` | Compute indentation for a line |

Token types are standard CodeMirror highlighting tags: `"keyword"`,
`"variableName"`, `"string"`, `"comment"`, `"number"`, `"operator"`, etc.

## StringStream

The `StringStream` class provides methods for consuming input:

| Method | Description |
|--------|-------------|
| `next()` | Consume and return the next character |
| `peek()` | Look at the next character without consuming |
| `eat(ch)` / `eat(regex)` | Consume if matching |
| `eatWhile(ch)` / `eatWhile(regex)` | Consume while matching |
| `eatSpace()` | Skip whitespace |
| `match(string, consume, caseInsensitive)` | Match a string, returns `Boolean` |
| `match(regex, consume)` | Match a regex, returns `MatchResult?` |
| `skipToEnd()` | Move to end of line |
| `skipTo(ch)` | Skip to a character |
| `current()` | Get the text consumed since `start` |
| `eol()` / `sol()` | At end/start of line? |
| `column()` | Current column |

!!! warning "match() return types"
    `stream.match(String)` returns `Boolean`. `stream.match(Regex)` returns
    `MatchResult?`. Don't use `!= null` on the string overload.

## Adding indentation

Provide an `indent` method in your `StreamParser`:

```kotlin
override fun indent(state: MyState, textAfter: String, context: IndentContext): Int? {
    // Return the number of spaces for indentation, or null for default
    return null
}
```

For tree-based languages, use `indentNodeProp`:

```kotlin
val indentNodeProp: NodeProp<(TreeIndentContext) -> Int?>
```

Built-in strategies:

- `delimitedIndent()` — indent between matching delimiters
- `continuedIndent()` — indent continuation lines
- `flatIndent` — no indentation change

## Adding code folding

Use `foldNodeProp` to define foldable regions:

```kotlin
val foldNodeProp: NodeProp<(SyntaxNode, EditorState) -> FoldRange?>
```

The `foldInside(node)` helper returns a `FoldRange` for the region
between a node's first and last children (useful for brace-delimited
blocks).

## Using legacy modes

The `:legacy-modes` module provides over 100 languages ported from
CodeMirror 5. Each is a `StreamParser` that you wrap with
`StreamLanguage.define()`:

```kotlin
import com.monkopedia.kodemirror.legacy.modes.python

val pythonLanguage = StreamLanguage.define(python)
```

---

*Based on the [CodeMirror Language Package example](https://codemirror.net/examples/lang-package/).*
