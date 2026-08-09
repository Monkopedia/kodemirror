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
package com.monkopedia.kodemirror.view

import com.monkopedia.kodemirror.basicsetup.basicSetup
import com.monkopedia.kodemirror.lang.javascript.javascript
import com.monkopedia.kodemirror.state.ChangeSpec
import com.monkopedia.kodemirror.state.DocPos
import com.monkopedia.kodemirror.state.EditorState
import com.monkopedia.kodemirror.state.EditorStateConfig
import com.monkopedia.kodemirror.state.Extension
import com.monkopedia.kodemirror.state.SelectionSpec
import com.monkopedia.kodemirror.state.TransactionSpec
import com.monkopedia.kodemirror.state.asDoc
import com.monkopedia.kodemirror.state.asInsert
import com.monkopedia.kodemirror.state.plus
import kotlin.test.assertEquals

/**
 * Shared harness for the `keymap-commands.spec.ts` parity twins.
 *
 * The Playwright suite in `gap-analysis/` is *differential*: it drives a key
 * sequence into real CodeMirror 6 and into KodeMirror in the same browser and
 * asserts they agree, without ever recording what either one did. That oracle
 * cannot exist inside a Compose test, so the twins here assert **absolute**
 * expectations instead — the literals come from
 * `gap-analysis/fixtures/cm6-keymap-expectations.json`, captured from real CM6
 * by `keymap-expectations-capture.spec.ts`. Deriving them from KodeMirror's own
 * behaviour would make each twin a tautology that passes by construction on
 * whichever platform produced it.
 *
 * See `docs/testing-strategy.md` for the parity convention these tests follow.
 */

/**
 * The fixture document from `gap-analysis/fixtures/cm6-test.html`, byte for
 * byte. Every offset in the captured expectations is relative to this and to
 * nothing else, so a change here invalidates all of them — regenerate the
 * capture rather than adjusting a literal.
 */
val PARITY_DOC: String = listOf(
    "// Fibonacci sequence",
    "function fibonacci(n) {",
    "    if (n <= 1) return n;",
    "    return fibonacci(n - 1) + fibonacci(n - 2);",
    "}",
    "",
    "// Print first 10 numbers",
    "for (let i = 0; i < 10; i++) {",
    "    console.log(`fib(\${i}) = \${fibonacci(i)}`);",
    "}"
).joinToString("\n")

/** Length of [PARITY_DOC], asserted against the captured fixture's `initialDoc`. */
const val PARITY_DOC_LENGTH: Int = 229

/** The fixture's first line — the one most of the editing sequences alter. */
const val PARITY_LINE_1: String = "// Fibonacci sequence"

/** The fixture's second line. */
const val PARITY_LINE_2: String = "function fibonacci(n) {"

/** The fixture's third line. */
const val PARITY_LINE_3: String = "    if (n <= 1) return n;"

/** Everything after [PARITY_LINE_3]; untouched by every sequence here. */
val PARITY_TAIL: String = PARITY_DOC.substringAfter("$PARITY_LINE_3\n")

/**
 * The fixture document with its leading lines replaced by [head].
 *
 * Lets an expected document be written as the lines that actually changed
 * instead of a 229-character literal repeated nine times.
 */
fun parityDocWithHead(vararg head: String): String =
    (head.toList() + PARITY_TAIL).joinToString("\n")

/**
 * The extension set the CM6 fixture runs: `basicSetup` plus the JavaScript
 * language. Matching it matters — `Tab` is inert in both because neither adds
 * `indentWithTab`, bracket matching needs the JS syntax tree, and the emacs
 * keymap is absent from both.
 */
val parityExtensions: Extension = basicSetup + javascript().extension

/** A fresh editor session over the pristine fixture document. */
fun parityEditor(): EditorSession = EditorSession(
    EditorState.create(
        EditorStateConfig(doc = PARITY_DOC.asDoc(), extensions = parityExtensions)
    )
)

/**
 * A key press resolved for the current platform: the full name plus the two
 * derived forms [runKeyBindings] needs.
 */
private class PressSpec(val name: String, val nameWithoutShift: String?, val isShift: Boolean)

/**
 * The OS whose bindings the Playwright capture ran against. The differential
 * suite drives a headless Linux browser, so every `Control+…` in the spec is a
 * *non-Mac* key name and must be translated before being pressed here.
 */
private const val CAPTURE_OS = "Linux"

/** Split a key name into its parts, keeping a trailing `-` as the key (`"Ctrl--"`). */
private fun keyParts(name: String): List<String> = name.split(Regex("-(?!$)"))

/** [name] with the `Shift` modifier removed, or [name] itself if it had none. */
private fun withoutShift(name: String): String {
    val parts = keyParts(name)
    if (parts.size <= 1) return name
    val kept = parts.dropLast(1).filter { it != "Shift" } + parts.last()
    return kept.joinToString("-")
}

private fun pressSpec(name: String): PressSpec {
    val bare = withoutShift(name)
    val isShift = bare != name
    return PressSpec(name, if (isShift) bare else null, isShift)
}

