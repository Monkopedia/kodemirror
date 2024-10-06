package com.monkopedia.kodemirror.state

import kotlin.math.min
import kotlin.random.Random
import kotlin.test.*


fun depth(node: Text): Int {
    return 1 + (node.children?.maxOf { depth(it) } ?: 0)
}

val line = "1234567890".repeat(10)
val lines = List(200) { line }
val text0 = lines.joinToString("\n")
val doc0 = Text.of(lines)

class TextTest {


    @Test
    fun can_create_basic() {
        assertEquals(
            "one\ntwo\nthree",
            Text.of(listOf("one", "two", "three")).toString()
        )
    }

    @Test
    fun handles_basic_replacement() {
        val doc = Text.of(listOf("one", "two", "three"))
        assertEquals(
            "onfoo\nbarwo\nthree",
            doc.replace(2, 5, Text.of(listOf("foo", "bar"))).toString()
        )
    }

    @Test
    fun can_append_documents() {
        assertEquals(
            "one\ntwo\nthree!\nok",
            Text.of(listOf("one", "two", "three")).append(Text.of(listOf("!", "ok"))).toString()
        )
    }

    @Test
    fun preserves_length() {
        assertEquals(text0.length, doc0.length)
    }

    @Test
    fun creates_a_balanced_tree_when_loading_a_document() {
        val doc = Text.of(List(2000) { line })
        val d = depth(doc)
        assertEquals(2, d, "<=")
    }

    @Test
    fun rebalances_on_insert() {
        var doc = doc0
        val insert = "abc".repeat(200)
        val at = (doc.length / 2)
        for (i in 0 until 10) {
            doc = doc.replace(at, at, Text.of(listOf(insert)))
        }
        assertEquals(2, depth(doc), "<=")
        assertEquals(
            text0.substring(0, at) + "abc".repeat(2000) + text0.substring(at),
            doc.toString()
        )
    }

    @Test
    fun collapses_on_delete() {
        val doc = doc0.replace(10, text0.length - 10, Text.empty)
        assertEquals(1, depth(doc))
        assertEquals(20, doc.length)
        assertEquals(line.substring(0, 20), doc.toString())
    }

    @Test
    fun handles_deleting_at_start() {
        assertEquals(
            text0.substring(9500) + "!",
            Text.of(lines.subList(0, lines.size - 1) + listOf("$line!"))
                .replace(0, 9500, Text.empty)
                .toString()
        )
    }

    @Test
    fun handles_deleting_at_end() {
        assertEquals(
            "?" + text0.substring(0, 9499),
            Text.of(listOf("?$line") + lines.subList(1, lines.size))
                .replace(9500, text0.length + 1, Text.empty).toString()
        )
    }

    @Test
    fun can_handle_deleting_the_entire_document() {
        assertEquals("", doc0.replace(0, doc0.length, Text.empty).toString())
    }

    @Test
    fun can_insert_on_node_boundaries() {
        val doc = doc0
        val pos = doc.children!![0].length
        assertEquals(
            "abc",
            doc.replace(pos, pos, Text.of(listOf("abc"))).slice(pos, pos + 3).toString()
        )
    }

    @Test
    fun can_build_up_a_doc_by_repeated_appending() {
        var doc = Text.of(listOf(""))
        var text = ""
        for (i in 1 until 1000) {
            val add = "newtext$i "
            doc = doc.replace(doc.length, doc.length, Text.of(listOf(add)))
            text += add
        }
        assertEquals(text, doc.toString())
    }

    @Test
    fun properly_maintains_content_during_editing() {
        var str = text0
        var doc = doc0
        for (i in 0 until 200) {
            val insPos = (Random.nextDouble() * doc.length).toInt()
            val insChar = ('A'.code + (Random.nextDouble() * 26).toInt()).toChar()
            str = str.substring(0, insPos) + insChar + str.substring(insPos)
            doc = doc.replace(insPos, insPos, Text.of(listOf(insChar.toString())))
            val delFrom = (Random.nextDouble() * doc.length).toInt()
            val delTo = min(doc.length, delFrom + (Random.nextDouble() * 20).toInt())
            str = str.substring(0, delFrom) + str.substring(delTo)
            doc = doc.replace(delFrom, delTo, Text.empty)
        }
        assertEquals(str, doc.toString())
    }

    @Test
    fun returns_the_correct_strings_for_slice() {
        val text = mutableListOf<String>()
        for (i in 0 until 1000) {
            text.add("0".repeat(4 - i.toString().length) + i)
        }
        val doc = Text.of(text)
        val str = text.joinToString("\n")
        for (i in 0 until 400) {
            var start = if (i == 0) 0 else (Random.nextDouble() * doc.length).toInt()
            var end =
                if (i == 399) doc.length else start + (Random.nextDouble() * (doc.length - start)).toInt()
//            start = 4150
//            end = 4160
            assertEquals(str.substring(start, end), doc.slice(start, end).toString())
        }
    }

    @Test
    fun can_be_compared() {
        val doc = doc0
        val doc2 = Text.of(lines)
        assertTrue(doc.eq(doc))
        assertTrue(doc.eq(doc2))
        assertTrue(doc2.eq(doc))
        assertFalse(doc.eq(doc2.replace(5000, 5000, Text.of(listOf("y")))))
        assertFalse(doc.eq(doc2.replace(5000, 5001, Text.of(listOf("y")))))
        assertTrue(doc.eq(doc.replace(5000, 5001, doc.slice(5000, 5001))))
        assertFalse(doc.eq(doc.replace(5000, 5001, Text.of(listOf("y")))))
    }

    @Test
    fun can_be_compared_despite_different_tree_shape() {
        assertTrue(
            doc0.replace(100, 201, Text.of(listOf("abc")))
                .eq(Text.of(listOf(line + "abc") + lines.subList(2, lines.size)))
        )
    }

