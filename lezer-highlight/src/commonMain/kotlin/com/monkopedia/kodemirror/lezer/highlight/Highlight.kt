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
package com.monkopedia.kodemirror.lezer.highlight

import com.monkopedia.kodemirror.lezer.common.NodeProp
import com.monkopedia.kodemirror.lezer.common.NodePropSource
import com.monkopedia.kodemirror.lezer.common.NodeType
import com.monkopedia.kodemirror.lezer.common.SyntaxNodeRef
import com.monkopedia.kodemirror.lezer.common.Tree
import com.monkopedia.kodemirror.lezer.common.TreeCursor

/**
 * A highlighter defines a mapping from highlighting tags to style strings.
 */
interface Highlighter {
    /** Get the style string for the given set of tags, or null. */
    fun style(tags: List<Tag>): String?

    /** When given, the highlighter will only apply to trees whose top
     * node type passes this predicate. */
    fun scope(node: NodeType): Boolean = true
}

// ---- Rule and styleTags ----

internal enum class Mode { Opaque, Inherit, Normal }

class Rule internal constructor(
    val tags: List<Tag>,
    internal val mode: Mode,
    val context: List<String>?,
    var next: Rule? = null
) {
    val opaque: Boolean get() = mode == Mode.Opaque
    val inherit: Boolean get() = mode == Mode.Inherit
    val depth: Int get() = context?.size ?: 0

    fun sort(other: Rule?): Rule {
        if (other == null || other.depth < this.depth) {
            this.next = other
            return this
        }
        other.next = this.sort(other.next)
        return other
    }

    companion object {
        val empty = Rule(emptyList(), Mode.Normal, null)
    }
}

/** The NodeProp that stores highlight rules on node types. */
val ruleNodeProp = NodeProp<Rule>(
    combine = { a, b ->
        var curA: Rule? = a
        var curB: Rule? = b
        var cur: Rule? = null
        var root: Rule? = null
        while (curA != null || curB != null) {
            val take: Rule
            if (curA == null || (curB != null && curA.depth >= curB.depth)) {
                take = curB!!
                curB = curB.next
            } else {
                take = curA
                curA = curA.next
            }
            if (cur != null && cur.mode == take.mode &&
                take.context == null && cur.context == null
            ) {
                continue
            }
            val copy = Rule(take.tags, take.mode, take.context)
            if (cur != null) {
                cur.next = copy
            } else {
                root = copy
            }
            cur = copy
        }
        root ?: Rule.empty
    }
)

/**
 * Associates highlighting [Tag]s with node types via a selector spec.
 *
 * The spec maps node selectors (space-separated names, with optional
 * `/` path syntax) to [Tag]s.
 */
fun styleTags(spec: Map<String, Tag>): NodePropSource<Rule> =
    styleTagsImpl(spec.mapValues { (_, v) -> listOf(v) })

/**
 * Associates highlighting [Tag]s with node types via a selector spec, where each entry maps a
 * node selector to a list of [Tag]s.
 *
 * Prefer the single-tag overload [styleTags] when each selector maps to exactly one tag.
 */
fun styleTagsList(spec: Map<String, List<Tag>>): NodePropSource<Rule> = styleTagsImpl(spec)

private fun styleTagsImpl(spec: Map<String, List<Tag>>): NodePropSource<Rule> {
    val byName = mutableMapOf<String, Rule>()
    for ((prop, tagList) in spec) {
        for (part in prop.split(" ")) {
            if (part.isEmpty()) continue
            val pieces = mutableListOf<String>()
            var mode = Mode.Normal
            var rest = part
            var pos = 0
            while (true) {
                if (rest == "..." && pos > 0 && pos + 3 == part.length) {
                    mode = Mode.Inherit
                    break
                }
                val m = Regex("""^"(?:[^"\\]|\\.)*?"|[^/!]+""").find(rest)
                    ?: throw IllegalArgumentException("Invalid path: $part")
                val matched = m.value
                pieces.add(
                    when {
                        matched == "*" -> ""
                        matched.startsWith("\"") -> parseJsonString(matched)
                        else -> matched
                    }
                )
                pos += matched.length
                if (pos == part.length) break
                val next = part[pos++]
                if (pos == part.length && next == '!') {
                    mode = Mode.Opaque
                    break
                }
                if (next != '/') {
                    throw IllegalArgumentException("Invalid path: $part")
                }
                rest = part.substring(pos)
            }
            val last = pieces.lastIndex
            val inner = pieces[last]
            require(inner.isNotEmpty()) { "Invalid path: $part" }
            val rule = Rule(
                tagList,
                mode,
                if (last > 0) pieces.subList(0, last) else null
            )
            byName[inner] = rule.sort(byName[inner])
        }
    }
    return ruleNodeProp.add(byName)
}

