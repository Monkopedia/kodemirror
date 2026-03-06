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

sealed interface DocSpec {
    data class StringDoc(val content: String) : DocSpec
    data class TextDoc(val text: Text) : DocSpec
}

fun String.asDoc(): DocSpec = DocSpec.StringDoc(this)

/**
 * Options passed when creating an editor state.
 */
data class EditorStateConfig(
    /**
     * The initial document. Defaults to an empty document.
     */
    val doc: DocSpec? = null,
    /**
     * The starting selection. Defaults to a cursor at position 0.
     */
    val selection: SelectionSpec? = null,
    /**
     * Extension(s) to associate with this state.
     */
    val extensions: Extension? = null
)

/**
 * The editor state class is a persistent (immutable) data
 * structure. To update a state, you create a [Transaction],
 * which produces a _new_ state instance.
 */
class EditorState private constructor(
    internal val config: Configuration,
    /** The current document. */
    val doc: Text,
    /** The current selection. */
    val selection: EditorSelection,
    internal val values: Array<Any?>,
    computeSlot: (
        EditorState,
        DynamicSlot
    ) -> SlotStatus,
    tr: Transaction?
) {
    internal val status: Array<SlotStatus> =
        config.statusTemplate.toTypedArray()
    internal var computeSlot:
        ((EditorState, DynamicSlot) -> SlotStatus)? =
        computeSlot

    init {
        if (tr != null) tr._state = this
        for (i in config.dynamicSlots.indices) {
            ensureAddr(this, i shl 1)
        }
        this.computeSlot = null
    }

    /**
     * Retrieve the value of a [state field][StateField]. Throws
     * an error when the state doesn't have that field, unless
     * you pass false as second parameter.
     */
    @Suppress("UNCHECKED_CAST")
    fun <T> field(field: StateField<T>): T {
        val addr = config.address[field.id]
            ?: throw IllegalArgumentException(
                "Field is not present in this state"
            )
        ensureAddr(this, addr)
        return getAddr(this, addr) as T
    }

    /**
     * Retrieve the value of a [state field][StateField], or
     * null if the field is not present.
     */
    @Suppress("UNCHECKED_CAST")
    fun <T> field(field: StateField<T>, require: Boolean): T? {
        val addr = config.address[field.id]
        if (addr == null) {
            if (require) {
                throw IllegalArgumentException(
                    "Field is not present in this state"
                )
            }
            return null
        }
        ensureAddr(this, addr)
        return getAddr(this, addr) as T
    }

    /** Retrieve the value of a [state field][StateField] using index syntax. */
    operator fun <T> get(field: StateField<T>): T = field(field)

    /**
     * Create a [Transaction] that updates this state.
     */
    fun update(vararg specs: TransactionSpec): Transaction = resolveTransaction(
        this,
        specs.toList(),
        true
    )

    internal fun applyTransaction(tr: Transaction) {
        var conf: Configuration? = config
        var base = conf!!.base
        var compartments =
            conf.compartments.toMutableMap()
        for (effect in tr.effects) {
            val compartmentReconfig =
                effect.asType(Compartment.reconfigure)
            val stateReconfig =
                effect.asType(StateEffect.reconfigure)
            val stateAppend =
                effect.asType(StateEffect.appendConfig)
            when {
                compartmentReconfig != null -> {
                    if (conf != null) {
                        compartments =
                            conf.compartments
                                .toMutableMap()
                        conf = null
                    }
                    compartments[
                        compartmentReconfig.value.compartment
                    ] = compartmentReconfig.value.extension
                }
                stateReconfig != null -> {
                    conf = null
                    base = stateReconfig.value
                }
                stateAppend != null -> {
                    conf = null
                    base = ExtensionList(
                        (
                            if (base is ExtensionList) {
                                base.extensions
                            } else {
                                listOf(base)
                            }
                            ) + stateAppend.value
                    )
                }
            }
        }
        val startValues: Array<Any?>
        if (conf == null) {
            conf = Configuration.resolve(
                base, compartments, this
            )
            val intermediateState = EditorState(
                conf,
                doc,
                selection,
                Array(conf.dynamicSlots.size) { null },
                { state, slot ->
                    slot.reconfigure(state, this)
                },
                null
            )
            startValues = intermediateState.values
        } else {
            startValues = tr.startState.values.copyOf()
        }
        val sel = if (
            tr.startState
                .facet(allowMultipleSelections)
        ) {
            tr.newSelection
        } else {
            tr.newSelection.asSingle()
        }
        EditorState(
            conf,
            tr.newDoc,
            sel,
            startValues,
            { state, slot -> slot.update(state, tr) },
            tr
        )
    }

    /**
     * Create a [transaction spec][TransactionSpec] that replaces
     * every selection range with the given content.
     */
    fun replaceSelection(text: String): TransactionSpec = replaceSelection(toText(text))

    fun replaceSelection(text: Text): TransactionSpec {
        return changeByRange { range ->
            ChangeByRangeResult(
                changes = ChangeSpec.Single(
                    range.from,
                    range.to,
                    InsertContent.TextContent(text)
                ),
                range = EditorSelection.cursor(
                    range.from + text.length
                )
            )
        }
    }

    /**
     * Create a set of changes and a new selection by running the
     * given function for each range in the active selection.
     */
    fun changeByRange(f: (SelectionRange) -> ChangeByRangeResult): TransactionSpec {
        val sel = selection
        val result1 = f(sel.ranges[0])
        var changes = changes(result1.changes)
        val ranges = mutableListOf(result1.range)
        var effects = result1.effects ?: emptyList()
        for (i in 1 until sel.ranges.size) {
            val result = f(sel.ranges[i])
            val newChanges = changes(result.changes)
            val newMapped = newChanges.map(changes)
            for (j in 0 until i) {
                ranges[j] = ranges[j].map(newMapped)
            }
            val mapBy = changes.mapDesc(
                newChanges,
                true
            )
            ranges.add(result.range.map(mapBy))
            changes = changes.compose(newMapped)
            effects = StateEffect.mapEffects(
                effects, newMapped
            ) + StateEffect.mapEffects(
                result.effects ?: emptyList(), mapBy
            )
        }
        return TransactionSpec(
            changes = ChangeSpec.Set(changes),
            selection = SelectionSpec.EditorSelectionSpec(
                EditorSelection.create(ranges, sel.mainIndex)
            ),
            effects = effects.takeIf { it.isNotEmpty() }
        )
    }

    /**
     * Create a [ChangeSet] from the given change description.
     */
    fun changes(spec: ChangeSpec? = null): ChangeSet {
        val actualSpec = spec
            ?: ChangeSpec.Multi(emptyList())
        if (actualSpec is ChangeSpec.Set) {
            return actualSpec.changeSet
        }
        return ChangeSet.of(
            actualSpec,
            doc.length,
            facet(EditorState.lineSeparator)
        )
    }

    /**
     * Using the state's line separator, create a [Text] instance
     * from the given string.
     */
    fun toText(string: String): Text = Text.of(
        string.split(
            facet(EditorState.lineSeparator)
                ?.let { Regex(Regex.escape(it)) }
                ?: DefaultSplit
        )
    )

    /**
     * Return the given range of the document as a string.
     */
    fun sliceDoc(from: Int = 0, to: Int = doc.length): String = doc.sliceString(from, to, lineBreak)

    /**
     * Get the value of a state [facet][Facet].
     */
    @Suppress("UNCHECKED_CAST")
    fun <Output> facet(facet: Facet<*, Output>): Output {
        val addr = config.address[facet.id]
            ?: return facet.default
        ensureAddr(this, addr)
        return getAddr(this, addr) as Output
    }

    /**
     * Get the value of a state facet via a [FacetReader].
     */
    @Suppress("UNCHECKED_CAST")
    fun <Output> facet(reader: FacetReader<Output>): Output {
        val addr = config.address[reader.id]
            ?: return reader.default
        ensureAddr(this, addr)
        return getAddr(this, addr) as Output
    }

    /**
     * Convert this state to a JSON-serializable object.
     */
    fun toJSON(fields: Map<String, StateField<*>>? = null): Map<String, Any?> {
        val result = mutableMapOf<String, Any?>(
            "doc" to sliceDoc(),
            "selection" to selection.toJSON()
        )
        if (fields != null) {
            for ((prop, value) in fields) {
                if (config.address[value.id] != null) {
                    @Suppress("UNCHECKED_CAST")
                    val sf = value as StateField<Any?>
                    result[prop] = sf.spec.toJSON?.invoke(
                        field(sf), this
                    )
                }
            }
        }
        return result
    }

    /**
     * Look up a translation for the given phrase.
     */
    fun phrase(phrase: String, vararg insert: Any): String {
        var result = phrase
        for (map in facet(EditorState.phrases)) {
            if (map.containsKey(result)) {
                result = map[result]!!
                break
            }
        }
        if (insert.isNotEmpty()) {
            result = result.replace(
                Regex("\\$(\\\$|\\d*)")
            ) { match ->
                val i = match.groupValues[1]
                if (i == "$") {
                    "$"
                } else {
                    val n = if (i.isEmpty()) {
                        1
                    } else {
                        i.toInt()
                    }
                    if (n == 0 || n > insert.size) {
                        match.value
                    } else {
                        insert[n - 1].toString()
                    }
                }
            }
        }
        return result
    }

    /**
     * Find the values for a given language data field.
     */
    @Suppress("UNCHECKED_CAST")
    fun <T> languageDataAt(name: String, pos: Int, side: Int = -1): List<T> {
        val values = mutableListOf<T>()
        for (provider in facet(languageData)) {
            for (result in provider(this, pos, side)) {
                if (result.containsKey(name)) {
                    values.add(result[name] as T)
                }
            }
        }
        return values
    }

    /**
     * Return a function that can categorize strings into one of:
     * Word, Space, or Other.
     */
    fun charCategorizer(at: Int): (String) -> CharCategory {
        val chars =
            languageDataAt<String>("wordChars", at)
        return makeCategorizer(
            if (chars.isNotEmpty()) chars[0] else ""
        )
    }

    /**
     * Find the word at the given position.
     */
    fun wordAt(pos: Int): SelectionRange? {
        val line = doc.lineAt(pos)
        val text = line.text
        val cat = charCategorizer(pos)
        var start = pos - line.from
        var end = pos - line.from
        while (start > 0) {
            val prev = findClusterBreak(
                text,
                start,
                false
            )
            if (cat(text.substring(prev, start)) !=
                CharCategory.Word
            ) {
                break
            }
            start = prev
        }
        while (end < line.length) {
            val next = findClusterBreak(text, end)
            if (cat(text.substring(end, next)) !=
                CharCategory.Word
            ) {
                break
            }
            end = next
        }
        return if (start == end) {
            null
        } else {
            EditorSelection.range(
                start + line.from,
                end + line.from
            )
        }
    }

    companion object {
        /**
         * Deserialize a state from its JSON representation.
         */
        @Suppress("UNCHECKED_CAST")
        fun fromJSON(
            json: Map<String, Any?>,
            config: EditorStateConfig = EditorStateConfig(),
            fields: Map<String, StateField<*>>? = null
        ): EditorState {
            val docStr = json["doc"] as? String
                ?: throw IllegalArgumentException(
                    "Invalid JSON representation " +
                        "for EditorState"
                )
            val fieldInit = mutableListOf<Extension>()
            if (fields != null) {
                for ((prop, field) in fields) {
                    if (json.containsKey(prop)) {
                        val value = json[prop]
                        val sf =
                            field as StateField<Any?>
                        fieldInit.add(
                            sf.init { state ->
                                sf.spec.fromJSON!!
                                    .invoke(value, state)
                            }
                        )
                    }
                }
            }
            return create(
                EditorStateConfig(
                    doc = DocSpec.StringDoc(docStr),
                    selection = SelectionSpec.EditorSelectionSpec(
                        EditorSelection.fromJSON(
                            json["selection"] as Map<String, Any?>
                        )
                    ),
                    extensions = if (config.extensions != null) {
                        ExtensionList(
                            fieldInit +
                                config.extensions
                        )
                    } else if (fieldInit.isNotEmpty()) {
                        ExtensionList(fieldInit)
                    } else {
                        null
                    }
                )
            )
        }

        /**
         * Create a new state with a string document and optional extensions.
         */
        fun create(
            doc: String,
            extensions: Extension? = null,
            selection: SelectionSpec? = null
        ): EditorState = create(
            EditorStateConfig(
                doc = doc.asDoc(),
                extensions = extensions,
                selection = selection
            )
        )

        /**
         * Create a new state.
         */
        fun create(config: EditorStateConfig = EditorStateConfig()): EditorState {
            val configuration = Configuration.resolve(
                config.extensions ?: ExtensionList(emptyList()),
                emptyMap()
            )
            val lineSepRegex = configuration.staticFacet(
                lineSeparator
            )?.let {
                Regex(Regex.escape(it))
            } ?: DefaultSplit
            val doc = when (val d = config.doc) {
                is DocSpec.TextDoc -> d.text
                is DocSpec.StringDoc -> Text.of(
                    d.content.split(lineSepRegex)
                )
                null -> Text.of(
                    "".split(lineSepRegex)
                )
            }
            var selection = when (val s = config.selection) {
                null -> EditorSelection.single(0)
                is SelectionSpec.EditorSelectionSpec ->
                    s.selection
                is SelectionSpec.CursorSpec ->
                    EditorSelection.single(
                        s.anchor,
                        s.head ?: s.anchor
                    )
            }
            checkSelection(selection, doc.length)
            if (!configuration.staticFacet(
                    allowMultipleSelections
                )
            ) {
                selection = selection.asSingle()
            }
            return EditorState(
                configuration,
                doc,
                selection,
                Array(
                    configuration.dynamicSlots.size
                ) { null },
                { state, slot -> slot.create(state) },
                null
            )
        }

        /** A facet for allowing multiple selections. */
        val allowMultipleSelections =
            com.monkopedia.kodemirror.state
                .allowMultipleSelections

        /** Configures the tab size to use in this state. */
        val tabSize: Facet<Int, Int> = Facet.define(
            combine = { values ->
                values.firstOrNull() ?: 4
            }
        )

        /** The line separator to use. */
        val lineSeparator =
            com.monkopedia.kodemirror.state.lineSeparator

        /** This facet controls the readOnly getter. */
        val readOnly =
            com.monkopedia.kodemirror.state.readOnly

        /** Registers translation phrases. */
        val phrases: Facet<
            Map<String, String>,
            List<Map<String, String>>
            > = Facet.define(
            compareInput = { a, b ->
                val kA = a.keys
                val kB = b.keys
                kA.size == kB.size &&
                    kA.all { k -> a[k] == b[k] }
            }
        )

        val languageData =
            com.monkopedia.kodemirror.state.languageData

        val changeFilter =
            com.monkopedia.kodemirror.state.changeFilter

        val transactionFilter =
            com.monkopedia.kodemirror.state.transactionFilter

        val transactionExtender =
            com.monkopedia.kodemirror.state
                .transactionExtender
    }

    /**
     * The size (in columns) of a tab in the document.
     */
    val tabSize: Int get() = facet(EditorState.tabSize)

    /**
     * Get the proper line-break string for this state.
     */
    val lineBreak: String
        get() = facet(EditorState.lineSeparator) ?: "\n"

    /**
     * Returns true when the editor is configured to be read-only.
     */
    @Suppress("MemberNameEqualsClassName")
    val readOnly: Boolean
        get() = facet(
            com.monkopedia.kodemirror.state.readOnly
        )

    /** The line containing the primary cursor. */
    val currentLine: Line get() = doc.lineAt(selection.main.head)

    /** The text currently selected by the primary selection, or empty string for cursors. */
    val selectedText: String
        get() {
            val main = selection.main
            return if (main.empty) "" else doc.sliceString(main.from, main.to)
        }

    /** The character position of the primary cursor. */
    val cursorPosition: Int get() = selection.main.head
}

data class ChangeByRangeResult(
    val range: SelectionRange,
    val changes: ChangeSpec? = null,
    val effects: List<StateEffect<*>>? = null
)

/**
 * Property delegate for reading a [StateField] value from [EditorState].
 *
 * ```kotlin
 * val counterField = StateField.define<Int>(...)
 *
 * // Instead of: state.field(counterField)
 * // Write:      state.counter
 * val EditorState.counter by counterField
 * ```
 */
operator fun <T> StateField<T>.getValue(
    thisRef: EditorState,
    property: kotlin.reflect.KProperty<*>
): T = thisRef.field(this)
