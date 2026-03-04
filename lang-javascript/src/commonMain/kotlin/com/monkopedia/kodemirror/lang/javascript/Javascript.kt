/*
 * Copyright 2026 Jason Monk
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * Originally based on CodeMirror 6 by Marijn Haverbeke, licensed under MIT.
 * See NOTICE file for details.
 */
package com.monkopedia.kodemirror.lang.javascript

import com.monkopedia.kodemirror.language.CommentTokens
import com.monkopedia.kodemirror.language.FoldRange
import com.monkopedia.kodemirror.language.LRLanguage
import com.monkopedia.kodemirror.language.LanguageSupport
import com.monkopedia.kodemirror.language.commentTokens
import com.monkopedia.kodemirror.language.continuedIndent
import com.monkopedia.kodemirror.language.delimitedIndent
import com.monkopedia.kodemirror.language.flatIndent
import com.monkopedia.kodemirror.language.foldInside
import com.monkopedia.kodemirror.language.foldNodeProp
import com.monkopedia.kodemirror.language.getIndentUnit
import com.monkopedia.kodemirror.language.indentNodeProp
import com.monkopedia.kodemirror.lezer.common.NodePropSource
import com.monkopedia.kodemirror.lezer.lr.ParserConfig

private val jsIndentProp: NodePropSource<*> = indentNodeProp.add { type ->
    when {
        type.name == "IfStatement" ->
            continuedIndent(except = Regex("""^\s*(\{|else\b)"""))
        type.name == "TryStatement" ->
            continuedIndent(except = Regex("""^\s*(\{|catch\b|finally\b)"""))
        type.name == "LabeledStatement" -> flatIndent
        type.name == "SwitchBody" -> { cx ->
            val after = cx.textAfter
            val closed = Regex("""^\s*\}""").containsMatchIn(after)
            val isCase =
                Regex("""^\s*(case|default)\b""").containsMatchIn(after)
            val unit = getIndentUnit(cx.state)
            cx.baseIndent + (if (closed) 0 else if (isCase) 1 else 2) * unit
        }
        type.name == "Block" -> delimitedIndent(closing = "}")
        type.name == "ArrowFunction" -> { cx -> cx.baseIndent + cx.unit }
        type.name == "TemplateString" || type.name == "BlockComment" ->
            { _ -> null }
        type.name == "JSXElement" -> { cx ->
            val closed = Regex("""^\s*</""").containsMatchIn(cx.textAfter)
            cx.lineIndent(cx.node.from) + if (closed) 0 else cx.unit
        }
        type.name == "JSXEscape" -> { cx ->
            val closed = Regex("""\s*\}""").containsMatchIn(cx.textAfter)
            cx.lineIndent(cx.node.from) + if (closed) 0 else cx.unit
        }
        type.name == "JSXOpenTag" || type.name == "JSXSelfClosingTag" -> { cx ->
            cx.column(cx.node.from) + cx.unit
        }
        type.`is`("Statement") || type.name == "Property" ->
            continuedIndent(except = Regex("""^\s*\{"""))
        else -> null
    }
}

private val jsFoldProp: NodePropSource<*> = foldNodeProp.add { type ->
    when (type.name) {
        "Block", "ClassBody", "SwitchBody", "EnumBody",
        "ObjectExpression", "ArrayExpression",
        "ObjectType" ->
            { node, _ -> foldInside(node) }
        "BlockComment" -> { node, _ ->
            if (node.to - node.from > 4) {
                FoldRange(node.from + 2, node.to - 2)
            } else {
                null
            }
        }
        "JSXElement" -> { node, _ ->
            val open = node.firstChild ?: return@add null
            if (open.name == "JSXSelfClosingTag") return@add null
            val close = node.lastChild ?: return@add null
            val to = if (close.type.isError) node.to else close.from
            FoldRange(open.to, to)
        }
        "JSXSelfClosingTag", "JSXOpenTag" -> { node, _ ->
            val name = node.firstChild?.nextSibling
            val close = node.lastChild
            if (name == null || name.type.isError || close == null) {
                null
            } else {
                val to = if (close.type.isError) node.to else close.from
                FoldRange(name.to, to)
            }
        }
        else -> null
    }
}

private val jsProps = listOf(jsIndentProp, jsFoldProp)

/**
 * A language provider based on the Lezer JavaScript parser, extended with
 * highlighting and indentation information.
 */
val javascriptLanguage: LRLanguage = LRLanguage.define(
    parser = parser.configure(ParserConfig(props = jsProps)),
    name = "javascript"
)

/**
 * A language provider for TypeScript.
 */
val typescriptLanguage: LRLanguage = LRLanguage.define(
    parser = parser.configure(ParserConfig(props = jsProps, dialect = "ts")),
    name = "typescript"
)

/**
 * Language provider for JSX.
 */
val jsxLanguage: LRLanguage = LRLanguage.define(
    parser = parser.configure(ParserConfig(props = jsProps, dialect = "jsx")),
    name = "jsx"
)

/**
 * Language provider for JSX + TypeScript.
 */
val tsxLanguage: LRLanguage = LRLanguage.define(
    parser = parser.configure(ParserConfig(props = jsProps, dialect = "jsx ts")),
    name = "tsx"
)

/**
 * JavaScript language support.
 */
fun javascript(jsx: Boolean = false, typescript: Boolean = false): LanguageSupport {
    val lang = when {
        jsx && typescript -> tsxLanguage
        jsx -> jsxLanguage
        typescript -> typescriptLanguage
        else -> javascriptLanguage
    }
    return LanguageSupport(
        lang,
        support = commentTokens.of(
            CommentTokens(
                line = "//",
                block = CommentTokens.BlockComment("/*", "*/")
            )
        )
    )
}
