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
package com.monkopedia.kodemirror.legacy.modes

import com.monkopedia.kodemirror.language.StreamLanguage
import com.monkopedia.kodemirror.language.StreamParser
import com.monkopedia.kodemirror.language.syntaxTree
import com.monkopedia.kodemirror.state.EditorState
import com.monkopedia.kodemirror.state.EditorStateConfig
import com.monkopedia.kodemirror.state.asDoc
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@Suppress("LargeClass")
class LegacyModesSmokeTest {

    private fun <S> smokeTest(parser: StreamParser<S>, code: String) {
        val lang = StreamLanguage.define(parser)
        assertNotNull(lang)
        val state = EditorState.create(
            EditorStateConfig(
                doc = code.asDoc(),
                extensions = lang.extension
            )
        )
        val tree = syntaxTree(state)
        assertNotNull(tree)
        assertEquals(code.length, tree.length)
        assertEquals("Document", tree.type.name)
    }

    @Test fun aplSmoke() = smokeTest(apl, "(+/⍳10)×2")

    @Test fun asciiArmorSmoke() = smokeTest(
        asciiArmor,
        "-----BEGIN PGP MESSAGE-----\ndata\n-----END PGP MESSAGE-----"
    )

    @Test fun asn1Smoke() = smokeTest(
        asn1(),
        "Module DEFINITIONS ::= BEGIN\n  Type ::= INTEGER\nEND"
    )

    @Test fun asteriskSmoke() = smokeTest(asterisk, "[general]\nexten => 100,1,Answer()")

    @Test fun brainfuckSmoke() = smokeTest(brainfuck, "++++++++[>++++<-]>.")

    @Test fun cobolSmoke() = smokeTest(
        cobol,
        "IDENTIFICATION DIVISION.\nPROGRAM-ID. HELLO.\nPROCEDURE DIVISION." +
            "\nDISPLAY 'HELLO'.\nSTOP RUN."
    )

    @Test fun coffeeScriptSmoke() = smokeTest(coffeeScript, "hello = -> console.log 'hi'")

    @Test fun commonLispSmoke() = smokeTest(commonLisp, "(defun hello () (format t \"hello\"))")

    @Test fun cSmoke() = smokeTest(c, "int main() { return 0; }")

    @Test fun cppSmoke() = smokeTest(cpp, "auto x = std::vector<int>{1,2,3};")

    @Test fun javaSmoke() = smokeTest(
        java,
        "public class Main { public static void main(String[] args) {} }"
    )

    @Test fun csharpSmoke() = smokeTest(csharp, "class Program { static void Main() {} }")

    @Test fun scalaSmoke() = smokeTest(scala, "object Main extends App { println(42) }")

    @Test fun kotlinSmoke() = smokeTest(kotlin, "fun main() { println(\"hello\") }")

    @Test fun dartSmoke() = smokeTest(dart, "void main() { print('hello'); }")

    @Test fun clojureSmoke() = smokeTest(clojure, "(defn hello [name] (str \"Hello \" name))")

    @Test fun cmakeSmoke() = smokeTest(
        cmake,
        "cmake_minimum_required(VERSION 3.10)\nproject(MyProject)"
    )

    @Test fun crystalSmoke() = smokeTest(crystal, "puts \"Hello Crystal\"")

    @Test fun cssSmoke() = smokeTest(cssLegacy, "body { color: red; font-size: 14px; }")

    @Test fun cypherSmoke() = smokeTest(cypher, "MATCH (n:Person) RETURN n.name")

    @Test fun dSmoke() = smokeTest(d, "import std.stdio; void main() { writeln(42); }")

    @Test fun diffSmoke() = smokeTest(diff, "+added\n-removed\n unchanged")

    @Test fun dockerfileSmoke() = smokeTest(dockerFile, "FROM ubuntu:latest\nRUN apt-get update")

    @Test fun dtdSmoke() = smokeTest(dtd, "<!ELEMENT note (to,body)>")

    @Test fun dylanSmoke() = smokeTest(dylan, "define method main() format-out(\"hello\") end")

    @Test fun ebnfSmoke() = smokeTest(ebnf, "rule = 'a' | 'b' ;")

    @Test fun eclSmoke() = smokeTest(ecl, "OUTPUT('Hello ECL');")

    @Test fun eiffelSmoke() = smokeTest(
        eiffel,
        "class HELLO\ncreate make\nfeature\n  make do print(\"hello\") end\nend"
    )

    @Test fun elmSmoke() = smokeTest(elm, "main = text \"Hello\"")

    @Test fun erlangSmoke() = smokeTest(erlang, "-module(hello).\nhello() -> io:format(\"hello\").")

    @Test fun factorSmoke() = smokeTest(factor, "USING: io ; \"hello\" print")

    @Test fun fclSmoke() = smokeTest(fcl, "FUNCTION_BLOCK fb1\nEND_FUNCTION_BLOCK")

    @Test fun forthSmoke() = smokeTest(forth, ": HELLO .\" Hello World\" ; HELLO")

