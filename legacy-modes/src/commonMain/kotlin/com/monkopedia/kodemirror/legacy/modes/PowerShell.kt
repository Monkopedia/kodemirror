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

private val psNotCharOrDash = "(?=[^A-Za-z\\d\\-_]|$)"
private val psVarNames = Regex("[\\w\\-:]")

private val psKeywords = Regex(
    "^(?:begin|break|catch|continue|data|default|do|dynamicparam|" +
        "else|elseif|end|exit|filter|finally|for|foreach|from|function|if|in|" +
        "param|process|return|switch|throw|trap|try|until|where|while)" +
        psNotCharOrDash,
    RegexOption.IGNORE_CASE
)

private val psPunctuation = Regex("[\\[\\]{},;`\\\\.]|@[({]")

private val psWordOperators = Regex(
    "-(?:f|b?not|[ic]?split|join|is(?:not)?|as|[ic]?(?:eq|ne|[gl][te])|" +
        "[ic]?(?:not)?(?:like|match|contains)|[ic]?replace|b?(?:and|or|xor))" +
        psNotCharOrDash,
    RegexOption.IGNORE_CASE
)

private val psSymbolOperators =
    Regex("[+\\-*\\/%]=|\\+\\+|--|\\.\\.  |[+\\-*&^%:=!|/]|<(?!#)|(?!#)>")
private val psOperators = Regex(
    "^(?:-(?:f|b?not|[ic]?split|join|is(?:not)?|as|[ic]?(?:eq|ne|[gl][te])|" +
        "[ic]?(?:not)?(?:like|match|contains)|[ic]?replace|b?(?:and|or|xor))" +
        psNotCharOrDash +
        "|[+\\-*\\/%]=|\\+\\+|--|\\.\\.  |[+\\-*&^%:=!|/]|<(?!#)|(?!#)>)",
    RegexOption.IGNORE_CASE
)

private val psNumbers = Regex(
    "^((0x[\\da-f]+)|((\\d+\\.\\d+|\\d\\.|\\.\\d+|\\d+)(e[\\+\\-]?\\d+)?))[ld]?([kmgtp]b)?",
    RegexOption.IGNORE_CASE
)

private val psIdentifiers = Regex("^[A-Za-z_][A-Za-z\\-_\\d]*\\b")

