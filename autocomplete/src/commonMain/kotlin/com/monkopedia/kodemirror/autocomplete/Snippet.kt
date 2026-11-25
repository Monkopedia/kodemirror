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
package com.monkopedia.kodemirror.autocomplete

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import com.monkopedia.kodemirror.language.indentUnit
import com.monkopedia.kodemirror.state.ChangeDesc
import com.monkopedia.kodemirror.state.ChangeSpec
import com.monkopedia.kodemirror.state.DocPos
import com.monkopedia.kodemirror.state.EditorSelection
import com.monkopedia.kodemirror.state.EditorState
import com.monkopedia.kodemirror.state.Extension
import com.monkopedia.kodemirror.state.ExtensionList
import com.monkopedia.kodemirror.state.InsertContent
import com.monkopedia.kodemirror.state.MapMode
import com.monkopedia.kodemirror.state.RangeSet
import com.monkopedia.kodemirror.state.RangeSetBuilder
import com.monkopedia.kodemirror.state.SelectionSpec
import com.monkopedia.kodemirror.state.StateEffect
import com.monkopedia.kodemirror.state.StateEffectType
import com.monkopedia.kodemirror.state.StateField
import com.monkopedia.kodemirror.state.StateFieldSpec
import com.monkopedia.kodemirror.state.Text
import com.monkopedia.kodemirror.state.TransactionSpec
import com.monkopedia.kodemirror.view.Decoration
import com.monkopedia.kodemirror.view.DecorationSet
import com.monkopedia.kodemirror.view.DecorationSource
import com.monkopedia.kodemirror.view.EditorSession
import com.monkopedia.kodemirror.view.KeyBinding
import com.monkopedia.kodemirror.view.MarkDecorationSpec
import com.monkopedia.kodemirror.view.PluginValue
import com.monkopedia.kodemirror.view.ThemeKey
import com.monkopedia.kodemirror.view.ViewPlugin
import com.monkopedia.kodemirror.view.ViewUpdate
import com.monkopedia.kodemirror.view.editorTheme
import com.monkopedia.kodemirror.view.keymap

// ── Snippet field representation ──

/** One place a [SnippetField] occupies in the document, from [from] to [to]. */
data class SnippetRange(val from: DocPos, val to: DocPos)

/**
 * A named field within an active snippet, at each of its [ranges] in template order. A
 * field the template names more than once is linked, as upstream's: all its ranges are
 * selected together, so typing edits every copy.
 */
data class SnippetField(val name: String, val ranges: List<SnippetRange>) {
    /** Whether every range of [selection] is inside one of the field's ranges. */
    internal fun contains(selection: EditorSelection): Boolean =
        selection.ranges.all { sel -> ranges.any { it.from <= sel.from && it.to >= sel.to } }

    /** The field's ranges as a selection, the first one main (upstream `fieldSelection`). */
    internal val selection: SelectionSpec
        get() = SelectionSpec.EditorSelectionSpec(
            EditorSelection.create(ranges.map { EditorSelection.range(it.from, it.to) })
        )
}

/**
 * Tracks the currently active snippet insertion — the list of tab-stop
 * [fields] and the index of the field the cursor is at.
 */
data class ActiveSnippet(val fields: List<SnippetField>, val fieldIndex: Int) {
    /** The field the cursor is currently on, or `null` if [fieldIndex] is out of bounds. */
    val currentField: SnippetField? get() = fields.getOrNull(fieldIndex)
}

// ── State effects ──

private val setActiveSnippet: StateEffectType<ActiveSnippet?> = StateEffect.define()

// ── State field ──

/** [field] mapped through [changes], or `null` when the text of any of its ranges was deleted. */
private fun SnippetField.mapped(changes: ChangeDesc): SnippetField? = SnippetField(
    name,
    ranges.map {
        val from = changes.mapPos(it.from, -1, MapMode.TrackDel) ?: return null
        val to = changes.mapPos(it.to, 1, MapMode.TrackDel) ?: return null
        SnippetRange(from, to)
    }
)

/**
 * State field that holds the currently active snippet, or `null` when no snippet is active.
 *
 * As upstream: a snippet set by a transaction is taken as is (its positions
 * already refer to that transaction's document), an active one is mapped through
 * later changes and ends when a field's text is deleted, and it also ends when
 * the selection moves outside the current field's ranges.
 */
val snippetState: StateField<ActiveSnippet?> = StateField.define(
    StateFieldSpec(
        create = { null },
        update = update@{ value, tr ->
            for (effect in tr.effects) {
                effect.asType(setActiveSnippet)?.let { return@update it.value }
            }
            var result = value
            if (result != null && tr.docChanged) {
                val fields = result.fields.map { it.mapped(tr.changes) ?: return@update null }
                result = result.copy(fields = fields)
            }
            val selection = tr.selection
            val field = result?.currentField
            if (selection != null && field != null && !field.contains(selection)) result = null
            result
        }
    )
)

// ── Template parsing ──

/** A field's position in the template: line index and offsets within that line. */
private data class FieldPos(var field: Int, val line: Int, var from: Int, var to: Int)

