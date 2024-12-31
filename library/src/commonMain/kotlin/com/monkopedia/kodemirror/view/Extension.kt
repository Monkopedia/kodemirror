package com.monkopedia.kodemirror.view

import androidx.compose.foundation.interaction.Interaction
import com.monkopedia.kodemirror.state.ChangeDesc
import com.monkopedia.kodemirror.state.ChangeSet
import com.monkopedia.kodemirror.state.EditorSelection
import com.monkopedia.kodemirror.state.EditorState
import com.monkopedia.kodemirror.state.Extension
import com.monkopedia.kodemirror.state.Facet
import com.monkopedia.kodemirror.state.Facet.Companion.of
import com.monkopedia.kodemirror.state.Facet.Companion.ofRight
import com.monkopedia.kodemirror.state.Line
import com.monkopedia.kodemirror.state.RangeSet
import com.monkopedia.kodemirror.state.SelectionRange
import com.monkopedia.kodemirror.state.SpanIterator
import com.monkopedia.kodemirror.state.StateEffect
import com.monkopedia.kodemirror.state.Transaction
import com.monkopedia.kodemirror.state.extension
import com.monkopedia.kodemirror.state.fold
import kotlin.jvm.JvmInline
import kotlin.math.max
import kotlin.math.min
import kotlinx.atomicfu.atomic

//import {EditorState, Transaction, ChangeSet, ChangeDesc, Facet, Line,
//StateEffect, Extension, SelectionRange, RangeSet, EditorSelection} from "@codemirror/state"
//import {StyleModule} from "style-mod"
//import {DecorationSet, Decoration} from "./decoration"
//import {EditorView, DOMEventHandlers} from "./editorview"
//import {Attrs} from "./attributes"
//import {Isolate, autoDirection} from "./bidi"
//import {Rect, ScrollStrategy} from "./dom"
//import {MakeSelectionStyle} from "./input"

/// Command functions are used in key bindings and other types of user
/// actions. Given an editor view, they check whether their effect can
/// apply to the editor, and if it can, perform it as a side effect
/// (which usually means [dispatching](#view.EditorView.dispatch) a
/// transaction) and return `true`.
fun interface Command {
    fun exec(target: EditorView): Boolean
}

val clickAddsSelectionRange = Facet.define<(event: Interaction) -> Boolean>()

val dragMovesSelection = Facet.define<(event: Interaction) -> Boolean>()

val mouseSelectionStyle = Facet.define<MakeSelectionStyle>()

val exceptionSink = Facet.define<(exception: Throwable) -> Unit>()

val updateListener = Facet.define<(update: ViewUpdate) -> Unit>()

typealias InputHandler = (view: EditorView, from: Int, to: Int, text: String, insert: () -> Transaction) -> Boolean

val inputHandler = Facet.define<InputHandler>()

typealias FocusChangeEffect = (state: EditorState, focusing: Boolean) -> StateEffect<*>?

val focusChangeEffect = Facet.define<FocusChangeEffect>()

val clipboardInputFilter = Facet.define<(text: String, state: EditorState) -> String>()
val clipboardOutputFilter = Facet.define<(text: String, state: EditorState) -> String>()

val perLineTextDirection = Facet.define<Boolean, Boolean>(combine = { values -> values.any { it } })

val nativeSelectionHidden =
    Facet.define<Boolean, Boolean>(combine = { values -> values.any { it } })

data class ScrollOptions(
    val x: ScrollStrategy,
    val y: ScrollStrategy,
    val xMargin: Int,
    val yMargin: Int
)
typealias ScrollHandler = (
    view: EditorView,
    range: SelectionRange,
    options: ScrollOptions
) -> Boolean

val scrollHandler = Facet.define<ScrollHandler>()

class ScrollTarget(
    val range: SelectionRange,
    val y: ScrollStrategy = ScrollStrategy.nearest,
    val x: ScrollStrategy = ScrollStrategy.nearest,
    val yMargin: Int = 5,
    val xMargin: Int = 5,
    // This data structure is abused to also store precise scroll
    // snapshots, instead of a `scrollIntoView` request. When this
    // flag is `true`, `range` points at a position in the reference
    // line, `yMargin` holds the difference between the top of that
    // line and the top of the editor, and `xMargin` holds the
    // editor's `scrollLeft`.
    val isSnapshot: Boolean = false
) {

    fun map(changes: ChangeDesc): ScrollTarget {
        return if (changes.isEmpty) this else ScrollTarget(
            this.range.map(changes),
            this.y,
            this.x,
            this.yMargin,
            this.xMargin,
            this.isSnapshot
        )
    }

    fun clip(state: EditorState): ScrollTarget {
        return if (this.range.to <= state.doc.length) this else
            ScrollTarget(
                EditorSelection.cursor(state.doc.length),
                this.y,
                this.x,
                this.yMargin,
                this.xMargin,
                this.isSnapshot
            )
    }
}

