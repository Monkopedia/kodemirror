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

import com.monkopedia.kodemirror.state.EditorState.Companion.changeFilter
import com.monkopedia.kodemirror.state.EditorState.Companion.lineSeparator
import com.monkopedia.kodemirror.state.EditorState.Companion.transactionExtender
import com.monkopedia.kodemirror.state.EditorState.Companion.transactionFilter
import com.monkopedia.kodemirror.state.Either.Companion.asLeft
import com.monkopedia.kodemirror.state.Either.Companion.asRight
import com.monkopedia.kodemirror.state.SingleOrList.Companion.list
import com.monkopedia.kodemirror.state.SingleOrList.Companion.orEmpty
import com.monkopedia.kodemirror.state.SingleOrList.Companion.plus
import kotlinx.datetime.Clock

// import {ChangeSet, ChangeDesc, ChangeSpec} from "./change"
// import {EditorState} from "./state"
// import {EditorSelection, checkSelection} from "./selection"
// import {changeFilter, transactionFilter, transactionExtender, lineSeparator} from "./extension"
// import {Extension} from "./facet"
// import {Text} from "./text"

// / Annotations are tagged values that are used to add metadata to
// / transactions in an extensible way. They should be used to model
// / things that effect the entire transaction (such as its [time
// / stamp](#state.Transaction^time) or information about its
// / [origin](#state.Transaction^userEvent)). For effects that happen
// / _alongside_ the other changes made by the transaction, [state
// / effects](#state.StateEffect) are more appropriate.
class Annotation<T> internal constructor(
    // / The annotation type.
    val type: AnnotationType<T>,
    // / The value of this annotation.
    val value: T
) {

    // This is just to get less sloppy typing (where StateEffect is a subtype of Annotation)
    // @ts-ignore
    private val _isAnnotation = true

    companion object {
        // / Define a new type of annotation.
        fun <T> define(): AnnotationType<T> {
            return AnnotationType<T>()
        }
    }
}

// / Marker that identifies a type of [annotation](#state.Annotation).
class AnnotationType<T> {
    // / Create an instance of this annotation.
    fun of(value: T): Annotation<T> {
        return Annotation(this, value)
    }
}

data class StateEffectSpec<Value>(
    // / Provides a way to map an effect like this through a position
    // / mapping. When not given, the effects will simply not be mapped.
    // / When the function returns `undefined`, that means the mapping
    // / deletes the effect.
    val map: ((value: Value, mapping: ChangeDesc) -> Value)? = null
)

// / Representation of a type of state effect. Defined with
// / [`StateEffect.define`](#state.StateEffect^define).
class StateEffectType<Value> internal constructor(
    // The `any` types in these function types are there to work
    // around TypeScript issue #37631, where the type guard on
    // `StateEffect.is` mysteriously stops working when these properly
    // have type `Value`.
    // / @internal
    val map: ((value: Value, mapping: ChangeDesc) -> Value?)
) {

    // / Create a [state effect](#state.StateEffect) instance of this
    // / type.
    fun of(value: Value): StateEffect<Value> {
        return StateEffect(this, value)
    }
}

