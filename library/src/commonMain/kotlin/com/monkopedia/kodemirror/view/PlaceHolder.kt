package com.monkopedia.kodemirror.view

import com.monkopedia.kodemirror.state.Extension
import com.monkopedia.kodemirror.decoration.Decoration
import com.monkopedia.kodemirror.decoration.WidgetType
import com.monkopedia.kodemirror.dom.clientRectsFor
import com.monkopedia.kodemirror.dom.flattenRect
import com.monkopedia.kodemirror.dom.Rect
import {ViewPlugin} from "./extension"
import {Decoration, DecorationSet} from "./decoration"
import {EditorView} from "./editorview"

class Placeholder(
    private val content: Any // String | HTMLElement | ((view: EditorView) -> HTMLElement)
) : WidgetType() {

    override fun toDOM(view: EditorView): HTMLElement {
        val wrap = document.createElement("span")
        wrap.className = "cm-placeholder"
        wrap.style.pointerEvents = "none"
        
        val element = when (content) {
            is String -> document.createTextNode(content)
            is HTMLElement -> content.cloneNode(true)
            is Function<*> -> (content as (EditorView) -> HTMLElement)(view)
            else -> throw IllegalArgumentException("Invalid content type")
        }
        
        wrap.appendChild(element)
        
        if (content is String) {
            wrap.setAttribute("aria-label", "placeholder $content")
        } else {
            wrap.setAttribute("aria-hidden", "true")
        }
        
        return wrap
    }

    override fun coordsAt(dom: HTMLElement): Rect? {
        val rects = dom.firstChild?.let { clientRectsFor(it) } ?: emptyList()
        if (rects.isEmpty()) return null
        
        val style = window.getComputedStyle(dom.parentNode as HTMLElement)
        val rect = flattenRect(rects[0], style.direction != "rtl")
        val lineHeight = style.lineHeight.toIntOrNull() ?: return rect
        
        return if (rect.bottom - rect.top > lineHeight * 1.5) {
            Rect(
                left = rect.left,
                right = rect.right,
                top = rect.top,
                bottom = rect.top + lineHeight
            )
        } else rect
    }

    override fun ignoreEvent(): Boolean = false
}

/// Extension that enables a placeholder—a piece of example content
/// to show when the editor is empty.
fun placeholder(content: Any): Extension {
    return ViewPlugin.fromClass(
        { view ->
            object : PluginValue {
                val placeholder = if (content != null) {
                    Decoration.set(listOf(
                        Decoration.widget(
                            mapOf(
                                "widget" to Placeholder(content),
                                "side" to 1
                            )
                        ).range(0)
                    ))
                } else {
                    Decoration.none
                }

                val decorations: DecorationSet
                    get() = if (view.state.doc.length == 0) placeholder else Decoration.none
            }
        },
        mapOf("decorations" to { v -> v.decorations })
    )
}
