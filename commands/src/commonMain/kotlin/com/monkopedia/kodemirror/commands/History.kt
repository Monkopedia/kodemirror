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
package com.monkopedia.kodemirror.commands

import com.monkopedia.kodemirror.state.Annotation
import com.monkopedia.kodemirror.state.AnnotationType
import com.monkopedia.kodemirror.state.ChangeDesc
import com.monkopedia.kodemirror.state.ChangeSet
import com.monkopedia.kodemirror.state.ChangeSpec
import com.monkopedia.kodemirror.state.EditorSelection
import com.monkopedia.kodemirror.state.EditorState
import com.monkopedia.kodemirror.state.Extension
import com.monkopedia.kodemirror.state.ExtensionList
import com.monkopedia.kodemirror.state.SelectionSpec
import com.monkopedia.kodemirror.state.StateEffect
import com.monkopedia.kodemirror.state.StateField
import com.monkopedia.kodemirror.state.StateFieldSpec
import com.monkopedia.kodemirror.state.Transaction
import com.monkopedia.kodemirror.state.TransactionSpec
import com.monkopedia.kodemirror.state.invertedEffects
import com.monkopedia.kodemirror.view.EditorView
import com.monkopedia.kodemirror.view.KeyBinding
import com.monkopedia.kodemirror.view.keymapOf

private const val DEFAULT_GROUP_DELAY = 500L
private const val MAX_SELECTIONS_PER_EVENT = 200

private val joinableUserEvent = Regex("^(input\\.type|delete)($|\\.)")

/**
 * This annotation can be used on transactions to prevent them from
 * being combined with other transactions in the undo history.
 * Given `"before"`, it'll prevent grouping with previous events.
 * With `"after"`, it prevents grouping with the next event. `"full"`
 * does both.
 */
val isolateHistory: AnnotationType<String> = Annotation.define()

internal enum class BranchName { Done, Undone }

private data class FromHistoryInfo(
    val side: BranchName,
    val rest: List<HistoryEvent>,
    val selection: EditorSelection
)

private val fromHistory: AnnotationType<FromHistoryInfo> = Annotation.define()

internal class HistoryEvent(
    val changes: ChangeSet?,
    val effects: List<StateEffect<*>>,
    val mapped: ChangeDesc?,
    val startSelection: EditorSelection?,
    val selectionsAfter: List<EditorSelection>
) {
    fun setSelAfter(after: List<EditorSelection>): HistoryEvent =
        HistoryEvent(changes, effects, mapped, startSelection, after)

    companion object {
        fun fromTransaction(tr: Transaction, selection: EditorSelection? = null): HistoryEvent? {
            val effects = tr.startState
                .facet(invertedEffects)
                .flatMap { fn -> fn(tr) }
            if (effects.isEmpty() && tr.changes.empty) return null
            return HistoryEvent(
                tr.changes.invert(tr.startState.doc),
                effects,
                null,
                selection ?: tr.startState.selection,
                emptyList()
            )
        }

        fun selection(selections: List<EditorSelection>): HistoryEvent =
            HistoryEvent(null, emptyList(), null, null, selections)
    }
}

private fun mapEvent(
    event: HistoryEvent,
    mapping: ChangeDesc,
    extraSelections: List<EditorSelection>
): HistoryEvent {
    val selections = conc(
        if (event.selectionsAfter.isNotEmpty()) {
            event.selectionsAfter.map { it.map(mapping) }
        } else {
            emptyList()
        },
        extraSelections
    )
    if (event.changes == null) return HistoryEvent.selection(selections)
    val mappedChanges = event.changes.map(mapping)
    val before = mapping.mapDesc(event.changes, true)
    val fullMapping =
        if (event.mapped != null) event.mapped.composeDesc(before) else before
    return HistoryEvent(
        mappedChanges,
        StateEffect.mapEffects(event.effects, mapping),
        fullMapping,
        event.startSelection!!.map(before),
        selections
    )
}

