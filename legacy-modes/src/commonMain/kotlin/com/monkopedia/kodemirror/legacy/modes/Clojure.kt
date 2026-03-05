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
package com.monkopedia.kodemirror.legacy.modes

import com.monkopedia.kodemirror.language.IndentContext
import com.monkopedia.kodemirror.language.StreamParser
import com.monkopedia.kodemirror.language.StringStream

data class ClojureCtx(
    val prev: ClojureCtx?,
    val start: Int,
    var indentTo: Any // Int or "next"
)

data class ClojureState(
    var ctx: ClojureCtx = ClojureCtx(null, 0, 0),
    var lastToken: String? = null,
    var tokenize: (StringStream, ClojureState) -> Pair<String?, String?> =
        ::clojureBase
)

private val clojureAtoms = setOf("false", "nil", "true")
private val clojureSpecialForms = setOf(
    ".", "catch", "def", "do", "if", "monitor-enter",
    "monitor-exit", "new", "quote", "recur", "set!", "throw", "try", "var"
)

@Suppress("ktlint:standard:max-line-length")
private val clojureCoreSymbols = setOf(
    "*", "*'", "*1", "*2", "*3", "*agent*", "*allow-unresolved-vars*",
    "*assert*", "*clojure-version*", "*command-line-args*",
    "*compile-files*", "*compile-path*", "*compiler-options*",
    "*data-readers*", "*default-data-reader-fn*", "*e", "*err*", "*file*",
    "*flush-on-newline*", "*fn-loader*", "*in*", "*math-context*", "*ns*",
    "*out*", "*print-dup*", "*print-length*", "*print-level*",
    "*print-meta*", "*print-namespace-maps*", "*print-readably*",
    "*read-eval*", "*reader-resolver*", "*source-path*",
    "*suppress-read*", "*unchecked-math*", "*use-context-classloader*",
    "*verbose-defrecords*", "*warn-on-reflection*", "+", "+'", "-", "-'",
    "->", "->>", "->ArrayChunk", "->Eduction", "->Vec", "->VecNode",
    "->VecSeq", "-cache-protocol-fn", "-reset-methods", "..", "/", "<",
    "<=", "=", "==", ">", ">=", "EMPTY-NODE", "Inst",
    "StackTraceElement->vec", "Throwable->map", "accessor", "aclone",
    "add-classpath", "add-watch", "agent", "agent-error", "agent-errors",
    "aget", "alength", "alias", "all-ns", "alter", "alter-meta!",
    "alter-var-root", "amap", "ancestors", "and", "any?", "apply",
    "areduce", "array-map", "as->", "aset", "aset-boolean", "aset-byte",
    "aset-char", "aset-double", "aset-float", "aset-int", "aset-long",
    "aset-short", "assert", "assoc", "assoc!", "assoc-in", "associative?",
    "atom", "await", "await-for", "await1", "bases", "bean", "bigdec",
    "bigint", "biginteger", "binding", "bit-and", "bit-and-not",
    "bit-clear", "bit-flip", "bit-not", "bit-or", "bit-set",
    "bit-shift-left", "bit-shift-right", "bit-test", "bit-xor", "boolean",
    "boolean-array", "boolean?", "booleans", "bound-fn", "bound-fn*",
    "bound?", "bounded-count", "butlast", "byte", "byte-array", "bytes",
    "bytes?", "case", "cast", "cat", "char", "char-array",
    "char-escape-string", "char-name-string", "char?", "chars", "chunk",
    "chunk-append", "chunk-buffer", "chunk-cons", "chunk-first",
    "chunk-next", "chunk-rest", "chunked-seq?", "class", "class?",
    "clear-agent-errors", "clojure-version", "coll?", "comment", "commute",
    "comp", "comparator", "compare", "compare-and-set!", "compile",
    "complement", "completing", "concat", "cond", "cond->", "cond->>",
    "condp", "conj", "conj!", "cons", "constantly", "construct-proxy",
    "contains?", "count", "counted?", "create-ns", "create-struct", "cycle",
    "dec", "dec'", "decimal?", "declare", "dedupe",
    "default-data-readers", "definline", "definterface", "defmacro",
    "defmethod", "defmulti", "defn", "defn-", "defonce", "defprotocol",
    "defrecord", "defstruct", "deftype", "delay", "delay?", "deliver",
    "denominator", "deref", "derive", "descendants", "destructure", "disj",
    "disj!", "dissoc", "dissoc!", "distinct", "distinct?", "doall", "dorun",
    "doseq", "dosync", "dotimes", "doto", "double", "double-array",
    "double?", "doubles", "drop", "drop-last", "drop-while", "eduction",
    "empty", "empty?", "ensure", "ensure-reduced", "enumeration-seq",
    "error-handler", "error-mode", "eval", "even?", "every-pred", "every?",
    "ex-data", "ex-info", "extend", "extend-protocol", "extend-type",
    "extenders", "extends?", "false?", "ffirst", "file-seq", "filter",
    "filterv", "find", "find-keyword", "find-ns", "find-protocol-impl",
    "find-protocol-method", "find-var", "first", "flatten", "float",
    "float-array", "float?", "floats", "flush", "fn", "fn?", "fnext",
    "fnil", "for", "force", "format", "frequencies", "future",
    "future-call", "future-cancel", "future-cancelled?", "future-done?",
    "future?", "gen-class", "gen-interface", "gensym", "get", "get-in",
    "get-method", "get-proxy-class", "get-thread-bindings", "get-validator",
    "group-by", "halt-when", "hash", "hash-combine", "hash-map",
    "hash-ordered-coll", "hash-set", "hash-unordered-coll", "ident?",
    "identical?", "identity", "if-let", "if-not", "if-some", "ifn?",
    "import", "in-ns", "inc", "inc'", "indexed?", "init-proxy", "inst-ms",
    "inst-ms*", "inst?", "instance?", "int", "int-array", "int?",
    "integer?", "interleave", "intern", "interpose", "into", "into-array",
    "ints", "io!", "isa?", "iterate", "iterator-seq", "juxt", "keep",
    "keep-indexed", "key", "keys", "keyword", "keyword?", "last",
    "lazy-cat", "lazy-seq", "let", "letfn", "line-seq", "list", "list*",
    "list?", "load", "load-file", "load-reader", "load-string",
    "loaded-libs", "locking", "long", "long-array", "longs", "loop",
    "macroexpand", "macroexpand-1", "make-array", "make-hierarchy", "map",
    "map-entry?", "map-indexed", "map?", "mapcat", "mapv", "max",
    "max-key", "memfn", "memoize", "merge", "merge-with", "meta",
    "method-sig", "methods", "min", "min-key", "mix-collection-hash",
    "mod", "munge", "name", "namespace", "namespace-munge", "nat-int?",
    "neg-int?", "neg?", "newline", "next", "nfirst", "nil?", "nnext",
    "not", "not-any?", "not-empty", "not-every?", "not=", "ns",
    "ns-aliases", "ns-imports", "ns-interns", "ns-map", "ns-name",
    "ns-publics", "ns-refers", "ns-resolve", "ns-unalias", "ns-unmap",
    "nth", "nthnext", "nthrest", "num", "number?", "numerator",
    "object-array", "odd?", "or", "parents", "partial", "partition",
    "partition-all", "partition-by", "pcalls", "peek", "persistent!",
    "pmap", "pop", "pop!", "pop-thread-bindings", "pos-int?", "pos?", "pr",
    "pr-str", "prefer-method", "prefers", "primitives-classnames", "print",
    "print-ctor", "print-dup", "print-method", "print-simple", "print-str",
    "printf", "println", "println-str", "prn", "prn-str", "promise",
    "proxy", "proxy-call-with-super", "proxy-mappings", "proxy-name",
    "proxy-super", "push-thread-bindings", "pvalues", "qualified-ident?",
    "qualified-keyword?", "qualified-symbol?", "quot", "rand", "rand-int",
    "rand-nth", "random-sample", "range", "ratio?", "rational?",
    "rationalize", "re-find", "re-groups", "re-matcher", "re-matches",
    "re-pattern", "re-seq", "read", "read-line", "read-string",
    "reader-conditional", "reader-conditional?", "realized?", "record?",
    "reduce", "reduce-kv", "reduced", "reduced?", "reductions", "ref",
    "ref-history-count", "ref-max-history", "ref-min-history", "ref-set",
    "refer", "refer-clojure", "reify", "release-pending-sends", "rem",
    "remove", "remove-all-methods", "remove-method", "remove-ns",
    "remove-watch", "repeat", "repeatedly", "replace", "replicate",
    "require", "reset!", "reset-meta!", "reset-vals!", "resolve", "rest",
    "restart-agent", "resultset-seq", "reverse", "reversible?", "rseq",
    "rsubseq", "run!", "satisfies?", "second", "select-keys", "send",
    "send-off", "send-via", "seq", "seq?", "seqable?", "seque", "sequence",
    "sequential?", "set", "set-agent-send-executor!",
    "set-agent-send-off-executor!", "set-error-handler!", "set-error-mode!",
    "set-validator!", "set?", "short", "short-array", "shorts", "shuffle",
    "shutdown-agents", "simple-ident?", "simple-keyword?", "simple-symbol?",
    "slurp", "some", "some->", "some->>", "some-fn", "some?", "sort",
    "sort-by", "sorted-map", "sorted-map-by", "sorted-set", "sorted-set-by",
    "sorted?", "special-symbol?", "spit", "split-at", "split-with", "str",
    "string?", "struct", "struct-map", "subs", "subseq", "subvec", "supers",
    "swap!", "swap-vals!", "symbol", "symbol?", "sync", "tagged-literal",
    "tagged-literal?", "take", "take-last", "take-nth", "take-while",
    "test", "the-ns", "thread-bound?", "time", "to-array", "to-array-2d",
    "trampoline", "transduce", "transient", "tree-seq", "true?", "type",
    "unchecked-add", "unchecked-add-int", "unchecked-byte",
    "unchecked-char", "unchecked-dec", "unchecked-dec-int",
    "unchecked-divide-int", "unchecked-double", "unchecked-float",
    "unchecked-inc", "unchecked-inc-int", "unchecked-int", "unchecked-long",
    "unchecked-multiply", "unchecked-multiply-int", "unchecked-negate",
    "unchecked-negate-int", "unchecked-remainder-int", "unchecked-short",
    "unchecked-subtract", "unchecked-subtract-int", "underive", "unquote",
    "unquote-splicing", "unreduced", "unsigned-bit-shift-right", "update",
    "update-in", "update-proxy", "uri?", "use", "uuid?", "val", "vals",
    "var-get", "var-set", "var?", "vary-meta", "vec", "vector", "vector-of",
    "vector?", "volatile!", "volatile?", "vreset!", "vswap!", "when",
    "when-first", "when-let", "when-not", "when-some", "while",
    "with-bindings", "with-bindings*", "with-in-str", "with-loading-context",
    "with-local-vars", "with-meta", "with-open", "with-out-str",
    "with-precision", "with-redefs", "with-redefs-fn", "xml-seq", "zero?",
    "zipmap"
)
private val clojureHasBodyParameter = setOf(
    "->", "->>", "as->", "binding", "bound-fn", "case", "catch", "comment",
    "cond", "cond->", "cond->>", "condp", "def", "definterface",
    "defmethod", "defn", "defmacro", "defprotocol", "defrecord",
    "defstruct", "deftype", "do", "doseq", "dotimes", "doto", "extend",
    "extend-protocol", "extend-type", "fn", "for", "future", "if", "if-let",
    "if-not", "if-some", "let", "letfn", "locking", "loop", "ns", "proxy",
    "reify", "struct-map", "some->", "some->>", "try", "when", "when-first",
    "when-let", "when-not", "when-some", "while", "with-bindings",
    "with-bindings*", "with-in-str", "with-loading-context",
    "with-local-vars", "with-meta", "with-open", "with-out-str",
    "with-precision", "with-redefs", "with-redefs-fn"
)