val scrollIntoView = StateEffect.define<ScrollTarget>(map = { t, ch -> t.map(ch) })

val setEditContextFormatting = StateEffect.define<DecorationSet>()

/// Log or report an unhandled exception in client code. Should
/// probably only be used by extension code that allows client code to
/// provide functions, and calls those functions in a context where an
/// exception can't be propagated to calling code in a reasonable way
/// (for example when in an event handler).
///
/// Either calls a handler registered with
/// [`EditorView.exceptionSink`](#view.EditorView^exceptionSink),
/// `window.onerror`, if defined, or `console.error` (in which case
/// it'll pass `context`, when given, as first argument).
fun logException(state: EditorState, exception: Throwable, context: String? = null) {
    val handler = state.facet(exceptionSink)
    if (handler.isNotEmpty()) handler[0](exception)
//    else if (window.onerror) window.onerror(
//        String(exception),
//        context,
//        undefined,
//        undefined,
//        exception
//    )
    else if (context != null) println("$context:${exception.stackTraceToString()}")
    else println(exception.stackTraceToString())
}

val editable = Facet.define<Boolean, Boolean>(combine = { values -> values.firstOrNull() ?: true })

/// This is the interface plugin objects conform to.
interface PluginValue {
    /// Notifies the plugin of an update that happened in the view. This
    /// is called _before_ the view updates its own DOM. It is
    /// responsible for updating the plugin's internal state (including
    /// any state that may be read by plugin fields) and _writing_ to
    /// the DOM for the changes in the update. To avoid unnecessary
    /// layout recomputations, it should _not_ read the DOM layout—use
    /// [`requestMeasure`](#view.EditorView.requestMeasure) to schedule
    /// your code in a DOM reading phase if you need to.
    fun update(update: ViewUpdate) = Unit

    /// Called when the document view is updated (due to content,
    /// decoration, or viewport changes). Should not try to immediately
    /// start another view update. Often useful for calling
    /// [`requestMeasure`](#view.EditorView.requestMeasure).
    fun docViewUpdate(view: EditorView) = Unit

    /// Called when the plugin is no longer going to be used. Should
    /// revert any changes the plugin made to the DOM.
    fun destroy() = Unit
}

val nextPluginID = atomic(0)

val viewPlugin = Facet.define<ViewPlugin<*>>()

/// Provides additional information when defining a [view
/// plugin](#view.ViewPlugin).
data class PluginSpec<V : PluginValue>(
    /// Register the given [event
    /// handlers](#view.EditorView^domEventHandlers) for the plugin.
    /// When called, these will have their `this` bound to the plugin
    /// value.
    val eventHandlers: DOMEventHandlers<V>? = null,

    /// Registers [event observers](#view.EditorView^domEventObservers)
    /// for the plugin. Will, when called, have their `this` bound to
    /// the plugin value.
    val eventObservers: DOMEventHandlers<V>? = null,

    /// Specify that the plugin provides additional extensions when
    /// added to an editor configuration.
    val provide: ((plugin: ViewPlugin<V>) -> Extension)? = null,

    /// Allow the plugin to provide decorations. When given, this should
    /// be a function that take the plugin value and return a
    /// [decoration set](#view.DecorationSet). See also the caveat about
    /// [layout-changing decorations](#view.EditorView^decorations) that
    /// depend on the view.
    val decorations: ((value: V) -> DecorationSet)? = null
)

/// View plugins associate stateful values with a view. They can
/// influence the way the content is drawn, and are notified of things
/// that happen in the view.
class ViewPlugin<V : PluginValue> private constructor(
    internal val id: Int,
    internal val create: (view: EditorView) -> V,
    internal val domEventHandlers: DOMEventHandlers<V>? = null,
    internal val domEventObservers: DOMEventHandlers<V>? = null,
    buildExtensions: (plugin: ViewPlugin<V>) -> Extension
) {
    /// Instances of this class act as extensions.
    val extension = buildExtensions(this)

    companion object {
        /// Define a plugin from a constructor function that creates the
        /// plugin's value, given an editor view.
        fun <V : PluginValue> define(
            create: (view: EditorView) -> V,
            spec: PluginSpec<V> = PluginSpec()
        ): ViewPlugin<V> {
            val (eventHandlers, eventObservers, provide, deco) = spec
            return ViewPlugin<V>(
                nextPluginID.getAndIncrement(),
                create,
                eventHandlers,
                eventObservers
            ) { plugin ->
                val ext = mutableListOf(viewPlugin.of(plugin))
                if (deco != null) {
                    ext.add(decorations.ofRight { view ->
                        val pluginInst = view.plugin(plugin)
                        pluginInst?.let { deco(it) } ?: Decoration.none
                    })
                }
                if (provide != null) ext.add(provide(plugin))
                ext.extension
            }
        }

        /// Create a plugin for a class whose constructor takes a single
        /// editor view as argument.
        fun <V : PluginValue> fromClass(
            cls: (view: EditorView) -> V,
            spec: PluginSpec<V>? = null
        ): ViewPlugin<V> {
            return ViewPlugin.define(cls, spec)
        }
    }
}

