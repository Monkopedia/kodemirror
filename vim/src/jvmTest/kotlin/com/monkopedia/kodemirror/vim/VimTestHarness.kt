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
package com.monkopedia.kodemirror.vim

import com.monkopedia.kodemirror.commands.history
import com.monkopedia.kodemirror.language.indentService
import com.monkopedia.kodemirror.state.DocPos
import com.monkopedia.kodemirror.state.EditorState
import com.monkopedia.kodemirror.state.EditorStateConfig
import com.monkopedia.kodemirror.state.LineNumber
import com.monkopedia.kodemirror.state.SelectionSpec
import com.monkopedia.kodemirror.state.asDoc
import com.monkopedia.kodemirror.state.extensionListOf
import com.monkopedia.kodemirror.view.EditorSession
import com.monkopedia.kodemirror.view.ViewUpdate
import kotlin.test.assertEquals
import kotlin.test.fail

/**
 * Default code used in vim tests, matching the upstream vim_test.js `code` variable.
 */
val DEFAULT_CODE = buildString {
    appendLine(" wOrd1 (#%")
    appendLine(" word3] ")
    appendLine("aopop pop 0 1 2 3 4")
    appendLine(" (a) [b] {c} ")
    appendLine("int getchar(void) {")
    appendLine("  static char buf[BUFSIZ];")
    appendLine("  static char *bufp = buf;")
    appendLine("  if (n == 0) {  /* buffer is empty */")
    appendLine("    n = read(0, buf, sizeof buf);")
    appendLine("    bufp = buf;")
    appendLine("  }")
    appendLine()
    appendLine("  return (--n >= 0) ? (unsigned char) *bufp++ : EOF;")
    appendLine(" ")
    appendLine("}")
}

/**
 * Helper class providing vim test utilities, mirroring the upstream `helpers` object.
 */
internal class VimHelpers(
    val cm: VimEditor,
    val vim: VimState
) {
    /**
     * Simulate pressing vim keys. Each argument is a single key or a special
     * key in angle brackets like `<Esc>`, `<C-r>`, `<CR>`.
     */
    fun doKeys(vararg keys: String) {
        for (key in keys) {
            // Pass keys directly in vim notation — handleKey expects <C-r>, not Ctrl-r
            typeKey(cm, key)
        }
    }

    /**
     * Execute an ex command (e.g., `doEx("s/foo/bar/g")`).
     * This simulates typing `:command\n`.
     */
    fun doEx(command: String) {
        doKeys(":")
        // Feed the command characters and Enter
        for (ch in command) {
            typeKey(cm, ch.toString())
        }
        typeKey(cm, "Enter")
    }

    /**
     * Assert the cursor is at the given position.
     */
    fun assertCursorAt(line: Int, ch: Int) {
        val actual = cm.getCursor()
        assertEquals(
            LinePos(line, ch),
            LinePos(actual.line, actual.ch),
            "Expected cursor at ($line, $ch) but was at (${actual.line}, ${actual.ch})"
        )
    }

    /**
     * Assert the cursor is at the given LinePos.
     */
    fun assertCursorAt(pos: LinePos) = assertCursorAt(pos.line, pos.ch)

    /**
     * Get the register controller for inspecting register contents.
     */
    internal fun getRegisterController() = Vim.getRegisterController(cm)

    /**
     * Get the current selection text.
     */
    fun getSelection(): String = cm.getSelection()
}

/**
 * Convert vim key notation to a key name.
 * e.g., "C-r" → "Ctrl-r", "CR" → "Return", "BS" → "Backspace"
 */
private fun vimKeyToKeyName(key: String): String {
    return key.replace(Regex("[CS]-|CR|BS")) { match ->
        when (match.value) {
            "C-" -> "Ctrl-"
            "S-" -> "Shift-"
            "CR" -> "Return"
            "BS" -> "Backspace"
            else -> match.value
        }
    }
}

/**
 * Simulate typing a key in vim mode.
 * When in insert mode and the key is a printable character that vim doesn't handle,
 * insert it into the document (simulating what EditorView would do).
 */
// Maps browser-style key names to vim notation, matching upstream vimKeyFromEvent's specialKey map
private val keyNameToVim = mapOf(
    "Space" to "<Space>",
    "Enter" to "<CR>",
    "Return" to "<CR>",
    "Backspace" to "<BS>",
    "Delete" to "<Del>",
    "Escape" to "<Esc>",
    "Insert" to "<Ins>",
    "ArrowLeft" to "<Left>",
    "ArrowRight" to "<Right>",
    "ArrowUp" to "<Up>",
    "ArrowDown" to "<Down>"
)

