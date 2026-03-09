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
package com.monkopedia.kodemirror.lang.html

import com.monkopedia.kodemirror.autocomplete.Completion
import com.monkopedia.kodemirror.autocomplete.CompletionContext
import com.monkopedia.kodemirror.autocomplete.CompletionResult
import com.monkopedia.kodemirror.autocomplete.CompletionSource
import com.monkopedia.kodemirror.language.syntaxTree
import com.monkopedia.kodemirror.lezer.common.SyntaxNode
import com.monkopedia.kodemirror.state.DocPos
import com.monkopedia.kodemirror.state.Text
import com.monkopedia.kodemirror.state.endPos

/**
 * Specification for a single HTML tag's attributes. Attribute values
 * of `null` mean free-form text; a list of strings provides
 * predefined value completions.
 */
data class TagSpec(
    val attrs: Map<String, List<String>?>? = null,
    val globalAttrs: Boolean = true,
    val children: List<String>? = null
)

/**
 * Configuration for [htmlCompletionSourceWith].
 */
data class HtmlCompletionConfig(
    val extraTags: Map<String, TagSpec>? = null,
    val extraGlobalAttributes: Map<String, List<String>?>? = null
)

private val Targets = listOf("_blank", "_self", "_top", "_parent")
private val Charsets = listOf("ascii", "utf-8", "utf-16", "latin1")
private val Methods = listOf("get", "post", "put", "delete")
private val Encs = listOf(
    "application/x-www-form-urlencoded",
    "multipart/form-data",
    "text/plain"
)
private val Bool = listOf("true", "false")
private val S = TagSpec()

