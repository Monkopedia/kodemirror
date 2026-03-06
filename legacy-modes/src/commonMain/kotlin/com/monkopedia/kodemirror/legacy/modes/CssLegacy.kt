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

private fun cssKeySet(array: List<String>): Set<String> {
    return array.map { it.lowercase() }.toSet()
}

private val cssDocumentTypes = cssKeySet(listOf("domain", "regexp", "url", "url-prefix"))

private val cssMediaTypes = cssKeySet(
    listOf(
        "all", "aural", "braille", "handheld", "print", "projection", "screen",
        "tty", "tv", "embossed"
    )
)

private val cssMediaFeatures = cssKeySet(
    listOf(
        "width", "min-width", "max-width", "height", "min-height", "max-height",
        "device-width", "min-device-width", "max-device-width", "device-height",
        "min-device-height", "max-device-height", "aspect-ratio",
        "min-aspect-ratio", "max-aspect-ratio", "device-aspect-ratio",
        "min-device-aspect-ratio", "max-device-aspect-ratio", "color", "min-color",
        "max-color", "color-index", "min-color-index", "max-color-index",
        "monochrome", "min-monochrome", "max-monochrome", "resolution",
        "min-resolution", "max-resolution", "scan", "grid", "orientation",
        "device-pixel-ratio", "min-device-pixel-ratio", "max-device-pixel-ratio",
        "pointer", "any-pointer", "hover", "any-hover", "prefers-color-scheme",
        "dynamic-range", "video-dynamic-range"
    )
)

private val cssMediaValueKeywords = cssKeySet(
    listOf(
        "landscape", "portrait", "none", "coarse", "fine", "on-demand", "hover",
        "interlace", "progressive", "dark", "light", "standard", "high"
    )
)

