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
package com.monkopedia.kodemirror.language

import com.monkopedia.kodemirror.lezer.common.ChangedRange
import com.monkopedia.kodemirror.lezer.common.Input
import com.monkopedia.kodemirror.lezer.common.IterMode
import com.monkopedia.kodemirror.lezer.common.NodeProp
import com.monkopedia.kodemirror.lezer.common.Parser
import com.monkopedia.kodemirror.lezer.common.PartialParse
import com.monkopedia.kodemirror.lezer.common.SyntaxNode
import com.monkopedia.kodemirror.lezer.common.Tree
import com.monkopedia.kodemirror.lezer.common.TreeFragment
import com.monkopedia.kodemirror.lezer.lr.LRParser
import com.monkopedia.kodemirror.lezer.lr.ParserConfig
import com.monkopedia.kodemirror.state.DocPos
import com.monkopedia.kodemirror.state.EditorState
import com.monkopedia.kodemirror.state.Extension
import com.monkopedia.kodemirror.state.Facet
import com.monkopedia.kodemirror.state.FacetEnabler
import com.monkopedia.kodemirror.state.StateEffect
import com.monkopedia.kodemirror.state.StateEffectType
import com.monkopedia.kodemirror.state.StateField
import com.monkopedia.kodemirror.state.StateFieldSpec
import com.monkopedia.kodemirror.state.Text
import com.monkopedia.kodemirror.state.Transaction
import com.monkopedia.kodemirror.state.TransactionSpec
import com.monkopedia.kodemirror.state.extensionListOf
import com.monkopedia.kodemirror.state.languageData
import com.monkopedia.kodemirror.view.EditorSession
import com.monkopedia.kodemirror.view.PluginValue
import com.monkopedia.kodemirror.view.ViewPlugin
import com.monkopedia.kodemirror.view.ViewUpdate
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.TimeSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.yield

/**
 * [StateEffect] used internally to swap in an asynchronously-completed parse tree.
 * The value is the new [Tree] produced by the parse worker.
 */
internal val setTreeEffect: StateEffectType<Tree> = StateEffect.define()

// Internal state field holding the parsed tree.
// Defined first so the `language` facet can reference it in enables.
// The lambdas inside capture `language` lazily (not at field-init time).
private val languageStateField: StateField<LanguageState> = StateField.define(
    StateFieldSpec(
        create = { state -> LanguageState.init(state) },
        update = { value, tr ->
            if (tr.startState.facet(language) != tr.state.facet(language)) {
                LanguageState.init(tr.state)
            } else {
                value.apply(tr)
            }
        }
    )
)

/** ViewPlugin that drives the async parse worker coroutine. */
private val parseWorkerPlugin: ViewPlugin<ParseWorker> = ViewPlugin.define(
    create = { session -> ParseWorker(session) }
)

/**
 * The facet used to associate a language with an editor state.
 *
 * Only one language can be active at a time. Providing multiple language
 * extensions will throw [IllegalStateException].
 */
val language: Facet<Language, Language?> = Facet.define(
    combine = { languages ->
        require(languages.size <= 1) {
            "Multiple language extensions configured: " +
                "${languages.map { it.name }.filter { it.isNotEmpty() }}. " +
                "Only one language can be active at a time."
        }
        languages.firstOrNull()
    },
    enables = FacetEnabler.StaticExtension(
        extensionListOf(languageStateField, parseWorkerPlugin.asExtension())
    )
)

/**
 * The facet type that holds a language's data: maps of named fields (`indentOnInput`,
 * `wordChars`, `closeBrackets`, ...) read through [EditorState.languageDataAt].
 */
typealias LanguageDataFacet = Facet<Map<String, Any?>, List<Map<String, Any?>>>

/**
 * A language object manages parsing and per-language metadata.
 * Parse data is managed as a Lezer tree.
 *
 * [data] is the language data facet for this language. To have it take effect, attach it to
 * the parser's top node type with [languageDataProp]; [LRLanguage.define] does that for you.
 */
open class Language(
    val parser: Parser,
    val name: String = "",
    val data: LanguageDataFacet = defineLanguageFacet()
) {
    /**
     * The extension value to install this as the document language. It also provides the data
     * attached to the syntax tree's top node to [EditorState.languageDataAt].
     */
    val extension: Extension = extensionListOf(
        language.of(this),
        languageData.of { state, pos, side ->
            val top = topNodeAt(state, pos.value, side)
            val data = top.type.prop(languageDataProp) ?: return@of emptyList()
            @Suppress("UNCHECKED_CAST")
            state.facet(data as LanguageDataFacet)
        }
    )

    /** Whether this language allows nesting other languages inside it. */
    open val allowsNesting: Boolean get() = true

    /**
     * Parse the document in [state] and return the resulting syntax tree.
     * Subclasses may override to pass additional context (e.g. tabSize) to
     * the underlying parser.
     */
    open fun getTree(state: EditorState): Tree = parser.parse(DocInput(state.doc))

    /**
     * Start an incremental parse of [doc], reusing tree portions from [fragments].
     * Returns a [PartialParse] whose [PartialParse.advance] can be called repeatedly.
     */
    open fun startParse(doc: Text, fragments: List<TreeFragment> = emptyList()): PartialParse =
        parser.startParse(DocInput(doc), fragments)
}

