package com.monkopedia.kodemirror.view

import com.monkopedia.kodemirror.state.*
import androidx.compose.ui.input.key.KeyEvent

/**
 * Key bindings associate key names with command-style functions.
 *
 * Key names may be strings like `"Shift-Ctrl-Enter"`—a key identifier
 * prefixed with zero or more modifiers. Key identifiers are based on the strings
 * that can appear in KeyEvent.key. Use lowercase letters to refer to letter keys
 * (or uppercase letters if you want shift to be held). You may use `"Space"`
 * as an alias for the `" "` name.
 *
 * Modifiers can be given in any order. `Shift-` (or `s-`), `Alt-` (or `a-`),
 * `Ctrl-` (or `c-` or `Control-`) and `Cmd-` (or `m-` or `Meta-`) are recognized.
 *
 * You can use `Mod-` as a shorthand for `Cmd-` on Mac and `Ctrl-` on other platforms.
 */
data class KeyBinding(
    /** The key name to use for this binding. */
    val key: String? = null,
    /** Key to use specifically on macOS. */
    val mac: String? = null,
    /** Key to use specifically on Windows. */
    val win: String? = null,
    /** Key to use specifically on Linux. */
    val linux: String? = null,
    /** The command to execute when this binding is triggered. */
    val run: Command? = null,
    /** When given, this defines a second binding using Shift- prefix. */
    val shift: Command? = null,
    /** When present, called for every key that is not a multi-stroke prefix. */
    val any: ((view: EditorView, event: KeyEvent) -> Boolean)? = null,
    /** The scope for this binding (default is "editor"). */
    val scope: String? = null,
    /** Whether to always prevent default handling. */
    val preventDefault: Boolean = false,
    /** Whether to stop event propagation. */
    val stopPropagation: Boolean = false
)

private val currentPlatform = when {
    browser.mac -> "mac"
    browser.windows -> "win"
    browser.linux -> "linux"
    else -> "key"
}

private fun normalizeKeyName(name: String, platform: String): String {
    val parts = name.split("-(?!$)".toRegex())
    var result = parts.last()
    if (result == "Space") result = " "
    var alt = false
    var ctrl = false
    var shift = false
    var meta = false
    
    for (i in 0 until parts.size - 1) {
        val mod = parts[i]
        when {
            mod.matches("^(cmd|meta|m)$".toRegex(RegexOption.IGNORE_CASE)) -> meta = true
            mod.matches("^a(lt)?$".toRegex(RegexOption.IGNORE_CASE)) -> alt = true
            mod.matches("^(c|ctrl|control)$".toRegex(RegexOption.IGNORE_CASE)) -> ctrl = true
            mod.matches("^s(hift)?$".toRegex(RegexOption.IGNORE_CASE)) -> shift = true
            mod.matches("^mod$".toRegex(RegexOption.IGNORE_CASE)) -> {
                if (platform == "mac") meta = true else ctrl = true
            }
            else -> throw IllegalArgumentException("Unrecognized modifier name: $mod")
        }
    }
    if (alt) result = "Alt-$result"
    if (ctrl) result = "Ctrl-$result"
    if (meta) result = "Meta-$result"
    if (shift) result = "Shift-$result"
    return result
}

private fun modifiers(name: String, event: KeyEvent, shift: Boolean): String {
    var result = name
    if (event.altKey) result = "Alt-$result"
    if (event.ctrlKey) result = "Ctrl-$result"
    if (event.metaKey) result = "Meta-$result"
    if (shift && event.shiftKey) result = "Shift-$result"
    return result
}

private data class Binding(
    val preventDefault: Boolean,
    val stopPropagation: Boolean,
    val run: List<(view: EditorView) -> Boolean>
)

// In each scope, the `_any` property is used for bindings that apply to all keys
private typealias Keymap = Map<String, Map<String, Binding>>

private val handleKeyEvents = Prec.default(EditorView.domEventHandlers(mapOf(
    "keydown" to { event: KeyEvent, view: EditorView ->
        runHandlers(getKeymap(view.state), event, view, "editor")
    }
)))

/**
 * Facet used for registering keymaps.
 *
 * You can add multiple keymaps to an editor. Their priorities
 * determine their precedence (the ones specified early or with high
 * priority get checked first). When a handler has returned `true`
 * for a given key, no further handlers are called.
 */
val keymap = Facet.define<List<KeyBinding>> { enables = handleKeyEvents }

private val Keymaps = WeakMap<List<List<KeyBinding>>, Keymap>()

private fun getKeymap(state: EditorState): Keymap {
    val bindings = state.facet(keymap)
    return Keymaps.get(bindings) ?: buildKeymap(bindings.flatten()).also { Keymaps.set(bindings, it) }
}

/**
 * Run the key handlers registered for a given scope. The event
 * object should be a "keydown" event. Returns true if any of the
 * handlers handled it.
 */
fun runScopeHandlers(view: EditorView, event: KeyEvent, scope: String): Boolean {
    return runHandlers(getKeymap(view.state), event, view, scope)
}

private var storedPrefix: PrefixState? = null

private data class PrefixState(
    val view: EditorView,
    val prefix: String,
    val scope: String
)

private const val PrefixTimeout = 4000L