private val cssPropertyKeywords_ = listOf(
    "align-content", "align-items", "align-self", "alignment-adjust",
    "alignment-baseline", "all", "anchor-point", "animation", "animation-delay",
    "animation-direction", "animation-duration", "animation-fill-mode",
    "animation-iteration-count", "animation-name", "animation-play-state",
    "animation-timing-function", "appearance", "azimuth", "backdrop-filter",
    "backface-visibility", "background", "background-attachment",
    "background-blend-mode", "background-clip", "background-color",
    "background-image", "background-origin", "background-position",
    "background-position-x", "background-position-y", "background-repeat",
    "background-size", "baseline-shift", "binding", "bleed", "block-size",
    "bookmark-label", "bookmark-level", "bookmark-state", "bookmark-target",
    "border", "border-bottom", "border-bottom-color", "border-bottom-left-radius",
    "border-bottom-right-radius", "border-bottom-style", "border-bottom-width",
    "border-collapse", "border-color", "border-image", "border-image-outset",
    "border-image-repeat", "border-image-slice", "border-image-source",
    "border-image-width", "border-left", "border-left-color", "border-left-style",
    "border-left-width", "border-radius", "border-right", "border-right-color",
    "border-right-style", "border-right-width", "border-spacing", "border-style",
    "border-top", "border-top-color", "border-top-left-radius",
    "border-top-right-radius", "border-top-style", "border-top-width",
    "border-width", "bottom", "box-decoration-break", "box-shadow", "box-sizing",
    "break-after", "break-before", "break-inside", "caption-side", "caret-color",
    "clear", "clip", "color", "color-profile", "column-count", "column-fill",
    "column-gap", "column-rule", "column-rule-color", "column-rule-style",
    "column-rule-width", "column-span", "column-width", "columns", "contain",
    "content", "counter-increment", "counter-reset", "crop", "cue", "cue-after",
    "cue-before", "cursor", "direction", "display", "dominant-baseline",
    "drop-initial-after-adjust", "drop-initial-after-align",
    "drop-initial-before-adjust", "drop-initial-before-align",
    "drop-initial-size", "drop-initial-value", "elevation", "empty-cells", "fit",
    "fit-content", "fit-position", "flex", "flex-basis", "flex-direction",
    "flex-flow", "flex-grow", "flex-shrink", "flex-wrap", "float", "float-offset",
    "flow-from", "flow-into", "font", "font-family", "font-feature-settings",
    "font-kerning", "font-language-override", "font-optical-sizing", "font-size",
    "font-size-adjust", "font-stretch", "font-style", "font-synthesis",
    "font-variant", "font-variant-alternates", "font-variant-caps",
    "font-variant-east-asian", "font-variant-ligatures", "font-variant-numeric",
    "font-variant-position", "font-variation-settings", "font-weight", "gap",
    "grid", "grid-area", "grid-auto-columns", "grid-auto-flow", "grid-auto-rows",
    "grid-column", "grid-column-end", "grid-column-gap", "grid-column-start",
    "grid-gap", "grid-row", "grid-row-end", "grid-row-gap", "grid-row-start",
    "grid-template", "grid-template-areas", "grid-template-columns",
    "grid-template-rows", "hanging-punctuation", "height", "hyphens", "icon",
    "image-orientation", "image-rendering", "image-resolution",
    "inline-box-align", "inset", "inset-block", "inset-block-end",
    "inset-block-start", "inset-inline", "inset-inline-end",
    "inset-inline-start", "isolation", "justify-content", "justify-items",
    "justify-self", "left", "letter-spacing", "line-break", "line-height",
    "line-height-step", "line-stacking", "line-stacking-ruby",
    "line-stacking-shift", "line-stacking-strategy", "list-style",
    "list-style-image", "list-style-position", "list-style-type", "margin",
    "margin-bottom", "margin-left", "margin-right", "margin-top", "marks",
    "marquee-direction", "marquee-loop", "marquee-play-count", "marquee-speed",
    "marquee-style", "mask-clip", "mask-composite", "mask-image", "mask-mode",
    "mask-origin", "mask-position", "mask-repeat", "mask-size", "mask-type",
    "max-block-size", "max-height", "max-inline-size", "max-width",
    "min-block-size", "min-height", "min-inline-size", "min-width",
    "mix-blend-mode", "move-to", "nav-down", "nav-index", "nav-left",
    "nav-right", "nav-up", "object-fit", "object-position", "offset",
    "offset-anchor", "offset-distance", "offset-path", "offset-position",
    "offset-rotate", "opacity", "order", "orphans", "outline", "outline-color",
    "outline-offset", "outline-style", "outline-width", "overflow",
    "overflow-style", "overflow-wrap", "overflow-x", "overflow-y", "padding",
    "padding-bottom", "padding-left", "padding-right", "padding-top", "page",
    "page-break-after", "page-break-before", "page-break-inside", "page-policy",
    "pause", "pause-after", "pause-before", "perspective", "perspective-origin",
    "pitch", "pitch-range", "place-content", "place-items", "place-self",
    "play-during", "position", "presentation-level", "punctuation-trim", "quotes",
    "region-break-after", "region-break-before", "region-break-inside",
    "region-fragment", "rendering-intent", "resize", "rest", "rest-after",
    "rest-before", "richness", "right", "rotate", "rotation", "rotation-point",
    "row-gap", "ruby-align", "ruby-overhang", "ruby-position", "ruby-span",
    "scale", "scroll-behavior", "scroll-margin", "scroll-margin-block",
    "scroll-margin-block-end", "scroll-margin-block-start",
    "scroll-margin-bottom", "scroll-margin-inline", "scroll-margin-inline-end",
    "scroll-margin-inline-start", "scroll-margin-left", "scroll-margin-right",
    "scroll-margin-top", "scroll-padding", "scroll-padding-block",
    "scroll-padding-block-end", "scroll-padding-block-start",
    "scroll-padding-bottom", "scroll-padding-inline",
    "scroll-padding-inline-end", "scroll-padding-inline-start",
    "scroll-padding-left", "scroll-padding-right", "scroll-padding-top",
    "scroll-snap-align", "scroll-snap-type", "shape-image-threshold",
    "shape-inside", "shape-margin", "shape-outside", "size", "speak",
    "speak-as", "speak-header", "speak-numeral", "speak-punctuation",
    "speech-rate", "stress", "string-set", "tab-size", "table-layout", "target",
    "target-name", "target-new", "target-position", "text-align",
    "text-align-last", "text-combine-upright", "text-decoration",
    "text-decoration-color", "text-decoration-line", "text-decoration-skip",
    "text-decoration-skip-ink", "text-decoration-style", "text-emphasis",
    "text-emphasis-color", "text-emphasis-position", "text-emphasis-style",
    "text-height", "text-indent", "text-justify", "text-orientation",
    "text-outline", "text-overflow", "text-rendering", "text-shadow",
    "text-size-adjust", "text-space-collapse", "text-transform",
    "text-underline-position", "text-wrap", "top", "touch-action", "transform",
    "transform-origin", "transform-style", "transition", "transition-delay",
    "transition-duration", "transition-property", "transition-timing-function",
    "translate", "unicode-bidi", "user-select", "vertical-align", "visibility",
    "voice-balance", "voice-duration", "voice-family", "voice-pitch",
    "voice-range", "voice-rate", "voice-stress", "voice-volume", "volume",
    "white-space", "widows", "width", "will-change", "word-break", "word-spacing",
    "word-wrap", "writing-mode", "z-index",
    // SVG-specific
    "clip-path", "clip-rule", "mask", "enable-background", "filter",
    "flood-color", "flood-opacity", "lighting-color", "stop-color",
    "stop-opacity", "pointer-events", "color-interpolation",
    "color-interpolation-filters", "color-rendering", "fill", "fill-opacity",
    "fill-rule", "image-rendering", "marker", "marker-end", "marker-mid",
    "marker-start", "paint-order", "shape-rendering", "stroke",
    "stroke-dasharray", "stroke-dashoffset", "stroke-linecap", "stroke-linejoin",
    "stroke-miterlimit", "stroke-opacity", "stroke-width", "text-rendering",
    "baseline-shift", "dominant-baseline", "glyph-orientation-horizontal",
    "glyph-orientation-vertical", "text-anchor", "writing-mode"
)

private val cssPropertyKeywords = cssKeySet(cssPropertyKeywords_)

private val cssNonStandardPropertyKeywords = cssKeySet(
    listOf(
        "accent-color", "aspect-ratio", "border-block", "border-block-color",
        "border-block-end", "border-block-end-color", "border-block-end-style",
        "border-block-end-width", "border-block-start", "border-block-start-color",
        "border-block-start-style", "border-block-start-width",
        "border-block-style", "border-block-width", "border-inline",
        "border-inline-color", "border-inline-end", "border-inline-end-color",
        "border-inline-end-style", "border-inline-end-width",
        "border-inline-start", "border-inline-start-color",
        "border-inline-start-style", "border-inline-start-width",
        "border-inline-style", "border-inline-width", "content-visibility",
        "margin-block", "margin-block-end", "margin-block-start",
        "margin-inline", "margin-inline-end", "margin-inline-start",
        "overflow-anchor", "overscroll-behavior", "padding-block",
        "padding-block-end", "padding-block-start", "padding-inline",
        "padding-inline-end", "padding-inline-start", "scroll-snap-stop",
        "scrollbar-3d-light-color", "scrollbar-arrow-color",
        "scrollbar-base-color", "scrollbar-dark-shadow-color",
        "scrollbar-face-color", "scrollbar-highlight-color",
        "scrollbar-shadow-color", "scrollbar-track-color",
        "searchfield-cancel-button", "searchfield-decoration",
        "searchfield-results-button", "searchfield-results-decoration",
        "shape-inside", "zoom"
    )
)