private fun parseJsonString(s: String): String {
    // Remove surrounding quotes and handle escapes
    val inner = s.substring(1, s.length - 1)
    return inner.replace("\\\"", "\"")
        .replace("\\\\", "\\")
        .replace("\\/", "/")
}

/**
 * Match a syntax node's highlight rules. If there's a match, return its
 * set of tags, and whether it is opaque or inheriting.
 */
fun getStyleTags(node: SyntaxNodeRef): Rule? {
    var rule = node.type.prop(ruleNodeProp)
    while (rule != null && rule.context != null && !node.matchContext(rule.context)) {
        rule = rule.next
    }
    return rule
}

// ---- tagHighlighter ----

data class TagStyleRule(
    val tags: List<Tag>,
    val `class`: String
)

fun TagStyleRule(tag: Tag, `class`: String): TagStyleRule = TagStyleRule(listOf(tag), `class`)

/**
 * Define a [Highlighter] from an array of tag/class pairs.
 */
fun tagHighlighter(
    tagRules: List<TagStyleRule>,
    scope: ((NodeType) -> Boolean)? = null,
    all: String? = null
): Highlighter {
    val map = mutableMapOf<Int, String>()
    for (style in tagRules) {
        for (tag in style.tags) {
            map[tag.id] = style.`class`
        }
    }
    return object : Highlighter {
        override fun style(tags: List<Tag>): String? {
            var cls = all
            for (tag in tags) {
                for (sub in tag.set) {
                    val tagClass = map[sub.id]
                    if (tagClass != null) {
                        cls = if (cls != null) "$cls $tagClass" else tagClass
                        break
                    }
                }
            }
            return cls
        }

        override fun scope(node: NodeType): Boolean {
            return scope?.invoke(node) ?: true
        }
    }
}

// ---- highlightTree ----

/**
 * Highlight the given tree with the given highlighter(s), calling
 * [putStyle] for each styled range.
 */
fun highlightTree(
    tree: Tree,
    highlighter: Highlighter,
    putStyle: (from: Int, to: Int, classes: String) -> Unit,
    from: Int = 0,
    to: Int = tree.length
) {
    highlightTree(tree, listOf(highlighter), putStyle, from, to)
}

fun highlightTree(
    tree: Tree,
    highlighters: List<Highlighter>,
    putStyle: (from: Int, to: Int, classes: String) -> Unit,
    from: Int = 0,
    to: Int = tree.length
) {
    val builder = HighlightBuilder(from, highlighters, putStyle)
    builder.highlightRange(tree.cursor(), from, to, "", builder.highlighters)
    builder.flush(to)
}

private fun highlightTags(highlighters: List<Highlighter>, tags: List<Tag>): String? {
    var result: String? = null
    for (highlighter in highlighters) {
        val value = highlighter.style(tags)
        if (value != null) {
            result = if (result != null) "$result $value" else value
        }
    }
    return result
}

/**
 * Highlight the given [code] string using the provided [tree] and
 * [highlighter], calling [putText] for each piece of text with its
 * CSS classes, and [putBreak] at line breaks.
 */
fun highlightCode(
    code: String,
    tree: Tree,
    highlighter: Highlighter,
    putText: (text: String, classes: String) -> Unit,
    putBreak: () -> Unit,
    from: Int = 0,
    to: Int = code.length
) {
    var pos = from
    fun flush(flushTo: Int, classes: String) {
        if (flushTo <= pos) return
        var text = code.substring(pos, flushTo)
        if (text.isNotEmpty()) {
            val lines = text.split("\n")
            for (i in lines.indices) {
                if (i > 0) putBreak()
                if (lines[i].isNotEmpty()) putText(lines[i], classes)
            }
        }
        pos = flushTo
    }
    highlightTree(tree, highlighter, { styleFrom, styleTo, classes ->
        flush(styleFrom, "")
        flush(styleTo, classes)
    }, from, to)
    flush(to, "")
}

