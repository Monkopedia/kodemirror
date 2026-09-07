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
import com.monkopedia.kodemirror.lezer.common.TreeFragment
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

/**
 * The indentation [com.monkopedia.kodemirror.lezer.lr.ContextTracker] must keep
 * `strict` at its default of true, as `@lezer/yaml` 1.0.4 does.
 *
 * A non-strict tracker pins the stack's context hash to 0 and stops
 * `Stack.close` writing `NodeProp.contextHash` on to the nodes it builds, so
 * `Parse.useCachedResult` has nothing to compare and reuses a cached node
 * whatever indentation context it was parsed under. Re-indenting a line then
 * lets a subtree parsed at one block depth be spliced back in at another.
 */
class YamlIncrementalReuseTest {

    private val doc = """
AntiFeatures:
    en-US: App depends on Reddit.
Categories:
License: GPL-3.0-only
RepoType: git
Builds:
  - versionName: 1.0.4
    versionCode: 5
    commit: 770905d77eac8bb960502769b591c492c70d6b86
    target: android-15
  - versionName: 1.0.4.1
    versionCode: 6
    commit: 85d946af7a9a
    target: android-15
  - versionName: 1.0.5
    versionCode: 7
    commit: d1.0.5
    target: android-15
  - versionName: 1.0.6
    versionCode: 8
    commit: d1.0.6
    target: android-15
  - versionName: 1.0.7
    versionCode: 9
    commit: d1.0.7
    target: android-15
  - versionName: 1.0.9
    versionCode: 11
    commit: d1.0.9
    target: android-15
  - versionName: 1.1.0
    versionCode: 12
    commit: d1.1.0
    target: android-15
  - versionName: 1.1.1
    versionCode: 13
    commit: d1.1.1
  - versionName: 1.1.2
    versionCode: 14
    commit: d1.1.2
  - versionName: 1.1.3
    versionCode: 15
    commit: d1.1.3
  - versionName: 1.1.4
    versionCode: 16
    commit: d1.1.4
  - versionName: 1.2.0
    versionCode: 17
    commit: d1.2.0
  - versionName: 1.2.1
    versionCode: 18
ArchivePolicy: 0
""".removePrefix("\n")

    /** The edit: indent the trailing top-level `ArchivePolicy` line by four. */
    private val editFrom = doc.lastIndexOf("ArchivePolicy")
    private val edited = doc.substring(0, editFrom) + "    " + doc.substring(editFrom)

    private fun reparse(): String {
        val fragments = TreeFragment.applyChanges(
            TreeFragment.addTree(yamlParser.parse(doc)),
            listOf(ChangedRange(editFrom, doc.length, editFrom, edited.length))
        )
        return treeToString(yamlParser.parse(edited, fragments))
    }

    /**
     * The tree `@lezer/yaml` 1.0.4 produces for [edited], both from scratch and
     * incrementally: five top-level pairs, the second of which (`Categories:`)
     * has no value, and a `Builds` sequence of seven four-field items followed
     * by six three-field ones.
     */
    private val expected: String = run {
        val pair = "Pair(Key(Literal),Literal)"
        val item4 = "Item(BlockMapping($pair,$pair,$pair,$pair))"
        val item3 = "Item(BlockMapping($pair,$pair,$pair))"
        val builds = List(7) { item4 }.joinToString(",") + "," + List(6) { item3 }.joinToString(",")
        "Stream(Document(BlockMapping(" +
            "Pair(Key(Literal),BlockMapping($pair))," +
            "Pair(Key(Literal))," +
            "$pair,$pair," +
            "Pair(Key(Literal),BlockSequence($builds))" +
            ")))"
    }

    @Test
    fun reindentingATailLineDoesNotRestructureTheReusedHead() = assertEquals(expected, reparse())

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
