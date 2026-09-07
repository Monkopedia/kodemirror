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
package com.monkopedia.kodemirror.lezer.lr

/** How far [wideScanTokenizer] looks ahead before it decides on a token. */
const val WIDE_SCAN_PEEK: Int = 40

/** How much input [wideScanTokenizer] actually consumes for a `Wide` token. */
const val WIDE_SCAN_TOKEN_LENGTH: Int = 8

private const val WIDE_TERM = 1
private const val W_CHAR = 'w'.code

/**
 * An external tokenizer that scans much further than it consumes.
 *
 * Real grammars do this: `@lezer/python`'s newline/indentation tokenizers and
 * `@lezer/css`'s external tokens all read well past the token they accept. It
 * matters here because [TokenCache.getActions] records
 * `stack.setLookAhead(...)` whenever a token's scan reaches more than
 * [Lookahead.MARGIN] characters past its end, and that recorded lookahead is
 * written into the tree as `NodeProp.lookAhead`.
 */
val wideScanTokenizer: ExternalTokenizer = ExternalTokenizer({ input, _ ->
    for (i in 0 until WIDE_SCAN_PEEK) input.peek(i)
    if (input.peek(0) == W_CHAR &&
        input.peek(WIDE_SCAN_TOKEN_LENGTH - 1) >= 0
    ) {
        input.acceptToken(WIDE_TERM, WIDE_SCAN_TOKEN_LENGTH)
    }
})

/**
 * A grammar whose only interesting feature is a chain of unit productions
 * above an external token, so that shifting a token is followed by several
 * default-reduce states at the same position. Built with lezer-generator from:
 *
 * ```
 * @external tokens scan from "./tok" { Wide }
 * @top Program { Item+ }
 * Item { Wrap }
 * Wrap { Inner }
 * Inner { X | Wide }
 * @tokens { X { "x" } }
 * ```
 *
 * An external tokenizer is what puts a non-zero `TOKENIZER_MASK` on those
 * default-reduce states (`@lezer/python` has 217 such states, `@lezer/css` 93,
 * `@lezer/javascript` 232), which is what makes it observable whether the
 * parser tokenises there.
 */
val wideScanParser: LRParser = LRParser.deserialize(
    ParserSpec(
        version = 14,
        states = "zOQOROOOOOP'#Ca'#CaOOOP'#C`'#C`OOOP'#C_'#C_OOOP'#Cc'#Cc" +
            "QQOROOOOOP-E6a-E6a",
        stateData = "Y~OPPOUPO~O",
        goto = "kWPPPX]aPeTSOTTROTTQOTQTORUT",
        nodeNames = "\u26A0 Wide Program Item Wrap Inner X",
        maxTerm = 8,
        skippedNodes = listOf(0),
        repeatNodeCount = 1,
        tokenData = "Z~RP#l#mU~ZOU~",
        tokenizers = listOf(wideScanTokenizer, 0),
        topRules = mapOf("Program" to listOf(0, 2)),
        tokenPrec = 0
    )
)
