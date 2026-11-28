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

import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import com.monkopedia.kodemirror.state.ChangeSpec
import com.monkopedia.kodemirror.state.DocPos
import com.monkopedia.kodemirror.state.EditorState
import com.monkopedia.kodemirror.state.SelectionSpec
import com.monkopedia.kodemirror.state.TransactionSpec
import com.monkopedia.kodemirror.state.asInsert

/**
 * What the editor's hidden text field holds, and the document text it stands for.
 *
 * Between edits the field is empty and stands for nothing. While an input method is composing
 * (pinyin before a candidate is picked, a dead-key accent, an Android keyboard's underlined
 * word) the field holds the composing text, and that same text is mirrored into the document at
 * `[from, from + value.text.length)`. Keeping the field's text and composition across edits is
 * what lets the input method keep replacing its own composing range; emptying the field after
 * every change, as the editor used to, threw the composition away and re-inserted each update
 * beside the previous one (#294).
 */
internal class HiddenInput(val value: TextFieldValue, val from: Int) {
    val composing: Boolean get() = value.composition != null

    /**
     * Whether the document still holds this buffer's mirrored text, with the selection where the
     * input method put it. Anything else changed the document or the selection under the
     * composition, and the buffer no longer describes it.
     */
    fun mirrors(state: EditorState): Boolean {
        val to = from + value.text.length
        if (to > state.doc.length) return false
        val main = state.selection.main
        return state.doc.sliceString(DocPos(from), DocPos(to)) == value.text &&
            main.anchor.value == from + value.selection.start &&
            main.head.value == from + value.selection.end
    }

    companion object {
        val Empty = HiddenInput(TextFieldValue(""), 0)
    }
}

/** The transaction a hidden-field edit makes, if any, and the buffer it leaves behind. */
internal class HiddenInputEdit(val transaction: TransactionSpec?, val next: HiddenInput)

/**
 * Turn the hidden field's new [value] into a document change.
 *
 * The change is the difference between the previous buffer and [value], applied to the
 * document text the previous buffer stands for: its mirrored composition when it still
 * [mirrors][HiddenInput.mirrors] the document, otherwise the main selection, which typed text
 * replaces. While [value] is still composing, the result keeps it as the new buffer; once the
 * input method commits, the buffer empties again.
 */
internal fun applyHiddenInput(
    state: EditorState,
    previous: HiddenInput,
    value: TextFieldValue
): HiddenInputEdit {
    val base = previous.takeIf { it.composing && it.mirrors(state) }
    val oldText = base?.value?.text ?: ""
    val sel = state.selection.main
    val baseFrom = base?.from ?: sel.from.value
    val baseTo = base?.let { it.from + oldText.length } ?: sel.to.value

    val newText = value.text
    val prefix = commonPrefix(oldText, newText)
    val suffix = commonSuffix(oldText, newText, prefix)
    val changeFrom = baseFrom + prefix
    val changeTo = baseTo - suffix
    val replaced = newText.substring(prefix, newText.length - suffix)

    if (value.composition != null) {
        // Mirror verbatim, so the buffer and the document range stay the same text.
        val unchanged = changeFrom == changeTo && replaced.isEmpty()
        val anchor = baseFrom + value.selection.start
        val head = baseFrom + value.selection.end
        val selectionUnchanged = sel.anchor.value == anchor && sel.head.value == head
        val transaction = if (unchanged && selectionUnchanged) {
            null
        } else {
            TransactionSpec(
                changes = if (unchanged) {
                    null
                } else {
                    ChangeSpec.Single(DocPos(changeFrom), DocPos(changeTo), replaced.asInsert())
                },
                selection = SelectionSpec.CursorSpec(DocPos(anchor), DocPos(head)),
                // CodeMirror's names for composition input, the first change marked `.start`.
                userEvent = if (base == null) "input.type.compose.start" else "input.type.compose"
            )
        }
        return HiddenInputEdit(transaction, HiddenInput(value, baseFrom))
    }

    // Committed text. Control characters other than newline (a Tab whose key the keymap left
    // alone, for instance) leak into the field and are not text.
    val inserted = replaced.filter { it == '\n' || !it.isISOControl() }
    // Ending a composition with nothing changed (the input method just accepted its text) or
    // committing nothing over a plain selection leaves the document alone. Committing nothing
    // over a mirrored composition is the input method withdrawing it, which removes it.
    val removesComposition = base != null && changeFrom != changeTo
    val transaction = if (inserted.isEmpty() && !removesComposition) {
        null
    } else {
        TransactionSpec(
            changes = ChangeSpec.Single(
                DocPos(changeFrom),
                DocPos(changeTo),
                inserted.asInsert()
            ),
            selection = SelectionSpec.CursorSpec(DocPos(changeFrom + inserted.length)),
            userEvent = if (base != null) "input.type.compose" else "input.type"
        )
    }
    return HiddenInputEdit(transaction, HiddenInput.Empty)
}

