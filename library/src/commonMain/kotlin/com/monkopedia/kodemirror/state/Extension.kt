package com.monkopedia.kodemirror.state

//import {EditorState} from "./state"
//import {Transaction, TransactionSpec} from "./transaction"
//import {Facet} from "./facet"

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

/// Subtype of [`Command`](#view.Command) that doesn't require access
/// to the actual editor view. Mostly useful to define commands that
/// can be run and tested outside of a browser environment.
fun interface StateCommand {
    fun target(state: EditorState, dispatch: (Transaction) -> Unit): Boolean
}
