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
package com.monkopedia.kodemirror.lezer.lr

/** Parse action bit layout constants. */
object Action {
    const val ReduceFlag = 1 shl 16
    const val ValueMask = (1 shl 16) - 1
    const val ReduceDepthShift = 19
    const val RepeatFlag = 1 shl 17
    const val GotoFlag = 1 shl 17
    const val StayFlag = 1 shl 18
}

/** Parse state flag constants. */
object StateFlag {
    const val Skipped = 1
    const val Accepting = 2
}

/** Specialization constants. */
object Specialize {
    const val Specialize = 0
    const val Extend = 1
}

/** Term constants. */
object Term {
    const val Err = 0
}

/** Sequence marker constants. */
object Seq {
    const val End = 0xffff
    const val Done = 0
    const val Next = 1
    const val Other = 2
}

/** Memory layout of parse states. */
object ParseState {
    const val Flags = 0
    const val Actions = 1
    const val Skip = 2
    const val TokenizerMask = 3
    const val DefaultReduce = 4
    const val ForcedReduce = 5
    const val Size = 6
}

/** Encoding constants for binary data. */
object Encode {
    const val BigValCode = 126
    const val BigVal = 0xffff
    const val Start = 32
    const val Gap1 = 34 // '"'
    const val Gap2 = 92 // '\\'
    const val Base = 46 // (126 - 32 - 2) / 2
}

/** File format version. */
object File {
    const val Version = 14
}

/** Recovery constants. */
object Recover {
    const val Insert = 200
    const val Delete = 190
    const val Reduce = 100
    const val MaxNext = 4
    const val MaxInsertStackDepth = 300
    const val DampenInsertStackDepth = 120
    const val MinBigReduction = 2000
}

/** Parse loop constants. */
object Rec {
    const val Distance = 5
    const val MaxRemainingPerStep = 3
    const val MinBufferLengthPrune = 500
    const val ForceReduceLimit = 10
    const val CutDepth = 2800 * 3
    const val CutTo = 2000 * 3
    const val MaxLeftAssociativeReductionCount = 300
    const val MaxStackCount = 12
}

/** Lookahead margin. */
object Lookahead {
    const val Margin = 25
}