private val clojureDelimiter = Regex("^(?:[\\\\\\[\\]\\s\"(),;@^`{}~]|$)")

@Suppress("ktlint:standard:max-line-length")
private val clojureNumberLiteral = Regex(
    "^(?:[+\\-]?\\d+(?:(?:N|(?:[eE][+\\-]?\\d+))|(?:\\.?\\d*(?:M|(?:[eE][+\\-]?\\d+))?)|/\\d+|[xX][0-9a-fA-F]+|r[0-9a-zA-Z]+)?(?=[\\\\\\[\\]\\s\"#'(),;@^`{}~]|$))"
)

@Suppress("ktlint:standard:max-line-length")
private val clojureCharacterLiteral = Regex(
    "^(?:\\\\(?:backspace|formfeed|newline|return|space|tab|o[0-7]{3}|u[0-9A-Fa-f]{4}|x[0-9A-Fa-f]{4}|.)?(?=[\\\\\\[\\]\\s\"(),;@^`{}~]|$))"
)

@Suppress("ktlint:standard:max-line-length")
private val clojureQualifiedSymbol = Regex(
    "^(?:(?:[^\\\\/" +
        "\\[\\]\\d\\s\"#'(),;@^`{}~.][^\\\\\\[\\]\\s\"(),;@^`{}~./" +
        "]*(?:\\.[^\\\\/" +
        "\\[\\]\\d\\s\"#'(),;@^`{}~.][^\\\\\\[\\]\\s\"(),;@^`{}~./" +
        "]*)*/)?" +
        "(?:/|[^\\\\/\\[\\]\\d\\s\"#'(),;@^`{}~][^\\\\\\[\\]\\s\"(),;@^`{}~]*)" +
        "*(?=[\\\\\\[\\]\\s\"(),;@^`{}~]|$))"
)