// / State effects can be used to represent additional effects
// / associated with a [transaction](#state.Transaction.effects). They
// / are often useful to model changes to custom [state
// / fields](#state.StateField), when those changes aren't implicit in
// / document or selection changes.
class StateEffect<Value> internal constructor(
    // / @internal
    val type: StateEffectType<Value>,
    // / The value of this effect.
    val value: Value
) {

    // / Map this effect through a position mapping. Will return
    // / `undefined` when that ends up deleting the effect.
    fun map(mapping: ChangeDesc): StateEffect<Value>? {
        return when (val mapped = this.type.map?.invoke(this.value, mapping)) {
            null -> null
            this.value -> this
            else -> StateEffect(this.type, mapped as Value)
        }
    }

    // / Tells you whether this effect object is of a given
    // / [type](#state.StateEffectType).
    fun <T> isOf(type: StateEffectType<T>): Boolean {
        return this.type == type
    }

    companion object {
        // / Define a new effect type. The type parameter indicates the type
        // / of values that his effect holds. It should be a type that
        // / doesn't include `undefined`, since that is used in
        // / [mapping](#state.StateEffect.map) to indicate that an effect is
        // / removed.
        fun <Value> define(spec: StateEffectSpec<Value>): StateEffectType<Value> {
            return StateEffectType(spec.map ?: { a: Value, _: ChangeDesc -> a })
        }

        fun <T> define(): StateEffectType<T?> {
            return StateEffectType { v, _ -> v }
        }

        // / Map an array of effects through a change set.
        fun mapEffects(effects: List<StateEffect<*>>, mapping: ChangeDesc): List<StateEffect<*>> {
            if (effects.isEmpty()) return effects
            return effects.mapNotNull { it.map(mapping) }
        }

        // / This effect can be used to reconfigure the root extensions of
        // / the editor. Doing this will discard any extensions
        // / [appended](#state.StateEffect^appendConfig), but does not reset
        // / the content of [reconfigured](#state.Compartment.reconfigure)
        // / compartments.
        val reconfigure = StateEffect.define<Extension>()

        // / Append extensions to the top-level configuration of the editor.
        val appendConfig = StateEffect.define<Extension>()
    }
}

sealed interface Transactable {
    fun isNotEmpty(): Boolean {
        return (changes ?: selection ?: effects ?: annotations ?: userEvent) != null ||
            scrollIntoView ||
            !filter ||
            sequential
    }

    // / The changes to the document made by this transaction.
    val changes: ChangeSpec?
        get() = null

    // / When set, this transaction explicitly updates the selection.
    // / Offsets in this selection should refer to the document as it is
    // / _after_ the transaction.
    val selection: Selection? get() = null

    // / Attach [state effects](#state.StateEffect) to this transaction.
    // / Again, when they contain positions and this same spec makes
    // / changes, those positions should refer to positions in the
    // / updated document.
    val effects: SingleOrList<out StateEffect<*>>? get() = null

    // / Set [annotations](#state.Annotation) for this transaction.
    val annotations: SingleOrList<out Annotation<*>>? get() = null

    // / Shorthand for `annotations:` [`Transaction.userEvent`](#state.Transaction^userEvent)`.of(...)`.
    val userEvent: String? get() = null

    // / When set to `true`, the transaction is marked as needing to
    // / scroll the current selection into view.
    val scrollIntoView: Boolean get() = false

    // / By default, transactions can be modified by [change
    // / filters](#state.EditorState^changeFilter) and [transaction
    // / filters](#state.EditorState^transactionFilter). You can set this
    // / to `false` to disable that. This can be necessary for
    // / transactions that, for example, include annotations that must be
    // / kept consistent with their changes.
    val filter: Boolean get() = true

    // / Normally, when multiple specs are combined (for example by
    // / [`EditorState.update`](#state.EditorState.update)), the
    // / positions in `changes` are taken to refer to the document
    // / positions in the initial document. When a spec has `sequental`
    // / set to true, its positions will be taken to refer to the
    // / document created by the specs before it instead.
    val sequential: Boolean get() = false
}

// Picks:
// - "effects", "annotations"
//
// / Describes a [transaction](#state.Transaction) when calling the
// / [`EditorState.update`](#state.EditorState.update) method.
data class TransactionSpec(
    // / The changes to the document made by this transaction.
    override val changes: ChangeSpec? = null,
    // / When set, this transaction explicitly updates the selection.
    // / Offsets in this selection should refer to the document as it is
    // / _after_ the transaction.
    override val selection: Selection? = null,
    // / Attach [state effects](#state.StateEffect) to this transaction.
    // / Again, when they contain positions and this same spec makes
    // / changes, those positions should refer to positions in the
    // / updated document.
    override val effects: SingleOrList<StateEffect<*>>? = null,
    // / Set [annotations](#state.Annotation) for this transaction.
    override val annotations: SingleOrList<Annotation<*>>? = null,
    // / Shorthand for `annotations:` [`Transaction.userEvent`](#state.Transaction^userEvent)`.of(...)`.
    override val userEvent: String? = null,
    // / When set to `true`, the transaction is marked as needing to
    // / scroll the current selection into view.
    override val scrollIntoView: Boolean = false,
    // / By default, transactions can be modified by [change
    // / filters](#state.EditorState^changeFilter) and [transaction
    // / filters](#state.EditorState^transactionFilter). You can set this
    // / to `false` to disable that. This can be necessary for
    // / transactions that, for example, include annotations that must be
    // / kept consistent with their changes.
    override val filter: Boolean = true,
    // / Normally, when multiple specs are combined (for example by
    // / [`EditorState.update`](#state.EditorState.update)), the
    // / positions in `changes` are taken to refer to the document
    // / positions in the initial document. When a spec has `sequental`
    // / set to true, its positions will be taken to refer to the
    // / document created by the specs before it instead.
    override val sequential: Boolean = false
) : Transactable

