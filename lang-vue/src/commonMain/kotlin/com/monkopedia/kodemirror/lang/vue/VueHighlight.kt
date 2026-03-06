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
package com.monkopedia.kodemirror.lang.vue

import com.monkopedia.kodemirror.lezer.highlight.Tags
import com.monkopedia.kodemirror.lezer.highlight.styleTags

/** Highlighting rules for Vue template syntax. */
val vueHighlighting = styleTags(
    mapOf(
        "Text" to Tags.content,
        "Is" to Tags.definitionOperator,
        "AttributeName" to Tags.attributeName,
        "VueAttributeName" to Tags.keyword,
        "Identifier" to Tags.variableName,
        "AttributeValue ScriptAttributeValue" to Tags.attributeValue,
        "Entity" to Tags.character,
        "{{ }}" to Tags.brace,
        "@ :" to Tags.punctuation
    )
)
