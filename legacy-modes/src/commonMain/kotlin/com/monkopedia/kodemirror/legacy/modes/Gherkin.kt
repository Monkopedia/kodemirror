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

import com.monkopedia.kodemirror.language.StreamParser
import com.monkopedia.kodemirror.language.StringStream

data class GherkinState(
    var lineNumber: Int = 0,
    var tableHeaderLine: Boolean = false,
    var allowFeature: Boolean = true,
    var allowBackground: Boolean = false,
    var allowScenario: Boolean = false,
    var allowSteps: Boolean = false,
    var allowPlaceholders: Boolean = false,
    var allowMultilineArgument: Boolean = false,
    var inMultilineString: Boolean = false,
    var inMultilineTable: Boolean = false,
    var inKeywordLine: Boolean = false,
    var inStep: Boolean = false
)

@Suppress("MaxLineLength")
private val gherkinFeatureRE = Regex(
    "^(Feature|Ability|Ahoy matey!|Arwedd|Aspekt|Business Need|Caracteristica|Característica|Egenskap|Egenskab|Eiginleiki|Fitur|Fīča|Fonctionnalité|Funcionalidade|Funcionalitat|Functionalitate|Funcionalidade|Funcționalitate|Funcţionalitate|Functionaliteit|Funkcija|Funkcionalitāte|Funkcionalnost|Funkcia|Funkcja|Funktionalitéit|Funktionalität|Funzionalità|Fungsi|Hwaet|Hwæt|Jellemző|Mogucnost|Mogućnost|Möglichkeit|OH HAI|Omadus|Ominaisuus|Osobina|Pretty much|Požadavek|Požiadavka|Potrzeba biznesowa|Savybė|Trajto|Tính năng|Vlastnosť|Właściwość|Özellik|Üzenčälеklеlеk|Δυνατότητα|Λειτουργία|Могућност|Мөмкинлек|Мүмкінлік|Особина|Свойство|Функционал|Функционалност|Функціонал|Функция|וִיژگی|تکنالوژی|خاصية|خصوصیت|기능|โครงหลัก|ความต้องการทางธุรกิจ|ความสามารถ|ಹೆಚ್ಚಳ|గుణము|ਖਾਸੀਅਤ|ਨਕਸ਼ ਨੁਹਾਰ|ਮੁਹਾਂਦਰਾ|रूप लेख|機能|功能|フィーチャ):"
)

@Suppress("MaxLineLength")
private val gherkinBackgroundRE = Regex(
    "^(Background|Achtergrond|Aer|Ær|Antecedentes|Antecedents|B4|Baggrund|Bakgrund|Bakgrunn|Bakgrunnur|Bối cảnh|Cefndir|Cenário de Fundo|Cenario de Fundo|Contesto|Context|Contexte|Contexto|Dasar|Dis is what went down|First off|Fondo|Fono|Geçmiş|Grundlage|Háttér|Hannergrond|Kontekst|Kontekstas|Konteksts|Kontext|Latar Belakang|Osnova|Pozadí|Pozadie|Pozadina|Rerefons|Situācija|Tausta|Taust|Yo-ho-ho|Υπόβαθρο|Кереш|Контекст|Позадина|Предистория|Предыстория|Основа|Передумова|Тарих|זקע|רקע|الخلفية|زمینه|पृष्ठभूमि|ਪਿਛੋਕੜ|నేపథ్యం|ಹಿನ್ನೆಲೆ|แนวคิด|배경|背景):"
)

@Suppress("MaxLineLength")
private val gherkinScenarioOutlineRE = Regex(
    "^(Scenario Outline|Scenario Template|Abstract Scenario|All y'all|Abstrakt Scenario|Delineação do Cenário|Delineacao do Cenario|Esquema de l'escenari|Esquema del escenario|Esquema do Cenário|Esquema do Cenario|Esbozo do escenario|Forgatókönyv vázlat|Khung kịch bản|Khung tình huống|Koncept|Konturo de la scenaro|Lýsing Atburðarásar|Lýsing Dæma|MISHUN SRSLY|Menggariskan Senario|Náčrt Scenára|Náčrt Scenáru|Náčrt Scénáře|Osnova scénáře|Osnova Scenára|Plan du Scénario|Plan du scénario|Plang vum Szenario|Raamstsenaarium|Reckon it's like|Scenariomall|Scenariomal|Scenario Amlinellol|Scenārijs pēc parauga|Scenarijaus šablonas|Schema dello scenario|Senaryo taslağı|Shiver me timbers|Skenario konsep|Skica|Struktura scenarija|Structură scenariu|Structura scenariu|Swa hwaer swa|Swa hwær swa|Szablon scenariusza|Szenariogrundriss|Tapausaihio|Template Keadaan|Template Senario|Template Situai|Wharrimean is|Περιγραφή Σεναρίου|Концепт|Рамка на сценарий|Скица|Структура сценарија|Структура сценария|Структура сценарію|Сценарийның төзелеше|Сценарий структураси|תבנית תרחיש|الگوی سناریو|سيناريو مخطط|परिदृश्य रूपरेखा|ਪਟਕਥਾ ਢਾਂਚਾ|ਪਟਕਥਾ ਰੂਪ ਰੇਖਾ|కథనం|ವಿವರಣೆ|โครงสร้างของเหตุการณ์|สรุปเหตุการณ์|시나리오 개요|シナリオアウトライン|シナリオテンプレ|シナリオテンプレート|テンプレ|劇本大綱|剧本大纲|場景大綱|场景大纲):"
)