internal fun typeKey(cm: VimEditor, key: String) {
    // Normalize browser-style key names to vim notation
    val normalizedKey = keyNameToVim[key] ?: key
    val handled = Vim.handleKey(cm, normalizedKey, "user")
    if (!handled) {
        val vim = cm.vim
        if (vim != null && vim.insertMode) {
            when {
                key.length == 1 -> {
                    // Simulate text input: insert the character at the cursor.
                    // Use replaceSelection which places the cursor after the
                    // inserted text (matching real EditorView input handling).
                    val selCount = cm.listSelections().size
                    cm.replaceSelection(key)
                    // Record the change for macro/repeat tracking.
                    // With multi-cursor, create a chain of Changes (one per
                    // selection) so the ignoreCount logic works correctly.
                    val first = Change(text = listOf(key))
                    var last = first
                    for (i in 1 until selCount) {
                        val next = Change(text = listOf(key))
                        last.next = next
                        last = next
                    }
                    // Drive recording through the registered "change" handlers,
                    // exactly as the editor's operation pipeline does, rather
                    // than calling the recorder directly. Calling onChange here
                    // masked the missing change-event wiring behind #21.
                    cm.events.getHandlers("change")?.forEach { it(arrayOf(cm, first)) }
                }
                key == "Enter" || key == "Return" -> {
                    // Simulate Enter in insert mode: insert newline with
                    // indentation, matching what EditorView would do.
                    cm.execCommand("newlineAndIndent")
                    onChange(cm, Change(text = listOf("", "")))
                }
                key == "Backspace" || key == "Delete" -> {
                    // Record as InsertModeKey for macro/repeat tracking,
                    // matching upstream onKeyEventTargetKeyDown behavior.
                    val lastChange =
                        cm.vimContext.macroModeState.lastInsertModeChanges
                    if (lastChange.maybeReset == true) {
                        lastChange.changes.clear()
                        lastChange.maybeReset = false
                    }
                    lastChange.changes.add(InsertModeKey(key))
                    // Simulate the editor's default key handling
                    sendCmKey(cm, key)
                }
            }
        }
    }
}

/**
 * Run a vim test with the given initial document and cursor position.
 *
 * Usage:
 * ```kotlin
 * @Test fun myTest() = testVim(value = "hello\nworld", cursor = LinePos(0, 0)) { helpers ->
 *     helpers.doKeys("d", "d")
 *     assertEquals("world", helpers.cm.getValue())
 * }
 * ```
 */
internal fun testVim(
    value: String = DEFAULT_CODE,
    cursor: LinePos? = null,
    fn: (VimHelpers) -> Unit
) {
    // Create editor state
    val cursorOffset = if (cursor != null) {
        val lines = value.split("\n")
        var offset = 0
        for (i in 0 until cursor.line.coerceAtMost(lines.size - 1)) {
            offset += lines[i].length + 1
        }
        offset + cursor.ch.coerceAtMost(
            lines.getOrElse(cursor.line) { "" }.length
        )
    } else {
        0
    }

    val state = EditorState.create(
        EditorStateConfig(
            doc = value.asDoc(),
            selection = SelectionSpec.CursorSpec(DocPos(cursorOffset)),
            extensions = extensionListOf(
                vimContextField,
                history(),
                EditorState.allowMultipleSelections.of(true),
                indentService.of { context, pos ->
                    // Basic bracket-aware indentation for tests.
                    // When simulateBreak is set, the break position's
                    // line determines indent. Otherwise, use the
                    // previous line.
                    val line = context.lineAt(pos)
                    val textBefore = if (context.simulateBreak != null) {
                        val breakLine = context.lineAt(context.simulateBreak!!)
                        val col = context.simulateBreak!!.value -
                            breakLine.from.value
                        breakLine.text.substring(0, col)
                    } else if (line.number.value > 1) {
                        context.state.doc.line(
                            LineNumber(line.number.value - 1)
                        ).text
                    } else {
                        return@of 0
                    }
                    val baseIndent = textBefore.takeWhile {
                        it == ' ' || it == '\t'
                    }.sumOf { if (it == '\t') 4 else 1 }
                    val trimmed = textBefore.trimEnd()
                    val extra = if (trimmed.endsWith("{") ||
                        trimmed.endsWith("[") ||
                        trimmed.endsWith("(")
                    ) {
                        2
                    } else {
                        0
                    }
                    baseIndent + extra
                }
            )
        )
    )
    var cm: VimEditor? = null
    val session = EditorSession(state) { tr ->
        // Lightweight change tracking: keep line handles, marks, and
        // lastChangeEndOffset in sync for the headless test environment
        // (no VimExtension plugin to call onChange).
        val editor = cm ?: return@EditorSession
        if (editor.vimPlugin != null) return@EditorSession
        if (tr.docChanged) {
            val update = ViewUpdate(
                session = editor.session,
                state = editor.session.state,
                transactions = listOf(tr)
            )
            editor.onChangeLight(update)
        }
    }
    cm = VimEditor(session)

    // Initialize vim mode
    val vim = Vim.maybeInitVimState_(cm)
    Vim.resetVimGlobalState_()
    resetVimContext(cm)

    val helpers = VimHelpers(cm, vim)

    fn(helpers)
}

