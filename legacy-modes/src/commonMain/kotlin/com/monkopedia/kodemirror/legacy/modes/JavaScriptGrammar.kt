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

import com.monkopedia.kodemirror.language.StringStream

/**
 * One entry of the parser's continuation stack (`state.cc` upstream).
 *
 * Combinators are compared by identity in a few places -- the scope walk in
 * `indent` looks for [JsGrammar.poplex], [JsGrammar.maybeelse],
 * [JsGrammar.popcontext] and the two `maybeoperator` combinators -- so each
 * named combinator must be a single instance per mode, exactly as each is a
 * single closure per `mkJavaScript` call upstream.
 *
 * [lex] marks the combinators that only rearrange lexical/variable scopes and
 * carry no token of their own; the parse loop drains them eagerly once a
 * token has been consumed.
 */
internal abstract class JsComb(val lex: Boolean = false) {
    abstract operator fun invoke(type: String, value: String): Boolean
}

private class JsFn(lex: Boolean, private val body: (String, String) -> Boolean) : JsComb(lex) {
    override fun invoke(type: String, value: String): Boolean = body(type, value)
}

/** A single entry in a linked list of variable names in scope. */
internal class JsVar(val name: String, val next: JsVar?)

/** A variable scope. [block] marks a block scope rather than a function scope. */
internal class JsContext(val prev: JsContext?, val vars: JsVar?, val block: Boolean)

private val jsAtomicTypes = setOf(
    "atom",
    "number",
    "variable",
    "string",
    "regexp",
    "this",
    "import",
    "jsonld-keyword"
)

private val jsEndOfExpr = Regex("[;\\}\\)\\],]")
private val jsCloser = Regex("[\\}\\)\\]]")
private val jsIncDec = Regex("\\+\\+|--")
private val jsTypeArgsAhead = Regex("^([^<>]|<[^<>]*>)*>\\s*\\(")
private val jsBlankRest = Regex("^\\s*\$")
private val jsWordAhead = Regex("^\\s*\\w")
private val jsIsPredicate = Regex("^\\s*\\w+\\s+is\\b")
private val jsColonAhead = Regex("^\\s*:\\s*")
private val jsPropAhead = Regex("^\\s*:")
private val jsClassMemberAhead = Regex("^\\s+#?[\\w\$\\u00a1-\\uffff]")

/**
 * The `parseJS` half of CodeMirror 5's JavaScript mode, as shipped in
 * `@codemirror/legacy-modes` 6.5.0 `mode/javascript.js`.
 *
 * This is the continuation-stack grammar that maintains `state.lexical`; the
 * tokenizer and the `StreamParser` surface live in `JavaScriptLegacy.kt`.
 * Without it every `indent` query answers the base `"block"` scope's
 * indentation and JavaScript indentation is a constant (issue #276).
 */
