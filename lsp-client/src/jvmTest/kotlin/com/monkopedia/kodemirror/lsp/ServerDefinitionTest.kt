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

import com.monkopedia.kodemirror.state.EditorState
import com.monkopedia.kodemirror.state.Text
import com.monkopedia.kodemirror.view.EditorSession
import com.monkopedia.lsp.BooleanOr
import com.monkopedia.lsp.DefinitionOptions
import com.monkopedia.lsp.LanguageServer
import java.lang.reflect.Proxy
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotSame
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

class ServerDefinitionTest {

    private fun rangeJson(startLine: Int, startChar: Int, endLine: Int, endChar: Int): JsonObject =
        buildJsonObject {
            put(
                "start",
                buildJsonObject {
                    put("line", startLine)
                    put("character", startChar)
                }
            )
            put(
                "end",
                buildJsonObject {
                    put("line", endLine)
                    put("character", endChar)
                }
            )
        }

    private fun locationJson(uri: String, line: Int, char: Int): JsonObject = buildJsonObject {
        put("uri", uri)
        put("range", rangeJson(line, char, line, char + 1))
    }

    private fun locationLinkJson(uri: String, line: Int, char: Int): JsonObject = buildJsonObject {
        put("targetUri", uri)
        put("targetRange", rangeJson(line, 0, line, 20))
        put("targetSelectionRange", rangeJson(line, char, line, char + 4))
    }

    // --- result-union parsing: Location vs Location[] vs LocationLink[] vs null ---

    @Test
    fun parsesSingleLocation() {
        val target = parseDefinitionResult(locationJson("file:///a.kt", 3, 7))
        assertEquals("file:///a.kt", target?.uri)
        assertEquals(3u, target?.range?.start?.line)
        assertEquals(7u, target?.range?.start?.character)
    }

    @Test
    fun parsesLocationArrayPicksFirst() {
        val arr = buildJsonArray {
            add(locationJson("file:///first.kt", 1, 2))
            add(locationJson("file:///second.kt", 9, 9))
        }
        val target = parseDefinitionResult(arr)
        // Upstream uses response[0] for arrays.
        assertEquals("file:///first.kt", target?.uri)
        assertEquals(1u, target?.range?.start?.line)
    }

    @Test
    fun parsesSingleLocationLinkUsesTargetSelectionRange() {
        val target = parseDefinitionResult(locationLinkJson("file:///b.kt", 5, 4))
        assertEquals("file:///b.kt", target?.uri)
        // targetSelectionRange start (not targetRange).
        assertEquals(5u, target?.range?.start?.line)
        assertEquals(4u, target?.range?.start?.character)
    }

    @Test
    fun parsesLocationLinkArrayPicksFirst() {
        val arr = buildJsonArray {
            add(locationLinkJson("file:///x.kt", 2, 1))
            add(locationLinkJson("file:///y.kt", 8, 8))
        }
        val target = parseDefinitionResult(arr)
        assertEquals("file:///x.kt", target?.uri)
        assertEquals(2u, target?.range?.start?.line)
    }

    @Test
    fun parsesNullResults() {
        assertNull(parseDefinitionResult(null))
        assertNull(parseDefinitionResult(JsonNull))
        assertNull(parseDefinitionResult(JsonArray(emptyList())))
        assertNull(parseDefinitionResult(JsonPrimitive("nonsense")))
    }

    // --- target-range -> document offset resolution ---

    @Test
    fun targetRangeStartResolvesToOffset() {
        val doc = Text.of(listOf("line0", "line1", "XXXXXX"))
        // Target on line 2 (0-based), character 3 -> offset of "X" + 3.
        val target = parseDefinitionResult(locationJson("file:///a.kt", 2, 3))!!
        val offset = fromPosition(target.range.start, doc)
        // "line0\n" = 6, "line1\n" = 12, +3 = 15.
        assertEquals(15, offset)
    }

    @Test
    fun targetRangeStartFromLocationLinkResolvesToOffset() {
        val doc = Text.of(listOf("abc", "defgh"))
        val target = parseDefinitionResult(locationLinkJson("file:///a.kt", 1, 2))!!
        val offset = fromPosition(target.range.start, doc)
        // "abc\n" = 4, +2 = 6.
        assertEquals(6, offset)
    }

    // --- hasProvider: capability presence semantics (matches upstream hasCapability) ---

    @Test
    fun providerPresenceSemantics() {
        assertFalse(hasProvider(null))
        assertFalse(hasProvider(BooleanOr.BooleanValue(false)))
        assertTrue(hasProvider(BooleanOr.BooleanValue(true)))
        assertTrue(hasProvider(BooleanOr.Value(DefinitionOptions())))
    }

    // --- multi-instance: per-editor go-to-definition binding ---

    /**
     * A do-nothing [LanguageServer] used only to construct distinct [LSPClient]s;
     * [definitionJumps] merely stores the client in the [definitionServer] facet,
     * it does not contact the server at install time.
     */
    private fun stubServer(): LanguageServer = Proxy.newProxyInstance(
        LanguageServer::class.java.classLoader,
        arrayOf(LanguageServer::class.java)
    ) { _, method, _ ->
        error("stub LanguageServer.${method.name} should not be called in this test")
    } as LanguageServer

    @Test
    fun twoEditorsKeepIndependentDefinitionBindings() {
        val clientA = LSPClient(stubServer())
        val clientB = LSPClient(stubServer())

        val stateA = EditorState.create(
            doc = "a",
            extensions = definitionJumps(clientA, "file:///a.kt")
        )
        // Installing a second editor must NOT clobber the first one's binding —
        // the per-editor Facet design (no module-level mutable state) guards
        // against the multi-instance bug we fixed in signature help.
        val stateB = EditorState.create(
            doc = "b",
            extensions = definitionJumps(clientB, "file:///b.kt")
        )

        val bindingA = stateA.facet(definitionServer)
        val bindingB = stateB.facet(definitionServer)

        assertEquals("file:///a.kt", bindingA?.uri)
        assertSame(clientA, bindingA?.client)
        assertEquals("file:///b.kt", bindingB?.uri)
        assertSame(clientB, bindingB?.client)

        assertNotSame(bindingA, bindingB)
        // The second install did not overwrite the first.
        assertSame(clientA, stateA.facet(definitionServer)?.client)
    }

    @Test
    fun editorWithoutDefinitionJumpsHasNullBinding() {
        val state = EditorState.create(doc = "x")
        assertNull(state.facet(definitionServer))
    }

    @Test
    fun jumpCommandReturnsFalseWithoutBinding() {
        // No definitionJumps installed -> command is not handled (returns false),
        // so a keymap can fall through to other handlers (and nothing touches the
        // server).
        val state = EditorState.create(doc = "x")
        val session = EditorSession(state)
        assertFalse(jumpToDefinition(session))
        assertFalse(jumpToDeclaration(session))
        assertFalse(jumpToTypeDefinition(session))
        assertFalse(jumpToImplementation(session))
    }

    @Test
    fun defaultKeymapBindsF12ToJumpToDefinition() {
        assertEquals(1, jumpToDefinitionKeymap.size)
        val binding = jumpToDefinitionKeymap.single()
        assertEquals("F12", binding.key)
        assertTrue(binding.preventDefault)
    }
}