/**
 * Shorthand for testing a motion: press keys, assert cursor position.
 */
fun testMotion(keys: List<String>, endPos: LinePos, startPos: LinePos = LinePos(0, 0)) =
    testVim(cursor = startPos) { helpers ->
        helpers.doKeys(*keys.toTypedArray())
        helpers.assertCursorAt(endPos)
    }

/**
 * Test an edit operation. Mirrors the upstream `testEdit` function.
 *
 * @param before The initial document text
 * @param pos A regex to find the cursor position in the `before` string
 * @param edit The keys to press (each character is a separate key)
 * @param after The expected document text after the edit
 */
fun testEdit(before: String, pos: Regex, edit: String, after: String) =
    testVim(value = before) { h ->
        val matchIdx = pos.find(before)?.range?.first
            ?: fail("Regex $pos not found in '$before'")
        val beforeMatch = before.substring(0, matchIdx)
        val lines = beforeMatch.split("\n")
        val line = lines.size - 1
        val ch = lines.last().length
        h.cm.setCursor(line, ch)
        h.doKeys(*edit.map { it.toString() }.toTypedArray())
        assertEquals(after, h.cm.getValue())
    }

/**
 * Test a selection operation. Mirrors the upstream `testSelection` function.
 *
 * @param before The initial document text
 * @param pos A regex to find the cursor position in the `before` string
 * @param keys The keys to press (each character is a separate key)
 * @param expectedSel The expected selection text
 */
fun testSelection(before: String, pos: Regex, keys: String, expectedSel: String) =
    testVim(value = before) { h ->
        val matchIdx = pos.find(before)?.range?.first
            ?: fail("Regex $pos not found in '$before'")
        val beforeMatch = before.substring(0, matchIdx)
        val lines = beforeMatch.split("\n")
        val line = lines.size - 1
        val ch = lines.last().length
        h.cm.setCursor(line, ch)
        h.doKeys(*keys.map { it.toString() }.toTypedArray())
        assertEquals(expectedSel, h.getSelection())
    }

/**
 * Test a substitute command. Runs the test twice — once with pcre=true and once with
 * pcre=false. Mirrors the upstream `testSubstitute` function.
 *
 * @param value The initial document text
 * @param expr The substitute expression (used when pcre=true)
 * @param expectedValue The expected result
 * @param noPcreExpr The substitute expression for pcre=false (defaults to [expr])
 */
fun testSubstitute(value: String, expr: String, expectedValue: String, noPcreExpr: String? = null) {
    // Test with pcre=true
    testVim(value = value, cursor = LinePos(1, 0)) { h ->
        Vim.setOption("pcre", true)
        h.doEx(expr)
        assertEquals(expectedValue, h.cm.getValue(), "pcre=true")
    }
    // Test with pcre=false
    testVim(value = value, cursor = LinePos(1, 0)) { h ->
        Vim.setOption("pcre", false)
        h.doEx(noPcreExpr ?: expr)
        assertEquals(expectedValue, h.cm.getValue(), "pcre=false")
    }
}

/**
 * Test substitute with confirm flag. Mirrors the upstream `testSubstituteConfirm`.
 *
 * @param command The ex substitute command (e.g., "%s/a/b/cg")
 * @param initial The initial document text
 * @param expected The expected result after confirm keys
 * @param keys The confirm response keys (y/n/a/q/l)
 * @param finalPos The expected final cursor position
 */
fun testSubstituteConfirm(
    command: String,
    initial: String,
    expected: String,
    keys: String,
    finalPos: LinePos
) = testVim(value = initial) { h ->
    h.doEx(command)
    for (ch in keys) {
        typeKey(h.cm, ch.toString())
    }
    assertEquals(expected, h.cm.getValue())
    h.assertCursorAt(finalPos)
}

/**
 * Test jumplist behavior. Mirrors the upstream `testJumplist` function.
 */
fun testJumplist(keys: List<String>, endPos: LinePos, startPos: LinePos, value: String) =
    testVim(value = value) { h ->
        Vim.resetVimGlobalState_()
        resetVimContext(h.cm)
        h.cm.setCursor(startPos.line, startPos.ch)
        h.doKeys(*keys.toTypedArray())
        h.assertCursorAt(endPos)
    }
