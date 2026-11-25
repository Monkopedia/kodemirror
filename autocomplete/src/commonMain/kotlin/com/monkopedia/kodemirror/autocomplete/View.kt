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
import com.monkopedia.kodemirror.state.Text
import com.monkopedia.kodemirror.state.Transaction
import com.monkopedia.kodemirror.state.TransactionSpec
import com.monkopedia.kodemirror.state.codePointAt
import com.monkopedia.kodemirror.state.codePointSize
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
import com.monkopedia.kodemirror.view.logException
import com.monkopedia.kodemirror.view.showTooltip
import kotlin.concurrent.Volatile
import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeSource
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.isActive
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
    if (view.state.field(completionStateField, require = false) == null) {
        false
    } else {
        view.dispatch(TransactionSpec(effects = listOf(startCompletionEffect.of(true))))
        true
    }
}

/** Close the completion list, and stop any query in flight. */
val closeCompletion: (EditorSession) -> Boolean = { view ->
    val cs = view.state.field(completionStateField, require = false)
    if (cs == null || cs.active.none { it.state != SourceState.Inactive }) {
        false
    } else {
        view.dispatch(TransactionSpec(effects = listOf(closeCompletionEffect.of(Unit))))
        true
    }
}

/** Accept the currently selected completion. */
val acceptCompletion: (EditorSession) -> Boolean = { view ->
    val open = view.state.field(completionStateField, require = false)?.open
    if (view.state.readOnly || open == null || open.selected < 0 || open.disabled) {
        false
    } else {
        applyCompletion(view, open.options[open.selected])
    }
}

/** How far a "page" move goes; upstream measures the rendered list. */
private const val PAGE_STEP = 10

