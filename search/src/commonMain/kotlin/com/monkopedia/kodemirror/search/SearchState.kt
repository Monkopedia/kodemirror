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
package com.monkopedia.kodemirror.search

import com.monkopedia.kodemirror.state.EditorState
import com.monkopedia.kodemirror.state.StateEffect
import com.monkopedia.kodemirror.state.StateEffectType
import com.monkopedia.kodemirror.state.StateField
import com.monkopedia.kodemirror.state.StateFieldSpec

/** Effect to set the current search query. */
val setSearchQuery: StateEffectType<SearchQuery> = StateEffect.define()

/** Effect to toggle the search panel open/closed. */
val toggleSearchPanel: StateEffectType<Boolean> = StateEffect.define()

/** State field tracking the current search query. */
val searchQueryField: StateField<SearchQuery> = StateField.define(
    StateFieldSpec(
        create = { SearchQuery() },
        update = { value, tr ->
            var result = value
            for (effect in tr.effects) {
                val queryEffect = effect.asType(setSearchQuery)
                if (queryEffect != null) {
                    result = queryEffect.value
                }
            }
            result
        }
    )
)

/** State field tracking whether the search panel is open. */
val searchPanelOpenField: StateField<Boolean> = StateField.define(
    StateFieldSpec(
        create = { false },
        update = { value, tr ->
            var result = value
            for (effect in tr.effects) {
                val panelEffect = effect.asType(toggleSearchPanel)
                if (panelEffect != null) {
                    result = panelEffect.value
                }
            }
            result
        }
    )
)

/** Get the current search query from an editor state. */
fun getSearchQuery(state: EditorState): SearchQuery =
    state.field(searchQueryField, require = false) ?: SearchQuery()

/** Whether the search panel is currently open. */
fun searchPanelOpen(state: EditorState): Boolean =
    state.field(searchPanelOpenField, require = false) ?: false
