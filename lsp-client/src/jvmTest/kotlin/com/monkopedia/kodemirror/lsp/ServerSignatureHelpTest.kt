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
package com.monkopedia.kodemirror.lsp

import androidx.compose.ui.text.font.FontWeight
import com.monkopedia.kodemirror.state.EditorState
import com.monkopedia.lsp.LanguageServer
import com.monkopedia.lsp.ParameterInformation
import com.monkopedia.lsp.SignatureHelp
import com.monkopedia.lsp.SignatureInformation
import java.lang.reflect.Proxy
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotSame
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive

class ServerSignatureHelpTest {

    private fun param(label: kotlinx.serialization.json.JsonElement) =
        ParameterInformation(label = label)

    private fun strParam(label: String) = param(JsonPrimitive(label))

    private fun offsetParam(from: Int, to: Int) =
        param(JsonArray(listOf(JsonPrimitive(from), JsonPrimitive(to))))

    private fun signature(
        label: String,
        parameters: List<ParameterInformation>? = null,
        activeParameter: UInt? = null
    ) = SignatureInformation(
        label = label,
        parameters = parameters,
        activeParameter = activeParameter
    )

    // --- activeParamRange: string label vs [start, end] offset label ---

    @Test
    fun stringLabelResolvedAsSubstring() {
        val sig = signature("foo(a: Int, b: Int)", listOf(strParam("a: Int"), strParam("b: Int")))
        // Help-level active parameter = 1 -> "b: Int".
        val range = activeParamRange(sig, helpActiveParameter = 1u)
        assertEquals(ActiveParamRange(12, 18), range)
        assertEquals("b: Int", sig.label.substring(range!!.from, range.to))
    }

    @Test
    fun offsetLabelResolvedDirectly() {
        val sig = signature("foo(a, b)", listOf(offsetParam(4, 5), offsetParam(7, 8)))
        val range = activeParamRange(sig, helpActiveParameter = 0u)
        assertEquals(ActiveParamRange(4, 5), range)
    }

    @Test
    fun signatureActiveParameterOverridesHelpLevel() {
        val sig = signature(
            "f(x, y)",
            listOf(offsetParam(2, 3), offsetParam(5, 6)),
            activeParameter = 1u
        )
        // Help says 0, but the signature's own activeParameter (1) wins.
        val range = activeParamRange(sig, helpActiveParameter = 0u)
        assertEquals(ActiveParamRange(5, 6), range)
    }

    @Test
    fun nullWhenNoParameters() {
        assertNull(activeParamRange(signature("f()"), helpActiveParameter = 0u))
    }

    @Test
    fun nullWhenNoActiveParameter() {
        val sig = signature("f(a)", listOf(strParam("a")))
        assertNull(activeParamRange(sig, helpActiveParameter = null))
    }

    @Test
    fun nullWhenIndexOutOfRange() {
        val sig = signature("f(a)", listOf(strParam("a")))
        assertNull(activeParamRange(sig, helpActiveParameter = 5u))
    }

    @Test
    fun nullWhenStringLabelNotFound() {
        val sig = signature("f(a)", listOf(strParam("zzz")))
        assertNull(activeParamRange(sig, helpActiveParameter = 0u))
    }

    // --- signatureLabel: active parameter highlighting ---

    @Test
    fun labelHighlightsActiveParameter() {
        val out = signatureLabel("foo(a, b)", ActiveParamRange(4, 5))
        assertEquals("foo(a, b)", out.text)
        val bold = out.spanStyles.first { it.item.fontWeight == FontWeight.Bold }
        assertEquals(4, bold.start)
        assertEquals(5, bold.end)
    }

    @Test
    fun labelVerbatimWhenNoRange() {
        val out = signatureLabel("foo(a, b)", null)
        assertEquals("foo(a, b)", out.text)
        assertTrue(out.spanStyles.isEmpty())
    }

    @Test
    fun labelVerbatimWhenRangeOutOfBounds() {
        val out = signatureLabel("foo", ActiveParamRange(2, 10))
        assertEquals("foo", out.text)
        assertTrue(out.spanStyles.isEmpty())
    }

    // --- sameSignatures / sameActiveParam ---

