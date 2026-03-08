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
package com.monkopedia.kodemirror.basicsetup

import com.monkopedia.kodemirror.autocomplete.autocompletion
import com.monkopedia.kodemirror.autocomplete.closeBrackets
import com.monkopedia.kodemirror.autocomplete.closeBracketsKeymap
import com.monkopedia.kodemirror.autocomplete.completionKeymap
import com.monkopedia.kodemirror.commands.defaultKeymap
import com.monkopedia.kodemirror.commands.history
import com.monkopedia.kodemirror.language.bracketMatching
import com.monkopedia.kodemirror.language.defaultHighlightStyle
import com.monkopedia.kodemirror.language.foldGutter
import com.monkopedia.kodemirror.language.foldKeymap
import com.monkopedia.kodemirror.language.indentOnInput
import com.monkopedia.kodemirror.language.syntaxHighlighting
import com.monkopedia.kodemirror.lint.lintKeymap
import com.monkopedia.kodemirror.search.highlightSelectionMatches
import com.monkopedia.kodemirror.search.searchKeymap
import com.monkopedia.kodemirror.state.Extension
import com.monkopedia.kodemirror.state.allowMultipleSelections
import com.monkopedia.kodemirror.state.extensionListOf
import com.monkopedia.kodemirror.view.crosshairCursor
import com.monkopedia.kodemirror.view.drawSelection
import com.monkopedia.kodemirror.view.dropCursor
import com.monkopedia.kodemirror.view.highlightActiveLine
import com.monkopedia.kodemirror.view.highlightActiveLineGutter
import com.monkopedia.kodemirror.view.highlightSpecialChars
import com.monkopedia.kodemirror.view.keymapOf
import com.monkopedia.kodemirror.view.lineNumbers
import com.monkopedia.kodemirror.view.rectangularSelection

/**
 * A minimal set of extensions to get a functional editor.
 *
 * Includes special character highlighting, undo/redo history, custom
 * selection drawing, default syntax highlighting, and the default keymap.
 *
 * Use [basicSetup] for a more fully-featured configuration.
 */
val minimalSetup: Extension = extensionListOf(
    highlightSpecialChars,
    history(),
    drawSelection,
    syntaxHighlighting(defaultHighlightStyle),
    keymapOf(defaultKeymap)
)

/**
 * A comprehensive set of extensions for a fully-featured editor.
 *
 * Includes everything from [minimalSetup] plus line numbers, code folding,
 * bracket matching, autocompletion, search, and more. This matches the
 * upstream CodeMirror `basicSetup` bundle.
 *
 * Individual extensions can be overridden by providing your own configuration
 * after `basicSetup` in the extensions list, or you can start from
 * [minimalSetup] and add only what you need.
 */
val basicSetup: Extension = extensionListOf(
    lineNumbers,
    highlightActiveLineGutter,
    highlightSpecialChars,
    history(),
    foldGutter(),
    drawSelection,
    dropCursor,
    allowMultipleSelections.of(true),
    indentOnInput,
    syntaxHighlighting(defaultHighlightStyle),
    bracketMatching(),
    closeBrackets(),
    autocompletion(),
    rectangularSelection,
    crosshairCursor,
    highlightActiveLine,
    highlightSelectionMatches(),
    keymapOf(
        closeBracketsKeymap +
            defaultKeymap +
            searchKeymap +
            foldKeymap +
            completionKeymap +
            lintKeymap
    )
)
