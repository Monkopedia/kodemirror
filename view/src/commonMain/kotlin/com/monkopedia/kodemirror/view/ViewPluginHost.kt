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
 * Manages the lifecycle of [ViewPlugin] instances for an [EditorSession].
 *
 * Created once per session instance and kept alive across recompositions.
 */
internal class ViewPluginHost(private val session: EditorSession) {
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
            val created = mutableListOf<PluginValue>()
            for (plugin in activePlugins) {
                if (!instances.containsKey(plugin)) {
                    @Suppress("UNCHECKED_CAST")
                    val inst = (plugin as ViewPlugin<PluginValue>).spec.create(session)
                    instances[plugin] = inst
                    created.add(inst)
                }
                keep.add(plugin)
            }
            // Destroy removed plugins
            val toRemove = instances.keys.filter { it !in keep }
            for (key in toRemove) {
                instances.remove(key)?.destroy()
            }
            // Catch-up update for the instances just created. A plugin whose
            // pending work lives in a state field rather than in the plugin
            // itself has no way to notice that work from its constructor; it
            // only ever acts from update(). The language state field is the
            // case that bites: an edit dispatched while no composable was
            // attached parks it with `parsing = true` and the pre-edit tree,
            // and the replacement ParseWorker built here would otherwise idle
            // forever, leaving the new text under the old highlight positions
            // until some later transaction happened to wake it (#284).
            //
            // Deliberately only the new instances: handing this to every
            // instance would also re-trigger linting, LSP syncing and
            // completion on each reconfigure, which is a wider behaviour
            // change than this fix needs. The update carries no transactions,
            // so `docChanged`/`selectionSet` are false and `startState` is
            // `newState` — plugins that derive purely from a transaction
            // correctly see it as a no-op.
            if (created.isNotEmpty()) {
                // session.state, not newState: a plugin constructed above may
                // itself have dispatched, so the state this method was handed
                // can already be one behind by the time the catch-up is sent.
                val catchUp = ViewUpdate(session, session.state, emptyList())
                for (inst in created) {
                    inst.update(catchUp)
                }
            }
        }
    }

    /**
     * Propagate a [ViewUpdate] to all active plugin instances.
     */
    fun update(update: ViewUpdate) {
        // Iterate a snapshot: bringing a plugin up can dispatch a transaction
        // from its constructor or from its catch-up update, which re-enters
        // here while syncToState is still adding to `instances` (#284).
        for (inst in instances.values.toList()) {
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

    /**
     * Collect all active [HoverTooltipPlugin] instances.
     */
    internal fun collectHoverPlugins(): List<HoverTooltipPlugin> = buildList {
        for (inst in instances.values) {
            if (inst is HoverTooltipPlugin) add(inst)
        }
    }

    /**
     * Collect active hover tooltips from all [HoverTooltipPlugin] instances.
     */
    fun collectHoverTooltips(): List<Tooltip> = buildList {
        for (inst in instances.values) {
            if (inst is HoverTooltipPlugin) {
                inst.currentTooltip?.let { add(it) }
            }
        }
    }

    /** Destroy all plugin instances (called when the view is disposed). */
    fun destroy() {
        for (inst in instances.values) inst.destroy()
        instances.clear()
    }
}
