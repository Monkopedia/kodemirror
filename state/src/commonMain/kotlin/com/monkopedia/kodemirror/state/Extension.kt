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
package com.monkopedia.kodemirror.state

// import {EditorState} from "./state"
// import {Transaction, TransactionSpec} from "./transaction"
// import {Facet} from "./facet"

enum class Side(val sign: Int) {
    NEG(-1),
    ZERO(0),
    POS(1);

    operator fun minus(other: Side): Side {
        val value = sign - other.sign
        return enumValues<Side>().find { it.sign == value }!!
    }

    companion object {
        val Int.asSide: Side
            get() = when {
                this < 0 -> NEG
                this > 0 -> POS
                else -> ZERO
            }
    }
}
typealias LanguageDataType = (state: EditorState, pos: Int, side: Side) -> List<Map<String, Any>>

// / Subtype of [`Command`](#view.Command) that doesn't require access
// / to the actual editor view. Mostly useful to define commands that
// / can be run and tested outside of a browser environment.
fun interface StateCommand {
    fun target(state: EditorState, dispatch: (Transaction) -> Unit): Boolean
}
