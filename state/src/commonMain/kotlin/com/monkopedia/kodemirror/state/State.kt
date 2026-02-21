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
package com.monkopedia.kodemirror.state

import com.monkopedia.kodemirror.state.SingleOrList.Companion.singleOrList
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

// import {Text} from "./text"
// import {findClusterBreak} from "./char"
// import {ChangeSet, ChangeSpec, DefaultSplit} from "./change"
// import {EditorSelection, SelectionRange, checkSelection} from "./selection"
// import {Transaction, TransactionSpec, resolveTransaction, asArray, StateEffect} from "./transaction"
// import {allowMultipleSelections, changeFilter, transactionFilter, transactionExtender,
// lineSeparator, languageData, readOnly} from "./extension"
// import {Configuration, Facet, FacetReader, Extension, StateField, SlotStatus, ensureAddr, getAddr,
// Compartment, DynamicSlot} from "./facet"
// import {CharCategory, makeCategorizer} from "./charcategory"

// / Options passed when [creating](#state.EditorState^create) an
// / editor state.
data class EditorStateConfig(
    // / The initial document. Defaults to an empty document. Can be
    // / provided either as a plain string (which will be split into
    // / lines according to the value of the [`lineSeparator`
    // / facet](#state.EditorState^lineSeparator)), or an instance of
    // / the [`Text`](#state.Text) class (which is what the state will use
    // / to represent the document).
    val doc: Text? = null,

    // / The starting selection. Defaults to a cursor at the very start
    // / of the document.
    val selection: Selection? = null,

    // / [Extension(s)](#state.Extension) to associate with this state.
    val extensions: Extension? = null
)

