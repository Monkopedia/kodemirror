package com.monkopedia.kodemirror.state

import com.monkopedia.kodemirror.state.Either.Companion.asLeft
import com.monkopedia.kodemirror.state.Either.Companion.asRight
import com.monkopedia.kodemirror.state.SingleOrList.Companion.list
import com.monkopedia.kodemirror.state.SingleOrList.Companion.single
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFails
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject

object EditorStateTest {
    class State {
        @Test
        fun holds_doc_and_selection_properties() {
            val state = EditorState.create(doc = "hello".asText)
            assertEquals("hello", state.doc.toString())
            assertEquals(0, state.selection.main.from)
        }

        @Test
        fun can_apply_changes() {
            val state = EditorState.create(doc = "hello".asText)
            val transaction = state.update(
                changes = listOf(
                    ChangeSpecData(from = 2, to = 4, insert = "w".asText),
                    ChangeSpecData(from = 5, insert = "!".asText)
                ).asSpec
            )
            assertEquals("hewo!", transaction.state.doc.toString())
        }

        @Test
        fun maps_selection_through_changes() {
            val state = EditorState.create(
                doc = "abcdefgh".asText,
                extensions = listOf(EditorState.allowMultipleSelections.of(true)).extension,
                selection = EditorSelection.create(
                    listOf(
                        0,
                        4,
                        8
                    ).map { n -> EditorSelection.cursor(n) }
                )
            )
            val newState = state.update(state.replaceSelection("Q".asText)).state
            assertEquals("QabcdQefghQ", newState.doc.toString())
            assertEquals("1/6/11", newState.selection.ranges.map({ r -> r.from }).joinToString("/"))
        }

        val someAnnotation = Annotation.define<Int>()

        @Test
        fun can_store_annotations_on_transactions() {
            val tr =
                EditorState.create(doc = "foo".asText)
                    .update(annotations = someAnnotation.of(55).single)
            assertEquals(55, tr.annotation(someAnnotation))
        }

        @Test
        fun throws_when_a_changes_bounds_are_invalid() {
            val state = EditorState.create(doc = "1234".asText)
            assertFails { state.update(changes = ChangeSpecData(from = -1, to = 1)) }
            assertFails { state.update(changes = ChangeSpecData(from = 2, to = 1)) }
            assertFails { state.update(changes = ChangeSpecData(from = 2, to = 10, insert = "x")) }
        }

        @Test
        fun stores_and_updates_tab_size() {
            val deflt = EditorState.create()
            val two = EditorState.create(
                extensions = EditorState.tabSize.of(2)
            )
            assertEquals(4, deflt.tabSize)
            assertEquals(2, two.tabSize)
            val updated =
                deflt.update(
                    effects = StateEffect.reconfigure.of(EditorState.tabSize.of(8)).single
                ).state
            assertEquals(8, updated.tabSize)
        }

        @Test
        fun stores_and_updates_the_line_separator() {
            val deflt = EditorState.create()
            val crlf = EditorState.create(
                extensions = EditorState.lineSeparator.of("\r\n")
            )
            assertEquals(null, deflt.facet(EditorState.lineSeparator))
            assertEquals(2, deflt.toText("a\nb").lines)
            assertEquals("\r\n", crlf.facet(EditorState.lineSeparator))
            assertEquals(1, crlf.toText("a\nb").lines)
            val updated = crlf.update(
                effects = StateEffect.reconfigure.of(EditorState.lineSeparator.of("\n")).single
            ).state
            assertEquals("\n", updated.facet(EditorState.lineSeparator))
        }

        @Test
        fun stores_and_updates_fields() {
            val field1 = StateField.define<Int>(create = { 0 }, update = { v, _ -> v + 1 })
            val field2 = StateField.define<Int>(
                create = { state -> state.field(field1) + 10 },
                update = { v, _ -> v }
            )
            val state = EditorState.create(extensions = listOf(field1, field2).extension)
            assertEquals(0, state.field(field1))
            assertEquals(10, state.field(field2))
            val newState = state.update().state
            assertEquals(1, newState.field(field1))
            assertEquals(10, newState.field(field2))
        }