private fun buildKeymap(bindings: List<KeyBinding>, platform: String = currentPlatform): Keymap {
    val bound = mutableMapOf<String, MutableMap<String, Binding>>()
    val isPrefix = mutableMapOf<String, Boolean>()

    fun checkPrefix(name: String, is_: Boolean) {
        val current = isPrefix[name]
        if (current == null) {
            isPrefix[name] = is_
        } else if (current != is_) {
            throw IllegalArgumentException("Key binding $name is used both as a regular binding and as a multi-stroke prefix")
        }
    }

    fun add(
        scope: String,
        key: String,
        command: Command?,
        preventDefault: Boolean = false,
        stopPropagation: Boolean = false
    ) {
        val scopeObj = bound.getOrPut(scope) { mutableMapOf() }
        val parts = key.split(" (?!$)".toRegex()).map { normalizeKeyName(it, platform) }
        
        for (i in 1 until parts.size) {
            val prefix = parts.subList(0, i).joinToString(" ")
            checkPrefix(prefix, true)
            if (!scopeObj.containsKey(prefix)) {
                scopeObj[prefix] = Binding(
                    preventDefault = true,
                    stopPropagation = false,
                    run = listOf { view ->
                        val ourObj = PrefixState(view, prefix, scope).also { storedPrefix = it }
                        view.scheduleCallback({
                            if (storedPrefix == ourObj) storedPrefix = null
                        }, PrefixTimeout)
                        true
                    }
                )
            }
        }

        val full = parts.joinToString(" ")
        checkPrefix(full, false)
        val binding = scopeObj.getOrPut(full) {
            Binding(
                preventDefault = false,
                stopPropagation = false,
                run = scopeObj["_any"]?.run?.toList() ?: emptyList()
            )
        }
        if (command != null) binding.run += command
        if (preventDefault) binding.preventDefault = true
        if (stopPropagation) binding.stopPropagation = true
    }

    for (b in bindings) {
        val scopes = b.scope?.split(" ") ?: listOf("editor")
        if (b.any != null) {
            for (scope in scopes) {
                val scopeObj = bound.getOrPut(scope) { mutableMapOf() }
                if (!scopeObj.containsKey("_any")) {
                    scopeObj["_any"] = Binding(preventDefault = false, stopPropagation = false, run = emptyList())
                }
                val any = b.any
                for (key in scopeObj.keys) {
                    scopeObj[key]!!.run += { view -> any(view, currentKeyEvent!!) }
                }
            }
        }
        val name = b.run { when (platform) {
            "mac" -> mac
            "win" -> win
            "linux" -> linux
            else -> key
        }} ?: continue
        
        for (scope in scopes) {
            add(scope, name, b.run, b.preventDefault, b.stopPropagation)
            if (b.shift != null) {
                add(scope, "Shift-$name", b.shift, b.preventDefault, b.stopPropagation)
            }
        }
    }
    return bound
}

private var currentKeyEvent: KeyEvent? = null

private fun runHandlers(map: Keymap, event: KeyEvent, view: EditorView, scope: String): Boolean {
    currentKeyEvent = event
    val name = keyName(event)
    val charCode = codePointAt(name, 0)
    val isChar = codePointSize(charCode) == name.length && name != " "
    
    var prefix = ""
    var handled = false
    var prevented = false
    var stopPropagation = false
    
    storedPrefix?.let { stored ->
        if (stored.view == view && stored.scope == scope) {
            prefix = "${stored.prefix} "
            if (event.keyCode !in modifierCodes) {
                prevented = true
                storedPrefix = null
            }
        }
    }

    val ran = mutableSetOf<(EditorView) -> Boolean>()
    fun runFor(binding: Binding?) {
        if (binding != null) {
            for (cmd in binding.run) {
                if (!ran.contains(cmd)) {
                    ran.add(cmd)
                    if (cmd(view)) {
                        if (binding.stopPropagation) stopPropagation = true
                        handled = true
                        return
                    }
                }
            }
            if (binding.preventDefault) {
                if (binding.stopPropagation) stopPropagation = true
                prevented = true
            }
        }
    }

    val scopeObj = map[scope]
    if (scopeObj != null) {
        val baseName: String?
        val shiftName: String?
        if (runFor(scopeObj[prefix + modifiers(name, event, !isChar)])) {
            handled = true
        } else if (isChar && (event.altKey || event.metaKey || event.ctrlKey) &&
            !(browser.windows && event.ctrlKey && event.altKey) &&
            base[event.keyCode].also { baseName = it } != null && baseName != name) {
            if (runFor(scopeObj[prefix + modifiers(baseName!!, event, true)])) {
                handled = true
            } else if (event.shiftKey && shift[event.keyCode].also { shiftName = it } != name && shiftName != baseName &&
                runFor(scopeObj[prefix + modifiers(shiftName!!, event, false)])) {
                handled = true
            }
        } else if (isChar && event.shiftKey &&
            runFor(scopeObj[prefix + modifiers(name, event, true)])) {
            handled = true
        }
        if (!handled && runFor(scopeObj["_any"])) handled = true
    }

    if (prevented) handled = true
    if (handled && stopPropagation) event.stopPropagation()
    currentKeyEvent = null
    return handled
}

// Key codes for modifier keys
val modifierCodes = listOf(16, 17, 18, 20, 91, 92, 224, 225)
