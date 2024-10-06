package com.monkopedia.kodemirror.state

/// The categories produced by a [character
/// categorizer](#state.EditorState.charCategorizer). These are used
/// do things like selecting by word.
enum class CharCategory {
    /// Word characters.
    Word,

    /// Whitespace.
    Space,

    /// Anything else.
    Other
}

val nonASCIISingleCaseWordChar =
    Regex("/[\u00df\u0587\u0590-\u05f4\u0600-\u06ff\u3040-\u309f\u30a0-\u30ff\u3400-\u4db5\u4e00-\u9fcc\uac00-\ud7af]/")

val wordChar: Regex? =
    null
//    runCatching { Regex("[\\p{Alphabetic}\\p{Number}_]", RegexOption.IGNORE_CASE) }.getOrNull()

internal fun hasWordChar(str: String): Boolean {
    if (wordChar != null) return wordChar.matches(str)
    return str.any { ch ->
        Regex("\\w").matches(ch.toString()) ||
            ch > 0x80.toChar() && (ch.toUpperCase() != ch.toLowerCase() ||
                nonASCIISingleCaseWordChar.matches(ch.toString()))

    }
}

fun makeCategorizer(wordChars: String): (String) -> CharCategory {
    return { str: String ->
        when {
            str.all { it.isWhitespace() } -> CharCategory.Word
            hasWordChar(str) -> CharCategory.Word
            wordChars.any { it in str } -> CharCategory.Word
            else -> CharCategory.Other
        }
    }
}