class PluginInstance(var spec: ViewPlugin<*>?) {
    // When starting an update, all plugins have this field set to the
    // update object, indicating they need to be updated. When finished
    // updating, it is set to `false`. Retrieving a plugin that needs to
    // be updated with `view.plugin` forces an eager update.
    var mustUpdate: ViewUpdate? = null

    // This is null when the plugin is initially created, but
    // initialized on the first update.
    var value: PluginValue? = null

    fun update(view: EditorView): PluginInstance {
        if (this.value == null) {
            if (this.spec != null) {
                try {
                    this.value = this.spec!!.create(view)
                } catch (e: Throwable) {
                    logException(view.state, e, "CodeMirror plugin crashed")
                    this.deactivate()
                }
            }
        } else if (this.mustUpdate != null) {
            val update = this.mustUpdate!!
            this.mustUpdate = null
            try {
                this.value!!.update(update)
            } catch (e: Throwable) {
                logException(update.state, e, "CodeMirror plugin crashed")
                try {
                    this.value!!.destroy()
                } catch (_: Throwable) {
                }
                this.deactivate()
            }
        }
        return this
    }

    fun destroy(view: EditorView) {
        try {
            this.value!!.destroy()
        } catch (e: Throwable) {
            logException(view.state, e, "CodeMirror plugin crashed")
        }
    }

    fun deactivate() {
        this.spec = null
        this.value = null
    }
}

interface MeasureRequest<T> {
    /// Called in a DOM read phase to gather information that requires
    /// DOM layout. Should _not_ mutate the document.
    fun read(view: EditorView): T

    /// Called in a DOM write phase to update the document. Should _not_
    /// do anything that triggers DOM layout.
    fun write(measure: T, view: EditorView): Unit = Unit

    /// When multiple requests with the same key are scheduled, only the
    /// last one will actually be run.
    val key: Any?
        get() = null
}

sealed interface AttrSource {
    fun interface AttrFactory : AttrSource {
        fun get(view: EditorView): Attrs
    }

    @JvmInline
    value class AttrHolder(val attrs: Attrs) : AttrSource
}
//export type AttrSource = Attrs | ((view: EditorView) -> Attrs | null)

val editorAttributes = Facet.define<AttrSource?>()

val contentAttributes = Facet.define<AttrSource?>()

// Provide decorations
val decorations = Facet.define<DecorationSetOrFactory>()

val outerDecorations = Facet.define<DecorationSetOrFactory>()

val atomicRanges = Facet.define<(view: EditorView) -> RangeSet<*>>()

val bidiIsolatedRanges = Facet.define<DecorationSetOrFactory>()

fun getIsolatedRanges(view: EditorView, line: Line): List<Isolate> {
    val isolates = view.state.facet(bidiIsolatedRanges)
    if (isolates.isEmpty()) return emptyList()
    val sets = isolates.map { i -> i.fold({ this }, { this(view) }) }
    val result = mutableListOf<Isolate>()
    RangeSet.spans(sets, line.from, line.to, object : SpanIterator<Isolate> {
        override fun point(
            from: Int,
            to: Int,
            value: Isolate,
            active: List<Isolate>,
            openStart: Int,
            index: Int
        ) {
        }

        override fun span(fromDoc: Int, toDoc: Int, active: List<Isolate>, open: Int) {
            val from = fromDoc - line.from
            val to = toDoc - line.from
            val level = result
            var open = open
            for (i in active.indices.reversed()) {
                val direction = active[i].spec.bidiIsolate ?: autoDirection(line.text, from, to)
                val update = level.lastOrNull()
                if (open > 0 && update != null &&
                    update.to == from && update.direction == direction
                ) {
                    update.to = to
                    level = update.inner as Isolate[]
                } else {
                    val add = { from, to, direction, inner: [] }
                    level.push(add)
                    level = emptyList()
                }
                open--
            }
        }
    })
    return result
}

val scrollMargins = Facet.define<(view: EditorView) -> Rect?>()

fun getScrollMargins(view: EditorView): Rect {
    var left = 0
    var right = 0
    var top = 0
    var bottom = 0
    for (source in view.state.facet(scrollMargins)) {
        val m = source(view) ?: continue
        if (m.left != null) left = max(left, m.left)
        if (m.right != null) right = max(right, m.right)
        if (m.top != null) top = max(top, m.top)
        if (m.bottom != null) bottom = max(bottom, m.bottom)
    }
    return Rect(left, top, right, bottom)
}

