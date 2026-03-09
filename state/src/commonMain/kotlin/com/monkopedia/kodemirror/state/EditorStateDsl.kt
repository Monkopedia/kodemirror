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
 * Builder scope for constructing an [EditorState] via DSL.
 *
 * Example:
 * ```kotlin
 * val state = editorState {
 *     doc("Hello, world!")
 *     selection(5)
 *     extensions {
 *         +lineNumbers
 *         +history()
 *         +bracketMatching()
 *     }
 * }
 * ```
 */
@EditorStateDsl
class EditorStateBuilder @PublishedApi internal constructor() {
    private var docSpec: DocSpec? = null
    private var selectionSpec: SelectionSpec? = null
    private var extensionValue: Extension? = null

    /** Set the initial document content from a string. */
    fun doc(content: String) {
        docSpec = DocSpec.StringDoc(content)
    }

    /** Set the initial document content from a [Text]. */
    fun doc(text: Text) {
        docSpec = DocSpec.TextDoc(text)
    }

    /** Set the initial cursor position. */
    fun selection(cursor: DocPos) {
        selectionSpec = SelectionSpec.CursorSpec(cursor)
    }

    /** Set the initial selection as a range. */
    fun selection(anchor: DocPos, head: DocPos) {
        selectionSpec = SelectionSpec.CursorSpec(anchor, head)
    }

    /** Set the initial selection from an [EditorSelection]. */
    fun selection(selection: EditorSelection) {
        selectionSpec = SelectionSpec.EditorSelectionSpec(selection)
    }

    /** Set a single extension. */
    fun extension(ext: Extension) {
        extensionValue = ext
    }

    /** Build the extension list using a DSL block. */
    fun extensions(block: ExtensionListBuilder.() -> Unit) {
        extensionValue = ExtensionListBuilder().apply(block).build()
    }

    @PublishedApi
    internal fun build(): EditorStateConfig = EditorStateConfig(
        doc = docSpec,
        selection = selectionSpec,
        extensions = extensionValue
    )
}

/**
 * Builder scope for assembling a list of extensions using unary `+`.
 *
 * Example:
 * ```kotlin
 * extensions {
 *     +lineNumbers
 *     +history()
 *     +syntaxHighlighting(defaultHighlightStyle)
 * }
 * ```
 */
@EditorStateDsl
class ExtensionListBuilder @PublishedApi internal constructor() {
    @PublishedApi
    internal val list = mutableListOf<Extension>()

    /** Add an extension to the list. */
    operator fun Extension.unaryPlus() {
        list.add(this)
    }

    @PublishedApi
    internal fun build(): Extension = extensionListOf(*list.toTypedArray())
}

/** Marks DSL scope for [EditorStateBuilder] to prevent accidental scope leaking. */
@DslMarker
annotation class EditorStateDsl

/**
 * Create an [EditorState] using a DSL builder.
 *
 * ```kotlin
 * val state = editorState {
 *     doc("Hello, world!")
 *     selection(5)
 *     extensions {
 *         +lineNumbers
 *         +history()
 *     }
 * }
 * ```
 */
inline fun editorState(block: EditorStateBuilder.() -> Unit): EditorState =
    EditorState.create(EditorStateBuilder().apply(block).build())