@Suppress("ktlint:standard:max-line-length")
private val psBuiltins = Regex(
    "^(?:" +
        "Add-(?:Computer|Content|History|Member|PSSnapin|Type)|" +
        "Checkpoint-Computer|" +
        "Clear-(?:Content|EventLog|History|Host|Item(?:Property)?|Variable)|" +
        "Compare-Object|Complete-Transaction|Connect-PSSession|" +
        "ConvertFrom-(?:Csv|Json|SecureString|StringData)|Convert-Path|" +
        "ConvertTo-(?:Csv|Html|Json|SecureString|Xml)|" +
        "Copy-Item(?:Property)?|Debug-Process|" +
        "Disable-(?:ComputerRestore|PSBreakpoint|PSRemoting|PSSessionConfiguration)|" +
        "Disconnect-PSSession|" +
        "Enable-(?:ComputerRestore|PSBreakpoint|PSRemoting|PSSessionConfiguration)|" +
        "(?:Enter|Exit)-PSSession|" +
        "Export-(?:Alias|Clixml|Console|Counter|Csv|FormatData|ModuleMember|PSSession)|" +
        "ForEach-Object|Format-(?:Custom|List|Table|Wide)|" +
        "Get-(?:Acl|Alias|AuthenticodeSignature|ChildItem|Command|ComputerRestorePoint|Content|" +
        "ControlPanelItem|Counter|Credential|Culture|Date|Event|EventLog|EventSubscriber|" +
        "ExecutionPolicy|FormatData|Help|History|Host|HotFix|Item|ItemProperty|Job|Location|" +
        "Member|Module|PfxCertificate|Process|PSBreakpoint|PSCallStack|PSDrive|PSProvider|" +
        "PSSession|PSSessionConfiguration|PSSnapin|Random|Service|TraceSource|Transaction|" +
        "TypeData|UICulture|Unique|Variable|Verb|WinEvent|WmiObject)|" +
        "Group-Object|" +
        "Import-(?:Alias|Clixml|Counter|Csv|LocalizedData|Module|PSSession)|ImportSystemModules|" +
        "Invoke-(?:Command|Expression|History|Item|RestMethod|WebRequest|WmiMethod)|" +
        "Join-Path|Limit-EventLog|Measure-(?:Command|Object)|Move-Item(?:Property)?|" +
        "New-(?:Alias|Event|EventLog|Item(?:Property)?|Module|ModuleManifest|Object|PSDrive|" +
        "PSSession|PSSessionConfigurationFile|PSSessionOption|PSTransportOption|Service|" +
        "TimeSpan|Variable|WebServiceProxy|WinEvent)|" +
        "Out-(?:Default|File|GridView|Host|Null|Printer|String)|Pause|" +
        "(?:Pop|Push)-Location|Read-Host|Receive-(?:Job|PSSession)|" +
        "Register-(?:EngineEvent|ObjectEvent|PSSessionConfiguration|WmiEvent)|" +
        "Remove-(?:Computer|Event|EventLog|Item(?:Property)?|Job|Module|PSBreakpoint|PSDrive|" +
        "PSSession|PSSnapin|TypeData|Variable|WmiObject)|" +
        "Rename-(?:Computer|Item(?:Property)?)|Reset-ComputerMachinePassword|" +
        "Resolve-Path|Restart-(?:Computer|Service)|Restore-Computer|" +
        "Resume-(?:Job|Service)|Save-Help|Select-(?:Object|String|Xml)|Send-MailMessage|" +
        "Set-(?:Acl|Alias|AuthenticodeSignature|Content|Date|ExecutionPolicy|Item(?:Property)?|" +
        "Location|PSBreakpoint|PSDebug|PSSessionConfiguration|Service|StrictMode|TraceSource|" +
        "Variable|WmiInstance)|" +
        "Show-(?:Command|ControlPanelItem|EventLog)|Sort-Object|Split-Path|" +
        "Start-(?:Job|Process|Service|Sleep|Transaction|Transcript)|" +
        "Stop-(?:Computer|Job|Process|Service|Transcript)|Suspend-(?:Job|Service)|" +
        "TabExpansion2|Tee-Object|" +
        "Test-(?:ComputerSecureChannel|Connection|ModuleManifest|Path|PSSessionConfigurationFile)|" +
        "Trace-Command|Unblock-File|Undo-Transaction|" +
        "Unregister-(?:Event|PSSessionConfiguration)|" +
        "Update-(?:FormatData|Help|List|TypeData)|Use-Transaction|" +
        "Wait-(?:Event|Job|Process)|Where-Object|" +
        "Write-(?:Debug|Error|EventLog|Host|Output|Progress|Verbose|Warning)|" +
        "cd|help|mkdir|more|oss|prompt|" +
        "ac|asnp|cat|chdir|clc|clear|clhy|cli|clp|cls|clv|cnsn|compare|copy|cp|cpi|cpp|cvpa|" +
        "dbp|del|diff|dir|dnsn|ebp|echo|epal|epcsv|epsn|erase|etsn|exsn|fc|fl|foreach|ft|fw|" +
        "gal|gbp|gc|gci|gcm|gcs|gdr|ghy|gi|gjb|gl|gm|gmo|gp|gps|group|gsn|gsnp|gsv|gu|gv|" +
        "gwmi|h|history|icm|iex|ihy|ii|ipal|ipcsv|ipmo|ipsn|irm|ise|iwmi|iwr|kill|lp|ls|" +
        "man|md|measure|mi|mount|move|mp|mv|nal|ndr|ni|nmo|npssc|nsn|nv|ogv|oh|popd|ps|pushd|" +
        "pwd|r|rbp|rcjb|rcsn|rd|rdr|ren|ri|rjb|rm|rmdir|rmo|rni|rnp|rp|rsn|rsnp|rujb|rv|" +
        "rvpa|rwmi|sajb|sal|saps|sasv|sbp|sc|select|set|shcm|si|sl|sleep|sls|sort|sp|spjb|" +
        "spps|spsv|start|sujb|sv|swmi|tee|trcm|type|where|wjb|write|" +
        // symbol builtins
        "[A-Z]:|%|\\?" +
        // variable builtins
        "|\\$(?:[\\$?^_]|Args|ConfirmPreference|ConsoleFileName|DebugPreference|Error|" +
        "ErrorActionPreference|ErrorView|ExecutionContext|FormatEnumerationLimit|Home|Host|" +
        "Input|MaximumAliasCount|MaximumDriveCount|MaximumErrorCount|MaximumFunctionCount|" +
        "MaximumHistoryCount|MaximumVariableCount|MyInvocation|NestedPromptLevel|" +
        "OutputEncoding|Pid|Profile|ProgressPreference|PSBoundParameters|PSCommandPath|" +
        "PSCulture|PSDefaultParameterValues|PSEmailServer|PSHome|PSScriptRoot|" +
        "PSSessionApplicationName|PSSessionConfigurationName|PSSessionOption|PSUICulture|" +
        "PSVersionTable|Pwd|ShellId|StackTrace|VerbosePreference|WarningPreference|" +
        "WhatIfPreference|Event|EventArgs|EventSubscriber|Sender|Matches|Ofs|ForEach|" +
        "LastExitCode|PSCmdlet|PSItem|PSSenderInfo|This|true|false|null)" +
        psNotCharOrDash + ")",
    RegexOption.IGNORE_CASE
)