        @Test
        fun allows_fields_to_have_an_initializer() {
            val field = StateField.define(create = { 0 }, update = { v, _ -> v + 1 })
            val state = EditorState.create(extensions = field.init { 10 })
            assertEquals(10, state.field(field))
            assertEquals(11, state.update().state.field(field))
        }

        @Serializable
        data class JsonState(@SerialName("number") val n: Int)

        @Test
        fun can_be_serialized_to_JSON() {
            val field = StateField.defineSerializable<JsonState>(
                create = { JsonState(n = 0) },
                update = { v, _ -> JsonState(v.n + 1) }
            )
            val fields = mapOf("f" to field)
            val state = EditorState.create(extensions = field).update().state
            val json = state.toJSON(fields)
            assertEquals("{\"number\":1}", json.jsonObject["f"].toString())
            val state2 = EditorState.fromJSON(json, fields = fields)
            assertEquals("JsonState(n=1)", state2.field(field).toString())
        }

        @Test
        fun can_preserve_fields_across_reconfiguration() {
            val field = StateField.define(create = { 0 }, update = { v, _ -> v + 1 })
            val start = EditorState.create(extensions = field).update().state
            assertEquals(1, start.field(field))
            assertEquals(
                2,
                start.update(effects = StateEffect.reconfigure.of(field).single).state.field(field)
            )
            assertEquals(
                null,
                start.update(
                    effects = StateEffect.reconfigure.of(emptyList<Extension>().extension).single
                ).state.field(field, false)
            )
        }

        @Test
        fun can_replace_extension_groups() {
            val comp = Compartment()
            val f = Facet.define<Int>()
            val content = f.of(10)
            val state =
                EditorState.create(extensions = listOf(comp.of(content), f.of(20)).extension)
            assertEquals(content, comp.get(state))
            assertEquals("10,20", state.facet(f).joinToString(","))
            val content2 = listOf(f.of(1), f.of(2)).extension
            val state2 = state.update(effects = comp.reconfigure(content2).single).state
            assertEquals(content2, comp.get(state2))
            assertEquals("1,2,20", state2.facet(f).joinToString(","))
            val state3 = state2.update(effects = comp.reconfigure(f.of(3)).single).state
            assertEquals("3,20", state3.facet(f).joinToString(","))
        }

        @Test
        fun raises_an_error_on_duplicate_extension_groups() {
            val comp = Compartment()
            val f = Facet.define<Int>()
            assertFails(
                "duplicate use of compartment"
            ) {
                EditorState.create(
                    extensions = listOf(comp.of(f.of(1)), comp.of(f.of(2))).extension
                )
            }
            assertFails(
                "duplicate use of compartment",
                {
                    EditorState.create(extensions = comp.of(comp.of(f.of(1))))
                }
            )
        }

        @Test
        fun preserves_compartments_on_reconfigure() {
            val comp = Compartment()
            val f = Facet.define<Int>()
            val init = comp.of(f.of(10))
            var state = EditorState.create(extensions = listOf(init, f.of(20)).extension)
            state = state.update(effects = comp.reconfigure(f.of(0)).single).state
            assertEquals("0,20", state.facet(f).joinToString(","))
            state = state.update(
                effects = StateEffect.reconfigure.of(listOf(init, f.of(2)).extension).single
            ).state
            assertEquals("0,2", state.facet(f).joinToString(","))
        }