    @Test fun fortranSmoke() = smokeTest(fortran, "program hello\n  print *, 'hello'\nend program")

    @Test fun gasSmoke() = smokeTest(gas, ".text\n.globl _start\n_start: movl \$1, %eax")

    @Test fun gherkinSmoke() = smokeTest(
        gherkin,
        "Feature: Test\n  Scenario: Hello\n    Given a step"
    )

    @Test fun goSmoke() = smokeTest(
        goLang,
        "package main\nimport \"fmt\"\nfunc main() { fmt.Println(42) }"
    )

    @Test fun groovySmoke() = smokeTest(groovy, "println 'Hello Groovy'")

    @Test fun haskellSmoke() = smokeTest(haskell, "main = putStrLn \"Hello\"")

    @Test fun haxeSmoke() = smokeTest(haxe, "class Main { static function main() {} }")

    @Test fun httpSmoke() = smokeTest(http, "GET /path HTTP/1.1\nHost: example.com")

    @Test fun idlSmoke() = smokeTest(idl, "pro hello\n  print, 'Hello'\nend")

    @Test fun jsSmoke() = smokeTest(javaScriptLegacy, "const x = () => 42;")

    @Test fun jinja2Smoke() = smokeTest(
        jinja2Legacy,
        "{% for item in items %}{{ item }}{% endfor %}"
    )

    @Test fun juliaSmoke() = smokeTest(julia, "function hello()\n  println(\"hello\")\nend")

    @Test fun liveScriptSmoke() = smokeTest(liveScript, "hello = -> console.log 'hi'")

    @Test fun luaSmoke() = smokeTest(lua, "function hello()\n  print('hello')\nend")

    @Test fun mathematicaSmoke() = smokeTest(mathematica, "f[x_] := x^2 + 1")

    @Test fun mboxSmoke() = smokeTest(mbox, "From sender@example.com\nSubject: Test\n\nBody")

    @Test fun mircSmoke() = smokeTest(mirc, "on *:TEXT:*:#:{ msg # hello }")

    @Test fun mlLikeSmoke() = smokeTest(oCaml, "let hello () = print_endline \"hello\"")

    @Test fun modelicaSmoke() = smokeTest(modelica, "model Hello\n  Real x;\nend Hello;")

    @Test fun mscgenSmoke() = smokeTest(mscgen, "msc { a -> b [label=\"hello\"]; }")

    @Test fun mumpsSmoke() = smokeTest(mumps, "HELLO ; comment\n W \"Hello\",!")

    @Test fun nginxSmoke() = smokeTest(nginx, "server { listen 80; server_name example.com; }")

    @Test fun nsisSmoke() = smokeTest(
        nsis,
        "Name \"Test\"\nOutFile \"test.exe\"\nSection\nSectionEnd"
    )

    @Test fun ntriplesSmoke() = smokeTest(
        ntriples,
        "<http://example.org/s> <http://example.org/p> \"hello\" ."
    )

    @Test fun octaveSmoke() = smokeTest(octave, "function y = square(x)\n  y = x.^2;\nendfunction")

    @Test fun ozSmoke() = smokeTest(oz, "declare X = 42 in {Browse X} end")

    @Test fun pascalSmoke() = smokeTest(pascal, "program Hello;\nbegin\n  writeln('Hello');\nend.")

    @Test fun pegjsSmoke() = smokeTest(pegjs, "start = 'hello' / 'world'")

    @Test fun perlSmoke() = smokeTest(perl, "#!/usr/bin/perl\nprint \"Hello\\n\";")

    @Test fun pigSmoke() = smokeTest(pig, "A = LOAD 'data' USING PigStorage(',');")

    @Test fun propertiesSmoke() = smokeTest(properties, "key=value\n# comment")

    @Test fun protobufSmoke() = smokeTest(
        protobuf,
        "syntax = \"proto3\";\nmessage Hello { string name = 1; }"
    )

    @Test fun pugSmoke() = smokeTest(pug, "doctype html\nhtml\n  head\n    title Hello")

    @Test fun puppetSmoke() = smokeTest(
        puppet,
        "class hello { notify { 'hi': message => 'hello' } }"
    )

    @Test fun powerShellSmoke() = smokeTest(
        powerShell,
        "function Hello { Write-Host \"Hello\" }\nHello"
    )

    @Test fun pythonSmoke() = smokeTest(pythonLegacy, "def hello():\n    print('hello')")

    @Test fun qSmoke() = smokeTest(q, "f:{x+y}\nf[1;2]")

    @Test fun rSmoke() = smokeTest(r, "hello <- function() { cat('hello\\n') }")

    @Test fun rpmSmoke() = smokeTest(
        rpmSpec,
        "Name: test\nVersion: 1.0\n%description\nTest package"
    )

    @Test fun rubySmoke() = smokeTest(ruby, "def hello\n  puts 'hello'\nend")

    @Test fun rustSmoke() = smokeTest(rustLegacy, "fn main() { println!(\"hello\"); }")

    @Test fun sasSmoke() = smokeTest(sas, "data test; x = 1; run;")