/**
 * Translate a key name as the Playwright spec presses it into the name this
 * platform actually binds, then describe it for [runKeyBindings].
 *
 * **This is why a twin must not hardcode `Ctrl`.** `standardKeymap` declares
 * bindings like `KeyBinding(key = "Ctrl-Home", mac = "Meta-Home")` and
 * `KeyBinding(key = "Ctrl-ArrowRight", mac = "Alt-ArrowRight")` — note the two
 * disagree about *which* modifier Mac substitutes, so a blanket `Mod` rule
 * would be wrong too. `platformOsName()` also reports `"Mac"` on every
 * Kotlin/Native target (#217), so a hardcoded `Ctrl` passes on the JVM and
 * fails on macOS and iOS for a reason that is not a bug.
 *
 * The translation is therefore read out of the keymap itself: find the binding
 * whose name on the capture platform is the key the spec pressed, and press
 * whatever that same binding resolves to here. A key no binding claims — the
 * emacs block, `Tab` — is pressed verbatim, which is exactly what pins it as
 * inert.
 */
private fun EditorSession.resolvePress(capturedKey: String): PressSpec {
    val wanted = normalizeKeyName(capturedKey)
    val bindings = state.facet(keymap).asReversed()

    bindings.firstOrNull { resolveBindingKey(it, CAPTURE_OS) == wanted }?.let {
        return pressSpec(resolveBindingKey(it)!!)
    }

    // Shift variants (`Shift-ArrowRight`, `Ctrl-Shift-Home`) are not bindings of
    // their own: they reach `KeyBinding.shift` through the *base* key, so the
    // base is what gets translated and Shift is re-applied afterwards.
    val bare = withoutShift(wanted)
    if (bare != wanted) {
        bindings.firstOrNull {
            it.shift != null && resolveBindingKey(it, CAPTURE_OS) == bare
        }?.let {
            return pressSpec(normalizeKeyName("Shift-${resolveBindingKey(it)!!}"))
        }
    }

    return pressSpec(wanted)
}

/**
 * Press [capturedKey] (the name used in `keymap-commands.spec.ts`, e.g.
 * `"Control+Home"` written as `"Ctrl-Home"`) [times] times.
 *
 * Goes through [runKeyBindings], the same resolution the real key path uses,
 * rather than a private copy that could drift from it.
 */
fun EditorSession.press(capturedKey: String, times: Int = 1) {
    repeat(times) {
        val spec = resolvePress(capturedKey)
        runKeyBindings(
            view = this,
            name = spec.name,
            nameWithoutShift = spec.nameWithoutShift,
            physicalKeyName = null,
            isShift = spec.isShift,
            event = null
        )
    }
}

/**
 * Type [text] one character at a time, exactly as [handleRawKeyEvent] does for
 * an unbound printable key: a single-character replacement of the selection
 * tagged `input.type`, so history groups it the way real typing does.
 */
fun EditorSession.type(text: String) {
    for (ch in text) {
        val sel = state.selection.main
        dispatch(
            TransactionSpec(
                changes = ChangeSpec.Single(
                    from = sel.from,
                    to = sel.to,
                    insert = ch.toString().asInsert()
                ),
                selection = SelectionSpec.CursorSpec(DocPos(sel.from.value + 1)),
                userEvent = "input.type"
            )
        )
    }
}

/**
 * Assert the captured CM6 result: document text, then selection anchor and
 * head. [doc] defaults to the pristine fixture, so a sequence that must not
 * edit says nothing about the document and a sequence that must edit shows the
 * expected text in full.
 */
fun EditorSession.assertParity(anchor: Int, head: Int, doc: String = PARITY_DOC) {
    assertEquals(doc, state.doc.toString(), "document")
    val sel = state.selection.main
    assertEquals(anchor, sel.anchor.value, "selection anchor")
    assertEquals(head, sel.head.value, "selection head")
}

/** [assertParity] for the sequences CM6 left with an empty selection. */
fun EditorSession.assertParityCursor(pos: Int, doc: String = PARITY_DOC) =
    assertParity(anchor = pos, head = pos, doc = doc)

/** The document offset of the cursor (head of the main selection). */
val EditorSession.cursorPos: Int
    get() = state.selection.main.head.value

/**
 * Which modifier this platform binds for the `Mod`-style commands, derived the
 * same way [resolveBindingKey] derives it. Used by the wiring probe to assert
 * that the *other* modifier is inert here.
 */
val boundDocStartKey: String
    get() = normalizeKeyName(if (isMacPlatform) "Meta-Home" else "Ctrl-Home")

/** The modifier this platform does *not* bind for document-start. */
val unboundDocStartKey: String
    get() = normalizeKeyName(if (isMacPlatform) "Ctrl-Home" else "Meta-Home")

/** True when [currentOs] resolves Mac-flavoured bindings. */
val isMacPlatform: Boolean
    get() = currentOs.contains("mac", ignoreCase = true) ||
        currentOs.contains("darwin", ignoreCase = true)

/**
 * Press a fully-resolved key name verbatim, bypassing the capture-platform
 * translation. Only the wiring probe needs this — every twin goes through
 * [press] so it stays platform-correct.
 */
fun EditorSession.pressResolved(name: String) {
    val spec = pressSpec(normalizeKeyName(name))
    runKeyBindings(this, spec.name, spec.nameWithoutShift, null, spec.isShift, null)
}
