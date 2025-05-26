package com.monkopedia.kodemirror.view

typealias ClientRect = Rect
open class Node {
    val nodeType: Int
    val parentNode: Node?
    val innerWidth: Int
    val innerHeight: Int
    val ownerDocument: Document
    val defaultView: Node?

    fun getBoundingClientRect(): ClientRect {

    }
}

open class HTMLElement : Node() {
    val offsetParent: Node?
    val clientHeight: Int
    val clientWidth: Int
    val scrollHeight: Int
    val scrollWidth: Int
}

val document: Document
val window: Window
open class Document : Node() {
    val body: Node
}
class Style {
    val position: String
    val overflow: String
}

open class Window : Node() {
    fun getComputedStyle(elt: HTMLElement): Style {

    }
}
interface ShadowRoot {
    val host: Node
}

class DocumentOrShadowRoot : Document(), ShadowRoot

class HTMLText : HTMLElement()