@Suppress("MaxLineLength")
private val gherkinExamplesRE = Regex(
    "^(Examples|Scenarios|Atburðarásir|Beispiele|Beispiller|Cenários|Cenarios|Contoh|Dæmi|Dead men tell no tales|Dữ liệu|EXAMPLZ|Ekzemploj|Eksempler|Ejemplos|Esempio|Esempi|Exempel|Exemple|Exemples|Exemplos|Enghreifftiau|Juhtumid|Tapaukset|Variantai|Voorbeelden|You'll wanna|Örnekler|Παραδείγματα|Σενάρια|Мисаллар|Мисоллар|Примери|Примеры|Приклади|Сценарији|Үрнәкләр|דוגמאות|امثلة|نمونه ها|उदाहरण|ਉਦਾਹਰਨਾਂ|ఉదాహరణలు|ಉದಾಹರಣೆಗಳು|ชุดของตัวอย่าง|ชุดของเหตุการณ์|예|サンプル|例|例子|Piemēri|Példák|Pavyzdžiai|Paraugs|Příklady|Príklady|Primeri|Primjeri|Przykłady|Scenarijai|Scenariji|Se ðe|Se the|Se þe):"
)

@Suppress("MaxLineLength")
private val gherkinScenarioRE = Regex(
    "^(Scenario|Atburðarás|Awww, look mate|Cenário|Cenario|Escenari|Escenario|Forgatókönyv|Heave to|Keadaan|Kịch bản|MISHUN|Primer|Scenariusz|Scenariu|Scénario|Scenaro|Scenarijus|Scenārijs|Scenarij|Scenarie|Scénář|Scenár|Scenario|Senaryo|Senario|Situai|Skenario|Stsenaarium|Swa|Szenario|Tapaus|The thing of it is|Tình huống|Σενάριο|Пример|Сценарий|Сценарио|Сценарій|תרחיש|سيناريو|سناریو|परिदृश्य|ਪਟਕਥਾ|సన్నివేశం|ಕಥಾಸಾರಾಂಶ|เหตุการณ์|시나리오|シナリオ|劇本|剧本|場景|场景):"
)

@Suppress("MaxLineLength")
private val gherkinStepsRE = Regex(
    "^(\\* |Given |When |Then |And |But |Och |Og |Und |Und |Mais |Mas |Pero |Però |Pero |Lorsque |Lorsqu'|Soit |Étant donné |Etant donné |Étant donnée |Etant donnée |Étant données |Etant données |Étant donnés |Etant donnés |Cuando |Entonces |Y |E |Ir |Bet |Tada |Kai |Duota |En |Dan |Maar |Als |Gegeven |Stel |Когда |Тогда |Допустим |Дано |К тому же |Если |Но |И |А также |Але |Та |І |Коли |Тоді |Нехай |Припустимо |Дадено |Когато |Então |Entao |Dado |Dada |Dados |Dadas |Quando |E |Дано |Когда |Тогда |И |Но |Допустим |Если |А |А також |Але |Та |І |Коли |Тоді |Нехай |Припустимо, що |Und |Wenn |Dann |Gegeben sei |Gegeben seien |Angenommen |Aber |Ja |Oletetaan |Niin |Kun |Mutta |Ale |Pokud |Pak |A |Ale |Pokiaľ |Tak |A |Kuid |Siis |Eeldades |Ja |Kui |De |Da |Lorsque |Si |Alors |Soit |Étant donné |Etant donné |Apabila |Diberi |Kemudian |Tapi |Maka |Dan |Dengan |Bagi |Atunci |Și |Şi |Dar |Dați fiind |Daţi fiind |Dati fiind |Când |Cand |Dato |Date |Dati |Data |Dat fiind |Dat |Allora |Quando |Ma |E |那麼|那么|而且|當|当|并且|同時|同时|前提|假设|假設|假定|假如|但是|但し|並且|もし|ならば|ただし|しかし|かつ|하지만|조건|먼저|만일|만약|단|그리고|그러면|และ |เมื่อ |แต่ |ดังนั้น |กำหนดให้ |ಸ್ಥಿತಿಯನ್ನು |ಮತ್ತು |ನೀಡಿದ |ನಂತರ |ಆದರೆ |మరియు |చెప్పబడినది |కాని |ఈ పరిస్థితిలో |అప్పుడు |ਪਰ |ਤਦ |ਜੇਕਰ |ਜਿਵੇਂ ਕਿ |ਜਦੋਂ |ਅਤੇ |यदि |परन्तु |पर |तब |तदा |तथा |जब |चूंकि |किन्तु |कदा |और |अगर |و |هنگامی |متى |لكن |عندما |ثم |بفرض |با فرض |اما |اذاً |آنگاه |כאשר |וגם |בהינתן |אזי |אז |אבל )"
)