    @Test
    fun sameSignaturesByLabel() {
        val a = SignatureHelp(signatures = listOf(signature("f(a)"), signature("g(b)")))
        val b = SignatureHelp(signatures = listOf(signature("f(a)"), signature("g(b)")))
        assertTrue(sameSignatures(a, b))
    }

    @Test
    fun differentSignaturesByLength() {
        val a = SignatureHelp(signatures = listOf(signature("f(a)")))
        val b = SignatureHelp(signatures = listOf(signature("f(a)"), signature("g(b)")))
        assertFalse(sameSignatures(a, b))
    }

    @Test
    fun differentSignaturesByLabel() {
        val a = SignatureHelp(signatures = listOf(signature("f(a)")))
        val b = SignatureHelp(signatures = listOf(signature("f(b)")))
        assertFalse(sameSignatures(a, b))
    }

    @Test
    fun sameActiveParamHelpLevel() {
        val a = SignatureHelp(signatures = listOf(signature("f(a)")), activeParameter = 0u)
        val b = SignatureHelp(signatures = listOf(signature("f(a)")), activeParameter = 0u)
        assertTrue(sameActiveParam(a, b, 0))
    }

    @Test
    fun differentActiveParam() {
        val a = SignatureHelp(signatures = listOf(signature("f(a)")), activeParameter = 0u)
        val b = SignatureHelp(signatures = listOf(signature("f(a)")), activeParameter = 1u)
        assertFalse(sameActiveParam(a, b, 0))
    }

    @Test
    fun signatureActiveParamWinsInSameActiveParam() {
        val a = SignatureHelp(
            signatures = listOf(signature("f(a)", activeParameter = 2u)),
            activeParameter = 0u
        )
        val b = SignatureHelp(
            signatures = listOf(signature("f(a)", activeParameter = 2u)),
            activeParameter = 9u
        )
        // Signature-level activeParameter (2 == 2) is used, ignoring help-level.
        assertTrue(sameActiveParam(a, b, 0))
    }

    // --- signature cycling: next/prev clamp at the ends ---

    @Test
    fun nextAdvancesUntilLast() {
        assertEquals(1, nextActive(0, 3))
        assertEquals(2, nextActive(1, 3))
        // Clamp at last.
        assertEquals(2, nextActive(2, 3))
    }

    @Test
    fun prevDecrementsUntilFirst() {
        assertEquals(0, prevActive(1))
        // Clamp at first.
        assertEquals(0, prevActive(0))
    }

    // --- multi-instance: per-editor signature-help binding ---

    /**
     * A do-nothing [LanguageServer] used only to construct distinct [LSPClient]s;
     * none of its methods are invoked because [signatureHelp] merely stores the
     * client in the [signatureHelpServer] facet (it does not contact the server).
     */
    private fun stubServer(): LanguageServer = Proxy.newProxyInstance(
        LanguageServer::class.java.classLoader,
        arrayOf(LanguageServer::class.java)
    ) { _, method, _ ->
        error("stub LanguageServer.${method.name} should not be called in this test")
    } as LanguageServer

    @Test
    fun twoEditorsKeepIndependentSignatureHelpBindings() {
        val clientA = LSPClient(stubServer())
        val clientB = LSPClient(stubServer())

        val stateA = EditorState.create(
            doc = "a",
            extensions = signatureHelp(clientA, "file:///a.kt")
        )
        // Installing a second editor must NOT clobber the first one's binding,
        // which was the original module-level `activeSignaturePlugin` bug.
        val stateB = EditorState.create(
            doc = "b",
            extensions = signatureHelp(clientB, "file:///b.kt")
        )

        val bindingA = stateA.facet(signatureHelpServer)
        val bindingB = stateB.facet(signatureHelpServer)

        // Each editor resolves its OWN client/uri from its own state.
        assertEquals("file:///a.kt", bindingA?.uri)
        assertSame(clientA, bindingA?.client)
        assertEquals("file:///b.kt", bindingB?.uri)
        assertSame(clientB, bindingB?.client)

        // The second install did not overwrite the first (no shared mutable state).
        assertNotSame(bindingA, bindingB)
        assertSame(clientA, stateA.facet(signatureHelpServer)?.client)
    }

    @Test
    fun editorWithoutSignatureHelpHasNullBinding() {
        val state = EditorState.create(doc = "x")
        assertNull(state.facet(signatureHelpServer))
    }
}