        @Test
        fun forgets_dropped_compartments() {
            val comp = Compartment()
            val f = Facet.define<Int>()
            val init = comp.of(f.of(10))
            var state = EditorState.create(extensions = listOf(init, f.of(20)).extension)
            state = state.update(effects = comp.reconfigure(f.of(0)).single).state
            assertEquals("0,20", state.facet(f).joinToString(","))
            state = state.update(effects = StateEffect.reconfigure.of(f.of(2)).single).state
            assertEquals("2", state.facet(f).joinToString(","))
            assertEquals(null, comp.get(state))
            state = state.update(
                effects = StateEffect.reconfigure.of(listOf(init, f.of(2)).extension).single
            ).state
            assertEquals("10,2", state.facet(f).joinToString(","))
        }

        @Test
        fun allows_facets_computed_from_fields() {
            val field = StateField.define(
                create =
                { listOf(0) },
                update = { v, tr -> if (tr.docChanged) listOf(tr.state.doc.length) else v }
            )
            val facet = Facet.define<Int>()
            val state = EditorState.create(
                extensions = listOf(
                    field,
                    facet.compute(listOf(field), { state -> state.field(field)[0] }),
                    facet.of(1)
                ).extension
            )
            assertEquals("0,1", state.facet(facet).joinToString(","))
            val state2 = state.update().state
            assertEquals(state.facet(facet), state2.facet(facet))
            val state3 = state.update(changes = ChangeSpecData(insert = "hi", from = 0)).state
            assertEquals("2,1", state3.facet(facet).joinToString(","))
        }

        @Test
        fun blocks_multiple_selections_when_not_allowed() {
            val cursors =
                EditorSelection.create(listOf(EditorSelection.cursor(0), EditorSelection.cursor(1)))
            val state = EditorState.create(
                selection = cursors,
                doc = "123".asText
            )
            assertEquals(1, state.selection.ranges.size)
            assertEquals(1, state.update(selection = cursors).state.selection.ranges.size)
        }

        class ChangeByRange {
            @Test
            fun can_make_simple_changes() {
                var state = EditorState.create(doc = "hi".asText)
                state = state.update(
                    state.changeByRange { r ->
                        EditorState.PartialChangeResult(
                            changes = ChangeSpecData(from = r.from, to = r.from + 1, insert = "q"),
                            range = EditorSelection.cursor(r.from + 1)
                        )
                    }
                ).state
                assertEquals("qi", state.doc.toString())
                assertEquals(1, state.selection.main.from)
            }

            @Test
            fun does_the_right_thing_when_there_are_multiple_selections() {
                var state = EditorState.create(
                    doc = "1 2 3 4".asText,
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
                state = state.update(
                    state.changeByRange { r ->
                        EditorState.PartialChangeResult(
                            changes =
                            ChangeSpecData(
                                from = r.from,
                                to = r.to,
                                insert = "-".repeat((r.from shr 1) + 1)
                            ),
                            range = EditorSelection.range(r.from, r.from + 1 + (r.from shr 1))
                        )
                    }
                ).state
                assertEquals("- -- --- ----", state.doc.toString())
                assertEquals(
                    "0-1 2-4 5-8 9-13",
                    state.selection.ranges.joinToString(" ") { r -> "${r.from}-${r.to}" }
                )
            }
        }
    }

    class ChangeFilter {
        @Test
        fun can_cancel_changes() {
            // Cancels all changes that add length
            val state = EditorState.create(
                extensions = EditorState.changeFilter.of { (_, changes) ->
                    (changes.newLength <= changes.length).asLeft
                },
                doc = "one two".asText
            )
            val tr1 = state.update(
                changes = ChangeSpecData(from = 3, insert = " three"),
                selection = Selection.Data(anchor = 13)
            )
            assertEquals("one two", tr1.state.doc.toString())
            assertEquals(7, tr1.state.selection.main.head)
            val tr2 = state.update(changes = ChangeSpecData(from = 4, to = 7, insert = "2"))
            assertEquals("one 2", tr2.state.doc.toString())
        }

