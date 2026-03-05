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

package com.monkopedia.kodemirror.lang.liquid

import com.monkopedia.kodemirror.lezer.lr.LRParser
import com.monkopedia.kodemirror.lezer.lr.ParserSpec
import com.monkopedia.kodemirror.lezer.lr.SpecializerSpec

private val specIdentifier = mapOf(
    "contains" to 34,
    "or" to 38,
    "and" to 38,
    "true" to 52,
    "false" to 52,
    "empty" to 54,
    "forloop" to 57,
    "tablerowloop" to 59,
    "continue" to 61,
    "in" to 131,
    "with" to 197,
    "for" to 199,
    "as" to 201,
    "if" to 237,
    "endif" to 241,
    "unless" to 247,
    "endunless" to 251,
    "elsif" to 255,
    "else" to 259,
    "case" to 265,
    "endcase" to 269,
    "when" to 273,
    "endfor" to 281,
    "tablerow" to 287,
    "endtablerow" to 291,
    "break" to 295,
    "cycle" to 301,
    "echo" to 305,
    "render" to 309,
    "include" to 313,
    "assign" to 317,
    "capture" to 323,
    "endcapture" to 327,
    "increment" to 331,
    "decrement" to 335
)

private val specTagName = mapOf(
    "if" to 86,
    "endif" to 90,
    "elsif" to 94,
    "else" to 98,
    "unless" to 104,
    "endunless" to 108,
    "case" to 114,
    "endcase" to 118,
    "when" to 122,
    "for" to 128,
    "endfor" to 138,
    "tablerow" to 144,
    "endtablerow" to 148,
    "break" to 152,
    "continue" to 156,
    "cycle" to 160,
    "comment" to 166,
    "endcomment" to 172,
    "raw" to 178,
    "endraw" to 184,
    "echo" to 188,
    "render" to 192,
    "include" to 204,
    "assign" to 208,
    "capture" to 214,
    "endcapture" to 218,
    "increment" to 222,
    "decrement" to 226,
    "liquid" to 230
)

