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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.monkopedia.kodemirror.state.ChangeSpec
import com.monkopedia.kodemirror.state.DocPos
import com.monkopedia.kodemirror.state.EditorState
import com.monkopedia.kodemirror.state.Extension
import com.monkopedia.kodemirror.state.ExtensionList
import com.monkopedia.kodemirror.state.InsertContent
import com.monkopedia.kodemirror.state.SelectionSpec
import com.monkopedia.kodemirror.state.Slot
import com.monkopedia.kodemirror.state.TransactionSpec
import com.monkopedia.kodemirror.view.EditorSession
import com.monkopedia.kodemirror.view.KeyBinding
import com.monkopedia.kodemirror.view.LocalEditorTheme
import com.monkopedia.kodemirror.view.PluginValue
import com.monkopedia.kodemirror.view.ThemeKey
import com.monkopedia.kodemirror.view.Tooltip
import com.monkopedia.kodemirror.view.ViewPlugin
import com.monkopedia.kodemirror.view.ViewUpdate
import kotlinx.coroutines.launch
import com.monkopedia.kodemirror.view.keymap
import com.monkopedia.kodemirror.view.showTooltip

/** Background color for the completion popup. */
val completionBackground = ThemeKey(default = Color(0xFF353A42))

/** Background color for the selected completion item. */
val completionSelectedBackground = ThemeKey(default = Color(0xFF3E4451))

/** Text color for completion labels. */
val completionTextColor = ThemeKey(default = Color(0xFFABB2BF))

/** Text color for completion detail/info text. */
val completionDetailColor = ThemeKey(default = Color(0xFF7F848E))

/** Text color for completion type icons. */
val completionIconColor = ThemeKey(default = Color(0xFF61AFEF))

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
    val asyncSources = config.asyncOverride ?: emptyList()
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
    // Async (suspend) sources — e.g. a language server. Launched on the editor's
    // coroutine scope; the result is dispatched when it resolves. This is the
    // wasmJs-safe path (no blocking bridge). See CompletionConfig.asyncOverride.
    if (asyncSources.isNotEmpty()) {
        view.coroutineScope.launch {
            for (source in asyncSources) {
                val result = source(ctx)
                if (result != null && result.options.isNotEmpty()) {
                    view.dispatch(
                        TransactionSpec(
                            effects = listOf(startCompletionEffect.of(result))
                        )
                    )
                    return@launch
                }
            }
            if (explicit) {
                view.dispatch(
                    TransactionSpec(effects = listOf(closeCompletionEffect.of(Unit)))
                )
            }
        }
        return
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
fun insertCompletionText(
    state: EditorState,
    text: String,
    from: DocPos,
    to: DocPos
): TransactionSpec = TransactionSpec(
    changes = ChangeSpec.Single(from, to, InsertContent.StringContent(text)),
    selection = SelectionSpec.CursorSpec(from + text.length),
    userEvent = "input.complete"
)

// ── ViewPlugin ──

private class CompletionPlugin(private val view: EditorSession) : PluginValue {
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
    val theme = LocalEditorTheme.current

    // A plain Column (not LazyColumn) so the popup wraps-content to the WIDEST
    // row: a LazyColumn does not wrap-content on the cross axis, so under this
    // wrap-content parent it collapsed to the min width and clipped every label
    // to ~0 (only the fixed-width type icon showed) (#109). It also avoids the
    // unbounded-height LazyColumn collapse (cf. #33). The list is capped at
    // config.maxRenderedOptions, so a non-lazy column + verticalScroll is fine.
    Column(
        modifier = Modifier
            .background(theme[completionBackground])
            // Bounded width (not pure wrap-content): on Compose-wasmJs a BasicText
            // with no width modifier measures to ~0 intrinsic width, so the label
            // needs a CONCRETE width to paint (the fixed-width icon survives, a
            // width-less label collapses to blank). A bounded Column lets the row's
            // fillMaxWidth + the label's weight(1f) resolve to a real width (#109).
            .widthIn(min = 200.dp, max = 360.dp)
            .heightIn(max = 240.dp)
            .verticalScroll(rememberScrollState())
            .padding(2.dp)
    ) {
        items.forEachIndexed { index, item ->
            val isSelected = index == completionState.selected
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        if (isSelected) {
                            theme[completionSelectedBackground]
                        } else {
                            Color.Transparent
                        }
                    )
                    .clickable { applyCompletion(view, item.completion, result) }
                    .padding(horizontal = 4.dp, vertical = 2.dp)
            ) {
                val textColor = theme[completionTextColor]
                if (config.icons && item.completion.type != null) {
                    BasicText(
                        text = typeIcon(item.completion.type),
                        style = TextStyle(color = theme[completionIconColor]),
                        modifier = Modifier.width(20.dp)
                    )
                }
                // weight(1f) gives the label text a concrete width within the bounded
                // row so it paints — a width-less BasicText collapses to ~0 on wasmJs.
                // Detail (if any) is folded into the same text rather than a separate
                // width-less BasicText (which would likewise collapse). Plain String,
                // not AnnotatedString — BasicText(AnnotatedString) also failed to paint
                // here, so prefix-match bold highlighting is dropped for now (#111).
                val label = item.completion.displayLabel ?: item.completion.label
                val text = item.completion.detail?.let { "$label  $it" } ?: label
                BasicText(
                    text = text,
                    style = TextStyle(color = textColor),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
            }
        }
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
        val text = doc.sliceString(DocPos.ZERO)
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
            Tooltip(pos = cs.result.from.value) {
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
        buildList {
            add(completionConfig.of(config))
            add(completionStateField)
            add(plugin.asExtension())
            add(tooltipProvider)
            if (config.defaultKeymap) {
                add(keymap.of(completionKeymap))
            }
        }
    )
}
