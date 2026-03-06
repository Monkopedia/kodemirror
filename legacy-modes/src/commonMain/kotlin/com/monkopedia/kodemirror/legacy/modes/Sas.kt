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

private data class SasWord(val style: String, val state: List<String>)

private val sasWords: MutableMap<String, SasWord> = mutableMapOf()

private val sasIsDoubleOperatorSym = mapOf(
    "eq" to "operator",
    "lt" to "operator",
    "le" to "operator",
    "gt" to "operator",
    "ge" to "operator",
    "in" to "operator",
    "ne" to "operator",
    "or" to "operator"
)
private val sasIsDoubleOperatorChar = Regex("^(<=|>=|!=|<>)")
private val sasSingleOperatorChar = Regex("[=(:\\),{}.*<>+\\-/^\\[\\]]")

private fun sasDefine(style: String, string: String, context: List<String>) {
    val split = string.split(' ')
    for (word in split) {
        if (word.isNotEmpty()) {
            sasWords[word] = SasWord(style = style, state = context)
        }
    }
}

// Initialize SAS word tables
private val sasDefined: Unit = run {
    // datastep
    sasDefine("def", "stack pgm view source debug nesting nolist", listOf("inDataStep"))
    sasDefine(
        "def",
        "if while until for do do; end end; then else cancel",
        listOf("inDataStep")
    )
    sasDefine("def", "label format _n_ _error_", listOf("inDataStep"))
    sasDefine(
        "def",
        "ALTER BUFNO BUFSIZE CNTLLEV COMPRESS DLDMGACTION ENCRYPT ENCRYPTKEY " +
            "EXTENDOBSCOUNTER GENMAX GENNUM INDEX LABEL OBSBUF OUTREP PW PWREQ READ " +
            "REPEMPTY REPLACE REUSE ROLE SORTEDBY SPILL TOBSNO TYPE WRITE FILECLOSE " +
            "FIRSTOBS IN OBS POINTOBS WHERE WHEREUP IDXNAME IDXWHERE DROP KEEP RENAME",
        listOf("inDataStep")
    )
    sasDefine(
        "def",
        "filevar finfo finv fipname fipnamel fipstate first firstobs floor",
        listOf("inDataStep")
    )
    sasDefine(
        "def",
        "varfmt varinfmt varlabel varlen varname varnum varray varrayx vartype verify " +
            "vformat vformatd vformatdx vformatn vformatnx vformatw vformatwx vformatx " +
            "vinarray vinarrayx vinformat vinformatd vinformatdx vinformatn vinformatnx " +
            "vinformatw vinformatwx vinformatx vlabel vlabelx vlength vlengthx vname " +
            "vnamex vnferr vtype vtypex weekday",
        listOf("inDataStep")
    )
    sasDefine("def", "zipfips zipname zipnamel zipstate", listOf("inDataStep"))
    sasDefine("def", "put putc putn", listOf("inDataStep"))
    sasDefine("builtin", "data run", listOf("inDataStep"))

    // proc
    sasDefine("def", "data", listOf("inProc"))

    // flow control for macros
    sasDefine("def", "%if %end %end; %else %else; %do %do; %then", listOf("inMacro"))

    // everywhere
    sasDefine(
        "builtin",
        "proc run; quit; libname filename %macro %mend option options",
        listOf("ALL")
    )
    sasDefine("def", "footnote title libname ods", listOf("ALL"))
    sasDefine("def", "%let %put %global %sysfunc %eval ", listOf("ALL"))
    sasDefine(
        "variable",
        "&sysbuffr &syscc &syscharwidth &syscmd &sysdate &sysdate9 &sysday &sysdevic " +
            "&sysdmg &sysdsn &sysencoding &sysenv &syserr &syserrortext &sysfilrc " +
            "&syshostname &sysindex &sysinfo &sysjobid &syslast &syslckrc &syslibrc " +
            "&syslogapplname &sysmacroname &sysmenv &sysmsg &sysncpu &sysodspath &sysparm " +
            "&syspbuff &sysprocessid &sysprocessname &sysprocname &sysrc &sysscp &sysscpl " +
            "&sysscpl &syssite &sysstartid &sysstartname &systcpiphostname &systime " +
            "&sysuserid &sysver &sysvlong &sysvlong4 &syswarningtext",
        listOf("ALL")
    )
    sasDefine("def", "source2 nosource2 page pageno pagesize", listOf("ALL"))
    sasDefine(
        "def",
        "_all_ _character_ _cmd_ _freq_ _i_ _infile_ _last_ _msg_ _null_ _numeric_ " +
            "_temporary_ _type_ abort abs addr adjrsq airy alpha alter altlog altprint " +
            "and arcos array arsin as atan attrc attrib attrn authserver autoexec " +
            "awscontrol awsdef awsmenu awsmenumerge awstitle backward band base betainv " +
            "between blocksize blshift bnot bor brshift bufno bufsize bxor by byerr byline " +
            "byte calculated call cards cards4 catcache cbufno cdf ceil center cexist " +
            "change chisq cinv class cleanup close cnonct cntllev coalesce codegen col " +
            "collate collin column comamid comaux1 comaux2 comdef compbl compound compress " +
            "config continue convert cos cosh cpuid create cross crosstab css curobs cv " +
            "daccdb daccdbsl daccsl daccsyd dacctab dairy datalines datalines4 datejul " +
            "datepart datetime day dbcslang dbcstype dclose ddfm ddm delete delimiter " +
            "depdb depdbsl depsl depsyd deptab dequote descending descript design= device " +
            "dflang dhms dif digamma dim dinfo display distinct dkricond dkrocond dlm dnum " +
            "do dopen doptname doptnum dread drop dropnote dsname dsnferr echo else " +
            "emaildlg emailid emailpw emailserver emailsys encrypt end endsas engine eof " +
            "eov erf erfc error errorcheck errors exist exp fappend fclose fcol fdelete " +
            "feedback fetch fetchobs fexist fget file fileclose fileexist filefmt filename " +
            "fileref fmterr fmtsearch fnonct fnote font fontalias fopen foptname foptnum " +
            "force formatted formchar formdelim formdlim forward fpoint fpos fput fread " +
            "frewind frlen from fsep fuzz fwrite gaminv gamma getoption getvarc getvarn " +
            "go goto group gwindow hbar hbound helpenv helploc hms honorappearance " +
            "hosthelp hostprint hour hpct html hvar ibessel ibr id if index indexc indexw " +
            "initcmd initstmt inner input inputc inputn inr insert int intck intnx into " +
            "intrr invaliddata irr is jbessel join juldate keep kentb kurtosis label lag " +
            "last lbound leave left length levels lgamma lib library libref line linesize " +
            "link list log log10 log2 logpdf logpmf logsdf lostcard lowcase lrecl ls " +
            "macro macrogen maps mautosource max maxdec maxr mdy mean measures median " +
            "memtype merge merror min minute missing missover mlogic mod mode model modify " +
            "month mopen mort mprint mrecall msglevel msymtabmax mvarsize myy n nest netpv " +
            "new news nmiss no nobatch nobs nocaps nocardimage nocenter nocharcode nocmdmac " +
            "nocol nocum nodate nodbcs nodetails nodmr nodms nodmsbatch nodup nodupkey " +
            "noduplicates noechoauto noequals noerrorabend noexitwindows nofullstimer " +
            "noicon noimplmac noint nolist noloadlist nomiss nomlogic nomprint nomrecall " +
            "nomsgcase nomstored nomultenvappl nonotes nonumber noobs noovp nopad nopercent " +
            "noprint noprintinit normal norow norsasuser nosetinit nosplash nosymbolgen " +
            "note notes notitle notitles notsorted noverbose noxsync noxwait npv null " +
            "number numkeys nummousekeys nway obs on open order ordinal otherwise out " +
            "outer outp= output over ovp p(1 5 10 25 50 75 90 95 99) pad pad2 paired parm " +
            "parmcards path pathdll pathname pdf peek peekc pfkey pmf point poisson poke " +
            "position printer probbeta probbnml probchi probf probgam probhypr probit " +
            "probnegb probnorm probsig probt procleave prt ps pw pwreq qtr quote r ranbin " +
            "rancau random ranexp rangam range ranks rannor ranpoi rantbl rantri ranuni " +
            "rcorr read recfm register regr remote remove rename repeat repeated replace " +
            "resolve retain return reuse reverse rewind right round rsquare rtf rtrace " +
            "rtraceloc s s2 samploc sasautos sascontrol sasfrscr sasmsg sasmstore " +
            "sasscript sasuser saving scan sdf second select selection separated seq " +
            "serror set setcomm setot sign simple sin sinh siteinfo skewness skip sle sls " +
            "sortedby sortpgm sortseq sortsize soundex spedis splashlocation split spool " +
            "sqrt start std stderr stdin stfips stimer stname stnamel stop stopover sub " +
            "subgroup subpopn substr sum sumwgt symbol symbolgen symget symput sysget sysin " +
            "sysleave sysmsg sysparm sysprint sysprintfont sysprod sysrc system t table " +
            "tables tan tanh tapeclose tbufsize terminal test then timepart tinv tnonct to " +
            "today tol tooldef totper transformout translate trantab tranwrd trigamma trim " +
            "trimn trunc truncover type unformatted uniform union until upcase update user " +
            "usericon uss validate value var weight when where while wincharset window work " +
            "workinit workterm write wsum xsync xwait yearcutoff yes yyq min max",
        listOf("inDataStep", "inProc")
    )
    sasDefine("operator", "and not ", listOf("inDataStep", "inProc"))
}

