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
@file:Suppress("unused")

package com.monkopedia.kodemirror.state

/**
 * Public API surface for the state module.
 *
 * In Kotlin Multiplatform, explicit re-exports are not needed
 * since all public declarations in the package are accessible.
 * This file documents the intended public API.
 */

// State
internal const val STATE_MODULE = "state"

// Public API:
// - EditorStateConfig, EditorState (State.kt)
// - Facet, FacetReader, StateField, Extension,
//     Prec, Compartment (Facet.kt)
// - EditorSelection, SelectionRange (Selection.kt)
// - Transaction, TransactionSpec, Annotation,
//     AnnotationType, StateEffect,
//     StateEffectType (Transaction.kt)
// - ChangeSpec, ChangeSet, ChangeDesc,
//     MapMode (Change.kt)
// - CharCategory (CharCategory.kt)
// - RangeValue, Range, RangeSet, RangeCursor,
//     RangeSetBuilder, RangeComparator,
//     SpanIterator (RangeSet.kt)
// - findClusterBreak, codePointAt, fromCodePoint,
//     codePointSize (Char.kt)
// - countColumn, findColumn (Column.kt)
// - Line, TextIterator, Text (Text.kt)
