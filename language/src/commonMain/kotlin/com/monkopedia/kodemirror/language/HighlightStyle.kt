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

import androidx.compose.ui.text.SpanStyle
import com.monkopedia.kodemirror.lezer.common.NodeType
import com.monkopedia.kodemirror.lezer.highlight.Highlighter
import com.monkopedia.kodemirror.lezer.highlight.Tag
import com.monkopedia.kodemirror.lezer.highlight.TagStyleRule
import com.monkopedia.kodemirror.lezer.highlight.tagHighlighter

/**
 * Specification for a tag-to-style mapping in [HighlightStyle.define].
 */
data class TagStyleSpec(
    // tag: Tag or List<Tag>
    val tag: Any,
    val style: SpanStyle
)

/**
 * A highlight style maps [Tag]s to [SpanStyle]s.
 *
 * Implements [Highlighter] by delegating tag resolution to a [tagHighlighter]
 * and maintaining a separate tag→SpanStyle map for Compose rendering.
 */
class HighlightStyle private constructor(
    val specs: List<TagStyleSpec>,
    private val delegate: Highlighter,
    private val styleMap: Map<String, SpanStyle>
) : Highlighter {

    override fun style(tags: List<Tag>): String? = delegate.style(tags)

    override fun scope(node: NodeType): Boolean = delegate.scope(node)

    /**
     * Resolve the [SpanStyle] for a given style class key returned by [style].
     */
    fun spanStyleFor(cls: String): SpanStyle? = styleMap[cls]

    companion object {
        /**
         * Create a [HighlightStyle] from a list of tag→style specs.
         */
        fun define(
            specs: List<TagStyleSpec>,
            scope: ((NodeType) -> Boolean)? = null
        ): HighlightStyle {
            val styleMap = mutableMapOf<String, SpanStyle>()
            val tagRules = specs.mapIndexed { index, spec ->
                val cls = "hl-$index"
                styleMap[cls] = spec.style
                TagStyleRule(tag = spec.tag, `class` = cls)
            }
            val delegate = tagHighlighter(tagRules, scope = scope)
            return HighlightStyle(specs, delegate, styleMap)
        }

        /**
         * Define a [HighlightStyle] using a DSL builder.
         *
         * ```kotlin
         * val myStyle = HighlightStyle.define {
         *     Tags.keyword styles SpanStyle(color = Color.Blue)
         *     Tags.string styles SpanStyle(color = Color.Green)
         *     listOf(Tags.number, Tags.bool) styles SpanStyle(color = Color.Cyan)
         * }
         * ```
         */
        inline fun define(
            noinline scope: ((NodeType) -> Boolean)? = null,
            block: HighlightStyleBuilder.() -> Unit
        ): HighlightStyle = define(
            HighlightStyleBuilder().apply(block).specs,
            scope
        )
    }
}

/**
 * Builder scope for defining a [HighlightStyle] via DSL.
 */
@HighlightStyleDsl
class HighlightStyleBuilder @PublishedApi internal constructor() {
    @PublishedApi
    internal val specs = mutableListOf<TagStyleSpec>()

    /** Map a single [Tag] to a [SpanStyle]. */
    infix fun Tag.styles(style: SpanStyle) {
        specs.add(TagStyleSpec(tag = this, style = style))
    }

    /** Map multiple [Tag]s to the same [SpanStyle]. */
    infix fun List<Tag>.styles(style: SpanStyle) {
        specs.add(TagStyleSpec(tag = this, style = style))
    }
}

/** Marks DSL scope for [HighlightStyleBuilder]. */
@DslMarker
annotation class HighlightStyleDsl
