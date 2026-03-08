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

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.monkopedia.kodemirror.state.ChangeSpec
import com.monkopedia.kodemirror.state.EditorState
import com.monkopedia.kodemirror.state.Extension
import com.monkopedia.kodemirror.state.ExtensionList
import com.monkopedia.kodemirror.state.InsertContent
import com.monkopedia.kodemirror.state.SelectionSpec
import com.monkopedia.kodemirror.state.Slot
import com.monkopedia.kodemirror.state.TransactionSpec
import com.monkopedia.kodemirror.view.EditorSession
import com.monkopedia.kodemirror.view.KeyBinding
import com.monkopedia.kodemirror.view.PluginValue
import com.monkopedia.kodemirror.view.Tooltip
import com.monkopedia.kodemirror.view.ViewPlugin
import com.monkopedia.kodemirror.view.ViewUpdate
import com.monkopedia.kodemirror.view.keymap
import com.monkopedia.kodemirror.view.showTooltip

// ── Commands ──

/** Explicitly start completion (Ctrl-Space). */
val startCompletion: (EditorSession) -> Boolean = { view ->
    triggerCompletion(view, explicit = true)
    true
}

/** Close the completion list. */
val closeCompletion: (EditorSession) -> Boolean = { view ->
    val cs = view.state.field(completionStateField, require = false)
    if (cs != null && cs.open) {
        view.dispatch(
            TransactionSpec(effects = listOf(closeCompletionEffect.of(Unit)))
        )
        true
    } else {
        false
    }
}

/** Accept the currently selected completion. */
val acceptCompletion: (EditorSession) -> Boolean = { view ->
    val cs = view.state.field(completionStateField, require = false)
    if (cs != null && cs.open && cs.filtered.isNotEmpty()) {
        val completion = cs.filtered[cs.selected].completion
        applyCompletion(view, completion, cs.result!!)
        true
    } else {
        false
    }
}

/** Move the completion selection. */
fun moveCompletionSelection(forward: Boolean, by: String = "option"): (EditorSession) -> Boolean =
    { view ->
        val cs = view.state.field(completionStateField, require = false)
        if (cs != null && cs.open && cs.filtered.isNotEmpty()) {
            val delta = when (by) {
                "page" -> if (forward) 10 else -10
                else -> if (forward) 1 else -1
            }
            val newIndex = (cs.selected + delta).mod(cs.filtered.size)
            view.dispatch(
                TransactionSpec(
                    effects = listOf(setSelectedCompletion.of(newIndex))
                )
            )
            true
        } else {
            false
        }
    }

// ── Keymap ──

/** Default autocompletion keymap bindings. */
val completionKeymap: List<KeyBinding> = listOf(
    KeyBinding(key = "Ctrl-Space", run = startCompletion),
    KeyBinding(key = "Escape", run = closeCompletion),
    KeyBinding(key = "ArrowDown", run = moveCompletionSelection(forward = true)),
    KeyBinding(key = "ArrowUp", run = moveCompletionSelection(forward = false)),
    KeyBinding(key = "PageDown", run = moveCompletionSelection(forward = true, by = "page")),
    KeyBinding(key = "PageUp", run = moveCompletionSelection(forward = false, by = "page")),
    KeyBinding(key = "Enter", run = acceptCompletion)
)

// ── Internal helpers ──

private fun triggerCompletion(view: EditorSession, explicit: Boolean) {
    val state = view.state
    val config = state.facet(completionConfig)
    val pos = state.selection.main.head
    val ctx = CompletionContext(state, pos, explicit)
    val sources = config.override ?: emptyList()

    for (source in sources) {
        val result = source(ctx)
        if (result != null && result.options.isNotEmpty()) {
            view.dispatch(
                TransactionSpec(
                    effects = listOf(startCompletionEffect.of(result))
                )
            )
            return
        }
    }
    // No results, close if open
    if (explicit) {
        view.dispatch(
            TransactionSpec(effects = listOf(closeCompletionEffect.of(Unit)))
        )
    }
}

private fun applyCompletion(view: EditorSession, completion: Completion, result: CompletionResult) {
    val fn = completion.applyFn
    if (fn != null) {
        fn(
            CompletionApplyContext(
                view,
                completion,
                result.from,
                result.to ?: view.state.selection.main.head
            )
        )
        return
    }
    val text = completion.apply ?: completion.label
    val from = result.from
    val to = result.to ?: view.state.selection.main.head
    view.dispatch(
        TransactionSpec(
            changes = ChangeSpec.Single(from, to, InsertContent.StringContent(text)),
            selection = SelectionSpec.CursorSpec(from + text.length),
            effects = listOf(closeCompletionEffect.of(Unit)),
            annotations = listOf(pickedCompletion.of(completion)),
            userEvent = "input.complete"
        )
    )
}

/** Insert completion text into the document (utility for custom apply functions). */
fun insertCompletionText(state: EditorState, text: String, from: Int, to: Int): TransactionSpec =
    TransactionSpec(
        changes = ChangeSpec.Single(from, to, InsertContent.StringContent(text)),
        selection = SelectionSpec.CursorSpec(from + text.length),
        userEvent = "input.complete"
    )

// ── ViewPlugin ──