// / Changes to the editor state are grouped into transactions.
// / Typically, a user action creates a single transaction, which may
// / contain any number of document changes, may change the selection,
// / or have other effects. Create a transaction by calling
// / [`EditorState.update`](#state.EditorState.update), or immediately
// / dispatch one by calling
// / [`EditorView.dispatch`](#view.EditorView.dispatch).
data class Transaction private constructor(
    // / The state from which the transaction starts.
    val startState: EditorState,
    // / The document changes made by this transaction.
    override val changes: ChangeSet,
    // / The selection set by this transaction, or undefined if it
    // / doesn't explicitly set a selection.
    override val selection: EditorSelection?,
// / The effects added to the transaction.
    override val effects: SingleOrList<out StateEffect<*>>,
// / @internal
    override var annotations: SingleOrList<out Annotation<*>>,
// / Whether the selection should be scrolled into view after this
// / transaction is dispatched.
    override val scrollIntoView: Boolean
) : ResolvedSpec {
    // / @internal
    internal var _doc: Text? = null

    // / @internal
    internal var _state: EditorState? = null

    init {
        if (selection != null) checkSelection(selection, changes.newLength)
        if (!annotations.list.any { a: Annotation<*> -> a.type == time }) {
            this.annotations += time.of(Clock.System.now().toEpochMilliseconds())
        }
    }

    // / The new document produced by the transaction. Contrary to
    // / [`.state`](#state.Transaction.state)`.doc`, accessing this won't
    // / force the entire new state to be computed right away, so it is
    // / recommended that [transaction
    // / filters](#state.EditorState^transactionFilter) use this getter
    // / when they need to look at the new document.
    val newDoc: Text
        get() {
            return this._doc ?: this.changes.apply(this.startState.doc).also {
                this._doc = it
            }
        }

    // / The new selection produced by the transaction. If
    // / [`this.selection`](#state.Transaction.selection) is undefined,
    // / this will [map](#state.EditorSelection.map) the start state's
    // / current selection through the changes made by the transaction.
    val newSelection: Selection
        get() {
            return this.selection ?: this.startState.selection.map(this.changes)
        }

    // / The new state created by the transaction. Computed on demand
    // / (but retained for subsequent access), so it is recommended not to
    // / access it in [transaction
    // / filters](#state.EditorState^transactionFilter) when possible.
    val state: EditorState
        get() {
            if (this._state == null) this.startState.applyTransaction(this)
            return this._state!!
        }

    // / Get the value of the given annotation type, if any.
    fun <T> annotation(type: AnnotationType<T>): T? {
        @Suppress("UNCHECKED_CAST")
        for (ann in this.annotations.list) if (ann.type == type) return ann.value as T
        return null
    }

    // / Indicates whether the transaction changed the document.
    val docChanged: Boolean
        get() {
            return !this.changes.isEmpty
        }

    // / Indicates whether this transaction reconfigures the state
    // / (through a [configuration compartment](#state.Compartment) or
    // / with a top-level configuration
    // / [effect](#state.StateEffect^reconfigure).
    val reconfigured: Boolean
        get() {
            return this.startState.config != this.state.config
        }

    // / Returns true if the transaction has a [user
    // / event](#state.Transaction^userEvent) annotation that is equal to
    // / or more specific than `event`. For example, if the transaction
    // / has `"select.pointer"` as user event, `"select"` and
    // / `"select.pointer"` will match it.
    fun isUserEvent(event: String): Boolean {
        val e = this.annotation(Transaction.Companion.userEvent)
        return (
            e != null &&
                (
                    e == event ||
                        e.length > event.length &&
                        e.substring(0, event.length) == event &&
                        e[event.length] == '.'
                    )
            )
    }

    companion object {
        // / @internal
        internal fun create(
            startState: EditorState,
            changes: ChangeSet,
            selection: EditorSelection?,
            effects: SingleOrList<out StateEffect<*>>,
            annotations: SingleOrList<out Annotation<*>>,
            scrollIntoView: Boolean
        ): Transaction {
            return Transaction(
                startState,
                changes,
                selection,
                effects,
                annotations,
                scrollIntoView
            )
        }

        // / Annotation used to store transaction timestamps. Automatically
        // / added to every transaction, holding `Date.now()`.
        val time = Annotation.define<Long>()

        // / Annotation used to associate a transaction with a user interface
        // / event. Holds a string identifying the event, using a
        // / dot-separated format to support attaching more specific
        // / information. The events used by the core libraries are:
        // /
        // /  - `"input"` when content is entered
        // /    - `"input.type"` for typed input
        // /      - `"input.type.compose"` for composition
        // /    - `"input.paste"` for pasted input
        // /    - `"input.drop"` when adding content with drag-and-drop
        // /    - `"input.complete"` when autocompleting
        // /  - `"delete"` when the user deletes content
        // /    - `"delete.selection"` when deleting the selection
        // /    - `"delete.forward"` when deleting forward from the selection
        // /    - `"delete.backward"` when deleting backward from the selection
        // /    - `"delete.cut"` when cutting to the clipboard
        // /  - `"move"` when content is moved
        // /    - `"move.drop"` when content is moved within the editor through drag-and-drop
        // /  - `"select"` when explicitly changing the selection
        // /    - `"select.pointer"` when selecting with a mouse or other pointing device
        // /  - `"undo"` and `"redo"` for history actions
        // /
        // / Use [`isUserEvent`](#state.Transaction.isUserEvent) to check
        // / whether the annotation matches a given event.
        val userEvent = Annotation.define<String>()

        // / Annotation indicating whether a transaction should be added to
        // / the undo history or not.
        val addToHistory = Annotation.define<Boolean>()

        // / Annotation indicating (when present and true) that a transaction
        // / represents a change made by some other actor, not the user. This
        // / is used, for example, to tag other people's changes in
        // / collaborative editing.
        val remote = Annotation.define<Boolean>()
    }
}

