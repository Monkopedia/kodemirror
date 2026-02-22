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
package com.monkopedia.kodemirror.state

import kotlin.math.floor
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertSame

class EditorStateTest {

    @Test
    fun holdsDocAndSelectionProperties() {
        val state = EditorState.create(EditorStateConfig(doc = "hello"))
        assertEquals("hello", state.doc.toString())
        assertEquals(0, state.selection.main.from)
    }

    @Test
    fun canApplyChanges() {
        val state = EditorState.create(EditorStateConfig(doc = "hello"))
        val transaction = state.update(
            TransactionSpec(
                changes = ChangeSpec.Multi(
                    listOf(
                        ChangeSpec.Single(
                            from = 2,
                            to = 4,
                            insert = InsertContent.StringContent("w")
                        ),
                        ChangeSpec.Single(from = 5, insert = InsertContent.StringContent("!"))
                    )
                )
            )
        )
        assertEquals("hewo!", transaction.state.doc.toString())
    }

    @Test
    fun mapsSelectionThroughChanges() {
        val state = EditorState.create(
            EditorStateConfig(
                doc = "abcdefgh",
                extensions = ExtensionList(listOf(EditorState.allowMultipleSelections.of(true))),
                selection = EditorSelection.create(
                    listOf(0, 4, 8).map { n -> EditorSelection.cursor(n) }
                )
            )
        )
        val newState = state.update(state.replaceSelection("Q")).state
        assertEquals("QabcdQefghQ", newState.doc.toString())
        assertEquals(
            "1/6/11",
            newState.selection.ranges.map { r -> r.from }.joinToString("/")
        )
    }

    private val someAnnotation = Annotation.define<Int>()

    @Test
    fun canStoreAnnotationsOnTransactions() {
        val tr = EditorState.create(EditorStateConfig(doc = "foo")).update(
            TransactionSpec(annotations = listOf(someAnnotation.of(55)))
        )
        assertEquals(55, tr.annotation(someAnnotation))
    }

    @Test
    fun throwsWhenAChangeBoundsAreInvalid() {
        val state = EditorState.create(EditorStateConfig(doc = "1234"))
        assertFailsWith<Exception> {
            state.update(TransactionSpec(changes = ChangeSpec.Single(from = -1, to = 1)))
        }
        assertFailsWith<Exception> {
            state.update(TransactionSpec(changes = ChangeSpec.Single(from = 2, to = 1)))
        }
        assertFailsWith<Exception> {
            state.update(
                TransactionSpec(
                    changes = ChangeSpec.Single(
                        from = 2,
                        to = 10,
                        insert = InsertContent.StringContent("x")
                    )
                )
            )
        }
    }

    @Test
    fun storesAndUpdatesTabSize() {
        val deflt = EditorState.create(EditorStateConfig())
        val two = EditorState.create(
            EditorStateConfig(extensions = ExtensionList(listOf(EditorState.tabSize.of(2))))
        )
        assertEquals(4, deflt.tabSize)
        assertEquals(2, two.tabSize)
        val updated = deflt.update(
            TransactionSpec(
                effects = listOf(StateEffect.reconfigure.of(EditorState.tabSize.of(8)))
            )
        ).state
        assertEquals(8, updated.tabSize)
    }

    @Test
    fun storesAndUpdatesTheLineSeparator() {
        val deflt = EditorState.create(EditorStateConfig())
        val crlf = EditorState.create(
            EditorStateConfig(
                extensions = ExtensionList(listOf(EditorState.lineSeparator.of("\r\n")))
            )
        )
        assertNull(deflt.facet(EditorState.lineSeparator))
        assertEquals(2, deflt.toText("a\nb").lines)
        assertEquals("\r\n", crlf.facet(EditorState.lineSeparator))
        assertEquals(1, crlf.toText("a\nb").lines)
        val updated = crlf.update(
            TransactionSpec(
                effects = listOf(StateEffect.reconfigure.of(EditorState.lineSeparator.of("\n")))
            )
        ).state
        assertEquals("\n", updated.facet(EditorState.lineSeparator))
    }

