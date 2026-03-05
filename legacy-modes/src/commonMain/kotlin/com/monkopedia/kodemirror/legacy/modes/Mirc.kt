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

@Suppress("ktlint:standard:max-line-length")
private val mircSpecials = (
    "\$! \$\$ \$& \$? \$+ \$abook \$abs \$active \$activecid " +
        "\$activewid \$address \$addtok \$agent \$agentname \$agentstat \$agentver " +
        "\$alias \$and \$anick \$ansi2mirc \$aop \$appactive \$appstate \$asc \$asctime " +
        "\$asin \$atan \$avoice \$away \$awaymsg \$awaytime \$banmask \$base \$bfind " +
        "\$binoff \$biton \$bnick \$bvar \$bytes \$calc \$cb \$cd \$ceil \$chan \$chanmodes " +
        "\$chantypes \$chat \$chr \$cid \$clevel \$click \$cmdbox \$cmdline \$cnick \$color " +
        "\$com \$comcall \$comchan \$comerr \$compact \$compress \$comval \$cos \$count " +
        "\$cr \$crc \$creq \$crlf \$ctime \$ctimer \$ctrlenter \$date \$day \$daylight " +
        "\$dbuh \$dbuw \$dccignore \$dccport \$dde \$ddename \$debug \$decode \$decompress " +
        "\$deltok \$devent \$dialog \$did \$didreg \$didtok \$didwm \$disk \$dlevel \$dll " +
        "\$dllcall \$dname \$dns \$duration \$ebeeps \$editbox \$emailaddr \$encode \$error " +
        "\$eval \$event \$exist \$feof \$ferr \$fgetc \$file \$filename \$filtered \$finddir " +
        "\$finddirn \$findfile \$findfilen \$findtok \$fline \$floor \$fopen \$fread \$fserve " +
        "\$fulladdress \$fulldate \$fullname \$fullscreen \$get \$getdir \$getdot \$gettok \$gmt " +
        "\$group \$halted \$hash \$height \$hfind \$hget \$highlight \$hnick \$hotline " +
        "\$hotlinepos \$ial \$ialchan \$ibl \$idle \$iel \$ifmatch \$ignore \$iif \$iil " +
        "\$inelipse \$ini \$inmidi \$inpaste \$inpoly \$input \$inrect \$inroundrect " +
        "\$insong \$instok \$int \$inwave \$ip \$isalias \$isbit \$isdde \$isdir \$isfile " +
        "\$isid \$islower \$istok \$isupper \$keychar \$keyrpt \$keyval \$knick \$lactive " +
        "\$lactivecid \$lactivewid \$left \$len \$level \$lf \$line \$lines \$link \$lock " +
        "\$locked \$log \$logstamp \$logstampfmt \$longfn \$longip \$lower \$ltimer " +
        "\$maddress \$mask \$matchkey \$matchtok \$md5 \$me \$menu \$menubar \$menucontext " +
        "\$menutype \$mid \$middir \$mircdir \$mircexe \$mircini \$mklogfn \$mnick \$mode " +
        "\$modefirst \$modelast \$modespl \$mouse \$msfile \$network \$newnick \$nick \$nofile " +
        "\$nopath \$noqt \$not \$notags \$notify \$null \$numeric \$numok \$oline \$onpoly " +
        "\$opnick \$or \$ord \$os \$passivedcc \$pic \$play \$pnick \$port \$portable \$portfree " +
        "\$pos \$prefix \$prop \$protect \$puttok \$qt \$query \$rand \$r \$rawmsg \$read \$readomo " +
        "\$readn \$regex \$regml \$regsub \$regsubex \$remove \$remtok \$replace \$replacex " +
        "\$reptok \$result \$rgb \$right \$round \$scid \$scon \$script \$scriptdir \$scriptline " +
        "\$sdir \$send \$server \$serverip \$sfile \$sha1 \$shortfn \$show \$signal \$sin " +
        "\$site \$sline \$snick \$snicks \$snotify \$sock \$sockbr \$sockerr \$sockname " +
        "\$sorttok \$sound \$sqrt \$ssl \$sreq \$sslready \$status \$strip \$str \$stripped " +
        "\$syle \$submenu \$switchbar \$tan \$target \$ticks \$time \$timer \$timestamp " +
        "\$timestampfmt \$timezone \$tip \$titlebar \$toolbar \$treebar \$trust \$ulevel " +
        "\$ulist \$upper \$uptime \$url \$usermode \$v1 \$v2 \$var \$vcmd \$vcmdstat \$vcmdver " +
        "\$version \$vnick \$vol \$wid \$width \$wildsite \$wildtok \$window \$wrap \$xor"
    ).split(" ").filter { it.isNotEmpty() }.toSet()

