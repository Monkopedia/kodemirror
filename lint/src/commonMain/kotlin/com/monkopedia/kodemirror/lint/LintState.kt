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
package com.monkopedia.kodemirror.lint

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.unit.dp
import com.monkopedia.kodemirror.state.ChangeSet
import com.monkopedia.kodemirror.state.DocPos
import com.monkopedia.kodemirror.state.EditorState
import com.monkopedia.kodemirror.state.Facet
import com.monkopedia.kodemirror.state.MapMode
import com.monkopedia.kodemirror.state.Range
import com.monkopedia.kodemirror.state.RangeSet
import com.monkopedia.kodemirror.state.RangeSetBuilder
import com.monkopedia.kodemirror.state.RangeValue
import com.monkopedia.kodemirror.state.StateEffect
import com.monkopedia.kodemirror.state.StateEffectType
import com.monkopedia.kodemirror.state.StateField
import com.monkopedia.kodemirror.state.StateFieldSpec
import com.monkopedia.kodemirror.state.Text
import com.monkopedia.kodemirror.state.Transaction
import com.monkopedia.kodemirror.state.TransactionSpec
import com.monkopedia.kodemirror.view.Decoration
import com.monkopedia.kodemirror.view.DecorationSet
import com.monkopedia.kodemirror.view.EditorSession
import com.monkopedia.kodemirror.view.EditorTheme
import com.monkopedia.kodemirror.view.LocalContentTextStyle
import com.monkopedia.kodemirror.view.LocalEditorTheme
import com.monkopedia.kodemirror.view.MarkDecorationSpec
import com.monkopedia.kodemirror.view.PluginValue
import com.monkopedia.kodemirror.view.ViewPlugin
import com.monkopedia.kodemirror.view.ViewUpdate
import com.monkopedia.kodemirror.view.WidgetDecorationSpec
import com.monkopedia.kodemirror.view.WidgetType
import com.monkopedia.kodemirror.view.decorations
import com.monkopedia.kodemirror.view.editorTheme
import com.monkopedia.kodemirror.view.logException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/** Effect used to update the set of diagnostics. */
val setDiagnosticsEffect: StateEffectType<List<Diagnostic>> = StateEffect.define()

/** Internal effect to signal forced linting. */
internal val forceLintEffect: StateEffectType<Unit> = StateEffect.define()

/** Internal state holding diagnostics and their decorations. */
internal class LintStateValue private constructor(
    private val entries: List<Entry>,
    /**
     * The pieces upstream `LintState.init` splits the diagnostics into (see [lintPieces]), which
     * the decorations are drawn from and the lint commands navigate over as upstream's do. An edit
     * maps this set, as upstream maps its decoration set, rather than splitting the mapped
     * diagnostics again.
     */
    val pieceSet: RangeSet<LintPiece>,
    private val theme: EditorTheme?
) {
    /**
     * A stored diagnostic. [inclusiveEnd] marks one whose end was cut off at the document end:
     * upstream gives that mark `inclusiveEnd`, so text typed at its end extends it.
     */
    private class Entry(val diagnostic: Diagnostic, val inclusiveEnd: Boolean) {
        /**
         * Map through [changes] the way upstream maps the decoration: a range is a mark that is
         * dropped once it collapses; a point (`from == to`) is a widget that stays before text
         * inserted at it and is dropped only when a deletion spans it.
         */
        fun map(changes: ChangeSet): Entry? {
            val diag = diagnostic
            if (diag.from == diag.to) {
                val pos = changes.mapPos(diag.from, -1, MapMode.TrackDel) ?: return null
                return Entry(diag.copy(from = pos, to = pos), inclusiveEnd)
            }
            val newFrom = changes.mapPos(diag.from, 1)
            val newTo = changes.mapPos(diag.to, if (inclusiveEnd) 1 else -1)
            return if (newFrom < newTo) {
                Entry(diag.copy(from = newFrom, to = newTo), inclusiveEnd)
            } else {
                null
            }
        }
    }

    val diagnostics: List<Diagnostic> = entries.map { it.diagnostic }

    /**
     * The diagnostic decorations, built on first use (the gutter's copy of the state never needs
     * them). As upstream `LintState.init` does, each piece gets one decoration in the color of
     * the most severe diagnostic covering it: a [DiagnosticWidget] for a zero-width piece, which
     * a mark could not show, otherwise a mark.
     */
    val decorations: DecorationSet by lazy {
        val ranges = mutableListOf<Range<Decoration>>()
        if (theme != null) {
            val iter = pieceSet.iter()
            while (true) {
                val piece = iter.value ?: break
                val severity = piece.diagnostics.maxOf { it.severity }
                val decoration = if (piece.widget) {
                    Decoration.widget(WidgetDecorationSpec(DiagnosticWidget(severity)))
                } else {
                    val background = theme.lintColor(severity).copy(alpha = 0.2f)
                    Decoration.mark(MarkDecorationSpec(style = SpanStyle(background = background)))
                }
                ranges += decoration.range(DocPos(iter.from), DocPos(iter.to))
                iter.next()
            }
        }
        // The decorations' sides differ from the pieces', so they may sort differently.
        RangeSet.of(ranges, sort = true)
    }

    fun map(changes: ChangeSet, theme: EditorTheme): LintStateValue {
        val mappedEntries = entries.mapNotNull { it.map(changes) }.sortedWith(entryOrder)
        val mappedPieces = pieceSet.map(changes)
        return if (mappedEntries.isEmpty() && mappedPieces.isEmpty) {
            empty
        } else {
            LintStateValue(mappedEntries, mappedPieces, theme)
        }
    }

    companion object {
        val empty = LintStateValue(emptyList(), RangeSet.empty(), null)

        private val entryOrder: Comparator<Entry> =
            compareBy({ it.diagnostic.from }, { it.diagnostic.to })

        /**
         * Clamp [diagnostics] to the document, as upstream `LintState.init` does: a diagnostic
         * starting past the end is dropped, one ending past it is cut off at the end (and keeps
         * an inclusive end). The clamped list is what gets stored, so later edits map positions
         * that are in range.
         *
         * An inverted diagnostic (`to < from`) becomes a point at `from`. Upstream draws one
         * alone that way, but loops forever splitting one that overlaps another (#424).
         */
        fun build(diagnostics: List<Diagnostic>, doc: Text, theme: EditorTheme): LintStateValue {
            val docEnd = DocPos(doc.length)
            val inDoc = diagnostics
                .filter { it.from <= docEnd }
                .map { if (it.to < it.from) it.copy(to = it.from) else it }
                .sortedWith(compareBy({ it.from }, { it.to }))
            if (inDoc.isEmpty()) return empty
            val entries = inDoc.map {
                if (it.to > docEnd) Entry(it.copy(to = docEnd), true) else Entry(it, false)
            }
            // The pieces see the ends before clamping, as upstream's do: a mark cut off at the
            // end is inclusive there.
            return LintStateValue(entries, lintPieces(inDoc, doc), theme)
        }
    }
}