private fun addMappingToBranch(
    branch: List<HistoryEvent>,
    mapping: ChangeDesc
): List<HistoryEvent> {
    if (branch.isEmpty()) return branch
    var length = branch.size
    var currentMapping = mapping
    var selections: List<EditorSelection> = emptyList()
    while (length > 0) {
        val event = mapEvent(branch[length - 1], currentMapping, selections)
        if (event.changes != null && !event.changes.empty ||
            event.effects.isNotEmpty()
        ) {
            val result = branch.subList(0, length).toMutableList()
            result[length - 1] = event
            return result
        } else {
            currentMapping = event.mapped ?: currentMapping
            length--
            selections = event.selectionsAfter
        }
    }
    return if (selections.isNotEmpty()) {
        listOf(HistoryEvent.selection(selections))
    } else {
        emptyList()
    }
}

private fun isAdjacent(a: ChangeDesc, b: ChangeDesc): Boolean {
    val ranges = mutableListOf<Int>()
    var adjacent = false
    a.iterChangedRanges({ f, t, _, _ ->
        ranges.add(f)
        ranges.add(t)
    })
    b.iterChangedRanges({ _, _, f, t ->
        var i = 0
        while (i < ranges.size) {
            if (t >= ranges[i] && f <= ranges[i + 1]) adjacent = true
            i += 2
        }
    })
    return adjacent
}

private fun updateBranch(
    branch: List<HistoryEvent>,
    to: Int,
    maxLen: Int,
    newEvent: HistoryEvent
): List<HistoryEvent> {
    val start = if (to + 1 > maxLen + 20) to - maxLen - 1 else 0
    val newBranch = branch.subList(start, to).toMutableList()
    newBranch.add(newEvent)
    return newBranch
}

private fun addSelection(
    branch: List<HistoryEvent>,
    selection: EditorSelection
): List<HistoryEvent> {
    if (branch.isEmpty()) {
        return listOf(HistoryEvent.selection(listOf(selection)))
    } else {
        val lastEvent = branch[branch.size - 1]
        val startIdx = maxOf(
            0,
            lastEvent.selectionsAfter.size - MAX_SELECTIONS_PER_EVENT
        )
        val sels = lastEvent.selectionsAfter
            .subList(startIdx, lastEvent.selectionsAfter.size)
            .toMutableList()
        if (sels.isNotEmpty() && sels[sels.size - 1].eq(selection)) {
            return branch
        }
        sels.add(selection)
        return updateBranch(
            branch,
            branch.size - 1,
            1_000_000_000,
            lastEvent.setSelAfter(sels)
        )
    }
}

private fun popSelection(branch: List<HistoryEvent>): List<HistoryEvent> {
    val last = branch[branch.size - 1]
    val newBranch = branch.toMutableList()
    newBranch[branch.size - 1] = last.setSelAfter(
        last.selectionsAfter.subList(0, last.selectionsAfter.size - 1)
    )
    return newBranch
}

private fun <T> conc(a: List<T>, b: List<T>): List<T> =
    if (a.isEmpty()) b else if (b.isEmpty()) a else a + b

private fun eqSelectionShape(a: EditorSelection, b: EditorSelection): Boolean {
    if (a.ranges.size != b.ranges.size) return false
    for (i in a.ranges.indices) {
        if (!a.ranges[i].eq(b.ranges[i])) return false
    }
    return true
}

data class HistoryConfig(
    val groupDelay: Long = DEFAULT_GROUP_DELAY,
    val minDepth: Int = 100
)