internal fun joinRanges(
    a: List<Pair<Int, Int>>,
    b: List<Pair<Int, Int>>
): MutableList<Pair<Int, Int>> {
    val result = mutableListOf<Pair<Int, Int>>()
    var iA = 0
    var iB = 0
    while (true) {
        val (from, to) = if (iA < a.size && (iB == b.size || b[iB].first >= a[iA].first)) {
            a[iA++]
        } else if (iB < b.size) {
            b[iB++]
        } else {
            return result
        }
        if (result.isEmpty() || result[result.size - 1].second < from) {
            result.add(from to to)
        } else if (result[result.size - 1].second < to) {
            result[result.size - 1] =
                result[result.size - 1].first to to
        }
    }
}

interface ResolvedSpec : Transactable {
    override val changes: ChangeSet
    override val selection: EditorSelection?
    override val effects: SingleOrList<out StateEffect<*>>
    override val annotations: SingleOrList<out Annotation<*>>
    override val scrollIntoView: Boolean
}

data class ResolvedSpecData(
    override val changes: ChangeSet,
    override val selection: EditorSelection?,
    override val effects: SingleOrList<out StateEffect<*>>,
    override val annotations: SingleOrList<out Annotation<*>>,
    override val scrollIntoView: Boolean
) : ResolvedSpec

