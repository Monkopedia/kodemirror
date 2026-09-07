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
package com.monkopedia.kodemirror.lezer.common

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Resolution behaviour around overlay-mounted trees, matching `resolveNode` in
 * `@lezer/common` `test-mix.ts` / `test-tree.ts` semantics.
 *
 * The fixture models a mixed-language document (a host language with an inner
 * language mounted over two non-contiguous ranges), which is the exact shape
 * the `overlays` flag exists for. The mount deliberately does **not** start at
 * offset 0, so a result reported in subtree-local coordinates is
 * distinguishable from one in document coordinates.
 *
 * ```
 * Doc(0-30)
 *   Text(0-4)
 *   Script(4-24)                 mounted overlay over doc 10-14 and 18-22
 *     Attr(4-10)
 *     Attr(10-14)                covered by overlay range 1
 *     Attr(14-18)                the GAP between the two overlay ranges
 *     Attr(18-22)                covered by overlay range 2
 *     Attr(22-24)
 *   Text(24-30)
 *
 * mounted inner tree, rooted at doc 10:
 *   Expr(10-22)
 *     Ident(10-14)
 *     Ident(18-22)
 * ```
 */
class ResolveOverlayTest {

    private val doc = NodeType.define(NodeTypeSpec(name = "Doc", id = 0))
    private val text = NodeType.define(NodeTypeSpec(name = "Text", id = 1))
    private val script = NodeType.define(NodeTypeSpec(name = "Script", id = 2))
    private val attr = NodeType.define(NodeTypeSpec(name = "Attr", id = 3))
    private val expr = NodeType.define(NodeTypeSpec(name = "Expr", id = 4))
    private val ident = NodeType.define(NodeTypeSpec(name = "Ident", id = 5))

    private val dummyParser = object : Parser() {
        override fun createParse(
            input: Input,
            fragments: List<TreeFragment>,
            ranges: List<TextRange>
        ): PartialParse = error("Not implemented")
    }

    private fun leaf(type: NodeType, length: Int) = Tree(type, emptyList(), emptyList(), length)

    /** The inner (mounted) tree; its own coordinate space starts at doc 10. */
    private fun innerTree(): Tree = Tree(
        expr,
        listOf(leaf(ident, 4), leaf(ident, 4)),
        listOf(0, 8),
        12
    )

    private fun mixedTree(): Tree {
        val mounted = MountedTree(
            innerTree(),
            // Relative to the Script node, which starts at doc 4:
            // doc 10-14 and doc 18-22.
            listOf(TextRange(6, 10), TextRange(14, 18)),
            dummyParser
        )
        val scriptTree = Tree(
            script,
            listOf(
                leaf(attr, 6),
                leaf(attr, 4),
                leaf(attr, 4),
                leaf(attr, 4),
                leaf(attr, 2)
            ),
            listOf(0, 6, 10, 14, 18),
            20,
            mapOf(NodeProp.mounted.id to mounted)
        )
        return Tree(
            doc,
            listOf(leaf(text, 4), scriptTree, leaf(text, 6)),
            listOf(0, 4, 24),
            30
        )
    }

    private fun SyntaxNode.describe() = "$name($from-$to)"

    // ---- Defect 1/2: resolve() must not descend into overlay-mounted trees ----

    @Test
    fun treeResolveDoesNotEnterOverlay() {
        val resolved = mixedTree().resolve(12, 1)
        assertEquals(
            "Attr(10-14)",
            resolved.describe(),
            "Tree.resolve must stay in the host language (IterMode.IGNORE_OVERLAYS)"
        )
    }

    @Test
    fun nodeResolveDoesNotEnterOverlay() {
        val scriptNode = mixedTree().topNode.firstChild!!.nextSibling!!
        assertEquals("Script(4-24)", scriptNode.describe())
        assertEquals(
            "Attr(18-22)",
            scriptNode.resolve(20, 1).describe(),
            "SyntaxNode.resolve must stay in the host language"
        )
    }

    /** resolveInner is the one that *does* enter overlays; guard that too. */
    @Test
    fun treeResolveInnerEntersOverlay() {
        assertEquals("Ident(10-14)", mixedTree().resolveInner(12, 1).describe())
        assertEquals("Ident(18-22)", mixedTree().resolveInner(20, 1).describe())
    }

    // ---- Defect: resolveNode must start from the node, climbing up ----

    @Test
    fun nodeResolveClimbsToContainingNode() {
        val firstText = mixedTree().topNode.firstChild!!
        assertEquals("Text(0-4)", firstText.describe())
        assertEquals(
            "Attr(14-18)",
            firstText.resolve(16, 1).describe(),
            "SyntaxNode.resolve must climb out of the node when pos lies outside it"
        )
    }

    // ---- Defect 4: SyntaxNode.resolveInner uses document coordinates ----

    @Test
    fun nodeResolveInnerUsesDocumentCoordinates() {
        val scriptNode = mixedTree().topNode.firstChild!!.nextSibling!!
        assertEquals("Script(4-24)", scriptNode.describe())
        assertEquals(
            "Ident(18-22)",
            scriptNode.resolveInner(20, 1).describe(),
            "SyntaxNode.resolveInner must treat pos as an absolute document offset, " +
                "not an offset into the node's own subtree"
        )
    }

    @Test
    fun innerNodeResolveInnerUsesDocumentCoordinates() {
        val firstIdent = mixedTree().resolveInner(12, 1)
        assertEquals("Ident(10-14)", firstIdent.describe())
        assertEquals(
            "Ident(18-22)",
            firstIdent.resolveInner(20, 1).describe(),
            "resolveInner from inside the mount must still use document coordinates"
        )
    }

    // ---- Defect 3: resolveInner must climb back out of non-covering overlays ----

    @Test
    fun resolveInnerClimbsOutOfOverlayForGapPosition() {
        val firstIdent = mixedTree().resolveInner(12, 1)
        assertEquals("Ident(10-14)", firstIdent.describe())
        // doc 16 falls in the gap between the two overlay ranges, so it is not
        // covered by the mount at all: the answer must be the host node.
        assertEquals(
            "Attr(14-18)",
            firstIdent.resolveInner(16, 1).describe(),
            "resolveInner must climb out of an overlay that does not cover pos"
        )
    }

    @Test
    fun resolveInnerClimbsOutOfOverlayForPositionAfterMount() {
        val firstIdent = mixedTree().resolveInner(12, 1)
        assertEquals(
            "Attr(22-24)",
            firstIdent.resolveInner(23, 1).describe(),
            "resolveInner must climb out of the mount entirely for a position past it"
        )
    }

    /** The parent chain of an inner-language node runs through the host tree. */
    @Test
    fun overlayParentChainReachesHostTree() {
        val innermost = mixedTree().resolveInner(20, 1)
        val chain = generateSequence(innermost) { it.parent }.map { it.describe() }.toList()
        assertEquals(
            listOf("Ident(18-22)", "Expr(10-22)", "Script(4-24)", "Doc(0-30)"),
            chain
        )
    }
}