private fun clojureBase(stream: StringStream, state: ClojureState): Pair<String?, String?> {
    if (stream.eatSpace() || stream.eat(",") != null) return "space" to null
    if (stream.match(clojureNumberLiteral) != null) return null to "number"
    if (stream.match(clojureCharacterLiteral) != null) {
        return null to "string.special"
    }
    if (stream.eat(Regex("^\"")) != null) {
        state.tokenize = ::clojureInString
        return state.tokenize(stream, state)
    }
    if (stream.eat(Regex("^[\\(\\[\\{]")) != null) return "open" to "bracket"
    if (stream.eat(Regex("^[\\)\\]\\}]")) != null) return "close" to "bracket"
    if (stream.eat(Regex("^;")) != null) {
        stream.skipToEnd()
        return "space" to "comment"
    }
    if (stream.eat(Regex("^[#'@^`~]")) != null) return null to "meta"

    val matches = stream.match(clojureQualifiedSymbol)
    val symbol = matches?.value

    if (symbol == null) {
        stream.next()
        stream.eatWhile { c -> !clojureDelimiter.containsMatchIn(c) }
        return null to "error"
    }

    if (symbol == "comment" && state.lastToken == "(") {
        state.tokenize = ::clojureInComment
        return state.tokenize(stream, state)
    }
    if (symbol in clojureAtoms || symbol[0] == ':') return "symbol" to "atom"
    if (symbol in clojureSpecialForms || symbol in clojureCoreSymbols) {
        return "symbol" to "keyword"
    }
    if (state.lastToken == "(") return "symbol" to "builtin"

    return "symbol" to "variable"
}

