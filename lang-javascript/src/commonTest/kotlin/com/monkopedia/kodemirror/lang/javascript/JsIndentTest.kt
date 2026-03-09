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

import com.monkopedia.kodemirror.language.getIndentation
import com.monkopedia.kodemirror.language.indentUnit
import com.monkopedia.kodemirror.state.DocPos
import com.monkopedia.kodemirror.state.EditorState
import com.monkopedia.kodemirror.state.EditorStateConfig
import com.monkopedia.kodemirror.state.ExtensionList
import com.monkopedia.kodemirror.state.asDoc
import kotlin.test.Test
import kotlin.test.assertEquals

class JsIndentTest {

    private fun check(code: String, jsx: Boolean = false, typescript: Boolean = false) {
        val stripped = code.replace(Regex("^\\n+"), "")
        val lang = javascript(jsx = jsx, typescript = typescript)
        val state = EditorState.create(
            EditorStateConfig(
                doc = stripped.asDoc(),
                extensions = ExtensionList(
                    listOf(lang.language.extension, indentUnit.of(2))
                )
            )
        )
        val lines = stripped.split("\n")
        var pos = 0
        for ((i, line) in lines.withIndex()) {
            val expectedIndent = line.takeWhile { it == ' ' }.length
            val actual = getIndentation(state, DocPos(pos))
            assertEquals(
                expectedIndent,
                actual,
                "Line ${i + 1}: \"$line\""
            )
            pos += line.length + 1
        }
    }

    @Test
    fun indentsArgumentBlocks() = check(
        """
foo({
  bar,
  baz
})
"""
    )

    @Test
    fun indentsFunctionArgs() = check(
        """
foo(
  bar
)"""
    )

    @Test
    fun indentsNestedCalls() = check(
        """
one(
  two(
    three(
      four()
    )
  )
)"""
    )

    @Test
    fun deindentsElse() = check(
        """
if (1)
  a
else
  b
"""
    )

    @Test
    fun handlesHangingBraces() = check(
        """
function foo()
{
  body()
}
"""
    )

    @Test
    fun indentsCaseBodies() = check(
        """
switch (1) {
  case 22:
    console.log(2)
    break
  default:
    return 2
}"""
    )

    @Test
    fun indentsJsonStyle() = check(
        """
let j = {
  foo: [
    {
      1: true,
      2: false
    },
    {},
  ],
  quux: null
}
"""
    )

    @Test
    fun indentsContinuedProperties() = check(
        """
let o = {
  foo: 1 + 3 +
    4,
  bar: 11
}
"""
    )

    @Test
    fun doesntIndentBelowLabels() = check(
        """
abc:
foo()
"""
    )

    @Test
    fun properlyIndentsFunctionExpressionArguments() = check(
        """
foo(100, function() {
  return 2
})
"""
    )

    @Test
    fun indentsArrowFunctions() = check(
        """
let x = a => {
  return 4
}
let y = a =>
  6
"""
    )

    @Test
    fun indentsBracelessStructure() = check(
        """
for (;;)
  if (0)
    if (1)
      foo()
    else
      bar()
  else
    baz()
"""
    )

    @Test
    fun indentsJsxConstructs() = check(
        """
let y = <body>
  <div class="a"
    lang="it">
    What?
  </div>
  <img src={
    foo
  }/>
</body>""",
        jsx = true
    )
}