/** Move the completion selection. */
fun moveCompletionSelection(forward: Boolean, by: String = "option"): (EditorSession) -> Boolean =
    { view ->
        val open = view.state.field(completionStateField, require = false)?.open
        if (open == null || open.disabled) {
            false
        } else {
            val page = by == "page"
            val step = (if (page) PAGE_STEP else 1) * (if (forward) 1 else -1)
            val length = open.options.size
            var selected = when {
                open.selected > -1 -> open.selected + step
                forward -> 0
                else -> length - 1
            }
            if (selected < 0) {
                selected = if (page) 0 else length - 1
            } else if (selected >= length) {
                selected = if (page) length - 1 else 0
            }
            view.dispatch(TransactionSpec(effects = listOf(setSelectedCompletion.of(selected))))
            true
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

/**
 * Run one source's [query], reporting a throw to the exception sink and treating it as
 * no result, so one failing source neither hides the others' options nor fails the
 * editor's coroutine scope, which would end every later async query (#430). Upstream
 * closes the whole list on a rejected query instead.
 */
private inline fun EditorSession.queryLogged(query: () -> CompletionResult?): CompletionResult? =
    try {
        query()
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        logException(state, e)
        null
    }

/**
 * Apply [option] over its source's current range, as upstream's `applyCompletion`.
 * False when that source no longer has a result.
 */
private fun applyCompletion(view: EditorSession, option: FilterResult): Boolean {
    val result = view.state.field(completionStateField).active
        .find { it.source == option.source } as? ActiveResult ?: return false
    val completion = option.completion
    val fn = completion.applyFn
    if (fn != null) {
        fn(CompletionApplyContext(view, completion, result.from, result.to))
    } else {
        val text = completion.apply ?: completion.label
        view.dispatch(
            insertCompletionText(view.state, text, result.from, result.to)
                .copy(annotations = listOf(pickedCompletion.of(completion)))
        )
    }
    return true
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

/**
 * A source's query in flight, as upstream's `RunningQuery`. [updates] are the
 * transactions since it started, replayed onto its result when it arrives.
 */
private class RunningQuery(val active: ActiveSource) {
    val time = TimeSource.Monotonic.markNow()
    val updates = mutableListOf<Transaction>()
    var done = false
    var result: CompletionResult? = null
    var aborted = false
    var job: Job? = null

    fun finish(result: CompletionResult?) {
        this.result = result
        done = true
    }

    fun abort() {
        aborted = true
        job?.cancel()
    }
}

/** A query older than [MIN_ABORT_TIME] that has seen this many updates is given up. */
private const val MAX_UPDATE_COUNT = 50
private val MIN_ABORT_TIME = 1.seconds

/**
 * Runs the queries of pending sources and feeds their results back, as upstream's
 * `completionPlugin`. Each source has at most one query in flight: edits made while it
 * runs are recorded and replayed onto its result, which may then still apply, ask for
 * a new query, or close.
 *
 * Unlike upstream, which waits `activateOnTypingDelay` before querying and up to
 * `updateSyncTime` for slower sources before showing a result, queries start when a
 * source goes pending and each result is shown as soon as it arrives. So a sync source
 * is queried, and its result shown, within the transaction that activated it.
 */
internal class CompletionPlugin(private val view: EditorSession) : PluginValue {
    private val running = mutableListOf<RunningQuery>()

    /** Set while queries start or results are taken, so a query finishing inline waits. */
    private var busy = false

    init {
        val cState = view.state.field(completionStateField, require = false)
        if (cState != null && cState.active.any { it.isPending }) startUpdate()
    }

    override fun update(update: ViewUpdate) {
        val cState = update.state.field(completionStateField, require = false) ?: return
        val conf = update.state.facet(completionConfig)
        if (!update.selectionSet &&
            !update.docChanged &&
            update.startState.field(completionStateField, require = false) === cState
        ) {
            return
        }
        val doesReset = update.transactions.any { tr ->
            val type = getUpdateType(tr, conf)
            type has UPDATE_RESET ||
                (tr.selection != null || tr.docChanged) &&
                !(type has UPDATE_SIMPLE_INTERACTION)
        }
        for (query in running.toList()) {
            if (doesReset ||
                query.updates.size + update.transactions.size > MAX_UPDATE_COUNT &&
                query.time.elapsedNow() > MIN_ABORT_TIME
            ) {
                query.abort()
                running.remove(query)
            } else {
                query.updates += update.transactions
            }
        }
        if (cState.active.any { it.isPending && !isRunning(it.source) }) startUpdate()
    }

    override fun destroy() {
        running.forEach { it.abort() }
        running.clear()
    }

    private fun startUpdate() {
        val cState = view.state.field(completionStateField)
        busy = true
        try {
            for (active in cState.active) {
                if (active.isPending && !isRunning(active.source)) startQuery(active)
            }
        } finally {
            busy = false
        }
        val disabled = cState.open?.disabled == true
        if (running.any { it.done } || running.isNotEmpty() && disabled) accept()
    }

    private fun isRunning(source: QuerySource) = running.any { it.active.source == source }

    private fun startQuery(active: ActiveSource) {
        val state = view.state
        val context = CompletionContext(state, cur(state), active.explicit)
        val query = RunningQuery(active)
        running += query
        when (val source = active.source) {
            is SyncSource -> query.finish(view.queryLogged { source.query(context) })
            is AsyncSource -> {
                val scope = view.coroutineScope
                // A cancelled scope would never run the query, so count it as no result.
                if (!scope.isActive) {
                    query.finish(null)
                    return
                }
                query.job = scope.launch {
                    val result = try {
                        view.queryLogged { source.query(context) }
                    } catch (e: CancellationException) {
                        // The source's own cancellation, not this query's, is no result.
                        ensureActive()
                        null
                    }
                    if (!query.aborted) {
                        query.finish(result)
                        if (!busy) accept()
                    }
                }
            }
        }
    }

    /** For each finished query, make a result or, if appropriate, restart the query. */
    private fun accept() {
        val conf = view.state.facet(completionConfig)
        val cState = view.state.field(completionStateField)
        val updated = mutableListOf<ActiveSource>()
        busy = true
        try {
            // A restarted query that finishes inline is taken in this same pass.
            while (true) {
                val query = running.firstOrNull { it.done } ?: break
                running.remove(query)
                take(query, conf, cState)?.let { updated += it }
            }
        } finally {
            busy = false
        }
        if (updated.isNotEmpty() || cState.open?.disabled == true) {
            view.dispatch(TransactionSpec(effects = listOf(setActiveEffect.of(updated))))
        }
    }

    /**
     * The source state finished [query] leaves, after replaying the transactions since
     * it started, or null for no change. A result those edits invalidated asks again.
     */
    private fun take(
        query: RunningQuery,
        conf: CompletionConfig,
        cState: CompletionState
    ): ActiveSource? {
        val source = query.active.source
        val result = query.result
        if (result != null) {
            val pos = cur(query.updates.firstOrNull()?.startState ?: view.state)
            val arrived: ActiveSource = ActiveResult.arrived(
                source,
                query.active.explicit,
                pos,
                result
            )
            val active = query.updates.fold(arrived) { a, tr -> a.update(tr, conf) }
            if (active.hasResult) return active
        }
        val current = cState.active.find { it.source == source }
        if (current == null || !current.isPending) return null
        if (result != null) {
            startQuery(current)
            return null
        }
        // No result: clear the pending state unless it was set again since.
        val inactive = ActiveSource(source, SourceState.Inactive)
        return query.updates.fold(inactive) { a, tr ->
            a.update(tr, conf)
        }.takeUnless { it.isPending }
    }
}

/** The plugin that runs completion queries; one per editor, however many configs. */
internal val completionPlugin: ViewPlugin<CompletionPlugin> =
    ViewPlugin.define(create = { view -> CompletionPlugin(view) })

// ── Composable UI ──

@OptIn(ExperimentalFoundationApi::class)
@Suppress("ktlint:standard:function-naming")
@Composable
private fun CompletionList(
    view: EditorSession,
    dialog: CompletionDialog,
    config: CompletionConfig
) {
    val items = dialog.options.take(config.maxRenderedOptions)
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
    LaunchedEffect(dialog.selected) {
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
            val isSelected = index == dialog.selected
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
                    .clickable { applyCompletion(view, item) }
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

/** Chunks of the document at least this long get their own, cached, word list. */
private const val MIN_CACHE_LEN = 1000

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
        if (cp == '_'.code || isWordChar(cp) || wordChars.hasCodePoint(cp)) {
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

internal fun String.hasCodePoint(codePoint: Int): Boolean {
    var i = 0
    while (i < length) {
        val cp = codePointAt(this, i)
        if (cp == codePoint) return true
        i += codePointSize(cp)
    }
    return false
}

/**
 * Word lists of document chunks, by node identity ([Text.equals] compares content). Upstream keeps
 * one WeakMap per `wordChars`; common Kotlin has no weak map, so this holds only the chunks of the
 * last document scanned, which is what the next keystroke's document mostly shares.
 */
private class WordCache(val wordChars: String) {
    private val byLength = HashMap<Int, MutableList<Pair<Text, List<Completion>>>>()

    operator fun get(node: Text): List<Completion>? =
        byLength[node.length]?.firstOrNull { it.first === node }?.second

    operator fun set(node: Text, words: List<Completion>) {
        byLength.getOrPut(node.length) { mutableListOf() } += node to words
    }

    /** Copy [node]'s entry and its chunks' entries from [from]. */
    fun keep(node: Text, from: WordCache) {
        val words = from[node] ?: return
        set(node, words)
        node.children?.forEach { if (it.length >= MIN_CACHE_LEN) keep(it, from) }
    }
}

@Volatile
private var lastWordCache: WordCache? = null

/** Upstream's storeWords: the new words of [doc] into [result], up to [MAX_WORD_LIST]. */
private fun storeWords(
    doc: Text,
    wordChars: String,
    result: MutableList<Completion>,
    seen: MutableSet<String>,
    ignoreAt: Int
) {
    val text = doc.toString()
    for (range in findWords(text, wordChars)) {
        val word = text.substring(range)
        if (range.first != ignoreAt && seen.add(word)) {
            result += Completion(label = word, type = "text")
            if (result.size >= MAX_WORD_LIST) return
        }
    }
}

/**
 * Upstream's collectWords: each chunk of [MIN_CACHE_LEN] or more gets its own list, capped at
 * [MAX_WORD_LIST] and cached when under it, and the lists are merged without a cap.
 */
private fun collectWords(
    doc: Text,
    wordChars: String,
    ignoreAt: Int,
    old: WordCache?,
    cache: WordCache
): List<Completion> {
    val big = doc.length >= MIN_CACHE_LEN
    if (big) {
        old?.get(doc)?.let { cached ->
            cache.keep(doc, old)
            return cached
        }
    }
    val result = mutableListOf<Completion>()
    val seen = mutableSetOf<String>()
    val children = doc.children
    if (children == null) {
        storeWords(doc, wordChars, result, seen, ignoreAt)
    } else {
        var pos = 0
        for (ch in children) {
            if (ch.length >= MIN_CACHE_LEN) {
                for (c in collectWords(ch, wordChars, ignoreAt - pos, old, cache)) {
                    if (seen.add(c.label)) result += c
                }
            } else {
                storeWords(ch, wordChars, result, seen, ignoreAt - pos)
            }
            pos += ch.length + 1
        }
    }
    if (big && result.size < MAX_WORD_LIST) cache[doc] = result
    return result
}

/**
 * A completion source that provides the words in the document (letters, digits, `_` and the
 * language's `wordChars`), except the one being typed at the cursor.
 */
val completeAnyWord: CompletionSource = { ctx ->
    // Chunks are scanned whole, so leave out `\n`: upstream scans line by line.
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
        val old = lastWordCache?.takeIf { it.wordChars == wordChars }
        val cache = WordCache(wordChars)
        val options = collectWords(doc, wordChars, from.value, old, cache)
        lastWordCache = cache
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
    val tooltipProvider = showTooltip.compute(
        listOf(Slot.FieldSlot(completionStateField))
    ) { state ->
        val open = state.field(completionStateField, require = false)?.open
        if (open != null) {
            // Anchor the popup at the smallest from across the merged sources.
            Tooltip(pos = open.pos.value) {
                val editorView = LocalEditorSession.current
                val currentConfig = editorView.state.facet(completionConfig)
                val current = editorView.state.field(completionStateField, require = false)?.open
                    ?: open
                CompletionList(editorView, current, currentConfig)
            }
        } else {
            null
        }
    }

    return ExtensionList(
        buildList {
            add(completionConfig.of(config))
            add(completionStateField)
            add(completionPlugin.asExtension())
            add(tooltipProvider)
            if (config.defaultKeymap) {
                add(keymap.of(completionKeymap))
            }
        }
    )
}