    @Test
    fun storesAndUpdatesFields() {
        val field1 = StateField.define(
            StateFieldSpec<Int>(
                create = { 0 },
                update = { v, _ -> v + 1 }
            )
        )
        val field2 = StateField.define(
            StateFieldSpec<Int>(
                create = { state -> state.field(field1) + 10 },
                update = { v, _ -> v }
            )
        )
        val state = EditorState.create(
            EditorStateConfig(extensions = ExtensionList(listOf(field1, field2)))
        )
        assertEquals(0, state.field(field1))
        assertEquals(10, state.field(field2))
        val newState = state.update(TransactionSpec()).state
        assertEquals(1, newState.field(field1))
        assertEquals(10, newState.field(field2))
    }

    @Test
    fun allowsFieldsToHaveAnInitializer() {
        val field = StateField.define(
            StateFieldSpec<Int>(
                create = { 0 },
                update = { v, _ -> v + 1 }
            )
        )
        val state = EditorState.create(
            EditorStateConfig(extensions = field.init { 10 })
        )
        assertEquals(10, state.field(field))
        assertEquals(11, state.update(TransactionSpec()).state.field(field))
    }

    @Test
    fun canBeSerializedToJSON() {
        data class Wrapper(val n: Int)

        val field = StateField.define(
            StateFieldSpec<Wrapper>(
                create = { Wrapper(0) },
                update = { v, _ -> Wrapper(v.n + 1) },
                toJSON = { v, _ -> mapOf("number" to v.n) },
                fromJSON = { j, _ ->
                    @Suppress("UNCHECKED_CAST")
                    val map = j as Map<String, Any?>
                    Wrapper((map["number"] as Number).toInt())
                }
            )
        )
        val fields = mapOf("f" to field)
        val state = EditorState.create(
            EditorStateConfig(extensions = field)
        ).update(TransactionSpec()).state
        val json = state.toJSON(fields)

        @Suppress("UNCHECKED_CAST")
        val fJson = json["f"] as Map<String, Any?>
        assertEquals(1, fJson["number"])
        val state2 = EditorState.fromJSON(json, EditorStateConfig(), fields)
        assertEquals(1, (state2.field(field)).n)
    }

    @Test
    fun canPreserveFieldsAcrossReconfiguration() {
        val field = StateField.define(
            StateFieldSpec<Int>(
                create = { 0 },
                update = { v, _ -> v + 1 }
            )
        )
        val start = EditorState.create(
            EditorStateConfig(extensions = ExtensionList(listOf(field)))
        ).update(TransactionSpec()).state
        assertEquals(1, start.field(field))
        assertEquals(
            2,
            start.update(
                TransactionSpec(effects = listOf(StateEffect.reconfigure.of(field)))
            ).state.field(field)
        )
        assertNull(
            start.update(
                TransactionSpec(
                    effects = listOf(StateEffect.reconfigure.of(ExtensionList(emptyList())))
                )
            ).state.field(field, false)
        )
    }

    @Test
    fun canReplaceExtensionGroups() {
        val comp = Compartment()
        val f = Facet.define<Int, List<Int>>()
        val content = f.of(10)
        val state = EditorState.create(
            EditorStateConfig(
                extensions = ExtensionList(listOf(comp.of(content), f.of(20)))
            )
        )
        assertSame(content, comp.get(state))
        assertEquals("10,20", state.facet(f).joinToString(","))
        val content2 = ExtensionList(listOf(f.of(1), f.of(2)))
        val state2 = state.update(
            TransactionSpec(effects = listOf(comp.reconfigure(content2)))
        ).state
        assertSame(content2, comp.get(state2))
        assertEquals("1,2,20", state2.facet(f).joinToString(","))
        val state3 = state2.update(
            TransactionSpec(effects = listOf(comp.reconfigure(f.of(3))))
        ).state
        assertEquals("3,20", state3.facet(f).joinToString(","))
    }