@Suppress("ktlint:standard:max-line-length")
private val DefaultTags: Map<String, TagSpec> = mapOf(
    "a" to TagSpec(attrs = mapOf("href" to null, "ping" to null, "type" to null, "media" to null, "target" to Targets, "hreflang" to null)),
    "abbr" to S, "address" to S,
    "area" to TagSpec(attrs = mapOf("alt" to null, "coords" to null, "href" to null, "target" to null, "ping" to null, "media" to null, "hreflang" to null, "type" to null, "shape" to listOf("default", "rect", "circle", "poly"))),
    "article" to S, "aside" to S,
    "audio" to TagSpec(attrs = mapOf("src" to null, "mediagroup" to null, "crossorigin" to listOf("anonymous", "use-credentials"), "preload" to listOf("none", "metadata", "auto"), "autoplay" to listOf("autoplay"), "loop" to listOf("loop"), "controls" to listOf("controls"))),
    "b" to S,
    "base" to TagSpec(attrs = mapOf("href" to null, "target" to Targets)),
    "bdi" to S, "bdo" to S,
    "blockquote" to TagSpec(attrs = mapOf("cite" to null)),
    "body" to S, "br" to S,
    "button" to TagSpec(attrs = mapOf("form" to null, "formaction" to null, "name" to null, "value" to null, "autofocus" to listOf("autofocus"), "disabled" to listOf("autofocus"), "formenctype" to Encs, "formmethod" to Methods, "formnovalidate" to listOf("novalidate"), "formtarget" to Targets, "type" to listOf("submit", "reset", "button"))),
    "canvas" to TagSpec(attrs = mapOf("width" to null, "height" to null)),
    "caption" to S, "center" to S, "cite" to S, "code" to S,
    "col" to TagSpec(attrs = mapOf("span" to null)),
    "colgroup" to TagSpec(attrs = mapOf("span" to null)),
    "command" to TagSpec(attrs = mapOf("type" to listOf("command", "checkbox", "radio"), "label" to null, "icon" to null, "radiogroup" to null, "command" to null, "title" to null, "disabled" to listOf("disabled"), "checked" to listOf("checked"))),
    "data" to TagSpec(attrs = mapOf("value" to null)),
    "datagrid" to TagSpec(attrs = mapOf("disabled" to listOf("disabled"), "multiple" to listOf("multiple"))),
    "datalist" to TagSpec(attrs = mapOf("data" to null)),
    "dd" to S,
    "del" to TagSpec(attrs = mapOf("cite" to null, "datetime" to null)),
    "details" to TagSpec(attrs = mapOf("open" to listOf("open"))),
    "dfn" to S, "div" to S, "dl" to S, "dt" to S, "em" to S,
    "embed" to TagSpec(attrs = mapOf("src" to null, "type" to null, "width" to null, "height" to null)),
    "eventsource" to TagSpec(attrs = mapOf("src" to null)),
    "fieldset" to TagSpec(attrs = mapOf("disabled" to listOf("disabled"), "form" to null, "name" to null)),
    "figcaption" to S, "figure" to S, "footer" to S,
    "form" to TagSpec(attrs = mapOf("action" to null, "name" to null, "accept-charset" to Charsets, "autocomplete" to listOf("on", "off"), "enctype" to Encs, "method" to Methods, "novalidate" to listOf("novalidate"), "target" to Targets)),
    "h1" to S, "h2" to S, "h3" to S, "h4" to S, "h5" to S, "h6" to S,
    "head" to TagSpec(children = listOf("title", "base", "link", "style", "meta", "script", "noscript", "command")),
    "header" to S, "hgroup" to S, "hr" to S,
    "html" to TagSpec(attrs = mapOf("manifest" to null)),
    "i" to S,
    "iframe" to TagSpec(attrs = mapOf("src" to null, "srcdoc" to null, "name" to null, "width" to null, "height" to null, "sandbox" to listOf("allow-top-navigation", "allow-same-origin", "allow-forms", "allow-scripts"), "seamless" to listOf("seamless"))),
    "img" to TagSpec(attrs = mapOf("alt" to null, "src" to null, "ismap" to null, "usemap" to null, "width" to null, "height" to null, "crossorigin" to listOf("anonymous", "use-credentials"))),
    "input" to TagSpec(attrs = mapOf("alt" to null, "dirname" to null, "form" to null, "formaction" to null, "height" to null, "list" to null, "max" to null, "maxlength" to null, "min" to null, "name" to null, "pattern" to null, "placeholder" to null, "size" to null, "src" to null, "step" to null, "value" to null, "width" to null, "accept" to listOf("audio/*", "video/*", "image/*"), "autocomplete" to listOf("on", "off"), "autofocus" to listOf("autofocus"), "checked" to listOf("checked"), "disabled" to listOf("disabled"), "formenctype" to Encs, "formmethod" to Methods, "formnovalidate" to listOf("novalidate"), "formtarget" to Targets, "multiple" to listOf("multiple"), "readonly" to listOf("readonly"), "required" to listOf("required"), "type" to listOf("hidden", "text", "search", "tel", "url", "email", "password", "datetime", "date", "month", "week", "time", "datetime-local", "number", "range", "color", "checkbox", "radio", "file", "submit", "image", "reset", "button"))),
    "ins" to TagSpec(attrs = mapOf("cite" to null, "datetime" to null)),
    "kbd" to S,
    "keygen" to TagSpec(attrs = mapOf("challenge" to null, "form" to null, "name" to null, "autofocus" to listOf("autofocus"), "disabled" to listOf("disabled"), "keytype" to listOf("RSA"))),
    "label" to TagSpec(attrs = mapOf("for" to null, "form" to null)),
    "legend" to S,
    "li" to TagSpec(attrs = mapOf("value" to null)),
    "link" to TagSpec(attrs = mapOf("href" to null, "type" to null, "hreflang" to null, "media" to null, "sizes" to listOf("all", "16x16", "16x16 32x32", "16x16 32x32 64x64"))),
    "map" to TagSpec(attrs = mapOf("name" to null)),
    "mark" to S,
    "menu" to TagSpec(attrs = mapOf("label" to null, "type" to listOf("list", "context", "toolbar"))),
    "meta" to TagSpec(attrs = mapOf("content" to null, "charset" to Charsets, "name" to listOf("viewport", "application-name", "author", "description", "generator", "keywords"), "http-equiv" to listOf("content-language", "content-type", "default-style", "refresh"))),
    "meter" to TagSpec(attrs = mapOf("value" to null, "min" to null, "low" to null, "high" to null, "max" to null, "optimum" to null)),
    "nav" to S, "noscript" to S,
    "object" to TagSpec(attrs = mapOf("data" to null, "type" to null, "name" to null, "usemap" to null, "form" to null, "width" to null, "height" to null, "typemustmatch" to listOf("typemustmatch"))),
    "ol" to TagSpec(attrs = mapOf("reversed" to listOf("reversed"), "start" to null, "type" to listOf("1", "a", "A", "i", "I")), children = listOf("li", "script", "template", "ul", "ol")),
    "optgroup" to TagSpec(attrs = mapOf("disabled" to listOf("disabled"), "label" to null)),
    "option" to TagSpec(attrs = mapOf("disabled" to listOf("disabled"), "label" to null, "selected" to listOf("selected"), "value" to null)),
    "output" to TagSpec(attrs = mapOf("for" to null, "form" to null, "name" to null)),
    "p" to S,
    "param" to TagSpec(attrs = mapOf("name" to null, "value" to null)),
    "pre" to S,
    "progress" to TagSpec(attrs = mapOf("value" to null, "max" to null)),
    "q" to TagSpec(attrs = mapOf("cite" to null)),
    "rp" to S, "rt" to S, "ruby" to S, "samp" to S,
    "script" to TagSpec(attrs = mapOf("type" to listOf("text/javascript"), "src" to null, "async" to listOf("async"), "defer" to listOf("defer"), "charset" to Charsets)),
    "section" to S,
    "select" to TagSpec(attrs = mapOf("form" to null, "name" to null, "size" to null, "autofocus" to listOf("autofocus"), "disabled" to listOf("disabled"), "multiple" to listOf("multiple"))),
    "slot" to TagSpec(attrs = mapOf("name" to null)),
    "small" to S,
    "source" to TagSpec(attrs = mapOf("src" to null, "type" to null, "media" to null)),
    "span" to S, "strong" to S,
    "style" to TagSpec(attrs = mapOf("type" to listOf("text/css"), "media" to null, "scoped" to null)),
    "sub" to S, "summary" to S, "sup" to S, "table" to S,
    "tbody" to S,
    "td" to TagSpec(attrs = mapOf("colspan" to null, "rowspan" to null, "headers" to null)),
    "template" to S,
    "textarea" to TagSpec(attrs = mapOf("dirname" to null, "form" to null, "maxlength" to null, "name" to null, "placeholder" to null, "rows" to null, "cols" to null, "autofocus" to listOf("autofocus"), "disabled" to listOf("disabled"), "readonly" to listOf("readonly"), "required" to listOf("required"), "wrap" to listOf("soft", "hard"))),
    "tfoot" to S,
    "th" to TagSpec(attrs = mapOf("colspan" to null, "rowspan" to null, "headers" to null, "scope" to listOf("row", "col", "rowgroup", "colgroup"))),
    "thead" to S,
    "time" to TagSpec(attrs = mapOf("datetime" to null)),
    "title" to S, "tr" to S,
    "track" to TagSpec(attrs = mapOf("src" to null, "label" to null, "default" to null, "kind" to listOf("subtitles", "captions", "descriptions", "chapters", "metadata"), "srclang" to null)),
    "ul" to TagSpec(children = listOf("li", "script", "template", "ul", "ol")),
    "var" to S,
    "video" to TagSpec(attrs = mapOf("src" to null, "poster" to null, "width" to null, "height" to null, "crossorigin" to listOf("anonymous", "use-credentials"), "preload" to listOf("auto", "metadata", "none"), "autoplay" to listOf("autoplay"), "mediagroup" to listOf("movie"), "muted" to listOf("muted"), "controls" to listOf("controls"))),
    "wbr" to S
)