private class ParsedSnippet(
    val lines: List<String>,
    val fieldNames: List<String>,
    val positions: List<FieldPos>
)

private val placeholder = Regex("[#$]\\{(?:(\\d+)(?::([^{}]*))?|((?:\\\\[{}]|[^{}])*))\\}")
private val escapedBrace = Regex("\\\\([{}])")
private val lineBreak = Regex("\r\n?|\n")

/** Upstream's `Snippet.parse`. */
private fun parseTemplate(template: String): ParsedSnippet {
    // A double, as upstream reads a field number with `+`, so `${99999999999}` parses.
    data class Field(val seq: Double?, val name: String)
    val fields = mutableListOf<Field>()
    val lines = mutableListOf<String>()
    val positions = mutableListOf<FieldPos>()
    for (rawLine in template.split(lineBreak)) {
        var line = rawLine
        while (true) {
            val m = placeholder.find(line) ?: break
            val seq = m.groupValues[1].takeIf { it.isNotEmpty() }?.toDouble()
            val rawName = m.groupValues[2].ifEmpty { m.groupValues[3] }
            val name = escapedBrace.replace(rawName) { it.groupValues[1] }
            val index = m.range.first
            var found = fields.indexOfLast {
                if (seq != null) it.seq == seq else name.isNotEmpty() && it.name == name
            }
            if (found < 0) {
                var i = 0
                while (i < fields.size &&
                    (seq == null || fields[i].seq.let { it != null && it < seq })
                ) {
                    i++
                }
                fields.add(i, Field(seq, name))
                found = i
                for (pos in positions) if (pos.field >= found) pos.field++
            }
            for (pos in positions) {
                if (pos.line == lines.size && pos.from > index) {
                    val snip = if (m.groupValues[2].isNotEmpty()) 3 + m.groupValues[1].length else 2
                    pos.from -= snip
                    pos.to -= snip
                }
            }
            positions += FieldPos(found, lines.size, index, index + name.length)
            line = line.substring(0, index) + rawName + line.substring(m.range.last + 1)
        }
        line = escapedBrace.replace(line) { brace ->
            for (pos in positions) {
                if (pos.line == lines.size && pos.from > brace.range.first) {
                    pos.from--
                    pos.to--
                }
            }
            brace.groupValues[1]
        }
        lines += line
    }
    return ParsedSnippet(lines, fields.map { it.name }, positions)
}

/**
 * Parse a snippet template and return a function that, when called,
 * applies the snippet at the given range in the editor.
 *
 * Template syntax, as upstream: `${name}` (or `#{name}`) marks a field the user
 * can tab through, with `name` as its default text; `${}` is an empty field.
 * Fields are visited in textual order unless numbered — `${1}` or
 * `${1:default}`. `\{` and `\}` are literal braces. Each new line takes the
 * indentation of the line the snippet starts on, plus one indent unit per
 * leading tab.
 *
 * The first field is selected. Tab and Shift-Tab move between fields while the
 * snippet is active; moving onto the last field, or the cursor out of the current
 * field, ends it.
 *
 * A field that appears more than once is linked: every occurrence is selected when
 * the field is, so typing edits them all.
 */
fun snippet(template: String): (CompletionApplyContext) -> Unit {
    val parsed = parseTemplate(template)
    return { ctx ->
        val view = ctx.session
        val state = view.state
        val from = ctx.from
        val main = state.selection.main
        val to = if (ctx.to == main.from) main.to else ctx.to

        // Instantiate: indent continuation lines and record where each starts.
        val baseIndent = state.doc.lineAt(from).text.takeWhile { it.isWhitespace() }
        val unit = " ".repeat(state.facet(indentUnit))
        val lineStart = mutableListOf(from.value)
        val text = mutableListOf<String>()
        var pos = from.value
        for (templateLine in parsed.lines) {
            var line = templateLine
            if (text.isNotEmpty()) {
                val tabs = line.takeWhile { it == '\t' }.length
                val indent = baseIndent + unit.repeat(tabs)
                lineStart += pos + indent.length - tabs
                line = indent + line.substring(tabs)
            }
            text += line
            pos += line.length + 1
        }
        val fields = parsed.fieldNames.mapIndexed { field, name ->
            val ranges = parsed.positions.filter { it.field == field }.map {
                val start = lineStart[it.line]
                SnippetRange(DocPos(start + it.from), DocPos(start + it.to))
            }
            SnippetField(name, ranges)
        }

        val inserted = Text.of(text)
        // The first field is selected; without fields the cursor goes after the text.
        val selection = fields.firstOrNull()?.selection
            ?: SelectionSpec.CursorSpec(from + inserted.length)
        view.dispatch(
            TransactionSpec(
                changes = ChangeSpec.Single(from, to, InsertContent.TextContent(inserted)),
                selection = selection,
                effects = if (fields.size >
                    1
                ) {
                    listOf(setActiveSnippet.of(ActiveSnippet(fields, 0)))
                } else {
                    null
                },
                annotations = listOf(pickedCompletion.of(ctx.completion)),
                userEvent = "input.complete",
                scrollIntoView = true
            )
        )
    }
}

