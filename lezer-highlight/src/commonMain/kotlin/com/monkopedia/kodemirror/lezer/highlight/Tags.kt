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
package com.monkopedia.kodemirror.lezer.highlight

/**
 * The default set of highlighting Tags.
 */
@Suppress("MemberVisibilityCanBePrivate")
object Tags {
    private val comment_ = Tag.define("comment")
    private val name_ = Tag.define("name")
    private val typeName_ = Tag.define("typeName", name_)
    private val propertyName_ = Tag.define("propertyName", name_)
    private val literal_ = Tag.define("literal")
    private val string_ = Tag.define("string", literal_)
    private val number_ = Tag.define("number", literal_)
    private val content_ = Tag.define("content")
    private val heading_ = Tag.define("heading", content_)
    private val keyword_ = Tag.define("keyword")
    private val operator_ = Tag.define("operator")
    private val punctuation_ = Tag.define("punctuation")
    private val bracket_ = Tag.define("bracket", punctuation_)
    private val meta_ = Tag.define("meta")

    val comment: Tag = comment_
    val lineComment: Tag = Tag.define("lineComment", comment_)
    val blockComment: Tag = Tag.define("blockComment", comment_)
    val docComment: Tag = Tag.define("docComment", comment_)

    val name: Tag = name_
    val variableName: Tag = Tag.define("variableName", name_)
    val typeName: Tag = typeName_
    val tagName: Tag = Tag.define("tagName", typeName_)
    val propertyName: Tag = propertyName_
    val attributeName: Tag = Tag.define("attributeName", propertyName_)
    val className: Tag = Tag.define("className", name_)
    val labelName: Tag = Tag.define("labelName", name_)
    val namespace: Tag = Tag.define("namespace", name_)
    val macroName: Tag = Tag.define("macroName", name_)

    val literal: Tag = literal_
    val string: Tag = string_
    val docString: Tag = Tag.define("docString", string_)
    val character: Tag = Tag.define("character", string_)
    val attributeValue: Tag = Tag.define("attributeValue", string_)
    val number: Tag = number_
    val integer: Tag = Tag.define("integer", number_)
    val float: Tag = Tag.define("float", number_)
    val bool: Tag = Tag.define("bool", literal_)
    val regexp: Tag = Tag.define("regexp", literal_)
    val escape: Tag = Tag.define("escape", literal_)
    val color: Tag = Tag.define("color", literal_)
    val url: Tag = Tag.define("url", literal_)

    val keyword: Tag = keyword_
    val self: Tag = Tag.define("self", keyword_)
    val `null`: Tag = Tag.define("null", keyword_)
    val atom: Tag = Tag.define("atom", keyword_)
    val unit: Tag = Tag.define("unit", keyword_)
    val modifier: Tag = Tag.define("modifier", keyword_)
    val operatorKeyword: Tag = Tag.define("operatorKeyword", keyword_)
    val controlKeyword: Tag = Tag.define("controlKeyword", keyword_)
    val definitionKeyword: Tag = Tag.define("definitionKeyword", keyword_)
    val moduleKeyword: Tag = Tag.define("moduleKeyword", keyword_)

    val operator: Tag = operator_
    val derefOperator: Tag = Tag.define("derefOperator", operator_)
    val arithmeticOperator: Tag = Tag.define("arithmeticOperator", operator_)
    val logicOperator: Tag = Tag.define("logicOperator", operator_)
    val bitwiseOperator: Tag = Tag.define("bitwiseOperator", operator_)
    val compareOperator: Tag = Tag.define("compareOperator", operator_)
    val updateOperator: Tag = Tag.define("updateOperator", operator_)
    val definitionOperator: Tag = Tag.define("definitionOperator", operator_)
    val typeOperator: Tag = Tag.define("typeOperator", operator_)
    val controlOperator: Tag = Tag.define("controlOperator", operator_)

    val punctuation: Tag = punctuation_
    val separator: Tag = Tag.define("separator", punctuation_)
    val bracket: Tag = bracket_
    val angleBracket: Tag = Tag.define("angleBracket", bracket_)
    val squareBracket: Tag = Tag.define("squareBracket", bracket_)
    val paren: Tag = Tag.define("paren", bracket_)
    val brace: Tag = Tag.define("brace", bracket_)

    val content: Tag = content_
    val heading: Tag = heading_
    val heading1: Tag = Tag.define("heading1", heading_)
    val heading2: Tag = Tag.define("heading2", heading_)
    val heading3: Tag = Tag.define("heading3", heading_)
    val heading4: Tag = Tag.define("heading4", heading_)
    val heading5: Tag = Tag.define("heading5", heading_)
    val heading6: Tag = Tag.define("heading6", heading_)
    val contentSeparator: Tag = Tag.define("contentSeparator", content_)
    val list: Tag = Tag.define("list", content_)
    val quote: Tag = Tag.define("quote", content_)
    val emphasis: Tag = Tag.define("emphasis", content_)
    val strong: Tag = Tag.define("strong", content_)
    val link: Tag = Tag.define("link", content_)
    val monospace: Tag = Tag.define("monospace", content_)
    val strikethrough: Tag = Tag.define("strikethrough", content_)

    val inserted: Tag = Tag.define("inserted")
    val deleted: Tag = Tag.define("deleted")
    val changed: Tag = Tag.define("changed")

    val invalid: Tag = Tag.define("invalid")

    val meta: Tag = meta_
    val documentMeta: Tag = Tag.define("documentMeta", meta_)
    val annotation: Tag = Tag.define("annotation", meta_)
    val processingInstruction: Tag = Tag.define("processingInstruction", meta_)

    // Modifiers
    val definition: (Tag) -> Tag = Tag.defineModifier("definition")
    val constant: (Tag) -> Tag = Tag.defineModifier("constant")
    val function: (Tag) -> Tag = Tag.defineModifier("function")
    val standard: (Tag) -> Tag = Tag.defineModifier("standard")
    val local: (Tag) -> Tag = Tag.defineModifier("local")
    val special: (Tag) -> Tag = Tag.defineModifier("special")
}
