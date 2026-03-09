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
package com.monkopedia.kodemirror.lang.javascript

import com.monkopedia.kodemirror.language.syntaxTree
import com.monkopedia.kodemirror.lezer.common.Tree
import com.monkopedia.kodemirror.state.ChangeSpec
import com.monkopedia.kodemirror.state.DocPos
import com.monkopedia.kodemirror.state.EditorState
import com.monkopedia.kodemirror.state.EditorStateConfig
import com.monkopedia.kodemirror.state.InsertContent
import com.monkopedia.kodemirror.state.TransactionSpec
import com.monkopedia.kodemirror.state.asDoc
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class JsSyntaxTest {

    private fun s(doc: String): EditorState = EditorState.create(
        EditorStateConfig(
            doc = doc.asDoc(),
            extensions = javascriptLanguage.extension
        )
    )

    private fun tr(state: EditorState): Tree = syntaxTree(state)

    @Test
    fun returnsATree() {
        val state = s("let state = s()")
        val tree = tr(state)
        assertIs<Tree>(tree)
        assertEquals("Script", tree.type.name)
        assertEquals(state.doc.length, tree.length)
        val def = tree.resolve(6)
        assertEquals("VariableDefinition", def.name)
        assertEquals(4, def.from)
        assertEquals(9, def.to)
    }

    @Test
    fun keepsTheTreeUpToDateThroughChanges() {
        var state = s("if (2)\n  x")
        assertEquals(
            "IfStatement",
            tr(state).topNode.childAfter(0)!!.name
        )
        state = state.update(
            TransactionSpec(
                changes = ChangeSpec.Single(
                    from = DocPos.ZERO,
                    to = DocPos(3),
                    insert = InsertContent.StringContent("fac")
                )
            )
        ).state
        assertEquals(
            "ExpressionStatement",
            tr(state).topNode.childAfter(0)!!.name
        )
    }
}
