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
package com.monkopedia.kodemirror.lang.python

import com.monkopedia.kodemirror.language.getIndentation
import com.monkopedia.kodemirror.language.indentUnit
import com.monkopedia.kodemirror.state.EditorState
import com.monkopedia.kodemirror.state.EditorStateConfig
import com.monkopedia.kodemirror.state.ExtensionList
import com.monkopedia.kodemirror.state.asDoc
import kotlin.test.Test
import kotlin.test.assertEquals

class PythonIndentTest {

    private fun check(code: String) {
        val stripped = code.replace(Regex("^\\n+"), "")
        val lang = python()
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
            val actual = getIndentation(state, pos)
            assertEquals(
                expectedIndent,
                actual,
                "Line ${i + 1}: \"$line\""
            )
            pos += line.length + 1
        }
    }

    @Test
    fun indentsBodies() = check(
        """
def foo():
  bar
  baz

"""
    )

    @Test
    fun indentsFunctionArgLists() = check(
        """
foo(
  bar,
  baz
)"""
    )

    @Test
    fun indentsNestedBodies() = check(
        """
def foo():
  if True:
    a
  elif False:
    b
  else:
    c
"""
    )

    @Test
    fun dedentsExcept() = check(
        """
try:
  foo()
except e:
  bar()
"""
    )

    @Test
    fun multiLineBlockTryExcept() = check(
        """
try:
  foo()
  fooz()
except e:
  bar()
  barz()
finally:
  baz()
  bazz()
"""
    )

    @Test
    fun multiLineNestedBlockTryExcept() = check(
        """
try:
  foo()
  fooz()
  try:
    inner()
    inner2()
  except e2:
    f3()
    f4()
  else:
    f5()
    f6()
  finally:
    f7()
    f8()
except e:
  bar()
  barz()
finally:
  baz()
  bazz()
"""
    )

    @Test
    fun matchCase() = check(
        """
match x:
  case 1:
    foo()
  case 2:
    bar()
  case _:
    bar()
"""
    )

    @Test
    fun matchCaseMultiLineBlock() = check(
        """
def func():
  match x:
    case 1:
      foo()
      fooz()
    case 2:
      bar()
      bar()
      bar()
      match y:
        case 3:
          bar()
        case 4:
          bar()
    case _:
      bar()
"""
    )

    @Test
    fun classWithDecorators() = check(
        """
@decorator1
@decorator2(
  param1,
  param2
)
class MyClass:
  def method(self):
    pass
"""
    )

    @Test
    fun listComprehension() = check(
        """
result = [
  x * y
  for x in range(10)
  for y in range(5)
  if x > y
]
"""
    )

    @Test
    fun multiLineExpressions() = check(
        """
result = (
  very_long_variable_name +
  another_long_variable *
  some_computation(
    arg1,
    arg2
  )
)
"""
    )

    @Test
    fun asyncFunctionAndWith() = check(
        """
async def process_data():
  async with context() as ctx:
    result = await ctx.fetch(
      url,
      timeout=30
    )
    return result
"""
    )

    @Test
    fun nestedFunctions() = check(
        """
def outer():
  x = 1
  def inner1():
    y = 2
    def inner2():
      z = 3
      return x + y + z
    return inner2()
  return inner1()
"""
    )

    @Test
    fun typeHintsAndAnnotations() = check(
        """
def process_data(
  data: list[str],
  config: dict[str, Any]
) -> tuple[int, str]:
  result: Optional[str] = None
  if data:
    result = data[0]
  return len(data), result
"""
    )

    @Test
    fun multiLineDictComprehension() = check(
        """
config = {
  key: value
  for key, value in items
  if is_valid(
    key,
    value
  )
}
"""
    )

    @Test
    fun multiLineWithComments() = check(
        """
def process(
  x: int,  # The input value
  y: float  # The coefficient
):
  # Compute first step
  result = x * y
  # Apply additional processing
  if result > 0:
    # Positive case
    return result
  else:
    # Negative case
    return -result
"""
    )
}