        @Test
        fun can_split_changes() {
            // Disallows changes in the middle third of the document
            val state = EditorState.create(
                extensions =
                EditorState.changeFilter.of { tr ->
                    listOf(
                        tr.startState.doc.length / 3 to
                            2 * tr.startState.doc.length / 3
                    ).asRight
                },
                doc = "onetwo".asText
            )
            assertEquals(
                "et",
                state.update(changes = ChangeSpecData(from = 0, to = 6)).state.doc.toString()
            )
        }

        @Test
        fun combines_filter_masks() {
            val state = EditorState.create(
                extensions = listOf(
                    EditorState.changeFilter.of { listOf(0 to 2).asRight },
                    EditorState.changeFilter.of { listOf(4 to 6).asRight }
                ).extension,
                doc = "onetwo".asText
            )
            assertEquals(
                "onwo",
                state.update(changes = ChangeSpecData(from = 0, to = 6)).state.doc.toString()
            )
        }

        @Test
        fun can_be_turned_off() {
            val state =
                EditorState.create(
                    extensions = listOf(
                        EditorState.changeFilter.of { false.asLeft }
                    ).extension
                )
            assertEquals(
                0,
                state.update(changes = ChangeSpecData(from = 0, insert = "hi")).state.doc.length
            )
            assertEquals(
                2,
                state.update(
                    changes = ChangeSpecData(from = 0, insert = "hi"),
                    filter = false
                ).state.doc.length
            )
        }
    }

    class TransactionFilter {
        @Test
        fun can_constrain_the_selection() {
            val state = EditorState.create(
                extensions = EditorState.transactionFilter.of { tr ->
                    if (tr.selection != null && tr.selection!!.main.to > 4) {
                        listOf(
                            tr,
                            TransactionSpec(selection = Selection.Data(anchor = 4))
                        ).list
                    } else {
                        tr.single
                    }
                },
                doc = "one two".asText
            )
            assertEquals(
                3,
                state.update(selection = Selection.Data(anchor = 3)).selection!!.main.to
            )
            assertEquals(
                4,
                state.update(selection = Selection.Data(anchor = 7)).selection!!.main.to
            )
        }

        @Test
        fun can_append_sequential_changes() {
            val state = EditorState.create(
                extensions = EditorState.transactionFilter.of { tr ->
                    listOf(
                        tr,
                        TransactionSpec(
                            changes =
                            ChangeSpecData(
                                from = tr.changes.newLength,
                                insert = "!"
                            ),
                            sequential = true
                        )
                    ).list
                },
                doc = "one two".asText
            )
            assertEquals(
                "one, two!",
                state.update(changes = ChangeSpecData(from = 3, insert = ",")).state.doc.toString()
            )
        }
    }

    class TransactionExtender {
        @Test
        fun can_add_annotations() {
            val ann = Annotation.define<Int>()
            val state = EditorState.create(
                extensions =
                EditorState.transactionExtender.of {
                    (TransactionSpec(annotations = ann.of(100).single))
                }
            )
            val tr = state.update(changes = ChangeSpecData(from = 0, insert = "!"))
            assertEquals(100, tr.annotation(ann))
            val trNoFilter =
                state.update(changes = ChangeSpecData(from = 0, insert = "!"), filter = false)
            assertEquals(100, trNoFilter.annotation(ann))
        }

        @Test
        fun allows_multipe_extenders_to_take_effect() {
            val eff = StateEffect.define<Int>()
            val state = EditorState.create(
                extensions = listOf(
                    EditorState.transactionExtender.of {
                        TransactionSpec(effects = eff.of(1).single)
                    },
                    EditorState.transactionExtender.of {
                        TransactionSpec(effects = eff.of(2).single)
                    }
                ).extension
            )
            val tr = state.update(scrollIntoView = true)
            assertEquals(
                "2,1",
                tr.effects.list.joinToString(",") { if (it.isOf(eff)) it.value.toString() else "0" }
            )
        }
    }
}
