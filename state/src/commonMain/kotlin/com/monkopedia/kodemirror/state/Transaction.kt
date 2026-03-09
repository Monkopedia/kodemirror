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

/**
 * Annotations are tagged values that are used to add metadata to
 * transactions in an extensible way.
 */
class Annotation<T> internal constructor(
    /** The annotation type. */
    val type: AnnotationType<T>,
    /** The value of this annotation. */
    val value: T
) {
    companion object {
        /** Define a new type of annotation. */
        fun <T> define(): AnnotationType<T> = AnnotationType()
    }
}

/**
 * Marker that identifies a type of [Annotation].
 */
class AnnotationType<T> internal constructor() {
    /** Create an instance of this annotation. */
    fun of(value: T): Annotation<T> = Annotation(this, value)
}

/**
 * Representation of a type of state effect.
 */
class StateEffectType<Value> internal constructor(
    internal val mapFn: (Value, ChangeDesc) -> Value?
) {
    /** Create a [state effect][StateEffect] instance of this type. */
    fun of(value: Value): StateEffect<Value> = StateEffect(this, value)
}

/**
 * State effects can be used to represent additional effects
 * associated with a [transaction][Transaction.effects].
 */
class StateEffect<Value> internal constructor(
    internal val type: StateEffectType<Value>,
    /** The value of this effect. */
    val value: Value
) {
    /**
     * Map this effect through a position mapping. Will return
     * null when that ends up deleting the effect.
     */
    fun map(mapping: ChangeDesc): StateEffect<Value>? {
        val mapped = type.mapFn(value, mapping) ?: return null
        return if (mapped === value) {
            this
        } else {
            StateEffect(type, mapped)
        }
    }

    @Suppress("UNCHECKED_CAST")
    fun <T> asType(type: StateEffectType<T>): StateEffect<T>? =
        if (this.type === type) this as StateEffect<T> else null

    companion object {
        /**
         * Define a new effect type.
         */
        fun <Value> define(map: ((Value, ChangeDesc) -> Value?)? = null): StateEffectType<Value> {
            @Suppress("UNCHECKED_CAST")
            return StateEffectType(
                map ?: { v, _ -> v }
            )
        }

        /** Map an array of effects through a change set. */
        fun mapEffects(effects: List<StateEffect<*>>, mapping: ChangeDesc): List<StateEffect<*>> {
            if (effects.isEmpty()) return effects
            val result = mutableListOf<StateEffect<*>>()
            for (effect in effects) {
                val mapped = effect.map(mapping)
                if (mapped != null) result.add(mapped)
            }
            return result
        }

        /**
         * This effect can be used to reconfigure the root
         * extensions of the editor.
         */
        val reconfigure: StateEffectType<Extension> =
            define()

        /**
         * Append extensions to the top-level configuration of
         * the editor.
         */
        val appendConfig: StateEffectType<Extension> =
            define()
    }
}

/**
 * Describes a [Transaction] when calling
 * [EditorState.update].
 */
data class TransactionSpec(
    /** The changes to the document made by this transaction. */
    val changes: ChangeSpec? = null,
    /**
     * When set, this transaction explicitly updates the
     * selection.
     */
    val selection: SelectionSpec? = null,
    /** Attach [state effects][StateEffect] to this transaction. */
    val effects: List<StateEffect<*>>? = null,
    /** Set [annotations][Annotation] for this transaction. */
    val annotations: List<Annotation<*>>? = null,
    /** Shorthand for Transaction.userEvent annotation. */
    val userEvent: String? = null,
    /**
     * When set to true, the transaction is marked as needing to
     * scroll the current selection into view.
     */
    val scrollIntoView: Boolean = false,
    /**
     * By default, transactions can be modified by filters.
     * You can set this to false to disable that.
     */
    val filter: Boolean? = null,
    /**
     * Normally, positions in `changes` refer to the initial
     * document. When `sequential` is true, positions refer to
     * the document created by the specs before it instead.
     */
    val sequential: Boolean = false
)