/**
 * Bundles a [Language] with optional supporting extensions.
 */
class LanguageSupport(val language: Language, val support: Extension? = null) {
    val extension: Extension = if (support != null) {
        extensionListOf(language.extension, support)
    } else {
        language.extension
    }
}

/**
 * A [NodeProp] that can be attached to the top node of a language's
 * syntax tree to associate language metadata with that node type.
 *
 * The value is a [Facet] that can be queried for per-language data.
 */
val languageDataProp: NodeProp<Facet<*, *>> = NodeProp()

/**
 * Create a language data facet. Values provided for it (through `data.of(...)`) come first in
 * [EditorState.languageDataAt] results, followed by [baseData] when given.
 */
fun defineLanguageFacet(baseData: Map<String, Any?>? = null): LanguageDataFacet = Facet.define(
    combine = { values -> if (baseData != null) values + baseData else values }
)

/**
 * The top node of the innermost language at [pos], which carries that language's
 * [languageDataProp]. Nested languages are only entered when the active language allows it.
 */
private fun topNodeAt(state: EditorState, pos: Int, side: Int): SyntaxNode {
    val topLang = state.facet(language)
    var tree = syntaxTree(state).topNode
    if (topLang == null || topLang.allowsNesting) {
        var node: SyntaxNode? = tree
        while (node != null) {
            if (node.type.isTop) tree = node
            node = node.enter(pos, side, IterMode.EXCLUDE_BUFFERS or IterMode.ENTER_BRACKETED)
        }
    }
    return tree
}

/**
 * Query language-specific data at a given position by walking up the syntax
 * tree and looking for nodes with [languageDataProp] attached.
 *
 * Returns the facet value from the first node that has language data, or null.
 */
@Suppress("UNCHECKED_CAST")
fun <T> languageDataAt(state: EditorState, facet: Facet<T, *>, pos: Int): T? {
    val tree = syntaxTree(state)
    var node = tree.resolveInner(pos, 0)
    while (true) {
        val dataFacet = node.type.prop(languageDataProp)
        if (dataFacet != null) {
            return state.facet(dataFacet as Facet<T, T>)
        }
        node = node.parent ?: break
    }
    return null
}

/**
 * Subclass of [Language] for LR-parser-based languages.
 *
 * Provides additional functionality for reconfiguring the parser
 * and integrating with [languageDataProp].
 */
class LRLanguage(
    private val lrParser: LRParser,
    name: String = "",
    data: LanguageDataFacet = defineLanguageFacet()
) : Language(lrParser, name, data) {
    /**
     * Create a new instance of this language with a reconfigured version of its parser and
     * optionally a new name. The new language shares this one's [data].
     */
    fun configure(config: ParserConfig, name: String? = null): LRLanguage =
        LRLanguage(lrParser.configure(config), name ?: this.name, data)

    companion object {
        /**
         * Define a new LR-parser-based language. [languageData] becomes the base value of the
         * language's [data] facet, attached to the parser's top node type.
         */
        fun define(
            parser: LRParser,
            name: String = "",
            languageData: Map<String, Any?>? = null
        ): LRLanguage {
            val data = defineLanguageFacet(languageData)
            val withData = parser.configure(
                ParserConfig(
                    props = listOf(
                        languageDataProp.add { type ->
                            if (type.isTop) data else null
                        }
                    )
                )
            )
            return LRLanguage(withData, name, data)
        }
    }
}

/**
 * An [Input] implementation that reads directly from the [Text] rope,
 * avoiding a full `toString()` copy of the document for parsing.
 */
internal class DocInput(private val doc: Text) : Input {
    override val length: Int get() = doc.length

    override fun chunk(pos: Int): String {
        // Return a chunk starting at pos. Use sliceString to read a
        // reasonably-sized chunk without copying the whole document.
        val chunkSize = minOf(1024, doc.length - pos)
        return doc.sliceString(DocPos(pos), DocPos(pos + chunkSize))
    }

    override fun read(from: Int, to: Int): String = doc.sliceString(DocPos(from), DocPos(to))

    override val lineChunks: Boolean get() = false
}

/**
 * Internal state holding the current parse tree and metadata for
 * incremental async parsing.
 *
 * [tree] is always a valid tree (possibly stale while a parse is in progress).
 * [fragments] holds the reusable tree fragments for the next incremental parse.
 * [parsing] is `true` when a [PartialParse] is in flight.
 */
