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
@file:Suppress("ktlint:standard:max-line-length")

package com.monkopedia.kodemirror.lang.angular

import com.monkopedia.kodemirror.lezer.highlight.Tags
import com.monkopedia.kodemirror.lezer.highlight.styleTags
import com.monkopedia.kodemirror.lezer.lr.LRParser
import com.monkopedia.kodemirror.lezer.lr.ParserSpec

/** Highlighting rules for Angular template syntax. */
val angularHighlighting = styleTags(
    mapOf(
        "Text" to Tags.content,
        "Is" to Tags.definitionOperator,
        "AttributeName" to Tags.attributeName,
        "AttributeValue ExpressionAttributeValue StatementAttributeValue" to Tags.attributeValue,
        "Entity" to Tags.character,
        "InvalidEntity" to Tags.invalid,
        "BoundAttributeName/Identifier" to Tags.attributeName,
        "EventName/Identifier" to Tags.special(Tags.attributeName),
        "ReferenceName/Identifier" to Tags.variableName,
        "DirectiveName/Identifier" to Tags.keyword,
        "{{ }}" to Tags.brace,
        "( )" to Tags.paren,
        "[ ]" to Tags.bracket,
        "# '*'" to Tags.punctuation
    )
)

internal val angularParser: LRParser = LRParser.deserialize(
    ParserSpec(
        version = 14,
        states = "(jOVOqOOOeQpOOOvO!bO'#CaOOOP'#Cx'#CxQVOqOOO!OQpO'#CfO!WQpO'#ClO!]QpO'#CrO!bQpO'#CsOOQO'#Cv'#CvQ!gQpOOQ!lQpOOQ!qQpOOOOOV,58{,58{O!vOpO,58{OOOP-E6v-E6vO!{QpO,59QO#TQpO,59QOOQO,59W,59WO#YQpO,59^OOQO,59_,59_O#_QpOOO#_QpOOO#gQpOOOOOV1G.g1G.gO#oQpO'#CyO#tQpO1G.lOOQO1G.l1G.lO#|QpO1G.lOOQO1G.x1G.xO\$UO`O'#DUO\$ZOWO'#DUOOQO'#Co'#CoQOQpOOOOQO'#Cu'#CuO$`OtO'#CwO\$qOrO'#CwOOQO,59e,59eOOQO-E6w-E6wOOQO7+\$W7+\$WO%SQpO7+\$WO%[QpO7+\$WOOOO'#Cp'#CpO%aOpO,59pOOOO'#Cq'#CqO%fOpO,59pOOOS'#Cz'#CzO%kOtO,59cOOQO,59c,59cOOOQ'#C{'#C{O%|OrO,59cO&_QpO<<GrOOQO<<Gr<<GrOOQO1G/[1G/[OOOS-E6x-E6xOOQO1G.}1G.}OOOQ-E6y-E6yOOQOAN=^AN=^",
        stateData = "&d~OvOS~OPROSQOVROWRO~OZTO[XO^VOaUOhWO~OR]OU^O~O[`O^aO~O[bO~O[cO~O[dO~ObeO~ObfO~ObgO~ORhO~O]kOwiO~O[lO~O_mO~OynOzoO~OysOztO~O[uO~O]wOwiO~O_yOwiO~OtzO~Os|O~OSQOV!OOW!OOr!OOy!QO~OSQOV!ROW!ROq!ROz!QO~O_!TOwiO~O]!UO~Oy!VO~Oz!VO~OSQOV!OOW!OOr!OOy!XO~OSQOV!ROW!ROq!ROz!XO~O]!ZO~O",
        goto = "#dyPPPPPzPPPP!WPPPPP!WPP!Z!^!a!d!dP!g!j!m!p!v#Q#WPPPPPPPP#^SROSS!Os!PT!Rt!SRYPRqeR{nR}oRZPRqfR[PRqgQSOR_SQj`SvjxRxlQ!PsR!W!PQ!StR!Y!SQpeRrf",
        nodeNames = "\u26A0 Text Content }} {{ Interpolation InterpolationContent Entity InvalidEntity Attribute BoundAttributeName [ Identifier ] ( ) ReferenceName # Is ExpressionAttributeValue AttributeInterpolation AttributeInterpolation EventName DirectiveName * StatementAttributeValue AttributeName AttributeValue",
        maxTerm = 42,
        nodeProps = listOf(
            listOf("openedBy", 3, "{{", 15, "("),
            listOf("closedBy", 4, "}}", 14, ")"),
            listOf("isolate", -4, 5, 19, 25, 27, "")
        ),
        skippedNodes = listOf(0),
        repeatNodeCount = 4,
        tokenData = "0r~RyOX#rXY\$mYZ\$mZ]#r]^\$m^p#rpq\$mqr#rrs%jst&Qtv#rvw&hwx)zxy*byz*xz{+`{}\u0023r}!O+v!O!P-]!P!Q#r!Q![+v![!]+v!]!_#r!_!`-s!`!c#r!c!}+v!}#O.Z#O#P#r#P#Q.q#Q#R#r#R#S+v#S#T#r#T#o+v#o#p/X#p#q#r#q#r0Z#r%W#r%W;'S+v;'S;:j-V;:j;=`\$g<%lO+vQ#wTUQO#q#r#q#r\$W#r;'S#r;'S;=`\$g<%lO#rQ\$ZSO#q#r#r;'S#r;'S;=`\$g<%lO#rQ\$jP;=`<%l#rR\$t[UQvPOX#rXY\$mYZ\$mZ]#r]^\$m^p#rpq\$mq#q#r#q#r\$W#r;'S#r;'S;=`\$g<%lO#rR%qTyPUQO#q#r#q#r\$W#r;'S#r;'S;=`\$g<%lO#rR&XTaPUQO#q#r#q#r\$W#r;'S#r;'S;=`\$g<%lO#rR&oXUQWPOp'[pq#rq!]'[!]!^#r!^#q'[#q#r(d#r;'S'[;'S;=`)t<%lO'[R'aXUQOp'[pq#rq!]'[!]!^'|!^#q'[#q#r(d#r;'S'[;'S;=`)t<%lO'[R(TTVPUQO#q#r#q#r\$W#r;'S#r;'S;=`\$g<%lO#rR(gXOp'[pq#rq!]'[!]!^'|!^#q'[#q#r)S#r;'S'[;'S;=`)t<%lO'[P)VUOp)Sq!])S!]!^)i!^;'S)S;'S;=`)n<%lO)SP)nOVPP)qP;=`<%l)SR)wP;=`<%l'[R*RTzPUQO#q#r#q#r\$W#r;'S#r;'S;=`\$g<%lO#rR*iT^PUQO#q#r#q#r\$W#r;'S#r;'S;=`\$g<%lO#rR+PT_PUQO#q#r#q#r\$W#r;'S#r;'S;=`\$g<%lO#rR+gThPUQO#q#r#q#r\$W#r;'S#r;'S;=`\$g<%lO#rR+}b[PUQO}#r}!O+v!O!Q#r!Q![+v![!]+v!]!c#r!c!}+v!}#R#r#R#S+v#S#T#r#T#o+v#o#q#r#q#r\$W#r%W#r%W;'S+v;'S;:j-V;:j;=`\$g<%lO+vR-YP;=`<%l+vR-dTwPUQO#q#r#q#r\$W#r;'S#r;'S;=`\$g<%lO#rR-zTUQbPO#q#r#q#r\$W#r;'S#r;'S;=`\$g<%lO#rR.bTZPUQO#q#r#q#r\$W#r;'S#r;'S;=`\$g<%lO#rR.xT]PUQO#q#r#q#r\$W#r;'S#r;'S;=`\$g<%lO#rR/^VUQO#o#r#o#p/s#p#q#r#q#r\$W#r;'S#r;'S;=`\$g<%lO#rR/zTSPUQO#q#r#q#r\$W#r;'S#r;'S;=`\$g<%lO#r~0^TO#q#r#q#r0m#r;'S#r;'S;=`\$g<%lO#r~0rOR~",
        tokenizers = listOf(text, attrSingle, attrDouble, scriptAttrSingle, scriptAttrDouble, 0, 1),
        topRules = mapOf(
            "Content" to listOf(0, 2),
            "Attribute" to listOf(1, 9)
        ),
        tokenPrec = 0
    )
)