sealed interface SelectionSpec {
    data class EditorSelectionSpec(
        val selection: EditorSelection
    ) : SelectionSpec

    data class CursorSpec(
        val anchor: DocPos,
        val head: DocPos? = null
    ) : SelectionSpec
}

/** Create a [SelectionSpec] placing the cursor at [pos]. */
fun DocPos.asCursor(): SelectionSpec = SelectionSpec.CursorSpec(this)

/** Convert this [EditorSelection] to a [SelectionSpec]. */
fun EditorSelection.asSpec(): SelectionSpec = SelectionSpec.EditorSelectionSpec(this)

/**
 * Changes to the editor state are grouped into transactions.
 */
class Transaction private constructor(
    /** The state from which the transaction starts. */
    val startState: EditorState,
    /** The document changes made by this transaction. */
    val changes: ChangeSet,
    /** The selection set by this transaction, or null. */
    val selection: EditorSelection?,
    /** The effects added to the transaction. */
    val effects: List<StateEffect<*>>,
    /** The annotations on this transaction. */
    val annotations: List<Annotation<*>>,
    /** Whether the selection should be scrolled into view. */
    val scrollIntoView: Boolean
) {
    @Suppress("ktlint:standard:property-naming")
    internal var _doc: Text? = null

    @Suppress("ktlint:standard:property-naming")
    internal var _state: EditorState? = null

    init {
        if (selection != null) {
            checkSelection(selection, changes.newLength)
        }
    }

    /**
     * The new document produced by the transaction.
     */
    val newDoc: Text
        get() = _doc ?: changes.apply(startState.doc).also {
            _doc = it
        }

    /**
     * The new selection produced by the transaction.
     */
    val newSelection: EditorSelection
        get() = selection
            ?: startState.selection.map(changes)

    /**
     * The new state created by the transaction.
     */
    val state: EditorState
        get() {
            if (_state == null) {
                startState.applyTransaction(this)
            }
            return _state!!
        }

    /**
     * Get the value of the given annotation type, if any.
     */
    fun <T> annotation(type: AnnotationType<T>): T? {
        for (ann in annotations) {
            @Suppress("UNCHECKED_CAST")
            if (ann.type === type) return ann.value as T
        }
        return null
    }

    /** Indicates whether the transaction changed the document. */
    val docChanged: Boolean get() = !changes.empty

    /**
     * Indicates whether this transaction reconfigures the state.
     */
    val reconfigured: Boolean
        get() = startState.config !== state.config

    /**
     * Returns true if the transaction has a user event annotation
     * that is equal to or more specific than [event].
     */
    fun isUserEvent(event: String): Boolean {
        val e = annotation(userEvent) ?: return false
        return e == event || (
            e.length > event.length &&
                e.substring(0, event.length) == event &&
                e[event.length] == '.'
            )
    }

    companion object {
        /**
         * Annotation used to store transaction timestamps.
         */
        val time: AnnotationType<Long> =
            Annotation.define()

        /**
         * Annotation used to associate a transaction with a user
         * interface event.
         */
        val userEvent: AnnotationType<String> =
            Annotation.define()

        /**
         * Annotation indicating whether a transaction should be
         * added to the undo history or not.
         */
        val addToHistory: AnnotationType<Boolean> =
            Annotation.define()

        /**
         * Annotation indicating that a transaction represents a
         * change made by some other actor, not the user.
         */
        val remote: AnnotationType<Boolean> =
            Annotation.define()

        internal fun create(
            startState: EditorState,
            changes: ChangeSet,
            selection: EditorSelection?,
            effects: List<StateEffect<*>>,
            annotations: List<Annotation<*>>,
            scrollIntoView: Boolean
        ): Transaction {
            var anns = annotations
            if (anns.none { it.type === time }) {
                anns = anns + time.of(currentTimeMillis())
            }
            return Transaction(
                startState,
                changes,
                selection,
                effects,
                anns,
                scrollIntoView
            )
        }
    }
}

