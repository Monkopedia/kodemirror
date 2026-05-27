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

/**
 * Internal facet that drives the line-wrapping layout decision.
 *
 * The renderer reads this facet to decide whether long lines soft-wrap
 * (`true`) or extend horizontally with horizontal scrolling (`false`,
 * the default — matching CodeMirror 6, which does not wrap by default).
 *
 * Enabled via the public [lineWrapping] extension. Kept internal so the
 * public surface stays a single opt-in [Extension] (mirroring CM6's
 * `EditorView.lineWrapping`).
 */
internal val lineWrappingFacet: Facet<Boolean, Boolean> = Facet.define(
    combine = { values -> values.any { it } }
)

/**
 * Extension that enables soft line wrapping.
 *
 * By default the editor does NOT wrap long lines (matching CodeMirror 6):
 * lines extend horizontally and the content area scrolls horizontally so the
 * whole line is reachable. Adding this extension makes lines soft-wrap onto
 * multiple visual rows instead, growing the line's height and removing the
 * need for horizontal scrolling.
 */
val lineWrapping: Extension = lineWrappingFacet.of(true)

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

/**
 * Facet for registering an exception handler. When an extension
 * throws an exception, it can be reported through this handler
 * instead of crashing the editor. If no handler is configured,
 * the exception is printed to stderr.
 */
val exceptionSink: Facet<(Throwable) -> Unit, List<(Throwable) -> Unit>> =
    Facet.define()

/**
 * Log an exception that occurred during extension execution.
 * Reports to handlers registered via [exceptionSink], or prints
 * to stderr if none are configured.
 */
fun logException(state: com.monkopedia.kodemirror.state.EditorState, exception: Throwable) {
    val handlers = state.facet(exceptionSink)
    if (handlers.isEmpty()) {
        exception.printStackTrace()
    } else {
        for (handler in handlers) {
            handler(exception)
        }
    }
}