@Suppress("TooManyFunctions", "LargeClass")
internal class JsGrammar(
    private val isTS: Boolean,
    private val jsonMode: Boolean,
    private val jsonldMode: Boolean,
    private val findFatArrow: (StringStream, JavaScriptState) -> Unit
) {
    private var cxState: JavaScriptState = JavaScriptState()
    private var cxStream: StringStream = StringStream("", TAB_SIZE_PLACEHOLDER, 0)
    private var cxMarked: String? = null
    private var cxCc: MutableList<JsComb> = mutableListOf()
    private var cxStyle: String? = null

    // ---- Combinator utils ----

    private fun comb(body: (String, String) -> Boolean): JsComb = JsFn(false, body)

    private fun lexOp(body: () -> Unit): JsComb = JsFn(true) { _, _ ->
        body()
        false
    }

    private fun pass(vararg c: JsComb): Boolean {
        for (i in c.indices.reversed()) cxCc.add(c[i])
        return false
    }

    private fun cont(vararg c: JsComb): Boolean {
        pass(*c)
        return true
    }

    private fun inList(name: String, list: JsVar?): Boolean {
        var v = list
        while (v != null) {
            if (v.name == name) return true
            v = v.next
        }
        return false
    }

    private fun inScope(state: JavaScriptState, varname: String): Boolean {
        if (inList(varname, state.localVars)) return true
        var c = state.context
        while (c != null) {
            if (inList(varname, c.vars)) return true
            c = c.prev
        }
        return false
    }

    private fun register(varname: String) {
        val state = cxState
        cxMarked = "def"
        val context = state.context ?: return
        if (state.lexical.info == "var" && context.block) {
            val newContext = registerVarScoped(varname, context)
            if (newContext != null) {
                state.context = newContext
                return
            }
        } else if (!inList(varname, state.localVars)) {
            state.localVars = JsVar(varname, state.localVars)
            return
        }
        // Falling through means this is global; no globalVars are configured
        // by any of the modes this file exports, so there is nothing to record.
    }

    private fun registerVarScoped(varname: String, context: JsContext?): JsContext? = when {
        context == null -> null
        context.block -> {
            val inner = registerVarScoped(varname, context.prev)
            when {
                inner == null -> null
                inner === context.prev -> context
                else -> JsContext(inner, context.vars, true)
            }
        }
        inList(varname, context.vars) -> context
        else -> JsContext(context.prev, JsVar(varname, context.vars), false)
    }

    private fun isModifier(name: String): Boolean = name == "public" ||
        name == "private" ||
        name == "protected" ||
        name == "abstract" ||
        name == "readonly"

    // ---- Scope combinators ----

    private val defaultVars = JsVar("this", JsVar("arguments", null))

    private val pushcontext = lexOp {
        cxState.context = JsContext(cxState.context, cxState.localVars, false)
        cxState.localVars = defaultVars
    }

    private val pushblockcontext = lexOp {
        cxState.context = JsContext(cxState.context, cxState.localVars, true)
        cxState.localVars = null
    }

    /** Identity-compared by `indent`; must stay a single instance. */
    val popcontext = lexOp {
        // Upstream dereferences `state.context` unguarded here. Every push is
        // paired with a pop by construction, but the stack is also rebuilt from
        // a copied state, so guard rather than throw.
        val context = cxState.context ?: return@lexOp
        cxState.localVars = context.vars
        cxState.context = context.prev
    }

    private fun pushlex(type: String, info: String? = null): JsComb = lexOp {
        val state = cxState
        var indent = state.indented
        if (state.lexical.type == "stat") {
            indent = state.lexical.indented
        } else {
            var outer: JSLexical? = state.lexical
            while (outer != null && outer.type == ")" && outer.align == true) {
                indent = outer.indented
                outer = outer.prev
            }
        }
        state.lexical = JSLexical(indent, cxStream.column(), type, null, state.lexical, info)
    }

    /** Identity-compared by `indent`; must stay a single instance. */
    val poplex = lexOp {
        val state = cxState
        val prev = state.lexical.prev
        if (prev != null) {
            if (state.lexical.type == ")") state.indented = state.lexical.indented
            state.lexical = prev
        }
    }

    private inner class Expect(private val wanted: String) : JsComb() {
        override fun invoke(type: String, value: String): Boolean = when {
            type == wanted -> cont()
            wanted == ";" || type == "}" || type == ")" || type == "]" -> pass()
            else -> cont(this)
        }
    }

    private fun expect(wanted: String): JsComb = Expect(wanted)

    private inner class CommaSep(
        private val what: JsComb,
        private val end: String,
        private val sep: String? = null
    ) : JsComb() {
        private val proceed: JsComb = object : JsComb() {
            override fun invoke(type: String, value: String): Boolean {
                if (if (sep != null) sep.contains(type) else type == ",") {
                    return cont(
                        comb { t, v ->
                            if (t == end || v == end) pass() else pass(what)
                        },
                        this
                    )
                }
                if (type == end || value == end) return cont()
                if (sep != null && sep.contains(";")) return pass(what)
                return cont(expect(end))
            }
        }

        override fun invoke(type: String, value: String): Boolean {
            if (type == end || value == end) return cont()
            return pass(what, proceed)
        }
    }

    private fun commasep(what: JsComb, end: String, sep: String? = null): JsComb =
        CommaSep(what, end, sep)

    private fun contCommasep(
        what: JsComb,
        end: String,
        info: String? = null,
        vararg rest: JsComb
    ): Boolean {
        for (c in rest) cxCc.add(c)
        return cont(pushlex(end, info), commasep(what, end), poplex)
    }

    // ---- Statements ----

    @Suppress("CyclomaticComplexMethod", "ReturnCount", "LongMethod")
    val statement: JsComb = comb { type, value ->
        when {
            type == "var" -> cont(pushlex("vardef", value), vardef, expect(";"), poplex)
            type == "keyword a" -> cont(pushlex("form"), parenExpr, statement, poplex)
            type == "keyword b" -> cont(pushlex("form"), statement, poplex)
            type == "keyword d" ->
                if (cxStream.match(jsBlankRest, consume = false) != null) {
                    cont()
                } else {
                    cont(pushlex("stat"), maybeexpression, expect(";"), poplex)
                }
            type == "debugger" -> cont(expect(";"))
            type == "{" -> cont(pushlex("}"), pushblockcontext, block, poplex, popcontext)
            type == ";" -> cont()
            type == "if" -> {
                if (cxState.lexical.info == "else" && cxState.cc.lastOrNull() === poplex) {
                    cxState.cc.removeAt(cxState.cc.size - 1)("", "")
                }
                cont(pushlex("form"), parenExpr, statement, poplex, maybeelse)
            }
            type == "function" -> cont(functiondef)
            type == "for" ->
                cont(pushlex("form"), pushblockcontext, forspec, statement, popcontext, poplex)
            type == "class" || (isTS && value == "interface") -> {
                cxMarked = "keyword"
                cont(pushlex("form", if (type == "class") type else value), className, poplex)
            }
            type == "variable" -> tsOrPlainVariableStatement(value)
            type == "switch" -> cont(
                pushlex("form"),
                parenExpr,
                expect("{"),
                pushlex("}", "switch"),
                pushblockcontext,
                block,
                poplex,
                poplex,
                popcontext
            )
            type == "case" -> cont(expression, expect(":"))
            type == "default" -> cont(expect(":"))
            type == "catch" ->
                cont(pushlex("form"), pushcontext, maybeCatchBinding, statement, poplex, popcontext)
            type == "export" -> cont(pushlex("stat"), afterExport, poplex)
            type == "import" -> cont(pushlex("stat"), afterImport, poplex)
            type == "async" -> cont(statement)
            value == "@" -> cont(expression, statement)
            else -> pass(pushlex("stat"), expression, expect(";"), poplex)
        }
    }

    @Suppress("ReturnCount")
    private fun tsOrPlainVariableStatement(value: String): Boolean {
        if (isTS && value == "declare") {
            cxMarked = "keyword"
            return cont(statement)
        }
        if (isTS &&
            (value == "module" || value == "enum" || value == "type") &&
            cxStream.match(jsWordAhead, consume = false) != null
        ) {
            cxMarked = "keyword"
            return when (value) {
                "enum" -> cont(enumdef)
                "type" -> cont(typename, expect("operator"), typeexpr, expect(";"))
                else -> cont(
                    pushlex("form"),
                    pattern,
                    expect("{"),
                    pushlex("}"),
                    block,
                    poplex,
                    poplex
                )
            }
        }
        if (isTS && value == "namespace") {
            cxMarked = "keyword"
            return cont(pushlex("form"), expression, statement, poplex)
        }
        if (isTS && value == "abstract") {
            cxMarked = "keyword"
            return cont(statement)
        }
        return cont(pushlex("stat"), maybelabel)
    }

    private val maybeCatchBinding: JsComb = comb { type, _ ->
        if (type == "(") cont(funarg, expect(")")) else false
    }

    // ---- Expressions ----

    val expression: JsComb = comb { type, value -> expressionInner(type, value, false) }

    private val expressionNoComma: JsComb =
        comb { type, value -> expressionInner(type, value, true) }

    private val parenExpr: JsComb = comb { type, _ ->
        if (type != "(") {
            pass()
        } else {
            cont(pushlex(")"), maybeexpression, expect(")"), poplex)
        }
    }

    @Suppress("CyclomaticComplexMethod", "ReturnCount")
    private fun expressionInner(type: String, value: String, noComma: Boolean): Boolean {
        if (cxState.fatArrowAt == cxStream.start) {
            val body = if (noComma) arrowBodyNoComma else arrowBody
            if (type == "(") {
                return cont(
                    pushcontext,
                    pushlex(")"),
                    commasep(funarg, ")"),
                    poplex,
                    expect("=>"),
                    body,
                    popcontext
                )
            } else if (type == "variable") {
                return pass(pushcontext, pattern, expect("=>"), body, popcontext)
            }
        }
        val maybeop = if (noComma) maybeoperatorNoComma else maybeoperatorComma
        if (type in jsAtomicTypes) return cont(maybeop)
        if (type == "function") return cont(functiondef, maybeop)
        if (type == "class" || (isTS && value == "interface")) {
            cxMarked = "keyword"
            return cont(pushlex("form"), classExpression, poplex)
        }
        if (type == "keyword c" || type == "async") {
            return cont(if (noComma) expressionNoComma else expression)
        }
        if (type == "(") return cont(pushlex(")"), maybeexpression, expect(")"), poplex, maybeop)
        if (type == "operator" || type == "spread") {
            return cont(if (noComma) expressionNoComma else expression)
        }
        if (type == "[") return cont(pushlex("]"), arrayLiteral, poplex, maybeop)
        if (type == "{") return contCommasep(objprop, "}", null, maybeop)
        if (type == "quasi") return pass(quasi, maybeop)
        if (type == "new") return cont(maybeTarget(noComma))
        return cont()
    }

    private val maybeexpression: JsComb = comb { type, _ ->
        if (jsEndOfExpr.containsMatchIn(type)) pass() else pass(expression)
    }

    /** Identity-compared by `indent`; must stay a single instance. */
    val maybeoperatorComma: JsComb = comb { type, value ->
        if (type == ",") cont(maybeexpression) else maybeOperator(type, value, false)
    }

    /** Identity-compared by `indent`; must stay a single instance. */
    val maybeoperatorNoComma: JsComb = comb { type, value -> maybeOperator(type, value, null) }

    @Suppress("CyclomaticComplexMethod", "ReturnCount")
    private fun maybeOperator(type: String, value: String, noComma: Boolean?): Boolean {
        val me = if (noComma == false) maybeoperatorComma else maybeoperatorNoComma
        val expr = if (noComma == false) expression else expressionNoComma
        if (type == "=>") {
            val body = if (noComma == true) arrowBodyNoComma else arrowBody
            return cont(pushcontext, body, popcontext)
        }
        if (type == "operator") {
            if (jsIncDec.containsMatchIn(value) || (isTS && value == "!")) return cont(me)
            if (isTS &&
                value == "<" &&
                cxStream.match(jsTypeArgsAhead, consume = false) != null
            ) {
                return cont(pushlex(">"), commasep(typeexpr, ">"), poplex, me)
            }
            if (value == "?") return cont(expression, expect(":"), expr)
            return cont(expr)
        }
        if (type == "quasi") return pass(quasi, me)
        if (type == ";") return false
        if (type == "(") return contCommasep(expressionNoComma, ")", "call", me)
        if (type == ".") return cont(property, me)
        if (type == "[") return cont(pushlex("]"), maybeexpression, expect("]"), poplex, me)
        if (isTS && value == "as") {
            cxMarked = "keyword"
            return cont(typeexpr, me)
        }
        if (type == "regexp") {
            cxMarked = "operator"
            cxState.lastType = "operator"
            cxStream.backUp(cxStream.pos - cxStream.start - 1)
            return cont(expr)
        }
        return false
    }

    private val quasi: JsComb = comb { type, value ->
        when {
            type != "quasi" -> pass()
            value.takeLast(2) != "\${" -> cont(quasi)
            else -> cont(maybeexpression, continueQuasi)
        }
    }

    private val continueQuasi: JsComb = comb { type, _ ->
        if (type == "}") {
            cxMarked = "string.special"
            cxState.tokenize = JS_TOKENIZE_QUASI
            cont(quasi)
        } else {
            false
        }
    }

    private val arrowBody: JsComb = comb { type, _ ->
        findFatArrow(cxStream, cxState)
        pass(if (type == "{") statement else expression)
    }

    private val arrowBodyNoComma: JsComb = comb { type, _ ->
        findFatArrow(cxStream, cxState)
        pass(if (type == "{") statement else expressionNoComma)
    }

    private fun maybeTarget(noComma: Boolean): JsComb = comb { type, _ ->
        when {
            type == "." -> cont(if (noComma) targetNoComma else target)
            type == "variable" && isTS ->
                cont(maybeTypeArgs, if (noComma) maybeoperatorNoComma else maybeoperatorComma)
            else -> pass(if (noComma) expressionNoComma else expression)
        }
    }

    private val target: JsComb = comb { _, value ->
        if (value == "target") {
            cxMarked = "keyword"
            cont(maybeoperatorComma)
        } else {
            false
        }
    }

    private val targetNoComma: JsComb = comb { _, value ->
        if (value == "target") {
            cxMarked = "keyword"
            cont(maybeoperatorNoComma)
        } else {
            false
        }
    }

    private val maybelabel: JsComb = comb { type, _ ->
        if (type == ":") {
            cont(poplex, statement)
        } else {
            pass(maybeoperatorComma, expect(";"), poplex)
        }
    }

    private val property: JsComb = comb { type, _ ->
        if (type == "variable") {
            cxMarked = "property"
            cont()
        } else {
            false
        }
    }

    @Suppress("CyclomaticComplexMethod", "ReturnCount")
    private val objprop: JsComb = comb { type, value ->
        when {
            type == "async" -> {
                cxMarked = "property"
                cont(objprop)
            }
            type == "variable" || cxStyle == "keyword" -> {
                cxMarked = "property"
                if (value == "get" || value == "set") {
                    cont(getterSetter)
                } else {
                    // Work around fat-arrow-detection complication for
                    // detecting typescript typed arrow params.
                    if (isTS && cxState.fatArrowAt == cxStream.start) {
                        val m = cxStream.match(jsColonAhead, consume = false)
                        if (m != null) cxState.fatArrowAt = cxStream.pos + m.value.length
                    }
                    cont(afterprop)
                }
            }
            type == "number" || type == "string" -> {
                cxMarked = if (jsonldMode) "property" else "$cxStyle property"
                cont(afterprop)
            }
            type == "jsonld-keyword" -> cont(afterprop)
            isTS && isModifier(value) -> {
                cxMarked = "keyword"
                cont(objprop)
            }
            type == "[" -> cont(expression, maybetype, expect("]"), afterprop)
            type == "spread" -> cont(expressionNoComma, afterprop)
            value == "*" -> {
                cxMarked = "keyword"
                cont(objprop)
            }
            type == ":" -> pass(afterprop)
            else -> false
        }
    }

    private val getterSetter: JsComb = comb { type, _ ->
        if (type != "variable") {
            pass(afterprop)
        } else {
            cxMarked = "property"
            cont(functiondef)
        }
    }

    private val afterprop: JsComb = comb { type, _ ->
        when (type) {
            ":" -> cont(expressionNoComma)
            "(" -> pass(functiondef)
            else -> false
        }
    }

    private val block: JsComb = comb { type, _ ->
        if (type == "}") cont() else pass(statement, block)
    }

    // ---- Types (TypeScript) ----

    private val maybetype: JsComb = comb { type, value ->
        when {
            !isTS -> false
            type == ":" -> cont(typeexpr)
            value == "?" -> cont(maybetype)
            else -> false
        }
    }

    private val maybetypeOrIn: JsComb = comb { type, value ->
        if (isTS && (type == ":" || value == "in")) cont(typeexpr) else false
    }

    private val mayberettype: JsComb = comb { type, _ ->
        if (isTS && type == ":") {
            if (cxStream.match(jsIsPredicate, consume = false) != null) {
                cont(expression, isKW, typeexpr)
            } else {
                cont(typeexpr)
            }
        } else {
            false
        }
    }

    private val isKW: JsComb = comb { _, value ->
        if (value == "is") {
            cxMarked = "keyword"
            cont()
        } else {
            false
        }
    }

    @Suppress("CyclomaticComplexMethod")
    private val typeexpr: JsComb = comb { type, value ->
        when {
            value == "keyof" || value == "typeof" || value == "infer" || value == "readonly" -> {
                cxMarked = "keyword"
                cont(if (value == "typeof") expressionNoComma else typeexpr)
            }
            type == "variable" || value == "void" -> {
                cxMarked = "type"
                cont(afterType)
            }
            value == "|" || value == "&" -> cont(typeexpr)
            type == "string" || type == "number" || type == "atom" -> cont(afterType)
            type == "[" -> cont(pushlex("]"), commasep(typeexpr, "]", ","), poplex, afterType)
            type == "{" -> cont(pushlex("}"), typeprops, poplex, afterType)
            type == "(" -> cont(commasep(typearg, ")"), maybeReturnType, afterType)
            type == "<" -> cont(commasep(typeexpr, ">"), typeexpr)
            type == "quasi" -> pass(quasiType, afterType)
            else -> false
        }
    }

    private val maybeReturnType: JsComb = comb { type, _ ->
        if (type == "=>") cont(typeexpr) else false
    }

    private val typeprops: JsComb = comb { type, _ ->
        when {
            jsCloser.containsMatchIn(type) -> cont()
            type == "," || type == ";" -> cont(typeprops)
            else -> pass(typeprop, typeprops)
        }
    }

    private val typeprop: JsComb = comb { type, value ->
        when {
            type == "variable" || cxStyle == "keyword" -> {
                cxMarked = "property"
                cont(typeprop)
            }
            value == "?" || type == "number" || type == "string" -> cont(typeprop)
            type == ":" -> cont(typeexpr)
            type == "[" -> cont(expect("variable"), maybetypeOrIn, expect("]"), typeprop)
            type == "(" -> pass(functiondecl, typeprop)
            !jsEndOfExpr.containsMatchIn(type) -> cont()
            else -> false
        }
    }

    private val quasiType: JsComb = comb { type, value ->
        when {
            type != "quasi" -> pass()
            value.takeLast(2) != "\${" -> cont(quasiType)
            else -> cont(typeexpr, continueQuasiType)
        }
    }

    private val continueQuasiType: JsComb = comb { type, _ ->
        if (type == "}") {
            cxMarked = "string.special"
            cxState.tokenize = JS_TOKENIZE_QUASI
            cont(quasiType)
        } else {
            false
        }
    }

    private val typearg: JsComb = comb { type, value ->
        when {
            (type == "variable" && cxStream.match(jsTypeArgAhead, consume = false) != null) ||
                value == "?" -> cont(typearg)
            type == ":" -> cont(typeexpr)
            type == "spread" -> cont(typearg)
            else -> pass(typeexpr)
        }
    }

    private val afterType: JsComb = comb { type, value ->
        when {
            value == "<" -> cont(pushlex(">"), commasep(typeexpr, ">"), poplex, afterType)
            value == "|" || type == "." || value == "&" -> cont(typeexpr)
            type == "[" -> cont(typeexpr, expect("]"), afterType)
            value == "extends" || value == "implements" -> {
                cxMarked = "keyword"
                cont(typeexpr)
            }
            value == "?" -> cont(typeexpr, expect(":"), typeexpr)
            else -> false
        }
    }

    private val maybeTypeArgs: JsComb = comb { _, value ->
        if (value == "<") {
            cont(pushlex(">"), commasep(typeexpr, ">"), poplex, afterType)
        } else {
            false
        }
    }

    private val typeparam: JsComb = comb { _, _ -> pass(typeexpr, maybeTypeDefault) }

    private val maybeTypeDefault: JsComb = comb { _, value ->
        if (value == "=") cont(typeexpr) else false
    }

    // ---- Declarations and patterns ----

    private val vardef: JsComb = comb { _, value ->
        if (value == "enum") {
            cxMarked = "keyword"
            cont(enumdef)
        } else {
            pass(pattern, maybetype, maybeAssign, vardefCont)
        }
    }

    private val pattern: JsComb = comb { type, value ->
        when {
            isTS && isModifier(value) -> {
                cxMarked = "keyword"
                cont(pattern)
            }
            type == "variable" -> {
                register(value)
                cont()
            }
            type == "spread" -> cont(pattern)
            type == "[" -> contCommasep(eltpattern, "]")
            type == "{" -> contCommasep(proppattern, "}")
            else -> false
        }
    }

    private val proppattern: JsComb = comb { type, value ->
        when {
            type == "variable" && cxStream.match(jsPropAhead, consume = false) == null -> {
                register(value)
                cont(maybeAssign)
            }
            type == "spread" -> cont(pattern)
            type == "}" -> pass()
            type == "[" -> cont(expression, expect("]"), expect(":"), proppattern)
            else -> {
                if (type == "variable") cxMarked = "property"
                cont(expect(":"), pattern, maybeAssign)
            }
        }
    }

    private val eltpattern: JsComb = comb { _, _ -> pass(pattern, maybeAssign) }

    private val maybeAssign: JsComb = comb { _, value ->
        if (value == "=") cont(expressionNoComma) else false
    }

    private val vardefCont: JsComb = comb { type, _ ->
        if (type == ",") cont(vardef) else false
    }

    /** Identity-compared by `indent`; must stay a single instance. */
    val maybeelse: JsComb = comb { type, value ->
        if (type == "keyword b" && value == "else") {
            cont(pushlex("form", "else"), statement, poplex)
        } else {
            false
        }
    }

    private val forspec: JsComb = comb { type, value ->
        when {
            value == "await" -> cont(forspec)
            type == "(" -> cont(pushlex(")"), forspec1, poplex)
            else -> false
        }
    }

    private val forspec1: JsComb = comb { type, _ ->
        when (type) {
            "var" -> cont(vardef, forspec2)
            "variable" -> cont(forspec2)
            else -> pass(forspec2)
        }
    }

    private val forspec2: JsComb = comb { type, value ->
        when {
            type == ")" -> cont()
            type == ";" -> cont(forspec2)
            value == "in" || value == "of" -> {
                cxMarked = "keyword"
                cont(expression, forspec2)
            }
            else -> pass(expression, forspec2)
        }
    }

    private val functiondef: JsComb = comb { type, value ->
        when {
            value == "*" -> {
                cxMarked = "keyword"
                cont(functiondef)
            }
            type == "variable" -> {
                register(value)
                cont(functiondef)
            }
            type == "(" -> cont(
                pushcontext,
                pushlex(")"),
                commasep(funarg, ")"),
                poplex,
                mayberettype,
                statement,
                popcontext
            )
            isTS && value == "<" ->
                cont(pushlex(">"), commasep(typeparam, ">"), poplex, functiondef)
            else -> false
        }
    }

    private val functiondecl: JsComb = comb { type, value ->
        when {
            value == "*" -> {
                cxMarked = "keyword"
                cont(functiondecl)
            }
            type == "variable" -> {
                register(value)
                cont(functiondecl)
            }
            type == "(" -> cont(
                pushcontext,
                pushlex(")"),
                commasep(funarg, ")"),
                poplex,
                mayberettype,
                popcontext
            )
            isTS && value == "<" ->
                cont(pushlex(">"), commasep(typeparam, ">"), poplex, functiondecl)
            else -> false
        }
    }

    private val typename: JsComb = comb { type, value ->
        when {
            type == "keyword" || type == "variable" -> {
                cxMarked = "type"
                cont(typename)
            }
            value == "<" -> cont(pushlex(">"), commasep(typeparam, ">"), poplex)
            else -> false
        }
    }

    private val funarg: JsComb = comb { type, value ->
        // Upstream's `if (value == "@") cont(expression, funarg)` has no
        // `return`, so the pushed continuations stay and control falls through
        // to the checks below. Kept as-is; changing it is a behaviour change.
        if (value == "@") cont(expression, funarg)
        when {
            type == "spread" -> cont(funarg)
            isTS && isModifier(value) -> {
                cxMarked = "keyword"
                cont(funarg)
            }
            isTS && type == "this" -> cont(maybetype, maybeAssign)
            else -> pass(pattern, maybetype, maybeAssign)
        }
    }

    private val classExpression: JsComb = comb { type, value ->
        // Class expressions may have an optional name.
        if (type == "variable") className(type, value) else classNameAfter(type, value)
    }

    private val className: JsComb = comb { type, value ->
        if (type == "variable") {
            register(value)
            cont(classNameAfter)
        } else {
            false
        }
    }

    private val classNameAfter: JsComb = comb { type, value ->
        when {
            value == "<" ->
                cont(pushlex(">"), commasep(typeparam, ">"), poplex, classNameAfter)
            value == "extends" || value == "implements" || (isTS && type == ",") -> {
                if (value == "implements") cxMarked = "keyword"
                cont(if (isTS) typeexpr else expression, classNameAfter)
            }
            type == "{" -> cont(pushlex("}"), classBody, poplex)
            else -> false
        }
    }

    private val classMemberModifiers = setOf("static", "get", "set")

    private fun isClassMemberModifier(type: String, value: String): Boolean {
        if (type != "variable") return false
        val named = value in classMemberModifiers || (isTS && isModifier(value))
        return named && cxStream.match(jsClassMemberAhead, consume = false) != null
    }

    @Suppress("CyclomaticComplexMethod")
    private val classBody: JsComb = comb { type, value ->
        when {
            type == "async" || isClassMemberModifier(type, value) -> {
                cxMarked = "keyword"
                cont(classBody)
            }
            type == "variable" || cxStyle == "keyword" -> {
                cxMarked = "property"
                cont(classfield, classBody)
            }
            type == "number" || type == "string" -> cont(classfield, classBody)
            type == "[" -> cont(expression, maybetype, expect("]"), classfield, classBody)
            value == "*" -> {
                cxMarked = "keyword"
                cont(classBody)
            }
            isTS && type == "(" -> pass(functiondecl, classBody)
            type == ";" || type == "," -> cont(classBody)
            type == "}" -> cont()
            value == "@" -> cont(expression, classBody)
            else -> false
        }
    }

    private val classfield: JsComb = comb { type, value ->
        when {
            value == "!" || value == "?" -> cont(classfield)
            type == ":" -> cont(typeexpr, maybeAssign)
            value == "=" -> cont(expressionNoComma)
            else -> {
                val context = cxState.lexical.prev
                val isInterface = context != null && context.info == "interface"
                pass(if (isInterface) functiondecl else functiondef)
            }
        }
    }

    // ---- Modules ----

    private val afterExport: JsComb = comb { type, value ->
        when {
            value == "*" -> {
                cxMarked = "keyword"
                cont(maybeFrom, expect(";"))
            }
            value == "default" -> {
                cxMarked = "keyword"
                cont(expression, expect(";"))
            }
            type == "{" -> cont(commasep(exportField, "}"), maybeFrom, expect(";"))
            else -> pass(statement)
        }
    }

    private val exportField: JsComb = comb { type, value ->
        when {
            value == "as" -> {
                cxMarked = "keyword"
                cont(expect("variable"))
            }
            type == "variable" -> pass(expressionNoComma, exportField)
            else -> false
        }
    }

    private val afterImport: JsComb = comb { type, _ ->
        when (type) {
            "string" -> cont()
            "(" -> pass(expression)
            "." -> pass(maybeoperatorComma)
            else -> pass(importSpec, maybeMoreImports, maybeFrom)
        }
    }

    private val importSpec: JsComb = comb { type, value ->
        if (type == "{") {
            contCommasep(importSpec, "}")
        } else {
            if (type == "variable") register(value)
            if (value == "*") cxMarked = "keyword"
            cont(maybeAs)
        }
    }

    private val maybeMoreImports: JsComb = comb { type, _ ->
        if (type == ",") cont(importSpec, maybeMoreImports) else false
    }

    private val maybeAs: JsComb = comb { _, value ->
        if (value == "as") {
            cxMarked = "keyword"
            cont(importSpec)
        } else {
            false
        }
    }

    private val maybeFrom: JsComb = comb { _, value ->
        if (value == "from") {
            cxMarked = "keyword"
            cont(expression)
        } else {
            false
        }
    }

    private val arrayLiteral: JsComb = comb { type, _ ->
        if (type == "]") cont() else pass(commasep(expressionNoComma, "]"))
    }

    private val enumdef: JsComb = comb { _, _ ->
        pass(
            pushlex("form"),
            pattern,
            expect("{"),
            pushlex("}"),
            commasep(enummember, "}"),
            poplex,
            poplex
        )
    }

    private val enummember: JsComb = comb { _, _ -> pass(pattern, maybeAssign) }

    // ---- Driver ----

    /**
     * Run the continuation stack over one token, returning the style to use
     * for it. Mirrors upstream's `parseJS`.
     */
    fun parseJS(
        state: JavaScriptState,
        style: String?,
        type: String,
        content: String,
        stream: StringStream
    ): String? {
        val cc = state.cc
        cxState = state
        cxStream = stream
        cxMarked = null
        cxCc = cc
        cxStyle = style

        if (state.lexical.align == null) state.lexical.align = true

        while (true) {
            val combinator = if (cc.isNotEmpty()) {
                cc.removeAt(cc.size - 1)
            } else if (jsonMode) {
                expression
            } else {
                statement
            }
            if (combinator(type, content)) {
                while (cc.isNotEmpty() && cc[cc.size - 1].lex) {
                    cc.removeAt(cc.size - 1)("", "")
                }
                val marked = cxMarked
                if (!marked.isNullOrEmpty()) return marked
                if (type == "variable" && inScope(state, content)) return "variableName.local"
                return style
            }
        }
    }

    private companion object {
        const val TAB_SIZE_PLACEHOLDER = 4
    }
}

private val jsTypeArgAhead = Regex("^\\s*[?:]")

/** [JavaScriptState.tokenize] value for the template-literal tokenizer. */
internal const val JS_TOKENIZE_QUASI = 3
