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

import com.monkopedia.kodemirror.language.syntaxTree
import com.monkopedia.kodemirror.state.ChangeByRangeResult
import com.monkopedia.kodemirror.state.ChangeSpec
import com.monkopedia.kodemirror.state.CharCategory
import com.monkopedia.kodemirror.state.DocPos
import com.monkopedia.kodemirror.state.EditorSelection
import com.monkopedia.kodemirror.state.EditorState
import com.monkopedia.kodemirror.state.Extension
import com.monkopedia.kodemirror.state.Facet
import com.monkopedia.kodemirror.state.InsertContent
import com.monkopedia.kodemirror.state.LanguageDataKey
import com.monkopedia.kodemirror.state.MapMode
import com.monkopedia.kodemirror.state.SelectionRange
import com.monkopedia.kodemirror.state.StateEffect
import com.monkopedia.kodemirror.state.StateEffectType
import com.monkopedia.kodemirror.state.StateField
import com.monkopedia.kodemirror.state.StateFieldSpec
import com.monkopedia.kodemirror.state.Text
import com.monkopedia.kodemirror.state.Transaction
import com.monkopedia.kodemirror.state.TransactionFilterResult
import com.monkopedia.kodemirror.state.TransactionSpec
import com.monkopedia.kodemirror.state.codePointAt
import com.monkopedia.kodemirror.state.extensionListOf
import com.monkopedia.kodemirror.state.fromCodePoint
import com.monkopedia.kodemirror.state.transactionFilter
import com.monkopedia.kodemirror.view.EditorSession
import com.monkopedia.kodemirror.view.KeyBinding
import com.monkopedia.kodemirror.view.keymap

/**
 * Configuration for close-brackets behavior.
 *
 * Like upstream, the `closeBrackets` [language data][EditorState.languageDataAt] at the
 * cursor takes precedence: a map with `brackets` (the opening tokens, as a list of
 * strings; each one's closing token is derived as upstream's `closing` does, and a
 * quote listed three times over, such as `"""`, enables triple quotes), `before` and
 * `stringPrefixes` (prefixes such as Python's `f` that may precede a string's opening
 * quote). This config is used where the language provides none.
 *
 * @param brackets The bracket/quote pairs to auto-close.
 * @param before Characters before which a closing bracket may be inserted.
 */
data class CloseBracketsConfig(val brackets: String = "()[]{}''\"\"", val before: String = ")]}:;>")

/** The [CloseBracketsConfig] given to [closeBrackets], or the default without one. */
private val closeBracketsConfig: Facet<CloseBracketsConfig, CloseBracketsConfig> =
    Facet.define(combine = { it.firstOrNull() ?: CloseBracketsConfig() })

/** The `closeBrackets` language data, read loosely as upstream's `CloseBracketConfig`. */
private val closeBracketsData: LanguageDataKey<Map<String, Any?>> = LanguageDataKey("closeBrackets")

/**
 * The close-brackets settings at a position: the [brackets] as opening and closing
 * tokens, [before] and [stringPrefixes].
 */
private class BracketConfig(
    val brackets: List<Pair<String, String>>,
    val before: String,
    val stringPrefixes: List<String>
) {
    /** Whether [token] tripled is one of the brackets, so triple quotes are handled. */
    fun allowsTriple(token: String) = brackets.any { it.first == token + token + token }
}

private val defaultBrackets = CloseBracketsConfig()

/** Upstream's `config`: the language's settings at [pos], else [closeBracketsConfig]. */
private fun config(state: EditorState, pos: DocPos): BracketConfig {
    val data = state.languageDataAt(closeBracketsData, pos).firstOrNull()
    if (data == null) {
        val config = state.facet(closeBracketsConfig)
        return BracketConfig(config.pairs(), config.before, emptyList())
    }
    val brackets = (data["brackets"] as? List<*>)?.filterIsInstance<String>()
        ?.map { it to closing(codePointAt(it, 0)) }
        ?: defaultBrackets.pairs()
    val prefixes = (data["stringPrefixes"] as? List<*>)?.filterIsInstance<String>().orEmpty()
    return BracketConfig(brackets, data["before"] as? String ?: defaultBrackets.before, prefixes)
}

