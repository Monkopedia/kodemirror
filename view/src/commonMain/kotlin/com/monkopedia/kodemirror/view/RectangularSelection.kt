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

import com.monkopedia.kodemirror.state.Extension

/**
 * Extension that enables rectangular (column-mode) selection when the user
 * Alt-drags through the editor.
 *
 * Port of `rectangular-selection.ts` from CodeMirror 6.
 *
 * The actual gesture detection is wired up in the composable via
 * [handleRectangularDrag]; this extension serves as the opt-in flag.
 */
val rectangularSelection: Extension = ViewPlugin.define(
    create = { _ -> RectangularSelectionPlugin() }
).asExtension()

internal class RectangularSelectionPlugin : PluginValue {
    /** Whether rectangular selection is currently active (Alt held). */
    var active: Boolean = false
}

/**
 * Extension that changes the cursor to a crosshair when Alt is held,
 * indicating rectangular selection mode is available.
 *
 * In Compose, this would be implemented by detecting the Alt key state and
 * setting a custom cursor (if supported by the platform).
 */
val crosshairCursor: Extension = ViewPlugin.define(
    create = { _ -> CrosshairCursorPlugin() }
).asExtension()

internal class CrosshairCursorPlugin : PluginValue {
    var altPressed: Boolean = false
}
