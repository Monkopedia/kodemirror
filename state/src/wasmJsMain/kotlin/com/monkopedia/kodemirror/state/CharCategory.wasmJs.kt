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

// Kotlin/Wasm's own Regex engine (not the JS one) rejects `\p{Alphabetic}` and looks up astral
// code points by their low 16 bits (U+1F914 would match `\p{L}` as U+F914), so ask the JS
// engine directly, with upstream's exact pattern.
@Suppress("UNUSED_PARAMETER")
internal actual fun hasWordChar(str: String): Boolean =
    js("/[\\p{Alphabetic}\\p{Number}_]/u.test(str)")