@Suppress("ktlint:standard:max-line-length")
private val DefaultGlobalAttrs: Map<String, List<String>?> = buildMap {
    put("accesskey", null)
    put("class", null)
    put("contenteditable", Bool)
    put("contextmenu", null)
    put("dir", listOf("ltr", "rtl", "auto"))
    put("draggable", listOf("true", "false", "auto"))
    put("dropzone", listOf("copy", "move", "link", "string:", "file:"))
    put("hidden", listOf("hidden"))
    put("id", null)
    put("inert", listOf("inert"))
    put("itemid", null)
    put("itemprop", null)
    put("itemref", null)
    put("itemscope", listOf("itemscope"))
    put("itemtype", null)
    put(
        "lang",
        listOf("ar", "bn", "de", "en-GB", "en-US", "es", "fr", "hi", "id", "ja", "pa", "pt", "ru", "tr", "zh")
    )
    put("spellcheck", Bool)
    put("autocorrect", Bool)
    put("autocapitalize", Bool)
    put("style", null)
    put("tabindex", null)
    put("title", null)
    put("translate", listOf("yes", "no"))
    put(
        "rel",
        listOf("stylesheet", "alternate", "author", "bookmark", "help", "license", "next", "nofollow", "noreferrer", "prefetch", "prev", "search", "tag")
    )
    put(
        "role",
        "alert application article banner button cell checkbox complementary contentinfo dialog document feed figure form grid gridcell heading img list listbox listitem main navigation region row rowgroup search switch tab table tabpanel textbox timer".split(
            " "
        )
    )
    put("aria-activedescendant", null)
    put("aria-atomic", Bool)
    put("aria-autocomplete", listOf("inline", "list", "both", "none"))
    put("aria-busy", Bool)
    put("aria-checked", listOf("true", "false", "mixed", "undefined"))
    put("aria-controls", null)
    put("aria-describedby", null)
    put("aria-disabled", Bool)
    put("aria-dropeffect", null)
    put("aria-expanded", listOf("true", "false", "undefined"))
    put("aria-flowto", null)
    put("aria-grabbed", listOf("true", "false", "undefined"))
    put("aria-haspopup", Bool)
    put("aria-hidden", Bool)
    put("aria-invalid", listOf("true", "false", "grammar", "spelling"))
    put("aria-label", null)
    put("aria-labelledby", null)
    put("aria-level", null)
    put("aria-live", listOf("off", "polite", "assertive"))
    put("aria-multiline", Bool)
    put("aria-multiselectable", Bool)
    put("aria-owns", null)
    put("aria-posinset", null)
    put("aria-pressed", listOf("true", "false", "mixed", "undefined"))
    put("aria-readonly", Bool)
    put("aria-relevant", null)
    put("aria-required", Bool)
    put("aria-selected", listOf("true", "false", "undefined"))
    put("aria-setsize", null)
    put("aria-sort", listOf("ascending", "descending", "none", "other"))
    put("aria-valuemax", null)
    put("aria-valuemin", null)
    put("aria-valuenow", null)
    put("aria-valuetext", null)
    // Event handlers
    for (event in "beforeunload copy cut dragstart dragover dragleave dragenter dragend drag paste focus blur change click load mousedown mouseenter mouseleave mouseup keydown keyup resize scroll unload".split(
        " "
    )) {
        put("on$event", null)
    }
}

