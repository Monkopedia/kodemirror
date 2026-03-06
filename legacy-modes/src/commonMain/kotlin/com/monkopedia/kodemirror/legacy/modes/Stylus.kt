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
package com.monkopedia.kodemirror.legacy.modes

import com.monkopedia.kodemirror.language.IndentContext
import com.monkopedia.kodemirror.language.StreamParser
import com.monkopedia.kodemirror.language.StringStream

// Stylus keyword sets
private val stylusTagKeywords = setOf(
    "a", "abbr", "address", "area", "article", "aside", "audio", "b", "base",
    "bdi", "bdo", "bgsound", "blockquote", "body", "br", "button", "canvas",
    "caption", "cite", "code", "col", "colgroup", "data", "datalist", "dd",
    "del", "details", "dfn", "div", "dl", "dt", "em", "embed", "fieldset",
    "figcaption", "figure", "footer", "form", "h1", "h2", "h3", "h4", "h5",
    "h6", "head", "header", "hgroup", "hr", "html", "i", "iframe", "img",
    "input", "ins", "kbd", "keygen", "label", "legend", "li", "link", "main",
    "map", "mark", "marquee", "menu", "menuitem", "meta", "meter", "nav",
    "nobr", "noframes", "noscript", "object", "ol", "optgroup", "option",
    "output", "p", "param", "pre", "progress", "q", "rp", "rt", "ruby", "s",
    "samp", "script", "section", "select", "small", "source", "span", "strong",
    "style", "sub", "summary", "sup", "table", "tbody", "td", "textarea",
    "tfoot", "th", "thead", "time", "tr", "track", "u", "ul", "var", "video"
)

private val stylusTagVariablesRegexp = Regex("^(a|b|i|s|col|em)\$", RegexOption.IGNORE_CASE)

private val stylusPropertyKeywords = setOf(
    "align-content", "align-items", "align-self", "alignment-adjust",
    "alignment-baseline", "anchor-point", "animation", "animation-delay",
    "animation-direction", "animation-duration", "animation-fill-mode",
    "animation-iteration-count", "animation-name", "animation-play-state",
    "animation-timing-function", "appearance", "azimuth", "backface-visibility",
    "background", "background-attachment", "background-clip", "background-color",
    "background-image", "background-origin", "background-position",
    "background-repeat", "background-size", "baseline-shift", "binding", "bleed",
    "bookmark-label", "bookmark-level", "bookmark-state", "bookmark-target",
    "border", "border-bottom", "border-bottom-color",
    "border-bottom-left-radius", "border-bottom-right-radius",
    "border-bottom-style", "border-bottom-width", "border-collapse",
    "border-color", "border-image", "border-image-outset", "border-image-repeat",
    "border-image-slice", "border-image-source", "border-image-width",
    "border-left", "border-left-color", "border-left-style", "border-left-width",
    "border-radius", "border-right", "border-right-color", "border-right-style",
    "border-right-width", "border-spacing", "border-style", "border-top",
    "border-top-color", "border-top-left-radius", "border-top-right-radius",
    "border-top-style", "border-top-width", "border-width", "bottom",
    "box-decoration-break", "box-shadow", "box-sizing", "break-after",
    "break-before", "break-inside", "caption-side", "clear", "clip", "color",
    "color-profile", "column-count", "column-fill", "column-gap", "column-rule",
    "column-rule-color", "column-rule-style", "column-rule-width", "column-span",
    "column-width", "columns", "content", "counter-increment", "counter-reset",
    "crop", "cue", "cue-after", "cue-before", "cursor", "direction", "display",
    "dominant-baseline", "drop-initial-after-adjust",
    "drop-initial-after-align", "drop-initial-before-adjust",
    "drop-initial-before-align", "drop-initial-size", "drop-initial-value",
    "elevation", "empty-cells", "fit", "fit-position", "flex", "flex-basis",
    "flex-direction", "flex-flow", "flex-grow", "flex-shrink", "flex-wrap",
    "float", "float-offset", "flow-from", "flow-into", "font",
    "font-feature-settings", "font-family", "font-kerning",
    "font-language-override", "font-size", "font-size-adjust", "font-stretch",
    "font-style", "font-synthesis", "font-variant", "font-variant-alternates",
    "font-variant-caps", "font-variant-east-asian", "font-variant-ligatures",
    "font-variant-numeric", "font-variant-position", "font-weight", "grid",
    "grid-area", "grid-auto-columns", "grid-auto-flow", "grid-auto-position",
    "grid-auto-rows", "grid-column", "grid-column-end", "grid-column-start",
    "grid-row", "grid-row-end", "grid-row-start", "grid-template",
    "grid-template-areas", "grid-template-columns", "grid-template-rows",
    "hanging-punctuation", "height", "hyphens", "icon", "image-orientation",
    "image-rendering", "image-resolution", "inline-box-align", "justify-content",
    "left", "letter-spacing", "line-break", "line-height", "line-stacking",
    "line-stacking-ruby", "line-stacking-shift", "line-stacking-strategy",
    "list-style", "list-style-image", "list-style-position", "list-style-type",
    "margin", "margin-bottom", "margin-left", "margin-right", "margin-top",
    "marker-offset", "marks", "marquee-direction", "marquee-loop",
    "marquee-play-count", "marquee-speed", "marquee-style", "max-height",
    "max-width", "min-height", "min-width", "move-to", "nav-down", "nav-index",
    "nav-left", "nav-right", "nav-up", "object-fit", "object-position",
    "opacity", "order", "orphans", "outline", "outline-color", "outline-offset",
    "outline-style", "outline-width", "overflow", "overflow-style",
    "overflow-wrap", "overflow-x", "overflow-y", "padding", "padding-bottom",
    "padding-left", "padding-right", "padding-top", "page", "page-break-after",
    "page-break-before", "page-break-inside", "page-policy", "pause",
    "pause-after", "pause-before", "perspective", "perspective-origin", "pitch",
    "pitch-range", "play-during", "position", "presentation-level",
    "punctuation-trim", "quotes", "region-break-after", "region-break-before",
    "region-break-inside", "region-fragment", "rendering-intent", "resize",
    "rest", "rest-after", "rest-before", "richness", "right", "rotation",
    "rotation-point", "ruby-align", "ruby-overhang", "ruby-position",
    "ruby-span", "shape-image-threshold", "shape-inside", "shape-margin",
    "shape-outside", "size", "speak", "speak-as", "speak-header",
    "speak-numeral", "speak-punctuation", "speech-rate", "stress", "string-set",
    "tab-size", "table-layout", "target", "target-name", "target-new",
    "target-position", "text-align", "text-align-last", "text-decoration",
    "text-decoration-color", "text-decoration-line", "text-decoration-skip",
    "text-decoration-style", "text-emphasis", "text-emphasis-color",
    "text-emphasis-position", "text-emphasis-style", "text-height",
    "text-indent", "text-justify", "text-outline", "text-overflow",
    "text-shadow", "text-size-adjust", "text-space-collapse", "text-transform",
    "text-underline-position", "text-wrap", "top", "transform",
    "transform-origin", "transform-style", "transition", "transition-delay",
    "transition-duration", "transition-property", "transition-timing-function",
    "unicode-bidi", "vertical-align", "visibility", "voice-balance",
    "voice-duration", "voice-family", "voice-pitch", "voice-range", "voice-rate",
    "voice-stress", "voice-volume", "volume", "white-space", "widows", "width",
    "will-change", "word-break", "word-spacing", "word-wrap", "z-index",
    "clip-path", "clip-rule", "mask", "enable-background", "filter",
    "flood-color", "flood-opacity", "lighting-color", "stop-color",
    "stop-opacity", "pointer-events", "color-interpolation",
    "color-interpolation-filters", "color-rendering", "fill", "fill-opacity",
    "fill-rule", "image-rendering", "marker", "marker-end", "marker-mid",
    "marker-start", "shape-rendering", "stroke", "stroke-dasharray",
    "stroke-dashoffset", "stroke-linecap", "stroke-linejoin",
    "stroke-miterlimit", "stroke-opacity", "stroke-width", "text-rendering",
    "baseline-shift", "dominant-baseline", "glyph-orientation-horizontal",
    "glyph-orientation-vertical", "text-anchor", "writing-mode",
    "font-smoothing", "osx-font-smoothing"
)

