package com.monkopedia.kodemirror.view

import com.monkopedia.kodemirror.state.*
import com.monkopedia.kodemirror.extension.*
import com.monkopedia.kodemirror.dom.*
import kotlinx.browser.document

// Whether the primary cursor can be hidden (not supported on iOS WebKit < 534)
private val CanHidePrimary = !(browser.ios && browser.webkit && browser.webkit_version < 534)

/**
 * Configuration for selection drawing.
 */
data class SelectionConfig(
    /**
     * The length of a full cursor blink cycle, in milliseconds.
     * Defaults to 1200. Can be set to 0 to disable blinking.
     */
    val cursorBlinkRate: Int = 1200,
    
    /**
     * Whether to show a cursor for non-empty ranges. Defaults to true.
     */
    val drawRangeCursor: Boolean = true
)

/**
 * Facet for selection configuration.
 */
private val selectionConfig = Facet.define<SelectionConfig, SelectionConfig> { configs ->
    configs.fold(SelectionConfig()) { acc, config ->
        SelectionConfig(
            cursorBlinkRate = minOf(acc.cursorBlinkRate, config.cursorBlinkRate),
            drawRangeCursor = acc.drawRangeCursor || config.drawRangeCursor
        )
    }
}

/**
 * Layer for drawing cursors.
 */
private val cursorLayer = layer(
    above = true,
    markers = { view ->
        val state = view.state
        val conf = state.facet(selectionConfig)
        val cursors = mutableListOf<RectangleMarker>()
        
        for (range in state.selection.ranges) {
            val isPrimary = range == state.selection.main
            if (range.empty) {
                if (!isPrimary || CanHidePrimary) {
                    val className = if (isPrimary) "cm-cursor cm-cursor-primary" else "cm-cursor cm-cursor-secondary"
                    cursors.addAll(RectangleMarker.forRange(view, className, range))
                }
            } else if (conf.drawRangeCursor) {
                val className = if (isPrimary) "cm-cursor cm-cursor-primary" else "cm-cursor cm-cursor-secondary"
                val cursor = EditorSelection.cursor(range.head, if (range.head > range.anchor) -1 else 1)
                cursors.addAll(RectangleMarker.forRange(view, className, cursor))
            }
        }
        cursors
    },
    update = { update, dom ->
        if (update.transactions.any { it.selection }) {
            dom.style.animationName = if (dom.style.animationName == "cm-blink") "cm-blink2" else "cm-blink"
        }
        val confChange = update.startState.facet(selectionConfig) != update.state.facet(selectionConfig)
        if (confChange) {
            setBlinkRate(update.state, dom)
        }
        update.docChanged || update.selectionSet || confChange
    },
    mount = { dom, view ->
        setBlinkRate(view.state, dom)
    },
    class_ = "cm-cursorLayer"
)

private fun setBlinkRate(state: EditorState, dom: HTMLElement) {
    dom.style.animationDuration = "${state.facet(selectionConfig).cursorBlinkRate}ms"
}

/**
 * Layer for drawing selection backgrounds.
 */
private val selectionLayer = layer(
    above = false,
    markers = { view ->
        view.state.selection.ranges
            .flatMap { range -> 
                if (range.empty) emptyList()
                else RectangleMarker.forRange(view, "cm-selectionBackground", range)
            }
    },
    update = { update, _ ->
        update.docChanged || update.selectionSet || update.viewportChanged || 
        update.startState.facet(selectionConfig) != update.state.facet(selectionConfig)
    },
    class_ = "cm-selectionLayer"
)

/**
 * Theme specification for selection styling.
 */
private val themeSpec = mapOf(
    ".cm-line" to mapOf(
        "& ::selection, &::selection" to mapOf(
            "backgroundColor" to "transparent !important"
        ),
        "caretColor" to if (CanHidePrimary) "transparent !important" else null
    ),
    ".cm-content" to mapOf(
        "& :focus" to mapOf(
            "caretColor" to if (CanHidePrimary) "transparent !important" else "initial !important",
            "&::selection, & ::selection" to mapOf(
                "backgroundColor" to "Highlight !important"
            )
        )
    )
)

private val hideNativeSelection = Prec.highest(EditorView.theme(themeSpec))

/**
 * Returns an extension that hides the browser's native selection and
 * cursor, replacing the selection with a background behind the text
 * (with the `cm-selectionBackground` class), and the
 * cursors with elements overlaid over the code (using
 * `cm-cursor-primary` and `cm-cursor-secondary`).
 *
 * This allows the editor to display secondary selection ranges, and
 * tends to produce a type of selection more in line with that users
 * expect in a text editor (the native selection styling will often
 * leave gaps between lines and won't fill the horizontal space after
 * a line when the selection continues past it).
 *
 * It does have a performance cost, in that it requires an extra DOM
 * layout cycle for many updates (the selection is drawn based on DOM
 * layout information that's only available after laying out the
 * content).
 */
fun drawSelection(config: SelectionConfig = SelectionConfig()): Extension {
    return listOf(
        selectionConfig.of(config),
        cursorLayer,
        selectionLayer,
        hideNativeSelection,
        nativeSelectionHidden.of(true)
    )
}

/**
 * Retrieve the [drawSelection] configuration for this state.
 * (Note that this will return a set of defaults even if `drawSelection` isn't enabled.)
 */
fun getDrawSelectionConfig(state: EditorState): SelectionConfig {
    return state.facet(selectionConfig)
}
