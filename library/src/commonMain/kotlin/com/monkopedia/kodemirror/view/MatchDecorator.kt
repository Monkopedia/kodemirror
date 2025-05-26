package com.monkopedia.kodemirror.view

import com.monkopedia.kodemirror.state.*
import kotlin.math.max
import kotlin.math.min

/**
 * Helper class used to make it easier to maintain decorations on
 * visible code that matches a given regular expression. To be used
 * in a [ViewPlugin]. Instances of this object represent a matching
 * configuration.
 */
class MatchDecorator(
    private val config: MatchDecoratorConfig
) {
    private val regexp = config.regexp
    private val boundary = config.boundary
    private val maxLength = config.maxLength ?: 1000

    init {
        if (!regexp.global) {
            throw IllegalArgumentException("The regular expression given to MatchDecorator should have its 'g' flag set")
        }
    }

    private fun addMatch(
        match: RegExpExecArray,
        view: EditorView,
        from: Int,
        add: (from: Int, to: Int, deco: Decoration<*>) -> Unit
    ) {
        if (config.decorate != null) {
            config.decorate.invoke(add, from, from + match[0].length, match, view)
        } else if (config.decoration is Function1<*, *>) {
            @Suppress("UNCHECKED_CAST")
            val deco = (config.decoration as (RegExpExecArray, EditorView, Int) -> Decoration<*>?)(match, view, from)
            if (deco != null) add(from, from + match[0].length, deco)
        } else if (config.decoration != null) {
            add(from, from + match[0].length, config.decoration)
        } else {
            throw IllegalArgumentException("Either 'decorate' or 'decoration' should be provided to MatchDecorator")
        }
    }

    /**
     * Compute the full set of decorations for matches in the given
     * view's viewport. You'll want to call this when initializing your
     * plugin.
     */
    fun createDeco(view: EditorView): DecorationSet {
        val builder = RangeSetBuilder<Decoration<*>>()
        val add = { from: Int, to: Int, deco: Decoration<*> -> builder.add(from, to, deco) }
        for (range in matchRanges(view, maxLength)) {
            iterMatches(view.state.doc, regexp, range.from, range.to) { from, m -> addMatch(m, view, from, add) }
        }
        return builder.finish()
    }

    /**
     * Update a set of decorations for a view update. [deco] _must_ be
     * the set of decorations produced by _this_ [MatchDecorator] for
     * the view state before the update.
     */
    fun updateDeco(update: ViewUpdate, deco: DecorationSet): DecorationSet {
        var changeFrom = 1e9.toInt()
        var changeTo = -1
        if (update.docChanged) {
            update.changes.iterChanges { fromA, toA, fromB, toB, _ ->
                if (toB >= update.view.viewport.from && fromB <= update.view.viewport.to) {
                    changeFrom = min(fromB, changeFrom)
                    changeTo = max(toB, changeTo)
                }
            }
        }
        if (update.viewportChanged || changeTo - changeFrom > 1000) {
            return createDeco(update.view)
        }
        if (changeTo > -1) {
            return updateRange(update.view, deco.map(update.changes), changeFrom, changeTo)
        }
        return deco
    }

    private fun updateRange(view: EditorView, deco: DecorationSet, updateFrom: Int, updateTo: Int): DecorationSet {
        for (r in view.visibleRanges) {
            val from = max(r.from, updateFrom)
            val to = min(r.to, updateTo)
            if (to > from) {
                val fromLine = view.state.doc.lineAt(from)
                val toLine = if (fromLine.to < to) view.state.doc.lineAt(to) else fromLine
                var start = max(r.from, fromLine.from)
                var end = min(r.to, toLine.to)
                if (boundary != null) {
                    for (i in from downTo fromLine.from) {
                        if (boundary.matches(fromLine.text[i - 1 - fromLine.from].toString())) {
                            start = i
                            break
                        }
                    }
                    for (i in to until toLine.to) {
                        if (boundary.matches(toLine.text[i - toLine.from].toString())) {
                            end = i
                            break
                        }
                    }
                }
                val ranges = mutableListOf<Range<Decoration<*>>>()
                val add = { from: Int, to: Int, deco: Decoration<*> -> ranges.add(deco.range(from, to)) }
                if (fromLine == toLine) {
                    regexp.lastIndex = start - fromLine.from
                    var m: RegExpExecArray?
                    while (regexp.exec(fromLine.text).also { m = it } != null && m!!.index < end - fromLine.from) {
                        addMatch(m!!, view, m!!.index + fromLine.from, add)
                    }
                } else {
                    iterMatches(view.state.doc, regexp, start, end) { from, m -> addMatch(m, view, from, add) }
                }
                deco = deco.update(
                    DecorationSet.UpdateSpec(
                        filterFrom = start,
                        filterTo = end,
                        filter = { from, to -> from < start || to > end },
                        add = ranges
                    )
                )
            }
        }
        return deco
    }
}

/**
 * Configuration options for [MatchDecorator].
 */
data class MatchDecoratorConfig(
    /** The regular expression to match against the content. Will only be matched inside lines (not across them). Should have its 'g' flag set. */
    val regexp: RegExp,
    /** The decoration to apply to matches, either directly or as a function of the match. */
    val decoration: Any? = null,
    /** Customize the way decorations are added for matches. */
    val decorate: ((add: (Int, Int, Decoration<*>) -> Unit, from: Int, to: Int, match: RegExpExecArray, view: EditorView) -> Unit)? = null,
    /** A boundary expression to reduce the amount of re-matching. */
    val boundary: Regex? = null,
    /** Controls how much additional invisible content to include in matches. Defaults to 1000. */
    val maxLength: Int? = null
)

private fun iterMatches(
    doc: Text,
    re: RegExp,
    from: Int,
    to: Int,
    f: (from: Int, m: RegExpExecArray) -> Unit
) {
    re.lastIndex = 0
    var pos = from
    for (cursor in doc.iterRange(from, to)) {
        if (!cursor.lineBreak) {
            var m: RegExpExecArray?
            while (re.exec(cursor.value).also { m = it } != null) {
                f(pos + m!!.index, m!!)
            }
        }
        pos += cursor.value.length
    }
}

private fun matchRanges(view: EditorView, maxLength: Int): List<ViewRange> {
    val visible = view.visibleRanges
    if (visible.size == 1 && visible[0].from == view.viewport.from && visible[0].to == view.viewport.to) {
        return visible
    }
    val result = mutableListOf<ViewRange>()
    for (range in visible) {
        val from = max(view.state.doc.lineAt(range.from).from, range.from - maxLength)
        val to = min(view.state.doc.lineAt(range.to).to, range.to + maxLength)
        if (result.isNotEmpty() && result.last().to >= from) {
            result[result.lastIndex] = ViewRange(result.last().from, to)
        } else {
            result.add(ViewRange(from, to))
        }
    }
    return result
}

data class ViewRange(val from: Int, val to: Int)
