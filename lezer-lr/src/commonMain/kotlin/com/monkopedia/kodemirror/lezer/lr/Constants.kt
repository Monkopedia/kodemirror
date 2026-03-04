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
    const val REDUCE_FLAG = 1 shl 16
    const val VALUE_MASK = (1 shl 16) - 1
    const val REDUCE_DEPTH_SHIFT = 19
    const val REPEAT_FLAG = 1 shl 17
    const val GOTO_FLAG = 1 shl 17
    const val STAY_FLAG = 1 shl 18
}

/** Parse state flag constants. */
object StateFlag {
    const val SKIPPED = 1
    const val ACCEPTING = 2
}

/** Specialization constants. */
object Specialize {
    const val SPECIALIZE = 0
    const val EXTEND = 1
}

/** Term constants. */
object Term {
    const val ERR = 0
}

/** Sequence marker constants. */
object Seq {
    const val END = 0xffff
    const val DONE = 0
    const val NEXT = 1
    const val OTHER = 2
}

/** Memory layout of parse states. */
object ParseState {
    const val FLAGS = 0
    const val ACTIONS = 1
    const val SKIP = 2
    const val TOKENIZER_MASK = 3
    const val DEFAULT_REDUCE = 4
    const val FORCED_REDUCE = 5
    const val SIZE = 6
}

/** Encoding constants for binary data. */
object Encode {
    const val BIG_VAL_CODE = 126
    const val BIG_VAL = 0xffff
    const val START = 32
    const val GAP1 = 34 // '"'
    const val GAP2 = 92 // '\\'
    const val BASE = 46 // (126 - 32 - 2) / 2
}

/** File format version. */
object File {
    const val VERSION = 14
}

/** Recovery constants. */
object Recover {
    const val INSERT = 200
    const val DELETE = 190
    const val REDUCE = 100
    const val MAX_NEXT = 4
    const val MAX_INSERT_STACK_DEPTH = 300
    const val DAMPEN_INSERT_STACK_DEPTH = 120
    const val MIN_BIG_REDUCTION = 2000
}

/** Parse loop constants. */
object Rec {
    const val DISTANCE = 5
    const val MAX_REMAINING_PER_STEP = 3
    const val MIN_BUFFER_LENGTH_PRUNE = 500
    const val FORCE_REDUCE_LIMIT = 10
    const val CUT_DEPTH = 2800 * 3
    const val CUT_TO = 2000 * 3
    const val MAX_LEFT_ASSOCIATIVE_REDUCTION_COUNT = 300
    const val MAX_STACK_COUNT = 12
}

/** Lookahead margin. */
object Lookahead {
    const val MARGIN = 25
}