internal fun currentTimeMillis(): Long = kotlinx.datetime.Clock.System.now()
    .toEpochMilliseconds()

private fun joinRanges(a: IntArray, b: IntArray): IntArray {
    val result = mutableListOf<Int>()
    var iA = 0
    var iB = 0
    while (true) {
        val from: Int
        val to: Int
        if (iA < a.size &&
            (iB == b.size || b[iB] >= a[iA])
        ) {
            from = a[iA++]
            to = a[iA++]
        } else if (iB < b.size) {
            from = b[iB++]
            to = b[iB++]
        } else {
            return result.toIntArray()
        }
        if (result.isEmpty() ||
            result[result.size - 1] < from
        ) {
            result.add(from)
            result.add(to)
        } else if (result[result.size - 1] < to) {
            result[result.size - 1] = to
        }
    }
}

private data class ResolvedSpec(
    val changes: ChangeSet,
    val selection: EditorSelection?,
    val effects: List<StateEffect<*>>,
    val annotations: List<Annotation<*>>,
    val scrollIntoView: Boolean
)

private fun mergeTransaction(a: ResolvedSpec, b: ResolvedSpec, sequential: Boolean): ResolvedSpec {
    val mapForA: ChangeDesc
    val mapForB: ChangeDesc
    val changes: ChangeSet
    if (sequential) {
        mapForA = b.changes
        mapForB = ChangeSet.empty(b.changes.length)
        changes = a.changes.compose(b.changes)
    } else {
        mapForA = b.changes.map(a.changes)
        mapForB = a.changes.mapDesc(b.changes, true)
        changes = a.changes.compose(mapForA as ChangeSet)
    }
    return ResolvedSpec(
        changes = changes,
        selection = if (b.selection != null) {
            b.selection.map(mapForB)
        } else {
            a.selection?.map(mapForA)
        },
        effects = StateEffect.mapEffects(a.effects, mapForA) +
            StateEffect.mapEffects(b.effects, mapForB),
        annotations = if (a.annotations.isNotEmpty()) {
            a.annotations + b.annotations
        } else {
            b.annotations
        },
        scrollIntoView = a.scrollIntoView || b.scrollIntoView
    )
}

private fun resolveTransactionInner(
    state: EditorState,
    spec: TransactionSpec,
    docSize: Int
): ResolvedSpec {
    val sel = spec.selection
    var annotations = spec.annotations ?: emptyList()
    if (spec.userEvent != null) {
        annotations = annotations +
            Transaction.userEvent.of(spec.userEvent)
    }
    return ResolvedSpec(
        changes = when (val c = spec.changes) {
            is ChangeSpec.Set -> c.changeSet
            null -> ChangeSet.of(
                ChangeSpec.Multi(emptyList()),
                docSize,
                state.facet(EditorState.lineSeparator)
            )
            else -> ChangeSet.of(
                c,
                docSize,
                state.facet(EditorState.lineSeparator)
            )
        },
        selection = when (sel) {
            is SelectionSpec.EditorSelectionSpec -> sel.selection
            is SelectionSpec.CursorSpec ->
                EditorSelection.single(sel.anchor, sel.head ?: sel.anchor)
            null -> null
        },
        effects = spec.effects ?: emptyList(),
        annotations = annotations,
        scrollIntoView = spec.scrollIntoView
    )
}

