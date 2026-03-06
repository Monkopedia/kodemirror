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

import com.monkopedia.kodemirror.language.StreamParser
import com.monkopedia.kodemirror.language.StringStream

private val fortranKeywords = setOf(
    "abstract", "accept", "allocatable", "allocate", "array", "assign",
    "asynchronous", "backspace", "bind", "block", "byte", "call",
    "case", "class", "close", "common", "contains", "continue",
    "cycle", "data", "deallocate", "decode", "deferred", "dimension",
    "do", "elemental", "else", "encode", "end", "endif", "entry",
    "enumerator", "equivalence", "exit", "external", "extrinsic",
    "final", "forall", "format", "function", "generic", "go", "goto",
    "if", "implicit", "import", "include", "inquire", "intent",
    "interface", "intrinsic", "module", "namelist", "non_intrinsic",
    "non_overridable", "none", "nopass", "nullify", "open",
    "optional", "options", "parameter", "pass", "pause", "pointer",
    "print", "private", "program", "protected", "public", "pure",
    "read", "recursive", "result", "return", "rewind", "save",
    "select", "sequence", "stop", "subroutine", "target", "then",
    "to", "type", "use", "value", "volatile", "where", "while",
    "write"
)

private val fortranBuiltins = setOf(
    "abort", "abs", "access", "achar", "acos", "adjustl", "adjustr",
    "aimag", "aint", "alarm", "all", "allocated", "alog", "amax",
    "amin", "amod", "and", "anint", "any", "asin", "associated",
    "atan", "besj", "besjn", "besy", "besyn", "bit_size", "btest",
    "cabs", "ccos", "ceiling", "cexp", "char", "chdir", "chmod",
    "clog", "cmplx", "command_argument_count", "complex", "conjg",
    "cos", "cosh", "count", "cpu_time", "cshift", "csin", "csqrt",
    "ctime", "c_funloc", "c_loc", "c_associated", "c_null_ptr",
    "c_null_funptr", "c_f_pointer", "c_null_char", "c_alert",
    "c_backspace", "c_form_feed", "c_new_line", "c_carriage_return",
    "c_horizontal_tab", "c_vertical_tab", "dabs", "dacos", "dasin",
    "datan", "date_and_time", "dbesj", "dbesjn", "dbesy", "dbesyn",
    "dble", "dcos", "dcosh", "ddim", "derf", "derfc", "dexp",
    "digits", "dim", "dint", "dlog", "dmax", "dmin", "dmod", "dnint",
    "dot_product", "dprod", "dsign", "dsinh", "dsin", "dsqrt",
    "dtanh", "dtan", "dtime", "eoshift", "epsilon", "erf", "erfc",
    "etime", "exit", "exp", "exponent", "extends_type_of", "fdate",
    "fget", "fgetc", "float", "floor", "flush", "fnum", "fputc",
    "fput", "fraction", "fseek", "fstat", "ftell", "gerror",
    "getarg", "get_command", "get_command_argument",
    "get_environment_variable", "getcwd", "getenv", "getgid",
    "getlog", "getpid", "getuid", "gmtime", "hostnm", "huge", "iabs",
    "iachar", "iand", "iargc", "ibclr", "ibits", "ibset", "ichar",
    "idate", "idim", "idint", "idnint", "ieor", "ierrno", "ifix",
    "imag", "imagpart", "index", "int", "ior", "irand", "isatty",
    "ishft", "ishftc", "isign", "iso_c_binding", "is_iostat_end",
    "is_iostat_eor", "itime", "kill", "kind", "lbound", "len",
    "len_trim", "lge", "lgt", "link", "lle", "llt", "lnblnk", "loc",
    "log", "logical", "long", "lshift", "lstat", "ltime", "matmul",
    "max", "maxexponent", "maxloc", "maxval", "mclock", "merge",
    "move_alloc", "min", "minexponent", "minloc", "minval", "mod",
    "modulo", "mvbits", "nearest", "new_line", "nint", "not", "or",
    "pack", "perror", "precision", "present", "product", "radix",
    "rand", "random_number", "random_seed", "range", "real",
    "realpart", "rename", "repeat", "reshape", "rrspacing", "rshift",
    "same_type_as", "scale", "scan", "second", "selected_int_kind",
    "selected_real_kind", "set_exponent", "shape", "short", "sign",
    "signal", "sinh", "sin", "sleep", "sngl", "spacing", "spread",
    "sqrt", "srand", "stat", "sum", "symlnk", "system",
    "system_clock", "tan", "tanh", "time", "tiny", "transfer",
    "transpose", "trim", "ttynam", "ubound", "umask", "unlink",
    "unpack", "verify", "xor", "zabs", "zcos", "zexp", "zlog",
    "zsin", "zsqrt"
)

