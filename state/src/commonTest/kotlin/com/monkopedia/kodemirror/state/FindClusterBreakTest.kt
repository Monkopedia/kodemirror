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

import kotlin.test.Test
import kotlin.test.assertEquals

class FindClusterBreakTest {
    // Expected values from @codemirror/state 6.5.4 (#384): stepping back over a cluster longer
    // than 32 code units lands on the cluster start, not mid-cluster.
    @Test
    fun backwardBreakSkipsWholeLongCluster() {
        val long = "a" + "́".repeat(40) + "b"
        assertEquals(0, findClusterBreak(long, 41, forward = false))
        assertEquals(41, findClusterBreak(long, 42, forward = false))
        val short = "a" + "́".repeat(20) + "b"
        assertEquals(0, findClusterBreak(short, 21, forward = false))
    }
}
