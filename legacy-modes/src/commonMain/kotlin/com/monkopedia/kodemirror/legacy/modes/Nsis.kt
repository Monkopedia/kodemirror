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

val nsis = simpleMode(
    SimpleModeConfig(
        states = mapOf(
            "start" to listOf(
                // Numbers
                SimpleModeRule(
                    regex = Regex(
                        "^(?:[+-]?)(?:0x[\\d,a-f]+)|(?:0o[0-7]+)" +
                            "|(?:0b[0,1]+)|(?:\\d+.?\\d*)"
                    ),
                    token = "number"
                ),
                // Strings
                SimpleModeRule(
                    regex = Regex("^\"(?:[^\\\\\"]|\\\\.)*\"?"),
                    token = "string"
                ),
                SimpleModeRule(
                    regex = Regex("^'(?:[^\\\\']|\\\\.)*'?"),
                    token = "string"
                ),
                SimpleModeRule(
                    regex = Regex("^`(?:[^\\\\`]|\\\\.)*`?"),
                    token = "string"
                ),
                // Compile Time Commands
                SimpleModeRule(
                    regex = Regex(
                        "^\\s*(?:!(addincludedir|addplugindir|appendfile|assert|cd|" +
                            "define|delfile|echo|error|execute|finalize|getdllversion|" +
                            "gettlbversion|include|insertmacro|macro|macroend|makensis|" +
                            "packhdr|pragma|searchparse|searchreplace|system|tempfile|" +
                            "undef|uninstfinalize|verbose|warning))\\b",
                        RegexOption.IGNORE_CASE
                    ),
                    token = "keyword",
                    sol = true
                ),
                // Conditional Compilation
                SimpleModeRule(
                    regex = Regex(
                        "^\\s*(?:!(if(?:n?def)?|ifmacron?def|macro))\\b",
                        RegexOption.IGNORE_CASE
                    ),
                    token = "keyword",
                    indent = true,
                    sol = true
                ),
                SimpleModeRule(
                    regex = Regex(
                        "^\\s*(?:!(else|endif|macroend))\\b",
                        RegexOption.IGNORE_CASE
                    ),
                    token = "keyword",
                    dedent = true,
                    sol = true
                ),
                // Runtime Commands
                @Suppress("ktlint:standard:max-line-length")
                SimpleModeRule(
                    regex = Regex(
                        "^\\s*(?:Abort|AddBrandingImage|AddSize|AllowRootDirInstall|" +
                            "AllowSkipFiles|AutoCloseWindow|BGFont|BGGradient|" +
                            "BrandingText|BringToFront|Call|CallInstDLL|Caption|" +
                            "ChangeUI|CheckBitmap|ClearErrors|CompletedText|" +
                            "ComponentText|CopyFiles|CRCCheck|CreateDirectory|" +
                            "CreateFont|CreateShortCut|Delete|DeleteINISec|" +
                            "DeleteINIStr|DeleteRegKey|DeleteRegValue|DetailPrint|" +
                            "DetailsButtonText|DirText|DirVar|DirVerify|EnableWindow|" +
                            "EnumRegKey|EnumRegValue|Exch|Exec|ExecShell|" +
                            "ExecShellWait|ExecWait|ExpandEnvStrings|File|FileBufSize|" +
                            "FileClose|FileErrorText|FileOpen|FileRead|FileReadByte|" +
                            "FileReadUTF16LE|FileReadWord|FileWriteUTF16LE|FileSeek|" +
                            "FileWrite|FileWriteByte|FileWriteWord|FindClose|" +
                            "FindFirst|FindNext|FindWindow|FlushINI|GetCurInstType|" +
                            "GetCurrentAddress|GetDlgItem|GetDLLVersion|" +
                            "GetDLLVersionLocal|GetErrorLevel|GetFileTime|" +
                            "GetFileTimeLocal|GetFullPathName|GetFunctionAddress|" +
                            "GetInstDirError|GetKnownFolderPath|GetLabelAddress|" +
                            "GetTempFileName|GetWinVer|Goto|HideWindow|Icon|IfAbort|" +
                            "IfErrors|IfFileExists|IfRebootFlag|IfRtlLanguage|" +
                            "IfShellVarContextAll|IfSilent|InitPluginsDir|" +
                            "InstallButtonText|InstallColors|InstallDir|" +
                            "InstallDirRegKey|InstProgressFlags|InstType|" +
                            "InstTypeGetText|InstTypeSetText|Int64Cmp|Int64CmpU|" +
                            "Int64Fmt|IntCmp|IntCmpU|IntFmt|IntOp|IntPtrCmp|" +
                            "IntPtrCmpU|IntPtrOp|IsWindow|LangString|" +
                            "LicenseBkColor|LicenseData|LicenseForceSelection|" +
                            "LicenseLangString|LicenseText|LoadAndSetImage|" +
                            "LoadLanguageFile|LockWindow|LogSet|LogText|" +
                            "ManifestDPIAware|ManifestLongPathAware|" +
                            "ManifestMaxVersionTested|ManifestSupportedOS|" +
                            "MessageBox|MiscButtonText|Name|Nop|OutFile|Page|" +
                            "PageCallbacks|PEAddResource|PEDllCharacteristics|" +
                            "PERemoveResource|PESubsysVer|Pop|Push|Quit|ReadEnvStr|" +
                            "ReadINIStr|ReadRegDWORD|ReadRegStr|Reboot|RegDLL|Rename|" +
                            "RequestExecutionLevel|ReserveFile|Return|RMDir|" +
                            "SearchPath|SectionGetFlags|SectionGetInstTypes|" +
                            "SectionGetSize|SectionGetText|SectionIn|" +
                            "SectionSetFlags|SectionSetInstTypes|SectionSetSize|" +
                            "SectionSetText|SendMessage|SetAutoClose|" +
                            "SetBrandingImage|SetCompress|SetCompressor|" +
                            "SetCompressorDictSize|SetCtlColors|SetCurInstType|" +
                            "SetDatablockOptimize|SetDateSave|SetDetailsPrint|" +
                            "SetDetailsView|SetErrorLevel|SetErrors|" +
                            "SetFileAttributes|SetFont|SetOutPath|SetOverwrite|" +
                            "SetRebootFlag|SetRegView|SetShellVarContext|SetSilent|" +
                            "ShowInstDetails|ShowUninstDetails|ShowWindow|" +
                            "SilentInstall|SilentUnInstall|Sleep|SpaceTexts|StrCmp|" +
                            "StrCmpS|StrCpy|StrLen|SubCaption|Target|Unicode|" +
                            "UninstallButtonText|UninstallCaption|UninstallIcon|" +
                            "UninstallSubCaption|UninstallText|UninstPage|UnRegDLL|" +
                            "Var|VIAddVersionKey|VIFileVersion|VIProductVersion|" +
                            "WindowIcon|WriteINIStr|WriteRegBin|WriteRegDWORD|" +
                            "WriteRegExpandStr|WriteRegMultiStr|WriteRegNone|" +
                            "WriteRegStr|WriteUninstaller|XPStyle)\\b",
                        RegexOption.IGNORE_CASE
                    ),
                    token = "keyword",
                    sol = true
                ),
                SimpleModeRule(
                    regex = Regex(
                        "^\\s*(?:Function|PageEx|Section(?:Group)?)\\b",
                        RegexOption.IGNORE_CASE
                    ),
                    token = "keyword",
                    indent = true,
                    sol = true
                ),
                SimpleModeRule(
                    regex = Regex(
                        "^\\s*(?:(?:Function|PageEx|Section(?:Group)?)End)\\b",
                        RegexOption.IGNORE_CASE
                    ),
                    token = "keyword",
                    dedent = true,
                    sol = true
                ),
                // Command Options
                @Suppress("ktlint:standard:max-line-length")
                SimpleModeRule(
                    regex = Regex(
                        "^\\b(?:ARCHIVE|FILE_ATTRIBUTE_ARCHIVE|FILE_ATTRIBUTE_HIDDEN|" +
                            "FILE_ATTRIBUTE_NORMAL|FILE_ATTRIBUTE_OFFLINE|" +
                            "FILE_ATTRIBUTE_READONLY|FILE_ATTRIBUTE_SYSTEM|" +
                            "FILE_ATTRIBUTE_TEMPORARY|HIDDEN|HKCC|HKCR(?:32|64)?|" +
                            "HKCU(?:32|64)?|HKDD|HKEY_CLASSES_ROOT|" +
                            "HKEY_CURRENT_CONFIG|HKEY_CURRENT_USER|HKEY_DYN_DATA|" +
                            "HKEY_LOCAL_MACHINE|HKEY_PERFORMANCE_DATA|HKEY_USERS|" +
                            "HKLM(?:32|64)?|HKPD|HKU|IDABORT|IDCANCEL|IDD_DIR|" +
                            "IDD_INST|IDD_INSTFILES|IDD_LICENSE|IDD_SELCOM|" +
                            "IDD_UNINST|IDD_VERIFY|IDIGNORE|IDNO|IDOK|IDRETRY|" +
                            "IDYES|MB_ABORTRETRYIGNORE|MB_DEFBUTTON1|MB_DEFBUTTON2|" +
                            "MB_DEFBUTTON3|MB_DEFBUTTON4|MB_ICONEXCLAMATION|" +
                            "MB_ICONINFORMATION|MB_ICONQUESTION|MB_ICONSTOP|MB_OK|" +
                            "MB_OKCANCEL|MB_RETRYCANCEL|MB_RIGHT|MB_RTLREADING|" +
                            "MB_SETFOREGROUND|MB_TOPMOST|MB_USERICON|MB_YESNO|" +
                            "MB_YESNOCANCEL|NORMAL|OFFLINE|READONLY|SHCTX|" +
                            "SHELL_CONTEXT|SW_HIDE|SW_SHOWDEFAULT|SW_SHOWMAXIMIZED|" +
                            "SW_SHOWMINIMIZED|SW_SHOWNORMAL|SYSTEM|TEMPORARY)\\b",
                        RegexOption.IGNORE_CASE
                    ),
                    token = "atom"
                ),
                @Suppress("ktlint:standard:max-line-length")
                SimpleModeRule(
                    regex = Regex(
                        "^\\b(?:admin|all|amd64-unicode|auto|both|bottom|bzip2|" +
                            "components|current|custom|directory|false|force|hide|" +
                            "highest|ifdiff|ifnewer|instfiles|lastused|leave|left|" +
                            "license|listonly|lzma|nevershow|none|normal|notset|off|" +
                            "on|right|show|silent|silentlog|textonly|top|true|try|" +
                            "un\\.components|un\\.custom|un\\.directory|un\\.instfiles|" +
                            "un\\.license|uninstConfirm|user|Win10|Win7|Win8|WinVista|" +
                            "x-86-(?:ansi|unicode)|zlib)\\b",
                        RegexOption.IGNORE_CASE
                    ),
                    token = "builtin"
                ),
                // LogicLib.nsh
                SimpleModeRule(
                    regex = Regex(
                        "^\\$\\{(?:And(?:If(?:Not)?|Unless)|Break|" +
                            "Case(?:2|3|4|5|Else)?|Continue|Default|" +
                            "Do(?:Until|While)?|Else(?:If(?:Not)?|Unless)?|" +
                            "End(?:If|Select|Switch)|Exit(?:Do|For|While)|" +
                            "For(?:Each)?|If(?:Cmd|Not(?:Then)?|Then)?|" +
                            "Loop(?:Until|While)?|Or(?:If(?:Not)?|Unless)|" +
                            "Select|Switch|Unless|While)\\}",
                        RegexOption.IGNORE_CASE
                    ),
                    token = "variable-2",
                    indent = true
                ),
                // FileFunc.nsh
                SimpleModeRule(
                    regex = Regex(
                        "^\\$\\{(?:BannerTrimPath|DirState|DriveSpace|" +
                            "Get(?:BaseName|Drives|ExeName|ExePath|FileAttributes|" +
                            "FileExt|FileName|FileVersion|Options|OptionsS|Parameters|" +
                            "Parent|Root|Size|Time)|Locate|RefreshShellIcons)\\}",
                        RegexOption.IGNORE_CASE
                    ),
                    token = "variable-2",
                    dedent = true
                ),
                // Memento.nsh
                SimpleModeRule(
                    regex = Regex(
                        "^\\$\\{(?:Memento(?:Section(?:Done|End|Restore|Save)?" +
                            "|UnselectedSection))\\}",
                        RegexOption.IGNORE_CASE
                    ),
                    token = "variable-2",
                    dedent = true
                ),
                // TextFunc.nsh
                SimpleModeRule(
                    regex = Regex(
                        "^\\$\\{(?:Config(?:Read|ReadS|Write|WriteS)|" +
                            "File(?:Join|ReadFromEnd|Recode)|" +
                            "Line(?:Find|Read|Sum)|" +
                            "Text(?:Compare|CompareS)|TrimNewLines)\\}",
                        RegexOption.IGNORE_CASE
                    ),
                    token = "variable-2",
                    dedent = true
                ),
                // WinVer.nsh
                SimpleModeRule(
                    regex = Regex(
                        "^\\$\\{(?:(?:At(?:Least|Most)|Is)" +
                            "(?:ServicePack|Win(?:7|8|10|95|98|" +
                            "200(?:0|3|8(?:R2)?)|ME|NT4|Vista|XP))" +
                            "|Is(?:NT|Server))\\}",
                        RegexOption.IGNORE_CASE
                    ),
                    token = "variable",
                    dedent = true
                ),
                // WordFunc.nsh
                SimpleModeRule(
                    regex = Regex(
                        "^\\$\\{(?:StrFilterS?|Version(?:Compare|Convert)|" +
                            "Word(?:AddS?|Find(?:(?:2|3)X)?S?|InsertS?|ReplaceS?))\\}",
                        RegexOption.IGNORE_CASE
                    ),
                    token = "keyword",
                    dedent = true
                ),
                // x64.nsh
                SimpleModeRule(
                    regex = Regex(
                        "^\\$\\{(?:RunningX64)\\}",
                        RegexOption.IGNORE_CASE
                    ),
                    token = "variable",
                    dedent = true
                ),
                SimpleModeRule(
                    regex = Regex(
                        "^\\$\\{(?:Disable|Enable)X64FSRedirection\\}",
                        RegexOption.IGNORE_CASE
                    ),
                    token = "keyword",
                    dedent = true
                ),
                // Line Comment
                SimpleModeRule(
                    regex = Regex("^(?:#|;).*"),
                    token = "comment"
                ),
                // Block Comment
                SimpleModeRule(
                    regex = Regex("^/\\*"),
                    token = "comment",
                    next = "comment"
                ),
                // Operator
                SimpleModeRule(
                    regex = Regex("^[-+/*=<>!]+"),
                    token = "operator"
                ),
                // Variable
                SimpleModeRule(
                    regex = Regex("^\\$\\w[\\w.]*"),
                    token = "variable"
                ),
                // Constant
                SimpleModeRule(
                    regex = Regex("^\\$\\{[!\\w.:-]+\\}"),
                    token = "variableName.constant"
                ),
                // Language String
                SimpleModeRule(
                    regex = Regex("^\\$\\([!\\w.:-]+\\)"),
                    token = "atom"
                )
            ),
            "comment" to listOf(
                SimpleModeRule(
                    regex = Regex("^.*?\\*/"),
                    token = "comment",
                    next = "start"
                ),
                SimpleModeRule(
                    regex = Regex("^.*"),
                    token = "comment"
                )
            )
        ),
        name = "nsis",
        languageData = mapOf(
            "commentTokens" to mapOf(
                "line" to "#",
                "block" to mapOf("open" to "/*", "close" to "*/")
            )
        )
    )
)