private fun CloseBracketsConfig.pairs(): List<Pair<String, String>> =
    brackets.chunked(2).filter { it.length == 2 }.map { "${it[0]}" to "${it[1]}" }

private const val DEFINED_CLOSING = "()[]{}<>«»»«［］｛｝"

/** Upstream's `closing`: the closing token for an opening code point. */
private fun closing(ch: Int): String {
    for (i in DEFINED_CLOSING.indices step 2) {
        if (DEFINED_CLOSING[i].code == ch) return DEFINED_CLOSING[i + 1].toString()
    }
    return fromCodePoint(if (ch < 128) ch else ch + 1)
}

/** Records a closing bracket the extension inserted, at its (new-document) position. */
private val closeBracketEffect: StateEffectType<DocPos> =
    StateEffect.define { pos, mapping -> mapping.mapPos(pos, -1, MapMode.TrackAfter) }

/**
 * Positions of the closing brackets the extension inserted, which typing that
 * bracket may overtype. Like upstream's `bracketState`, an entry drops when its
 * character is deleted or replaced, and when the selection leaves its line.
 */
private val bracketState: StateField<List<DocPos>> = StateField.define(
    StateFieldSpec(
        create = { emptyList() },
        update = { value, tr ->
            var result = value.mapNotNull { pos ->
                val from = tr.changes.mapPos(pos, 1)
                if (tr.changes.mapPos(pos + 1, -1) > from) from else null
            }
            val selection = tr.selection
            if (selection != null) {
                val line = tr.state.doc.lineAt(selection.main.head)
                result = result.filter { it >= line.from && it <= line.to }
            }
            for (effect in tr.effects) {
                effect.asType(closeBracketEffect)?.let { result = result + it.value }
            }
            result
        }
    )
)

/**
 * Extension that auto-closes brackets and quotes, following upstream
 * `@codemirror/autocomplete`'s `closeBrackets`:
 *
 * - Typing an opening bracket inserts its closing bracket after each cursor
 *   when the next character is whitespace, one of [CloseBracketsConfig.before],
 *   or the end of the document, and wraps each non-empty selection.
 * - Typing a closing bracket directly in front of one the extension inserted
 *   moves over it.
 * - A quote is closed only where a string can start (not after a word
 *   character) and the cursor is not already inside a string.
 */
fun closeBrackets(config: CloseBracketsConfig = CloseBracketsConfig()): Extension {
    val filter = transactionFilter.of { tr ->
        val state = tr.startState
        val main = state.selection.main
        val typed = if (tr.isUserEvent("input.type") && !state.readOnly) typedAt(tr, main) else null
        val spec = typed?.let { bracketTransaction(state, it) }
        if (spec == null) {
            TransactionFilterResult.Filtered(tr)
        } else {
            TransactionFilterResult.Specs(listOf(spec))
        }
    }
    return extensionListOf(
        closeBracketsConfig.of(config),
        bracketState,
        filter,
        keymap.of(closeBracketsKeymap)
    )
}

/**
 * The text a typed transaction put in place of exactly [main], if it is a
 * single character (one code point), as upstream's input handler requires.
 */
private fun typedAt(tr: Transaction, main: SelectionRange): String? {
    var typed: String? = null
    tr.changes.iterChanges(
        f = { fromA, toA, _, _, inserted ->
            if (fromA == main.from && toA == main.to) typed = inserted.sliceString(DocPos.ZERO)
        }
    )
    val text = typed ?: return null
    val single = text.length == 1 || text.length == 2 && text[0].isHighSurrogate()
    return if (single) text else null
}

/** Delete bracket pair command: when every cursor is between a pair, delete both. */
val deleteBracketPair: (EditorSession) -> Boolean = command@{ view ->
    val state = view.state
    if (state.readOnly) return@command false
    val brackets = config(state, state.selection.main.head).brackets
    var dont = false
    val spec = state.changeByRange { range ->
        val before = prevChar(state.doc, range.head)
        val next = nextChar(state.doc, range.head)
        val pair = if (range.empty) {
            brackets.find { (open, close) ->
                open == before && close == next
            }
        } else {
            null
        }
        if (pair == null) {
            dont = true
            ChangeByRangeResult(range = range)
        } else {
            val len = pair.first.length
            ChangeByRangeResult(
                changes = ChangeSpec.Single(range.head - len, range.head + len),
                range = EditorSelection.cursor(range.head - len)
            )
        }
    }
    if (!dont) view.dispatch(spec.copy(userEvent = "delete.backward", scrollIntoView = true))
    !dont
}