private val cssFontProperties = cssKeySet(
    listOf(
        "font-display", "font-family", "src", "unicode-range", "font-variant",
        "font-feature-settings", "font-stretch", "font-weight", "font-style"
    )
)

private val cssFontProperties_ = listOf(
    "font-display", "font-family", "src", "unicode-range", "font-variant",
    "font-feature-settings", "font-stretch", "font-weight", "font-style"
)

private val cssCounterDescriptors = cssKeySet(
    listOf(
        "additive-symbols", "fallback", "negative", "pad", "prefix", "range",
        "speak-as", "suffix", "symbols", "system"
    )
)

private val cssColorKeywords_ = listOf(
    "aliceblue", "antiquewhite", "aqua", "aquamarine", "azure", "beige",
    "bisque", "black", "blanchedalmond", "blue", "blueviolet", "brown",
    "burlywood", "cadetblue", "chartreuse", "chocolate", "coral",
    "cornflowerblue", "cornsilk", "crimson", "cyan", "darkblue", "darkcyan",
    "darkgoldenrod", "darkgray", "darkgreen", "darkgrey", "darkkhaki",
    "darkmagenta", "darkolivegreen", "darkorange", "darkorchid", "darkred",
    "darksalmon", "darkseagreen", "darkslateblue", "darkslategray",
    "darkslategrey", "darkturquoise", "darkviolet", "deeppink", "deepskyblue",
    "dimgray", "dimgrey", "dodgerblue", "firebrick", "floralwhite",
    "forestgreen", "fuchsia", "gainsboro", "ghostwhite", "gold", "goldenrod",
    "gray", "grey", "green", "greenyellow", "honeydew", "hotpink", "indianred",
    "indigo", "ivory", "khaki", "lavender", "lavenderblush", "lawngreen",
    "lemonchiffon", "lightblue", "lightcoral", "lightcyan",
    "lightgoldenrodyellow", "lightgray", "lightgreen", "lightgrey", "lightpink",
    "lightsalmon", "lightseagreen", "lightskyblue", "lightslategray",
    "lightslategrey", "lightsteelblue", "lightyellow", "lime", "limegreen",
    "linen", "magenta", "maroon", "mediumaquamarine", "mediumblue",
    "mediumorchid", "mediumpurple", "mediumseagreen", "mediumslateblue",
    "mediumspringgreen", "mediumturquoise", "mediumvioletred", "midnightblue",
    "mintcream", "mistyrose", "moccasin", "navajowhite", "navy", "oldlace",
    "olive", "olivedrab", "orange", "orangered", "orchid", "palegoldenrod",
    "palegreen", "paleturquoise", "palevioletred", "papayawhip", "peachpuff",
    "peru", "pink", "plum", "powderblue", "purple", "rebeccapurple", "red",
    "rosybrown", "royalblue", "saddlebrown", "salmon", "sandybrown", "seagreen",
    "seashell", "sienna", "silver", "skyblue", "slateblue", "slategray",
    "slategrey", "snow", "springgreen", "steelblue", "tan", "teal", "thistle",
    "tomato", "turquoise", "violet", "wheat", "white", "whitesmoke", "yellow",
    "yellowgreen"
)

private val cssColorKeywords = cssKeySet(cssColorKeywords_)

