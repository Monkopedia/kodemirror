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

import com.monkopedia.kodemirror.state.EditorState

/**
 * Manages the lifecycle of [ViewPlugin] instances for an [EditorView].
 *
 * Created once per view instance and kept alive across recompositions.
 */
class ViewPluginHost(private val view: EditorView) {
    /**
     * Map from [ViewPlugin] identity to the live plugin value instance.
     */
    private val instances = mutableMapOf<ViewPlugin<*>, PluginValue>()

    /**
     * Sync the set of running plugin instances to match the active extensions
     * in [newState], destroying any that were removed and creating any that
     * were added.
     *
     * Active plugins are discovered via the [viewPluginRegistry] facet.
     */
    fun syncToState(newState: EditorState, oldState: EditorState?) {
        val activePlugins = newState.facet(viewPluginRegistry)

        if (oldState == null || activePlugins != oldState.facet(viewPluginRegistry)) {
            // Reconcile instances
            val keep = mutableSetOf<ViewPlugin<*>>()
            for (plugin in activePlugins) {
                if (!instances.containsKey(plugin)) {
                    @Suppress("UNCHECKED_CAST")
                    val inst = (plugin as ViewPlugin<PluginValue>).spec.create(view)
                    instances[plugin] = inst
                }
                keep.add(plugin)
            }
            // Destroy removed plugins
            val toRemove = instances.keys.filter { it !in keep }
            for (key in toRemove) {
                instances.remove(key)?.destroy()
            }
        }
    }

    /**
     * Propagate a [ViewUpdate] to all active plugin instances.
     */
    fun update(update: ViewUpdate) {
        for (inst in instances.values) {
            inst.update(update)
        }
    }

    /**
     * Collect all active [DecorationSet]s contributed by plugins.
     */
    fun collectDecorations(): List<DecorationSet> {
        val result = mutableListOf<DecorationSet>()
        for ((plugin, inst) in instances) {
            @Suppress("UNCHECKED_CAST")
            val spec = (plugin as ViewPlugin<PluginValue>).spec
            spec.decorations?.invoke(inst)?.let { result.add(it) }
        }
        return result
    }

    /**
     * Return the value of the given plugin, or null if it is not active.
     */
    @Suppress("UNCHECKED_CAST")
    fun <V : PluginValue> plugin(plugin: ViewPlugin<V>): V? = instances[plugin] as? V

    /** Destroy all plugin instances (called when the view is disposed). */
    fun destroy() {
        for (inst in instances.values) inst.destroy()
        instances.clear()
    }
}