val gherkin: StreamParser<GherkinState> = object : StreamParser<GherkinState> {
    override val name: String get() = "gherkin"

    override fun startState(indentUnit: Int) = GherkinState()
    override fun copyState(state: GherkinState) = state.copy()

    @Suppress("CyclomaticComplexMethod", "LongMethod", "ReturnCount")
    override fun token(stream: StringStream, state: GherkinState): String? {
        if (stream.sol()) {
            state.lineNumber++
            state.inKeywordLine = false
            if (state.inMultilineTable) {
                state.tableHeaderLine = false
                if (stream.match(Regex("^\\s*\\|"), consume = false) == null) {
                    state.allowMultilineArgument = false
                    state.inMultilineTable = false
                }
            }
        }

        stream.eatSpace()

        if (state.allowMultilineArgument) {
            if (state.inMultilineString) {
                if (stream.match("\"\"\"")) {
                    state.inMultilineString = false
                    state.allowMultilineArgument = false
                } else {
                    stream.match(Regex("^.*"))
                }
                return "string"
            }

            if (state.inMultilineTable) {
                if (stream.match(Regex("^\\|\\s*")) != null) {
                    return "bracket"
                } else {
                    stream.match(Regex("^[^|]*"))
                    return if (state.tableHeaderLine) "header" else "string"
                }
            }

            if (stream.match("\"\"\"")) {
                state.inMultilineString = true
                return "string"
            } else if (stream.match("|")) {
                state.inMultilineTable = true
                state.tableHeaderLine = true
                return "bracket"
            }
        }

        if (stream.match(Regex("^#.*")) != null) {
            return "comment"
        } else if (!state.inKeywordLine && stream.match(Regex("^@\\S+")) != null) {
            return "tag"
        } else if (
            !state.inKeywordLine && state.allowFeature &&
            stream.match(gherkinFeatureRE) != null
        ) {
            state.allowScenario = true
            state.allowBackground = true
            state.allowPlaceholders = false
            state.allowSteps = false
            state.allowMultilineArgument = false
            state.inKeywordLine = true
            return "keyword"
        } else if (
            !state.inKeywordLine && state.allowBackground &&
            stream.match(gherkinBackgroundRE) != null
        ) {
            state.allowPlaceholders = false
            state.allowSteps = true
            state.allowBackground = false
            state.allowMultilineArgument = false
            state.inKeywordLine = true
            return "keyword"
        } else if (
            !state.inKeywordLine && state.allowScenario &&
            stream.match(gherkinScenarioOutlineRE) != null
        ) {
            state.allowPlaceholders = true
            state.allowSteps = true
            state.allowMultilineArgument = false
            state.inKeywordLine = true
            return "keyword"
        } else if (
            state.allowScenario &&
            stream.match(gherkinExamplesRE) != null
        ) {
            state.allowPlaceholders = false
            state.allowSteps = true
            state.allowBackground = false
            state.allowMultilineArgument = true
            return "keyword"
        } else if (
            !state.inKeywordLine && state.allowScenario &&
            stream.match(gherkinScenarioRE) != null
        ) {
            state.allowPlaceholders = false
            state.allowSteps = true
            state.allowBackground = false
            state.allowMultilineArgument = false
            state.inKeywordLine = true
            return "keyword"
        } else if (
            !state.inKeywordLine && state.allowSteps &&
            stream.match(gherkinStepsRE) != null
        ) {
            state.inStep = true
            state.allowPlaceholders = true
            state.allowMultilineArgument = true
            state.inKeywordLine = true
            return "keyword"
        } else if (stream.match(Regex("^\"[^\"]*\"?")) != null) {
            return "string"
        } else if (
            state.allowPlaceholders &&
            stream.match(Regex("^<[^>]*>?")) != null
        ) {
            return "variable"
        } else {
            stream.next()
            stream.eatWhile(Regex("[^@\"<#]"))
            return null
        }
    }
}