data class PsReturnItem(
    val shouldReturnFrom: (PowerShellState) -> Boolean,
    val tokenize: (StringStream, PowerShellState) -> String?
)

data class PowerShellState(
    var returnStack: MutableList<PsReturnItem> = mutableListOf(),
    var bracketNesting: Int = 0,
    var tokenize: (StringStream, PowerShellState) -> String? = ::psTokenBase,
    var startQuote: String = ""
)

private fun psTokenBase(stream: StringStream, state: PowerShellState): String? {
    val parent = state.returnStack.lastOrNull()
    if (parent != null && parent.shouldReturnFrom(state)) {
        state.tokenize = parent.tokenize
        state.returnStack.removeLast()
        return state.tokenize(stream, state)
    }

    if (stream.eatSpace()) return null

    if (stream.eat("(") != null) {
        state.bracketNesting += 1
        return "punctuation"
    }
    if (stream.eat(")") != null) {
        state.bracketNesting -= 1
        return "punctuation"
    }

    if (stream.match(psBuiltins) != null) return "builtin"
    if (stream.match(psKeywords) != null) return "keyword"
    if (stream.match(psNumbers) != null) return "number"
    if (stream.match(psOperators) != null) return "operator"
    if (stream.match(psPunctuation) != null) return "punctuation"
    if (stream.match(psIdentifiers) != null) return "variable"

    val ch = stream.next() ?: return null

    if (ch == "'") return psTokenSingleQuoteString(stream, state)
    if (ch == "\$") return psTokenVariable(stream, state)
    if (ch == "\"") return psTokenDoubleQuoteString(stream, state)
    if (ch == "<" && stream.eat("#") != null) {
        state.tokenize = ::psTokenComment
        return psTokenComment(stream, state)
    }
    if (ch == "#") {
        stream.skipToEnd()
        return "comment"
    }
    if (ch == "@") {
        val quoteMatch = stream.eat(Regex("[\"']"))
        if (quoteMatch != null && stream.eol()) {
            state.tokenize = ::psTokenMultiString
            state.startQuote = quoteMatch
            return psTokenMultiString(stream, state)
        } else if (stream.eol()) {
            return "error"
        } else if (stream.peek()?.let { Regex("[({]").containsMatchIn(it) } == true) {
            return "punctuation"
        } else if (stream.peek()?.let { psVarNames.containsMatchIn(it) } == true) {
            return psTokenVariable(stream, state)
        }
    }
    return "error"
}

private fun psTokenSingleQuoteString(stream: StringStream, state: PowerShellState): String {
    var ch: String?
    while (stream.peek().also { ch = it } != null) {
        stream.next()
        if (ch == "'" && stream.eat("'") == null) {
            state.tokenize = ::psTokenBase
            return "string"
        }
    }
    return "error"
}