internal class HistoryState(
    val done: List<HistoryEvent>,
    val undone: List<HistoryEvent>,
    private val prevTime: Long = 0,
    private val prevUserEvent: String? = null
) {
    fun isolate(): HistoryState = if (prevTime != 0L) HistoryState(done, undone) else this

    fun addChanges(
        event: HistoryEvent,
        time: Long,
        userEvent: String?,
        groupDelay: Long,
        minDepth: Int
    ): HistoryState {
        val lastEvent = done.lastOrNull()
        val newDone: List<HistoryEvent>
        if (lastEvent != null &&
            lastEvent.changes != null && !lastEvent.changes.empty &&
            event.changes != null &&
            (
                userEvent == null ||
                    joinableUserEvent.containsMatchIn(userEvent)
                ) &&
            lastEvent.selectionsAfter.isEmpty() &&
            time - prevTime < groupDelay &&
            isAdjacent(lastEvent.changes, event.changes)
        ) {
            newDone = updateBranch(
                done, done.size - 1, minDepth,
                HistoryEvent(
                    event.changes.compose(lastEvent.changes),
                    conc(
                        StateEffect.mapEffects(
                            event.effects,
                            lastEvent.changes
                        ),
                        lastEvent.effects
                    ),
                    lastEvent.mapped,
                    lastEvent.startSelection,
                    emptyList()
                )
            )
        } else {
            newDone = updateBranch(done, done.size, minDepth, event)
        }
        return HistoryState(newDone, emptyList(), time, userEvent)
    }

    fun addMapping(mapping: ChangeDesc): HistoryState = HistoryState(
        addMappingToBranch(done, mapping),
        addMappingToBranch(undone, mapping),
        prevTime,
        prevUserEvent
    )

    fun addSelection(
        selection: EditorSelection,
        time: Long,
        userEvent: String?,
        newGroupDelay: Long
    ): HistoryState {
        val last = if (done.isNotEmpty()) {
            done[done.size - 1].selectionsAfter
        } else {
            emptyList()
        }
        if (last.isNotEmpty() &&
            time - prevTime < newGroupDelay &&
            userEvent == prevUserEvent &&
            userEvent != null &&
            Regex("^select($|\\.)").containsMatchIn(userEvent) &&
            eqSelectionShape(last[last.size - 1], selection)
        ) {
            return this
        }
        return HistoryState(
            addSelection(done, selection),
            undone,
            time,
            userEvent
        )
    }

    fun pop(side: BranchName, state: EditorState, onlySelection: Boolean): Transaction? {
        val branch = if (side == BranchName.Done) done else undone
        if (branch.isEmpty()) return null
        val event = branch[branch.size - 1]
        val selection = event.selectionsAfter.firstOrNull()
            ?: if (event.startSelection != null) {
                event.startSelection.map(event.changes!!.invertedDesc, 1)
            } else {
                state.selection
            }
        if (onlySelection && event.selectionsAfter.isNotEmpty()) {
            return state.update(
                TransactionSpec(
                    selection = SelectionSpec.EditorSelectionSpec(
                        event.selectionsAfter[event.selectionsAfter.size - 1]
                    ),
                    annotations = listOf(
                        fromHistory.of(
                            FromHistoryInfo(
                                side,
                                popSelection(branch),
                                selection
                            )
                        ),
                        Transaction.userEvent.of(
                            if (side == BranchName.Done) {
                                "select.undo"
                            } else {
                                "select.redo"
                            }
                        )
                    ),
                    scrollIntoView = true
                )
            )
        } else if (event.changes == null) {
            return null
        } else {
            var rest = if (branch.size == 1) {
                emptyList()
            } else {
                branch.subList(0, branch.size - 1)
            }
            if (event.mapped != null) {
                rest = addMappingToBranch(rest, event.mapped)
            }
            return state.update(
                TransactionSpec(
                    changes = ChangeSpec.Set(event.changes),
                    selection = SelectionSpec.EditorSelectionSpec(
                        event.startSelection!!
                    ),
                    effects = event.effects,
                    annotations = listOf(
                        fromHistory.of(
                            FromHistoryInfo(side, rest, selection)
                        ),
                        Transaction.userEvent.of(
                            if (side == BranchName.Done) "undo" else "redo"
                        )
                    ),
                    filter = false
                )
            )
        }
    }
}