private val stylusFontProperties = setOf(
    "font-family",
    "src",
    "unicode-range",
    "font-variant",
    "font-feature-settings",
    "font-stretch",
    "font-weight",
    "font-style"
)

private val stylusValueKeywords = setOf(
    "above", "absolute", "activeborder", "additive", "activecaption", "afar",
    "after-white-space", "ahead", "alias", "all", "all-scroll", "alphabetic",
    "alternate", "always", "amharic", "amharic-abegede", "antialiased",
    "appworkspace", "arabic-indic", "armenian", "asterisks", "attr", "auto",
    "avoid", "avoid-column", "avoid-page", "avoid-region", "background",
    "backwards", "baseline", "below", "bidi-override", "binary", "bengali",
    "blink", "block", "block-axis", "bold", "bolder", "border", "border-box",
    "both", "bottom", "break", "break-all", "break-word", "bullets", "button",
    "buttonface", "buttonhighlight", "buttonshadow", "buttontext", "calc",
    "cambodian", "capitalize", "caps-lock-indicator", "caption", "captiontext",
    "caret", "cell", "center", "checkbox", "circle", "cjk-decimal",
    "cjk-earthly-branch", "cjk-heavenly-stem", "cjk-ideographic", "clear",
    "clip", "close-quote", "col-resize", "collapse", "column", "compact",
    "condensed", "conic-gradient", "contain", "content", "contents",
    "content-box", "context-menu", "continuous", "copy", "counter", "counters",
    "cover", "crop", "cross", "crosshair", "currentcolor", "cursive", "cyclic",
    "dashed", "decimal", "decimal-leading-zero", "default", "default-button",
    "destination-atop", "destination-in", "destination-out", "destination-over",
    "devanagari", "disc", "discard", "disclosure-closed", "disclosure-open",
    "document", "dot-dash", "dot-dot-dash", "dotted", "double", "down",
    "e-resize", "ease", "ease-in", "ease-in-out", "ease-out", "element",
    "ellipse", "ellipsis", "embed", "end", "ethiopic", "ethiopic-abegede",
    "ethiopic-abegede-am-et", "ethiopic-abegede-gez", "ethiopic-abegede-ti-er",
    "ethiopic-abegede-ti-et", "ethiopic-halehame-aa-er",
    "ethiopic-halehame-aa-et", "ethiopic-halehame-am-et",
    "ethiopic-halehame-gez", "ethiopic-halehame-om-et",
    "ethiopic-halehame-sid-et", "ethiopic-halehame-so-et",
    "ethiopic-halehame-ti-er", "ethiopic-halehame-ti-et",
    "ethiopic-halehame-tig", "ethiopic-numeric", "ew-resize", "expanded",
    "extends", "extra-condensed", "extra-expanded", "fantasy", "fast", "fill",
    "fixed", "flat", "flex", "footnotes", "forwards", "from",
    "geometricPrecision", "georgian", "graytext", "groove", "gujarati",
    "gurmukhi", "hand", "hangul", "hangul-consonant", "hebrew", "help",
    "hidden", "hide", "high", "higher", "highlight", "highlighttext",
    "hiragana", "hiragana-iroha", "horizontal", "hsl", "hsla", "icon",
    "ignore", "inactiveborder", "inactivecaption", "inactivecaptiontext",
    "infinite", "infobackground", "infotext", "inherit", "initial", "inline",
    "inline-axis", "inline-block", "inline-flex", "inline-table", "inset",
    "inside", "intrinsic", "invert", "italic", "japanese-formal",
    "japanese-informal", "justify", "kannada", "katakana", "katakana-iroha",
    "keep-all", "khmer", "korean-hangul-formal", "korean-hanja-formal",
    "korean-hanja-informal", "landscape", "lao", "large", "larger", "left",
    "level", "lighter", "line-through", "linear", "linear-gradient", "lines",
    "list-item", "listbox", "listitem", "local", "logical", "loud", "lower",
    "lower-alpha", "lower-armenian", "lower-greek", "lower-hexadecimal",
    "lower-latin", "lower-norwegian", "lower-roman", "lowercase", "ltr",
    "malayalam", "match", "matrix", "matrix3d", "media-play-button",
    "media-slider", "media-sliderthumb", "media-volume-slider",
    "media-volume-sliderthumb", "medium", "menu", "menulist", "menulist-button",
    "menutext", "message-box", "middle", "min-intrinsic", "mix", "mongolian",
    "monospace", "move", "multiple", "myanmar", "n-resize", "narrower",
    "ne-resize", "nesw-resize", "no-close-quote", "no-drop", "no-open-quote",
    "no-repeat", "none", "normal", "not-allowed", "nowrap", "ns-resize",
    "numbers", "numeric", "nw-resize", "nwse-resize", "oblique", "octal",
    "open-quote", "optimizeLegibility", "optimizeSpeed", "oriya", "oromo",
    "outset", "outside", "outside-shape", "overlay", "overline", "padding",
    "padding-box", "painted", "page", "paused", "persian", "perspective",
    "plus-darker", "plus-lighter", "pointer", "polygon", "portrait", "pre",
    "pre-line", "pre-wrap", "preserve-3d", "progress", "push-button",
    "radial-gradient", "radio", "read-only", "read-write",
    "read-write-plaintext-only", "rectangle", "region", "relative", "repeat",
    "repeating-linear-gradient", "repeating-radial-gradient",
    "repeating-conic-gradient", "repeat-x", "repeat-y", "reset", "reverse",
    "rgb", "rgba", "ridge", "right", "rotate", "rotate3d", "rotateX",
    "rotateY", "rotateZ", "round", "row-resize", "rtl", "run-in", "running",
    "s-resize", "sans-serif", "scale", "scale3d", "scaleX", "scaleY", "scaleZ",
    "scroll", "scrollbar", "scroll-position", "se-resize", "searchfield",
    "searchfield-cancel-button", "searchfield-decoration",
    "searchfield-results-button", "searchfield-results-decoration",
    "semi-condensed", "semi-expanded", "separate", "serif", "show", "sidama",
    "simp-chinese-formal", "simp-chinese-informal", "single", "skew", "skewX",
    "skewY", "skip-white-space", "slide", "slider-horizontal",
    "slider-vertical", "sliderthumb-horizontal", "sliderthumb-vertical", "slow",
    "small", "small-caps", "small-caption", "smaller", "solid", "somali",
    "source-atop", "source-in", "source-out", "source-over", "space",
    "spell-out", "square", "square-button", "standard", "start", "static",
    "status-bar", "stretch", "stroke", "sub", "subpixel-antialiased", "super",
    "sw-resize", "symbolic", "symbols", "table", "table-caption", "table-cell",
    "table-column", "table-column-group", "table-footer-group",
    "table-header-group", "table-row", "table-row-group", "tamil", "telugu",
    "text", "text-bottom", "text-top", "textarea", "textfield", "thai", "thick",
    "thin", "threeddarkshadow", "threedface", "threedhighlight",
    "threedlightshadow", "threedshadow", "tibetan", "tigre", "tigrinya-er",
    "tigrinya-er-abegede", "tigrinya-et", "tigrinya-et-abegede", "to", "top",
    "trad-chinese-formal", "trad-chinese-informal", "translate", "translate3d",
    "translateX", "translateY", "translateZ", "transparent", "ultra-condensed",
    "ultra-expanded", "underline", "up", "upper-alpha", "upper-armenian",
    "upper-greek", "upper-hexadecimal", "upper-latin", "upper-norwegian",
    "upper-roman", "uppercase", "urdu", "url", "var", "vertical",
    "vertical-text", "visible", "visibleFill", "visiblePainted",
    "visibleStroke", "visual", "w-resize", "wait", "wave", "wider", "window",
    "windowframe", "windowtext", "words", "x-large", "x-small", "xor",
    "xx-large", "xx-small", "bicubic", "optimizespeed", "grayscale", "row",
    "row-reverse", "wrap", "wrap-reverse", "column-reverse", "flex-start",
    "flex-end", "space-between", "space-around", "unset"
)