//val styleModule = Facet.define<StyleModule>()

enum class UpdateFlag(val value: Int) {
    Focus(1),
    Height(2),
    Viewport(4),
    ViewportMoved(8),
    Geometry(16)
}

data class ChangedRange(val fromA: Int, val toA: Int, val fromB: Int, val toB: Int) {
    fun join(other: ChangedRange): ChangedRange {
        return ChangedRange(
            min(this.fromA, other.fromA), max(this.toA, other.toA),
            min(this.fromB, other.fromB), max(this.toB, other.toB)
        )
    }

    fun addToSet(set: MutableList<ChangedRange>): List<ChangedRange> {
        var i = set.size
        var me: ChangedRange = this
        while (i > 0) {
            val range = set[i - 1]
            if (range.fromA > me.toA) continue
            if (range.toA < me.fromA) break
            me = me.join(range)
            set.removeAt(i - 1)
            i--
        }
        set.add(i, me)
        return set
    }

    companion object {
        fun extendWithRanges(diff: List<ChangedRange>, ranges: List<Int>): List<ChangedRange> {
            if (ranges.isEmpty()) return diff
            val result = mutableListOf<ChangedRange>()
            var dI = 0
            var rI = 0
            var posA = 0
            var posB = 0
            while (true) {
                val next = diff.getOrNull(dI)
                val off = posA - posB
                val end = next?.fromB ?: 1e9.toInt()
                while (rI < ranges.size && ranges[rI] < end) {
                    val from = ranges[rI]
                    val to = ranges[rI + 1]
                    val fromB = max(posB, from)
                    val toB = min(end, to)
                    if (fromB <= toB) ChangedRange(fromB + off, toB + off, fromB, toB).addToSet(
                        result
                    )
                    if (to > end) break
                    else rI += 2
                }
                if (next == null) return result
                ChangedRange(next.fromA, next.toA, next.fromB, next.toB).addToSet(result)
                posA = next.toA
                posB = next.toB
                dI++
            }
        }
    }
}

/// View [plugins](#view.ViewPlugin) are given instances of this
/// class, which describe what happened, whenever the view is updated.
class ViewUpdate
private constructor(
    /// The editor view that the update is associated with.
    val view: EditorView,
    /// The new editor state.
    val state: EditorState,
    /// The transactions involved in the update. May be empty.
    val transactions: List<Transaction>
) {
    /// The previous editor state.
    val startState: EditorState = view.state

    /// The changes made to the document by this update.
    val changes: ChangeSet =
        transactions.fold(ChangeSet.empty(this.startState.doc.length)) { state, tr ->
            state.compose(tr.changes)
        }

    /// @internal
    internal var flags = 0

    /// @internal
    internal val changedRanges: List<ChangedRange> = buildList {
        changes.iterChangedRanges({ fromA, toA, fromB, toB ->
            add(ChangedRange(fromA, toA, fromB, toB))
        })
    }

    /// Tells you whether the [viewport](#view.EditorView.viewport) or
    /// [visible ranges](#view.EditorView.visibleRanges) changed in this
    /// update.
    val viewportChanged: Boolean
        get() {
            return (this.flags and UpdateFlag.Viewport.value) > 0
        }

    /// Returns true when
    /// [`viewportChanged`](#view.ViewUpdate.viewportChanged) is true
    /// and the viewport change is not just the result of mapping it in
    /// response to document changes.
    val viewportMoved: Boolean
        get() {
            return (this.flags and UpdateFlag.ViewportMoved.value) > 0
        }

    /// Indicates whether the height of a block element in the editor
    /// changed in this update.
    val heightChanged: Boolean
        get() {
            return (this.flags and UpdateFlag.Height.value) > 0
        }

    /// Returns true when the document was modified or the size of the
    /// editor, or elements within the editor, changed.
    val geometryChanged: Boolean
        get() {
            return this.docChanged || (this.flags and (UpdateFlag.Geometry.value or UpdateFlag.Height.value)) > 0
        }

    /// True when this update indicates a focus change.
    val focusChanged: Boolean
        get() {
            return (this.flags and UpdateFlag.Focus.value) > 0
        }

    /// Whether the document changed in this update.
    val docChanged: Boolean
        get() {
            return !this.changes.isEmpty
        }

    /// Whether the selection was explicitly set in this update.
    val selectionSet: Boolean
        get() {
            return this.transactions.any { it.selection != null }
        }

    /// @internal
    internal val empty: Boolean
        get() {
            return this.flags == 0 && this.transactions.size == 0
        }

    companion object {
        /// @internal
        internal fun create(
            view: EditorView,
            state: EditorState,
            transactions: List<Transaction>
        ): ViewUpdate {
            return ViewUpdate(view, state, transactions)
        }
    }
}