/**
 * An HTML completion schema that knows about valid tags, their
 * attributes, and attribute values. Can be extended with extra tags
 * and attributes via [HtmlCompletionConfig].
 */
class HtmlSchema(
    extraTags: Map<String, TagSpec>? = null,
    extraAttrs: Map<String, List<String>?>? = null
) {
    val tags: Map<String, TagSpec> =
        if (extraTags != null) DefaultTags + extraTags else DefaultTags
    val globalAttrs: Map<String, List<String>?> =
        if (extraAttrs != null) DefaultGlobalAttrs + extraAttrs else DefaultGlobalAttrs
    val allTags: List<String> = tags.keys.sorted()
    val globalAttrNames: List<String> = globalAttrs.keys.sorted()

    companion object {
        val default: HtmlSchema = HtmlSchema()
    }
}

private val identifier = Regex("^[:\\-\\.\\w\\u00b7-\\uffff]*$")

private fun elementName(doc: Text, tree: SyntaxNode?, max: Int = doc.length): String {
    if (tree == null) return ""
    val tag = tree.firstChild ?: return ""
    val name = tag.getChild("TagName") ?: return ""
    return doc.sliceString(DocPos(name.from), DocPos(minOf(name.to, max)))
}

private fun findParentElement(tree: SyntaxNode?, skip: Boolean = false): SyntaxNode? {
    var node = tree
    var doSkip = skip
    while (node != null) {
        if (node.name == "Element") {
            if (doSkip) {
                doSkip = false
            } else {
                return node
            }
        }
        node = node.parent
    }
    return null
}

private fun allowedChildren(doc: Text, tree: SyntaxNode?, schema: HtmlSchema): List<String> {
    val parentInfo = schema.tags[elementName(doc, findParentElement(tree))]
    return parentInfo?.children ?: schema.allTags
}