private val stylusColorKeywords = setOf(
    "aliceblue", "antiquewhite", "aqua", "aquamarine", "azure", "beige",
    "bisque", "black", "blanchedalmond", "blue", "blueviolet", "brown",
    "burlywood", "cadetblue", "chartreuse", "chocolate", "coral",
    "cornflowerblue", "cornsilk", "crimson", "cyan", "darkblue", "darkcyan",
    "darkgoldenrod", "darkgray", "darkgreen", "darkkhaki", "darkmagenta",
    "darkolivegreen", "darkorange", "darkorchid", "darkred", "darksalmon",
    "darkseagreen", "darkslateblue", "darkslategray", "darkturquoise",
    "darkviolet", "deeppink", "deepskyblue", "dimgray", "dodgerblue",
    "firebrick", "floralwhite", "forestgreen", "fuchsia", "gainsboro",
    "ghostwhite", "gold", "goldenrod", "gray", "grey", "green", "greenyellow",
    "honeydew", "hotpink", "indianred", "indigo", "ivory", "khaki", "lavender",
    "lavenderblush", "lawngreen", "lemonchiffon", "lightblue", "lightcoral",
    "lightcyan", "lightgoldenrodyellow", "lightgray", "lightgreen", "lightpink",
    "lightsalmon", "lightseagreen", "lightskyblue", "lightslategray",
    "lightsteelblue", "lightyellow", "lime", "limegreen", "linen", "magenta",
    "maroon", "mediumaquamarine", "mediumblue", "mediumorchid", "mediumpurple",
    "mediumseagreen", "mediumslateblue", "mediumspringgreen",
    "mediumturquoise", "mediumvioletred", "midnightblue", "mintcream",
    "mistyrose", "moccasin", "navajowhite", "navy", "oldlace", "olive",
    "olivedrab", "orange", "orangered", "orchid", "palegoldenrod", "palegreen",
    "paleturquoise", "palevioletred", "papayawhip", "peachpuff", "peru", "pink",
    "plum", "powderblue", "purple", "rebeccapurple", "red", "rosybrown",
    "royalblue", "saddlebrown", "salmon", "sandybrown", "seagreen", "seashell",
    "sienna", "silver", "skyblue", "slateblue", "slategray", "snow",
    "springgreen", "steelblue", "tan", "teal", "thistle", "tomato", "turquoise",
    "violet", "wheat", "white", "whitesmoke", "yellow", "yellowgreen"
)