internal fun resolveTransaction(
    state: EditorState,
    specs: List<TransactionSpec>,
    filter: Boolean
): Transaction {
    var s = resolveTransactionInner(
        state,
        if (specs.isNotEmpty()) specs[0] else TransactionSpec(),
        state.doc.length
    )
    var currentFilter = filter
    if (specs.isNotEmpty() && specs[0].filter == false) {
        currentFilter = false
    }
    for (i in 1 until specs.size) {
        if (specs[i].filter == false) currentFilter = false
        val seq = specs[i].sequential
        s = mergeTransaction(
            s,
            resolveTransactionInner(
                state,
                specs[i],
                if (seq) {
                    s.changes.newLength
                } else {
                    state.doc.length
                }
            ),
            seq
        )
    }
    val tr = Transaction.create(
        state,
        s.changes,
        s.selection,
        s.effects,
        s.annotations,
        s.scrollIntoView
    )
    return extendTransaction(
        if (currentFilter) filterTransaction(tr) else tr
    )
}

private fun filterTransaction(tr: Transaction): Transaction {
    val state = tr.startState
    var result: ChangeFilterResult = ChangeFilterResult.Accept
    for (filter in state.facet(changeFilter)) {
        val value = filter(tr)
        when (value) {
            is ChangeFilterResult.Reject -> {
                result = ChangeFilterResult.Reject
                break
            }
            is ChangeFilterResult.Ranges -> {
                result = when (val r = result) {
                    is ChangeFilterResult.Accept -> value
                    is ChangeFilterResult.Ranges ->
                        ChangeFilterResult.Ranges(joinRanges(r.ranges, value.ranges))
                    is ChangeFilterResult.Reject -> result
                }
            }
            is ChangeFilterResult.Accept -> {}
        }
    }
    var currentTr = tr
    if (result !is ChangeFilterResult.Accept) {
        val changes: ChangeSet
        val back: ChangeDesc
        when (result) {
            is ChangeFilterResult.Reject -> {
                back = tr.changes.invertedDesc
                changes = ChangeSet.empty(state.doc.length)
            }
            is ChangeFilterResult.Ranges -> {
                val filtered = tr.changes.filter(result.ranges)
                changes = filtered.changes
                back = filtered.filtered
                    .mapDesc(filtered.changes).invertedDesc
            }
            is ChangeFilterResult.Accept -> error("unreachable")
        }
        currentTr = Transaction.create(
            state, changes,
            tr.selection?.map(back),
            StateEffect.mapEffects(tr.effects, back),
            tr.annotations, tr.scrollIntoView
        )
    }

    val filters = state.facet(transactionFilter)
    for (i in filters.indices.reversed()) {
        when (val filtered = filters[i](currentTr)) {
            is TransactionFilterResult.Filtered -> {
                currentTr = filtered.transaction
            }
            is TransactionFilterResult.Specs -> {
                currentTr = resolveTransaction(
                    state,
                    filtered.specs,
                    false
                )
            }
        }
    }
    return currentTr
}

private fun extendTransaction(tr: Transaction): Transaction {
    val state = tr.startState
    val extenders = state.facet(transactionExtender)
    var spec: ResolvedSpec = ResolvedSpec(
        tr.changes,
        tr.selection,
        tr.effects,
        tr.annotations,
        tr.scrollIntoView
    )
    var changed = false
    for (i in extenders.indices.reversed()) {
        val extension = extenders[i](tr) ?: continue
        if (extension.effects != null ||
            extension.annotations != null
        ) {
            spec = mergeTransaction(
                spec,
                resolveTransactionInner(
                    state,
                    TransactionSpec(
                        effects = extension.effects,
                        annotations = extension.annotations
                    ),
                    tr.changes.newLength
                ),
                true
            )
            changed = true
        }
    }
    return if (!changed) {
        tr
    } else {
        Transaction.create(
            state,
            tr.changes,
            tr.selection,
            spec.effects,
            spec.annotations,
            spec.scrollIntoView
        )
    }
}

/**
 * Result type for transaction extender facet.
 */
data class TransactionExtenderResult(
    val effects: List<StateEffect<*>>? = null,
    val annotations: List<Annotation<*>>? = null
)

internal fun <T> asArray(value: T?): List<T> {
    if (value == null) return emptyList()
    @Suppress("UNCHECKED_CAST")
    if (value is List<*>) return value as List<T>
    return listOf(value)
}
