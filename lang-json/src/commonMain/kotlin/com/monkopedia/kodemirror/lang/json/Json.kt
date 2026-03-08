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
package com.monkopedia.kodemirror.lang.json

import com.monkopedia.kodemirror.language.LRLanguage
import com.monkopedia.kodemirror.language.LanguageSupport
import com.monkopedia.kodemirror.language.continuedIndent
import com.monkopedia.kodemirror.language.foldInside
import com.monkopedia.kodemirror.language.foldNodeProp
import com.monkopedia.kodemirror.language.indentNodeProp
import com.monkopedia.kodemirror.lezer.lr.ParserConfig

/**
 * A language provider that provides JSON parsing.
 */
val jsonLanguage: LRLanguage = LRLanguage.define(
    parser = jsonParser.configure(
        ParserConfig(
            props = listOf(
                indentNodeProp.add(
                    mapOf(
                        "Object" to continuedIndent(except = Regex("""^\s*\}""")),
                        "Array" to continuedIndent(except = Regex("""^\s*]"""))
                    )
                ),
                foldNodeProp.add(
                    mapOf(
                        "Object" to { node, _ -> foldInside(node) },
                        "Array" to { node, _ -> foldInside(node) }
                    )
                )
            )
        )
    ),
    name = "json"
)

/**
 * JSON language support.
 */
fun json(): LanguageSupport = LanguageSupport(jsonLanguage)