private fun openTags(doc: Text, tree: SyntaxNode): List<String> {
    val open = mutableListOf<String>()
    var parent = findParentElement(tree)
    while (parent != null && !parent.type.isTop) {
        val tagName = elementName(doc, parent)
        if (tagName.isNotEmpty() && parent.lastChild?.name == "CloseTag") break
        if (tagName.isNotEmpty() && !open.contains(tagName) &&
            (tree.name == "EndTag" || tree.from >= (parent.firstChild?.to ?: 0))
        ) {
            open.add(tagName)
        }
        parent = findParentElement(parent.parent)
    }
    return open
}

private fun completeTag(
    state: com.monkopedia.kodemirror.state.EditorState,
    schema: HtmlSchema,
    tree: SyntaxNode,
    from: DocPos,
    to: DocPos
): CompletionResult {
    val end = if (Regex(
            "\\s*>"
        ).containsMatchIn(
            state.doc.sliceString(to, minOf(to + 5, state.doc.endPos))
        )
    ) {
        ""
    } else {
        ">"
    }
    val parent = findParentElement(tree, tree.name == "StartTag" || tree.name == "TagName")
    val tagOptions = allowedChildren(state.doc, parent, schema).map { tagName ->
        Completion(label = tagName, type = "type")
    }
    val closeOptions = openTags(state.doc, tree).mapIndexed { i, tag ->
        Completion(label = "/$tag", apply = "/$tag$end", type = "type", boost = 99 - i)
    }
    return CompletionResult(
        from = from,
        to = to,
        options = tagOptions + closeOptions,
        validFor = Regex("^/?[:\\-\\.\\w\\u00b7-\\uffff]*$")
    )
}

private fun completeCloseTag(
    state: com.monkopedia.kodemirror.state.EditorState,
    tree: SyntaxNode,
    from: DocPos,
    to: DocPos
): CompletionResult {
    val end = if (Regex(
            "\\s*>"
        ).containsMatchIn(
            state.doc.sliceString(to, minOf(to + 5, state.doc.endPos))
        )
    ) {
        ""
    } else {
        ">"
    }
    return CompletionResult(
        from = from,
        to = to,
        options = openTags(state.doc, tree).mapIndexed { i, tag ->
            Completion(label = tag, apply = "$tag$end", type = "type", boost = 99 - i)
        },
        validFor = identifier
    )
}

private fun completeStartTag(
    state: com.monkopedia.kodemirror.state.EditorState,
    schema: HtmlSchema,
    tree: SyntaxNode,
    pos: DocPos
): CompletionResult {
    val options = mutableListOf<Completion>()
    for (tagName in allowedChildren(state.doc, tree, schema)) {
        options.add(Completion(label = "<$tagName", type = "type"))
    }
    var level = 0
    for (open in openTags(state.doc, tree)) {
        options.add(Completion(label = "</$open>", type = "type", boost = 99 - level++))
    }
    return CompletionResult(
        from = pos,
        to = pos,
        options = options,
        validFor = Regex("^</?[:\\-\\.\\w\\u00b7-\\uffff]*$")
    )
}

private fun completeAttrName(
    state: com.monkopedia.kodemirror.state.EditorState,
    schema: HtmlSchema,
    tree: SyntaxNode,
    from: DocPos,
    to: DocPos
): CompletionResult {
    val elt = findParentElement(tree)
    val info = if (elt != null) schema.tags[elementName(state.doc, elt)] else null
    val localAttrs = if (info?.attrs != null) info.attrs.keys.toList() else emptyList()
    val names = if (info != null && !info.globalAttrs) {
        localAttrs
    } else if (localAttrs.isNotEmpty()) {
        localAttrs + schema.globalAttrNames
    } else {
        schema.globalAttrNames
    }
    return CompletionResult(
        from = from,
        to = to,
        options = names.map { Completion(label = it, type = "property") },
        validFor = identifier
    )
}

