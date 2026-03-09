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

import kotlin.jvm.JvmInline

/**
 * A document position represented as a 0-based character offset.
 *
 * This inline value class provides compile-time type safety for document
 * positions with zero runtime overhead. Use [DocPos] instead of raw [Int]
 * to distinguish document positions from other integer values like line
 * numbers, counts, or indices.
 */
@JvmInline
value class DocPos(val value: Int) : Comparable<DocPos> {
    operator fun plus(offset: Int): DocPos = DocPos(value + offset)
    operator fun minus(offset: Int): DocPos = DocPos(value - offset)
    operator fun minus(other: DocPos): Int = value - other.value
    override fun compareTo(other: DocPos): Int = value.compareTo(other.value)

    override fun toString(): String = "DocPos($value)"

    companion object {
        val ZERO = DocPos(0)
    }
}

/**
 * A 1-based line number in a document.
 *
 * This inline value class provides compile-time type safety for line
 * numbers with zero runtime overhead. Use [LineNumber] instead of raw
 * [Int] to distinguish line numbers from document positions or counts.
 */
@JvmInline
value class LineNumber(val value: Int) : Comparable<LineNumber> {
    operator fun plus(offset: Int): LineNumber = LineNumber(value + offset)
    operator fun minus(offset: Int): LineNumber = LineNumber(value - offset)
    operator fun minus(other: LineNumber): Int = value - other.value
    override fun compareTo(other: LineNumber): Int = value.compareTo(other.value)

    override fun toString(): String = "LineNumber($value)"

    companion object {
        val FIRST = LineNumber(1)
    }
}

/** The position at the end of this document. */
val Text.endPos: DocPos get() = DocPos(length)