// / The editor state class is a persistent (immutable) data structure.
// / To update a state, you [create](#state.EditorState.update) a
// / [transaction](#state.Transaction), which produces a _new_ state
// / instance, without modifying the original object.
// /
// / As such, _never_ mutate properties of a state directly. That'll
// / just break things.
class EditorState private constructor(
    // / @internal
    internal val config: Configuration,
    // / The current document.
    val doc: Text,
    // / The current selection.
    val selection: EditorSelection,
    // / @internal
    internal val values: MutableList<Any?>,
    internal var computeSlot: ((state: EditorState, slot: DynamicSlot) -> SlotStatus)?,
    tr: Transaction?
) {
    // / @internal
    internal val status: MutableList<SlotStatus> = config.statusTemplate.toMutableList()

    init {
        // Fill in the computed state immediately, so that further queries
        // for it made during the update return this state
        tr?._state = this
        for (i in this.config.dynamicSlots.indices) {
            ensureAddr(this, i shl 1)
        }
        this.computeSlot = null
    }

    // / Retrieve the value of a [state field](#state.StateField). Throws
    // / an error when the state doesn't have that field, unless you pass
    // / `false` as second parameter.
    fun <T> field(field: StateField<T>): T = field(field, require = true)!!

    fun <T> field(field: StateField<T>, require: Boolean): T? {
        val addr = this.config.address[field.id]
        if (addr == null) {
            if (require) throw IllegalArgumentException("Field is not present in this state")
            return null
        }
        ensureAddr(this, addr)
        @Suppress("UNCHECKED_CAST")
        return getAddr(this, addr) as T?
    }

    // / Create a [transaction](#state.Transaction) that updates this
    // / state. Any Int of [transaction specs](#state.TransactionSpec)
    // / can be passed. Unless
    // / [`sequential`](#state.TransactionSpec.sequential) is set, the
    // / [changes](#state.TransactionSpec.changes) (if any) of each spec
    // / are assumed to start in the _current_ document (not the document
    // / produced by previous specs), and its
    // / [selection](#state.TransactionSpec.selection) and
    // / [effects](#state.TransactionSpec.effects) are assumed to refer
    // / to the document created by its _own_ changes. The resulting
    // / transaction contains the combined effect of all the different
    // / specs. For [selection](#state.TransactionSpec.selection), later
    // / specs take precedence over earlier ones.
    fun update(vararg specs: TransactionSpec): Transaction =
        resolveTransaction(this, specs.toList(), true)

    fun update(
        // / The changes to the document made by this transaction.
        changes: ChangeSpec? = null,
        // / When set, this transaction explicitly updates the selection.
        // / Offsets in this selection should refer to the document as it is
        // / _after_ the transaction.
        selection: Selection? = null,
        // / Attach [state effects](#state.StateEffect) to this transaction.
        // / Again, when they contain positions and this same spec makes
        // / changes, those positions should refer to positions in the
        // / updated document.
        effects: SingleOrList<StateEffect<*>>? = null,
        // / Set [annotations](#state.Annotation) for this transaction.
        annotations: SingleOrList<Annotation<*>>? = null,
        // / Shorthand for `annotations:` [`Transaction.userEvent`](#state.Transaction^userEvent)`.of(...)`.
        userEvent: String? = null,
        // / When set to `true`, the transaction is marked as needing to
        // / scroll the current selection into view.
        scrollIntoView: Boolean = false,
        // / By default, transactions can be modified by [change
        // / filters](#state.EditorState^changeFilter) and [transaction
        // / filters](#state.EditorState^transactionFilter). You can set this
        // / to `false` to disable that. This can be necessary for
        // / transactions that, for example, include annotations that must be
        // / kept consistent with their changes.
        filter: Boolean = true,
        // / Normally, when multiple specs are combined (for example by
        // / [`EditorState.update`](#state.EditorState.update)), the
        // / positions in `changes` are taken to refer to the document
        // / positions in the initial document. When a spec has `sequental`
        // / set to true, its positions will be taken to refer to the
        // / document created by the specs before it instead.
        sequential: Boolean = false
    ): Transaction = update(
        TransactionSpec(
            changes = changes,
            selection = selection,
            effects = effects,
            annotations = annotations,
            userEvent = userEvent,
            scrollIntoView = scrollIntoView,
            filter = filter,
            sequential = sequential
        )
    )

    // / @internal
    internal fun applyTransaction(tr: Transaction) {
        var conf: Configuration? = this.config
        var base: Extension? = conf?.base
        var compartments = conf?.compartments.orEmpty().toMutableMap()
        for (effect in tr.effects.list) {
            if (effect.isOf(Compartment.reconfigure)) {
                if (conf != null) {
                    compartments = conf.compartments.toMutableMap()
                    conf = null
                }
                val value = effect.value as Compartment.CompartmentType
                compartments[value.compartment] = value.extension
            } else if (effect.isOf(StateEffect.reconfigure)) {
                conf = null
                base = effect.value as Extension?
            } else if (effect.isOf(StateEffect.appendConfig)) {
                conf = null
                base = listOfNotNull(base, effect.value as Extension?).extension
            }
        }
        val startValues = if (conf == null) {
            conf = Configuration.resolve(base!!, compartments, this)
            val intermediateState = EditorState(
                conf,
                this.doc,
                this.selection,
                conf.dynamicSlots.map { null }.toMutableList(),
                { state, slot -> slot.reconfigure(state, this) },
                null
            )
            intermediateState.values
        } else {
            tr.startState.values.toMutableList()
        }
        val selection =
            if (tr.startState.facet(allowMultipleSelections)) {
                (tr.newSelection as EditorSelection)
            } else {
                (tr.newSelection as EditorSelection).asSingle()
            }
        EditorState(
            conf,
            tr.newDoc,
            selection,
            startValues,
            { state, slot -> slot.update(state, tr) },
            tr
        )
    }

    // / Create a [transaction spec](#state.TransactionSpec) that
    // / replaces every selection range with the given content.
    fun replaceSelection(text: Text): TransactionSpec {
//        if (typeof text == "string") text = this.toText(text)
        return this.changeByRange { range ->
            PartialChangeResult(
                changes = ChangeSpecData(from = range.from, to = range.to, insert = text),
                range = EditorSelection.cursor(range.from + text.length)
            )
        }
    }

    data class PartialChangeResult(
        val range: SelectionRange,
        val changes: ChangeSpec? = null,
        val effects: SingleOrList<StateEffect<*>>? = null
    )

    // / Create a set of changes and a new selection by running the given
    // / fun for each range in the active selection. The fun
    // / can return an optional set of changes (in the coordinate space
    // / of the start document), plus an updated range (in the coordinate
    // / space of the document produced by the call's own changes). This
    // / method will merge all the changes and ranges into a single
    // / changeset and selection, and return it as a [transaction
    // / spec](#state.TransactionSpec), which can be passed to
    // / [`update`](#state.EditorState.update).
    fun changeByRange(f: (range: SelectionRange) -> PartialChangeResult): TransactionSpec {
        val sel = this.selection
        val result1 = f(sel.ranges[0])
        var changes = this.changes(result1.changes ?: ChangeSpec.empty)
        val ranges = mutableListOf(result1.range)
        var effects = result1.effects?.list.orEmpty()
        for (i in 1 until sel.ranges.size) {
            val result = f(sel.ranges[i])
            val newChanges = this.changes(result.changes ?: ChangeSpec.empty)
            val newMapped = newChanges.map(changes)
            for (j in ranges.indices) {
                ranges[j] = ranges[j].map(newMapped)
            }
            val mapBy = changes.mapDesc(newChanges, true)
            ranges.add(result.range.map(mapBy))
            changes = changes.compose(newMapped)
            effects = StateEffect.mapEffects(effects, newMapped) +
                StateEffect.mapEffects(result.effects?.list.orEmpty(), mapBy)
        }
        return TransactionSpec(
            changes = changes,
            selection = EditorSelection.create(ranges, sel.mainIndex),
            effects = effects.singleOrList
        )
    }

    // / Create a [change set](#state.ChangeSet) from the given change
    // / description, taking the state's document length and line
    // / separator into account.
    fun changes(spec: ChangeSpec = ChangeSpec.empty): ChangeSet {
        if (spec is ChangeSet) return spec
        return ChangeSet.of(spec, this.doc.length, this.facet(EditorState.lineSeparator))
    }

    // / Using the state's [line
    // / separator](#state.EditorState^lineSeparator), create a
    // / [`Text`](#state.Text) instance from the given string.
    fun toText(string: String): Text {
        this.facet(EditorState.lineSeparator)?.let {
            return Text.of(string.split(it))
        }
        return Text.of(string.split(DefaultSplit))
    }

    // / Return the given range of the document as a string.
    fun sliceDoc(from: Int = 0, to: Int = this.doc.length): String =
        this.doc.sliceString(from, to, this.lineBreak)

    // / Get the value of a state [facet](#state.Facet).
    fun <Output> facet(facet: FacetReader<Output>): Output {
        val addr = this.config.address[facet.id]
            ?: return facet.default
        ensureAddr(this, addr)
        @Suppress("UNCHECKED_CAST")
        return getAddr(this, addr) as Output
    }

    // / Convert this state to a JSON-serializable object. When custom
    // / fields should be serialized, you can pass them in as an object
    // / mapping property names (in the resulting object, which should
    // / not use `doc` or `selection`) to fields.
    fun toJSON(fields: Map<String, StateField<*>>? = null): JsonElement = buildJsonObject {
        put("doc", sliceDoc())
        put("selection", selection.toJSON())
        if (fields != null) {
            for ((prop, value) in fields) {
                if (config.address[value.id] != null) {
                    put(prop, serialize(value))
                }
            }
        }
    }

    // / The size (in columns) of a tab in the document, determined by
    // / the [`tabSize`](#state.EditorState^tabSize) facet.
    val tabSize: Int
        get() {
            return this.facet(EditorState.tabSize)
        }

    // / Get the proper [line-break](#state.EditorState^lineSeparator)
    // / string for this state.
    val lineBreak: String
        get() {
            return this.facet(EditorState.lineSeparator) ?: "\n"
        }

    // / Look up a translation for the given phrase (via the
    // / [`phrases`](#state.EditorState^phrases) facet), or return the
    // / original string if no translation is found.
    // /
    // / If additional arguments are passed, they will be inserted in
    // / place of markers like `$1` (for the first value) and `$2`, etc.
    // / A single `$` is equivalent to `$1`, and `$$` will produce a
    // / literal dollar sign.
    fun phrase(phrase: String, vararg insert: String): String {
        var phrase = this.facet(EditorState.phrases).firstNotNullOfOrNull { it[phrase] }
            ?: phrase
        if (insert.isNotEmpty()) {
            phrase = phrase.replace(Regex("\\$(\$|\\d*)"), { m ->
                if (m.groupValues.first() == "$") return@replace "$"
                val n = (m.groupValues.first().toIntOrNull() ?: 1)
                return@replace if (n == 0 || n > insert.size) m.value else insert[n - 1]
            })
        }
        return phrase
    }

    // / Find the values for a given language data field, provided by the
    // / the [`languageData`](#state.EditorState^languageData) facet.
    // /
    // / Examples of language data fields are...
    // /
    // / - [`"commentTokens"`](#commands.CommentTokens) for specifying
    // /   comment syntax.
    // / - [`"autocomplete"`](#autocomplete.autocompletion^config.override)
    // /   for providing language-specific completion sources.
    // / - [`"wordChars"`](#state.EditorState.charCategorizer) for adding
    // /   characters that should be considered part of words in this
    // /   language.
    // / - [`"closeBrackets"`](#autocomplete.CloseBracketConfig) controls
    // /   bracket closing behavior.
    fun <T> languageDataAt(name: String, pos: Int, side: Side = Side.NEG): List<T> {
        val values = mutableListOf<T>()
        for (provider in this.facet(languageData)) {
            for (result in provider(this, pos, side)) {
                @Suppress("UNCHECKED_CAST")
                (result[name] as? T)?.let {
                    values.add(it)
                }
            }
        }
        return values
    }

    // / Return a fun that can categorize strings (expected to
    // / represent a single [grapheme cluster](#state.findClusterBreak))
    // / into one of:
    // /
    // /  - Word (contains an alphanumeric character or a character
    // /    explicitly listed in the local language's `"wordChars"`
    // /    language data, which should be a string)
    // /  - Space (contains only whitespace)
    // /  - Other (anything else)
    fun charCategorizer(at: Int): (
        str: String
    ) -> CharCategory =
        makeCategorizer(this.languageDataAt<String>("wordChars", at).joinToString(""))

    // / Find the word at the given position, meaning the range
    // / containing all [word](#state.CharCategory.Word) characters
    // / around it. If no word characters are adjacent to the position,
    // / this returns null.
    fun wordAt(pos: Int): SelectionRange? {
        val line = this.doc.lineAt(pos)
        val text = line.text
        val from = line.from
        val length = line.length
        val cat = this.charCategorizer(pos)
        var start = pos - from
        var end = pos - from
        while (start > 0) {
            val prev = findClusterBreak(text, start, false)
            if (cat(text.substring(prev, start)) != CharCategory.Word) break
            start = prev
        }
        while (end < length) {
            val next = findClusterBreak(text, end)
            if (cat(text.substring(end, next)) != CharCategory.Word) break
            end = next
        }
        return if (start == end) null else EditorSelection.range(start + from, end + from)
    }

    companion object {
        // / Deserialize a state from its JSON representation. When custom
        // / fields should be deserialized, pass the same object you passed
        // / to [`toJSON`](#state.EditorState.toJSON) when serializing as
        // / third argument.
        fun fromJSON(
            json: JsonElement,
            config: EditorStateConfig = EditorStateConfig(),
            fields: Map<String, StateField<*>>? = null
        ): EditorState {
            if (json !is JsonObject || json["doc"] !is JsonPrimitive) {
                throw IllegalArgumentException("Invalid JSON representation for EditorState")
            }
            val fieldInit = mutableListOf<Extension>()
            if (fields != null) {
                for ((prop, field) in fields) {
                    if (prop in json.keys) {
                        val value = json[prop] ?: continue
                        fieldInit.add(deserializeInit(field, value))
                    }
                }
            }

            return EditorState.create(
                doc = json["doc"]!!.jsonPrimitive.content.asText,
                selection = json["selection"]?.let { EditorSelection.fromJSON(it) },
                extensions = (
                    if (config.extensions != null) {
                        fieldInit + config.extensions
                    } else {
                        fieldInit
                    }
                    ).extension
            )
        }

        fun create(doc: Text? = null, selection: Selection? = null, extensions: Extension? = null) =
            create(EditorStateConfig(doc, selection, extensions))

        // / Create a new state. You'll usually only need this when
        // / initializing an editor—updated states are created by applying
        // / transactions.
        fun create(config: EditorStateConfig): EditorState {
            val configuration = Configuration.resolve(
                config.extensions ?: emptyList<Extension>().extension,
                mutableMapOf()
            )
            val doc = config.doc
                ?: Text.of(listOf("")) // .split(configuration.staticFacet(EditorState.lineSeparator) || DefaultSplit))
            val sel = config.selection
            var selection = when (sel) {
                null -> EditorSelection.single(0)
                is EditorSelection -> sel
                is Selection.Data -> EditorSelection.single(sel.anchor, sel.head ?: sel.anchor)
            }
            checkSelection(selection, doc.length)
            if (!configuration.staticFacet(allowMultipleSelections)) {
                selection =
                    selection.asSingle()
            }
            return EditorState(
                configuration,
                doc,
                selection,
                configuration.dynamicSlots.map { null }.toMutableList(),
                { state, slot -> slot.create(state) },
                null
            )
        }

        // / A facet that, when enabled, causes the editor to allow multiple
        // / ranges to be selected. Be careful though, because by default the
        // / editor relies on the native DOM selection, which cannot handle
        // / multiple selections. An extension like
        // / [`drawSelection`](#view.drawSelection) can be used to make
        // / secondary selections visible to the user.
        val allowMultipleSelections = Facet.define<Boolean, Boolean>(
            combine = { values: List<Boolean> -> values.any { v -> v } },
            static = true
        )

        // / Configures the tab size to use in this state. The first
        // / (highest-precedence) value of the facet is used. If no value is
        // / given, this defaults to 4.
        val tabSize = Facet.define<Int, Int>(
            combine = { values -> values.firstOrNull() ?: 4 }
        )

        // / The line separator to use. By default, any of `"\n"`, `"\r\n"`
        // / and `"\r"` is treated as a separator when splitting lines, and
        // / lines are joined with `"\n"`.
        // /
        // / When you configure a value here, only that precise separator
        // / will be used, allowing you to round-trip documents through the
        // / editor without normalizing line separators.
        val lineSeparator = Facet.define<String, String?>(
            combine = { values -> values.getOrNull(0) },
            static = true
        )

        // / This facet controls the value of the
        // / [`readOnly`](#state.EditorState.readOnly) getter, which is
        // / consulted by commands and extensions that implement editing
        // / funality to determine whether they should apply. It
        // / defaults to false, but when its highest-precedence value is
        // / `true`, such funality disables itself.
        // /
        // / Not to be confused with
        // / [`EditorView.editable`](#view.EditorView^editable), which
        // / controls whether the editor's DOM is set to be editable (and
        // / thus focusable).
        var readOnlyFacet = Facet.define<Boolean, Boolean>(
            combine = { values -> values.getOrNull(0) ?: false }
        )

        // / Returns true when the editor is
        // / [configured](#state.EditorState^readOnly) to be read-only.
        val EditorState.readOnly: Boolean
            get() {
                return this.facet(readOnlyFacet)
            }

        // / Registers translation phrases. The
        // / [`phrase`](#state.EditorState.phrase) method will look through
        // / all objects registered with this facet to find translations for
        // / its argument.
        val phrases = Facet.define<Map<String, String>>(
            compare = { a, b ->
                a == b
            }
        )

        // / A facet used to register [language
        // / data](#state.EditorState.languageDataAt) providers.
        val languageData = Facet.define<LanguageDataType>()

        // / Facet used to register change filters, which are called for each
        // / transaction (unless explicitly
        // / [disabled](#state.TransactionSpec.filter)), and can suppress
        // / part of the transaction's changes.
        // /
        // / Such a fun can return `true` to indicate that it doesn't
        // / want to do anything, `false` to completely stop the changes in
        // / the transaction, or a set of ranges in which changes should be
        // / suppressed. Such ranges are represented as an array of Ints,
        // / with each pair of two Ints indicating the start and end of a
        // / range. So for example `[10, 20, 100, 110]` suppresses changes
        // / between 10 and 20, and between 100 and 110.
        val changeFilter =
            Facet.define<(tr: Transaction) -> Either<Boolean, List<Pair<Int, Int>>>>()

        // / Facet used to register a hook that gets a chance to update or
        // / replace transaction specs before they are applied. This will
        // / only be applied for transactions that don't have
        // / [`filter`](#state.TransactionSpec.filter) set to `false`. You
        // / can either return a single transaction spec (possibly the input
        // / transaction), or an array of specs (which will be combined in
        // / the same way as the arguments to
        // / [`EditorState.update`](#state.EditorState.update)).
        // /
        // / When possible, it is recommended to avoid accessing
        // / [`Transaction.state`](#state.Transaction.state) in a filter,
        // / since it will force creation of a state that will then be
        // / discarded again, if the transaction is actually filtered.
        // /
        // / (This funality should be used with care. Indiscriminately
        // / modifying transaction is likely to break something or degrade
        // / the user experience.)
        val transactionFilter = Facet.define<(tr: Transaction) -> SingleOrList<Transactable>>()

        // / This is a more limited form of
        // / [`transactionFilter`](#state.EditorState^transactionFilter),
        // / which can only add
        // / [annotations](#state.TransactionSpec.annotations) and
        // / [effects](#state.TransactionSpec.effects). _But_, this type
        // / of filter runs even if the transaction has disabled regular
        // / [filtering](#state.TransactionSpec.filter), making it suitable
        // / for effects that don't need to touch the changes or selection,
        // / but do want to process every transaction.
        // /
        // / Extenders run _after_ filters, when both are present.
        val transactionExtender = Facet.define<(tr: Transaction) -> Transactable?>()
    }
}

private fun <T> EditorState.serialize(f: StateField<T>) =
    Json.encodeToJsonElement(f.spec.serializer!!, field(f))

private fun <T> deserializeInit(f: StateField<T>, value: JsonElement): Extension =
    f.init { state -> Json.decodeFromJsonElement(f.spec.serializer!!, value) }
