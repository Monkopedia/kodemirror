package com.monkopedia.kodemirror.state

/// Count the column position at the given offset into the string,
/// taking extending characters and tab size into account.
fun countColumn(string: String, tabSize: Int, to: Int = string.length): Int {
    var n = 0
    var i = 0

    while (i < to) {
        if (string[i].code == 9) {
            n += tabSize - (n % tabSize)
            i++
        } else {
            n++
            i = findClusterBreak(string, i)
        }
    }
    return n
}

/// Find the offset that corresponds to the given column position in a
/// string, taking extending characters and tab size into account. By
/// default, the string length is returned when it is too short to
/// reach the column. Pass `strict` true to make it return -1 in that
/// situation.
fun findColumn(string: String, col: Int, tabSize: Int, strict: Boolean = false): Int {
    var i = 0
    var n = 0
    while (true) {
        if (n >= col) return i
        if (i == string.length) break
        n += if (string[i].code == 9) tabSize-(n % tabSize)  else 1
        i = findClusterBreak(string, i)
    }
    return if (strict == true ) -1 else string.length
}