private class LanguageState(
    val tree: Tree,
    val fragments: List<TreeFragment> = emptyList(),
    val parsing: Boolean = false
) {
    fun apply(tr: Transaction): LanguageState {
        // Handle async tree completion via setTreeEffect
        for (effect in tr.effects) {
            val treeEffect = effect.asType(setTreeEffect)
            if (treeEffect != null) {
                val newTree = treeEffect.value
                return LanguageState(
                    tree = newTree,
                    fragments = TreeFragment.addTree(newTree),
                    parsing = false
                )
            }
        }

        if (!tr.docChanged) return this
        val lang = tr.state.facet(language) ?: return LanguageState(Tree.empty)

        // Build changed ranges from the transaction's ChangeSet
        val changedRanges = mutableListOf<ChangedRange>()
        tr.changes.iterChangedRanges({ fromA, toA, fromB, toB ->
            changedRanges.add(
                ChangedRange(fromA.value, toA.value, fromB.value, toB.value)
            )
        })

        // Get fragments from the current tree, then apply changes
        val oldFragments = TreeFragment.addTree(tree, fragments, parsing)
        val newFragments = TreeFragment.applyChanges(oldFragments, changedRanges)

        // Return with old tree and mark as parsing — the parse worker will
        // pick this up and advance the PartialParse asynchronously.
        return LanguageState(
            tree = tree,
            fragments = newFragments,
            parsing = true
        )
    }

    companion object {
        fun init(state: EditorState): LanguageState {
            val lang = state.facet(language) ?: return LanguageState(Tree.empty)
            // Do a synchronous parse for the initial state so that
            // highlighting is available immediately on first render.
            val tree = lang.getTree(state)
            return LanguageState(
                tree = tree,
                fragments = TreeFragment.addTree(tree),
                parsing = false
            )
        }
    }
}

/**
 * Get the syntax tree for a state, which is the current (possibly stale)
 * parse tree of the active language, or [Tree.empty] if no language is
 * configured. During async parsing the returned tree may not yet reflect
 * the latest document changes — use [syntaxParserRunning] to check.
 */
fun syntaxTree(state: EditorState): Tree =
    state.field(languageStateField, require = false)?.tree ?: Tree.empty

/**
 * Get the syntax tree for the state if it covers at least up to
 * position [upto]. When async parsing is in progress and the current
 * tree does not yet reach [upto], returns `null`.
 *
 * @param upto The minimum document position the tree must cover.
 * @param timeout Ignored in the current implementation. Present
 *   for API compatibility with upstream CodeMirror.
 * @return The syntax tree, or `null` if no language is configured
 *   or the tree does not yet cover [upto].
 */
fun ensureSyntaxTree(
    state: EditorState,
    upto: Int,
    @Suppress("UNUSED_PARAMETER") timeout: Int = 0
): Tree? {
    val tree = syntaxTree(state)
    if (tree.length == 0 && state.facet(language) == null) return null
    return if (tree.length >= upto) tree else null
}

/**
 * Check whether a complete syntax tree is available up to the
 * given position.
 *
 * @param upto The position to check coverage for. Defaults to the
 *   full document length.
 */
fun syntaxTreeAvailable(state: EditorState, upto: Int = state.doc.length): Boolean {
    if (state.facet(language) == null) return false
    val ls = state.field(languageStateField, require = false) ?: return false
    return !ls.parsing && ls.tree.length >= upto
}

/**
 * Check whether the syntax parser is currently running in the
 * background (i.e. an async incremental parse is in progress).
 */
fun syntaxParserRunning(state: EditorState): Boolean =
    state.field(languageStateField, require = false)?.parsing ?: false

/**
 * Force a complete re-parse of the document. If an async parse is
 * in progress, this blocks until parsing is complete and returns
 * the up-to-date tree.
 *
 * This function is provided for API compatibility with upstream
 * CodeMirror.
 */
fun forceParsing(state: EditorState): Tree {
    val ls = state.field(languageStateField, require = false) ?: return Tree.empty
    if (!ls.parsing) return ls.tree
    // Synchronously complete the parse
    val lang = state.facet(language) ?: return ls.tree
    val parse = lang.startParse(state.doc, ls.fragments)
    while (true) {
        val done = parse.advance()
        if (done != null) return done
    }
}

/**
 * ViewPlugin that drives async incremental parsing.
 *
 * When [LanguageState.parsing] becomes `true` (a document change occurred),
 * this plugin launches a coroutine that advances a [PartialParse] in
 * time-limited chunks and, on completion, dispatches a [setTreeEffect]
 * to update the tree in the editor state.
 */