private val cssValueKeywords_ = listOf(
    "above", "absolute", "activeborder", "additive", "activecaption", "afar",
    "after-white-space", "ahead", "alias", "all", "all-scroll", "alphabetic",
    "alternate", "always", "amharic", "amharic-abegede", "antialiased",
    "appworkspace", "arabic-indic", "armenian", "asterisks", "attr", "auto",
    "auto-flow", "avoid", "avoid-column", "avoid-page", "avoid-region",
    "axis-pan", "background", "backwards", "baseline", "below",
    "bidi-override", "binary", "bengali", "blink", "block", "block-axis",
    "blur", "bold", "bolder", "border", "border-box", "both", "bottom",
    "break", "break-all", "break-word", "brightness", "bullets", "button",
    "buttonface", "buttonhighlight", "buttonshadow", "buttontext", "calc",
    "cambodian", "capitalize", "caps-lock-indicator", "caption", "captiontext",
    "caret", "cell", "center", "checkbox", "circle", "cjk-decimal",
    "cjk-earthly-branch", "cjk-heavenly-stem", "cjk-ideographic", "clear",
    "clip", "close-quote", "col-resize", "collapse", "color", "color-burn",
    "color-dodge", "column", "column-reverse", "compact", "condensed",
    "conic-gradient", "contain", "content", "contents", "content-box",
    "context-menu", "continuous", "contrast", "copy", "counter", "counters",
    "cover", "crop", "cross", "crosshair", "cubic-bezier", "currentcolor",
    "cursive", "cyclic", "darken", "dashed", "decimal",
    "decimal-leading-zero", "default", "default-button", "dense",
    "destination-atop", "destination-in", "destination-out",
    "destination-over", "devanagari", "difference", "disc", "discard",
    "disclosure-closed", "disclosure-open", "document", "dot-dash",
    "dot-dot-dash", "dotted", "double", "down", "drop-shadow", "e-resize",
    "ease", "ease-in", "ease-in-out", "ease-out", "element", "ellipse",
    "ellipsis", "embed", "end", "ethiopic", "ethiopic-abegede",
    "ethiopic-abegede-am-et", "ethiopic-abegede-gez",
    "ethiopic-abegede-ti-er", "ethiopic-abegede-ti-et",
    "ethiopic-halehame-aa-er", "ethiopic-halehame-aa-et",
    "ethiopic-halehame-am-et", "ethiopic-halehame-gez",
    "ethiopic-halehame-om-et", "ethiopic-halehame-sid-et",
    "ethiopic-halehame-so-et", "ethiopic-halehame-ti-er",
    "ethiopic-halehame-ti-et", "ethiopic-halehame-tig",
    "ethiopic-numeric", "ew-resize", "exclusion", "expanded", "extends",
    "extra-condensed", "extra-expanded", "fantasy", "fast", "fill",
    "fill-box", "fixed", "flat", "flex", "flex-end", "flex-start",
    "footnotes", "forwards", "from", "geometricPrecision", "georgian",
    "grayscale", "graytext", "grid", "groove", "gujarati", "gurmukhi",
    "hand", "hangul", "hangul-consonant", "hard-light", "hebrew", "help",
    "hidden", "hide", "higher", "highlight", "highlighttext", "hiragana",
    "hiragana-iroha", "horizontal", "hsl", "hsla", "hue", "hue-rotate",
    "icon", "ignore", "inactiveborder", "inactivecaption",
    "inactivecaptiontext", "infinite", "infobackground", "infotext",
    "inherit", "initial", "inline", "inline-axis", "inline-block",
    "inline-flex", "inline-grid", "inline-table", "inset", "inside",
    "intrinsic", "invert", "italic", "japanese-formal",
    "japanese-informal", "justify", "kannada", "katakana", "katakana-iroha",
    "keep-all", "khmer", "korean-hangul-formal", "korean-hanja-formal",
    "korean-hanja-informal", "landscape", "lao", "large", "larger", "left",
    "level", "lighter", "lighten", "line-through", "linear",
    "linear-gradient", "lines", "list-item", "listbox", "listitem", "local",
    "logical", "loud", "lower", "lower-alpha", "lower-armenian",
    "lower-greek", "lower-hexadecimal", "lower-latin", "lower-norwegian",
    "lower-roman", "lowercase", "ltr", "luminosity", "malayalam",
    "manipulation", "match", "matrix", "matrix3d", "media-play-button",
    "media-slider", "media-sliderthumb", "media-volume-slider",
    "media-volume-sliderthumb", "medium", "menu", "menulist",
    "menulist-button", "menutext", "message-box", "middle", "min-intrinsic",
    "mix", "mongolian", "monospace", "move", "multiple",
    "multiple_mask_images", "multiply", "myanmar", "n-resize", "narrower",
    "ne-resize", "nesw-resize", "no-close-quote", "no-drop", "no-open-quote",
    "no-repeat", "none", "normal", "not-allowed", "nowrap", "ns-resize",
    "numbers", "numeric", "nw-resize", "nwse-resize", "oblique", "octal",
    "opacity", "open-quote", "optimizeLegibility", "optimizeSpeed", "oriya",
    "oromo", "outset", "outside", "outside-shape", "overlay", "overline",
    "padding", "padding-box", "painted", "page", "paused", "persian",
    "perspective", "pinch-zoom", "plus-darker", "plus-lighter", "pointer",
    "polygon", "portrait", "pre", "pre-line", "pre-wrap", "preserve-3d",
    "progress", "push-button", "radial-gradient", "radio", "read-only",
    "read-write", "read-write-plaintext-only", "rectangle", "region",
    "relative", "repeat", "repeating-linear-gradient",
    "repeating-radial-gradient", "repeating-conic-gradient", "repeat-x",
    "repeat-y", "reset", "reverse", "rgb", "rgba", "ridge", "right",
    "rotate", "rotate3d", "rotateX", "rotateY", "rotateZ", "round", "row",
    "row-resize", "row-reverse", "rtl", "run-in", "running", "s-resize",
    "sans-serif", "saturate", "saturation", "scale", "scale3d", "scaleX",
    "scaleY", "scaleZ", "screen", "scroll", "scrollbar", "scroll-position",
    "se-resize", "searchfield", "searchfield-cancel-button",
    "searchfield-decoration", "searchfield-results-button",
    "searchfield-results-decoration", "self-start", "self-end",
    "semi-condensed", "semi-expanded", "separate", "sepia", "serif", "show",
    "sidama", "simp-chinese-formal", "simp-chinese-informal", "single",
    "skew", "skewX", "skewY", "skip-white-space", "slide",
    "slider-horizontal", "slider-vertical", "sliderthumb-horizontal",
    "sliderthumb-vertical", "slow", "small", "small-caps", "small-caption",
    "smaller", "soft-light", "solid", "somali", "source-atop", "source-in",
    "source-out", "source-over", "space", "space-around", "space-between",
    "space-evenly", "spell-out", "square", "square-button", "start",
    "static", "status-bar", "stretch", "stroke", "stroke-box", "sub",
    "subpixel-antialiased", "svg_masks", "super", "sw-resize", "symbolic",
    "symbols", "system-ui", "table", "table-caption", "table-cell",
    "table-column", "table-column-group", "table-footer-group",
    "table-header-group", "table-row", "table-row-group", "tamil", "telugu",
    "text", "text-bottom", "text-top", "textarea", "textfield", "thai",
    "thick", "thin", "threeddarkshadow", "threedface", "threedhighlight",
    "threedlightshadow", "threedshadow", "tibetan", "tigre", "tigrinya-er",
    "tigrinya-er-abegede", "tigrinya-et", "tigrinya-et-abegede", "to", "top",
    "trad-chinese-formal", "trad-chinese-informal", "transform", "translate",
    "translate3d", "translateX", "translateY", "translateZ", "transparent",
    "ultra-condensed", "ultra-expanded", "underline", "unidirectional-pan",
    "unset", "up", "upper-alpha", "upper-armenian", "upper-greek",
    "upper-hexadecimal", "upper-latin", "upper-norwegian", "upper-roman",
    "uppercase", "urdu", "url", "var", "vertical", "vertical-text",
    "view-box", "visible", "visibleFill", "visiblePainted", "visibleStroke",
    "visual", "w-resize", "wait", "wave", "wider", "window", "windowframe",
    "windowtext", "words", "wrap", "wrap-reverse", "x-large", "x-small",
    "xor", "xx-large", "xx-small"
)

