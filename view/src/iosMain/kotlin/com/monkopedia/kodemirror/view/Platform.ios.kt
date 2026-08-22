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
package com.monkopedia.kodemirror.view

/**
 * iOS reports itself as `"iOS"`. It is not macOS, and it no longer claims to
 * be (#217).
 *
 * This does **not** change which modifiers iOS binds. [isMacFamilyOs] accepts
 * the iOS family precisely so that an honest name here resolves the same `mac`
 * overrides iOS has always resolved; the two changes only make sense together.
 */
internal actual fun platformOsName(): String = "iOS"