    @Test
    fun raisesAnErrorOnDuplicateExtensionGroups() {
        val comp = Compartment()
        val f = Facet.define<Int, List<Int>>()
        assertFailsWith<Exception> {
            EditorState.create(
                EditorStateConfig(
                    extensions = ExtensionList(listOf(comp.of(f.of(1)), comp.of(f.of(2))))
                )
            )
        }
        assertFailsWith<Exception> {
            EditorState.create(
                EditorStateConfig(extensions = comp.of(comp.of(f.of(1))))
            )
        }
    }

    @Test
    fun preservesCompartmentsOnReconfigure() {
        val comp = Compartment()
        val f = Facet.define<Int, List<Int>>()
        val init = comp.of(f.of(10))
        var state = EditorState.create(
            EditorStateConfig(extensions = ExtensionList(listOf(init, f.of(20))))
        )
        state = state.update(
            TransactionSpec(effects = listOf(comp.reconfigure(f.of(0))))
        ).state
        assertEquals("0,20", state.facet(f).joinToString(","))
        state = state.update(
            TransactionSpec(
                effects = listOf(
                    StateEffect.reconfigure.of(ExtensionList(listOf(init, f.of(2))))
                )
            )
        ).state
        assertEquals("0,2", state.facet(f).joinToString(","))
    }

    @Test
    fun forgetsDroppedCompartments() {
        val comp = Compartment()
        val f = Facet.define<Int, List<Int>>()
        val init = comp.of(f.of(10))
        var state = EditorState.create(
            EditorStateConfig(extensions = ExtensionList(listOf(init, f.of(20))))
        )
        state = state.update(
            TransactionSpec(effects = listOf(comp.reconfigure(f.of(0))))
        ).state
        assertEquals("0,20", state.facet(f).joinToString(","))
        state = state.update(
            TransactionSpec(effects = listOf(StateEffect.reconfigure.of(f.of(2))))
        ).state
        assertEquals("2", state.facet(f).joinToString(","))
        assertNull(comp.get(state))
        state = state.update(
            TransactionSpec(
                effects = listOf(
                    StateEffect.reconfigure.of(ExtensionList(listOf(init, f.of(2))))
                )
            )
        ).state
        assertEquals("10,2", state.facet(f).joinToString(","))
    }

    @Test
    fun allowsFacetsComputedFromFields() {
        val field = StateField.define(
            StateFieldSpec<List<Int>>(
                create = { listOf(0) },
                update = { v, tr -> if (tr.docChanged) listOf(tr.state.doc.length) else v }
            )
        )
        val facet = Facet.define<Int, List<Int>>()
        val state = EditorState.create(
            EditorStateConfig(
                extensions = ExtensionList(
                    listOf(
                        field,
                        facet.compute(listOf(Slot.FieldSlot(field))) { s -> s.field(field)[0] },
                        facet.of(1)
                    )
                )
            )
        )
        assertEquals("0,1", state.facet(facet).joinToString(","))
        val state2 = state.update(TransactionSpec()).state
        assertSame(state2.facet(facet), state.facet(facet))
        val state3 = state.update(
            TransactionSpec(
                changes = ChangeSpec.Single(from = 0, insert = InsertContent.StringContent("hi"))
            )
        ).state
        assertEquals("2,1", state3.facet(facet).joinToString(","))
    }

    @Test
    fun blocksMultipleSelectionsWhenNotAllowed() {
        val cursors = EditorSelection.create(
            listOf(EditorSelection.cursor(0), EditorSelection.cursor(1))
        )
        val state = EditorState.create(
            EditorStateConfig(
                selection = cursors,
                doc = "123"
            )
        )
        assertEquals(1, state.selection.ranges.size)
        assertEquals(
            1,
            state.update(
                TransactionSpec(
                    selection = SelectionSpec.EditorSelectionSpec(cursors)
                )
            ).state.selection.ranges.size
        )
    }

    // changeByRange tests

