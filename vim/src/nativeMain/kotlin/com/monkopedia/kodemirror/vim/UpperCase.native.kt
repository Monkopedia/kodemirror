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
package com.monkopedia.kodemirror.vim

import platform.Foundation.NSCharacterSet

// Foundation's set is Lu plus Lt, and every Lt code point is in the BMP, so for an astral code
// point it is exactly Lu.
internal actual fun isAstralUpperCase(k: String): Boolean {
    val cp = 0x10000 + ((k[0].code - 0xd800) shl 10) + (k[1].code - 0xdc00)
    return NSCharacterSet.uppercaseLetterCharacterSet.longCharacterIsMember(cp.toUInt())
}
