package com.monkopedia.kodemirror.state

import kotlin.test.Test
import kotlin.test.assertEquals

class ClusterBreakTest {
    fun test(spec: String) {
        var spec = spec
        val breaks =mutableListOf<Int>()
        var next: Int = spec.indexOf("|")
        while (next  > -1) {
            breaks.add(next)
            spec = spec.substring(0, next) + spec.substring(next + 1)
            next = spec.indexOf("|")
        }
        val found = mutableListOf<Int>()
        var i = 0
        while (true) {
            val next = findClusterBreak (spec, i)
            if (next >= spec.length) break
            found.add( next)
            i = next
        }
        assertEquals(found.joinToString(","), breaks.joinToString(","))
    }

    @Test
    fun findClusterBreak1() = test("a|b|c|d")
    @Test
    fun findClusterBreak2() = test("a|é̠|ő|x")
    @Test
    fun findClusterBreak3() = test("😎|🙉")
    @Test
    fun findClusterBreak4() = test("👨‍🎤|💪🏽|👩‍👩‍👧‍👦|❤")
    @Test
    fun findClusterBreak5() = test("🇩🇪|🇫🇷|🇪🇸|x|🇮🇹")

    @Test
    fun countColumn_counts_characters() {
        assertEquals(countColumn("abc", 4), 3)
    }

    @Test
    fun countColumn_counts_tabs_correctly() {
        assertEquals( countColumn("a\t\tbc\tx", 4) , 13)
    }

    @Test
    fun countColumn_handles_clusters() {
        assertEquals(countColumn("a😎🇫🇷", 4), 3)
    }

    @Test
    fun findColumn_finds_positions() {
        assertEquals( findColumn("abc", 3, 4),3)
    }

    @Test
    fun findColumn_counts_tabs() {
        assertEquals(findColumn("a\tbc", 4, 4), 2)
    }

    @Test
    fun findColumn_handles_clusters() {
        assertEquals(findColumn("a😎🇫🇷bc", 4, 4), 8)
    }
}