private val _historyField: StateField<HistoryState> = StateField.define(
    StateFieldSpec(
        create = { HistoryState(emptyList(), emptyList()) },
        update = { value, tr ->
            val fromHist = tr.annotation(fromHistory)
            if (fromHist != null) {
                val item = HistoryEvent.fromTransaction(tr, fromHist.selection)
                val from = fromHist.side
                var other = if (from == BranchName.Done) {
                    value.undone
                } else {
                    value.done
                }
                if (item != null) {
                    other = updateBranch(other, other.size, 100, item)
                } else {
                    other = addSelection(other, tr.startState.selection)
                }
                return@StateFieldSpec if (from == BranchName.Done) {
                    HistoryState(fromHist.rest, other)
                } else {
                    HistoryState(other, fromHist.rest)
                }
            }

            var state = value
            val isolate = tr.annotation(isolateHistory)
            if (isolate == "full" || isolate == "before") {
                state = state.isolate()
            }

            if (tr.annotation(Transaction.addToHistory) == false) {
                state = if (!tr.changes.empty) {
                    state.addMapping(tr.changes.desc)
                } else {
                    state
                }
            } else {
                val event = HistoryEvent.fromTransaction(tr)
                val time = tr.annotation(Transaction.time) ?: 0L
                val userEvent = tr.annotation(Transaction.userEvent)
                if (event != null) {
                    state = state.addChanges(
                        event, time, userEvent,
                        DEFAULT_GROUP_DELAY, 100
                    )
                } else if (tr.selection != null) {
                    state = state.addSelection(
                        tr.startState.selection,
                        time,
                        userEvent,
                        DEFAULT_GROUP_DELAY
                    )
                }
            }

            if (isolate == "full" || isolate == "after") {
                state = state.isolate()
            }
            state
        }
    )
)

/**
 * The state field that stores undo/redo history. Can be used to
 * check whether history is active in a given state (by checking
 * `state.field(historyField, false) != null`) or to check undo/redo
 * availability via [undoDepth] and [redoDepth].
 */
val historyField: StateField<*> = _historyField

/** Undo the last change. */
val undo: (EditorView) -> Boolean = { view ->
    applyHistory(view, isUndo = true, onlySelection = false)
}

/** Redo the last undone change. */
val redo: (EditorView) -> Boolean = { view ->
    applyHistory(view, isUndo = false, onlySelection = false)
}

/**
 * Undo a change or selection change. Unlike [undo], this will
 * also restore selection-only changes (cursor movements) that
 * occurred between edits, stepping through selection history.
 */
val undoSelection: (EditorView) -> Boolean = { view ->
    applyHistory(view, isUndo = true, onlySelection = true)
}

/**
 * Redo a change or selection change.
 *
 * @see undoSelection
 */
val redoSelection: (EditorView) -> Boolean = { view ->
    applyHistory(view, isUndo = false, onlySelection = true)
}

private fun applyHistory(view: EditorView, isUndo: Boolean, onlySelection: Boolean): Boolean {
    val state = view.state
    val histState = state.field(_historyField, false) ?: return false
    val side = if (isUndo) BranchName.Done else BranchName.Undone
    val tr = histState.pop(side, state, onlySelection) ?: return false
    view.dispatchTransaction(tr)
    return true
}

fun undoDepth(state: EditorState): Int {
    val hist = state.field(_historyField, false) ?: return 0
    return hist.done.count { it.changes != null }
}

fun redoDepth(state: EditorState): Int {
    val hist = state.field(_historyField, false) ?: return 0
    return hist.undone.count { it.changes != null }
}

fun history(config: HistoryConfig = HistoryConfig()): Extension {
    return ExtensionList(
        listOf(
            _historyField,
            keymapOf(
                KeyBinding(
                    key = "Ctrl-z",
                    mac = "Meta-z",
                    run = undo
                ),
                KeyBinding(
                    key = "Ctrl-y",
                    mac = "Meta-Shift-z",
                    run = redo
                ),
                KeyBinding(
                    key = "Ctrl-Shift-z",
                    run = redo
                )
            )
        )
    )
}
