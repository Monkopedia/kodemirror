package com.monkopedia.kodemirror.view

import com.monkopedia.kodemirror.state.*
import com.monkopedia.kodemirror.dom.*
import kotlin.js.RegExp
import kotlin.math.abs

/**
 * Configuration for special character handling.
 */
interface SpecialCharConfig {
    /**
     * An optional function that renders the placeholder elements.
     *
     * The `description` argument will be text that clarifies what the
     * character is, which should be provided to screen readers (for
     * example with the `aria-label` attribute) and optionally shown
     * to the user in other ways (such as the `title` attribute).
     *
     * The given placeholder string is a suggestion for how to display
     * the character visually.
     */
    val render: ((code: Int, description: String?, placeholder: String) -> HTMLElement)? 
        get() = null

    /**
     * Regular expression that matches the special characters to highlight.
     * Must have its 'g'/global flag set.
     */
    val specialChars: Regex
        get() = Specials

    /**
     * Regular expression that can be used to add characters to the
     * default set of characters to highlight.
     */
    val addSpecialChars: Regex?
        get() = null
}

private val UnicodeRegexpSupport = if ("/x/".toRegex().hasUnicodeSupport()) "gu" else "g"

private val Specials = Regex(
    "[\u0000-\u0008\u000a-\u001f\u007f-\u009f\u00ad\u061c\u200b\u200e\u200f\u2028\u2029\u202d\u202e\u2066\u2067\u2069\ufeff\ufff9-\ufffc]",
    setOf(RegexOption.UNICODE)
)

private val Names = mapOf(
    0 to "null",
    7 to "bell",
    8 to "backspace",
    10 to "newline",
    11 to "vertical tab",
    13 to "carriage return",
    27 to "escape",
    8203 to "zero width space",
    8204 to "zero width non-joiner",
    8205 to "zero width joiner",
    8206 to "left-to-right mark",
    8207 to "right-to-left mark",
    8232 to "line separator",
    8237 to "left-to-right override",
    8238 to "right-to-left override",
    8294 to "left-to-right isolate",
    8295 to "right-to-left isolate",
    8297 to "pop directional isolate",
    8233 to "paragraph separator",
    65279 to "zero width no-break space",
    65532 to "object replacement"
)

private var _supportsTabSize: Boolean? = null
private fun supportsTabSize(): Boolean {
    if (_supportsTabSize == null && js("typeof document") != "undefined" && js("document.body") != null) {
        val styles = js("document.body.style")
        _supportsTabSize = (styles.tabSize ?: styles.MozTabSize) != null
    }
    return _supportsTabSize ?: false
}

private val specialCharConfig = Facet.define<SpecialCharConfig, Required<SpecialCharConfig>> {
    combine { configs ->
        val config = combineConfig(configs, object : Required<SpecialCharConfig> {
            override var render: ((Int, String?, String) -> HTMLElement)? = null
            override var specialChars: Regex = Specials
            override var addSpecialChars: Regex? = null
            var replaceTabs = !supportsTabSize()
        })

        if (config.replaceTabs) {
            config.specialChars = Regex("\t|${config.specialChars.pattern}", setOf(RegexOption.UNICODE))
        }

        if (config.addSpecialChars != null) {
            config.specialChars = Regex(
                "${config.specialChars.pattern}|${config.addSpecialChars.pattern}",
                setOf(RegexOption.UNICODE)
            )
        }

        config
    }
}

/**
 * Returns an extension that installs highlighting of special characters.
 */
fun highlightSpecialChars(config: SpecialCharConfig = object : SpecialCharConfig {}): Extension {
    return listOf(specialCharConfig.of(config), specialCharPlugin())
}

private var _plugin: Extension? = null
private fun specialCharPlugin(): Extension {
    return _plugin ?: run {
        _plugin = ViewPlugin.fromClass(
            create = { view -> SpecialCharPluginView(view) },
            decorations = { v -> v.decorations }
        )
        _plugin!!
    }
}

// Helper interfaces
private interface Required<T> {
    var render: ((Int, String?, String) -> HTMLElement)?
    var specialChars: Regex
    var addSpecialChars: Regex?
}

private const val DefaultPlaceholder = "\u2022"

// Assigns placeholder characters from the Control Pictures block to ASCII control characters
private fun placeholder(code: Int): String {
    return when {
        code >= 32 -> DefaultPlaceholder
        code == 10 -> "\u2424"
        else -> (9216 + code).toChar().toString()
    }
}

private class SpecialCharWidget(
    private val options: Required<SpecialCharConfig>,
    private val code: Int
) : WidgetType() {

    override fun eq(other: WidgetType): Boolean {
        return other is SpecialCharWidget && other.code == code
    }

    override fun toDOM(view: EditorView): HTMLElement {
        val ph = placeholder(code)
        val desc = view.state.phrase("Control character") + " " + (Names[code] ?: "0x${code.toString(16)}")
        val custom = options.render?.invoke(code, desc, ph)
        if (custom != null) return custom

        return document.createElement("span").apply {
            textContent = ph
            title = desc
            setAttribute("aria-label", desc)
            className = "cm-specialChar"
        }
    }

    override fun ignoreEvent(): Boolean = false
}

private class TabWidget(private val width: Double) : WidgetType() {
    override fun eq(other: WidgetType): Boolean {
        return other is TabWidget && other.width == width
    }

    override fun toDOM(): HTMLElement {
        return document.createElement("span").apply {
            textContent = "\t"
            className = "cm-tab"
            style.width = "${width}px"
        }
    }

    override fun ignoreEvent(): Boolean = false
}

private class SpecialCharPluginView(private val view: EditorView) {
    var decorations: DecorationSet = Decoration.none
    private val decorationCache = mutableMapOf<Int, Decoration>()
    private var decorator: MatchDecorator

    init {
        decorator = makeDecorator(view.state.facet(specialCharConfig))
        decorations = decorator.createDeco(view)
    }

    private fun makeDecorator(conf: Required<SpecialCharConfig>): MatchDecorator {
        return MatchDecorator(
            regexp = conf.specialChars,
            decoration = { m, view, pos ->
                val code = codePointAt(m[0], 0)
                if (code == 9) {
                    val line = view.state.doc.lineAt(pos)
                    val size = view.state.tabSize
                    val col = countColumn(line.text, size, pos - line.from)
                    Decoration.replace(
                        spec = DecorationSpec(
                            widget = TabWidget((size - (col % size)) * view.defaultCharacterWidth / view.scaleX)
                        )
                    )
                } else {
                    decorationCache.getOrPut(code) {
                        Decoration.replace(
                            spec = DecorationSpec(
                                widget = SpecialCharWidget(conf, code)
                            )
                        )
                    }
                }
            },
            boundary = if (conf.replaceTabs) null else Regex("[^]")
        )
    }

    fun update(update: ViewUpdate) {
        val conf = update.state.facet(specialCharConfig)
        if (update.startState.facet(specialCharConfig) != conf) {
            decorator = makeDecorator(conf)
            decorations = decorator.createDeco(update.view)
        } else {
            decorations = decorator.updateDeco(update, decorations)
        }
    }
}

private fun Regex.hasUnicodeSupport(): Boolean {
    val jsRegex = RegExp("/x/")
    return jsRegex.asDynamic().unicode != null
}
