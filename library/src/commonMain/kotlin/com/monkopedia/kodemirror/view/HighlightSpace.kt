package com.monkopedia.kodemirror.view

import com.monkopedia.kodemirror.state.Extension
import com.monkopedia.kodemirror.decoration.Decoration
import com.monkopedia.kodemirror.decoration.DecorationSpec
import com.monkopedia.kodemirror.decoration.MatchDecorator
import com.monkopedia.kodemirror.extension.ViewPlugin
import com.monkopedia.kodemirror.extension.ViewUpdate

private fun matcher(decorator: MatchDecorator): Extension {
    return ViewPlugin.define(
        create = { view -> 
            object {
                var decorations = decorator.createDeco(view)
                
                fun update(u: ViewUpdate) {
                    decorations = decorator.updateDeco(u, decorations)
                }
            }
        },
        decorations = { v -> v.decorations }
    )
}

private val tabDeco = Decoration.mark(DecorationSpec(className = "cm-highlightTab"))
private val spaceDeco = Decoration.mark(DecorationSpec(className = "cm-highlightSpace"))

private val whitespaceHighlighter = matcher(MatchDecorator(
    regexp = Regex("""\t| """),
    decoration = { match -> if (match[0] == "\t") tabDeco else spaceDeco },
    boundary = Regex("""\S""")
))

/**
 * Returns an extension that highlights whitespace, adding a
 * `cm-highlightSpace` class to stretches of spaces, and a
 * `cm-highlightTab` class to individual tab characters. By default,
 * the former are shown as faint dots, and the latter as arrows.
 */
fun highlightWhitespace(): Extension {
    return whitespaceHighlighter
}

private val trailingHighlighter = matcher(MatchDecorator(
    regexp = Regex("""\s+$"""),
    decoration = { _ -> Decoration.mark(DecorationSpec(className = "cm-trailingSpace")) },
    boundary = Regex("""\S""")
))

/**
 * Returns an extension that adds a `cm-trailingSpace` class to all
 * trailing whitespace.
 */
fun highlightTrailingWhitespace(): Extension {
    return trailingHighlighter
}
