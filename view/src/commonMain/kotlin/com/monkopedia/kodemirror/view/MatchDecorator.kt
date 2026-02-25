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
package com.monkopedia.kodemirror.view

import com.monkopedia.kodemirror.state.EditorState
import com.monkopedia.kodemirror.state.RangeSetBuilder

/**
 * Port of `matchdecorator.ts` — applies a decoration to every match of a
 * regular expression in the visible document range.
 *
 * @param regexp   The regular expression to match. Must have the `g` flag (or
 *                 Kotlin's `findAll` equivalent).
 * @param decorate Called for each match; returns a [Decoration] (or null to
 *                 skip this match).
 * @param boundary Optional positive-lookahead boundary (not used in Kotlin port).
 * @param maxLength Maximum characters to scan per viewport update. Defaults to
 *                  1 000 000 (effectively unbounded).
 */
class MatchDecorator(
    private val regexp: Regex,
    private val decorate: (
        add: (from: Int, to: Int, deco: Decoration) -> Unit,
        match: MatchResult,
        view: EditorView
    ) -> Unit,
    private val maxLength: Int = 1_000_000
) {
    /**
     * Scan the viewport of [view] for matches and return a fresh
     * [DecorationSet].
     */
    fun createDeco(view: EditorView): DecorationSet {
        val builder = RangeSetBuilder<Decoration>()
        val state = view.state
        val viewport = Viewport(0, state.doc.length) // full doc for simplicity
        addDecos(builder, state, viewport, view)
        return builder.finish()
    }

    /**
     * Incrementally update [deco] after [update]. Only rescans regions that
     * changed or moved into the viewport.
     */
    fun updateDeco(update: ViewUpdate, deco: DecorationSet): DecorationSet {
        if (!update.docChanged && !update.viewportChanged) return deco
        return createDeco(update.view)
    }

    private fun addDecos(
        builder: RangeSetBuilder<Decoration>,
        state: EditorState,
        viewport: Viewport,
        view: EditorView
    ) {
        val doc = state.doc
        val text = doc.sliceString(viewport.from, viewport.to.coerceAtMost(doc.length))
        val baseOffset = viewport.from
        var scanned = 0

        for (match in regexp.findAll(text)) {
            if (scanned++ > maxLength) break
            val from = baseOffset + match.range.first
            val to = baseOffset + match.range.last + 1
            decorate(
                { mFrom, mTo, dec ->
                    if (mFrom <= mTo) builder.add(mFrom, mTo, dec)
                },
                match,
                view
            )
            // If decorate didn't add anything, add the match itself as a default
        }
    }
}