/**
 * The hidden field's own buffer, while it still differs from the value the editor gave it.
 *
 * The field adopts a new value only when it next recomposes, so after the editor empties it (a
 * commit, a dropped echo) a second edit in the same frame is applied to the field's previous
 * buffer: typing `fi` within one frame reported `"fi"` for the `i` (#480). [value] is that
 * buffer, the value the field last reported, and the editor's own value sits inside it at
 * [windowStart].
 */
internal class FieldBuffer(val value: TextFieldValue, val windowStart: Int) {
    companion object {
        /**
         * The field's buffer after it reported [reported] and the editor answered with [next], or
         * null when they agree. [windowStart] is where [next] sits in [reported] when it is not
         * empty; an emptied value sits at the field's cursor.
         */
        fun after(reported: TextFieldValue, next: TextFieldValue, windowStart: Int): FieldBuffer? {
            if (next == reported) return null
            val start = if (next.text.isEmpty()) reported.selection.min else windowStart
            val fits = start + next.text.length <= reported.text.length &&
                reported.text.regionMatches(start, next.text, 0, next.text.length)
            return if (fits) FieldBuffer(reported, start) else null
        }
    }
}

/** A field edit restated against the editor's value, and where that value sits in the field. */
internal class RebasedEdit(val value: TextFieldValue, val windowStart: Int)

/**
 * Restate [reported], an edit the field made to its stale [buffer], as the same edit made to
 * [current], the value the editor gave it, which sits in the buffer at
 * [FieldBuffer.windowStart]. Changes to buffer text outside that window are text the editor has
 * already dealt with, so they are dropped.
 */
internal fun rebaseFieldEdit(
    reported: TextFieldValue,
    buffer: FieldBuffer,
    current: TextFieldValue
): RebasedEdit {
    val old = buffer.value.text
    val new = reported.text
    val prefix = commonPrefix(old, new)
    val suffix = commonSuffix(old, new, prefix)
    val inserted = new.substring(prefix, new.length - suffix)
    val winFrom = buffer.windowStart
    val winTo = winFrom + current.text.length
    val from = prefix.coerceIn(winFrom, winTo)
    val to = (old.length - suffix).coerceIn(from, winTo)
    val text = current.text.substring(0, from - winFrom) + inserted +
        current.text.substring(to - winFrom)

    // Where an offset in the reported value lands in the restated one.
    fun map(offset: Int): Int = when {
        offset <= prefix -> offset.coerceIn(winFrom, from) - winFrom
        offset >= new.length - suffix -> {
            val inOld = offset - (new.length - suffix) + (old.length - suffix)
            from - winFrom + inserted.length + (inOld.coerceIn(to, winTo) - to)
        }
        else -> from - winFrom + (offset - prefix)
    }
    val composition = reported.composition
        ?.let { TextRange(map(it.start), map(it.end)) }
        ?.takeUnless { it.collapsed }
    return RebasedEdit(
        TextFieldValue(
            text,
            TextRange(map(reported.selection.start), map(reported.selection.end)),
            composition
        ),
        windowStart = if (prefix >= winFrom) winFrom else prefix
    )
}

/** Length of the common prefix of [a] and [b], never ending inside a surrogate pair. */
internal fun commonPrefix(a: String, b: String): Int {
    val max = minOf(a.length, b.length)
    var n = 0
    while (n < max && a[n] == b[n]) n++
    // Equal high surrogates followed by different low surrogates are different characters.
    if (n > 0 && a[n - 1].isHighSurrogate()) n--
    return n
}

/**
 * Length of the common suffix of [a] and [b] that does not overlap the first [prefix] chars of
 * either, never starting inside a surrogate pair.
 */
internal fun commonSuffix(a: String, b: String, prefix: Int): Int {
    val max = minOf(a.length, b.length) - prefix
    var n = 0
    while (n < max && a[a.length - 1 - n] == b[b.length - 1 - n]) n++
    // Equal low surrogates preceded by different high surrogates are different characters.
    if (n > 0 && a[a.length - n].isLowSurrogate()) n--
    return n
}