/**
 * The value of one piece of the decoration set upstream `LintState.init` builds, covering
 * [diagnostics], with the sides of the decoration upstream uses for it: a widget
 * (`Side.InlineBefore`) for a zero-width piece, otherwise a mark that is inclusive at its end when
 * a diagnostic continues past it. The sides decide how an edit maps the piece and which
 * [RangeSet] layer it lands in, and so the order `between` visits pieces in, which the lint
 * commands depend on exactly as upstream's do.
 */
internal class LintPiece(
    val diagnostics: List<Diagnostic>,
    val widget: Boolean,
    inclusiveEnd: Boolean
) : RangeValue() {
    override val startSide: Int = if (widget) -100_000_000 else 500_000_000
    override val endSide: Int = if (widget) {
        -100_000_000
    } else if (inclusiveEnd) {
        1
    } else {
        -600_000_000
    }
    override val point: Boolean = widget

    fun carries(diagnostic: Diagnostic) = diagnostics.any { it === diagnostic }
}

/**
 * Split [sorted], diagnostics sorted by `from` then `to`, into non-overlapping pieces, as upstream
 * `LintState.init` does: split wherever the set of covering diagnostics changes, cut off at the
 * document end, and with a point or a short line-break-only range turned into a zero-width piece
 * at its start.
 */
private fun lintPieces(sorted: List<Diagnostic>, doc: Text): RangeSet<LintPiece> {
    val docEnd = DocPos(doc.length)
    val pieces = RangeSetBuilder<LintPiece>()
    val active = mutableListOf<Diagnostic>()
    var pos = DocPos.ZERO
    var i = 0
    while (i < sorted.size || active.isNotEmpty()) {
        val next = sorted.getOrNull(i)
        val from: DocPos
        var to: DocPos
        if (active.isNotEmpty()) {
            from = pos
            to = active.minOf { it.to }
            if (next != null && next.from > from) to = minOf(to, next.from)
        } else {
            from = next!!.from
            if (from > docEnd) break
            to = next.to
            active += next
            i++
        }
        while (i < sorted.size) {
            val candidate = sorted[i]
            if (candidate.from == from && (candidate.to > candidate.from || candidate.to == from)) {
                active += candidate
                i++
                to = minOf(to, candidate.to)
            } else {
                to = minOf(to, candidate.from)
                break
            }
        }
        to = minOf(to, docEnd)
        val widget = active.any { it.from == from && (it.to == to || to == docEnd) } &&
            (
                from == to ||
                    (to.value - from.value < 10 && doc.sliceString(from, to).all { it == '\n' })
                )
        val piece = LintPiece(active.toList(), widget, inclusiveEnd = active.any { it.to > to })
        pieces.add(from, if (widget) from else to, piece)
        pos = to
        if (pos == docEnd) break
        active.removeAll { it.to <= pos }
    }
    return pieces.finish()
}