private fun psTokenDoubleQuoteString(stream: StringStream, state: PowerShellState): String {
    var ch: String?
    while (stream.peek().also { ch = it } != null) {
        if (ch == "\$") {
            state.tokenize = ::psTokenStringInterpolation
            return "string"
        }
        stream.next()
        if (ch == "`") {
            stream.next()
            continue
        }
        if (ch == "\"" && stream.eat("\"") == null) {
            state.tokenize = ::psTokenBase
            return "string"
        }
    }
    return "error"
}

private fun psTokenStringInterpolation(stream: StringStream, state: PowerShellState): String? {
    return psTokenInterpolation(stream, state, ::psTokenDoubleQuoteString)
}

private fun psTokenMultiStringReturn(stream: StringStream, state: PowerShellState): String? {
    state.tokenize = ::psTokenMultiString
    state.startQuote = "\""
    return psTokenMultiString(stream, state)
}

private fun psTokenHereStringInterpolation(stream: StringStream, state: PowerShellState): String? {
    return psTokenInterpolation(stream, state, ::psTokenMultiStringReturn)
}

private fun psTokenInterpolation(
    stream: StringStream,
    state: PowerShellState,
    parentTokenize: (StringStream, PowerShellState) -> String?
): String? {
    if (stream.match("\$(") != null) {
        val savedBracketNesting = state.bracketNesting
        state.returnStack.add(
            PsReturnItem(
                shouldReturnFrom = { s -> s.bracketNesting == savedBracketNesting },
                tokenize = parentTokenize
            )
        )
        state.tokenize = ::psTokenBase
        state.bracketNesting += 1
        return "punctuation"
    } else {
        stream.next()
        state.returnStack.add(
            PsReturnItem(
                shouldReturnFrom = { _ -> true },
                tokenize = parentTokenize
            )
        )
        state.tokenize = ::psTokenVariable
        return state.tokenize(stream, state)
    }
}

private fun psTokenComment(stream: StringStream, state: PowerShellState): String {
    var maybeEnd = false
    var ch: String?
    while (stream.next().also { ch = it } != null) {
        if (maybeEnd && ch == ">") {
            state.tokenize = ::psTokenBase
            break
        }
        maybeEnd = ch == "#"
    }
    return "comment"
}

private fun psTokenVariable(stream: StringStream, state: PowerShellState): String? {
    val ch = stream.peek()
    if (stream.eat("{") != null) {
        state.tokenize = ::psTokenVariableWithBraces
        return psTokenVariableWithBraces(stream, state)
    } else if (ch != null && psVarNames.containsMatchIn(ch)) {
        stream.eatWhile(psVarNames)
        state.tokenize = ::psTokenBase
        return "variable"
    } else {
        state.tokenize = ::psTokenBase
        return "error"
    }
}

private fun psTokenVariableWithBraces(stream: StringStream, state: PowerShellState): String? {
    var ch: String?
    while (stream.next().also { ch = it } != null) {
        if (ch == "}") {
            state.tokenize = ::psTokenBase
            break
        }
    }
    return "variable"
}

private fun psTokenMultiString(stream: StringStream, state: PowerShellState): String {
    val quote = state.startQuote
    if (stream.sol() && stream.match(Regex("^${Regex.escape(quote)}@")) != null) {
        state.tokenize = ::psTokenBase
    } else if (quote == "\"") {
        while (!stream.eol()) {
            val ch = stream.peek()
            if (ch == "\$") {
                state.tokenize = ::psTokenHereStringInterpolation
                return "string"
            }
            stream.next()
            if (ch == "`") {
                stream.next()
            }
        }
    } else {
        stream.skipToEnd()
    }
    return "string"
}

val powerShell: StreamParser<PowerShellState> = object : StreamParser<PowerShellState> {
    override val name: String get() = "powershell"

    override fun startState(indentUnit: Int) = PowerShellState()

    override fun copyState(state: PowerShellState) = PowerShellState(
        returnStack = state.returnStack.toMutableList(),
        bracketNesting = state.bracketNesting,
        tokenize = state.tokenize,
        startQuote = state.startQuote
    )

    override fun token(stream: StringStream, state: PowerShellState): String? {
        return state.tokenize(stream, state)
    }

    override val languageData: Map<String, Any>
        get() = mapOf(
            "commentTokens" to mapOf(
                "line" to "#",
                "block" to mapOf("open" to "<#", "close" to "#>")
            )
        )
}
