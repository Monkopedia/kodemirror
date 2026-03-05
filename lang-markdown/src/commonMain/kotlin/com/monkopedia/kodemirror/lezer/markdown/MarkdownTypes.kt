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
package com.monkopedia.kodemirror.lezer.markdown

@Suppress("ktlint:standard:property-naming")
object Type {
    const val Document = 1

    const val CodeBlock = 2
    const val FencedCode = 3
    const val Blockquote = 4
    const val HorizontalRule = 5
    const val BulletList = 6
    const val OrderedList = 7
    const val ListItem = 8
    const val ATXHeading1 = 9
    const val ATXHeading2 = 10
    const val ATXHeading3 = 11
    const val ATXHeading4 = 12
    const val ATXHeading5 = 13
    const val ATXHeading6 = 14
    const val SetextHeading1 = 15
    const val SetextHeading2 = 16
    const val HTMLBlock = 17
    const val LinkReference = 18
    const val Paragraph = 19
    const val CommentBlock = 20
    const val ProcessingInstructionBlock = 21

    // Inline
    const val Escape = 22
    const val Entity = 23
    const val HardBreak = 24
    const val Emphasis = 25
    const val StrongEmphasis = 26
    const val Link = 27
    const val Image = 28
    const val InlineCode = 29
    const val HTMLTag = 30
    const val Comment = 31
    const val ProcessingInstruction = 32
    const val Autolink = 33

    // Smaller tokens
    const val HeaderMark = 34
    const val QuoteMark = 35
    const val ListMark = 36
    const val LinkMark = 37
    const val EmphasisMark = 38
    const val CodeMark = 39
    const val CodeText = 40
    const val CodeInfo = 41
    const val LinkTitle = 42
    const val LinkLabel = 43
    const val URL = 44
}

// Returns true if a result means the block was consumed
typealias BlockResult = Boolean?
