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
import com.monkopedia.kodemirror.state.ChangeSpec
import com.monkopedia.kodemirror.state.DocPos
import com.monkopedia.kodemirror.state.Extension
import com.monkopedia.kodemirror.state.ExtensionList
import com.monkopedia.kodemirror.state.InsertContent
import com.monkopedia.kodemirror.state.RangeSet
import com.monkopedia.kodemirror.state.RangeSetBuilder
import com.monkopedia.kodemirror.state.SelectionSpec
import com.monkopedia.kodemirror.state.StateEffect
import com.monkopedia.kodemirror.state.StateEffectType
import com.monkopedia.kodemirror.state.StateField
import com.monkopedia.kodemirror.state.StateFieldSpec
import com.monkopedia.kodemirror.state.TransactionSpec
import com.monkopedia.kodemirror.view.Decoration
import com.monkopedia.kodemirror.view.DecorationSet
import com.monkopedia.kodemirror.view.DecorationSource
import com.monkopedia.kodemirror.view.EditorSession
import com.monkopedia.kodemirror.view.KeyBinding
import com.monkopedia.kodemirror.view.MarkDecorationSpec
import com.monkopedia.kodemirror.view.PluginValue
import com.monkopedia.kodemirror.view.ViewPlugin
import com.monkopedia.kodemirror.view.ViewUpdate
import com.monkopedia.kodemirror.view.keymap

// ── Snippet field representation ──

/** A named field within an active snippet, spanning [from] to [to] in the document. */
data class SnippetField(val name: String, val from: DocPos, val to: DocPos)

/**
 * Tracks the currently active snippet insertion — the list of tab-stop
 * [fields] and the index of the field the cursor is at.
 */
data class ActiveSnippet(
    val fields: List<SnippetField>,
    val fieldIndex: Int
) {
    /** The field the cursor is currently on, or `null` if [fieldIndex] is out of bounds. */
    val currentField: SnippetField? get() = fields.getOrNull(fieldIndex)
}

// ── State effects ──

private val setActiveSnippet: StateEffectType<ActiveSnippet?> = StateEffect.define()

// ── State field ──

/** State field that holds the currently active snippet, or `null` when no snippet is active. */
val snippetState: StateField<ActiveSnippet?> = StateField.define(
    StateFieldSpec(
        create = { null },
        update = { value, tr ->
            var result = value
            for (effect in tr.effects) {
                val snippetEffect = effect.asType(setActiveSnippet)
                if (snippetEffect != null) {
                    result = snippetEffect.value
                }
            }
            if (result != null && tr.docChanged) {
                val mapping = tr.changes
                val mapped = result.fields.map { field ->
                    SnippetField(
                        field.name,
                        mapping.mapPos(field.from),
                        mapping.mapPos(field.to, 1)
                    )
                }
                result = result.copy(fields = mapped)
            }
            result
        }
    )
)

// ── Template parsing ──

private data class ParsedField(val name: String, val index: Int)

private data class ParsedSnippet(
    val parts: List<String>,
    val fields: List<ParsedField>
)

private fun parseTemplate(template: String): ParsedSnippet {
    val parts = mutableListOf<String>()
    val fields = mutableListOf<ParsedField>()
    var i = 0
    val current = StringBuilder()

    while (i < template.length) {
        if (template[i] == '$' && i + 1 < template.length && template[i + 1] == '{') {
            parts.add(current.toString())
            current.clear()
            val start = i + 2
            val end = template.indexOf('}', start)
            if (end == -1) {
                current.append(template.substring(i))
                i = template.length
            } else {
                val name = template.substring(start, end)
                fields.add(ParsedField(name, parts.size - 1))
                i = end + 1
            }
        } else {
            current.append(template[i])
            i++
        }
    }
    parts.add(current.toString())
    return ParsedSnippet(parts, fields)
}

/**
 * Parse a snippet template and return a function that, when called,
 * applies the snippet at the given range in the editor.
 *
 * Template syntax: `${name}` for named fields that the user can
 * tab through. `${}` marks the final cursor position.
 */
