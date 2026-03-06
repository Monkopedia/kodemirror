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
package com.monkopedia.kodemirror.lang.javascript

import com.monkopedia.kodemirror.autocomplete.Completion
import com.monkopedia.kodemirror.autocomplete.snippetCompletion

/**
 * A collection of JavaScript-specific
 * [snippet][com.monkopedia.kodemirror.autocomplete.snippet] completions.
 */
val snippets: List<Completion> = listOf(
    snippetCompletion(
        "function \${name}(\${params}) {\n\t\${}\n}",
        Completion(label = "function", detail = "definition", type = "keyword")
    ),
    snippetCompletion(
        "for (let \${index} = 0; \${index} < \${bound}; \${index}++) {\n\t\${}\n}",
        Completion(label = "for", detail = "loop", type = "keyword")
    ),
    snippetCompletion(
        "for (let \${name} of \${collection}) {\n\t\${}\n}",
        Completion(label = "for", detail = "of loop", type = "keyword")
    ),
    snippetCompletion(
        "do {\n\t\${}\n} while (\${})",
        Completion(label = "do", detail = "loop", type = "keyword")
    ),
    snippetCompletion(
        "while (\${}) {\n\t\${}\n}",
        Completion(label = "while", detail = "loop", type = "keyword")
    ),
    snippetCompletion(
        "try {\n\t\${}\n} catch (\${error}) {\n\t\${}\n}",
        Completion(label = "try", detail = "/ catch block", type = "keyword")
    ),
    snippetCompletion(
        "if (\${}) {\n\t\${}\n}",
        Completion(label = "if", detail = "block", type = "keyword")
    ),
    snippetCompletion(
        "if (\${}) {\n\t\${}\n} else {\n\t\${}\n}",
        Completion(label = "if", detail = "/ else block", type = "keyword")
    ),
    snippetCompletion(
        "class \${name} {\n\tconstructor(\${params}) {\n\t\t\${}\n\t}\n}",
        Completion(label = "class", detail = "definition", type = "keyword")
    ),
    snippetCompletion(
        "import {\${names}} from \"\${module}\"\n\${}",
        Completion(label = "import", detail = "named", type = "keyword")
    ),
    snippetCompletion(
        "import \${name} from \"\${module}\"\n\${}",
        Completion(label = "import", detail = "default", type = "keyword")
    )
)

/**
 * A collection of snippet completions for TypeScript. Includes
 * everything from [snippets] plus TypeScript-specific entries.
 */
val typescriptSnippets: List<Completion> = snippets + listOf(
    snippetCompletion(
        "interface \${name} {\n\t\${}\n}",
        Completion(label = "interface", detail = "definition", type = "keyword")
    ),
    snippetCompletion(
        "type \${name} = \${type}",
        Completion(label = "type", detail = "definition", type = "keyword")
    ),
    snippetCompletion(
        "enum \${name} {\n\t\${}\n}",
        Completion(label = "enum", detail = "definition", type = "keyword")
    )
)
