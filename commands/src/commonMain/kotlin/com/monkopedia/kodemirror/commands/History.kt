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
import com.monkopedia.kodemirror.state.ChangeSet
import com.monkopedia.kodemirror.state.EditorSelection
import com.monkopedia.kodemirror.state.EditorState
import com.monkopedia.kodemirror.state.Extension
import com.monkopedia.kodemirror.state.ExtensionList
import com.monkopedia.kodemirror.state.SelectionSpec
import com.monkopedia.kodemirror.state.StateField
import com.monkopedia.kodemirror.state.StateFieldSpec
import com.monkopedia.kodemirror.state.Transaction
import com.monkopedia.kodemirror.state.TransactionSpec
import com.monkopedia.kodemirror.view.EditorView
import com.monkopedia.kodemirror.view.KeyBinding
import com.monkopedia.kodemirror.view.keymapOf

/** Default delay (ms) within which consecutive edits are grouped. */
private const val DEFAULT_GROUP_DELAY = 500L

/** Annotation marking a transaction as an undo or redo operation. */
private val isHistoryTransaction: AnnotationType<Boolean> = Annotation.define()

/**
 * A single history event: stores the inverted changes needed to undo,
 * plus the selection before the change.
 */
internal class HistoryEvent(
    val changes: ChangeSet,
    val startSelection: EditorSelection,
    val userEvent: String?,
    val timestamp: Long
)

/**
 * A branch (undo or redo stack) is a list of history events.
 */
internal class Branch(val events: List<HistoryEvent> = emptyList()) {
    val isEmpty: Boolean get() = events.isEmpty()
    val size: Int get() = events.size

    fun push(event: HistoryEvent): Branch = Branch(events + event)

    fun pop(): Pair<HistoryEvent, Branch> {
        val last = events.last()
        return last to Branch(events.dropLast(1))
    }

    fun mapThrough(changes: ChangeSet): Branch {
        if (events.isEmpty() || changes.empty) return this
        return Branch(
            events.map { event ->
                HistoryEvent(
                    event.changes.map(changes),
                    event.startSelection.map(changes),
                    event.userEvent,
                    event.timestamp
                )
            }
        )
    }
}

/**
 * The full history state: done (undo) and undone (redo) branches.
 */
internal class HistoryState(
    val done: Branch = Branch(),
    val undone: Branch = Branch()
) {
    fun addChange(tr: Transaction, groupDelay: Long): HistoryState {
        val invertedChanges = tr.changes.invert(tr.startState.doc)
        val time = tr.annotation(Transaction.time) ?: 0L
        val event = tr.annotation(Transaction.userEvent)

        // Check if we can group with the last event
        if (!done.isEmpty) {
            val lastEvent = done.events.last()
            val canGroup = event != null &&
                lastEvent.userEvent != null &&
                event == lastEvent.userEvent &&
                (time - lastEvent.timestamp) < groupDelay
            if (canGroup) {
                // Merge with the last event
                val merged = HistoryEvent(
                    invertedChanges.compose(lastEvent.changes),
                    lastEvent.startSelection,
                    event,
                    time
                )
                val newDone = Branch(done.events.dropLast(1) + merged)
                return HistoryState(newDone, Branch())
            }
        }

        val newEvent = HistoryEvent(
            invertedChanges,
            tr.startState.selection,
            event,
            time
        )
        return HistoryState(done.push(newEvent), Branch())
    }

    fun mapThrough(changes: ChangeSet): HistoryState {
        return HistoryState(
            done.mapThrough(changes),
            undone.mapThrough(changes)
        )
    }
}

/**
 * Configuration for the history extension.
 *
 * @param groupDelay Milliseconds within which consecutive edits with
 *   the same userEvent are grouped into a single undo step.
 */
data class HistoryConfig(
    val groupDelay: Long = DEFAULT_GROUP_DELAY
)

/**
 * The StateField that stores undo/redo history.
 */
internal val historyField: StateField<HistoryState> = StateField.define(
    StateFieldSpec(
        create = { HistoryState() },
        update = { value, tr ->
            // Check if this transaction carries a direct history state update
            for (effect in tr.effects) {
                val histEffect = effect.asType(historyFieldEffect)
                if (histEffect != null) {
                    return@StateFieldSpec histEffect.value
                }
            }

            // If addToHistory is explicitly false, just map existing events
            if (tr.annotation(Transaction.addToHistory) == false) {
                if (tr.docChanged) value.mapThrough(tr.changes) else value
            } else if (tr.docChanged) {
                value.addChange(tr, DEFAULT_GROUP_DELAY)
            } else {
                value
            }
        }
    )
)

/**
 * Undo the last change.
 */
val undo: (EditorView) -> Boolean = { view ->
    applyHistory(view, undo = true)
}

/**
 * Redo the last undone change.
 */
val redo: (EditorView) -> Boolean = { view ->
    applyHistory(view, undo = false)
}

private fun applyHistory(view: EditorView, undo: Boolean): Boolean {
    val state = view.state
    val histState = state.field(historyField, false) ?: return false
    val source = if (undo) histState.done else histState.undone
    if (source.isEmpty) return false

    val (event, remaining) = source.pop()
    val dest = if (undo) histState.undone else histState.done

    // The inverted changes to apply
    val changes = event.changes

    // Push the reverse of this operation onto the other branch
    val reverseChanges = changes.invert(state.doc)
    val reverseEvent = HistoryEvent(
        reverseChanges,
        state.selection,
        if (undo) "undo" else "redo",
        event.timestamp
    )

    val newHistState = if (undo) {
        HistoryState(remaining, dest.push(reverseEvent))
    } else {
        HistoryState(dest.push(reverseEvent), remaining)
    }

    view.dispatch(
        TransactionSpec(
            changes = com.monkopedia.kodemirror.state.ChangeSpec.Set(changes),
            selection = SelectionSpec.EditorSelectionSpec(
                event.startSelection.map(changes)
            ),
            effects = listOf(
                historyFieldEffect.of(newHistState)
            ),
            annotations = listOf(
                Transaction.addToHistory.of(false),
                isHistoryTransaction.of(true),
                Transaction.userEvent.of(if (undo) "undo" else "redo")
            ),
            filter = false
        )
    )
    return true
}

/** Effect type for directly setting the history state. */
private val historyFieldEffect =
    com.monkopedia.kodemirror.state.StateEffect.define<HistoryState>()

/**
 * Return the number of undoable events.
 */
fun undoDepth(state: EditorState): Int = state.field(historyField, false)?.done?.size ?: 0

/**
 * Return the number of redoable events.
 */
fun redoDepth(state: EditorState): Int = state.field(historyField, false)?.undone?.size ?: 0

/**
 * Create the history extension with the given configuration.
 *
 * This returns an [Extension] that installs the history StateField and
 * adds default undo/redo key bindings (Ctrl-z / Ctrl-y / Ctrl-Shift-z).
 */
fun history(config: HistoryConfig = HistoryConfig()): Extension {
    return ExtensionList(
        listOf(
            historyField,
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