private val fortranDataTypes = setOf(
    "c_bool", "c_char", "c_double", "c_double_complex", "c_float",
    "c_float_complex", "c_funptr", "c_int", "c_int16_t", "c_int32_t",
    "c_int64_t", "c_int8_t", "c_int_fast16_t", "c_int_fast32_t",
    "c_int_fast64_t", "c_int_fast8_t", "c_int_least16_t",
    "c_int_least32_t", "c_int_least64_t", "c_int_least8_t",
    "c_intmax_t", "c_intptr_t", "c_long", "c_long_double",
    "c_long_double_complex", "c_long_long", "c_ptr", "c_short",
    "c_signed_char", "c_size_t", "character", "complex", "double",
    "integer", "logical", "real"
)

private val fortranIsOperatorChar = Regex("[+\\-*&=<>/:]")
private val fortranLitOperator =
    Regex("^\\.(and|or|eq|lt|le|gt|ge|ne|not|eqv|neqv)\\.", RegexOption.IGNORE_CASE)

data class FortranState(
    var tokenize: ((StringStream, FortranState) -> String?)? = null
)

private fun fortranTokenBase(stream: StringStream, state: FortranState): String? {
    if (stream.match(fortranLitOperator) != null) {
        return "operator"
    }
    val ch = stream.next() ?: return null
    if (ch == "!") {
        stream.skipToEnd()
        return "comment"
    }
    if (ch == "\"" || ch == "'") {
        state.tokenize = fortranTokenString(ch)
        return state.tokenize!!(stream, state)
    }
    if (Regex("[\\[\\](),]").containsMatchIn(ch)) {
        return null
    }
    if (Regex("\\d").containsMatchIn(ch)) {
        stream.eatWhile(Regex("[\\w.]"))
        return "number"
    }
    if (fortranIsOperatorChar.containsMatchIn(ch)) {
        stream.eatWhile(fortranIsOperatorChar)
        return "operator"
    }
    stream.eatWhile(Regex("[\\w\$_]"))
    val word = stream.current().lowercase()
    if (word in fortranKeywords) return "keyword"
    if (word in fortranBuiltins || word in fortranDataTypes) {
        return "builtin"
    }
    return "variable"
}

private fun fortranTokenString(quote: String): (StringStream, FortranState) -> String? =
    { stream, state ->
        var escaped = false
        var end = false
        var next: String?
        while (true) {
            next = stream.next()
            if (next == null) break
            if (next == quote && !escaped) {
                end = true
                break
            }
            escaped = !escaped && next == "\\"
        }
        if (end || !escaped) state.tokenize = null
        "string"
    }

/** Stream parser for Fortran. */
val fortran: StreamParser<FortranState> =
    object : StreamParser<FortranState> {
        override val name: String get() = "fortran"

        override fun startState(indentUnit: Int) = FortranState()
        override fun copyState(state: FortranState) = state.copy()

        override fun token(stream: StringStream, state: FortranState): String? {
            if (stream.eatSpace()) return null
            return (state.tokenize ?: ::fortranTokenBase)(stream, state)
        }
    }
