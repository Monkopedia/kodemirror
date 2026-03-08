# Mixed-Language Parsing

Some documents contain multiple languages — for example, HTML with
embedded JavaScript and CSS. Kodemirror supports this through the
`parseMixed` wrapper.

## parseMixed

The `parseMixed` function wraps a parser to scan its tree for regions
that should be parsed by a different language:

```kotlin
import com.monkopedia.kodemirror.lezer.common.*

val mixedParser = parseMixed { nodeRef, input ->
    when (nodeRef.type.name) {
        "ScriptContent" -> NestedParse(
            parser = javascriptParser,
            overlay = null  // parse the entire node
        )
        "StyleContent" -> NestedParse(
            parser = cssParser,
            overlay = null
        )
        else -> null
    }
}
```

## NestedParse

```kotlin
class NestedParse(
    val parser: Parser,
    val overlay: ParseOverlay? = null,
    val bracketed: Boolean = false
)

sealed interface ParseOverlay {
    data class Ranges(val ranges: List<TextRange>) : ParseOverlay
    data class Predicate(val match: (SyntaxNodeRef) -> ParseOverlayMatch?) : ParseOverlay
}

sealed interface ParseOverlayMatch {
    data object FullNode : ParseOverlayMatch
    data class CustomRange(val range: TextRange) : ParseOverlayMatch
}
```

| Property | Description |
|----------|-------------|
| `parser` | The parser for the nested language |
| `overlay` | `null` to parse the full node; `ParseOverlay.Ranges` for specific ranges; or `ParseOverlay.Predicate` for dynamic matching |
| `bracketed` | Whether the nested region is bracket-delimited |

## Overlay mode

Use overlay mode when the nested language appears in scattered regions
within a node. Pass a `ParseOverlay.Predicate` as `overlay`:

```kotlin
parseMixed { nodeRef, input ->
    if (nodeRef.type.name == "TemplateString") {
        NestedParse(
            parser = expressionParser,
            overlay = ParseOverlay.Predicate { child: SyntaxNodeRef ->
                if (child.type.name == "Interpolation") {
                    ParseOverlayMatch.FullNode
                } else {
                    null
                }
            }
        )
    } else null
}
```

## Building a mixed-language support

Combine `parseMixed` with `LRLanguage` and `LanguageSupport`:

```kotlin
import com.monkopedia.kodemirror.language.*

val htmlLanguage = LRLanguage.define(
    parser = parseMixed { nodeRef, input ->
        when (nodeRef.type.name) {
            "ScriptContent" -> NestedParse(parser = jsParser)
            "StyleContent" -> NestedParse(parser = cssParser)
            else -> null
        }
    }.configure(htmlParser),
    name = "html"
)

fun html(): LanguageSupport = LanguageSupport(
    language = htmlLanguage,
    // supporting extensions for JS, CSS, etc.
)
```

## Key points

- The `nest` function in `parseMixed` is called for each node in the
  outer parse tree. Return `NestedParse` to parse that region with
  another parser, or `null` to skip it.
- `parseMixed` returns a `ParseWrapper` that you apply to the outer
  parser to produce a combined parser.
- Nested parsers run incrementally — only the changed regions are
  re-parsed on edits.

---

*Based on the [CodeMirror Mixed-Language Parsing example](https://codemirror.net/examples/mixed-language/).*
