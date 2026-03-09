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
package com.monkopedia.kodemirror.lang.python

import com.monkopedia.kodemirror.language.CommentTokens
import com.monkopedia.kodemirror.language.FoldRange
import com.monkopedia.kodemirror.language.LRLanguage
import com.monkopedia.kodemirror.language.LanguageSupport
import com.monkopedia.kodemirror.language.TreeIndentContext
import com.monkopedia.kodemirror.language.commentTokens
import com.monkopedia.kodemirror.language.delimitedIndent
import com.monkopedia.kodemirror.language.foldInside
import com.monkopedia.kodemirror.language.foldNodeProp
import com.monkopedia.kodemirror.language.indentNodeProp
import com.monkopedia.kodemirror.lezer.common.SyntaxNode
import com.monkopedia.kodemirror.lezer.lr.ParserConfig
import com.monkopedia.kodemirror.state.DocPos

private fun innerBody(context: TreeIndentContext): SyntaxNode? {
    val lineIndent = context.lineIndent(context.pos, -1)
    var found: SyntaxNode? = null
    var node = context.node
    var pos = context.pos.value
    loop@ while (true) {
        val before = node.childBefore(pos) ?: break
        when (before.name) {
            "Comment" -> pos = before.from
            "Body", "MatchBody" -> {
                if (context.baseIndentFor(before) + context.unit <= lineIndent) {
                    found = before
                }
                node = before
            }
            "MatchClause" -> node = before
            else -> {
                if (before.type.`is`("Statement")) {
                    node = before
                } else {
                    break@loop
                }
            }
        }
    }
    return found
}

private fun indentBody(context: TreeIndentContext, node: SyntaxNode): Int? {
    val base = context.baseIndentFor(node)
    val line = context.lineAt(context.pos, -1)
    val to = line.from + line.text.length
    if (Regex("""^\s*($|#)""").containsMatchIn(line.text) &&
        context.node.to < to.value + 100 &&
        !Regex("""\S""").containsMatchIn(
            context.state.doc.sliceString(to, DocPos(context.node.to))
        ) &&
        context.lineIndent(context.pos, -1) <= base
    ) {
        return null
    }
    if (Regex("""^\s*(else:|elif |except |finally:|case\s+[^=:]+:)""")
            .containsMatchIn(context.textAfter) &&
        context.lineIndent(context.pos, -1) <= base
    ) {
        return null
    }
    return base + context.unit
}

/**
 * A language provider based on the Lezer Python parser, extended with
 * highlighting and indentation information.
 */
val pythonLanguage: LRLanguage = LRLanguage.define(
    parser = pythonParser.configure(
        ParserConfig(
            props = listOf(
                indentNodeProp.add { type ->
                    when (type.name) {
                        "Body" -> { cx ->
                            val body =
                                if (Regex("""^\s*(#|$)""").containsMatchIn(cx.textAfter)) {
                                    innerBody(cx) ?: cx.node
                                } else {
                                    cx.node
                                }
                            indentBody(cx, body) ?: cx.continueAt()
                        }
                        "MatchBody" -> { cx ->
                            val inner = innerBody(cx)
                            indentBody(cx, inner ?: cx.node) ?: cx.continueAt()
                        }
                        "IfStatement" -> { cx ->
                            if (Regex("""^\s*(else:|elif )""")
                                    .containsMatchIn(cx.textAfter)
                            ) {
                                cx.baseIndent
                            } else {
                                cx.continueAt()
                            }
                        }
                        "ForStatement", "WhileStatement" -> { cx ->
                            if (Regex("""^\s*else:""").containsMatchIn(cx.textAfter)) {
                                cx.baseIndent
                            } else {
                                cx.continueAt()
                            }
                        }
                        "TryStatement" -> { cx ->
                            if (Regex("""^\s*(except[ :]|finally:|else:)""")
                                    .containsMatchIn(cx.textAfter)
                            ) {
                                cx.baseIndent
                            } else {
                                cx.continueAt()
                            }
                        }
                        "MatchStatement" -> { cx ->
                            if (Regex("""^\s*case """).containsMatchIn(cx.textAfter)) {
                                cx.baseIndent + cx.unit
                            } else {
                                cx.continueAt()
                            }
                        }
                        "TupleExpression", "ComprehensionExpression",
                        "ParamList", "ArgList",
                        "ParenthesizedExpression" ->
                            delimitedIndent(closing = ")")
                        "DictionaryExpression",
                        "DictionaryComprehensionExpression",
                        "SetExpression",
                        "SetComprehensionExpression" ->
                            delimitedIndent(closing = "}")
                        "ArrayExpression",
                        "ArrayComprehensionExpression" ->
                            delimitedIndent(closing = "]")
                        "MemberExpression" -> { cx -> cx.baseIndent + cx.unit }
                        "String", "FormatString" -> { _ -> null }
                        "Script" -> { cx ->
                            val inner = innerBody(cx)
                            if (inner != null) {
                                indentBody(cx, inner) ?: cx.continueAt()
                            } else {
                                cx.continueAt()
                            }
                        }
                        else -> null
                    }
                },
                foldNodeProp.add { type ->
                    when (type.name) {
                        "ArrayExpression", "DictionaryExpression",
                        "SetExpression", "TupleExpression" ->
                            { node, _ -> foldInside(node) }
                        "Body" -> { node, state ->
                            val end =
                                node.to - if (node.to == state.doc.length) 0 else 1
                            FoldRange(DocPos(node.from + 1), DocPos(end))
                        }
                        "String", "FormatString" -> { node, state ->
                            FoldRange(
                                state.doc.lineAt(DocPos(node.from)).to,
                                DocPos(node.to)
                            )
                        }
                        else -> null
                    }
                }
            )
        )
    ),
    name = "python"
)

/**
 * Python language support.
 */
fun python(): LanguageSupport = LanguageSupport(
    pythonLanguage,
    support = commentTokens.of(
        CommentTokens(line = "#")
    )
)