private val cssValueKeywords = cssKeySet(cssValueKeywords_)

/** Exported keywords for use by sass/stylus modes. */
object CssKeywords {
    val properties: List<String> = cssPropertyKeywords_
    val colors: List<String> = cssColorKeywords_
    val fonts: List<String> = cssFontProperties_
    val values: List<String> = cssValueKeywords_
}

data class CssContext(
    val type: String,
    val indent: Int,
    val prev: CssContext?
)

data class CssConfig(
    val name: String = "css",
    val inline: Boolean = false,
    val allowNested: Boolean = false,
    val lineComment: String? = null,
    val supportsAtComponent: Boolean = false,
    val highlightNonStandardPropertyKeywords: Boolean = true,
    val tokenHooks: Map<String, (StringStream, CssState) -> Any?> = emptyMap()
)

class CssState(
    // 0=base, 1=string, 2=cComment, 3=parenthesized
    var tokenize: Int = 0,
    var stringQuote: String = "",
    var state: String = "top",
    var stateArg: String? = null,
    var context: CssContext = CssContext("top", 0, null)
)

@Suppress("CyclomaticComplexMethod", "LongMethod", "ReturnCount", "NestedBlockDepth")
fun mkCSS(config: CssConfig): StreamParser<CssState> {
    val allowNested = config.allowNested
    val lineComment = config.lineComment
    val supportsAtComponent = config.supportsAtComponent
    val highlightNonStd = config.highlightNonStandardPropertyKeywords
    val tokenHooks = config.tokenHooks
    val inline = config.inline

    var type = ""
    var override: String? = null

    fun ret(style: String?, tp: String): String? {
        type = tp
        return style
    }

    fun tokenCComment(stream: StringStream, state: CssState): String? {
        var maybeEnd = false
        while (true) {
            val ch = stream.next() ?: break
            if (maybeEnd && ch == "/") {
                state.tokenize = 0
                break
            }
            maybeEnd = ch == "*"
        }
        return ret("comment", "comment")
    }

    fun tokenBase(stream: StringStream, state: CssState): String? {
        val ch = stream.next() ?: return null

        // token hooks
        val hookFn = tokenHooks[ch]
        if (hookFn != null) {
            val result = hookFn(stream, state)
            if (result != false && result != null) {
                // result might be a string or a list of two strings [style, type]
                @Suppress("UNCHECKED_CAST")
                if (result is List<*>) {
                    val list = result as List<String?>
                    type = list[1] ?: ""
                    return list[0]
                }
                return result as? String
            }
        }

        if (ch == "@") {
            stream.eatWhile(Regex("[\\w\\\\-]"))
            return ret("def", stream.current())
        } else if (ch == "=" || (ch == "~" || ch == "|") && stream.eat("=") != null) {
            return ret(null, "compare")
        } else if (ch == "\"" || ch == "'") {
            state.stringQuote = ch
            state.tokenize = 1
            // inline string tokenize
            var escaped = false
            while (true) {
                val c = stream.next() ?: break
                if (c == ch && !escaped) break
                escaped = !escaped && c == "\\"
            }
            state.tokenize = 0
            return ret("string", "string")
        } else if (ch == "#") {
            stream.eatWhile(Regex("[\\w\\\\-]"))
            return ret("atom", "hash")
        } else if (ch == "!") {
            stream.match(Regex("^\\s*\\w*"))
            return ret("keyword", "important")
        } else if (Regex("\\d").containsMatchIn(ch) ||
            (ch == "." && stream.eat(Regex("\\d")) != null)
        ) {
            stream.eatWhile(Regex("[\\w.%]"))
            return ret("number", "unit")
        } else if (ch == "-") {
            if (Regex("[\\d.]").containsMatchIn(stream.peek() ?: "")) {
                stream.eatWhile(Regex("[\\w.%]"))
                return ret("number", "unit")
            } else if (stream.match(Regex("^-[\\w\\\\-]*")) != null) {
                stream.eatWhile(Regex("[\\w\\\\-]"))
                if (stream.match(Regex("^\\s*:"), false) != null) {
                    return ret("def", "variable-definition")
                }
                return ret("variableName", "variable")
            } else if (stream.match(Regex("^\\w+-")) != null) {
                return ret("meta", "meta")
            }
        } else if (Regex("[,+>*/]").containsMatchIn(ch)) {
            return ret(null, "select-op")
        } else if (ch == "." &&
            stream.match(Regex("^-?[_a-z][_a-z0-9-]*", RegexOption.IGNORE_CASE)) != null
        ) {
            return ret("qualifier", "qualifier")
        } else if (Regex("[:;{}\\[\\]()]").containsMatchIn(ch)) {
            return ret(null, ch)
        } else if (stream.match(Regex("^[\\w-.]+(?=\\()")) != null) {
            if (Regex("^(url(-prefix)?|domain|regexp)\$", RegexOption.IGNORE_CASE)
                    .containsMatchIn(stream.current())
            ) {
                state.tokenize = 3
            }
            return ret("variableName.function", "variable")
        } else if (Regex("[\\w\\\\-]").containsMatchIn(ch)) {
            stream.eatWhile(Regex("[\\w\\\\-]"))
            return ret("property", "word")
        }
        return ret(null, "")
    }

    fun pushContext(state: CssState, stream: StringStream, ctxType: String): String {
        state.context = CssContext(
            ctxType,
            stream.indentation() + stream.indentUnit,
            state.context
        )
        return ctxType
    }

    fun popContext(state: CssState): String {
        if (state.context.prev != null) {
            state.context = state.context.prev!!
        }
        return state.context.type
    }

    // stateDispatchFn is assigned after all state functions are defined to break the
    // circular forward-reference cycle: pass -> stateDispatch -> state fns -> pass.
    var stateDispatchFn: ((String, StringStream, CssState) -> String)? = null

    fun pass(tp: String, stream: StringStream, state: CssState): String {
        return stateDispatchFn!!(tp, stream, state)
    }

    fun popAndPass(tp: String, stream: StringStream, state: CssState, n: Int = 1): String {
        for (i in 0 until n) {
            if (state.context.prev != null) {
                state.context = state.context.prev!!
            }
        }
        return pass(tp, stream, state)
    }

    fun wordAsValue(stream: StringStream) {
        val word = stream.current().lowercase()
        override = when {
            cssValueKeywords.contains(word) -> "atom"
            cssColorKeywords.contains(word) -> "keyword"
            else -> "variable"
        }
    }

    fun stateTop(tp: String, stream: StringStream, state: CssState): String {
        if (tp == "{") {
            return pushContext(state, stream, "block")
        } else if (tp == "}" && state.context.prev != null) {
            return popContext(state)
        } else if (supportsAtComponent &&
            Regex("@component", RegexOption.IGNORE_CASE).containsMatchIn(tp)
        ) {
            return pushContext(state, stream, "atComponentBlock")
        } else if (Regex("^@(-moz-)?document\$", RegexOption.IGNORE_CASE).containsMatchIn(tp)) {
            return pushContext(state, stream, "documentTypes")
        } else if (Regex(
                "^@(media|supports|(-moz-)?document|import)\$",
                RegexOption.IGNORE_CASE
            ).containsMatchIn(tp)
        ) {
            return pushContext(state, stream, "atBlock")
        } else if (Regex("^@(font-face|counter-style)", RegexOption.IGNORE_CASE)
                .containsMatchIn(tp)
        ) {
            state.stateArg = tp
            return "restricted_atBlock_before"
        } else if (Regex(
                "^@(-(moz|ms|o|webkit)-)?keyframes\$",
                RegexOption.IGNORE_CASE
            ).containsMatchIn(tp)
        ) {
            return "keyframes"
        } else if (tp.isNotEmpty() && tp[0] == '@') {
            return pushContext(state, stream, "at")
        } else if (tp == "hash") {
            override = "builtin"
        } else if (tp == "word") {
            override = "tag"
        } else if (tp == "variable-definition") {
            return "maybeprop"
        } else if (tp == "interpolation") {
            return pushContext(state, stream, "interpolation")
        } else if (tp == ":") {
            return "pseudo"
        } else if (allowNested && tp == "(") {
            return pushContext(state, stream, "parens")
        }
        return state.context.type
    }

    fun stateBlock(tp: String, stream: StringStream, state: CssState): String {
        if (tp == "word") {
            val word = stream.current().lowercase()
            if (cssPropertyKeywords.contains(word)) {
                override = "property"
                return "maybeprop"
            } else if (cssNonStandardPropertyKeywords.contains(word)) {
                override = if (highlightNonStd) "string.special" else "property"
                return "maybeprop"
            } else if (allowNested) {
                override = if (stream.match(Regex("^\\s*:(?:\\s|\$)"), false) != null) {
                    "property"
                } else {
                    "tag"
                }
                return "block"
            } else {
                override = "error"
                return "maybeprop"
            }
        } else if (tp == "meta") {
            return "block"
        } else if (!allowNested && (tp == "hash" || tp == "qualifier")) {
            override = "error"
            return "block"
        }
        return stateTop(tp, stream, state)
    }

    fun stateMaybeprop(tp: String, stream: StringStream, state: CssState): String {
        if (tp == ":") return pushContext(state, stream, "prop")
        return pass(tp, stream, state)
    }

    fun stateProp(tp: String, stream: StringStream, state: CssState): String {
        if (tp == ";") return popContext(state)
        if (tp == "{" && allowNested) return pushContext(state, stream, "propBlock")
        if (tp == "}" || tp == "{") return popAndPass(tp, stream, state)
        if (tp == "(") return pushContext(state, stream, "parens")

        if (tp == "hash" &&
            !Regex("^#([0-9a-fA-F]{3,4}|[0-9a-fA-F]{6}|[0-9a-fA-F]{8})\$")
                .containsMatchIn(stream.current())
        ) {
            override = "error"
        } else if (tp == "word") {
            wordAsValue(stream)
        } else if (tp == "interpolation") {
            return pushContext(state, stream, "interpolation")
        }
        return "prop"
    }

    fun statePropBlock(tp: String, stream: StringStream, state: CssState): String {
        if (tp == "}") return popContext(state)
        if (tp == "word") {
            override = "property"
            return "maybeprop"
        }
        return state.context.type
    }

    fun stateParens(tp: String, stream: StringStream, state: CssState): String {
        if (tp == "{" || tp == "}") return popAndPass(tp, stream, state)
        if (tp == ")") return popContext(state)
        if (tp == "(") return pushContext(state, stream, "parens")
        if (tp == "interpolation") return pushContext(state, stream, "interpolation")
        if (tp == "word") wordAsValue(stream)
        return "parens"
    }

    fun statePseudo(tp: String, stream: StringStream, state: CssState): String {
        if (tp == "meta") return "pseudo"
        if (tp == "word") {
            override = "variableName.constant"
            return state.context.type
        }
        return pass(tp, stream, state)
    }

    fun stateAtBlock(tp: String, stream: StringStream, state: CssState): String {
        if (tp == "(") return pushContext(state, stream, "atBlock_parens")
        if (tp == "}" || tp == ";") return popAndPass(tp, stream, state)
        if (tp == "{") {
            popContext(state)
            return pushContext(state, stream, if (allowNested) "block" else "top")
        }
        if (tp == "interpolation") return pushContext(state, stream, "interpolation")

        if (tp == "word") {
            val word = stream.current().lowercase()
            override = when {
                word == "only" || word == "not" || word == "and" || word == "or" -> "keyword"
                cssMediaTypes.contains(word) -> "attribute"
                cssMediaFeatures.contains(word) -> "property"
                cssMediaValueKeywords.contains(word) -> "keyword"
                cssPropertyKeywords.contains(word) -> "property"
                cssNonStandardPropertyKeywords.contains(word) ->
                    if (highlightNonStd) "string.special" else "property"
                cssValueKeywords.contains(word) -> "atom"
                cssColorKeywords.contains(word) -> "keyword"
                else -> "error"
            }
        }
        return state.context.type
    }

    fun stateDocumentTypes(tp: String, stream: StringStream, state: CssState): String {
        if (tp == "word" && cssDocumentTypes.contains(stream.current())) {
            override = "tag"
            return state.context.type
        }
        return stateAtBlock(tp, stream, state)
    }

    fun stateAtComponentBlock(tp: String, stream: StringStream, state: CssState): String {
        if (tp == "}") return popAndPass(tp, stream, state)
        if (tp == "{") {
            popContext(state)
            return pushContext(state, stream, if (allowNested) "block" else "top")
        }
        if (tp == "word") override = "error"
        return state.context.type
    }

    fun stateAtBlockParens(tp: String, stream: StringStream, state: CssState): String {
        if (tp == ")") return popContext(state)
        if (tp == "{" || tp == "}") return popAndPass(tp, stream, state, 2)
        return stateAtBlock(tp, stream, state)
    }

    fun stateRestrictedAtBlockBefore(tp: String, stream: StringStream, state: CssState): String {
        if (tp == "{") return pushContext(state, stream, "restricted_atBlock")
        if (tp == "word" && state.stateArg == "@counter-style") {
            override = "variable"
            return "restricted_atBlock_before"
        }
        return pass(tp, stream, state)
    }

    fun stateRestrictedAtBlock(tp: String, stream: StringStream, state: CssState): String {
        if (tp == "}") {
            state.stateArg = null
            return popContext(state)
        }
        if (tp == "word") {
            override = when {
                state.stateArg == "@font-face" &&
                    !cssFontProperties.contains(stream.current().lowercase()) -> "error"
                state.stateArg == "@counter-style" &&
                    !cssCounterDescriptors.contains(stream.current().lowercase()) -> "error"
                else -> "property"
            }
            return "maybeprop"
        }
        return "restricted_atBlock"
    }

    fun stateKeyframes(tp: String, stream: StringStream, state: CssState): String {
        if (tp == "word") {
            override = "variable"
            return "keyframes"
        }
        if (tp == "{") return pushContext(state, stream, "top")
        return pass(tp, stream, state)
    }

    fun stateAt(tp: String, stream: StringStream, state: CssState): String {
        if (tp == ";") return popContext(state)
        if (tp == "{" || tp == "}") return popAndPass(tp, stream, state)
        if (tp == "word") {
            override = "tag"
        } else if (tp == "hash") override = "builtin"
        return "at"
    }

    fun stateInterpolation(tp: String, stream: StringStream, state: CssState): String {
        if (tp == "}") return popContext(state)
        if (tp == "{" || tp == ";") return popAndPass(tp, stream, state)
        if (tp == "word") {
            override = "variable"
        } else if (tp != "variable" && tp != "(" && tp != ")") override = "error"
        return "interpolation"
    }

    // Assign the dispatch function now that all state functions are defined.
    stateDispatchFn = { tp, stream, state ->
        when (state.context.type) {
            "top" -> stateTop(tp, stream, state)
            "block" -> stateBlock(tp, stream, state)
            "maybeprop" -> stateMaybeprop(tp, stream, state)
            "prop" -> stateProp(tp, stream, state)
            "propBlock" -> statePropBlock(tp, stream, state)
            "parens" -> stateParens(tp, stream, state)
            "pseudo" -> statePseudo(tp, stream, state)
            "documentTypes" -> stateDocumentTypes(tp, stream, state)
            "atBlock" -> stateAtBlock(tp, stream, state)
            "atComponentBlock" -> stateAtComponentBlock(tp, stream, state)
            "atBlock_parens" -> stateAtBlockParens(tp, stream, state)
            "restricted_atBlock_before" -> stateRestrictedAtBlockBefore(tp, stream, state)
            "restricted_atBlock" -> stateRestrictedAtBlock(tp, stream, state)
            "keyframes" -> stateKeyframes(tp, stream, state)
            "at" -> stateAt(tp, stream, state)
            "interpolation" -> stateInterpolation(tp, stream, state)
            else -> state.context.type
        }
    }

    return object : StreamParser<CssState> {
        override val name: String get() = config.name

        override fun startState(indentUnit: Int): CssState {
            val initialState = if (inline) "block" else "top"
            return CssState(
                state = initialState,
                context = CssContext(initialState, 0, null)
            )
        }

        override fun copyState(state: CssState): CssState {
            return CssState(
                tokenize = state.tokenize,
                stringQuote = state.stringQuote,
                state = state.state,
                stateArg = state.stateArg,
                // immutable data class - safe to share
                context = state.context
            )
        }

        @Suppress("CyclomaticComplexMethod", "ReturnCount")
        override fun token(stream: StringStream, state: CssState): String? {
            if (state.tokenize == 0 && stream.eatSpace()) return null

            val style: String? = when (state.tokenize) {
                1 -> {
                    // string
                    var escaped = false
                    while (true) {
                        val ch = stream.next() ?: break
                        if (ch == state.stringQuote && !escaped) break
                        escaped = !escaped && ch == "\\"
                    }
                    state.tokenize = 0
                    ret("string", "string")
                }
                2 -> tokenCComment(stream, state)
                3 -> {
                    // parenthesized
                    stream.next() // Must be '('
                    if (stream.match(Regex("^\\s*[\"')]"), false) == null) {
                        // tokenString for ")" -- simplified: just read to )
                        var escaped = false
                        while (true) {
                            val ch = stream.next() ?: break
                            if (ch == ")" && !escaped) {
                                stream.backUp(1)
                                break
                            }
                            escaped = !escaped && ch == "\\"
                        }
                    }
                    state.tokenize = 0
                    ret(null, "(")
                }
                else -> tokenBase(stream, state)
            }

            override = style
            if (type != "comment") {
                state.state = stateDispatchFn!!(type, stream, state)
            }
            return override
        }

        override fun indent(state: CssState, textAfter: String, context: IndentContext): Int? {
            var cx = state.context
            val ch = textAfter.firstOrNull()?.toString() ?: ""
            var indent = cx.indent
            if (cx.type == "prop" && (ch == "}" || ch == ")")) {
                cx = cx.prev ?: cx
            }
            if (cx.prev != null) {
                if (ch == "}" && (
                        cx.type == "block" || cx.type == "top" ||
                            cx.type == "interpolation" || cx.type == "restricted_atBlock"
                        )
                ) {
                    cx = cx.prev!!
                    indent = cx.indent
                } else if ((
                        ch == ")" &&
                            (cx.type == "parens" || cx.type == "atBlock_parens")
                        ) ||
                    (ch == "{" && (cx.type == "at" || cx.type == "atBlock"))
                ) {
                    indent = maxOf(0, cx.indent - context.unit)
                }
            }
            return indent
        }

        override val languageData: Map<String, Any>
            get() {
                val commentTokens = mutableMapOf<String, Any>()
                if (lineComment != null) commentTokens["line"] = lineComment
                commentTokens["block"] = mapOf("open" to "/*", "close" to "*/")
                return mapOf("commentTokens" to commentTokens)
            }
    }
}

