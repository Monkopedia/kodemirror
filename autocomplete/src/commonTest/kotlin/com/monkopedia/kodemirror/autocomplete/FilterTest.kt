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
package com.monkopedia.kodemirror.autocomplete

import com.monkopedia.kodemirror.state.DocPos
import com.monkopedia.kodemirror.state.EditorState
import com.monkopedia.kodemirror.state.EditorStateConfig
import com.monkopedia.kodemirror.state.SelectionSpec
import com.monkopedia.kodemirror.state.asDoc
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * [FuzzyMatcher] and [sortOptions] against upstream `@codemirror/autocomplete` 6.20.1's
 * on the same inputs (#392). Each expected value is the npm package's output (probe
 * rows `F` and `S1`–`S9` in the #392 PR). The PR also reports a corpus differential: 60,249
 * query/label pairs and 400 generated multi-source sort scenarios.
 */
class FilterTest {

    private val labels = listOf(
        "getBoundingClientRect", "getElementById", "get", "Get", "target", "forEach", "for",
        "format", "function", "Foo", "foo", "fooBar", "foo_bar", "barFoo", "abc", "xabc", "a_b_c",
        "aXbXc", "innerHTML", "innerText", "apple", "Banana", "cherry", "element"
    )

    @Test
    fun theEditorListsWhatUpstreamLists() {
        // Probe rows P: completeFromList over the same labels, through the real plugin.
        for ((doc, expected) in listOf(
            "e" to listOf("element"),
            "fo" to listOf("foo", "foo_bar", "fooBar", "for", "forEach", "format", "Foo", "barFoo"),
            "gbcr" to listOf("getBoundingClientRect"),
            "ac" to listOf("forEach", "a_b_c")
        )) {
            val session = completionSession(
                doc,
                config = CompletionConfig(
                    override = listOf(completeFromList(labels.map(::Completion)))
                )
            )
            session.startImplicit()
            assertEquals(expected, currentCompletions(session.state).map { it.label }, doc)
        }
    }

    /** Each label [query] matches, with its score and ranges, one matcher over all labels as upstream. */
    private fun fuzzy(query: String): List<Triple<String, Int, List<Int>>> {
        val matcher = FuzzyMatcher(query)
        return labels.mapNotNull { label ->
            matcher.match(label)?.let { Triple(label, it.score, it.matched) }
        }
    }

    @Test
    fun singleCharacterQueriesMatchOnlyAtTheStart() {
        // "e" used to match ten labels anywhere in them.
        assertEquals(listOf(Triple("element", -100, listOf(0, 1))), fuzzy("e"))
        assertEquals(
            listOf(
                Triple("getBoundingClientRect", -100, listOf(0, 1)),
                Triple("getElementById", -100, listOf(0, 1)),
                Triple("get", -100, listOf(0, 1)),
                Triple("Get", -300, listOf(0, 1))
            ),
            fuzzy("g")
        )
        assertEquals(listOf(-300, -300, -300, -100), fuzzy("G").map { it.second })
    }

    @Test
    fun prefixesBeatByWordAndGappedMatches() {
        // "fo" no longer offers `function`.
        assertEquals(
            listOf(
                Triple("forEach", -100, listOf(0, 2)),
                Triple("for", -100, listOf(0, 2)),
                Triple("format", -100, listOf(0, 2)),
                Triple("Foo", -303, listOf(0, 2)),
                Triple("foo", -100, listOf(0, 2)),
                Triple("fooBar", -100, listOf(0, 2)),
                Triple("foo_bar", -100, listOf(0, 2)),
                Triple("barFoo", -906, listOf(3, 5))
            ),
            fuzzy("fo")
        )
        assertEquals(
            listOf(
                Triple("abc", 0, listOf(0, 3)),
                Triple("xabc", -704, listOf(1, 4)),
                Triple("a_b_c", -105, listOf(0, 1, 2, 3, 4, 5)),
                Triple("aXbXc", -1305, listOf(0, 1, 2, 3, 4, 5))
            ),
            fuzzy("abc")
        )
        assertEquals(
            listOf(
                Triple("forEach", -707, listOf(4, 6)),
                Triple("a_b_c", -1905, listOf(0, 1, 4, 5))
            ),
            fuzzy("ac")
        )
        assertEquals(listOf(Triple("aXbXc", -905, listOf(3, 5))), fuzzy("xc"))
    }

    @Test
    fun wordStartsMatchAcronyms() {
        val gbcr = listOf(Triple("getBoundingClientRect", -321, listOf(0, 1, 3, 4, 11, 12, 17, 18)))
        assertEquals(gbcr, fuzzy("gbcr"))
        assertEquals(gbcr, fuzzy("GBCR"))
        assertEquals(listOf(Triple("innerHTML", -309, listOf(0, 1, 5, 6))), fuzzy("ih"))
        assertEquals(listOf(Triple("innerHTML", -309, listOf(0, 1, 5, 7))), fuzzy("iht"))
        assertEquals(
            listOf(
                Triple("fooBar", -306, listOf(0, 4)),
                Triple("foo_bar", -1307, listOf(0, 3, 4, 5))
            ),
            fuzzy("foob")
        )
        assertEquals(
            listOf(-203, -203, -306, -307, -906),
            fuzzy("FOO").map { it.second }
        )
        assertEquals(listOf(-100, -704, -105), fuzzy("ab").map { it.second })
    }

    @Test
    fun aMatcherKeepsItsByWordBufferBetweenLabels() {
        // F3: upstream reuses its by-word buffer, so a second, identical label scores
        // worse once an earlier one has written to it.
        val matcher = FuzzyMatcher("bc")
        assertEquals(
            -805 to listOf(2, 3, 4, 5),
            matcher.match("a_b_c")?.let {
                it.score to
                    it.matched
            }
        )
        assertEquals(
            -1905 to listOf(2, 3, 4, 5),
            matcher.match("a_b_c")?.let {
                it.score to
                    it.matched
            }
        )
    }

    @Test
    fun scatteredMatchesArePenalizedUnlessTheyStartTheLabel() {
        // F6
        val matcher = FuzzyMatcher("brc")
        assertEquals(
            listOf(
                -2006 to listOf(2, 4, 5, 6),
                -2005 to listOf(1, 3, 4, 5),
                -1304 to listOf(0, 2, 3, 4)
            ),
            listOf("xabrxc", "abrxc", "brxc").map { l ->
                matcher.match(l)!!.let {
                    it.score to
                        it.matched
                }
            }
        )
    }

    @Test
    fun astralCharactersAreHighlightedWhole() {
        // F7: the emoji's range covers both of its UTF-16 units.
        assertEquals(
            -1307 to listOf(0, 2, 3, 5),
            FuzzyMatcher("😀mi").match("😀smile")?.let {
                it.score to
                    it.matched
            }
        )
    }

    /** Sort [results] at the end of [doc] as one session; each entry is `label[/detail]`, score and ranges. */
    private fun sort(
        doc: String,
        vararg results: CompletionResult
    ): List<Triple<String, Int, List<Int>>> {
        val state = EditorState.create(
            EditorStateConfig(
                doc = doc.asDoc(),
                selection = SelectionSpec.CursorSpec(DocPos(doc.length))
            )
        )
        val active = results.map { r ->
            ActiveResult(SyncSource { r }, false, r.from, r, r.from, r.to ?: DocPos(doc.length))
        }
        return sortOptions(active, state).map { o ->
            val name = o.completion.label + (o.completion.detail?.let { "/$it" } ?: "")
            Triple(name, o.score, o.highlighted.flatMap { listOf(it.first, it.last + 1) })
        }
    }

    private fun result(vararg options: Completion, filter: Boolean = true) =
        CompletionResult(from = DocPos.ZERO, options = options.toList(), filter = filter)

    @Test
    fun onlyOptionsThatAreTheSameInEveryWayAreDeduplicated() {
        // S1: the `for` snippets differ by detail, so all three stay.
        assertEquals(
            listOf("for/loop", "for/of loop", "for"),
            sort(
                "fo",
                result(
                    Completion("for", detail = "loop"),
                    Completion("for", detail = "of loop"),
                    Completion("for", type = "keyword")
                )
            ).map { it.first }
        )
        // S2: a duplicate keeps the copy with more information.
        val rich = Completion("foo", type = "variable", info = "x")
        val state = EditorState.create(
            EditorStateConfig(doc = "fo".asDoc(), selection = SelectionSpec.CursorSpec(DocPos(2)))
        )
        val plain = result(Completion("foo"))
        val withInfo = result(rich)
        val sorted = sortOptions(
            listOf(plain, withInfo).map { r ->
                ActiveResult(SyncSource { r }, false, DocPos.ZERO, r, DocPos.ZERO, DocPos(2))
            },
            state
        )
        assertEquals(listOf(rich), sorted.map { it.completion })
        // S2b: different types aren't duplicates.
        assertEquals(
            2,
            sort(
                "fo",
                result(Completion("foo", type = "function")),
                result(Completion("foo", type = "variable"))
            ).size
        )
    }

    @Test
    fun anUnfilteredResultKeepsItsSourceOrderAhead() {
        // S3
        assertEquals(
            listOf(
                Triple("zeta", 1_000_000_000, emptyList()),
                Triple("alpha", 999_999_999, emptyList()),
                Triple("mid", 999_999_998, emptyList()),
                Triple("x", 0, listOf(0, 1)),
                Triple("xray", -100, listOf(0, 1))
            ),
            sort(
                "x",
                result(Completion("zeta"), Completion("alpha"), Completion("mid"), filter = false),
                result(Completion("xray"), Completion("x"))
            )
        )
    }

    @Test
    fun sectionsSortByRankThenName() {
        // S4: unsectioned first, then ranked sections, then unranked ones by name.
        assertEquals(
            listOf(
                "c1" to -100,
                "b1" to -100_100,
                "a1" to -200_100,
                "e1" to -300_100,
                "d1" to -400_100
            ),
            sort(
                "",
                result(
                    Completion("a1", section = CompletionSection("late", rank = 2)),
                    Completion("b1", section = CompletionSection("early", rank = 1)),
                    Completion("c1"),
                    Completion("d1", section = CompletionSection("zzz")),
                    Completion("e1", section = CompletionSection("aaa"))
                )
            ).map { it.first to it.second }
        )
        // S5: dynamic sections by their best match.
        val one = CompletionSection("one", dynamicRank = true)
        val two = CompletionSection("two", dynamicRank = true)
        assertEquals(
            listOf("gel" to -100_100, "xget" to -100_704, "get" to -200_100),
            sort(
                "ge",
                result(
                    Completion("xget", section = one),
                    Completion("get", section = two),
                    Completion("gel", section = one)
                )
            )
                .map { it.first to it.second }
        )
    }

    @Test
    fun dynamicSectionsSortByTheirBestMatch() {
        val a = CompletionSection("a", dynamicRank = true)
        val b = CompletionSection("b", dynamicRank = true)
        // S4: "b" matches better, so it comes first despite its name.
        assertEquals(
            listOf("getter" to -100_100, "xget" to -200_704),
            sort("ge", result(Completion("xget", section = a), Completion("getter", section = b)))
                .map { it.first to it.second }
        )
        // S3: upstream treats a best score of exactly 0 as unset, so the later, worse
        // match becomes section "a"'s best.
        assertEquals(
            listOf("getter" to -100_100, "get" to -200_000, "xget" to -200_704),
            sort(
                "get",
                result(
                    Completion("get", section = a),
                    Completion("xget", section = a),
                    Completion("getter", section = b)
                )
            )
                .map { it.first to it.second }
        )
    }

    @Test
    fun tiesAreBrokenByLocaleCompareOfSortTextOrLabel() {
        // S6
        assertEquals(
            listOf("_a", "a", "A", "ä", "a_", "a1", "b", "B"),
            sort(
                "",
                result(
                    *listOf(
                        "b",
                        "B",
                        "a",
                        "_a",
                        "A",
                        "ä",
                        "a1",
                        "a_"
                    ).map(::Completion).toTypedArray()
                )
            )
                .map { it.first }
        )
        // S7
        assertEquals(
            listOf("b", "a", "c"),
            sort(
                "",
                result(
                    Completion("b", sortText = "1"),
                    Completion("a", sortText = "2"),
                    Completion("c")
                )
            )
                .map { it.first }
        )
    }

    @Test
    fun boostAddsToTheScoreAndADisplayLabelHasNoRanges() {
        // S8
        assertEquals(
            listOf("getAll" to -98, "get" to -100, "go" to -101),
            sort(
                "g",
                result(
                    Completion("get"),
                    Completion("getAll", boost = 2),
                    Completion("go", boost = -1)
                )
            )
                .map { it.first to it.second }
        )
        // S9
        assertEquals(
            listOf(Triple("gem", -100, listOf(0, 2)), Triple("get", -100, emptyList())),
            sort("ge", result(Completion("get", displayLabel = "get()"), Completion("gem")))
        )
    }
}