internal val parser: LRParser = LRParser.deserialize(
    ParserSpec(
        version = 14,
        states = "KtQYOPOOOOOP'#F{'#F{OeOaO'#CdOsQhO'#CfO!bQxO'#DSO#{OPO'#DVO\$ZOPO'#D`O\$iOPO'#DeO\$wOPO'#DlO%VOPO'#DtO%eOSO'#EPO%jOQO'#EVO%oOPO'#EiOOOP'#Ge'#GeOOOP'#G]'#G]OOOP'#Fz'#FzQYOPOOOOOP-E9y-E9yOOQW'#Cg'#CgO&cQ!jO,59QO&jQ!jO'#G^OsQhO'#CtOOQW'#Gb'#GbOOQW'#Gc'#GcOOQW'#Gd'#GdOOQW'#G^'#G^OOOP,59n,59nO)YQhO,59nOsQhO,59rOsQhO,59vO)dQhO,59xOsQhO,59{OsQhO,5:QOsQhO,5:UO!]QhO,5:XO!]QhO,5:aO)iQhO,5:eO)nQhO,5:gO)sQhO,5:iO)xQhO,5:lO)}QhO,5:rOsQhO,5:wOsQhO,5:yOsQhO,5;POsQhO,5;ROsQhO,5;UOsQhO,5;YOsQhO,5;[O+^QhO,5;^O+eOPO'#CdOOOP,59q,59qO#{OPO,59qO+sQxO'#DYOOOP,59z,59zO\$ZOPO,59zO+xQxO'#DcOOOP,5:P,5:PO\$iOPO,5:PO+}QxO'#DhOOOP,5:W,5:WO\$wOPO,5:WO,SQxO'#DrOOOP,5:`,5:`O%VOPO,5:`O,XQxO'#DwOOOS'#GQ'#GQO,^OSO'#ESO,fOSO,5:kOOOQ'#GR'#GRO,kOQO'#EYO,sOQO,5:qOOOP,5;T,5;TO%oOPO,5;TO,xQxO'#ElOOOP-E9x-E9xO,}Q#|O,59SOsQhO,59VOsQhO,59WOsQhO,59WO-SQhO'#C}OOQW'#F|'#F|O-XQhO1G.lOOOP1G.l1G.lOsQhO,59WOsQhO,59[O-rQ!jO,59`O-yQ!jO1G/YO.QQhO1G/YOOOP1G/Y1G/YO.YQ!jO1G/^O.aQ!jO1G/bOOOP1G/d1G/dO.hQ!jO1G/gO.oQ!jO1G/lO.vQ!jO1G/pO/QQhO1G/sO/QQhO1G/{OOOP1G0P1G0POOOP1G0R1G0RO/VQhO1G0TOOOS1G0W1G0WOOOQ1G0^1G0^O/bQ!jO1G0cO/iQ!jO1G0eO/yQ!jO1G0kO0QQ!jO1G0mO0XQ!jO1G0pO0`Q!jO1G0tO0gQ!jO1G0vOOQW'#Gh'#GhOOQW'#Gk'#GkOsQhO'#EuO0nQhO'#EtOOQW'#Gm'#GmOsQhO'#EzO0uQhO'#EyOOQW'#Go'#GoOsQhO'#FOOOQW'#Gp'#GpOOQW'#FQ'#FQOOQW'#Gq'#GqOsQhO'#FTO0|QhO'#FSOOQW'#Gs'#GsOsQhO'#FXO!]QhO'#F[O1TQhO'#FZOOQW'#Gu'#GuO!]QhO'#F`O1[QhO'#F_OOQW'#Gw'#GwOOQW'#Fd'#FdOOQW'#Ff'#FfOOQW'#Gx'#GxO1cQhO'#FgOOQW'#Gy'#GyOsQhO'#FiOOQW'#Gz'#GzOsQhO'#FkOOQW'#G{'#G{OsQhO'#FmOOQW'#G|'#G|OsQhO'#FoOOQW'#G}'#G}OsQhO'#FrO1hQhO'#FqOOQW'#HP'#HPOsQhO'#FvOOQW'#HQ'#HQOsQhO'#FxOOQW'#Gj'#GjOOQW'#GT'#GTO1oQhO1G0xOOOP1G0x1G0xOOOP1G/]1G/]O1vQhO,59tOOOP1G/f1G/fO1{QhO,59}OOOP1G/k1G/kO2QQhO,5:SOOOP1G/r1G/rO2VQhO,5:^OOOP1G/z1G/zO2[QhO,5:cOOOS-E:O-E:OOOOP1G0V1G0VO2aQxO'#ETOOOQ-E:P-E:POOOP1G0]1G0]O2fQxO'#EZOOOP1G0o1G0oO2kQhO,5;WOOQW1G.n1G.nO2pQ!jO1G.qO5aQ!jO1G.rO5hQ!jO1G.rOOQW'#DP'#DPO7vQhO,59iOOQW-E9z-E9zOOOP7+\$W7+\$WO9pQ!jO1G.rO9wQ!jO1G.vOsQhO1G.zO<VQhO7+\$tOOOP7+\$t7+\$tOOOP7+\$x7+\$xOOOP7+\$|7+\$|OOOP7+%R7+%ROOOP7+%W7+%WOsQhO'#F}O<_QhO7+%[OOOP7+%[7+%[OOQW'#Gf'#GfOsQhO7+%_OsQhO7+%gO<gQhO'#GPO<lQhO7+%oOOOP7+%o7+%oO<tQhO7+%oO<yQhO7+%}OOOP7+%}7+%}OOQW'#Gg'#GgO!]QhO'#EaOsQhO'#EaOOQW'#GS'#GSO=RQhO7+&POOOP7+&P7+&POOOP7+&V7+&VO=aQhO7+&XOOOP7+&X7+&XOOOP7+&[7+&[OOOP7+&`7+&`OOOP7+&b7+&bO=iQ!jO,5;aOOQW'#Gl'#GlOOQW'#Ew'#EwOOQW,5;`,5;`O0nQhO,5;`O>xQ!jO,5;fOOQW'#Gn'#GnOOQW'#E|'#E|OOQW,5;e,5;eO0uQhO,5;eO@XQ!jO,5;jOAzQ!jO,5;oOOQW'#Gr'#GrOOQW'#FV'#FVOOQW,5;n,5;nO0|QhO,5;nOCZQ!jO,5;sO/QQhO,5;vOOQW'#Gt'#GtOOQW'#F]'#F]OOQW,5;u,5;uO1TQhO,5;uO/QQhO,5;zOOQW'#Gv'#GvOOQW'#Fb'#FbOOQW,5;y,5;yO1[QhO,5;yOEPQhO,5<ROFvQ!jO,5<TOHiQ!jO,5<VOJbQ!jO,5<XOLTQ!jO,5<ZOMvQ!jO,5<^OOQW'#HO'#HOOOQW'#Ft'#FtOOQW,5<],5<]O1hQhO,5<]O! VQ!jO,5<bO!!xQ!jO,5<dOOQW-E:R-E:ROOOP7+&d7+&dOOOP1G/`1G/`OOOP1G/i1G/iOOOP1G/n1G/nOOOP1G/x1G/xOOOP1G/}1G/}O!\$kQhO,5:oO!\$pQhO,5:uOOOP1G0r1G0rOOQW7+\$]7+\$]OsQhO1G/TO!\$uQ!jO7+\$fOOOP<<H`<<H`O!\$|Q!jO,5<iOOQW-E9{-E9{OOOP<<Hv<<HvO!&xQ!jO<<HyO!'SQ!jO<<IROOQW,5<k,5<kOOQW-E9}-E9}OOOP<<IZ<<IZO!'^QhO<<IZOOOP<<Ii<<IiO!'fQhO,5:{O!'kQ!jO,5:{OOQW-E:Q-E:QOOOP<<Ik<<IkOOOP<<Is<<IsOOQW1G0z1G0zOOQW1G1P1G1POOQW1G1Y1G1YO!)gQhO1G1_OsQhO1G1bOOQW1G1a1G1aOsQhO1G1fOOQW1G1e1G1eO!+ZQhO1G1mO!,}QhO1G1mO!-SQhO1G1oOOQW'#GU'#GUO!.vQhO1G1qO!0mQhO1G1uOOQW1G1w1G1wOOOP1G0Z1G0ZOOOP1G0a1G0aO!2aQ!jO7+\$oOOQW<<HQ<<HQOOQW'#Dq'#DqO!4]QhO'#DpOOQW'#GO'#GOO!5vQhOAN>eOOOPAN>eAN>eO!6OQhOAN>mOOOPAN>mAN>mO!6WQhOAN>uOOOPAN>uAN>uOsQhO1G0gOOQW'#Gi'#GiO!]QhO1G0gO!6`Q!jO7+&|O!7rQ!jO7+'QO!9UQhO7+'XOOQW-E:S-E:SO!:xQhO<<HZOsQhO,5:[OOQW-E9|-E9|OOOPG24PG24POOOPG24XG24XOOOPG24aG24aO!<rQ!jO7+&ROOQW7+&R7+&RO!>kQhO<<JhO!?{QhO<<JlO!A]QhO<<JsO!CPQ!jO1G/v",
        stateData = "!Di~O%OOSUOS~OPROQSO\$zPO~O\$zPOPWXQWX\$yWX~OgeOjiOkiOlfOmgOnhOoiOpiO%RbO~OwkOxjO{lO!PmO!RnO!UoO!ZpO!_qO!brO!jsO!ntO!puO!rvO!uwO!{xO#QyO#SzO#Y{O#[|O#_}O#c!OO#e!PO#g!QO~OPROQSOR!UO\$zPO~OPROQSOR!XO\$zPO~OPROQSOR![O\$zPO~OPROQSOR!_O\$zPO~OPROQSOR!bO\$zPO~O\$|!cO~O\${!fO~OPROQSOR!kO\$zPO~O]!mOa!uOb!oOc!pOr!qO%T!nO~OX!tO~P%}Oe!vOX%QX]%QXa%QXb%QXc%QXr%QX%T%QXi%QXx%QXu%QX#U%QX#V%QX%S%QXn%QX#j%QX#l%QX#o%QX#s%QX#u%QX#x%QX#|%QX\$T%QX\$X%QX\$[%QX\$^%QX\$`%QX\$b%QX\$d%QX\$g%QX\$k%QX\$m%QX#q%QX#z%QX\$i%QXf%QX%R%QX#W%QX\$Q%QX\$V%QX~Or!qOx!zO~PsOx!}O~Ox#TO~Ox#UO~Oo#VO~Ox#WO~Ox#XO~OnhO#V#aO#j#bO#o#eO#s#hO#u#jO#x#lO#|#oO\$T#sO\$X#vO\$[#yO\$^#{O\$`#}O\$b\$PO\$d\$RO\$g\$TO\$k\$WO\$m\$YO~Ox\$_O~P*SO\$zPOPWXQWXRWX~O}\$aO~O!W\$cO~O!]\$eO~O!g\$gO~O!l\$iO~O\$|!cOT!vX~OT\$lO~O\${!fOS!|X~OS\$oO~O#a\$qO~O^\$rO~O%R\$vO~OX\$yOr!qO~O]!mOa!uOb!oOc!pO%T!nO~Oi\$|O~P-aOx%OO~P%}Or!qOx%OO~Ox%PO~P-aOx%QO~P-aOx%RO~P-aOx%SO~P-aOu%TOx%VO~P-aO!c%WO~Ot%^Ou%ZOx%]O~Ox%`O~P%}Ou%bOx%fO#U%aO#V#aO~P-aOx%gO~P-aOx%iO~P%}Ox%jO~P-aOx%kO~P-aOx%lO~P-aO#l%nO~P*SO#q%sO~P*SO#z%yO~P*SO\$Q&PO~P*SO\$V&UO~P*SOo&YO~O\$i&`O~P*SOx&gO~P*SOx&hO~Ox&iO~Ox&jO~Ox&kO~Ox&lO~O!x&mO~O#O&nO~Ox&oO~O%S&pO~P-aO]!mO%T!nOX`ia`ic`ir`ii`ix`iu`i#U`i#V`i%S`in`i#j`i#l`i#o`i#s`i#u`i#x`i#|`i\$T`i\$X`i\$[`i\$^`i\$``i\$b`i\$d`i\$g`i\$k`i\$m`i#q`i#z`i\$i`if`i%R`i#W`i\$Q`i\$V`i~Ob`i~P2wOX`ir`ii`ix`iu`i#U`i#V`i%S`in`i#j`i#l`i#o`i#s`i#u`i#x`i#|`i\$T`i\$X`i\$[`i\$^`i\$``i\$b`i\$d`i\$g`i\$k`i\$m`i#q`i#z`i\$i`if`i%R`i#W`i\$Q`i\$V`i~P-aOt&qOXqarqaxqanqa#Vqa#jqa#oqa#sqa#uqa#xqa#|qa\$Tqa\$Xqa\$[qa\$^qa\$`qa\$bqa\$dqa\$gqa\$kqa\$mqa#lqa#qqa#zqa\$Qqa\$Vqa\$iqa~Ob!oO~P2wOXdirdiidixdiudi#Udi#Vdi%Sdindi#jdi#ldi#odi#sdi#udi#xdi#|di\$Tdi\$Xdi\$[di\$^di\$`di\$bdi\$ddi\$gdi\$kdi\$mdi#qdi#zdi\$idifdi%Rdi#Wdi\$Qdi\$Vdi~P-aOr!qOx&sO~Ou%TOx&vO~Oo&yO~Ou%ZOx&{O~Oo&|O~Or!qOx&}O~Ou%bOx'RO#U%aO#V#aO~Or!qOx'SO~On#ia#V#ia#j#ia#l#ia#o#ia#s#ia#u#ia#x#ia#|#ia\$T#ia\$X#ia\$[#ia\$^#ia\$`#ia\$b#ia\$d#ia\$g#ia\$k#ia\$m#ia~P-aOn#na#V#na#j#na#o#na#q#na#s#na#u#na#x#na#|#na\$T#na\$X#na\$[#na\$^#na\$`#na\$b#na\$d#na\$g#na\$k#na\$m#na~P-aOn#rax#ra#V#ra#j#ra#o#ra#s#ra#u#ra#x#ra#|#ra\$T#ra\$X#ra\$[#ra\$^#ra\$`#ra\$b#ra\$d#ra\$g#ra\$k#ra\$m#ra#l#ra#q#ra#z#ra\$Q#ra\$V#ra\$i#ra~P-aOn#wa#V#wa#j#wa#o#wa#s#wa#u#wa#x#wa#z#wa#|#wa\$T#wa\$X#wa\$[#wa\$^#wa\$`#wa\$b#wa\$d#wa\$g#wa\$k#wa\$m#wa~P-aOu%TOn#{ax#{a#V#{a#j#{a#o#{a#s#{a#u#{a#x#{a#|#{a\$T#{a\$X#{a\$[#{a\$^#{a\$`#{a\$b#{a\$d#{a\$g#{a\$k#{a\$m#{a#l#{a#q#{a#z#{a\$Q#{a\$V#{a\$i#{a~P-aOt'^Ou%ZOn\$Zax\$Za#V\$Za#j\$Za#o\$Za#s\$Za#u\$Za#x\$Za#|\$Za\$T\$Za\$X\$Za\$[\$Za\$^\$Za\$`\$Za\$b\$Za\$d\$Za\$g\$Za\$k\$Za\$m\$Za#l\$Za#q\$Za#z\$Za\$Q\$Za\$V\$Za\$i\$Za~On\$]ax\$]a#V\$]a#j\$]a#o\$]a#s\$]a#u\$]a#x\$]a#|\$]a\$T\$]a\$X\$]a\$[\$]a\$^\$]a\$`\$]a\$b\$]a\$d\$]a\$g\$]a\$k\$]a\$m\$]a#l\$]a#q\$]a#z\$]a\$Q\$]a\$V\$]a\$i\$]a~P%}Ou%bO#U%aO#V#aOn\$_ax\$_a#j\$_a#o\$_a#s\$_a#u\$_a#x\$_a#|\$_a\$T\$_a\$X\$_a\$[\$_a\$^\$_a\$`\$_a\$b\$_a\$d\$_a\$g\$_a\$k\$_a\$m\$_a#l\$_a#q\$_a#z\$_a\$Q\$_a\$V\$_a\$i\$_a~P-aOn\$aax\$aa#V\$aa#j\$aa#o\$aa#s\$aa#u\$aa#x\$aa#|\$aa\$T\$aa\$X\$aa\$[\$aa\$^\$aa\$`\$aa\$b\$aa\$d\$aa\$g\$aa\$k\$aa\$m\$aa#l\$aa#q\$aa#z\$aa\$Q\$aa\$V\$aa\$i\$aa~P-aOn\$cax\$ca#V\$ca#j\$ca#o\$ca#s\$ca#u\$ca#x\$ca#|\$ca\$T\$ca\$X\$ca\$[\$ca\$^\$ca\$`\$ca\$b\$ca\$d\$ca\$g\$ca\$k\$ca\$m\$ca#l\$ca#q\$ca#z\$ca\$Q\$ca\$V\$ca\$i\$ca~P%}On\$fa#V\$fa#j\$fa#o\$fa#s\$fa#u\$fa#x\$fa#|\$fa\$T\$fa\$X\$fa\$[\$fa\$^\$fa\$`\$fa\$b\$fa\$d\$fa\$g\$fa\$i\$fa\$k\$fa\$m\$fa~P-aOn\$jax\$ja#V\$ja#j\$ja#o\$ja#s\$ja#u\$ja#x\$ja#|\$ja\$T\$ja\$X\$ja\$[\$ja\$^\$ja\$`\$ja\$b\$ja\$d\$ja\$g\$ja\$k\$ja\$m\$ja#l\$ja#q\$ja#z\$ja\$Q\$ja\$V\$ja\$i\$ja~P-aOn\$lax\$la#V\$la#j\$la#o\$la#s\$la#u\$la#x\$la#|\$la\$T\$la\$X\$la\$[\$la\$^\$la\$`\$la\$b\$la\$d\$la\$g\$la\$k\$la\$m\$la#l\$la#q\$la#z\$la\$Q\$la\$V\$la\$i\$la~P-aOx'dO~Ox'eO~Of'gO~P-aOu\$qax\$qan\$qa#V\$qa#j\$qa#o\$qa#s\$qa#u\$qa#x\$qa#|\$qa\$T\$qa\$X\$qa\$[\$qa\$^\$qa\$`\$qa\$b\$qa\$d\$qa\$g\$qa\$k\$qa\$m\$qa#l\$qa#q\$qa#z\$qa\$Q\$qa\$V\$qa\$i\$qaX\$qar\$qa~P-aOx'lO%R'hO~P-aOx'nO%R'hO~P-aOu%ZOx'pO~Ot'qO~O#W'rOu#Tax#Ta#U#Ta#V#Tan#Ta#j#Ta#o#Ta#s#Ta#u#Ta#x#Ta#|\$Ta\$T#Ta\$X#Ta\$[#Ta\$^#Ta\$`#Ta\$b#Ta\$d#Ta\$g#Ta\$k#Ta\$m#Ta#l#Ta#q#Ta#z#Ta\$Q#Ta\$V#Ta\$i#Ta~P-aOu%TOn#{ix#{i#V#{i#j#{i#o#{i#s#{i#u#{i#x#{i#|#{i\$T#{i\$X#{i\$[#{i\$^#{i\$`#{i\$b#{i\$d#{i\$g#{i\$k#{i\$m#{i#l#{i#q#{i#z#{i\$Q#{i\$V#{i\$i#{i~Ou%ZOn\$Zix\$Zi#V\$Zi#j\$Zi#o\$Zi#s\$Zi#u\$Zi#x\$Zi#|\$Zi\$T\$Zi\$X\$Zi\$[\$Zi\$^\$Zi\$`\$Zi\$b\$Zi\$d\$Zi\$g\$Zi\$k\$Zi\$m\$Zi#l\$Zi#q\$Zi#z\$Zi\$Q\$Zi\$V\$Zi\$i\$Zi~Oo'vO~Or!qOn\$]ix\$]i#V\$]i#j\$]i#o\$]i#s\$]i#u\$]i#x\$]i#|\$]i\$T\$]i\$X\$]i\$[\$]i\$^\$]i\$`\$]i\$b\$]i\$d\$]i\$g\$]i\$k\$]i\$m\$]i#l\$]i#q\$]i#z\$]i\$Q\$]i\$V\$]i\$i\$]i~Ou%bO#U%aO#V#aOn\$_ix\$_i#j\$_i#o\$_i#s\$_i#u\$_i#x\$_i#|\$_i\$T\$_i\$X\$_i\$[\$_i\$^\$_i\$`\$_i\$b\$_i\$d\$_i\$g\$_i\$k\$_i\$m\$_i#l\$_i#q\$_i#z\$_i\$Q\$_i\$V\$_i\$i\$_i~Or!qOn\$cix\$ci#V\$ci#j\$ci#o\$ci#s\$ci#u\$ci#x\$ci#|\$ci\$T\$ci\$X\$ci\$[\$ci\$^\$ci\$`\$ci\$b\$ci\$d\$ci\$g\$ci\$k\$ci\$m\$ci#l\$ci#q\$ci#z\$ci\$Q\$ci\$V\$ci\$i\$ci~Ou%TOXqqrqqxqqnqq#Vqq#jqq#oqq#sqq#uqq#xqq#|qq\$Tqq\$Xqq\$[qq\$^qq\$`qq\$bqq\$dqq\$gqq\$kqq\$mqq#lqq#qqq#zqq\$Qqq\$Vqq\$iqq~P-aOt'yOx!dX%R!dXn!dX#V!dX#j!dX#o!dX#s!dX#u!dX#x!dX#|!dX\$Q!dX\$T!dX\$X!dX\$[!dX\$^!dX\$`!dX\$b!dX\$d!dX\$g!dX\$k!dX\$m!dX\$V!dX~Ox'{O%R'hO~Ox'|O%R'hO~Ou%ZOx'}O~O%R'hOn\$Oq#V\$Oq#j\$Oq#o\$Oq#s\$Oq#u\$Oq#x\$Oq#|\$Oq\$Q\$Oq\$T\$Oq\$X\$Oq\$[\$Oq\$^\$Oq\$`\$Oq\$b\$Oq\$d\$Oq\$g\$Oq\$k\$Oq\$m\$Oq~P-aO%R'hOn\$Sq#V\$Sq#j\$Sq#o\$Sq#s\$Sq#u\$Sq#x\$Sq#|\$Sq\$T\$Sq\$V\$Sq\$X\$Sq\$[\$Sq\$^\$Sq\$`\$Sq\$b\$Sq\$d\$Sq\$g\$Sq\$k\$Sq\$m\$Sq~P-aOu%ZOn\$Zqx\$Zq#V\$Zq#j\$Zq#o\$Zq#s\$Zq#u\$Zq#x\$Zq#|\$Zq\$T\$Zq\$X\$Zq\$[\$Zq\$^\$Zq\$`\$Zq\$b\$Zq\$d\$Zq\$g\$Zq\$k\$Zq\$m\$Zq#l\$Zq#q\$Zq#z\$Zq\$Q\$Zq\$V\$Zq\$i\$Zq~Ou%TOXqyrqyxqynqy#Vqy#jqy#oqy#sqy#uqy#xqy#|qy\$Tqy\$Xqy\$[qy\$^qy\$`qy\$bqy\$dqy\$gqy\$kqy\$mqy#lqy#qqy#zqy\$Qqy\$Vqy\$iqy~Ou#Tqx#Tq#U#Tq#V#Tqn#Tq#j#Tq#o#Tq#s#Tq#u#Tq#x#Tq#|\$Tq\$T#Tq\$X#Tq\$[#Tq\$^#Tq\$`#Tq\$b#Tq\$d#Tq\$g#Tq\$k#Tq\$m#Tq#l#Tq#q#Tq#z#Tq\$Q#Tq\$V#Tq\$i#Tq~P-aO%R'hOn\$Oy#V\$Oy#j\$Oy#o\$Oy#s\$Oy#u\$Oy#x\$Oy#|\$Oy\$Q\$Oy\$T\$Oy\$X\$Oy\$[\$Oy\$^\$Oy\$`\$Oy\$b\$Oy\$d\$Oy\$g\$Oy\$k\$Oy\$m\$Oy~O%R'hOn\$Sy#V\$Sy#j\$Sy#o\$Sy#s\$Sy#u\$Sy#x\$Sy#|\$Sy\$T\$Sy\$V\$Sy\$X\$Sy\$[\$Sy\$^\$Sy\$`\$Sy\$b\$Sy\$d\$Sy\$g\$Sy\$k\$Sy\$m\$Sy~Ou%ZOn\$Zyx\$Zy#V\$Zy#j\$Zy#o\$Zy#s\$Zy#u\$Zy#x\$Zy#|\$Zy\$T\$Zy\$X\$Zy\$[\$Zy\$^\$Zy\$`\$Zy\$b\$Zy\$d\$Zy\$g\$Zy\$k\$Zy\$m\$Zy#l\$Zy#q\$Zy#z\$Zy\$Q\$Zy\$V\$Zy\$i\$Zy~Ox!di%R!din!di#V!di#j!di#o!di#s!di#u!di#x!di#|!di\$Q!di\$T!di\$X!di\$[!di\$^!di\$`!di\$b!di\$d!di\$g!di\$k!di\$m!di\$V!di~P-aO",
        goto = "@p%uPPPPPPPP%vP%v&W'hPP'h'hPPP'hPPP'hPPPPPPPP(fP(vPP(yPP(y)ZP)kP(yP(yP(y)qP*RP(y*XP*iP(yP(y*oPP+P+Z+eP(y+kP+{P(yP(yP(yP(y,RP,c,fP(y,iP,y,|P(yP(yP-PPPP(yP(yP(y-XP-iP(yP(yP(y-o.PP.aP-o.gP.wP-oP-oP-o.}P/_P-oP-o/e/uP-o/{P0]P-oP-o-oP-oP-oP-oP-oP-o0cP0sP-oP-oP0y1i2P2o2}3a3s3y4P4V4uPPPPPP4{5]PPP'h'h8P%v9_9k9q:X:[:l:|;Q;b;f;v<W<h<l<|=Q=b=f=v>W>h>x?Y?j?z@O@`m^OTUVWX[`!T!W!Z!^!a!j!vdReklmopqyz{|}!O!P!n!o!p!u!v#c#f#i#m#p#|\$O\$Q\$S\$U\$X\$Z\$|%T%X%Y%c&q'X'Z'q'yQ#RrQ#SsQ&O#qQ&T#tQ'O%bR(P'sm]OTUVWX[`!T!W!Z!^!a!jmTOTUVWX[`!T!W!Z!^!a!jQ!STR\$`!TmUOTUVWX[`!T!W!Z!^!a!jQ!VUR\$b!WmVOTUVWX[`!T!W!Z!^!a!jQ!YVR\$d!ZmWOTUVWX[`!T!W!Z!^!a!ja'j&w&x'k'm't'u(Q(Ra'i&w&x'k'm't'u(Q(RQ!]WR\$f!^mXOTUVWX[`!T!W!Z!^!a!jQ!`XR\$h!amYOTUVWX[`!T!W!Z!^!a!jR!eYR\$k!emZOTUVWX[`!T!W!Z!^!a!jR!hZR\$n!hS%d#Z%eT'`&['am[OTUVWX[`!T!W!Z!^!a!jQ!i[R\$p!jm\$[!Q#d#g#n#r#u\$V\$^%q%v%|&S&X&cm#d!Q#d#g#n#r#u\$V\$^%q%v%|&S&X&cQ%p#dR'T%qm#g!Q#d#g#n#r#u\$V\$^%q%v%|&S&X&cQ%u#gR'U%vm#n!Q#d#g#n#r#u\$V\$^%q%v%|&S&X&cQ%{#nR'V%|m#r!Q#d#g#n#r#u\$V\$^%q%v%|&S&X&cQ&R#rR'Y&Sm#u!Q#d#g#n#r#u\$V\$^%q%v%|&S&X&cQ&W#uR'[&Xm\$V!Q#d#g#n#r#u\$V\$^%q%v%|&S&X&cQ&b\$VR'c&cQ`OQ!TTQ!WUQ!ZVQ!^WQ!aXQ!j[_!l`!T!W!Z!^!a!jSQO`SaQ!Ri!RTUVWX[!T!W!Z!^!a!jQ!scQ!yk^\$x!s!y\$}%_%h&Z&^'_'bQ\$}!xQ%_#YQ%h#]Q'_&ZR'b&^Q%U#QU&u%U'W'xQ'W%}R'x'fQ'k&wQ'm&xW'z'k'm(Q(RQ(Q'tR(R'uQ%[#VW&z%[']'o(SQ']&YQ'o&|R(S'vQ!dYR\$j!dQ!gZR\$m!gQ%e#ZR'Q%eQ\$^!QQ%q#dQ%v#gQ%|#nQ&S#rQ&X#uQ&c\$V_&f\$^%q%v%|&S&X&cQ'a&[R'w'am_OTUVWX[`!T!W!Z!^!a!jQcRQ!weQ!xkQ!{lQ!|mQ#OoQ#PpQ#QqQ#YyQ#ZzQ#[{Q#]|Q#^}Q#_!OQ#`!PQ\$s!nQ\$t!oQ\$u!pQ\$z!uQ\${!vQ%m#cQ%r#fQ%w#iQ%x#mQ%}#pQ&Z#|Q&[\$OQ&]\$QQ&^\$SQ&_\$UQ&d\$XQ&e\$ZQ&r\$|Q&t%TQ&w%XQ&x%YQ'P%cQ'f&qQ't'XQ'u'ZQ(O'qR(T'y",
        nodeNames = "\u26A0 {{ {% {% {% {% InlineComment Template Text }} Interpolation VariableName MemberExpression . PropertyName SubscriptExpression BinaryExpression contains CompareOp LogicOp AssignmentExpression AssignOp ) ( RangeExpression .. BooleanLiteral empty forloop tablerowloop continue StringLiteral NumberLiteral Filter | FilterName : , Tag TagName %} IfDirective Tag if EndTag endif Tag elsif Tag else UnlessDirective Tag unless EndTag endunless CaseDirective Tag case EndTag endcase Tag when ForDirective Tag for in Parameter ParameterName EndTag endfor TableDirective Tag tablerow EndTag endtablerow Tag break Tag continue Tag cycle Comment Tag comment CommentText EndTag endcomment RawDirective Tag raw RawText EndTag endraw Tag echo Tag render RenderParameter with for as Tag include Tag assign CaptureDirective Tag capture EndTag endcapture Tag increment Tag decrement Tag liquid IfDirective Tag if EndTag endif UnlessDirective Tag unless EndTag endunless Tag elsif Tag else CaseDirective Tag case EndTag endcase Tag when ForDirective Tag EndTag endfor TableDirective Tag tablerow EndTag endtablerow Tag break Tag Tag cycle Tag echo Tag render Tag include Tag assign CaptureDirective Tag capture EndTag endcapture Tag increment Tag decrement",
        maxTerm = 220,
        nodeProps = listOf(
            listOf("closedBy", 1, "}}", -4, 2, 3, 4, 5, "%}", 23, ")"),
            listOf("openedBy", 9, "{{", 22, "(", 40, "{%"),
            listOf("group", -13, 11, 12, 15, 16, 20, 24, 26, 27, 28, 29, 30, 31, 32, "Expression")
        ),
        skippedNodes = listOf(0, 6),
        repeatNodeCount = 11,
        tokenData = ")e~RmXY!|YZ!|]^!|pq!|qr#_rs#juv\$[wx\$gxy%Syz%X{|%^|}&x}!O&}!O!P'Z!Q![&g![!]'k!^!_'p!_!`'x!`!a'p!c!}(Q!}#O(y#P#Q)O#R#S(Q#T#o(Q#p#q)T#q#r)Y%W;'S(Q;'S;:j(s<%lO(Q~#RS%O~XY!|YZ!|]^!|pq!|~#bP!_!`#e~#jOb~~#mUOY#jZr#jrs\$Ps;'S#j;'S;=`\$U<%lO#j~\$UOo~~\$XP;=`<%l#j~\$_P#q#r\$b~\$gOx~~\$jUOY\$gZw\$gwx\$Px;'S\$g;'S;=`\$|<%lO\$g~%PP;=`<%l\$g~%XOg~~%^Of~P%aQ!O!P%g!Q![&gP%jP!Q![%mP%rRpP!Q![%m!g!h%{#X#Y%{P&OR{|&X}!O&X!Q![&_P&[P!Q![&_P&dPpP!Q![&_P&lSpP!O!P%g!Q![&g!g!h%{#X#Y%{~&}Ou~~'QRuv\$[!O!P%g!Q![&g~'`Q]S!O!P'f!Q![%m~'kOi~~'pOt~~'uPb~!_!`#e~'}Pe~!_!`#e_(ZW^WwQ%RT}!O(Q!Q![(Q!c!}(Q#R#S(Q#T#o(Q%W;'S(Q;'S;:j(s<%lO(Q_(vP;=`<%l(Q~)OO%T~~)TO%S~~)YOr~~)]P#q#r)`~)eOX~",
        tokenizers = listOf(base, raw, comment, inlineComment, 0, 1, 2, 3),
        topRules = mapOf("Template" to listOf(0, 7)),
        dynamicPrecedences = mapOf(
            190 to 1, 191 to 1, 192 to 1, 194 to 1, 195 to 1, 196 to 1,
            197 to 1, 199 to 1, 200 to 1, 201 to 1, 202 to 1, 203 to 1,
            204 to 1, 205 to 1, 206 to 1, 207 to 1, 208 to 1, 209 to 1,
            210 to 1, 211 to 1, 212 to 1, 213 to 1, 214 to 1, 215 to 1,
            216 to 1, 217 to 1, 218 to 1, 219 to 1, 220 to 1
        ),
        specialized = listOf(
            SpecializerSpec(term = 187, get = { value, _ -> specIdentifier[value] ?: -1 }),
            SpecializerSpec(term = 39, get = { value, _ -> specTagName[value] ?: -1 })
        ),
        tokenPrec = 0
    )
)