private fun completeAttrValue(
    state: com.monkopedia.kodemirror.state.EditorState,
    schema: HtmlSchema,
    tree: SyntaxNode,
    from: DocPos,
    to: DocPos
): CompletionResult {
    val nameNode = tree.parent?.getChild("AttributeName")
    val options = mutableListOf<Completion>()
    var token: Regex? = null
    var adjustedFrom = from
    if (nameNode != null) {
        val attrName = state.doc.sliceString(DocPos(nameNode.from), DocPos(nameNode.to))
        var attrs = schema.globalAttrs[attrName]
        if (attrs == null) {
            val elt = findParentElement(tree)
            val info = if (elt != null) schema.tags[elementName(state.doc, elt)] else null
            attrs = info?.attrs?.get(attrName)
        }
        if (attrs != null) {
            val base = state.doc.sliceString(from, to).lowercase()
            var quoteStart = "\""
            var quoteEnd = "\""

            @Suppress("UNUSED_VARIABLE")
            var textBase = base
            if (base.isNotEmpty() && (base[0] == '\'' || base[0] == '"')) {
                token = if (base[0] == '"') Regex("^[^\"]*$") else Regex("^[^']*$")
                quoteStart = ""
                val afterTo =
                    if (to < state.doc.endPos) state.doc.sliceString(to, to + 1) else ""
                quoteEnd = if (afterTo == base[0].toString()) "" else base[0].toString()
                textBase = base.substring(1)
                adjustedFrom += 1
            } else {
                token = Regex("^[^\\s<>='\"]*$")
            }
            for (value in attrs) {
                options.add(
                    Completion(
                        label = value,
                        apply = "$quoteStart$value$quoteEnd",
                        type = "constant"
                    )
                )
            }
        }
    }
    return CompletionResult(
        from = adjustedFrom,
        to = to,
        options = options,
        validFor = token
    )
}

private fun htmlCompletionFor(schema: HtmlSchema, context: CompletionContext): CompletionResult? {
    val state = context.state
    val pos = context.pos
    var tree = syntaxTree(state).resolveInner(pos.value, -1)
    var around = tree.resolve(pos.value)

    // Walk back through error nodes to find the real context
    var scan = pos.value
    while (around.from == tree.from && around.to == tree.to) {
        val before = tree.childBefore(scan) ?: break
        val last = before.lastChild
        if (last == null || !last.type.isError || last.from >= last.to) break
        around = before
        tree = before
        scan = last.from
    }

    return when (tree.name) {
        "TagName" -> {
            if (tree.parent != null && Regex("CloseTag$").containsMatchIn(tree.parent!!.name)) {
                completeCloseTag(state, tree, DocPos(tree.from), pos)
            } else {
                completeTag(state, schema, tree, DocPos(tree.from), pos)
            }
        }
        "StartTag", "IncompleteTag" -> {
            completeTag(state, schema, tree, pos, pos)
        }
        "StartCloseTag", "IncompleteCloseTag" -> {
            completeCloseTag(state, tree, pos, pos)
        }
        "OpenTag", "SelfClosingTag", "AttributeName" -> {
            completeAttrName(
                state,
                schema,
                tree,
                if (tree.name == "AttributeName") DocPos(tree.from) else pos,
                pos
            )
        }
        "Is", "AttributeValue", "UnquotedAttributeValue" -> {
            completeAttrValue(
                state,
                schema,
                tree,
                if (tree.name == "Is") pos else DocPos(tree.from),
                pos
            )
        }
        else -> {
            val isTagContext =
                around.name == "Element" || around.name == "Text" || around.name == "Document"
            if (context.explicit && isTagContext) {
                completeStartTag(state, schema, tree, pos)
            } else {
                null
            }
        }
    }
}

/**
 * HTML tag completion source. Opens and closes tags and attributes
 * in a context-aware way.
 */
val htmlCompletionSource: CompletionSource = { context ->
    htmlCompletionFor(HtmlSchema.default, context)
}

/**
 * Create a completion source for HTML extended with additional tags
 * or attributes.
 */
fun htmlCompletionSourceWith(
    config: HtmlCompletionConfig = HtmlCompletionConfig()
): CompletionSource {
    val schema = if (config.extraTags != null || config.extraGlobalAttributes != null) {
        HtmlSchema(config.extraTags, config.extraGlobalAttributes)
    } else {
        HtmlSchema.default
    }
    return { context -> htmlCompletionFor(schema, context) }
}