internal fun mergeTransaction(a: ResolvedSpec, b: ResolvedSpec, sequential: Boolean): ResolvedSpec {
    val mapForA =
        if (sequential) {
            b.changes
        } else {
            b.changes.map(a.changes)
        }
    val mapForB: ChangeDesc =
        if (sequential) {
            ChangeSet.empty(b.changes.length)
        } else {
            a.changes.mapDesc(b.changes, true)
        }
    val changes =
        if (sequential) {
            a.changes.compose(b.changes)
        } else {
            a.changes.compose(mapForA)
        }
    return ResolvedSpecData(
        changes,
        selection = b.selection?.map(mapForB) ?: a.selection?.map(mapForA),
        effects = (
            StateEffect.mapEffects(a.effects.list, mapForA) +
                StateEffect.mapEffects(b.effects.list, mapForB)
            ).list,
        annotations = a.annotations + b.annotations,
        scrollIntoView = a.scrollIntoView || b.scrollIntoView
    )
}

internal fun resolveTransactionInner(
    state: EditorState,
    spec: Transactable,
    docSize: Int
): ResolvedSpec {
    val sel = spec.selection
    var annotations = spec.annotations.orEmpty()
    if (spec.userEvent != null) {
        annotations += Transaction.userEvent.of(spec.userEvent!!)
    }
    return ResolvedSpecData(
        changes = when (val changes = spec.changes) {
            is ChangeSet -> changes
            else -> ChangeSet.of(changes ?: ChangeSpec.empty, docSize, state.facet(lineSeparator))
        },
        selection = when (sel) {
            is Selection.Data -> EditorSelection.single(sel.anchor, sel.head ?: sel.anchor)
            is EditorSelection -> sel
            null -> null
        },
        effects = spec.effects ?: SingleOrList.empty,
        annotations = annotations,
        scrollIntoView = spec.scrollIntoView
    )
}

fun resolveTransaction(
    state: EditorState,
    specs: List<Transactable>,
    filter: Boolean
): Transaction {
    var s =
        resolveTransactionInner(state, specs.getOrNull(0) ?: TransactionSpec(), state.doc.length)
    var useFilter =
        if (specs.getOrNull(0)?.filter == false) {
            false
        } else {
            filter
        }
    for (spec in specs.drop(1)) {
        if (!spec.filter) useFilter = false
        val seq = spec.sequential
        s = mergeTransaction(
            s,
            resolveTransactionInner(
                state,
                spec,
                if (seq) s.changes.newLength else state.doc.length
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
    return extendTransaction(if (useFilter) filterTransaction(tr) else tr)
}

// Finish a transaction by applying filters if necessary.
internal fun filterTransaction(tr: Transaction): Transaction {
    val state = tr.startState

    // Change filters
    var result: Either<out Boolean, out List<Pair<Int, Int>>> = true.asLeft
    for (filter in state.facet(changeFilter)) {
        val value = filter(tr)
        if (value.a == false) {
            result = false.asLeft
            break
        }
        value.b?.let { list ->
            result = if (result.a == true) value else joinRanges(result.b.orEmpty(), list).asRight
        }
    }
    val filteredTransaction = result.fold(
        {
            if (this) {
                tr
            } else {
                val back = tr.changes.invertedDesc
                val changes = ChangeSet.empty(state.doc.length)
                Transaction.create(
                    state,
                    changes,
                    tr.selection?.map(back),
                    StateEffect.mapEffects(tr.effects.list, back).list,
                    tr.annotations,
                    tr.scrollIntoView
                )
            }
        },
        {
            val (changes, filtered) = tr.changes.filter(this)
            val back = filtered.mapDesc(changes).invertedDesc
            Transaction.create(
                state,
                changes,
                tr.selection?.map(back),
                StateEffect.mapEffects(tr.effects.list, back).list,
                tr.annotations,
                tr.scrollIntoView
            )
        }
    )

    // Transaction filters
    val filters = state.facet(transactionFilter)
    return filters.fold(filteredTransaction) { transaction, filter ->
        val filtered = filter(transaction)
        (filtered.singleOrNull as? Transaction) ?: resolveTransaction(state, filtered.list, false)
    }
}

internal fun extendTransaction(tr: Transaction): Transaction {
    val state = tr.startState
    val extenders = state.facet(transactionExtender)
    var spec: ResolvedSpec = tr
    extenders.reversed().forEach { extender ->
        val extension = extender(tr)
        if (extension != null && extension.isNotEmpty()) {
            spec = mergeTransaction(
                spec,
                resolveTransactionInner(state, extension, tr.changes.newLength),
                true
            )
        }
    }
    return if (spec == tr) {
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
