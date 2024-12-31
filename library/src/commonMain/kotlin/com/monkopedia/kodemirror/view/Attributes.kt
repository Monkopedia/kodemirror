package com.monkopedia.kodemirror.view

typealias Attrs = Map<String, String>
typealias MutableAttrs = MutableMap<String, String>
//export type Attrs = {[name: string]: string}

fun combineAttrs(source: Attrs, target: MutableAttrs): Attrs {
    for ((name, value) in source) {
        if (name == "class" && name in target) target["class"] += " " + source["class"]
        else if (name == "style" && name in target) target["style"] += ";" + source["style"]
        else target[name] = value
    }
    return target
}

val noAttrs = emptyMap<String, String>()

fun attrsEq(a: Attrs?, b: Attrs?, ignore: String? = null): Boolean {
    return (a.orEmpty() - ignore) == (b.orEmpty() - ignore)
}

//fun updateAttrs(dom: HTMLElement, prev: Attrs?, attrs: Attrs?) {
//    let changed = false
//    if (prev) for (let name in prev) if (!(attrs && name in attrs)) {
//        changed = true
//        if (name == "style") dom.style.cssText = ""
//        else dom.removeAttribute(name)
//    }
//    if (attrs) for (let name in attrs) if (!(prev && prev[name] == attrs[name])) {
//        changed = true
//        if (name == "style") dom.style.cssText = attrs[name]
//        else dom.setAttribute(name, attrs[name])
//    }
//    return changed
//}

//fun getAttrs(dom: HTMLElement) {
//    let attrs = Object . create (null)
//    for (let i = 0; i < dom.attributes.length; i++) {
//        let attr = dom . attributes [i]
//        attrs[attr.name] = attr.value
//    }
//    return attrs
//}
