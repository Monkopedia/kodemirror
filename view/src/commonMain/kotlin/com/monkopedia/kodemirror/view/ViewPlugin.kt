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
import com.monkopedia.kodemirror.state.ExtensionList
import com.monkopedia.kodemirror.state.Facet
import com.monkopedia.kodemirror.state.RangeSet

/**
 * Facet that collects all [ViewPlugin] instances registered in the state's
 * extension configuration.  Each plugin contributes itself via [ViewPlugin.asExtension].
 */
val viewPluginRegistry: Facet<ViewPlugin<*>, List<ViewPlugin<*>>> = Facet.define()

/** A plugin value is the mutable instance managed by a [ViewPlugin]. */
interface PluginValue {
    /** Called whenever the view state changes. */
    fun update(update: ViewUpdate) {}

    /** Called when the plugin is being destroyed. */
    fun destroy() {}
}

/** A plugin value that contributes decorations. */
interface DecorationSource : PluginValue {
    val decorations: DecorationSet
}

/**
 * Specification for a [ViewPlugin].
 *
 * @param create Factory that builds the plugin value for a given view.
 * @param provide Allows the plugin to contribute additional [Extension]s.
 * @param decorations Extract a [DecorationSet] from the plugin value.
 */
data class PluginSpec<V : PluginValue>(
    val create: (EditorView) -> V,
    val provide: ((ViewPlugin<V>) -> Extension)? = null,
    val decorations: ((V) -> DecorationSet)? = null
)

/**
 * A view plugin contributes behaviour and/or decorations to the editor view.
 *
 * To add a plugin to the editor, call [asExtension] and include the result in
 * the state's extension configuration.
 *
 * ```kotlin
 * val myPlugin = ViewPlugin.define(create = { view -> MyPluginValue(view) })
 *
 * EditorState.create(EditorStateConfig(extensions = myPlugin.asExtension()))
 * ```
 */
class ViewPlugin<V : PluginValue>(val spec: PluginSpec<V>) {

    /**
     * Returns an [Extension] that, when included in an [EditorState]'s
     * configuration, registers this plugin with the [EditorView].
     */
    fun asExtension(): Extension {
        val registrationExt: Extension = viewPluginRegistry.of(this)
        val providedExt = spec.provide?.invoke(this)
        return if (providedExt != null) {
            ExtensionList(listOf(registrationExt, providedExt))
        } else {
            registrationExt
        }
    }

    companion object {
        /** Define a plugin from a factory function. */
        fun <V : PluginValue> define(
            create: (EditorView) -> V,
            configure: PluginSpec<V>.() -> PluginSpec<V> = { this }
        ): ViewPlugin<V> = ViewPlugin(PluginSpec(create = create).configure())

        /** Define a plugin from a spec. */
        fun <V : PluginValue> define(spec: PluginSpec<V>): ViewPlugin<V> = ViewPlugin(spec)

        /** Define a plugin whose value also implements [DecorationSource]. */
        @Suppress("UNCHECKED_CAST")
        fun <V> fromClass(
            factory: () -> V
        ): ViewPlugin<PluginValue> where V : PluginValue, V : DecorationSource = ViewPlugin(
            PluginSpec<PluginValue>(
                create = { _ -> factory() as PluginValue },
                decorations = { v ->
                    (v as? DecorationSource)?.decorations ?: RangeSet.empty()
                }
            )
        )
    }
}