/** Keymap for close-brackets: Backspace deletes bracket pair. */
val closeBracketsKeymap: List<KeyBinding> = listOf(
    KeyBinding(key = "Backspace", run = deleteBracketPair)
)

/**
 * The transaction for typing [bracket] over the selection: the close-brackets
 * behaviour when it applies (see [closeBrackets]), otherwise a plain insertion
 * replacing each selection range.
 */
fun insertBracket(state: EditorState, bracket: String): TransactionSpec =
    bracketTransaction(state, bracket)
        ?: state.replaceSelection(bracket).copy(userEvent = "input.type")

/** Upstream's `insertBracket`: `null` when [bracket] needs no special handling. */
private fun bracketTransaction(state: EditorState, bracket: String): TransactionSpec? {
    val config = config(state, state.selection.main.head)
    for ((open, close) in config.brackets) {
        if (bracket == open) {
            return if (open == close) {
                handleSame(state, open, config.allowsTriple(open), config.stringPrefixes)
            } else {
                handleOpen(state, open, close, config.before)
            }
        }
        if (bracket == close && closedBracketAt(state, state.selection.main.from)) {
            return handleClose(state, close)
        }
    }
    return null
}

private fun closedBracketAt(state: EditorState, pos: DocPos): Boolean =
    state.field(bracketState, require = false)?.contains(pos) == true

private fun nextChar(doc: Text, pos: DocPos): String {
    val next = doc.sliceString(pos, minOf(pos + 2, DocPos(doc.length)))
    return if (next.isNotEmpty() && next[0].isHighSurrogate()) next else next.take(1)
}

private fun prevChar(doc: Text, pos: DocPos): String {
    val prev = doc.sliceString(maxOf(pos - 2, DocPos.ZERO), pos)
    return if (prev.length == 2 && prev[0].isHighSurrogate()) prev else prev.takeLast(1)
}

/**
 * Apply [f] to every range, or return `null` if any range declines (returns
 * `null`), as upstream's `dont` pattern.
 */
private fun allRanges(
    state: EditorState,
    f: (SelectionRange) -> ChangeByRangeResult?
): TransactionSpec? {
    var dont = false
    val spec = state.changeByRange { range ->
        f(range) ?: ChangeByRangeResult(range = range).also { dont = true }
    }
    return if (dont) null else spec.copy(userEvent = "input.type", scrollIntoView = true)
}

private fun insert(at: DocPos, text: String) =
    ChangeSpec.Single(at, at, InsertContent.StringContent(text))

/** Wrap a non-empty [range] in [open]/[close]. */
private fun wrap(range: SelectionRange, open: String, close: String) = ChangeByRangeResult(
    changes = ChangeSpec.Multi(listOf(insert(range.from, open), insert(range.to, close))),
    effects = listOf(closeBracketEffect.of(range.to + open.length)),
    range = EditorSelection.range(range.anchor + open.length, range.head + open.length)
)

/** Insert [open] + [close] at [pos], leaving the cursor between them. */
private fun pairAt(pos: DocPos, open: String, close: String) = ChangeByRangeResult(
    changes = insert(pos, open + close),
    effects = listOf(closeBracketEffect.of(pos + open.length)),
    range = EditorSelection.cursor(pos + open.length)
)

private fun handleOpen(state: EditorState, open: String, close: String, closeBefore: String) =
    allRanges(state) { range ->
        if (!range.empty) return@allRanges wrap(range, open, close)
        val next = nextChar(state.doc, range.head)
        if (next.isEmpty() || next.isBlank() || closeBefore.contains(next)) {
            pairAt(range.head, open, close)
        } else {
            null
        }
    }

private fun handleClose(state: EditorState, close: String) = allRanges(state) { range ->
    if (range.empty && nextChar(state.doc, range.head) == close) {
        ChangeByRangeResult(
            changes = ChangeSpec.Single(
                range.head,
                range.head + close.length,
                InsertContent.StringContent(close)
            ),
            range = EditorSelection.cursor(range.head + close.length)
        )
    } else {
        null
    }
}