private val stylusBlockKeywords = setOf(
    "for",
    "if",
    "else",
    "unless",
    "from",
    "to"
)

private val stylusCommonAtoms = setOf(
    "null", "true", "false", "href", "title", "type", "not-allowed",
    "readonly", "disabled"
)

private val stylusOperatorsRegexp =
    Regex("^\\s*([.]{2,3}|&&|\\|\\||\\*\\*|[?!=:]?=|[-+*/%<>]=?|\\?:|~)")
private val stylusVendorPrefixesRegexp =
    Regex("^-(moz|ms|o|webkit)-", RegexOption.IGNORE_CASE)

data class StylusContext(
    val type: String,
    val indent: Int,
    val prev: StylusContext?,
    val line: StylusLine = StylusLine()
)

data class StylusLine(
    var firstWord: String = "",
    var indent: Int = 0
)

class StylusState(
    // 0=null(base), 1=cComment, 2=string, 3=parenthesized
    var tokenize: Int = 0,
    var stringQuote: String = "",
    var state: String = "block",
    var context: StylusContext = StylusContext("block", 0, null)
)

private fun stylusEndOfLine(stream: StringStream): Boolean {
    return stream.eol() || stream.match(Regex("^\\s*\$"), false) != null
}

private fun stylusStartOfLine(stream: StringStream): Boolean {
    return stream.sol() ||
        stream.string.matches(Regex("^\\s*" + Regex.escape(stream.current())))
}

private fun stylusFirstWordOfLine(input: String): String {
    val re = Regex("^\\s*[-_]*[a-z0-9]+[\\w-]*", RegexOption.IGNORE_CASE)
    val result = re.find(input)
    return result?.value?.trimStart() ?: ""
}

private fun stylusWordIsTag(word: String): Boolean {
    return word.lowercase() in stylusTagKeywords
}

private fun stylusWordIsProperty(word: String): Boolean {
    val lc = word.lowercase()
    return lc in stylusPropertyKeywords || lc in stylusFontProperties
}

private fun stylusWordIsBlock(word: String): Boolean {
    return word.lowercase() in stylusBlockKeywords
}

@Suppress("CyclomaticComplexMethod", "LongMethod", "ReturnCount", "NestedBlockDepth")
private fun stylusWordAsValue(word: String): String {
    val wordLC = word.lowercase()
    return when {
        stylusWordIsTag(word) -> "tag"
        stylusWordIsBlock(word) -> "block-keyword"
        stylusWordIsProperty(word) -> "property"
        wordLC in stylusValueKeywords || wordLC in stylusCommonAtoms -> "atom"
        wordLC == "return" || wordLC in stylusColorKeywords -> "keyword"
        word.matches(Regex("^[A-Z].*")) -> "string"
        else -> "variable"
    }
}

private val stylusFirstWordRegex = Regex(
    "(^[\\w-]+\\s*=\\s*$)" +
        "|(^\\s*[\\w-]+\\s*=\\s*[\\w-])" +
        "|(^\\s*(\\.?|#|@|\\\$|&|\\[|\\d|\\+|::?|\\{|>|~|/)?" +
        "\\s*[\\w-]*([a-z0-9-]|\\*|/\\*)(\\(|,)?)",
    RegexOption.IGNORE_CASE
)

