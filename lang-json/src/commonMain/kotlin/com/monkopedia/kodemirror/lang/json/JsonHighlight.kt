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
package com.monkopedia.kodemirror.lang.json

import com.monkopedia.kodemirror.lezer.highlight.styleTags
import com.monkopedia.kodemirror.lezer.highlight.tags as t

val jsonHighlighting = styleTags(
    mapOf(
        "String" to t.string,
        "Number" to t.number,
        "True False" to t.bool,
        "PropertyName" to t.propertyName,
        "Null" to t.`null`,
        ", :" to t.separator,
        "[ ]" to t.squareBracket,
        "{ }" to t.brace
    )
)
