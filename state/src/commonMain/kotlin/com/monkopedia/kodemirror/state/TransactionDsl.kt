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
 * Builder scope for constructing a [TransactionSpec] via DSL.
 *
 * Example:
 * ```kotlin
 * val spec = transactionSpec {
 *     insert(0, "Hello")
 *     selection(5)
 *     scrollIntoView()
 * }
 * ```
 */
@TransactionDsl
class TransactionSpecBuilder @PublishedApi internal constructor() {
    private val changes = mutableListOf<ChangeSpec.Single>()
    private var selectionSpec: SelectionSpec? = null
    private val effectsList = mutableListOf<StateEffect<*>>()
    private val annotationsList = mutableListOf<Annotation<*>>()
    private var userEventValue: String? = null
    private var scrollIntoViewValue: Boolean = false
    private var filterValue: Boolean? = null
    private var sequentialValue: Boolean = false

    /** Insert text at the given position. */
    fun insert(pos: Int, text: String) {
        changes.add(ChangeSpec.Single(pos, pos, text.asInsert()))
    }

    /** Insert a [Text] at the given position. */
    fun insert(pos: Int, text: Text) {
        changes.add(ChangeSpec.Single(pos, pos, InsertContent.TextContent(text)))
    }

    /** Delete text from [from] to [to]. */
    fun delete(from: Int, to: Int) {
        changes.add(ChangeSpec.Single(from, to))
    }

    /** Replace text from [from] to [to] with [text]. */
    fun replace(from: Int, to: Int, text: String) {
        changes.add(ChangeSpec.Single(from, to, text.asInsert()))
    }

    /** Replace text from [from] to [to] with [text]. */
    fun replace(from: Int, to: Int, text: Text) {
        changes.add(ChangeSpec.Single(from, to, InsertContent.TextContent(text)))
    }

    /** Set the cursor position. */
    fun selection(cursor: Int) {
        selectionSpec = SelectionSpec.CursorSpec(cursor)
    }

    /** Set a selection range. */
    fun selection(anchor: Int, head: Int) {
        selectionSpec = SelectionSpec.CursorSpec(anchor, head)
    }

    /** Set the selection from an [EditorSelection]. */
    fun selection(selection: EditorSelection) {
        selectionSpec = SelectionSpec.EditorSelectionSpec(selection)
    }

    /** Add a state effect. */
    fun <T> effect(effect: StateEffect<T>) {
        effectsList.add(effect)
    }

    /** Add an annotation. */
    fun <T> annotate(annotation: Annotation<T>) {
        annotationsList.add(annotation)
    }

    /** Set the user event type. */
    fun userEvent(event: String) {
        userEventValue = event
    }

    /** Mark that the selection should be scrolled into view. */
    fun scrollIntoView() {
        scrollIntoViewValue = true
    }

    /** Set whether transaction filters should be applied. */
    fun filter(enabled: Boolean) {
        filterValue = enabled
    }

    /** Mark positions as relative to the document after preceding specs. */
    fun sequential() {
        sequentialValue = true
    }

    @PublishedApi
    internal fun build(): TransactionSpec {
        val changeSpec: ChangeSpec? = when (changes.size) {
            0 -> null
            1 -> changes[0]
            else -> ChangeSpec.Multi(changes)
        }
        return TransactionSpec(
            changes = changeSpec,
            selection = selectionSpec,
            effects = effectsList.ifEmpty { null },
            annotations = annotationsList.ifEmpty { null },
            userEvent = userEventValue,
            scrollIntoView = scrollIntoViewValue,
            filter = filterValue,
            sequential = sequentialValue
        )
    }
}

/** Marks DSL scope for [TransactionSpecBuilder] to prevent accidental scope leaking. */
@DslMarker
annotation class TransactionDsl

/**
 * Create a [TransactionSpec] using a DSL builder.
 *
 * ```kotlin
 * val spec = transactionSpec {
 *     replace(0, 5, "world")
 *     selection(5)
 *     scrollIntoView()
 * }
 * ```
 */
inline fun transactionSpec(block: TransactionSpecBuilder.() -> Unit): TransactionSpec =
    TransactionSpecBuilder().apply(block).build()