@Suppress("CyclomaticComplexMethod", "LongMethod", "ReturnCount", "NestedBlockDepth")
private fun stylusTokenBase(stream: StringStream, state: StylusState): List<String?> {
    val firstWordMatch = stream.string.matches(stylusFirstWordRegex)
    state.context = state.context.copy(
        line = state.context.line.copy(
            firstWord = if (firstWordMatch) {
                val m = stylusFirstWordRegex.find(stream.string)
                m?.value?.trimStart() ?: ""
            } else {
                ""
            },
            indent = stream.indentation()
        )
    )
    val ch = stream.peek() ?: run {
        stream.next()
        return listOf(null, null)
    }

    // Line comment
    if (stream.match("//")) {
        stream.skipToEnd()
        return listOf("comment", "comment")
    }
    // Block comment
    if (stream.match("/*")) {
        state.tokenize = 1
        return stylusTokenCComment(stream, state)
    }
    // String
    if (ch == "\"" || ch == "'") {
        stream.next()
        state.stringQuote = ch
        state.tokenize = 2
        return stylusTokenString(stream, state)
    }
    // Def
    if (ch == "@") {
        stream.next()
        stream.eatWhile(Regex("[\\w\\\\-]"))
        return listOf("def", stream.current())
    }
    // ID selector or Hex color
    if (ch == "#") {
        stream.next()
        if (stream.match(
                Regex("^[0-9a-f]{3}([0-9a-f]([0-9a-f]{2}){0,2})?\\b(?!-)", RegexOption.IGNORE_CASE)
            ) != null
        ) {
            return listOf("atom", "atom")
        }
        if (stream.match(Regex("^[a-z][\\w-]*", RegexOption.IGNORE_CASE)) != null) {
            return listOf("builtin", "hash")
        }
    }
    // Vendor prefixes
    if (stream.match(stylusVendorPrefixesRegexp) != null) {
        return listOf("meta", "vendor-prefixes")
    }
    // Numbers
    if (stream.match(Regex("^-?[0-9]?\\.?[0-9]")) != null) {
        stream.eatWhile(Regex("[a-z%]", RegexOption.IGNORE_CASE))
        return listOf("number", "unit")
    }
    // !important|optional
    if (ch == "!") {
        stream.next()
        val kw = stream.match(Regex("^(important|optional)", RegexOption.IGNORE_CASE))
        return listOf(if (kw != null) "keyword" else "operator", "important")
    }
    // Class
    if (ch == "." && stream.match(Regex("^\\.([a-z][\\w-]*)", RegexOption.IGNORE_CASE)) != null) {
        return listOf("qualifier", "qualifier")
    }
    // Mixins / Functions
    if (stream.match(Regex("^[a-z][\\w-]*\\(", RegexOption.IGNORE_CASE)) != null) {
        stream.backUp(1)
        return listOf("keyword", "mixin")
    }
    // Block mixins
    if (stream.match(Regex("^(\\+|-)[a-z][\\w-]*\\(", RegexOption.IGNORE_CASE)) != null) {
        stream.backUp(1)
        return listOf("keyword", "block-mixin")
    }
    // Parent Reference BEM naming
    if (stream.string.matches(Regex("^\\s*&.*")) &&
        stream.match(Regex("^[-_]+[a-z][\\w-]*")) != null
    ) {
        return listOf("qualifier", "qualifier")
    }
    // Root Reference & Parent Reference
    if (stream.match(Regex("^(/|&)(-|_|:|\\.|#|[a-z])")) != null) {
        stream.backUp(1)
        return listOf("variableName.special", "reference")
    }
    if (stream.match(Regex("^&{1}\\s*\$")) != null) {
        return listOf("variableName.special", "reference")
    }
    // Word
    if (stream.match(Regex("^\\\$?[-_]*[a-z0-9]+[\\w-]*", RegexOption.IGNORE_CASE)) != null) {
        if (stream.match(Regex("^(\\.|\\[)[\\w-'\"\\]]+", RegexOption.IGNORE_CASE), false) != null
        ) {
            if (!stylusWordIsTag(stream.current())) {
                stream.match(".")
                return listOf("variable", "variable-name")
            }
        }
        return listOf("variable", "word")
    }
    // Operators
    if (stream.match(stylusOperatorsRegexp) != null) {
        return listOf("operator", stream.current())
    }
    // Delimiters
    if (Regex("[:;,{}\\[\\]()]").containsMatchIn(ch)) {
        stream.next()
        return listOf(null, ch)
    }
    // Non-detected items
    stream.next()
    return listOf(null, null)
}

private fun stylusTokenCComment(stream: StringStream, state: StylusState): List<String?> {
    var maybeEnd = false
    while (true) {
        val ch = stream.next() ?: break
        if (maybeEnd && ch == "/") {
            state.tokenize = 0
            break
        }
        maybeEnd = ch == "*"
    }
    return listOf("comment", "comment")
}

private fun stylusTokenString(stream: StringStream, state: StylusState): List<String?> {
    var escaped = false
    while (true) {
        val ch = stream.next() ?: break
        if (ch == state.stringQuote && !escaped) {
            if (state.stringQuote == ")") stream.backUp(1)
            break
        }
        escaped = !escaped && ch == "\\"
    }
    if (stream.current().lastOrNull()?.toString() == state.stringQuote ||
        (!escaped && state.stringQuote != ")")
    ) {
        state.tokenize = 0
    }
    return listOf("string", "string")
}

private fun stylusPushContext(
    state: StylusState,
    stream: StringStream,
    ctxType: String,
    indent: Int = stream.indentUnit
): String {
    state.context = StylusContext(
        type = ctxType,
        indent = stream.indentation() + indent,
        prev = state.context
    )
    return ctxType
}

private fun stylusPopContext(
    state: StylusState,
    stream: StringStream,
    currentIndent: Boolean = false
): String {
    val contextIndent = state.context.indent - stream.indentUnit
    state.context = state.context.prev ?: state.context
    if (currentIndent) {
        state.context = state.context.copy(indent = contextIndent)
    }
    return state.context.type
}