/**
 * Opening and closing token are the same (quotes), possibly with triple quotes (as in
 * `"""abc"""`-style quoting) when [allowTriple].
 */
private fun handleSame(
    state: EditorState,
    token: String,
    allowTriple: Boolean,
    prefixes: List<String>
) = allRanges(state) { range ->
    if (!range.empty) return@allRanges wrap(range, token, token)
    val pos = range.head
    val next = nextChar(state.doc, pos)
    val triple = token + token + token
    if (next == token) {
        when {
            nodeStart(state, pos) -> pairAt(pos, token, token)
            closedBracketAt(state, pos) -> {
                val end = minOf(pos + triple.length, DocPos(state.doc.length))
                val isTriple = allowTriple && state.sliceDoc(pos, end) == triple
                val content = if (isTriple) triple else token
                ChangeByRangeResult(
                    changes = ChangeSpec.Single(
                        pos,
                        pos + content.length,
                        InsertContent.StringContent(content)
                    ),
                    range = EditorSelection.cursor(pos + content.length)
                )
            }
            else -> null
        }
    } else if (allowTriple && opensTriple(state, pos, token, prefixes)) {
        pairAt(pos, token, triple)
    } else if (state.charCategorizer(pos)(next) != CharCategory.Word &&
        canStartStringAt(state, pos, prefixes) != null &&
        !probablyInString(state, pos, token, prefixes)
    ) {
        pairAt(pos, token, token)
    } else {
        null
    }
}

/** Whether a [token] typed at [pos] is the third of a triple quote that starts a string. */
private fun opensTriple(
    state: EditorState,
    pos: DocPos,
    token: String,
    prefixes: List<String>
): Boolean {
    if (pos.value < 2 * token.length) return false
    val start = pos - 2 * token.length
    if (state.sliceDoc(start, pos) != token + token) return false
    return canStartStringAt(state, start, prefixes)?.let { nodeStart(state, it) } == true
}

private fun nodeStart(state: EditorState, pos: DocPos): Boolean {
    val node = syntaxTree(state).resolveInner(pos.value + 1)
    return node.parent != null && node.from == pos.value
}

/**
 * Whether [pos] is inside a string already, judged from the syntax tree around it.
 * A node counts as a string when it starts with [quote], possibly after one of
 * [prefixes].
 */
private fun probablyInString(
    state: EditorState,
    pos: DocPos,
    quote: String,
    prefixes: List<String>
): Boolean {
    var node = syntaxTree(state).resolveInner(pos.value, -1)
    val maxPrefix = prefixes.maxOfOrNull { it.length } ?: 0
    repeat(5) {
        val start = state.sliceDoc(
            DocPos(node.from),
            DocPos(minOf(node.to, node.from + quote.length + maxPrefix))
        )
        val quotePos = start.indexOf(quote)
        if (quotePos == 0 || quotePos > 0 && start.substring(0, quotePos) in prefixes) {
            var first = node.firstChild
            while (first != null &&
                first.from == node.from &&
                first.to - first.from > quote.length + quotePos
            ) {
                if (state.sliceDoc(DocPos(first.to - quote.length), DocPos(first.to)) == quote) {
                    return false
                }
                first = first.firstChild
            }
            return true
        }
        node = (if (node.to == pos.value) node.parent else null) ?: return false
    }
    return false
}

/**
 * Where a string starting at [pos] would begin: [pos] unless a word character
 * precedes it, else the start of one of [prefixes] that ends there and has no word
 * character before it, else `null`.
 */
private fun canStartStringAt(state: EditorState, pos: DocPos, prefixes: List<String>): DocPos? {
    val charCat = state.charCategorizer(pos)
    if (charCat(state.sliceDoc(maxOf(pos - 1, DocPos.ZERO), pos)) != CharCategory.Word) return pos
    for (prefix in prefixes) {
        val start = pos - prefix.length
        if (start >= DocPos.ZERO &&
            state.sliceDoc(start, pos) == prefix &&
            charCat(state.sliceDoc(maxOf(start - 1, DocPos.ZERO), start)) != CharCategory.Word
        ) {
            return start
        }
    }
    return null
}
