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
package com.monkopedia.kodemirror.lang.yaml

import com.monkopedia.kodemirror.lezer.common.ChangedRange
import com.monkopedia.kodemirror.lezer.common.DEFAULT_BUFFER_LENGTH
import com.monkopedia.kodemirror.lezer.common.Tree
import com.monkopedia.kodemirror.lezer.common.TreeFragment
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/**
 * The indentation [com.monkopedia.kodemirror.lezer.lr.ContextTracker] must keep
 * `strict` at its default of true, as `@lezer/yaml` 1.0.4 does.
 *
 * A non-strict tracker pins the stack's context hash to 0 and stops
 * `Stack.close` writing `NodeProp.contextHash` on to the nodes it builds, so
 * `Parse.useCachedResult` has nothing to compare and reuses a cached node
 * whatever indentation context it was parsed under. Re-indenting a line then
 * lets a subtree parsed at one block depth be spliced back in at another.
 *
 * The document has to be longer than `bufferLength * 4`: below that,
 * `@lezer/lr` 1.4.10 builds no fragment cursor at all, the incremental parse
 * is a fresh parse, and this test would pass whatever the tracker did.
 * [fixtureIsLongEnoughForFragmentReuse] and [incrementalParseReusesNodes]
 * guard that precondition.
 */
class YamlIncrementalReuseTest {

    private val fourFieldItems = 30
    private val threeFieldItems = 30

    private val doc = buildString {
        append("AntiFeatures:\n    en-US: App depends on Reddit.\n")
        append("Categories:\nLicense: GPL-3.0-only\nRepoType: git\nBuilds:\n")
        for (i in 0 until fourFieldItems + threeFieldItems) {
            append("  - versionName: 1.$i\n    versionCode: $i\n    commit: d1.$i\n")
            if (i < fourFieldItems) append("    target: android-15\n")
        }
        append("  - versionName: 1.2.1\n    versionCode: 18\nArchivePolicy: 0\n")
    }

    /** The edit: indent the trailing top-level `ArchivePolicy` line by four. */
    private val editFrom = doc.lastIndexOf("ArchivePolicy")
    private val edited = doc.substring(0, editFrom) + "    " + doc.substring(editFrom)

    private val original = yamlParser.parse(doc)

    private fun reparse(): Tree {
        val fragments = TreeFragment.applyChanges(
            TreeFragment.addTree(original),
            listOf(ChangedRange(editFrom, doc.length, editFrom, edited.length))
        )
        return yamlParser.parse(edited, fragments)
    }

    private fun subtrees(tree: Tree): List<Tree> =
        tree.children.filterIsInstance<Tree>().flatMap { listOf(it) + subtrees(it) }

    /**
     * The tree `@lezer/yaml` 1.0.4 produces for [edited], both from scratch and
     * incrementally: five top-level pairs, the second of which (`Categories:`)
     * has no value, and a `Builds` sequence of thirty four-field items followed
     * by thirty-one three-field ones (the last of which picks up the
     * re-indented `ArchivePolicy`).
     */
    private val expected: String = run {
        val pair = "Pair(Key(Literal),Literal)"
        val item4 = "Item(BlockMapping($pair,$pair,$pair,$pair))"
        val item3 = "Item(BlockMapping($pair,$pair,$pair))"
        val builds = (List(fourFieldItems) { item4 } + List(threeFieldItems + 1) { item3 })
            .joinToString(",")
        "Stream(Document(BlockMapping(" +
            "Pair(Key(Literal),BlockMapping($pair))," +
            "Pair(Key(Literal))," +
            "$pair,$pair," +
            "Pair(Key(Literal),BlockSequence($builds))" +
            ")))"
    }

    @Test
    fun fixtureIsLongEnoughForFragmentReuse() = assertTrue(
        edited.length > DEFAULT_BUFFER_LENGTH * 4,
        "edited document is ${edited.length} chars; reuse needs more than ${DEFAULT_BUFFER_LENGTH * 4}"
    )

    /**
     * Precondition for the test below: the incremental parse really splices in
     * subtrees of [original]. `@lezer/lr` 1.4.10 reuses two on this edit.
     */
    @Test
    fun incrementalParseReusesNodes() {
        val old = subtrees(original).toSet()
        val reused = subtrees(reparse()).count { it in old }
        assertTrue(reused > 0, "incremental parse reused no nodes from the original tree")
    }

    @Test
    fun reindentingATailLineDoesNotRestructureTheReusedHead() =
        assertEquals(expected, treeToString(reparse()))

    /**
     * Control: the same document parsed from scratch, with no fragments in
     * play. This holds either side of the tracker change, so the failure above
     * comes from fragment reuse and not from the grammar or the expectation.
     */
    @Test
    fun freshParseOfTheEditedDocumentMatchesUpstream() =
        assertEquals(expected, treeToString(yamlParser.parse(edited)))

    /**
     * Control: the edit really does restructure the document, so the two trees
     * above are not trivially the same one.
     */
    @Test
    fun theEditChangesTheTree() = assertNotEquals(expected, treeToString(yamlParser.parse(doc)))
}