@Suppress("CyclomaticComplexMethod", "LongMethod", "ReturnCount", "NestedBlockDepth")
private fun stylusStateDispatch(
    type: String,
    stream: StringStream,
    state: StylusState,
    override: String
): Pair<String, String> {
    var currentOverride = override
    val newState = when (state.context.type) {
        "block" -> {
            stylusBlockState(type, stream, state, currentOverride).also {
                currentOverride = it.second
            }.first
        }
        "parens" -> {
            stylusParensState(type, stream, state, currentOverride).also {
                currentOverride = it.second
            }.first
        }
        "interpolation" -> {
            stylusInterpolationState(type, stream, state, currentOverride).also {
                currentOverride = it.second
            }.first
        }
        "pseudo" -> {
            stylusPseudoState(type, stream, state, currentOverride).also {
                currentOverride = it.second
            }.first
        }
        "atBlock" -> {
            stylusAtBlockState(type, stream, state, currentOverride).also {
                currentOverride = it.second
            }.first
        }
        "atBlock_parens" -> {
            stylusAtBlockParensState(type, stream, state, currentOverride).also {
                currentOverride = it.second
            }.first
        }
        "keyframes" -> {
            stylusKeyframesState(type, stream, state, currentOverride).also {
                currentOverride = it.second
            }.first
        }
        "vendorPrefixes" -> {
            if (type == "word") {
                currentOverride = "property"
                stylusPushContext(state, stream, "block", 0)
            } else {
                stylusPopContext(state, stream)
            }
        }
        "extend" -> {
            if (type == "[" || type == "=") {
                "extend"
            } else if (type == "]") {
                stylusPopContext(state, stream)
            } else {
                if (type == "word") currentOverride = stylusWordAsValue(stream.current())
                "extend"
            }
        }
        "variableName" -> {
            if (type == "string" || type == "[" || type == "]" ||
                stream.current().matches(Regex("^[.\$].*"))
            ) {
                if (stream.current().matches(Regex("^\\.[\\w-]+", RegexOption.IGNORE_CASE))) {
                    currentOverride = "variable"
                }
                "variableName"
            } else {
                state.context = state.context.prev ?: state.context
                val r = stylusStateDispatch(type, stream, state, currentOverride)
                currentOverride = r.second
                r.first
            }
        }
        else -> state.context.type
    }
    return Pair(newState, currentOverride)
}

@Suppress("CyclomaticComplexMethod", "LongMethod", "ReturnCount", "NestedBlockDepth")
private fun stylusBlockState(
    type: String,
    stream: StringStream,
    state: StylusState,
    initialOverride: String
): Pair<String, String> {
    var ov = initialOverride

    if ((type == "comment" && stylusStartOfLine(stream)) ||
        (type == "," && stylusEndOfLine(stream)) ||
        type == "mixin"
    ) {
        return Pair(stylusPushContext(state, stream, "block", 0), ov)
    }
    val wordAfterBrace = Regex("^\\s*\\\$?[\\w-]+", RegexOption.IGNORE_CASE)
    if (type == "{" && stream.match(wordAfterBrace, false) != null) {
        return Pair(stylusPushContext(state, stream, "interpolation"), ov)
    }
    if (stylusEndOfLine(stream) && type == "]") {
        if (!Regex("^\\s*(\\.?|#|:|\\[|\\*|&)").containsMatchIn(stream.string) &&
            !stylusWordIsTag(stylusFirstWordOfLine(stream.string))
        ) {
            return Pair(stylusPushContext(state, stream, "block", 0), ov)
        }
    }
    val typeIsBlock = (
        stylusEndOfLine(stream) &&
            (type == "{" || type == "]" || type == "hash" || type == "qualifier")
        ) ||
        type == "block-mixin"
    if (typeIsBlock) {
        return Pair(stylusPushContext(state, stream, "block"), ov)
    }
    if (type == "}" && stylusEndOfLine(stream)) {
        return Pair(stylusPushContext(state, stream, "block", 0), ov)
    }
    if (type == "variable-name") {
        return if (stream.string.matches(Regex("^\\s?\\\$[\\w-\\.\\[\\]'\"]+\$")) ||
            stylusWordIsBlock(stylusFirstWordOfLine(stream.string))
        ) {
            Pair(stylusPushContext(state, stream, "variableName"), ov)
        } else {
            Pair(stylusPushContext(state, stream, "variableName", 0), ov)
        }
    }
    if (type == "=") {
        return if (!stylusEndOfLine(stream) &&
            !stylusWordIsBlock(stylusFirstWordOfLine(stream.string))
        ) {
            Pair(stylusPushContext(state, stream, "block", 0), ov)
        } else {
            Pair(stylusPushContext(state, stream, "block"), ov)
        }
    }
    if (type == "*") {
        if (stylusEndOfLine(stream) ||
            stream.match(Regex("\\s*(,|\\.?|#|\\[|:|\\{)"), false) != null
        ) {
            ov = "tag"
            return Pair(stylusPushContext(state, stream, "block"), ov)
        }
    }
    if (type == ":" && stream.match(Regex("^[a-z-]+"), false) != null) {
        return Pair(stylusPushContext(state, stream, "pseudo"), ov)
    }
    if (Regex("@(font-face|media|supports|(-moz-)?document)").containsMatchIn(type)) {
        return Pair(
            stylusPushContext(
                state,
                stream,
                if (stylusEndOfLine(stream)) "block" else "atBlock"
            ),
            ov
        )
    }
    if (Regex("@(-(moz|ms|o|webkit)-)?keyframes\$").containsMatchIn(type)) {
        return Pair(stylusPushContext(state, stream, "keyframes"), ov)
    }
    if (Regex("@extends?").containsMatchIn(type)) {
        return Pair(stylusPushContext(state, stream, "extend", 0), ov)
    }
    if (type.isNotEmpty() && type[0] == '@') {
        if (stream.indentation() > 0 && stylusWordIsProperty(stream.current().substring(1))) {
            ov = "variable"
            return Pair("block", ov)
        }
        if (Regex("@(import|require|charset)").containsMatchIn(type)) {
            return Pair(stylusPushContext(state, stream, "block", 0), ov)
        }
        return Pair(stylusPushContext(state, stream, "block"), ov)
    }
    if (type == "reference" && stylusEndOfLine(stream)) {
        return Pair(stylusPushContext(state, stream, "block"), ov)
    }
    if (type == "(") {
        return Pair(stylusPushContext(state, stream, "parens"), ov)
    }
    if (type == "vendor-prefixes") {
        return Pair(stylusPushContext(state, stream, "vendorPrefixes"), ov)
    }
    if (type == "word") {
        val word = stream.current()
        ov = stylusWordAsValue(word)

        if (ov == "property") {
            return if (stylusStartOfLine(stream)) {
                Pair(stylusPushContext(state, stream, "block", 0), ov)
            } else {
                ov = "atom"
                Pair("block", ov)
            }
        }

        if (ov == "tag") {
            if (Regex("embed|menu|pre|progress|sub|table").containsMatchIn(word)) {
                if (stylusWordIsProperty(stylusFirstWordOfLine(stream.string))) {
                    ov = "atom"
                    return Pair("block", ov)
                }
            }
            if (stylusTagVariablesRegexp.containsMatchIn(word)) {
                if ((stylusStartOfLine(stream) && stream.string.contains("=")) ||
                    (
                        !stylusStartOfLine(stream) &&
                            !Regex("^(\\s*\\.|#|&|\\[|/|>|\\*)").containsMatchIn(stream.string) &&
                            !stylusWordIsTag(stylusFirstWordOfLine(stream.string))
                        )
                ) {
                    ov = "variable"
                    return if (stylusWordIsBlock(stylusFirstWordOfLine(stream.string))) {
                        Pair("block", ov)
                    } else {
                        Pair(stylusPushContext(state, stream, "block", 0), ov)
                    }
                }
            }
            if (stylusEndOfLine(stream)) {
                return Pair(stylusPushContext(state, stream, "block"), ov)
            }
        }
        if (ov == "block-keyword") {
            ov = "keyword"
            return Pair(stylusPushContext(state, stream, "block"), ov)
        }
        if (word == "return") {
            return Pair(stylusPushContext(state, stream, "block", 0), ov)
        }
        if (ov == "variable" && stream.string.matches(Regex("^\\s?\\\$[\\w-\\.\\[\\]'\"]+\$"))) {
            return Pair(stylusPushContext(state, stream, "block"), ov)
        }
    }
    return Pair(state.context.type, ov)
}

