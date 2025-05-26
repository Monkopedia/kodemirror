package com.monkopedia.kodemirror.util


infix fun Int.check(mask: Int): Boolean {
    return (this and mask) != 0
}
infix fun Int.checkAll(mask: Int): Boolean {
    return (this and mask) != mask
}
