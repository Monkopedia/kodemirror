/*
 * Copyright 2025 Jason Monk
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
package com.monkopedia.kodemirror.language

import com.monkopedia.kodemirror.lezer.common.Input
import com.monkopedia.kodemirror.lezer.common.NodeType
import com.monkopedia.kodemirror.lezer.common.NodeTypeSpec
import com.monkopedia.kodemirror.lezer.common.Parser
import com.monkopedia.kodemirror.lezer.common.PartialParse
import com.monkopedia.kodemirror.lezer.common.TextRange
import com.monkopedia.kodemirror.lezer.common.Tree
import com.monkopedia.kodemirror.lezer.common.TreeFragment
import com.monkopedia.kodemirror.state.EditorState
import com.monkopedia.kodemirror.state.EditorStateConfig
import com.monkopedia.kodemirror.state.asDoc
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertSame

/**
 * A simple test parser that produces a single-node tree.
 */
class SimpleTestParser : Parser() {
    private val topType = NodeType.define(
        NodeTypeSpec(name = "TestDoc", id = 0, top = true)
    )

    override fun createParse(
        input: Input,
        fragments: List<TreeFragment>,
        ranges: List<TextRange>
    ): PartialParse {
        val length = input.length
        return object : PartialParse {
            override fun advance(): Tree {
                return Tree(topType, emptyList(), emptyList(), length)
            }

            override val parsedPos: Int get() = length
            override val stoppedAt: Int? get() = null
            override fun stopAt(pos: Int) {}
        }
    }
}

class LanguageTest {

    private val testParser = SimpleTestParser()
    private val testLang = Language(testParser, "test")

    @Test
    fun languageHasExtension() {
        assertNotNull(testLang.extension)
    }

    @Test
    fun syntaxTreeReturnsEmptyWithoutLanguage() {
        val state = EditorState.create(EditorStateConfig(doc = "hello".asDoc()))
        val tree = syntaxTree(state)
        assertSame(Tree.empty, tree)
    }

    @Test
    fun syntaxTreeReturnsTreeWithLanguage() {
        val state = EditorState.create(
            EditorStateConfig(
                doc = "hello world".asDoc(),
                extensions = testLang.extension
            )
        )
        val tree = syntaxTree(state)
        assertNotNull(tree)
        assertEquals(11, tree.length)
        assertEquals("TestDoc", tree.type.name)
    }

    @Test
    fun languageSupportBundlesExtension() {
        val support = LanguageSupport(testLang)
        assertNotNull(support.extension)
    }
}