private fun stylusParensState(
    type: String,
    stream: StringStream,
    state: StylusState,
    initialOverride: String
): Pair<String, String> {
    var ov = initialOverride
    if (type == "(") return Pair(stylusPushContext(state, stream, "parens"), ov)
    if (type == ")") {
        if (state.context.prev?.type == "parens") {
            return Pair(stylusPopContext(state, stream), ov)
        }
        // After closing ) decide on block or not
        if (stylusEndOfLine(stream)) {
            return Pair(stylusPushContext(state, stream, "block"), ov)
        }
        return Pair(stylusPushContext(state, stream, "block", 0), ov)
    }
    if (type.isNotEmpty() && type[0] == '@' && stylusWordIsProperty(stream.current().substring(1))
    ) {
        ov = "variable"
    }
    if (type == "word") {
        val word = stream.current()
        ov = stylusWordAsValue(word)
        if (ov == "tag" && stylusTagVariablesRegexp.containsMatchIn(word)) ov = "variable"
        if (ov == "property" || word == "to") ov = "atom"
    }
    if (type == "variable-name") {
        return Pair(stylusPushContext(state, stream, "variableName"), ov)
    }
    if (type == ":" && stream.match(Regex("^[a-z-]+"), false) != null) {
        return Pair(stylusPushContext(state, stream, "pseudo"), ov)
    }
    return Pair(state.context.type, ov)
}

private fun stylusInterpolationState(
    type: String,
    stream: StringStream,
    state: StylusState,
    initialOverride: String
): Pair<String, String> {
    var ov = initialOverride
    if (type == "{") {
        stylusPopContext(state, stream)
        return Pair(stylusPushContext(state, stream, "block"), ov)
    }
    if (type == "}") {
        if (Regex("^\\s*(\\.?|#|:|\\[|\\*|&|>|~|\\+|/)", RegexOption.IGNORE_CASE)
                .containsMatchIn(stream.string) ||
            (
                Regex("^\\s*[a-z]", RegexOption.IGNORE_CASE).containsMatchIn(stream.string) &&
                    stylusWordIsTag(stylusFirstWordOfLine(stream.string))
                )
        ) {
            return Pair(stylusPushContext(state, stream, "block"), ov)
        }
        if (!stream.string.matches(Regex("^(\\{|\\s*&).*")) ||
            stream.match(Regex("\\s*[\\w-]"), false) != null
        ) {
            return Pair(stylusPushContext(state, stream, "block", 0), ov)
        }
        return Pair(stylusPushContext(state, stream, "block"), ov)
    }
    if (type == "variable-name") {
        return Pair(stylusPushContext(state, stream, "variableName", 0), ov)
    }
    if (type == "word") {
        ov = stylusWordAsValue(stream.current())
        if (ov == "tag") ov = "atom"
    }
    return Pair(state.context.type, ov)
}

private fun stylusPseudoState(
    type: String,
    stream: StringStream,
    state: StylusState,
    initialOverride: String
): Pair<String, String> {
    var ov = initialOverride
    if (!stylusWordIsProperty(stylusFirstWordOfLine(stream.string))) {
        stream.match(Regex("^[a-z-]+"))
        ov = "variableName.special"
        return if (stylusEndOfLine(stream)) {
            Pair(stylusPushContext(state, stream, "block"), ov)
        } else {
            Pair(stylusPopContext(state, stream), ov)
        }
    }
    // popAndPass
    state.context = state.context.prev ?: state.context
    val r = stylusStateDispatch(type, stream, state, ov)
    return r
}