/**
 * Marker for a diagnostic that has no text to underline, port of upstream's `DiagnosticWidget`
 * (`cm-lintPoint`): a zero-width widget drawing a small triangle in the [severity]'s color under
 * its position.
 */
internal class DiagnosticWidget(val severity: Severity) : WidgetType() {
    @Composable
    override fun Content() {
        val color = LocalEditorTheme.current.lintColor(severity)
        // As tall as the text, like upstream's empty inline span, so its bottom is the text's.
        val height = with(LocalDensity.current) { LocalContentTextStyle.current.fontSize.toDp() }
        Spacer(
            Modifier.width(0.dp).height(height).drawBehind {
                // Upstream's `:after` triangle: 6px wide and 4px tall, 2px left of the position.
                val path = Path().apply {
                    moveTo(-2.dp.toPx(), size.height)
                    lineTo(4.dp.toPx(), size.height)
                    lineTo(1.dp.toPx(), size.height - 4.dp.toPx())
                    close()
                }
                drawPath(path, color)
            }
        )
    }

    override fun equals(other: Any?): Boolean =
        other is DiagnosticWidget && other.severity == severity

    override fun hashCode(): Int = severity.hashCode()
}

/**
 * The update shared by [lintState] and the gutter's [lintGutterState]: diagnostics from a
 * [setDiagnosticsEffect] (passed through [filter]) replace the value, otherwise it is mapped
 * through the document change.
 */
private fun updateLintStateValue(
    value: LintStateValue,
    tr: Transaction,
    filter: DiagnosticFilter?
): LintStateValue {
    val theme = tr.startState.facet(editorTheme)
    var result = value
    for (effect in tr.effects) {
        val diagEffect = effect.asType(setDiagnosticsEffect) ?: continue
        val diagnostics = filter?.invoke(diagEffect.value) ?: diagEffect.value
        result = LintStateValue.build(diagnostics, tr.newDoc, theme)
    }
    if (result === value && tr.docChanged) {
        result = result.map(tr.changes, theme)
    }
    return result
}

/**
 * State field tracking diagnostics and their decorations. [LintConfig.markerFilter] decides which
 * diagnostics it keeps, as in upstream `LintState.init`.
 */
internal val lintState: StateField<LintStateValue> = StateField.define(
    StateFieldSpec(
        create = { LintStateValue.empty },
        update = { value, tr ->
            updateLintStateValue(value, tr, tr.state.facet(lintConfig).markerFilter)
        },
        provide = { field ->
            decorations.from(field) { it.decorations }
        }
    )
)

/**
 * The diagnostics the lint gutter shows. Upstream keeps the gutter's markers in their own field
 * fed by every [setDiagnosticsEffect], so [LintConfig.markerFilter] does not hide them.
 */
internal val lintGutterState: StateField<LintStateValue> = StateField.define(
    StateFieldSpec(
        create = { LintStateValue.empty },
        update = { value, tr -> updateLintStateValue(value, tr, null) }
    )
)

/** Set diagnostics on an editor view. */
fun setDiagnostics(view: EditorSession, diagnostics: List<Diagnostic>) {
    view.dispatch(
        TransactionSpec(
            effects = listOf(setDiagnosticsEffect.of(diagnostics))
        )
    )
}

/** Get the total number of diagnostics in the given state. */
fun diagnosticCount(state: EditorState): Int =
    state.field(lintState, require = false)?.diagnostics?.size ?: 0

/** Iterate over all diagnostics in the given state. */
fun forEachDiagnostic(state: EditorState, callback: (Diagnostic) -> Unit) {
    state.field(lintState, require = false)?.diagnostics?.forEach(callback)
}

/**
 * Make a pending lint run happen right away instead of waiting for the delay. Does nothing when
 * no run is pending, as upstream `forceLinting` does.
 */
fun forceLinting(view: EditorSession) {
    view.dispatch(
        TransactionSpec(effects = listOf(forceLintEffect.of(Unit)))
    )
}

/** One `linter()`/`lintDisplay()` contribution to [lintConfig]. */
internal class LintConfigInput(val source: SuspendLintSource?, val config: LintConfig)

private typealias DiagnosticFilter = (List<Diagnostic>) -> List<Diagnostic>