/**
 * A pre-built [Highlighter] that maps highlighting tags to `tok-*`
 * CSS class names.
 */
val classHighlighter: Highlighter = tagHighlighter(
    listOf(
        TagStyleRule(Tags.link, "tok-link"),
        TagStyleRule(Tags.heading, "tok-heading"),
        TagStyleRule(Tags.emphasis, "tok-emphasis"),
        TagStyleRule(Tags.strong, "tok-strong"),
        TagStyleRule(Tags.keyword, "tok-keyword"),
        TagStyleRule(Tags.atom, "tok-atom"),
        TagStyleRule(Tags.bool, "tok-bool"),
        TagStyleRule(Tags.url, "tok-url"),
        TagStyleRule(Tags.labelName, "tok-labelName"),
        TagStyleRule(Tags.inserted, "tok-inserted"),
        TagStyleRule(Tags.deleted, "tok-deleted"),
        TagStyleRule(Tags.literal, "tok-literal"),
        TagStyleRule(Tags.string, "tok-string"),
        TagStyleRule(Tags.number, "tok-number"),
        TagStyleRule(
            listOf(Tags.regexp, Tags.escape, Tags.special(Tags.string)),
            "tok-string2"
        ),
        TagStyleRule(Tags.variableName, "tok-variableName"),
        TagStyleRule(Tags.local(Tags.variableName), "tok-variableName2"),
        TagStyleRule(Tags.definition(Tags.variableName), "tok-variableName tok-definition"),
        TagStyleRule(Tags.special(Tags.variableName), "tok-variableName2"),
        TagStyleRule(Tags.definition(Tags.propertyName), "tok-propertyName tok-definition"),
        TagStyleRule(Tags.typeName, "tok-typeName"),
        TagStyleRule(Tags.namespace, "tok-namespace"),
        TagStyleRule(Tags.className, "tok-className"),
        TagStyleRule(Tags.macroName, "tok-macroName"),
        TagStyleRule(Tags.propertyName, "tok-propertyName"),
        TagStyleRule(Tags.operator, "tok-operator"),
        TagStyleRule(Tags.comment, "tok-comment"),
        TagStyleRule(Tags.meta, "tok-meta"),
        TagStyleRule(Tags.invalid, "tok-invalid"),
        TagStyleRule(Tags.punctuation, "tok-punctuation")
    )
)

private class HighlightBuilder(
    var at: Int,
    val highlighters: List<Highlighter>,
    val span: (from: Int, to: Int, cls: String) -> Unit
) {
    var cls: String = ""

    fun startSpan(at: Int, cls: String) {
        if (cls != this.cls) {
            flush(at)
            if (at > this.at) this.at = at
            this.cls = cls
        }
    }

    fun flush(to: Int) {
        if (to > at && cls.isNotEmpty()) span(at, to, cls)
    }

    fun highlightRange(
        cursor: TreeCursor,
        from: Int,
        to: Int,
        inheritedClass: String,
        highlighters: List<Highlighter>
    ) {
        val start = cursor.from
        val end = cursor.to
        if (start >= to || end <= from) return

        var activeHighlighters = highlighters
        if (cursor.type.isTop) {
            activeHighlighters = this.highlighters.filter { h ->
                h.scope(cursor.type)
            }
        }

        var cls = inheritedClass
        var currentInherited = inheritedClass
        val rule = getStyleTags(cursor) ?: Rule.empty
        val tagCls = highlightTags(activeHighlighters, rule.tags)
        if (tagCls != null) {
            cls = if (cls.isNotEmpty()) "$cls $tagCls" else tagCls
            if (rule.mode == Mode.Inherit) {
                currentInherited += (if (currentInherited.isNotEmpty()) " " else "") + tagCls
            }
        }

        startSpan(maxOf(from, start), cls)
        if (rule.opaque) return

        if (cursor.firstChild()) {
            do {
                if (cursor.to <= from) continue
                if (cursor.from >= to) break
                highlightRange(cursor, from, to, currentInherited, activeHighlighters)
                startSpan(minOf(to, cursor.to), cls)
            } while (cursor.nextSibling())
            cursor.parent()
        }
    }
}
