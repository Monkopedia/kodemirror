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

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Tests that constant values in the lezer-lr module match the expected values
 * from the original CodeMirror/Lezer specification.
 */
class ConstantsTest {

    // --- Action constants ---

    @Test
    fun actionReduceFlagIs1Shl16() {
        assertEquals(1 shl 16, Action.REDUCE_FLAG)
        assertEquals(65536, Action.REDUCE_FLAG)
    }

    @Test
    fun actionValueMaskIs65535() {
        assertEquals((1 shl 16) - 1, Action.VALUE_MASK)
        assertEquals(65535, Action.VALUE_MASK)
    }

    @Test
    fun actionReduceDepthShiftIs19() {
        assertEquals(19, Action.REDUCE_DEPTH_SHIFT)
    }

    @Test
    fun actionRepeatFlagIs1Shl17() {
        assertEquals(1 shl 17, Action.REPEAT_FLAG)
        assertEquals(131072, Action.REPEAT_FLAG)
    }

    @Test
    fun actionGotoFlagIs1Shl17() {
        assertEquals(1 shl 17, Action.GOTO_FLAG)
        assertEquals(Action.REPEAT_FLAG, Action.GOTO_FLAG)
    }

    @Test
    fun actionStayFlagIs1Shl18() {
        assertEquals(1 shl 18, Action.STAY_FLAG)
        assertEquals(262144, Action.STAY_FLAG)
    }

    @Test
    fun reduceFlagAndValueMaskAreComplementary() {
        // REDUCE_FLAG and VALUE_MASK should not overlap
        assertEquals(0, Action.REDUCE_FLAG and Action.VALUE_MASK)
        // Together they should cover bits 0-16
        assertEquals(0x1FFFF, Action.REDUCE_FLAG or Action.VALUE_MASK)
    }

    // --- StateFlag constants ---

    @Test
    fun stateFlagSkippedIs1() {
        assertEquals(1, StateFlag.SKIPPED)
    }

    @Test
    fun stateFlagAcceptingIs2() {
        assertEquals(2, StateFlag.ACCEPTING)
    }

    // --- Specialize constants ---

    @Test
    fun specializeSpecializeIs0() {
        assertEquals(0, Specialize.SPECIALIZE)
    }

    @Test
    fun specializeExtendIs1() {
        assertEquals(1, Specialize.EXTEND)
    }

    // --- Term constants ---

    @Test
    fun termErrIs0() {
        assertEquals(0, Term.ERR)
    }

    // --- Seq constants ---

    @Test
    fun seqEndIs0xffff() {
        assertEquals(0xffff, Seq.END)
        assertEquals(65535, Seq.END)
    }

    @Test
    fun seqDoneIs0() {
        assertEquals(0, Seq.DONE)
    }

    @Test
    fun seqNextIs1() {
        assertEquals(1, Seq.NEXT)
    }

    @Test
    fun seqOtherIs2() {
        assertEquals(2, Seq.OTHER)
    }

    // --- ParseState constants ---

    @Test
    fun parseStateSizeIs6() {
        assertEquals(6, ParseState.SIZE)
    }

    @Test
    fun parseStateFlagsIs0() {
        assertEquals(0, ParseState.FLAGS)
    }

    @Test
    fun parseStateActionsIs1() {
        assertEquals(1, ParseState.ACTIONS)
    }

    @Test
    fun parseStateSkipIs2() {
        assertEquals(2, ParseState.SKIP)
    }

    @Test
    fun parseStateTokenizerMaskIs3() {
        assertEquals(3, ParseState.TOKENIZER_MASK)
    }

    @Test
    fun parseStateDefaultReduceIs4() {
        assertEquals(4, ParseState.DEFAULT_REDUCE)
    }

    @Test
    fun parseStateForcedReduceIs5() {
        assertEquals(5, ParseState.FORCED_REDUCE)
    }

    @Test
    fun parseStateFieldsAreSequential() {
        assertEquals(ParseState.FLAGS + 1, ParseState.ACTIONS)
        assertEquals(ParseState.ACTIONS + 1, ParseState.SKIP)
        assertEquals(ParseState.SKIP + 1, ParseState.TOKENIZER_MASK)
        assertEquals(ParseState.TOKENIZER_MASK + 1, ParseState.DEFAULT_REDUCE)
        assertEquals(ParseState.DEFAULT_REDUCE + 1, ParseState.FORCED_REDUCE)
        assertEquals(ParseState.FORCED_REDUCE + 1, ParseState.SIZE)
    }