data class SasState(
    var inDataStep: Boolean = false,
    var inProc: Boolean = false,
    var inMacro: Boolean = false,
    var nextword: Boolean = false,
    var continueString: String? = null,
    var continueComment: Boolean = false
)

@Suppress("CyclomaticComplexMethod", "LongMethod", "ReturnCount", "NestedBlockDepth")
private fun sasTokenize(stream: StringStream, state: SasState): String? {
    val ch = stream.next() ?: return null

    // block comment
    if (ch == "/" && stream.eat("*") != null) {
        state.continueComment = true
        return "comment"
    } else if (state.continueComment) {
        if (ch == "*" && stream.peek() == "/") {
            stream.next()
            state.continueComment = false
        } else if (stream.skipTo("*")) {
            stream.next()
            if (stream.eat("/") != null) state.continueComment = false
        } else {
            stream.skipToEnd()
        }
        return "comment"
    }

    if (ch == "*" && stream.column() == stream.indentation()) {
        stream.skipToEnd()
        return "comment"
    }

    val doubleOperator = ch + (stream.peek() ?: "")

    if ((ch == "\"" || ch == "'") && state.continueString == null) {
        state.continueString = ch
        return "string"
    } else if (state.continueString != null) {
        if (state.continueString == ch) {
            state.continueString = null
        } else if (stream.skipTo(state.continueString!!)) {
            stream.next()
            state.continueString = null
        } else {
            stream.skipToEnd()
        }
        return "string"
    } else if (state.continueString != null && stream.eol()) {
        if (!stream.skipTo(state.continueString!!)) stream.skipToEnd()
        return "string"
    } else if (Regex("[\\d.]").containsMatchIn(ch)) {
        if (ch == ".") {
            stream.match(Regex("^[0-9]+([eE][\\-+]?[0-9]+)?"))
        } else if (ch == "0") {
            if (stream.match(Regex("^[xX][0-9a-fA-F]+")) == null) stream.match(Regex("^0[0-7]+"))
        } else {
            stream.match(Regex("^[0-9]*\\.?[0-9]*([eE][\\-+]?[0-9]+)?"))
        }
        return "number"
    } else if (sasIsDoubleOperatorChar.containsMatchIn(ch + (stream.peek() ?: ""))) {
        stream.next()
        return "operator"
    } else if (sasIsDoubleOperatorSym.containsKey(doubleOperator)) {
        stream.next()
        if (stream.peek() == " ") return sasIsDoubleOperatorSym[doubleOperator.lowercase()]
    } else if (sasSingleOperatorChar.containsMatchIn(ch)) {
        return "operator"
    }

    // match a word
    val word: String
    val matchResult = stream.match(Regex("[%&;\\w]+"), consume = false)
    word = if (matchResult != null) {
        ch + (stream.match(Regex("[%&;\\w]+"), consume = true) ?: "")
    } else {
        ch
    }

    if (Regex("&").containsMatchIn(word)) return "variable"

    if (state.nextword) {
        stream.match(Regex("[\\w]+"))
        if (stream.peek() == ".") stream.skipTo(" ")
        state.nextword = false
        return "variableName.special"
    }

    val wordLower = word.lowercase()

    if (state.inDataStep) {
        if (wordLower == "run;" || stream.match(Regex("run\\s;")) != null) {
            state.inDataStep = false
            return "builtin"
        }
        if (stream.next() == ".") {
            return if (stream.peek()?.let { Regex("\\w").containsMatchIn(it) } == true) {
                "variableName.special"
            } else {
                "variable"
            }
        }
        val sasWord = sasWords[wordLower]
        if (sasWord != null && ("inDataStep" in sasWord.state || "ALL" in sasWord.state)) {
            stream.backUp(stream.pos - stream.start)
            repeat(wordLower.length) { stream.next() }
            return sasWord.style
        }
    }

    if (state.inProc) {
        if (wordLower == "run;" || wordLower == "quit;") {
            state.inProc = false
            return "builtin"
        }
        val sasWord = sasWords[wordLower]
        if (sasWord != null && ("inProc" in sasWord.state || "ALL" in sasWord.state)) {
            stream.match(Regex("[\\w]+"))
            return sasWord.style
        }
    }

    if (state.inMacro) {
        if (wordLower == "%mend") {
            if (stream.peek() == ";") stream.next()
            state.inMacro = false
            return "builtin"
        }
        val sasWord = sasWords[wordLower]
        if (sasWord != null && ("inMacro" in sasWord.state || "ALL" in sasWord.state)) {
            stream.match(Regex("[\\w]+"))
            return sasWord.style
        }
        return "atom"
    }

    val sasWord = sasWords[wordLower]
    if (sasWord != null) {
        stream.backUp(1)
        stream.match(Regex("[\\w]+"))
        if (wordLower == "data" && stream.peek() != "=") {
            state.inDataStep = true
            state.nextword = true
            return "builtin"
        }
        if (wordLower == "proc") {
            state.inProc = true
            state.nextword = true
            return "builtin"
        }
        if (wordLower == "%macro") {
            state.inMacro = true
            state.nextword = true
            return "builtin"
        }
        if (Regex("title[1-9]").matches(wordLower)) return "def"
        if (wordLower == "footnote") {
            stream.eat(Regex("[1-9]"))
            return "def"
        }
        if (state.inDataStep && "inDataStep" in sasWord.state) return sasWord.style
        if (state.inProc && "inProc" in sasWord.state) return sasWord.style
        if (state.inMacro && "inMacro" in sasWord.state) return sasWord.style
        if ("ALL" in sasWord.state) return sasWord.style
        return null
    }

    return null
}

/** Stream parser for SAS. */
val sas: StreamParser<SasState> = object : StreamParser<SasState> {
    override val name: String get() = "sas"

    override fun startState(indentUnit: Int) = SasState()
    override fun copyState(state: SasState) = state.copy()

    override fun token(stream: StringStream, state: SasState): String? {
        if (stream.eatSpace()) return null
        return sasTokenize(stream, state)
    }

    override val languageData: Map<String, Any>
        get() = mapOf(
            "commentTokens" to mapOf(
                "block" to mapOf("open" to "/*", "close" to "*/")
            )
        )
}