private class ParseWorker(private val session: EditorSession) : PluginValue {
    // Null until the first update() call — deferred so we don't touch
    // session.coroutineScope at construction time. The session's backing
    // coroutine scope is set by KodeMirror.kt after the remember{} block
    // that calls ViewPlugin.create, so it is guaranteed to be available by
    // the time update() is first invoked.
    private var supervisorJob: Job? = null
    private var scope: CoroutineScope? = null

    /** The currently running parse job, if any. */
    private var parseJob: Job? = null

    private fun ensureScope(): CoroutineScope {
        scope?.let { return it }
        val job = SupervisorJob(session.coroutineScope.coroutineContext[Job])
        supervisorJob = job
        return CoroutineScope(session.coroutineScope.coroutineContext + job).also { scope = it }
    }

    override fun update(update: ViewUpdate) {
        val ls = update.state.field(languageStateField, require = false) ?: return
        if (!ls.parsing) return

        // Cancel any in-flight parse — a new document change supersedes it
        parseJob?.cancel()

        val lang = update.state.facet(language) ?: return
        val doc = update.state.doc
        val fragments = ls.fragments

        // Capture the document version so we can discard stale results.
        // If the document changes while we're parsing, update() will cancel
        // this job — but there's a race between advance() completing and
        // the cancellation arriving. Checking the doc identity guards against
        // dispatching a tree built for an outdated document.
        val parsedDoc = doc
        parseJob = ensureScope().launch {
            val parse = lang.startParse(parsedDoc, fragments)
            var mark = TimeSource.Monotonic.markNow()
            while (true) {
                val done = parse.advance()
                if (done != null) {
                    if (session.state.doc === parsedDoc) {
                        // Document unchanged — dispatch the completed tree
                        session.dispatch(
                            TransactionSpec(
                                effects = listOf(setTreeEffect.of(done))
                            )
                        )
                    }
                    // If the doc changed, the tree is stale — discard it.
                    // The cancellation from update() should have caught this,
                    // but in the race window it didn't. The next update() call
                    // (triggered by the document change that invalidated us)
                    // will already have started a fresh parse.
                    break
                }
                if (mark.elapsedNow() >= PARSE_BUDGET) {
                    yield() // Yield to other coroutines / UI
                    mark = TimeSource.Monotonic.markNow()
                }
            }
        }
    }

    override fun destroy() {
        parseJob?.cancel()
        supervisorJob?.cancel()
    }

    companion object {
        /** Maximum time per parse slice before yielding. */
        private val PARSE_BUDGET = 25.milliseconds
    }
}

/**
 * Metadata descriptor for a language. Used for language detection
 * and dynamic language loading.
 *
 * @param name Human-readable name of the language (e.g., "JavaScript").
 * @param alias Alternative names for the language (e.g., "js", "ecmascript").
 * @param extensions File extensions associated with the language
 *   (e.g., `listOf("js", "mjs", "cjs")`).
 * @param filename Regex patterns matching filenames for this language
 *   (e.g., `listOf(Regex("Makefile"))` for Makefiles without extensions).
 * @param load Factory function that creates a [LanguageSupport] instance.
 */
data class LanguageDescription(
    val name: String,
    val alias: List<String> = emptyList(),
    val extensions: List<String> = emptyList(),
    val filename: List<Regex> = emptyList(),
    val load: (() -> LanguageSupport)? = null
) {
    /**
     * Check whether this language matches a given file name.
     */
    fun matchFilename(filename: String): Boolean {
        val ext = filename.substringAfterLast('.', "")
        if (ext.isNotEmpty() && extensions.any { it.equals(ext, ignoreCase = true) }) {
            return true
        }
        return this.filename.any { it.containsMatchIn(filename) }
    }

    /**
     * Check whether this language matches a given name or alias.
     */
    fun matchLanguageName(name: String): Boolean {
        if (this.name.equals(name, ignoreCase = true)) return true
        return alias.any { it.equals(name, ignoreCase = true) }
    }

    companion object {
        /**
         * Find a language description matching a file name from a
         * list of descriptions.
         */
        fun matchFilename(
            descriptions: List<LanguageDescription>,
            filename: String
        ): LanguageDescription? = descriptions.firstOrNull { it.matchFilename(filename) }

        /**
         * Find a language description matching a language name from
         * a list of descriptions.
         */
        fun matchLanguageName(
            descriptions: List<LanguageDescription>,
            name: String
        ): LanguageDescription? = descriptions.firstOrNull { it.matchLanguageName(name) }
    }
}

/**
 * Comment tokens for a language, used by comment toggle commands.
 */
data class CommentTokens(val line: String? = null, val block: BlockComment? = null) {
    data class BlockComment(val open: String, val close: String)
}

/**
 * Facet that provides comment token information for comment commands.
 * Languages should register their comment tokens via this facet.
 */
val commentTokens: Facet<CommentTokens, CommentTokens?> = Facet.define(
    combine = { values -> values.firstOrNull() }
)