private fun tokenCCommentForHook(stream: StringStream, state: CssState): List<String> {
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

/** Stream parser for CSS. */
val cssLegacy: StreamParser<CssState> = mkCSS(
    CssConfig(
        name = "css",
        tokenHooks = mapOf(
            "/" to { stream, state ->
                if (stream.eat("*") == null) {
                    false
                } else {
                    state.tokenize = 2
                    tokenCCommentForHook(stream, state)
                }
            }
        )
    )
)

/** Stream parser for SCSS. */
val sCSS: StreamParser<CssState> = mkCSS(
    CssConfig(
        name = "scss",
        allowNested = true,
        lineComment = "//",
        tokenHooks = mapOf(
            "/" to { stream, state ->
                if (stream.eat("/") != null) {
                    stream.skipToEnd()
                    listOf("comment", "comment")
                } else if (stream.eat("*") != null) {
                    state.tokenize = 2
                    tokenCCommentForHook(stream, state)
                } else {
                    listOf("operator", "operator")
                }
            },
            ":" to { stream, _ ->
                if (stream.match(Regex("^\\s*\\{"), false) != null) {
                    listOf(null, null)
                } else {
                    false
                }
            },
            "$" to { stream, _ ->
                stream.match(Regex("^[\\w-]+"))
                if (stream.match(Regex("^\\s*:"), false) != null) {
                    listOf("def", "variable-definition")
                } else {
                    listOf("variableName.special", "variable")
                }
            },
            "#" to { stream, _ ->
                if (stream.eat("{") == null) {
                    false
                } else {
                    listOf(null, "interpolation")
                }
            }
        )
    )
)

/** Stream parser for Less. */
val less: StreamParser<CssState> = mkCSS(
    CssConfig(
        name = "less",
        allowNested = true,
        lineComment = "//",
        tokenHooks = mapOf(
            "/" to { stream, state ->
                if (stream.eat("/") != null) {
                    stream.skipToEnd()
                    listOf("comment", "comment")
                } else if (stream.eat("*") != null) {
                    state.tokenize = 2
                    tokenCCommentForHook(stream, state)
                } else {
                    listOf("operator", "operator")
                }
            },
            "@" to { stream, _ ->
                if (stream.eat("{") != null) {
                    listOf(null, "interpolation")
                } else if (stream.match(
                        Regex(
                            "^(charset|document|font-face|import|(-(moz|ms|o|webkit)-)?keyframes|" +
                                "media|namespace|page|supports)\\b",
                            RegexOption.IGNORE_CASE
                        ),
                        false
                    ) != null
                ) {
                    false
                } else {
                    stream.eatWhile(Regex("[\\w\\\\-]"))
                    if (stream.match(Regex("^\\s*:"), false) != null) {
                        listOf("def", "variable-definition")
                    } else {
                        listOf("variableName", "variable")
                    }
                }
            },
            "&" to { _, _ ->
                listOf("atom", "atom")
            }
        )
    )
)

/** Stream parser for GSS (Closure Stylesheets). */
val gss: StreamParser<CssState> = mkCSS(
    CssConfig(
        name = "gss",
        supportsAtComponent = true,
        tokenHooks = mapOf(
            "/" to { stream, state ->
                if (stream.eat("*") == null) {
                    false
                } else {
                    state.tokenize = 2
                    tokenCCommentForHook(stream, state)
                }
            }
        )
    )
)