/** The combination of every [LintConfigInput] in the state, like upstream's `lintConfig`. */
internal class CombinedLintConfig(
    val sources: List<SuspendLintSource>,
    val delay: Long,
    val markerFilter: DiagnosticFilter?,
    val tooltipFilter: DiagnosticFilter?,
    val autoPanel: Boolean
)

/** Apply [a], then [b], like upstream's `combineFilter`. */
private fun combineFilters(a: DiagnosticFilter?, b: DiagnosticFilter?): DiagnosticFilter? =
    if (a == null) {
        b
    } else if (b == null) {
        a
    } else {
        { diagnostics -> b(a(diagnostics)) }
    }

private val defaultDelay = LintConfig().delay

/**
 * Facet collecting every lint source and its configuration, so that all sources lint together and
 * their diagnostics are dispatched as one list (upstream `lintConfig`).
 *
 * Upstream combines only the options a config sets, taking the largest delay. [LintConfig] cannot
 * tell an explicit `delay = 750` from the default, so a config left at the default delay does not
 * take part, which keeps `linter(source)` from overriding another linter's shorter delay.
 */
internal val lintConfig: Facet<LintConfigInput, CombinedLintConfig> = Facet.define(
    combine = { inputs ->
        CombinedLintConfig(
            sources = inputs.mapNotNull { it.source },
            delay =
            inputs.map { it.config.delay }.filter { it != defaultDelay }.maxOrNull()
                ?: defaultDelay,
            markerFilter = inputs.fold(null as DiagnosticFilter?) { acc, input ->
                combineFilters(acc, input.config.markerFilter)
            },
            tooltipFilter = inputs.fold(null as DiagnosticFilter?) { acc, input ->
                combineFilters(acc, input.config.tooltipFilter)
            },
            autoPanel = inputs.any { it.config.autoPanel }
        )
    }
)

/** How long a source result waits for the remaining sources before the partial list is shown. */
private const val BATCH_TIMEOUT_MS = 200L

/**
 * Runs every configured lint source once the document has been idle for the configured delay, and
 * dispatches their combined diagnostics (upstream `lintPlugin`).
 */
internal class LintPlugin(private val view: EditorSession) : PluginValue {
    private val job = SupervisorJob(view.coroutineScope.coroutineContext[Job])
    private val scope = CoroutineScope(view.coroutineScope.coroutineContext + job)

    /** The scheduled run, while its delay has not yet passed. */
    private var timer: Job? = null

    /** Parent of the runs whose sources are in flight; a doc change makes their results stale. */
    private val runs = SupervisorJob(job)

    init {
        schedule()
    }

    override fun update(update: ViewUpdate) {
        val forced = update.transactions.any { tr ->
            tr.effects.any { it.asType(forceLintEffect) != null }
        }
        if (forced) {
            if (timer != null) startRun(waitMs = 0)
            return
        }
        if (update.docChanged) {
            runs.cancelChildren()
            schedule()
        } else if (update.state.facet(lintConfig) != update.startState.facet(lintConfig)) {
            // Like upstream, a config change lints again but lets a run in flight land.
            schedule()
        }
    }

    private fun schedule() {
        startRun(waitMs = view.state.facet(lintConfig).delay)
    }

    private fun startRun(waitMs: Long) {
        timer?.cancel()
        timer = scope.launch {
            delay(waitMs)
            timer = null
            scope.launch(runs) { runSources() }
        }
    }

    private suspend fun runSources() = coroutineScope {
        val state = view.state
        val sources = state.facet(lintConfig).sources
        if (sources.isEmpty()) return@coroutineScope
        val results = Channel<List<Diagnostic>>(Channel.UNLIMITED)
        for (source in sources) {
            launch {
                try {
                    results.send(source(view))
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    logException(view.state, e)
                }
            }
        }
        val sink = { collected: List<List<Diagnostic>> ->
            if (view.state.doc === state.doc) {
                view.dispatch(
                    TransactionSpec(effects = listOf(setDiagnosticsEffect.of(collected.flatten())))
                )
            }
        }
        // Like upstream batchResults: dispatch once every source has answered, or, if the rest
        // stall for BATCH_TIMEOUT_MS after an answer, show what has arrived so far.
        val collected = mutableListOf<List<Diagnostic>>()
        while (collected.size < sources.size) {
            val next = if (collected.isEmpty()) {
                results.receive()
            } else {
                withTimeoutOrNull(BATCH_TIMEOUT_MS) { results.receive() }
                    ?: run {
                        sink(collected)
                        results.receive()
                    }
            }
            collected += next
        }
        sink(collected)
    }

    override fun destroy() {
        job.cancel()
    }
}

internal val lintPlugin: ViewPlugin<LintPlugin> = ViewPlugin.define(create = { LintPlugin(it) })
