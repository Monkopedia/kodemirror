package com.monkopedia.kodemirror.view

import com.monkopedia.kodemirror.state.*

/**
 * A plugin that adds extra padding at the bottom of the editor content,
 * allowing the user to scroll the last line to the top of the editor.
 */
class ScrollPastEndPlugin : ViewPlugin {
    private var height = 1000
    private var attrs = mapOf("style" to "padding-bottom: 1000px")

    fun update(update: ViewUpdate) {
        val view = update.view
        val newHeight = view.viewState.editorHeight -
                view.defaultLineHeight - view.documentPadding.top - 0.5
        if (newHeight >= 0 && newHeight != height) {
            height = newHeight
            attrs = mapOf("style" to "padding-bottom: ${height}px")
        }
    }
}

/**
 * Returns an extension that makes sure the content has a bottom
 * margin equivalent to the height of the editor, minus one line
 * height, so that every line in the document can be scrolled to the
 * top of the editor.
 *
 * This is only meaningful when the editor is scrollable, and should
 * not be enabled in editors that take the size of their content.
 */
fun scrollPastEnd(): Extension {
    val plugin = ViewPlugin.fromClass { ScrollPastEndPlugin() }
    return listOf(
        plugin,
        contentAttributes.of { view -> view.plugin(plugin)?.attrs }
    )
}