private fun stylusAtBlockState(
    type: String,
    stream: StringStream,
    state: StylusState,
    initialOverride: String
): Pair<String, String> {
    var ov = initialOverride
    if (type == "(") return Pair(stylusPushContext(state, stream, "atBlock_parens"), ov)
    val typeIsBlock = (
        stylusEndOfLine(stream) &&
            (type == "{" || type == "]" || type == "hash" || type == "qualifier")
        ) ||
        type == "block-mixin"
    if (typeIsBlock) return Pair(stylusPushContext(state, stream, "block"), ov)
    val wordAfterBrace = Regex("^\\s*\\\$?[\\w-]+", RegexOption.IGNORE_CASE)
    if (type == "{" && stream.match(wordAfterBrace, false) != null) {
        return Pair(stylusPushContext(state, stream, "interpolation"), ov)
    }
    if (type == "word") {
        val word = stream.current().lowercase()
        if (Regex("^(only|not|and|or)\$").containsMatchIn(word)) {
            ov = "keyword"
        } else {
            ov = stylusWordAsValue(stream.current())
        }
        if (ov == "tag" && stylusEndOfLine(stream)) {
            return Pair(stylusPushContext(state, stream, "block"), ov)
        }
    }
    if (type == "operator" && Regex("^(not|and|or)\$").containsMatchIn(stream.current())) {
        ov = "keyword"
    }
    return Pair(state.context.type, ov)
}

private fun stylusAtBlockParensState(
    type: String,
    stream: StringStream,
    state: StylusState,
    initialOverride: String
): Pair<String, String> {
    var ov = initialOverride
    if (type == "{" || type == "}") return Pair(state.context.type, ov)
    if (type == ")") {
        return if (stylusEndOfLine(stream)) {
            Pair(stylusPushContext(state, stream, "block"), ov)
        } else {
            Pair(stylusPushContext(state, stream, "atBlock"), ov)
        }
    }
    if (type == "word") {
        val word = stream.current().lowercase()
        ov = stylusWordAsValue(word)
        if (Regex("^(max|min)").containsMatchIn(word)) ov = "property"
        if (ov == "tag") {
            ov = if (stylusTagVariablesRegexp.containsMatchIn(word)) "variable" else "atom"
        }
        return Pair(state.context.type, ov)
    }
    return stylusAtBlockState(type, stream, state, ov)
}

private fun stylusKeyframesState(
    type: String,
    stream: StringStream,
    state: StylusState,
    initialOverride: String
): Pair<String, String> {
    var ov = initialOverride
    if (type == "{") return Pair(stylusPushContext(state, stream, "keyframes"), ov)
    if (type == "}") {
        return if (stylusStartOfLine(stream)) {
            Pair(stylusPopContext(state, stream, true), ov)
        } else {
            Pair(stylusPushContext(state, stream, "keyframes"), ov)
        }
    }
    if (type == "word") {
        ov = stylusWordAsValue(stream.current())
        if (ov == "block-keyword") {
            ov = "keyword"
            return Pair(stylusPushContext(state, stream, "keyframes"), ov)
        }
    }
    if (Regex("@(font-face|media|supports|(-moz-)?document)").containsMatchIn(type)) {
        return Pair(
            stylusPushContext(
                state,
                stream,
                if (stylusEndOfLine(stream)) "block" else "atBlock"
            ),
            ov
        )
    }
    if (type == "mixin") {
        return Pair(stylusPushContext(state, stream, "block", 0), ov)
    }
    return Pair(state.context.type, ov)
}

/** Stream parser for Stylus. */
val stylus: StreamParser<StylusState> = object : StreamParser<StylusState> {
    override val name: String get() = "stylus"

    override fun startState(indentUnit: Int): StylusState {
        return StylusState()
    }

    override fun copyState(state: StylusState): StylusState {
        return StylusState(
            tokenize = state.tokenize,
            stringQuote = state.stringQuote,
            state = state.state,
            context = state.context
        )
    }

    @Suppress("CyclomaticComplexMethod", "ReturnCount")
    override fun token(stream: StringStream, state: StylusState): String? {
        if (state.tokenize == 0 && stream.eatSpace()) return null

        val result: List<String?> = when (state.tokenize) {
            1 -> stylusTokenCComment(stream, state)
            2 -> stylusTokenString(stream, state)
            3 -> {
                // parenthesized
                stream.next()
                if (stream.match(Regex("^\\s*[\"')]"), false) == null) {
                    state.stringQuote = ")"
                    state.tokenize = 2
                } else {
                    state.tokenize = 0
                }
                listOf(null, "(")
            }
            else -> stylusTokenBase(stream, state)
        }

        var style = result[0]
        var type = result.getOrNull(1) ?: ""
        var override = style ?: ""

        if (type.isNotEmpty()) {
            val (newState, newOverride) = stylusStateDispatch(type, stream, state, override)
            state.state = newState
            override = newOverride
        }

        return if (override.isEmpty()) null else override
    }

    override fun indent(state: StylusState, textAfter: String, context: IndentContext): Int? {
        val cx = state.context
        val ch = textAfter.firstOrNull()?.toString() ?: ""
        var indent = cx.indent

        if (cx.prev != null &&
            (
                (
                    ch == "}" && (
                        cx.type == "block" || cx.type == "atBlock" ||
                            cx.type == "keyframes"
                        )
                    ) ||
                    (ch == ")" && (cx.type == "parens" || cx.type == "atBlock_parens")) ||
                    (ch == "{" && cx.type == "at")
                )
        ) {
            indent = cx.indent - context.unit
        }
        return indent
    }

    override val languageData: Map<String, Any>
        get() = mapOf(
            "commentTokens" to mapOf(
                "line" to "//",
                "block" to mapOf("open" to "/*", "close" to "*/")
            )
        )
}
