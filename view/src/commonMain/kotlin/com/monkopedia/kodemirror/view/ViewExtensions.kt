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
package com.monkopedia.kodemirror.view

import com.monkopedia.kodemirror.state.Extension
import com.monkopedia.kodemirror.state.Facet
import com.monkopedia.kodemirror.state.RangeSet

/** Whether the editor content is editable. */
val editable: Facet<Boolean, Boolean> = Facet.define(
    combine = { values -> values.lastOrNull() ?: true }
)

/** Extra key/value attributes for the editor content element. */
val contentAttributes: Facet<Map<String, String>, Map<String, String>> = Facet.define(
    combine = { values -> values.fold(emptyMap()) { acc, m -> acc + m } }
)

/** Extra key/value attributes for the outer editor wrapper element. */
val editorAttributes: Facet<Map<String, String>, Map<String, String>> = Facet.define(
    combine = { values -> values.fold(emptyMap()) { acc, m -> acc + m } }
)

/** Extension that enables soft line wrapping. */
val lineWrapping: Extension = contentAttributes.of(mapOf("class" to "cm-lineWrapping"))

/**
 * Facet for contributing decoration sets from extensions and plugins.
 * Multiple sources are collected into a list.
 */
val decorations: Facet<DecorationSet, List<DecorationSet>> = Facet.define()

/**
 * Facet for marking ranges as "atomic" — the cursor won't stop inside them.
 */
@Suppress("UNCHECKED_CAST")
val atomicRanges: Facet<RangeSet<*>, List<RangeSet<*>>> = Facet.define()

/**
 * Scroll margins around the cursor (pixels on each side).
 */
val scrollMargins: Facet<ScrollMarginSpec, ScrollMarginSpec> = Facet.define(
    combine = { values ->
        values.fold(ScrollMarginSpec()) { acc, m ->
            ScrollMarginSpec(
                top = maxOf(acc.top, m.top),
                right = maxOf(acc.right, m.right),
                bottom = maxOf(acc.bottom, m.bottom),
                left = maxOf(acc.left, m.left)
            )
        }
    }
)

/** Margin spec for scroll target offsets. */
data class ScrollMarginSpec(
    val top: Float = 0f,
    val right: Float = 0f,
    val bottom: Float = 0f,
    val left: Float = 0f
)

/** Facet for per-line class names added by extensions. */
val perLineTextDirection: Facet<Boolean, Boolean> = Facet.define(
    combine = { values -> values.any { it } }
)