    @Test
    fun can_compare_small_documents() {
        assertTrue(Text.of(listOf("foo", "bar")).eq(Text.of(listOf("foo", "bar"))))
        assertFalse(Text.of(listOf("foo", "bar")).eq(Text.of(listOf("foo", "baz"))))
    }

    @Test
    fun is_iterable() {
        val iter = doc0.iter()
        var build = ""
        while (true) {
            val value = iter.next()
            val lineBreak = iter.lineBreak
            val done = iter.done
            if (done) {
                assertEquals(text0, build)
                break
            }
            if (lineBreak) {
                build += "\n"
            } else {
                assertEquals(-1, value.indexOf("\n"))
                build += value
            }
        }
    }

    @Test
    fun is_iterable_in_reverse() {
        var found = ""
        val iter = doc0.iter(dir = false)
        while (!iter.also { it.next() }.done) {
            found = iter.value + found
        }
        assertEquals(text0, found)
    }

    @Test
    fun allows_negative_skip_values_in_iteration() {
        val iter = Text.of(listOf("one", "two", "three", "four")).iter()
        assertEquals("e", iter.next(12).value)
        assertEquals("ne", iter.next(-12).value)
        assertEquals("our", iter.next(12).value)
        assertEquals("one", iter.next(-1000).value)
    }

    @Test
    fun is_partially_iterable() {
        var found = ""
        val iter = doc0.iterRange(500, doc0.length - 500)
        while (!iter.also { it.next() }.done) {
            found += iter.value
        }
        assertEquals(text0.substring(500, text0.length - 500).toString(), found.toString())
    }

    @Test
    fun is_partially_iterable_in_reverse() {
        var found = ""
        val iter = doc0.iterRange(doc0.length - 500, 500)
        while (!iter.also { it.next() }.done) {
            found = iter.value + found
        }
        assertEquals(text0.substring(500, text0.length - 500).toString(), found)
    }

    @Test
    fun can_partially_iter_over_subsections_at_the_start_and_end() {
        assertEquals("1", doc0.iterRange(0, 1).also { it.next() }.value)
        assertEquals("2", doc0.iterRange(1, 2).also { it.next() }.value)
        assertEquals("0", doc0.iterRange(doc0.length - 1, doc0.length).also { it.next() }.value)
        assertEquals("9", doc0.iterRange(doc0.length - 2, doc0.length - 1).also { it.next() }.value)
    }

    @Test
    fun can_iterate_over_lines() {
        val doc = Text.of(listOf("ab", "cde", "", "", "f", "", "g"))

        fun get(from: Int? = null, to: Int? = null): String {
            val result = mutableListOf<Any>()
            val i = doc.iterLines(from, to)
            while (!i.also { it.next() }.done) {
                result.add(i.value)
            }
            return result.joinToString("\n")
        }
        assertEquals("ab\ncde\n\n\nf\n\ng", get())
        assertEquals("ab\ncde\n\n\nf\n\ng", get(1, doc.lines + 1))
        assertEquals("cde", get(2, 3))
        assertEquals("cde", get(2, 3))
        assertEquals("ab\ncde\n\n", get(1, 5))
        assertEquals("", get(2, 1))
        assertEquals("\n\nf\n\ng", get(3))
    }

//    @Test fun can_convert_to_JSON() {
//        for (let i = 0; i < 200; i++) lines.push("line "+i)
//        val text = Text.of(lines)
//        assertEquals(Text.of(text.toJSON()).eq(text))
//    }

    @Test
    fun can_get_line_info_by_line_number() {
        assertFails("Invalid line") {
            doc0.line(0)
        }
        assertFails("Invalid line") {
            doc0.line(doc0.lines + 1)
        }
        for (i in 1 until doc0.lines step 5) {
            val l = doc0.line(i)
            assertEquals((i - 1) * 101, l.from)
            assertEquals(i * 101 - 1, l.to)
            assertEquals(i, l.number)
            assertEquals(line, l.text)
        }
    }

    @Test
    fun can_get_line_info_by_position() {
        assertFails("Invalid position") {
            doc0.lineAt(-10)
        }
        assertFails("Invalid position") {
            doc0.lineAt(doc0.length + 1)
        }
        for (i in 0 until doc0.length step 5) {
            val l = doc0.lineAt(i)
            assertEquals(i - (i % 101), l.from)
            assertEquals(i - (i % 101) + 100, l.to)
            assertEquals((i / 101.0).toInt() + 1, l.number)
            assertEquals(line, l.text)
        }
    }

    @Test
    fun can_delete_a_range_at_the_start_of_a_child_node() {
        assertEquals(
            "x" + text0.substring(100),
            doc0.replace(0, 100, Text.of(listOf("x"))).toString()
        )
    }

    @Test
    fun can_retrieve_pieces_of_text() {
        for (i in 0 until 500) {
            val from = (Random.nextDouble() * (doc0.length - 1)).toInt()
            val to =
                if (Random.nextDouble() < .5) from + 2
                else from + (Random.nextDouble() * (doc0.length - 1 - from)).toInt() + 1
            assertEquals(text0.substring(from, to), doc0.sliceString(from, to))
            assertEquals(text0.substring(from, to), doc0.slice(from, to).toString())
        }
    }

    @Test
    fun clips_out_of_range_boundaries() {
        assertEquals(0, doc0.slice(0, -10).length)
        assertEquals(0, Text.empty.slice(0, 10).length)
        assertEquals(0, Text.empty.slice(1000, 1100).length)
        assertEquals(0, doc0.slice(5, 0).length)
        assertEquals(0, doc0.slice(-5, 0).length)
    }
}
