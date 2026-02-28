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
        assertEquals(1 shl 16, Action.ReduceFlag)
        assertEquals(65536, Action.ReduceFlag)
    }

    @Test
    fun actionValueMaskIs65535() {
        assertEquals((1 shl 16) - 1, Action.ValueMask)
        assertEquals(65535, Action.ValueMask)
    }

    @Test
    fun actionReduceDepthShiftIs19() {
        assertEquals(19, Action.ReduceDepthShift)
    }

    @Test
    fun actionRepeatFlagIs1Shl17() {
        assertEquals(1 shl 17, Action.RepeatFlag)
        assertEquals(131072, Action.RepeatFlag)
    }

    @Test
    fun actionGotoFlagIs1Shl17() {
        assertEquals(1 shl 17, Action.GotoFlag)
        assertEquals(Action.RepeatFlag, Action.GotoFlag)
    }

    @Test
    fun actionStayFlagIs1Shl18() {
        assertEquals(1 shl 18, Action.StayFlag)
        assertEquals(262144, Action.StayFlag)
    }

    @Test
    fun reduceFlagAndValueMaskAreComplementary() {
        // ReduceFlag and ValueMask should not overlap
        assertEquals(0, Action.ReduceFlag and Action.ValueMask)
        // Together they should cover bits 0-16
        assertEquals(0x1FFFF, Action.ReduceFlag or Action.ValueMask)
    }

    // --- StateFlag constants ---

    @Test
    fun stateFlagSkippedIs1() {
        assertEquals(1, StateFlag.Skipped)
    }

    @Test
    fun stateFlagAcceptingIs2() {
        assertEquals(2, StateFlag.Accepting)
    }

    // --- Specialize constants ---

    @Test
    fun specializeSpecializeIs0() {
        assertEquals(0, Specialize.Specialize)
    }

    @Test
    fun specializeExtendIs1() {
        assertEquals(1, Specialize.Extend)
    }

    // --- Term constants ---

    @Test
    fun termErrIs0() {
        assertEquals(0, Term.Err)
    }

    // --- Seq constants ---

    @Test
    fun seqEndIs0xffff() {
        assertEquals(0xffff, Seq.End)
        assertEquals(65535, Seq.End)
    }

    @Test
    fun seqDoneIs0() {
        assertEquals(0, Seq.Done)
    }

    @Test
    fun seqNextIs1() {
        assertEquals(1, Seq.Next)
    }

    @Test
    fun seqOtherIs2() {
        assertEquals(2, Seq.Other)
    }

    // --- ParseState constants ---

    @Test
    fun parseStateSizeIs6() {
        assertEquals(6, ParseState.Size)
    }

    @Test
    fun parseStateFlagsIs0() {
        assertEquals(0, ParseState.Flags)
    }

    @Test
    fun parseStateActionsIs1() {
        assertEquals(1, ParseState.Actions)
    }

    @Test
    fun parseStateSkipIs2() {
        assertEquals(2, ParseState.Skip)
    }

    @Test
    fun parseStateTokenizerMaskIs3() {
        assertEquals(3, ParseState.TokenizerMask)
    }

    @Test
    fun parseStateDefaultReduceIs4() {
        assertEquals(4, ParseState.DefaultReduce)
    }

    @Test
    fun parseStateForcedReduceIs5() {
        assertEquals(5, ParseState.ForcedReduce)
    }

    @Test
    fun parseStateFieldsAreSequential() {
        assertEquals(ParseState.Flags + 1, ParseState.Actions)
        assertEquals(ParseState.Actions + 1, ParseState.Skip)
        assertEquals(ParseState.Skip + 1, ParseState.TokenizerMask)
        assertEquals(ParseState.TokenizerMask + 1, ParseState.DefaultReduce)
        assertEquals(ParseState.DefaultReduce + 1, ParseState.ForcedReduce)
        assertEquals(ParseState.ForcedReduce + 1, ParseState.Size)
    }

    // --- Encode constants ---

    @Test
    fun encodeBaseIs46() {
        assertEquals(46, Encode.Base)
    }

    @Test
    fun encodeStartIs32() {
        assertEquals(32, Encode.Start)
    }

    @Test
    fun encodeGap1Is34() {
        assertEquals(34, Encode.Gap1)
    }

    @Test
    fun encodeGap2Is92() {
        assertEquals(92, Encode.Gap2)
    }

    @Test
    fun encodeBigValCodeIs126() {
        assertEquals(126, Encode.BigValCode)
    }

    @Test
    fun encodeBigValIs0xffff() {
        assertEquals(0xffff, Encode.BigVal)
        assertEquals(65535, Encode.BigVal)
    }

    @Test
    fun encodeBaseCalculation() {
        // Base = (BigValCode - Start - 2) / 2, where the 2 accounts for the two gap chars
        assertEquals((Encode.BigValCode - Encode.Start - 2) / 2, Encode.Base)
    }

    // --- File constants ---

    @Test
    fun fileVersionIs14() {
        assertEquals(14, File.Version)
    }

    // --- Recover constants ---

    @Test
    fun recoverInsertIs200() {
        assertEquals(200, Recover.Insert)
    }

    @Test
    fun recoverDeleteIs190() {
        assertEquals(190, Recover.Delete)
    }

    @Test
    fun recoverReduceIs100() {
        assertEquals(100, Recover.Reduce)
    }

    @Test
    fun recoverMaxNextIs4() {
        assertEquals(4, Recover.MaxNext)
    }

    @Test
    fun recoverMaxInsertStackDepthIs300() {
        assertEquals(300, Recover.MaxInsertStackDepth)
    }

    @Test
    fun recoverDampenInsertStackDepthIs120() {
        assertEquals(120, Recover.DampenInsertStackDepth)
    }

    @Test
    fun recoverMinBigReductionIs2000() {
        assertEquals(2000, Recover.MinBigReduction)
    }

    // --- Rec constants ---

    @Test
    fun recDistanceIs5() {
        assertEquals(5, Rec.Distance)
    }

    @Test
    fun recMaxRemainingPerStepIs3() {
        assertEquals(3, Rec.MaxRemainingPerStep)
    }

    @Test
    fun recMinBufferLengthPruneIs500() {
        assertEquals(500, Rec.MinBufferLengthPrune)
    }

    @Test
    fun recForceReduceLimitIs10() {
        assertEquals(10, Rec.ForceReduceLimit)
    }

    @Test
    fun recCutDepthIs8400() {
        assertEquals(2800 * 3, Rec.CutDepth)
        assertEquals(8400, Rec.CutDepth)
    }

    @Test
    fun recCutToIs6000() {
        assertEquals(2000 * 3, Rec.CutTo)
        assertEquals(6000, Rec.CutTo)
    }

    @Test
    fun recMaxLeftAssociativeReductionCountIs300() {
        assertEquals(300, Rec.MaxLeftAssociativeReductionCount)
    }

    @Test
    fun recMaxStackCountIs12() {
        assertEquals(12, Rec.MaxStackCount)
    }

    // --- Lookahead constants ---

    @Test
    fun lookaheadMarginIs25() {
        assertEquals(25, Lookahead.Margin)
    }

    // --- Cross-constant relationships ---

    @Test
    fun recoverInsertIsGreaterThanDelete() {
        // Insert cost > Delete cost > Reduce cost
        assertTrue(Recover.Insert > Recover.Delete)
        assertTrue(Recover.Delete > Recover.Reduce)
    }

    @Test
    fun recCutToIsLessThanCutDepth() {
        assertTrue(Rec.CutTo < Rec.CutDepth)
    }

    private fun assertTrue(condition: Boolean) {
        kotlin.test.assertTrue(condition)
    }
}
