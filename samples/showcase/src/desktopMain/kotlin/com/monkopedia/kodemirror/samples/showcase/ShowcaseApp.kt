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
 */
package com.monkopedia.kodemirror.samples.showcase

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.monkopedia.kodemirror.samples.showcase.demos.AutocompletionDemo
import com.monkopedia.kodemirror.samples.showcase.demos.BasicDemo
import com.monkopedia.kodemirror.samples.showcase.demos.BidiDemo
import com.monkopedia.kodemirror.samples.showcase.demos.BundleDemo
import com.monkopedia.kodemirror.samples.showcase.demos.ChangeDemo
import com.monkopedia.kodemirror.samples.showcase.demos.CollabDemo
import com.monkopedia.kodemirror.samples.showcase.demos.ConfigDemo
import com.monkopedia.kodemirror.samples.showcase.demos.DecorationDemo
import com.monkopedia.kodemirror.samples.showcase.demos.GutterDemo
import com.monkopedia.kodemirror.samples.showcase.demos.InvertedEffectDemo
import com.monkopedia.kodemirror.samples.showcase.demos.LangPackageDemo
import com.monkopedia.kodemirror.samples.showcase.demos.LanguageGalleryDemo
import com.monkopedia.kodemirror.samples.showcase.demos.LintDemo
import com.monkopedia.kodemirror.samples.showcase.demos.MergeDemo
import com.monkopedia.kodemirror.samples.showcase.demos.MillionDemo
import com.monkopedia.kodemirror.samples.showcase.demos.MixedLanguageDemo
import com.monkopedia.kodemirror.samples.showcase.demos.PanelDemo
import com.monkopedia.kodemirror.samples.showcase.demos.ReadOnlyDemo
import com.monkopedia.kodemirror.samples.showcase.demos.SelectionDemo
import com.monkopedia.kodemirror.samples.showcase.demos.SplitDemo
import com.monkopedia.kodemirror.samples.showcase.demos.StylingDemo
import com.monkopedia.kodemirror.samples.showcase.demos.TabDemo
import com.monkopedia.kodemirror.samples.showcase.demos.TooltipDemo
import com.monkopedia.kodemirror.samples.showcase.demos.TranslateDemo
import com.monkopedia.kodemirror.samples.showcase.demos.ZebraDemo

enum class DemoCategory(val title: String) {
    GETTING_STARTED("Getting Started"),
    CONFIGURATION("Configuration"),
    CONTENT("Working with Content"),
    FEATURES("Features"),
    ADVANCED("Advanced"),
    GALLERY("Language Gallery")
}

data class DemoItem(
    val id: String,
    val title: String,
    val subtitle: String,
    val category: DemoCategory,
    val content: @Composable () -> Unit
)

val allDemos: List<DemoItem> = listOf(
    DemoItem(
        "basic", "Basic Editor", "Minimal setup",
        DemoCategory.GETTING_STARTED
    ) { BasicDemo() },
    DemoItem(
        "bundle", "Bundle Setup", "Gradle configuration",
        DemoCategory.GETTING_STARTED
    ) { BundleDemo() },
    DemoItem(
        "config", "Configuration", "Compartment switching",
        DemoCategory.CONFIGURATION
    ) { ConfigDemo() },
    DemoItem(
        "styling", "Custom Styling", "EditorTheme colors",
        DemoCategory.CONFIGURATION
    ) { StylingDemo() },
    DemoItem(
        "tab", "Tab Handling", "indentWithTab vs insertTab",
        DemoCategory.CONFIGURATION
    ) { TabDemo() },
    DemoItem(
        "readonly", "Read-Only Mode", "Toggle editable",
        DemoCategory.CONFIGURATION
    ) { ReadOnlyDemo() },
    DemoItem(
        "change", "Document Changes", "Insert / replace / delete",
        DemoCategory.CONTENT
    ) { ChangeDemo() },
    DemoItem(
        "selection", "Selections", "Cursor, range, multi-cursor",
        DemoCategory.CONTENT
    ) { SelectionDemo() },
    DemoItem(
        "decoration", "Decorations", "Mark, widget, line",
        DemoCategory.CONTENT
    ) { DecorationDemo() },
    DemoItem(
        "autocompletion", "Autocompletion", "Custom CompletionSource",
        DemoCategory.FEATURES
    ) { AutocompletionDemo() },
    DemoItem(
        "lint", "Linting", "Custom diagnostics",
        DemoCategory.FEATURES
    ) { LintDemo() },
    DemoItem(
        "gutter", "Gutters", "Breakpoint gutter",
        DemoCategory.FEATURES
    ) { GutterDemo() },
    DemoItem(
        "panel", "Panels", "Top / bottom panels",
        DemoCategory.FEATURES
    ) { PanelDemo() },
    DemoItem(
        "tooltip", "Tooltips", "Cursor & hover tooltips",
        DemoCategory.FEATURES
    ) { TooltipDemo() },
    DemoItem(
        "zebra", "Zebra Stripes", "ViewPlugin line decoration",
        DemoCategory.ADVANCED
    ) { ZebraDemo() },
    DemoItem(
        "lang-package", "Custom Language", "StreamParser",
        DemoCategory.ADVANCED
    ) { LangPackageDemo() },
    DemoItem(
        "mixed-language", "Mixed Languages", "HTML + JS + CSS",
        DemoCategory.ADVANCED
    ) { MixedLanguageDemo() },
    DemoItem(
        "collab", "Collaboration", "Two-editor local collab",
        DemoCategory.ADVANCED
    ) { CollabDemo() },
    DemoItem(
        "split", "Split View", "Shared doc, two panes",
        DemoCategory.ADVANCED
    ) { SplitDemo() },
    DemoItem(
        "million", "Large Document", "10k lines performance",
        DemoCategory.ADVANCED
    ) { MillionDemo() },
    DemoItem(
        "bidi", "Bidirectional Text", "RTL + perLineTextDirection",
        DemoCategory.ADVANCED
    ) { BidiDemo() },
    DemoItem(
        "inverted-effect", "Inverted Effects", "Undoable counter",
        DemoCategory.ADVANCED
    ) { InvertedEffectDemo() },
    DemoItem(
        "translate", "Phrase Translation", "UI localization",
        DemoCategory.ADVANCED
    ) { TranslateDemo() },
    DemoItem(
        "merge", "Merge View", "Side-by-side diff",
        DemoCategory.ADVANCED
    ) { MergeDemo() },
    DemoItem(
        "language-gallery", "Language Gallery", "10 languages",
        DemoCategory.GALLERY
    ) { LanguageGalleryDemo() }
)

@Composable
fun ShowcaseApp() {
    var selectedId by remember { mutableStateOf(allDemos.first().id) }
    val selectedDemo = allDemos.first { it.id == selectedId }

    Surface(modifier = Modifier.fillMaxSize()) {
        Row(modifier = Modifier.fillMaxSize()) {
            Sidebar(
                demos = allDemos,
                selectedId = selectedId,
                onSelect = { selectedId = it }
            )
            selectedDemo.content()
        }
    }
}