    @Test
    fun changeByRangeCanMakeSimpleChanges() {
        var state = EditorState.create(EditorStateConfig(doc = "hi"))
        state = state.update(
            state.changeByRange { r ->
                ChangeByRangeResult(
                    changes = ChangeSpec.Single(
                        from = r.from,
                        to = r.from + 1,
                        insert = InsertContent.StringContent("q")
                    ),
                    range = EditorSelection.cursor(r.from + 1)
                )
            }
        ).state
        assertEquals("qi", state.doc.toString())
        assertEquals(1, state.selection.main.from)
    }

    @Test
    fun changeByRangeDoesTheRightThingWithMultipleSelections() {
        var state = EditorState.create(
            EditorStateConfig(
                doc = "1 2 3 4",
                selection = EditorSelection.create(
                    listOf(
                        EditorSelection.range(0, 1),
                        EditorSelection.range(2, 3),
                        EditorSelection.range(4, 5),
                        EditorSelection.range(6, 7)
                    )
                ),
                extensions = EditorState.allowMultipleSelections.of(true)
            )
        )
        state = state.update(
            state.changeByRange { r ->
                ChangeByRangeResult(
                    changes = ChangeSpec.Single(
                        from = r.from,
                        to = r.to,
                        insert = InsertContent.StringContent("-".repeat((r.from shr 1) + 1))
                    ),
                    range = EditorSelection.range(r.from, r.from + 1 + (r.from shr 1))
                )
            }
        ).state
        assertEquals("- -- --- ----", state.doc.toString())
        assertEquals(
            "0-1 2-4 5-8 9-13",
            state.selection.ranges.map { r -> "${r.from}-${r.to}" }.joinToString(" ")
        )
    }

    // changeFilter tests

    @Test
    fun changeFilterCanCancelChanges() {
        // Cancels all changes that add length
        val state = EditorState.create(
            EditorStateConfig(
                extensions = ExtensionList(
                    listOf(
                        EditorState.changeFilter.of { tr ->
                            tr.changes.newLength <= tr.changes.length
                        }
                    )
                ),
                doc = "one two"
            )
        )
        val tr1 = state.update(
            TransactionSpec(
                changes = ChangeSpec.Single(
                    from = 3,
                    insert = InsertContent.StringContent(" three")
                ),
                selection = SelectionSpec.CursorSpec(anchor = 13)
            )
        )
        assertEquals("one two", tr1.state.doc.toString())
        assertEquals(7, tr1.state.selection.main.head)
        val tr2 = state.update(
            TransactionSpec(
                changes = ChangeSpec.Single(
                    from = 4,
                    to = 7,
                    insert = InsertContent.StringContent("2")
                )
            )
        )
        assertEquals("one 2", tr2.state.doc.toString())
    }

    @Test
    fun changeFilterCanSplitChanges() {
        // Disallows changes in the middle third of the document
        val state = EditorState.create(
            EditorStateConfig(
                extensions = ExtensionList(
                    listOf(
                        EditorState.changeFilter.of { tr ->
                            intArrayOf(
                                floor(tr.startState.doc.length.toDouble() / 3).toInt(),
                                floor(2.0 * tr.startState.doc.length / 3).toInt()
                            )
                        }
                    )
                ),
                doc = "onetwo"
            )
        )
        assertEquals(
            "et",
            state.update(
                TransactionSpec(changes = ChangeSpec.Single(from = 0, to = 6))
            ).state.doc.toString()
        )
    }

    @Test
    fun changeFilterCombinesFilterMasks() {
        val state = EditorState.create(
            EditorStateConfig(
                extensions = ExtensionList(
                    listOf(
                        EditorState.changeFilter.of { intArrayOf(0, 2) },
                        EditorState.changeFilter.of { intArrayOf(4, 6) }
                    )
                ),
                doc = "onetwo"
            )
        )
        assertEquals(
            "onwo",
            state.update(
                TransactionSpec(changes = ChangeSpec.Single(from = 0, to = 6))
            ).state.doc.toString()
        )
    }