@Suppress("ktlint:standard:max-line-length")
private val mircKeywords = (
    "abook ajinvite alias aline ame amsg anick aop auser autojoin avoice " +
        "away background ban bcopy beep bread break breplace bset btrunc bunset bwrite " +
        "channel clear clearall cline clipboard close cnick color comclose comopen " +
        "comreg continue copy creq ctcpreply ctcps dcc dccserver dde ddeserver " +
        "debug dec describe dialog did didtok disable disconnect dlevel dline dll " +
        "dns dqwindow drawcopy drawdot drawfill drawline drawpic drawrect drawreplace " +
        "drawrot drawsave drawscroll drawtext ebeeps echo editbox emailaddr enable " +
        "events exit fclose filter findtext finger firewall flash flist flood flush " +
        "flushini font fopen fseek fsend fserve fullname fwrite ghide gload gmove " +
        "gopts goto gplay gpoint gqreq groups gshow gsize gstop gtalk gunload hadd " +
        "halt haltdef hdec hdel help hfree hinc hload hmake hop hsave ial ialclear " +
        "ialmark identd if ignore iline inc invite iuser join kick linesep links list " +
        "load loadbuf localinfo log mdi me menubar mkdir mnick mode msg nick noop notice " +
        "notify omsg onotice part partall pdcc perform play playctrl pop protect pvoice " +
        "qme qmsg query queryn quit raw reload remini remote remove rename renwin " +
        "reseterror resetidle return rlevel rline rmdir run ruser save savebuf saveini " +
        "say scid scon server set showmirc signam sline sockaccept sockclose socklist " +
        "socklisten sockmark sockopen sockpause sockread sockrename sockudp sockwrite " +
        "sound speak splay sreq strip switchbar timer timestamp titlebar tnick tokenize " +
        "toolbar topic tray treebar ulist unload unset unsetall updatenl url uwho " +
        "var vcadd vcmd vcrem vol while whois window winhelp write writeint if isalnum " +
        "isalpha isaop isavoice isban ischan ishop isignore isin isincs isletter islower " +
        "isnotify isnum ison isop isprotect isreg isupper isvoice iswm iswmcs " +
        "elseif else goto menu nicklist status title icon size option text edit " +
        "button check radio box scroll list combo link tab item"
    ).split(" ").filter { it.isNotEmpty() }.toSet()

private val mircFunctions = setOf(
    "if", "elseif", "else", "and", "not", "or", "eq", "ne", "in", "ni",
    "for", "foreach", "while", "switch"
)

private val mircIsOperatorChar = Regex("[+\\-*&%=<>!?^/|]")

data class MircState(
    var tokenize: (StringStream, MircState) -> String? = ::mircTokenBase,
    var beforeParams: Boolean = false,
    var inParams: Boolean = false
)

@Suppress("CyclomaticComplexMethod", "ReturnCount")
private fun mircTokenBase(stream: StringStream, state: MircState): String? {
    val beforeParams = state.beforeParams
    state.beforeParams = false
    val ch = stream.next() ?: return null

    if (Regex("[\\[\\]{}(),.]").containsMatchIn(ch)) {
        if (ch == "(" && beforeParams) {
            state.inParams = true
        } else if (ch == ")") state.inParams = false
        return null
    }
    if (Regex("\\d").containsMatchIn(ch)) {
        stream.eatWhile(Regex("[\\w.]"))
        return "number"
    }
    if (ch == "\\") {
        stream.eat("\\")
        stream.eat(Regex("."))
        return "number"
    }
    if (ch == "/" && stream.eat("*") != null) {
        state.tokenize = ::mircTokenComment
        return mircTokenComment(stream, state)
    }
    if (ch == ";" && stream.match(Regex("^ *\\( *\\(")) != null) {
        state.tokenize = ::mircTokenUnparsed
        return mircTokenUnparsed(stream, state)
    }
    if (ch == ";" && !state.inParams) {
        stream.skipToEnd()
        return "comment"
    }
    if (ch == "\"") {
        stream.eat(Regex("\""))
        return "keyword"
    }
    if (ch == "$") {
        stream.eatWhile(Regex("[\$_a-z0-9A-Z.:]"))
        val cur = stream.current().lowercase()
        if (cur in mircSpecials) {
            return "keyword"
        }
        state.beforeParams = true
        return "builtin"
    }
    if (ch == "%") {
        stream.eatWhile(Regex("[^,\\s()]"))
        state.beforeParams = true
        return "string"
    }
    if (mircIsOperatorChar.containsMatchIn(ch)) {
        stream.eatWhile(mircIsOperatorChar)
        return "operator"
    }
    stream.eatWhile(Regex("[\\w\$_{}]"))
    val word = stream.current().lowercase()
    if (word in mircKeywords) return "keyword"
    if (word in mircFunctions) {
        state.beforeParams = true
        return "keyword"
    }
    return null
}

private fun mircTokenComment(stream: StringStream, state: MircState): String {
    var maybeEnd = false
    while (true) {
        val ch = stream.next() ?: break
        if (ch == "/" && maybeEnd) {
            state.tokenize = ::mircTokenBase
            break
        }
        maybeEnd = ch == "*"
    }
    return "comment"
}

private fun mircTokenUnparsed(stream: StringStream, state: MircState): String {
    var maybeEnd = 0
    while (true) {
        val ch = stream.next() ?: break
        if (ch == ";" && maybeEnd == 2) {
            state.tokenize = ::mircTokenBase
            break
        }
        if (ch == ")") {
            maybeEnd++
        } else if (ch != " ") maybeEnd = 0
    }
    return "meta"
}

val mirc: StreamParser<MircState> = object : StreamParser<MircState> {
    override val name: String get() = "mirc"

    override fun startState(indentUnit: Int) = MircState()
    override fun copyState(state: MircState) = state.copy()

    override fun token(stream: StringStream, state: MircState): String? {
        if (stream.eatSpace()) return null
        return state.tokenize(stream, state)
    }
}
