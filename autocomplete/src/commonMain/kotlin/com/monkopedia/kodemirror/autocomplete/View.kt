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

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.monkopedia.kodemirror.state.ChangeByRangeResult
import com.monkopedia.kodemirror.state.ChangeSpec
import com.monkopedia.kodemirror.state.DocPos
import com.monkopedia.kodemirror.state.EditorSelection
import com.monkopedia.kodemirror.state.EditorState
import com.monkopedia.kodemirror.state.Extension
import com.monkopedia.kodemirror.state.ExtensionList
import com.monkopedia.kodemirror.state.InsertContent
import com.monkopedia.kodemirror.state.LanguageDataKey
import com.monkopedia.kodemirror.state.Slot
import com.monkopedia.kodemirror.state.TransactionSpec
import com.monkopedia.kodemirror.state.codePointAt
import com.monkopedia.kodemirror.state.codePointSize
import com.monkopedia.kodemirror.state.fromCodePoint
import com.monkopedia.kodemirror.view.EditorSession
import com.monkopedia.kodemirror.view.KeyBinding
import com.monkopedia.kodemirror.view.LocalContentTextStyle
import com.monkopedia.kodemirror.view.LocalEditorSession
import com.monkopedia.kodemirror.view.LocalEditorTheme
import com.monkopedia.kodemirror.view.PluginValue
import com.monkopedia.kodemirror.view.ThemeKey
import com.monkopedia.kodemirror.view.Tooltip
import com.monkopedia.kodemirror.view.ViewPlugin
import com.monkopedia.kodemirror.view.ViewUpdate
import com.monkopedia.kodemirror.view.keymap
import com.monkopedia.kodemirror.view.showTooltip
import kotlinx.coroutines.launch

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
        val item = cs.filtered[cs.selected]
        // Apply over the option's OWN source result range — sources can return
        // different from..to, so we must not assume a single shared result (#137).
        val result = item.result ?: cs.results.first()
        applyCompletion(view, item.completion, result)
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
    // Query ALL sync sources and MERGE their results, rather than stopping at the
    // first non-empty one. The merge (dedup by label, score-ordered, per-source
    // filter/validFor/from) happens in the state field via mergeCompletions (#137).
    val results = sources.mapNotNull { source -> source(ctx) }
        .filter { it.options.isNotEmpty() }
    if (results.isNotEmpty()) {
        view.dispatch(
            TransactionSpec(
                effects = listOf(startCompletionEffect.of(StartCompletion(results, explicit)))
            )
        )
        return
    }
    // Async (suspend) sources — e.g. a language server. Launched on the editor's
    // coroutine scope; the result is dispatched when it resolves. This is the
    // wasmJs-safe path (no blocking bridge). See CompletionConfig.asyncOverride.
    // The async path stays first-non-empty-wins (not merged): async sources are
    // typically a single language server, and merging would force awaiting every
    // source serially before showing anything. A single result still flows through
    // the same merge machinery as a singleton list.
    if (asyncSources.isNotEmpty()) {
        // Capture the trigger position so we can detect, when the source resolves,
        // whether the doc/selection drifted while the coroutine was suspended (#153).
        val triggerPos = pos
        view.coroutineScope.launch {
            for (source in asyncSources) {
                val result = source(ctx)
                if (result != null && result.options.isNotEmpty()) {
                    // The source may have suspended across doc/selection edits (the
                    // user kept typing, moved the cursor, or closed the popup). A
                    // result that no longer applies to the LIVE state must be dropped
                    // rather than re-opening the popup — otherwise an in-flight LSP
                    // request resolving after an Escape re-opens it, demanding another
                    // Escape (#153). Survivors open the popup exactly as before.
                    if (!asyncResultApplicable(view.state, result, triggerPos)) {
                        return@launch
                    }
                    view.dispatch(
                        TransactionSpec(
                            effects = listOf(
                                startCompletionEffect.of(StartCompletion(listOf(result), explicit))
                            )
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

/**
 * Whether an async completion [result] that resolved after suspending is still
 * applicable to the current [state]. The coroutine may have suspended across
 * doc/selection changes since launch, so this re-checks against live state before
 * the popup opens — mirroring the in-place re-filter in [completionStateField]
 * (anchored `validFor.matches` over the `from`..head span, #139). A result is
 * dropped (returns `false`) when:
 *   * the primary selection is no longer an empty cursor (a range was selected, or
 *     the context otherwise moved), or
 *   * the cursor moved before the result's [CompletionResult.from] (`head < from`), or
 *   * the text from `from` to the cursor no longer matches [CompletionResult.validFor]
 *     (the typed token changed or cleared). When `validFor` is absent there is no
 *     reuse rule, so the result is valid only at the unchanged [triggerPos].
 */
private fun asyncResultApplicable(
    state: EditorState,
    result: CompletionResult,
    triggerPos: DocPos
): Boolean {
    val main = state.selection.main
    if (!main.empty) return false
    val head = main.head
    if (head < result.from) return false
    val validFor = result.validFor
        ?: return head == triggerPos
    return validFor.matches(state.doc.sliceString(result.from, head))
}

private fun applyCompletion(view: EditorSession, completion: Completion, result: CompletionResult) {
    // Clamp the stored result range to the CURRENT document. result.from/to are
    // captured when the completion is requested; if the doc shrank since (the user
    // backspaced while the popup was open, or a shorter re-sync landed) they can
    // point past the doc end and crash dispatch with "Invalid position" (#124).
    val docLen = DocPos(view.state.doc.length)
    val from = result.from.coerceIn(DocPos.ZERO, docLen)
    val to = (result.to ?: view.state.selection.main.head).coerceIn(from, docLen)
    val fn = completion.applyFn
    if (fn != null) {
        fn(
            CompletionApplyContext(
                view,
                completion,
                from,
                to
            )
        )
        return
    }
    val text = completion.apply ?: completion.label
    val base = insertCompletionText(view.state, text, from, to)
    view.dispatch(
        base.copy(
            effects = base.effects.orEmpty() + closeCompletionEffect.of(Unit),
            annotations = listOf(pickedCompletion.of(completion))
        )
    )
}

/**
 * Insert completion text into the document (utility for custom apply functions).
 *
 * The completion is applied at every selection range whose typed prefix matches the
 * one being replaced at the primary range, mirroring upstream's multi-cursor apply.
 * [from]/[to] describe the range replaced at the *primary* cursor; for each other
 * range the same relative span is replaced only when the doc text there equals the
 * primary span (i.e. the same token is being typed at that cursor). Ranges that don't
 * match are left untouched. With a single selection this reduces to a plain insert.
 */
fun insertCompletionText(
    state: EditorState,
    text: String,
    from: DocPos,
    to: DocPos
): TransactionSpec {
    val main = state.selection.main
    val fromOff = from - main.from
    val toOff = to - main.from
    val spec = state.changeByRange { range ->
        // Secondary ranges only get the completion when the text immediately around
        // them matches what's being replaced at the primary range. Empty primary
        // spans (from == to) always apply (nothing distinguishing to match).
        if (range != main &&
            from != to &&
            state.sliceDoc(range.from + fromOff, range.from + toOff) != state.sliceDoc(from, to)
        ) {
            ChangeByRangeResult(range = range)
        } else {
            // Split through the state's line separator, as the doc does: a CRLF
            // in [text] becomes one doc line break, so the cursor must be placed
            // by the inserted Text's length, not the raw string's.
            val lines = state.toText(text)
            ChangeByRangeResult(
                changes = ChangeSpec.Single(
                    range.from + fromOff,
                    if (to == main.from) range.to else range.from + toOff,
                    InsertContent.TextContent(lines)
                ),
                range = EditorSelection.cursor(range.from + fromOff + lines.length)
            )
        }
    }
    return spec.copy(userEvent = "input.complete")
}

// ── ViewPlugin ──

private class CompletionPlugin(private val view: EditorSession) : PluginValue {
    override fun update(update: ViewUpdate) {
        val config = update.state.facet(completionConfig)
        if (!config.activateOnTyping) return

        // Trigger completion on text input
        if (update.docChanged) {
            for (tr in update.transactions) {
                if (tr.isUserEvent("input") && !tr.isUserEvent("input.complete")) {
                    // If completion is STILL open after this edit, the state field
                    // already re-filtered the existing result against the typed text
                    // (the input matched its validFor) — narrowing happened in-place.
                    // Re-querying here would replace that narrowed list with a fresh
                    // full result and reset the apply range, which is exactly the
                    // "doesn't filter / inserts `.xplus`" bug (#114). Only (re-)trigger
                    // when completion is closed (or the edit closed it because the text
                    // no longer matched validFor) — that starts a fresh query.
                    val cs = update.state.field(completionStateField, require = false)
                    if (cs == null || !cs.open) {
                        triggerCompletion(view, explicit = false)
                    }
                    return
                }
            }
        }
    }
}

// ── Composable UI ──

@OptIn(ExperimentalFoundationApi::class)
@Suppress("ktlint:standard:function-naming")
@Composable
private fun CompletionList(
    view: EditorSession,
    completionState: CompletionState,
    config: CompletionConfig
) {
    val items = completionState.filtered.take(config.maxRenderedOptions)
    if (completionState.results.isEmpty()) return
    val theme = LocalEditorTheme.current
    // Render the popup in the editor's content font, like every other piece of
    // editor UI chrome (gutter, panels, placeholder all read LocalContentTextStyle).
    // This keeps the popup visually consistent with the editor and — because the
    // showcase / screenshot tests pin a concrete font via editorContentStyle — makes
    // the AnnotatedString label paint in a deterministic font (#111).
    val contentFontFamily = LocalContentTextStyle.current.fontFamily

    // Keep the selected row visible as the user arrow-navigates — the
    // verticalScroll Column does not follow the selection on its own (#115).
    // The requester is attached to whichever row is currently selected, so
    // bringIntoView() on selection change scrolls that row into the viewport.
    val bringIntoViewRequester = remember { BringIntoViewRequester() }
    LaunchedEffect(completionState.selected) {
        bringIntoViewRequester.bringIntoView()
    }

    // A plain Column (not LazyColumn) so the popup wraps-content to the WIDEST
    // row: a LazyColumn does not wrap-content on the cross axis, so under this
    // wrap-content parent it collapsed to the min width and clipped every label
    // to ~0 (only the fixed-width type icon showed) (#109). It also avoids the
    // unbounded-height LazyColumn collapse (cf. #33). The list is capped at
    // config.maxRenderedOptions, so a non-lazy column + verticalScroll is fine.
    Column(
        modifier = Modifier
            .testTag("completionPopup")
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
                    .then(
                        if (isSelected) {
                            Modifier.bringIntoViewRequester(bringIntoViewRequester)
                        } else {
                            Modifier
                        }
                    )
                    .fillMaxWidth()
                    .background(
                        if (isSelected) {
                            theme[completionSelectedBackground]
                        } else {
                            Color.Transparent
                        }
                    )
                    .clickable {
                        applyCompletion(
                            view,
                            item.completion,
                            item.result ?: completionState.results.first()
                        )
                    }
                    .padding(horizontal = 4.dp, vertical = 2.dp)
            ) {
                val textColor = theme[completionTextColor]
                if (config.icons && item.completion.type != null) {
                    BasicText(
                        text = typeIcon(item.completion.type),
                        style = TextStyle(
                            color = theme[completionIconColor],
                            fontFamily = contentFontFamily
                        ),
                        modifier = Modifier.width(20.dp)
                    )
                }
                // weight(1f) gives the label text a concrete width within the bounded
                // row so it paints — a width-less BasicText collapses to ~0 on wasmJs.
                // Detail (if any) is folded into the same AnnotatedString rather than a
                // separate width-less BasicText (which would likewise collapse).
                // The matched-prefix ranges (item.highlighted) are rendered bold (#111).
                // The earlier #109 "AnnotatedString doesn't paint on wasmJs" was an
                // intrinsic-width-0 measurement collapse, not a paint failure: with a
                // bounded popup width + weight(1f) the AnnotatedString now resolves to a
                // real width and paints on both jvm and wasmJs.
                val label = item.completion.displayLabel ?: item.completion.label
                BasicText(
                    text = buildHighlightedLabel(label, item.completion.detail, item.highlighted),
                    style = TextStyle(color = textColor, fontFamily = contentFontFamily),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

/**
 * Build the popup label as an [AnnotatedString] with the matched-prefix
 * [highlighted] ranges rendered bold, optionally folding [detail] on the end.
 * The [highlighted] ranges index into [label] and come from the fuzzy matcher
 * ([FilterResult.highlighted]); they are clamped defensively so a stale range
 * can never throw. [detail] (when present) is appended after the label and left
 * unstyled.
 */
private fun buildHighlightedLabel(
    label: String,
    detail: String?,
    highlighted: List<IntRange>
): AnnotatedString = buildAnnotatedString {
    append(label)
    for (range in highlighted) {
        val start = range.first.coerceIn(0, label.length)
        val end = (range.last + 1).coerceIn(start, label.length)
        if (start < end) {
            addStyle(SpanStyle(fontWeight = FontWeight.Bold), start, end)
        }
    }
    if (detail != null) {
        append("  ")
        append(detail)
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

private val wordLabel = Regex("\\w+")

/** Characters that must be escaped inside a regex character class on every platform. */
private const val CLASS_SPECIAL = "\\]-[^&"

private fun escapeForClass(c: Char): String = if (c in CLASS_SPECIAL) "\\$c" else "$c"

/**
 * Build the regex character class for [chars], as upstream's `toSet`: word
 * characters collapse into `\w`, and the rest are escaped where a class
 * needs it. An empty set yields a class that matches nothing.
 */
private fun charClass(chars: Set<Char>): String {
    if (chars.isEmpty()) return "[^\\s\\S]"
    val (word, other) = chars.partition { wordLabel.matches(it.toString()) }
    val escaped = other.joinToString("", transform = ::escapeForClass)
    return "[${if (word.isNotEmpty()) "\\w" else ""}$escaped]"
}

/** Create a completion source from a static list. */
fun completeFromList(list: List<Completion>): CompletionSource {
    // Upstream derives the token pattern from the labels' own characters, so a
    // label like `@media` matches the `@` typed before the cursor.
    val (validFor, match) = if (list.all { wordLabel.matches(it.label) }) {
        Regex("\\w*") to wordLabel
    } else {
        val first = list.mapNotNull { it.label.firstOrNull() }.toSet()
        val rest = list.flatMap { it.label.drop(1).toList() }.toSet()
        Regex(charClass(first) + charClass(rest) + "*").let { it to it }
    }
    return { ctx ->
        val token = ctx.matchBefore(match)
        if (token != null || ctx.explicit) {
            CompletionResult(
                from = token?.from ?: ctx.pos,
                options = list,
                validFor = validFor
            )
        } else {
            null
        }
    }
}

private const val MAX_WORD_LIST = 2000

/**
 * The runs of upstream's `[\p{Alphabetic}\p{Number}_<wordChars>]+` in [text]. Per platform, since
 * no target's Kotlin Regex supports that pattern, and Kotlin/Wasm's classifies astral code points
 * by their low 16 bits (U+1F914 would match `\p{L}` as U+F914).
 */
internal expect fun findWords(text: String, wordChars: String): List<IntRange>

/** [findWords] for a platform that classifies by code point with [isWordChar]. */
internal inline fun wordRuns(
    text: String,
    wordChars: String,
    isWordChar: (codePoint: Int) -> Boolean
): List<IntRange> {
    val runs = mutableListOf<IntRange>()
    var runStart = -1
    var i = 0
    while (i < text.length) {
        val cp = codePointAt(text, i)
        if (cp == '_'.code || isWordChar(cp) || fromCodePoint(cp) in wordChars) {
            if (runStart < 0) runStart = i
        } else if (runStart >= 0) {
            runs += runStart until i
            runStart = -1
        }
        i += codePointSize(cp)
    }
    if (runStart >= 0) runs += runStart until text.length
    return runs
}

/**
 * A completion source that provides the words in the document (letters, digits, `_` and the
 * language's `wordChars`), except the one being typed at the cursor.
 */
val completeAnyWord: CompletionSource = { ctx ->
    // Scanning the whole document at once, so leave out `\n`: upstream scans line by line.
    val wordChars = (
        ctx.state.languageDataAt(LanguageDataKey.WORD_CHARS, ctx.pos).firstOrNull() ?: ""
        ).replace("\n", "")
    val doc = ctx.state.doc
    val start = maxOf(doc.lineAt(ctx.pos).from, ctx.pos - MATCH_BEFORE_RANGE)
    val before = doc.sliceString(start, ctx.pos)
    val token = findWords(before, wordChars).lastOrNull()?.takeIf { it.last == before.length - 1 }
    if (token == null && !ctx.explicit) {
        null
    } else {
        val from = token?.let { start + it.first } ?: ctx.pos
        val text = doc.toString()
        val seen = mutableSetOf<String>()
        val options = mutableListOf<Completion>()
        for (range in findWords(text, wordChars)) {
            val word = text.substring(range)
            if (range.first != from.value && seen.add(word)) {
                options += Completion(label = word, type = "text")
                if (options.size >= MAX_WORD_LIST) break
            }
        }
        // validFor must be a Regex, so restrict it to the BMP, where every engine classifies
        // correctly, and to letters and numbers, a subset of upstream's class. Text it rejects
        // only re-runs this source, which then gets the word right.
        val escaped = wordChars.map(::escapeForClass).joinToString("")
        val validFor = Regex("[\\p{L}\\p{N}_$escaped&&[\\u0000-\\uffff]]+")
        CompletionResult(from = from, options = options, validFor = validFor)
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
        val csFrom = cs?.from
        if (cs != null && cs.open && cs.filtered.isNotEmpty() && csFrom != null) {
            // Anchor the popup at the smallest from across the merged sources.
            Tooltip(pos = csFrom.value) {
                val editorView = LocalEditorSession.current
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