private fun clojureInString(stream: StringStream, state: ClojureState): Pair<String?, String?> {
    var escaped = false
    while (true) {
        val next = stream.next() ?: break
        if (next == "\"" && !escaped) {
            state.tokenize = ::clojureBase
            break
        }
        escaped = !escaped && next == "\\"
    }
    return null to "string"
}

private fun clojureInComment(stream: StringStream, state: ClojureState): Pair<String?, String?> {
    var parenthesisCount = 1
    while (true) {
        val next = stream.next() ?: break
        if (next == ")") parenthesisCount--
        if (next == "(") parenthesisCount++
        if (parenthesisCount == 0) {
            stream.backUp(1)
            state.tokenize = ::clojureBase
            break
        }
    }
    return "space" to "comment"
}

val clojure: StreamParser<ClojureState> = object : StreamParser<ClojureState> {
    override val name: String get() = "clojure"
    override fun startState(indentUnit: Int) = ClojureState()

    override fun copyState(state: ClojureState): ClojureState {
        fun copyCtx(ctx: ClojureCtx): ClojureCtx = ClojureCtx(
            prev = ctx.prev?.let { copyCtx(it) },
            start = ctx.start,
            indentTo = ctx.indentTo
        )
        return ClojureState(
            ctx = copyCtx(state.ctx),
            lastToken = state.lastToken,
            tokenize = state.tokenize
        )
    }

    override fun token(stream: StringStream, state: ClojureState): String? {
        if (stream.sol() && state.ctx.indentTo !is Int) {
            state.ctx.indentTo = state.ctx.start + 1
        }

        val typeStylePair = state.tokenize(stream, state)
        val type = typeStylePair.first
        val style = typeStylePair.second
        val current = stream.current()

        if (type != "space") {
            if (state.lastToken == "(" && state.ctx.indentTo == null) {
                if (type == "symbol" && current in clojureHasBodyParameter) {
                    state.ctx.indentTo = state.ctx.start + stream.indentUnit
                } else {
                    state.ctx.indentTo = "next"
                }
            } else if (state.ctx.indentTo == "next") {
                state.ctx.indentTo = stream.column()
            }
            state.lastToken = current
        }

        if (type == "open") {
            state.ctx = ClojureCtx(
                prev = state.ctx,
                start = stream.column(),
                indentTo = null as Any
            )
        } else if (type == "close") {
            state.ctx = state.ctx.prev ?: state.ctx
        }

        return style
    }

    override fun indent(state: ClojureState, textAfter: String, context: IndentContext): Int? {
        val i = state.ctx.indentTo
        return if (i is Int) i else state.ctx.start + 1
    }

    override val languageData: Map<String, Any>
        get() = mapOf(
            "closeBrackets" to mapOf(
                "brackets" to listOf("(", "[", "{", "\"")
            ),
            "commentTokens" to mapOf("line" to ";;")
        )
}
