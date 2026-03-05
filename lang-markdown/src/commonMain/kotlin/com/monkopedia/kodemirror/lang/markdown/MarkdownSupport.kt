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
package com.monkopedia.kodemirror.lang.markdown

import com.monkopedia.kodemirror.language.Language
import com.monkopedia.kodemirror.language.LanguageSupport
import com.monkopedia.kodemirror.lezer.markdown.MarkdownExtension
import com.monkopedia.kodemirror.lezer.markdown.MarkdownParser
import com.monkopedia.kodemirror.lezer.markdown.ParseCodeConfig
import com.monkopedia.kodemirror.lezer.markdown.markdownExtensionOf
import com.monkopedia.kodemirror.lezer.markdown.parseCode
import com.monkopedia.kodemirror.state.Extension
import com.monkopedia.kodemirror.state.ExtensionList
import com.monkopedia.kodemirror.state.Prec
import com.monkopedia.kodemirror.view.KeyBinding
import com.monkopedia.kodemirror.view.keymap

val markdownKeymap: List<KeyBinding> = listOf(
    KeyBinding(key = "Enter", run = insertNewlineContinueMarkup),
    KeyBinding(key = "Backspace", run = deleteMarkupBackward)
)

data class MarkdownSupportConfig(
    val defaultCodeLanguage: Language? = null,
    val codeLanguages: ((info: String) -> Language?)? = null,
    val addKeymap: Boolean = true,
    val base: Language = commonmarkLanguage,
    val extensions: MarkdownExtension? = null
)

fun markdown(config: MarkdownSupportConfig = MarkdownSupportConfig()): LanguageSupport {
    val parser = config.base.parser
    require(parser is MarkdownParser) {
        "Base parser provided to `markdown` should be a Markdown parser"
    }

    val extensions = mutableListOf<MarkdownExtension>()
    if (config.extensions != null) extensions.add(config.extensions)

    val support = mutableListOf<Extension>()
    support.add(headerIndent)

    val codeParser: ((String) -> com.monkopedia.kodemirror.lezer.common.Parser?)? =
        if (config.codeLanguages != null || config.defaultCodeLanguage != null) {
            { info: String ->
                if (info.isNotEmpty() && config.codeLanguages != null) {
                    config.codeLanguages.invoke(info)?.parser
                } else {
                    config.defaultCodeLanguage?.parser
                }
            }
        } else {
            null
        }

    extensions.add(parseCode(ParseCodeConfig(codeParser = codeParser)))

    if (config.addKeymap) {
        support.add(Prec.high(keymap.of(markdownKeymap)))
    }

    val allExtensions = if (extensions.size == 1) {
        extensions[0]
    } else {
        markdownExtensionOf(*extensions.toTypedArray())
    }

    val lang = mkLang(parser.configure(allExtensions))
    return LanguageSupport(lang, ExtensionList(support))
}
