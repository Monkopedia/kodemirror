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
package com.monkopedia.kodemirror.materialtheme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.luminance
import com.monkopedia.kodemirror.state.Extension
import com.monkopedia.kodemirror.view.EditorTheme
import com.monkopedia.kodemirror.view.editorTheme
import com.monkopedia.kodemirror.view.editorThemeFromColors

/**
 * Create an [EditorTheme] that automatically derives its colors from the current
 * [MaterialTheme.colorScheme].
 *
 * The theme updates whenever the Material color scheme changes (e.g., when
 * switching between light and dark mode).
 *
 * ```kotlin
 * MaterialTheme(colorScheme = dynamicColorScheme) {
 *     val theme = rememberMaterialEditorTheme()
 *     val session = rememberEditorSession(
 *         doc = "Hello",
 *         extensions = basicSetup + theme
 *     )
 *     KodeMirror(session)
 * }
 * ```
 */
@Composable
fun rememberMaterialEditorTheme(): Extension {
    val colors = MaterialTheme.colorScheme
    return remember(colors) {
        editorTheme.of(
            editorThemeFromColors(
                background = colors.surface,
                foreground = colors.onSurface,
                primary = colors.primary,
                surface = colors.surfaceVariant,
                outline = colors.outline,
                dark = colors.surface.luminance() < 0.5f
            )
        )
    }
}