    @Test
    fun changeFilterCanBeTurnedOff() {
        val state = EditorState.create(
            EditorStateConfig(
                extensions = ExtensionList(
                    listOf(EditorState.changeFilter.of { false })
                )
            )
        )
        assertEquals(
            0,
            state.update(
                TransactionSpec(
                    changes = ChangeSpec.Single(
                        from = 0,
                        insert = InsertContent.StringContent("hi")
                    )
                )
            ).state.doc.length
        )
        assertEquals(
            2,
            state.update(
                TransactionSpec(
                    changes = ChangeSpec.Single(
                        from = 0,
                        insert = InsertContent.StringContent("hi")
                    ),
                    filter = false
                )
            ).state.doc.length
        )
    }

    // transactionFilter tests

    @Test
    fun transactionFilterCanConstrainTheSelection() {
        val state = EditorState.create(
            EditorStateConfig(
                extensions = EditorState.transactionFilter.of { tr ->
                    if (tr.selection != null && tr.selection!!.main.to > 4) {
                        listOf(
                            TransactionSpec(),
                            TransactionSpec(selection = SelectionSpec.CursorSpec(anchor = 4))
                        )
                    } else {
                        tr
                    }
                },
                doc = "one two"
            )
        )
        assertEquals(
            3,
            state.update(
                TransactionSpec(selection = SelectionSpec.CursorSpec(anchor = 3))
            ).selection!!.main.to
        )
        assertEquals(
            4,
            state.update(
                TransactionSpec(selection = SelectionSpec.CursorSpec(anchor = 7))
            ).selection!!.main.to
        )
    }

    @Test
    fun transactionFilterCanAppendSequentialChanges() {
        val state = EditorState.create(
            EditorStateConfig(
                extensions = EditorState.transactionFilter.of { tr ->
                    listOf(
                        TransactionSpec(
                            changes = ChangeSpec.Set(tr.changes),
                            selection = if (tr.selection != null) {
                                SelectionSpec.EditorSelectionSpec(tr.selection!!)
                            } else {
                                null
                            },
                            effects = tr.effects,
                            annotations = tr.annotations,
                            scrollIntoView = tr.scrollIntoView
                        ),
                        TransactionSpec(
                            changes = ChangeSpec.Single(
                                from = tr.changes.newLength,
                                insert = InsertContent.StringContent("!")
                            ),
                            sequential = true
                        )
                    )
                },
                doc = "one two"
            )
        )
        assertEquals(
            "one, two!",
            state.update(
                TransactionSpec(
                    changes = ChangeSpec.Single(
                        from = 3,
                        insert = InsertContent.StringContent(",")
                    )
                )
            ).state.doc.toString()
        )
    }

    // transactionExtender tests

    @Test
    fun transactionExtenderCanAddAnnotations() {
        val ann = Annotation.define<Int>()
        val state = EditorState.create(
            EditorStateConfig(
                extensions = EditorState.transactionExtender.of {
                    TransactionExtenderResult(annotations = listOf(ann.of(100)))
                }
            )
        )
        val tr = state.update(
            TransactionSpec(
                changes = ChangeSpec.Single(from = 0, insert = InsertContent.StringContent("!"))
            )
        )
        assertEquals(100, tr.annotation(ann))
        val trNoFilter = state.update(
            TransactionSpec(
                changes = ChangeSpec.Single(from = 0, insert = InsertContent.StringContent("!")),
                filter = false
            )
        )
        assertEquals(100, trNoFilter.annotation(ann))
    }

    @Test
    fun transactionExtenderAllowsMultipleExtendersToTakeEffect() {
        val eff = StateEffect.define<Int>()
        val state = EditorState.create(
            EditorStateConfig(
                extensions = ExtensionList(
                    listOf(
                        EditorState.transactionExtender.of {
                            TransactionExtenderResult(effects = listOf(eff.of(1)))
                        },
                        EditorState.transactionExtender.of {
                            TransactionExtenderResult(effects = listOf(eff.of(2)))
                        }
                    )
                )
            )
        )
        val tr = state.update(TransactionSpec(scrollIntoView = true))
        assertEquals(
            "2,1",
            tr.effects.map { e -> if (e.`is`(eff)) e.value else 0 }.joinToString(",")
        )
    }
}