fun snippet(template: String): (CompletionApplyContext) -> Unit {
    val parsed = parseTemplate(template)
    return { ctx ->
        val view = ctx.session
        val from = ctx.from
        val to = ctx.to
        val text = StringBuilder()
        val snippetFields = mutableListOf<SnippetField>()
        var offset = from

        for ((i, part) in parsed.parts.withIndex()) {
            text.append(part)
            val fieldHere = parsed.fields.filter { it.index == i }
            for (f in fieldHere) {
                val fieldFrom = offset + text.length
                snippetFields.add(SnippetField(f.name, fieldFrom, fieldFrom))
            }
        }

        val insertText = text.toString()

        val activeSnippet = if (snippetFields.isNotEmpty()) {
            ActiveSnippet(snippetFields, 0)
        } else {
            null
        }

        val effects = if (activeSnippet != null) {
            listOf(setActiveSnippet.of(activeSnippet))
        } else {
            emptyList()
        }

        val cursorPos = if (activeSnippet != null) {
            activeSnippet.currentField?.from ?: (from + insertText.length)
        } else {
            from + insertText.length
        }

        view.dispatch(
            TransactionSpec(
                changes = ChangeSpec.Single(
                    from,
                    to,
                    InsertContent.StringContent(insertText)
                ),
                selection = SelectionSpec.CursorSpec(cursorPos),
                effects = effects,
                userEvent = "input.complete"
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
fun snippetCompletion(template: String, completion: Completion): Completion {
    return completion.copy(applyFn = snippet(template))
}

// ── Snippet state queries ──

/** Whether there is a next snippet field to navigate to. */
fun hasNextSnippetField(state: com.monkopedia.kodemirror.state.EditorState): Boolean {
    val active = state.field(snippetState, require = false)
    return active != null && active.fieldIndex < active.fields.size - 1
}

/** Whether there is a previous snippet field to navigate to. */
fun hasPrevSnippetField(state: com.monkopedia.kodemirror.state.EditorState): Boolean {
    val active = state.field(snippetState, require = false)
    return active != null && active.fieldIndex > 0
}

// ── Commands ──

/** Move to the next snippet field, or clear the snippet if at the last field. */
val nextSnippetField: (EditorSession) -> Boolean = { view ->
    val active = view.state.field(snippetState, require = false)
    if (active != null && active.fieldIndex < active.fields.size - 1) {
        val next = active.fieldIndex + 1
        val field = active.fields[next]
        view.dispatch(
            TransactionSpec(
                selection = SelectionSpec.CursorSpec(field.from, field.to),
                effects = listOf(setActiveSnippet.of(active.copy(fieldIndex = next)))
            )
        )
        true
    } else if (active != null) {
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

/** Move to the previous snippet field. */
val prevSnippetField: (EditorSession) -> Boolean = { view ->
    val active = view.state.field(snippetState, require = false)
    if (active != null && active.fieldIndex > 0) {
        val prev = active.fieldIndex - 1
        val field = active.fields[prev]
        view.dispatch(
            TransactionSpec(
                selection = SelectionSpec.CursorSpec(field.from, field.to),
                effects = listOf(setActiveSnippet.of(active.copy(fieldIndex = prev)))
            )
        )
        true
    } else {
        false
    }
}

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

// ── Decoration plugin ──

private val snippetFieldDecoration = Decoration.mark(
    MarkDecorationSpec(
        cssClass = "cm-snippetField",
        style = SpanStyle(
            background = Color(0x44004488)
        )
    )
)

private val snippetFieldActiveDecoration = Decoration.mark(
    MarkDecorationSpec(
        cssClass = "cm-snippetFieldActive",
        style = SpanStyle(
            background = Color(0x66004488)
        )
    )
)

private class SnippetDecorationPlugin(
    private val view: EditorSession
) : PluginValue, DecorationSource {
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
        val builder = RangeSetBuilder<Decoration>()
        val sorted = active.fields
            .mapIndexed { i, f -> i to f }
            .sortedWith(compareBy({ it.second.from }, { it.second.to }))
        for ((i, field) in sorted) {
            if (field.from == field.to) continue
            val deco = if (i == active.fieldIndex) {
                snippetFieldActiveDecoration
            } else {
                snippetFieldDecoration
            }
            builder.add(field.from, field.to, deco)
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
