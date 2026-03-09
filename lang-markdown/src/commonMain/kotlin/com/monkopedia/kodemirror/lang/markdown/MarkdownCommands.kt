/*
 * Copyright 2025 Jason Monk
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
package com.monkopedia.kodemirror.lang.markdown

import com.monkopedia.kodemirror.language.syntaxTree
import com.monkopedia.kodemirror.lezer.common.SyntaxNode
import com.monkopedia.kodemirror.state.ChangeSpec
import com.monkopedia.kodemirror.state.DocPos
import com.monkopedia.kodemirror.state.EditorState
import com.monkopedia.kodemirror.state.InsertContent
import com.monkopedia.kodemirror.state.Text
import com.monkopedia.kodemirror.state.TransactionSpec
import com.monkopedia.kodemirror.view.EditorSession

private class Context(
    val node: SyntaxNode,
    val from: DocPos,
    val to: DocPos,
    val spaceBefore: String,
    val spaceAfter: String,
    val type: String,
    val item: SyntaxNode?
) {
    fun blank(trailing: Boolean = true): String {
        val width = to - from
        val result = if (type == "Blockquote") {
            ">"
        } else {
            " ".repeat(width)
        }
        return if (trailing) spaceBefore + result + spaceAfter else result
    }
}

private fun isMark(node: SyntaxNode): Boolean = node.name == "QuoteMark" || node.name == "ListMark"

private fun getContext(node: SyntaxNode, doc: Text): List<Context> {
    val nodes = mutableListOf<Context>()
    var cur: SyntaxNode? = node
    while (cur != null && cur.name != "Document") {
        if (cur.name == "Blockquote") {
            val mark = cur.firstChild
            if (mark != null && isMark(mark)) {
                nodes.add(
                    0,
                    Context(
                        node = cur,
                        from = DocPos(mark.from),
                        to = DocPos(mark.to),
                        spaceBefore = "",
                        spaceAfter = if (mark.to < cur.to &&
                            doc.sliceString(DocPos(mark.to), DocPos(mark.to + 1)) == " "
                        ) {
                            " "
                        } else {
                            ""
                        },
                        type = "Blockquote",
                        item = null
                    )
                )
            }
        } else if (cur.name == "ListItem") {
            val parent = cur.parent
            if (parent != null &&
                (parent.name == "OrderedList" || parent.name == "BulletList")
            ) {
                val mark = cur.firstChild
                if (mark != null && isMark(mark)) {
                    val line = doc.lineAt(DocPos(cur.from))
                    val indent = DocPos(cur.from) - line.from
                    nodes.add(
                        0,
                        Context(
                            node = cur,
                            from = DocPos(mark.from),
                            to = DocPos(mark.to),
                            spaceBefore = " ".repeat(indent),
                            spaceAfter = if (mark.to < cur.to &&
                                doc.sliceString(DocPos(mark.to), DocPos(mark.to + 1)) == " "
                            ) {
                                " "
                            } else {
                                ""
                            },
                            type = parent.name,
                            item = cur
                        )
                    )
                }
            }
        }
        if (cur.name == "FencedCode") break
        cur = cur.parent
    }
    return nodes
}

private fun blankLine(cx: Context, state: EditorState): Boolean {
    val line = state.doc.lineAt(DocPos(cx.node.from))
    val content = state.doc.sliceString(line.from, line.to).trim()
    return content.isEmpty() || content == ">" ||
        Regex("^\\d+[.)]\$").matches(content) ||
        Regex("^[-*+]\$").matches(content)
}

fun insertNewlineContinueMarkupCommand(
    continueOnBlank: Boolean = false
): (EditorSession) -> Boolean = { view ->
    val state = view.state
    val tree = syntaxTree(state)
    val specs = mutableListOf<ChangeSpec.Single>()
    var canHandle = true

    for (range in state.selection.ranges) {
        val pos = range.from
        val line = state.doc.lineAt(pos)
        if (pos < line.to || !range.empty) {
            canHandle = false
            break
        }

        val node = tree.resolveInner(pos.value, -1)
        val context = getContext(node, state.doc)
        if (context.isEmpty()) {
            canHandle = false
            break
        }

        val isBlank = blankLine(context.last(), state)
        if (isBlank && !continueOnBlank) {
            specs.add(
                ChangeSpec.Single(
                    from = line.from,
                    to = line.to,
                    insert = InsertContent.StringContent("")
                )
            )
        } else {
            val insert = buildString {
                append("\n")
                for (cx in context) {
                    append(cx.blank())
                }
            }
            specs.add(
                ChangeSpec.Single(
                    from = pos,
                    insert = InsertContent.StringContent(insert)
                )
            )
        }
    }

    if (canHandle && specs.isNotEmpty()) {
        val changes: ChangeSpec = if (specs.size == 1) {
            specs[0]
        } else {
            ChangeSpec.Multi(specs)
        }
        view.dispatch(TransactionSpec(changes = changes))
        true
    } else {
        false
    }
}

val insertNewlineContinueMarkup: (EditorSession) -> Boolean =
    insertNewlineContinueMarkupCommand()

val deleteMarkupBackward: (EditorSession) -> Boolean = { view ->
    val state = view.state
    val tree = syntaxTree(state)
    val specs = mutableListOf<ChangeSpec.Single>()

    for (range in state.selection.ranges) {
        val pos = range.from
        if (pos == DocPos.ZERO || !range.empty) continue

        val node = tree.resolveInner(pos.value, -1)
        val context = getContext(node, state.doc)
        if (context.isEmpty()) continue

        val lastCtx = context.last()
        if (pos <= lastCtx.to + lastCtx.spaceAfter.length) {
            specs.add(
                ChangeSpec.Single(
                    from = lastCtx.from - lastCtx.spaceBefore.length,
                    to = pos,
                    insert = InsertContent.StringContent("")
                )
            )
        }
    }

    if (specs.isNotEmpty()) {
        val changes: ChangeSpec = if (specs.size == 1) {
            specs[0]
        } else {
            ChangeSpec.Multi(specs)
        }
        view.dispatch(TransactionSpec(changes = changes))
        true
    } else {
        false
    }
}

fun renumberList(state: EditorState, pos: Int): TransactionSpec? {
    val tree = syntaxTree(state)
    val node = tree.resolveInner(pos, -1)
    var listNode: SyntaxNode? = node
    while (listNode != null && listNode.name != "OrderedList") {
        listNode = listNode.parent
    }
    if (listNode == null) return null

    val specs = mutableListOf<ChangeSpec.Single>()
    var number = 1
    var child = listNode.firstChild
    while (child != null) {
        if (child.name == "ListItem") {
            val mark = child.firstChild
            if (mark != null && mark.name == "ListMark") {
                val text = state.doc.sliceString(DocPos(mark.from), DocPos(mark.to))
                val match = Regex("^(\\d+)([.)])").find(text)
                if (match != null) {
                    val newText = "$number${match.groupValues[2]}"
                    if (newText != text) {
                        specs.add(
                            ChangeSpec.Single(
                                from = DocPos(mark.from),
                                to = DocPos(mark.to),
                                insert = InsertContent.StringContent(newText)
                            )
                        )
                    }
                }
                number++
            }
        }
        child = child.nextSibling
    }
    return if (specs.isNotEmpty()) {
        TransactionSpec(
            changes = if (specs.size == 1) specs[0] else ChangeSpec.Multi(specs)
        )
    } else {
        null
    }
}