private class CompletionPlugin(
    private val view: EditorSession
) : PluginValue {
    override fun update(update: ViewUpdate) {
        val config = update.state.facet(completionConfig)
        if (!config.activateOnTyping) return

        // Trigger completion on text input
        if (update.docChanged) {
            for (tr in update.transactions) {
                if (tr.isUserEvent("input") && !tr.isUserEvent("input.complete")) {
                    triggerCompletion(view, explicit = false)
                    return
                }
            }
        }
    }
}

// ── Composable UI ──

@Suppress("ktlint:standard:function-naming")
@Composable
private fun CompletionList(
    view: EditorSession,
    completionState: CompletionState,
    config: CompletionConfig
) {
    val items = completionState.filtered.take(config.maxRenderedOptions)
    val result = completionState.result ?: return

    Column(
        modifier = Modifier.background(Color.White).padding(2.dp)
    ) {
        LazyColumn {
            itemsIndexed(items) { index, item ->
                val isSelected = index == completionState.selected
                Row(
                    modifier = Modifier
                        .background(if (isSelected) Color(0xFFE0E0FF) else Color.Transparent)
                        .clickable { applyCompletion(view, item.completion, result) }
                        .padding(horizontal = 4.dp, vertical = 2.dp)
                ) {
                    if (config.icons && item.completion.type != null) {
                        BasicText(
                            text = typeIcon(item.completion.type),
                            modifier = Modifier.width(20.dp)
                        )
                    }
                    BasicText(
                        text = buildHighlightedLabel(
                            item.completion.displayLabel ?: item.completion.label,
                            item.highlighted
                        )
                    )
                    if (item.completion.detail != null) {
                        BasicText(
                            text = " ${item.completion.detail}",
                            modifier = Modifier.padding(start = 4.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun buildHighlightedLabel(label: String, highlights: List<IntRange>) =
    buildAnnotatedString {
        var pos = 0
        for (range in highlights) {
            if (pos < range.first) {
                append(label.substring(pos, range.first))
            }
            withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
                append(label.substring(range.first, range.last + 1))
            }
            pos = range.last + 1
        }
        if (pos < label.length) {
            append(label.substring(pos))
        }
    }

private fun typeIcon(type: String): String = when (type) {
    "function", "method" -> "f"
    "class" -> "C"
    "interface" -> "I"
    "variable" -> "v"
    "constant" -> "c"
    "type" -> "T"
    "enum" -> "E"
    "property" -> "p"
    "keyword" -> "k"
    "namespace" -> "N"
    else -> "\u00b7"
}

// ── Completion helpers ──

/** Create a completion source from a static list. */
fun completeFromList(list: List<Completion>): CompletionSource = { ctx ->
    val wordMatch = ctx.matchBefore(Regex("[\\w$]+"))
    if (wordMatch != null || ctx.explicit) {
        CompletionResult(
            from = wordMatch?.from ?: ctx.pos,
            options = list,
            validFor = Regex("[\\w$]*")
        )
    } else {
        null
    }
}

/** A completion source that provides all words in the document. */
val completeAnyWord: CompletionSource = { ctx ->
    val wordMatch = ctx.matchBefore(Regex("[\\w$]+"))
    if (wordMatch != null && wordMatch.text.length >= 2) {
        val word = wordMatch.text
        val doc = ctx.state.doc
        val text = doc.sliceString(0)
        val words = Regex("[\\w$]{2,}").findAll(text)
            .map { it.value }
            .filter { it != word }
            .distinct()
            .map { Completion(label = it, type = "text") }
            .toList()
        CompletionResult(
            from = wordMatch.from,
            options = words,
            validFor = Regex("[\\w$]*")
        )
    } else {
        null
    }
}

// ── Context-aware source wrappers ──

/**
 * Wrap a [CompletionSource] so it only activates when the cursor is inside
 * one of the given syntax node [types].
 */
fun ifIn(types: List<String>, source: CompletionSource): CompletionSource = { ctx ->
    if (ctx.tokenBefore(types) != null) source(ctx) else null
}

/**
 * Wrap a [CompletionSource] so it only activates when the cursor is NOT
 * inside one of the given syntax node [types].
 */
fun ifNotIn(types: List<String>, source: CompletionSource): CompletionSource = { ctx ->
    if (ctx.tokenBefore(types) == null) source(ctx) else null
}

// ── Entry point ──

/**
 * Create the autocompletion extension.
 *
 * @param config Optional configuration.
 */
fun autocompletion(config: CompletionConfig = CompletionConfig()): Extension {
    val plugin = ViewPlugin.define(
        create = { view -> CompletionPlugin(view) }
    )

    val tooltipProvider = showTooltip.compute(
        listOf(Slot.FieldSlot(completionStateField))
    ) { state ->
        val cs = state.field(completionStateField, require = false)
        if (cs != null && cs.open && cs.filtered.isNotEmpty() && cs.result != null) {
            Tooltip(pos = cs.result.from) {
                val editorView = com.monkopedia.kodemirror.view.LocalEditorSession.current
                val currentConfig = editorView.state.facet(completionConfig)
                val currentCs = editorView.state.field(completionStateField, require = false)
                    ?: cs
                CompletionList(editorView, currentCs, currentConfig)
            }
        } else {
            null
        }
    }

    return ExtensionList(
        listOf(
            completionConfig.of(config),
            completionStateField,
            plugin.asExtension(),
            tooltipProvider,
            keymap.of(completionKeymap)
        )
    )
}
