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

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonEncoder
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.double
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.int
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.long
import kotlinx.serialization.json.longOrNull

/**
 * Serializable representation of a [SelectionRange].
 */
@Serializable
data class SelectionRangeData(val anchor: Int, val head: Int)

/**
 * Serializable representation of an [EditorSelection].
 */
@Serializable
data class EditorSelectionData(
    val ranges: List<SelectionRangeData>,
    val main: Int
)

/**
 * Serializable representation of an [EditorState].
 *
 * Contains the document text, selection, and any serializable custom fields.
 */
@Serializable
data class EditorStateData(
    val doc: String,
    val selection: EditorSelectionData,
    val fields: Map<String, JsonElement> = emptyMap()
)

/** Default [Json] instance used for state serialization. */
val StateJson: Json = Json { ignoreUnknownKeys = true }

// -- SelectionRange conversions --

/** Convert this range to a serializable [SelectionRangeData]. */
fun SelectionRange.toData(): SelectionRangeData =
    SelectionRangeData(anchor = anchor.value, head = head.value)

/** Restore a [SelectionRange] from its serializable representation. */
fun SelectionRangeData.toSelectionRange(): SelectionRange =
    EditorSelection.range(DocPos(anchor), DocPos(head))

// -- EditorSelection conversions --

/** Convert this selection to a serializable [EditorSelectionData]. */
fun EditorSelection.toData(): EditorSelectionData = EditorSelectionData(
    ranges = ranges.map { it.toData() },
    main = mainIndex
)

/** Restore an [EditorSelection] from its serializable representation. */
fun EditorSelectionData.toEditorSelection(): EditorSelection = EditorSelection.create(
    ranges = ranges.map { it.toSelectionRange() },
    mainIndex = main
)

// -- EditorState conversions --

/**
 * Serialize this state to an [EditorStateData].
 *
 * @param fields Map of name → [StateField] for custom fields that should be
 *   included. Only fields with [FieldSerialization.Serializer] or
 *   [FieldSerialization.Custom] serialization will be saved.
 */
@Suppress("DEPRECATION")
fun EditorState.toData(fields: Map<String, StateField<*>>? = null): EditorStateData {
    val fieldMap = mutableMapOf<String, JsonElement>()
    if (fields != null) {
        for ((prop, value) in fields) {
            if (config.address[value.id] != null) {
                @Suppress("UNCHECKED_CAST")
                val sf = value as StateField<Any?>
                val ser = sf.spec.serialization
                when (ser) {
                    is FieldSerialization.Serializer<*> -> {
                        @Suppress("UNCHECKED_CAST")
                        val serializer = ser.serializer as KSerializer<Any?>
                        fieldMap[prop] = StateJson.encodeToJsonElement(
                            serializer,
                            field(sf)
                        )
                    }
                    is FieldSerialization.Custom -> {
                        val raw = ser.toJSON(field(sf), this)
                        fieldMap[prop] = StateJson.encodeToJsonElement(
                            AnySerializer,
                            raw
                        )
                    }
                    is FieldSerialization.None -> { /* skip */ }
                }
            }
        }
    }
    return EditorStateData(
        doc = sliceDoc(),
        selection = selection.toData(),
        fields = fieldMap
    )
}

/**
 * Deserialize an [EditorState] from its [EditorStateData] representation.
 *
 * @param data The serialized state data.
 * @param config Optional [EditorStateConfig] providing extensions for the restored state.
 * @param fields Map of name → [StateField] for custom fields to restore.
 */
@Suppress("DEPRECATION")
fun EditorState.Companion.fromData(
    data: EditorStateData,
    config: EditorStateConfig = EditorStateConfig(),
    fields: Map<String, StateField<*>>? = null
): EditorState {
    val fieldInit = mutableListOf<Extension>()
    if (fields != null) {
        for ((prop, field) in fields) {
            val element = data.fields[prop] ?: continue

            @Suppress("UNCHECKED_CAST")
            val sf = field as StateField<Any?>
            val ser = sf.spec.serialization
            when (ser) {
                is FieldSerialization.Serializer<*> -> {
                    @Suppress("UNCHECKED_CAST")
                    val serializer = ser.serializer as KSerializer<Any?>
                    val value = StateJson.decodeFromJsonElement(
                        serializer,
                        element
                    )
                    fieldInit.add(sf.init { value })
                }
                is FieldSerialization.Custom -> {
                    val raw = StateJson.decodeFromJsonElement(
                        AnySerializer,
                        element
                    )
                    fieldInit.add(
                        sf.init { state -> ser.fromJSON(raw, state) }
                    )
                }
                is FieldSerialization.None -> { /* skip */ }
            }
        }
    }
    return create(
        EditorStateConfig(
            doc = DocSpec.StringDoc(data.doc),
            selection = SelectionSpec.EditorSelectionSpec(
                data.selection.toEditorSelection()
            ),
            extensions = if (config.extensions != null) {
                ExtensionList(fieldInit + config.extensions)
            } else if (fieldInit.isNotEmpty()) {
                ExtensionList(fieldInit)
            } else {
                null
            }
        )
    )
}

/**
 * Serializer that bridges untyped `Any?` values to/from [JsonElement].
 *
 * Used internally to support the deprecated [FieldSerialization.Custom]
 * with the new [JsonElement]-based serialization infrastructure.
 */
internal object AnySerializer : KSerializer<Any?> {
    override val descriptor: SerialDescriptor =
        buildClassSerialDescriptor("Any")

    override fun serialize(encoder: Encoder, value: Any?) {
        val jsonEncoder = encoder as JsonEncoder
        jsonEncoder.encodeJsonElement(toJsonElement(value))
    }

    override fun deserialize(decoder: Decoder): Any? {
        val jsonDecoder = decoder as JsonDecoder
        return fromJsonElement(jsonDecoder.decodeJsonElement())
    }

    private fun toJsonElement(value: Any?): JsonElement = when (value) {
        null -> JsonNull
        is String -> JsonPrimitive(value)
        is Number -> JsonPrimitive(value)
        is Boolean -> JsonPrimitive(value)
        is Map<*, *> -> JsonObject(
            value.entries.associate { (k, v) ->
                k.toString() to toJsonElement(v)
            }
        )
        is List<*> -> JsonArray(value.map { toJsonElement(it) })
        else -> JsonPrimitive(value.toString())
    }

    private fun fromJsonElement(element: JsonElement): Any? = when (element) {
        is JsonNull -> null
        is JsonPrimitive -> when {
            element.isString -> element.content
            element.booleanOrNull != null -> element.boolean
            element.intOrNull != null -> element.int
            element.longOrNull != null -> element.long
            element.doubleOrNull != null -> element.double
            else -> element.content
        }
        is JsonObject -> element.mapValues {
            fromJsonElement(it.value)
        }
        is JsonArray -> element.map { fromJsonElement(it) }
    }
}