/**
 * Create a [Completion] that, when accepted, inserts a snippet.
 *
 * @param template The snippet template string.
 * @param completion A base completion whose [Completion.label], [Completion.detail],
 *   etc. are used. The [Completion.applyFn] is overridden with the snippet applicator.
 */
fun snippetCompletion(template: String, completion: Completion): Completion =
    completion.copy(applyFn = snippet(template))

// ── Snippet state queries ──

/** Whether there is a next snippet field to navigate to. */
fun hasNextSnippetField(state: EditorState): Boolean {
    val active = state.field(snippetState, require = false)
    return active != null && active.fieldIndex < active.fields.size - 1
}

/** Whether there is a previous snippet field to navigate to. */
fun hasPrevSnippetField(state: EditorState): Boolean {
    val active = state.field(snippetState, require = false)
    return active != null && active.fieldIndex > 0
}

// ── Commands ──

/**
 * Upstream's `moveField`: select the field [dir] away. Moving onto the last field ends
 * the snippet, as upstream does.
 */
private fun moveField(dir: Int): (EditorSession) -> Boolean = command@{ view ->
    val active = view.state.field(snippetState, require = false)
    if (active == null || dir < 0 && active.fieldIndex == 0) return@command false
    val next = active.fieldIndex + dir
    val field = active.fields.getOrNull(next) ?: return@command false
    val last = dir > 0 && next + dir !in active.fields.indices
    view.dispatch(
        TransactionSpec(
            selection = field.selection,
            effects = listOf(
                setActiveSnippet.of(if (last) null else active.copy(fieldIndex = next))
            ),
            scrollIntoView = true
        )
    )
    true
}

/** Move to the next snippet field. Moving onto the last field ends the snippet. */
val nextSnippetField: (EditorSession) -> Boolean = moveField(1)

/** Move to the previous snippet field. */
val prevSnippetField: (EditorSession) -> Boolean = moveField(-1)

/** Clear the active snippet. */
val clearSnippet: (EditorSession) -> Boolean = { view ->
    val active = view.state.field(snippetState, require = false)
    if (active != null) {
        view.dispatch(
            TransactionSpec(
                effects = listOf(setActiveSnippet.of(null))
            )
        )
        true
    } else {
        false
    }
}

// ── Keymap ──

/** Default keymap for snippet field navigation: Tab, Shift-Tab, and Escape. */
val snippetKeymap: List<KeyBinding> = listOf(
    KeyBinding(key = "Tab", run = nextSnippetField),
    KeyBinding(key = "Shift-Tab", run = prevSnippetField),
    KeyBinding(key = "Escape", run = clearSnippet)
)

// ── Theme keys ──

/** Background color for inactive snippet fields. */
val snippetFieldBackground = ThemeKey(default = Color(0x44004488))

/** Background color for the active snippet field. */
val snippetFieldActiveBackground = ThemeKey(default = Color(0x66004488))

// ── Decoration plugin ──

private class SnippetDecorationPlugin(private val view: EditorSession) :
    PluginValue,
    DecorationSource {
    override var decorations: DecorationSet = buildDecorations()
        private set

    override fun update(update: ViewUpdate) {
        val active = update.state.field(snippetState, require = false)
        if (active != null || update.docChanged) {
            decorations = buildDecorations()
        }
    }

    private fun buildDecorations(): DecorationSet {
        val active = view.state.field(snippetState, require = false)
            ?: return RangeSet.empty()
        val theme = view.state.facet(editorTheme)
        val fieldDeco = Decoration.mark(
            MarkDecorationSpec(
                cssClass = "cm-snippetField",
                style = SpanStyle(background = theme[snippetFieldBackground])
            )
        )
        val activeDeco = Decoration.mark(
            MarkDecorationSpec(
                cssClass = "cm-snippetFieldActive",
                style = SpanStyle(background = theme[snippetFieldActiveBackground])
            )
        )
        val builder = RangeSetBuilder<Decoration>()
        val sorted = active.fields
            .flatMapIndexed { i, f -> f.ranges.map { i to it } }
            .sortedWith(compareBy({ it.second.from }, { it.second.to }))
        for ((i, range) in sorted) {
            if (range.from == range.to) continue
            val deco = if (i == active.fieldIndex) activeDeco else fieldDeco
            builder.add(range.from, range.to, deco)
        }
        return builder.finish()
    }
}

// ── Entry point ──

/**
 * Returns an extension that enables snippet support: the snippet state
 * field, decoration highlights for active fields, and the Tab/Shift-Tab/Escape
 * keymap for navigating between fields.
 */
fun snippets(): Extension {
    val decoPlugin = ViewPlugin.define(
        create = { view -> SnippetDecorationPlugin(view) },
        configure = {
            copy(decorations = { it.decorations })
        }
    )

    return ExtensionList(
        listOf(
            snippetState,
            decoPlugin.asExtension(),
            keymap.of(snippetKeymap)
        )
    )
}
