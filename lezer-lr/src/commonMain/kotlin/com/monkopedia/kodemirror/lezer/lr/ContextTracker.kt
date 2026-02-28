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

import com.monkopedia.kodemirror.lezer.common.Tree

class ContextTracker<T>(
    val start: T,
    val shift: (context: T, term: Int, stack: Stack, input: InputStream) -> T = { c, _, _, _ -> c },
    val reduce: (
        context: T,
        term: Int,
        stack: Stack,
        input: InputStream
    ) -> T = { c, _, _, _ -> c },
    val reuse: (
        context: T,
        node: Tree,
        stack: Stack,
        input: InputStream
    ) -> T = { c, _, _, _ -> c },
    val hash: (context: T) -> Int = { 0 },
    val strict: Boolean = true
)