    // --- Encode constants ---

    @Test
    fun encodeBaseIs46() {
        assertEquals(46, Encode.BASE)
    }

    @Test
    fun encodeStartIs32() {
        assertEquals(32, Encode.START)
    }

    @Test
    fun encodeGap1Is34() {
        assertEquals(34, Encode.GAP1)
    }

    @Test
    fun encodeGap2Is92() {
        assertEquals(92, Encode.GAP2)
    }

    @Test
    fun encodeBigValCodeIs126() {
        assertEquals(126, Encode.BIG_VAL_CODE)
    }

    @Test
    fun encodeBigValIs0xffff() {
        assertEquals(0xffff, Encode.BIG_VAL)
        assertEquals(65535, Encode.BIG_VAL)
    }

    @Test
    fun encodeBaseCalculation() {
        // Base = (BigValCode - Start - 2) / 2, where the 2 accounts for the two gap chars
        assertEquals((Encode.BIG_VAL_CODE - Encode.START - 2) / 2, Encode.BASE)
    }

    // --- File constants ---

    @Test
    fun fileVersionIs14() {
        assertEquals(14, File.VERSION)
    }

    // --- Recover constants ---

    @Test
    fun recoverInsertIs200() {
        assertEquals(200, Recover.INSERT)
    }

    @Test
    fun recoverDeleteIs190() {
        assertEquals(190, Recover.DELETE)
    }

    @Test
    fun recoverReduceIs100() {
        assertEquals(100, Recover.REDUCE)
    }

    @Test
    fun recoverMaxNextIs4() {
        assertEquals(4, Recover.MAX_NEXT)
    }

    @Test
    fun recoverMaxInsertStackDepthIs300() {
        assertEquals(300, Recover.MAX_INSERT_STACK_DEPTH)
    }

    @Test
    fun recoverDampenInsertStackDepthIs120() {
        assertEquals(120, Recover.DAMPEN_INSERT_STACK_DEPTH)
    }

    @Test
    fun recoverMinBigReductionIs2000() {
        assertEquals(2000, Recover.MIN_BIG_REDUCTION)
    }

    // --- Rec constants ---

    @Test
    fun recDistanceIs5() {
        assertEquals(5, Rec.DISTANCE)
    }

    @Test
    fun recMaxRemainingPerStepIs3() {
        assertEquals(3, Rec.MAX_REMAINING_PER_STEP)
    }

    @Test
    fun recMinBufferLengthPruneIs500() {
        assertEquals(500, Rec.MIN_BUFFER_LENGTH_PRUNE)
    }

    @Test
    fun recForceReduceLimitIs10() {
        assertEquals(10, Rec.FORCE_REDUCE_LIMIT)
    }

    @Test
    fun recCutDepthIs8400() {
        assertEquals(2800 * 3, Rec.CUT_DEPTH)
        assertEquals(8400, Rec.CUT_DEPTH)
    }

    @Test
    fun recCutToIs6000() {
        assertEquals(2000 * 3, Rec.CUT_TO)
        assertEquals(6000, Rec.CUT_TO)
    }

    @Test
    fun recMaxLeftAssociativeReductionCountIs300() {
        assertEquals(300, Rec.MAX_LEFT_ASSOCIATIVE_REDUCTION_COUNT)
    }

    @Test
    fun recMaxStackCountIs12() {
        assertEquals(12, Rec.MAX_STACK_COUNT)
    }

    // --- Lookahead constants ---

    @Test
    fun lookaheadMarginIs25() {
        assertEquals(25, Lookahead.MARGIN)
    }

    // --- Cross-constant relationships ---

    @Test
    fun recoverInsertIsGreaterThanDelete() {
        // INSERT cost > DELETE cost > REDUCE cost
        assertTrue(Recover.INSERT > Recover.DELETE)
        assertTrue(Recover.DELETE > Recover.REDUCE)
    }

    @Test
    fun recCutToIsLessThanCutDepth() {
        assertTrue(Rec.CUT_TO < Rec.CUT_DEPTH)
    }

    private fun assertTrue(condition: Boolean) {
        kotlin.test.assertTrue(condition)
    }
}