    @Test fun sassSmoke() = smokeTest(sassLegacy, "body\n  color: red\n  font-size: 14px")

    @Test fun schemeSmoke() = smokeTest(scheme, "(define (hello) (display \"hello\"))")

    @Test fun shellSmoke() = smokeTest(shell, "#!/bin/bash\necho \"hello world\"")

    @Test fun sieveSmoke() = smokeTest(
        sieve,
        "require \"fileinto\";\nif header :contains \"Subject\" \"test\" { fileinto \"test\"; }"
    )

    @Test fun smalltalkSmoke() = smokeTest(smalltalk, "'Hello World' printNl")

    @Test fun solrSmoke() = smokeTest(solr, "field:value AND other:test")

    @Test fun sparqlSmoke() = smokeTest(
        sparql,
        "SELECT ?name WHERE { ?person <http://xmlns.com/foaf/0.1/name> ?name }"
    )

    @Test fun spreadsheetSmoke() = smokeTest(spreadsheet, "=SUM(A1:A10)")

    @Test fun sqlSmoke() = smokeTest(standardSQL, "SELECT * FROM users WHERE id = 1;")

    @Test fun stexSmoke() = smokeTest(
        stex,
        "\\documentclass{article}\n\\begin{document}\nHello\n\\end{document}"
    )

    @Test fun stylusSmoke() = smokeTest(stylus, "body\n  color red\n  font-size 14px")

    @Test fun swiftSmoke() = smokeTest(swiftLang, "func hello() { print(\"hello\") }")

    @Test fun tclSmoke() = smokeTest(tcl, "proc hello {} { puts \"hello\" }")

    @Test fun textileSmoke() = smokeTest(textile, "h1. Hello World\n\n*bold* _italic_")

    @Test fun tiddlyWikiSmoke() = smokeTest(tiddlyWiki, "! Heading\n''bold'' //italic//")

    @Test fun tikiSmoke() = smokeTest(tiki, "! Heading\n__bold__ ''italic''")

    @Test fun tomlSmoke() = smokeTest(toml, "[package]\nname = \"test\"\nversion = \"1.0\"")

    @Test fun troffSmoke() = smokeTest(troff, ".TH TEST 1\n.SH NAME\ntest \\- a test")

    @Test fun ttcnSmoke() = smokeTest(ttcn, "module Test { type integer MyInt; }")

    @Test fun ttcnCfgSmoke() = smokeTest(ttcnCfg, "[LOGGING]\nLogFile := \"test.log\"")

    @Test fun turtleSmoke() = smokeTest(
        turtle,
        "@prefix ex: <http://example.org/> .\nex:s ex:p ex:o ."
    )

    @Test fun vbSmoke() = smokeTest(
        vb,
        "Module Hello\n  Sub Main()\n    Console.WriteLine(\"hello\")\n  End Sub\nEnd Module"
    )

    @Test fun vbScriptSmoke() = smokeTest(vbScript, "Dim x\nx = 42\nMsgBox x")

    @Test fun velocitySmoke() = smokeTest(velocity, "#set(\$x = 42)\n\$x")

    @Test fun verilogSmoke() = smokeTest(verilog, "module test; wire a; endmodule")

    @Test fun vhdlSmoke() = smokeTest(vhdl, "entity test is\nend entity test;")

    @Test fun wastSmoke() = smokeTest(
        wastLegacy,
        "(module (func (export \"add\") (param i32 i32) " +
            "(result i32) local.get 0 local.get 1 i32.add))"
    )

    @Test fun webIdlSmoke() = smokeTest(webIDL, "interface Foo { void bar(); };")

    @Test fun xQuerySmoke() = smokeTest(
        xQuery,
        "for \$x in doc('books.xml')//book return \$x/title"
    )

    @Test fun xmlSmoke() = smokeTest(xmlLegacy, "<root><child attr=\"value\">text</child></root>")

    @Test fun htmlSmoke() = smokeTest(html, "<html><body><h1>Hello</h1></body></html>")

    @Test fun yacasSmoke() = smokeTest(yacas, "f(x) := x^2 + 1;")

    @Test fun yamlSmoke() = smokeTest(
        yamlLegacy,
        "name: test\nversion: 1.0\nitems:\n  - one\n  - two"
    )

    @Test fun z80Smoke() = smokeTest(z80, "LD A, 42\nADD A, B\nRET")

    @Test
    fun diffHasTokens() {
        val lang = StreamLanguage.define(diff)
        val state = EditorState.create(
            EditorStateConfig(
                doc = "+added\n-removed\n@@ context".asDoc(),
                extensions = lang.extension
            )
        )
        val tree = syntaxTree(state)
        val tokenNames = mutableSetOf<String>()
        val cursor = tree.cursor()
        while (cursor.next()) {
            tokenNames.add(cursor.type.name)
        }
        assertTrue(
            tokenNames.any { it.isNotEmpty() && it != "Document" },
            "Expected token types besides Document, got: $tokenNames"
        )
    }
}
