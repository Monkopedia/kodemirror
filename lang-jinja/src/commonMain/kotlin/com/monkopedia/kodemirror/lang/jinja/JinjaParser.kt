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

package com.monkopedia.kodemirror.lang.jinja

import com.monkopedia.kodemirror.lezer.lr.LRParser
import com.monkopedia.kodemirror.lezer.lr.LocalTokenGroup
import com.monkopedia.kodemirror.lezer.lr.ParserSpec
import com.monkopedia.kodemirror.lezer.lr.SpecializerSpec

private val specIdentifier = mapOf(
    "in" to 38,
    "is" to 40,
    "and" to 46,
    "or" to 48,
    "not" to 52,
    "if" to 78,
    "else" to 80,
    "true" to 98,
    "false" to 98,
    "self" to 100,
    "super" to 102,
    "loop" to 104,
    "recursive" to 136,
    "scoped" to 160,
    "required" to 162,
    "as" to 256,
    "import" to 260,
    "ignore" to 268,
    "missing" to 270,
    "with" to 272,
    "without" to 274,
    "context" to 276
)

private val specTagName = mapOf(
    "if" to 112,
    "elif" to 118,
    "else" to 122,
    "endif" to 126,
    "for" to 132,
    "endfor" to 140,
    "raw" to 146,
    "endraw" to 152,
    "block" to 158,
    "endblock" to 166,
    "macro" to 172,
    "endmacro" to 182,
    "call" to 188,
    "endcall" to 192,
    "filter" to 198,
    "endfilter" to 202,
    "set" to 208,
    "endset" to 212,
    "trans" to 218,
    "pluralize" to 222,
    "endtrans" to 226,
    "with" to 232,
    "endwith" to 236,
    "autoescape" to 242,
    "endautoescape" to 246,
    "import" to 254,
    "from" to 258,
    "include" to 266
)

internal val parser: LRParser = LRParser.deserialize(
    ParserSpec(
        version = 14,
        states = "!*dQVOPOOOOOP'#F`'#F`OeOTO'#CbOvQSO'#CdO!kOPO'#DcO!yOPO'#DnO#XOQO'#DuO#^OPO'#D{O#lOPO'#ESO#zOPO'#E[O\$YOPO'#EaO\$hOPO'#EfO\$vOPO'#EkO%UOPO'#ErO%dOPO'#EwOOOP'#F|'#F|O%rQWO'#E|O&sO#tO'#F]OOOP'#Fq'#FqOOOP'#F_'#F_QVOPOOOOOP-E9^-E9^OOQO'#Ce'#CeO'sQSO,59OO'zQSO'#DWO(RQSO'#DXO(YQ`O'#DZOOQO'#Fr'#FrOvQSO'#CuO(aOPO'#CbOOOP'#Fd'#FdO!kOPO,59}OOOP,59},59}O(oOPO,59}O(}QWO'#E|OOOP,5:Y,5:YO)[OPO,5:YO!yOPO,5:YO)jQWO'#E|OOOQ'#Ff'#FfO)tOQO'#DxO)|OQO,5:aOOOP,5:g,5:gO#^OPO,5:gO*RQWO'#E|OOOP,5:n,5:nO#lOPO,5:nO*YQWO'#E|OOOP,5:v,5:vO#zOPO,5:vO*aQWO'#E|OOOP,5:{,5:{O\$YOPO,5:{O*hQWO'#E|OOOP,5;Q,5;QO\$hOPO,5;QO*oQWO'#E|OOOP,5;V,5;VO*vOPO,5;VO\$vOPO,5;VO+UQWO'#E|OOOP,5;^,5;^O%UOPO,5;^O+`QWO'#E|OOOP,5;c,5;cO%dOPO,5;cO+gQWO'#E|O+nQSO,5;hOvQSO,5:OO+uQSO,5:ZO+zQSO,5:bO+uQSO,5:hO+uQSO,5:oO,PQSO,5:wO,XQpO,5:|O+uQSO,5;RO,^QSO,5;WO,fQSO,5;_OvQSO,5;dOvQSO,5;jOvQSO,5;jOvQSO,5;pOOOO'#Fk'#FkO,nO#tO,5;wOOOP-E9]-E9]O,vQ!bO,59QOvQSO,59TOvQSO,59UOvQSO,59UOvQSO,59UOvQSO,59UO,{QSO'#C}O,XQpO,59cOOQO,59q,59qOOOP1G.j1G.jOvQSO,59UO-SQSO,59UOvQSO,59UOvQSO,59UOvQSO,59nO-wQSO'#FxO.RQSO,59rO.WQSO,59tOOQO,59s,59sO.bQSO'#D[O.iQWO'#F{O.qQWO,59uO0WQSO,59aOOOP-E9b-E9bOOOP1G/i1G/iO(oOPO1G/iO(oOPO1G/iO)TQWO'#E|OvQSO,5:SO0nQSO,5:UO0sQSO,5:WOOOP1G/t1G/tO)[OPO1G/tO)mQWO'#E|O)[OPO1G/tO0xQSO,5:_OOOQ-E9d-E9dOOOP1G/{1G/{O0}QWO'#DyOOOP1G0R1G0RO1SQSO,5:lOOOP1G0Y1G0YO1[QSO,5:tOOOP1G0b1G0bO1aQSO,5:yOOOP1G0g1G0gO1fQSO,5;OOOOP1G0l1G0lO1kQSO,5;TOOOP1G0q1G0qO*vOPO1G0qO+XQWO'#E|O*vOPO1G0qOvQSO,5;YO1pQSO,5;[OOOP1G0x1G0xO1uQSO,5;aOOOP1G0}1G0}O1zQSO,5;fO2PQSO1G1SOOOP1G1S1G1SO2WQSO1G/jOOQO'#Dq'#DqO2_QSO1G/uOOOQ1G/|1G/|O2gQSO1G0SO2rQSO1G0ZO2zQSO'#EVO3SQSO1G0cO,SQSO1G0cO4fQSO'#FvOOQO'#Fv'#FvO5]QSO1G0hO5bQSO1G0mOOOP1G0r1G0rO5mQSO1G0rO5rQSO'#GOO5zQSO1G0yO6PQSO1G1OO6WQSO1G1UO6_QSO1G1UO6fQSO1G1[OOOO-E9i-E9iOOOP1G1c1G1cOOQO1G.l1G.lO6vQSO1G.oO8wQSO1G.pO:oQSO1G.pO:vQSO1G.pO<nQSO1G.pO<uQSO'#FwO>QQSO'#FrO>XQSO'#FwO>aQSO,59iOOQO1G.}1G.}O>fQSO1G.pO@aQSO1G.pOB_QSO1G.pOBfQSO1G.pOD^QSO1G/YOvQSO'#FbODeQSO,5<dOOQO1G/^1G/^ODmQSO1G/_OOQO1G/`1G/`ODuQSO,59vOvQSO'#FcOEjQWO,5<gOOQO1G/a1G/aPErQWO'#E|OOOP7+%T7+%TO(oOPO7+%TOEyQSO1G/nOOOP1G/p1G/pOOOP1G/r1G/rOOOP7+%`7+%`O)[OPO7+%`OOOP1G/y1G/yOFQQSO,5:eOOOP1G0W1G0WOFVQSO1G0WOOOP1G0`1G0`OOOP1G0e1G0eOOOP1G0j1G0jOOOP1G0o1G0oOOOP7+&]7+&]O*vOPO7+&]OF[QSO1G0tOOOP1G0v1G0vOOOP1G0{1G0{OOOP1G1Q1G1QOOOP7+&n7+&nOOOP7+%U7+%UO+uQSO'#FeOFcQSO7+%aOvQSO7+%aOOOP7+%n7+%nOFkQSO7+%nOFpQSO7+%nOOOP7+%u7+%uOFxQSO7+%uOF}QSO'#F}OGQQSO'#F}OGYQSO,5:qOOOP7+%}7+%}OG_QSO7+%}OGdQSO7+%}OOQO,59f,59fOOOP7+&S7+&SOGlQSO7+&oOOOP7+&X7+&XOvQSO7+&oOvQSO7+&^OGtQSO,5<jOvQSO,5<jOOOP7+&e7+&eOOOP7+&j7+&jO+uQSO7+&pOG|QSO7+&pOOOP7+&v7+&vOHRQSO7+&vOHWQSO7+&vOOQO7+\$Z7+\$ZOvQSO'#FaOH]QSO,5<cOvQSO,59jOOQO1G/T1G/TOOQO7+\$[7+\$[OvQSO7+\$tOHeQSO,5;|OOQO-E9`-E9`OOQO7+\$y7+\$yOImQ`O1G/bOIwQSO'#D^OOQO,5;},5;}OOQO-E9a-E9aOOOP<<Ho<<HoOOOP7+%Y7+%YOOOP<<Hz<<HzOOOP1G0P1G0POOOP7+%r7+%rOOOP<<Iw<<IwOOOP7+&`7+&`OOQO,5<P,5<POOQO-E9c-E9cOvQSO<<H{OJOQSO<<H{OOOP<<IY<<IYOJYQSO<<IYOOOP<<Ia<<IaOvQSO,5:rO+uQSO'#FgOJ_QSO,5<iOOQO1G0]1G0]OOOP<<Ii<<IiOJgQSO<<IiOvQSO<<JZOJlQSO<<JZOJsQSO<<IxOvQSO1G2UOJ}QSO1G2UOKXQSO<<J[OK^QSO'#CeOOQO'#FT'#FTOKiQSO'#FTOKnQSO<<J[OKvQSO<<JbOK{QSO<<JbOLWQSO,5;{OLbQSO'#FrOOQO,5;{,5;{OOQO-E9_-E9_OLiQSO1G/UOM}QSO<<H`O! ]Q`O,59aODuQSO,59xO! sQSOAN>gOOOPAN>gAN>gO! }QSOAN>gOOOPAN>tAN>tO!!SQSO1G0^O!!^QSO,5<ROOQO,5<R,5<ROOQO-E9e-E9eOOOPAN?TAN?TO!!iQSOAN?uOOOPAN?uAN?uO+uQSO'#FhO!!pQSOAN?dOOOPAN?dAN?dO!!xQSO7+'pO+uQSO'#FiO!#SQSO7+'pOOOPAN?vAN?vO+uQSO,5;oOG|QSO'#FjO!#[QSOAN?vOOOPAN?|AN?|O!#dQSOAN?|OvQSO,59mO!\$sQ`O1G.pO!%{Q`O1G.pO!&SQ`O1G.pO!'[Q`O1G.pO!'cQ`O'#FvO!'jQ`O1G.pO!(uQ`O1G.pO!*TQ`O1G.pO!*[Q`O1G.pO!+dQ`O1G/YO!+kQ`O1G/dOOOPG24RG24RO!+uQSOG24ROvQSO,5:sOOOPG25aG25aO!+zQSO,5<SOOQO-E9f-E9fOOOPG25OG25OO!,PQSO<<K[O!,XQSO,5<TOOQO-E9g-E9gOOQO1G1Z1G1ZOOQO,5<U,5<UOOQO-E9h-E9hOOOPG25bG25bO!,aQSOG25hO!,fQSO1G/XOOOPLD)mLD)mO!,pQSO1G0_OvQSO1G1nO!,zQSO1G1oOvQSO1G1oOOOPLD+SLD+SO!-wQ`O<<H`O!.[QSO7+'YOvQSO7+'ZO!.fQSO7+'ZO!.pQSO<<JuODuQSO'#CuODuQSO,59UODuQSO,59UODuQSO,59UODuQSO,59UO!.zQpO,59cODuQSO,59UO!/PQSO,59UODuQSO,59UODuQSO,59UODuQSO,59nO!/tQSO1G.pP!0RQSO1G/YODuQSO7+\$tO!0YQ`O1G.pP!0gQ`O1G/YO-SQSO,59UPvQSO,59nO!0nQSO,59aP!3XQSO1G.pP!3`QSO1G.pP!5aQSO1G/YP!5hQSO<<H`O!/PQSO,59UPDuQSO,59nP!6_QSO1G/YP!7pQ`O1G/YO-SQSO'#CuO-SQSO,59UO-SQSO,59UO-SQSO,59UO-SQSO,59UO-SQSO,59UP-SQSO,59UP-SQSO,59UP-SQSO,59nO!7wQSO1G.pO!9xQSO1G.pO!:PQSO1G.pO!;UQSO1G.pP-SQSO7+\$tO!<XQ`O,59aP!>SQ`O1G.pP!>ZQ`O1G.pP!>bQ`O1G/YP!?QQ`O<<H`O!/PQSO'#CuO!/PQSO,59UO!/PQSO,59UO!/PQSO,59UO!/PQSO,59UO!/PQSO,59UP!/PQSO,59UP!/PQSO,59UP!/PQSO,59nP!/PQSO7+\$tP-SQSO,59nP!/PQSO,59nO!?zQ`O1G.pO!@RQ`O1G.pO!@fQ`O1G.pO!@yQ`O1G.p",
        stateData = "!Ac~O\$dOS~OPROQaOR`O\$aPO~O\$aPOPUXQUXRUX\$`UX~OekOfkOjlOpiO!RkO!SkO!TkO!UkO\$gfO\$ihO\$njO~OPROQaORrO\$aPO~OPROQaORvO\$aPO~O\$bwO~OPROQaOR|O\$aPO~OPROQaOR!PO\$aPO~OPROQaOR!SO\$aPO~OPROQaOR!VO\$aPO~OPROQaOR!YO\$aPO~OPROQaOR!^O\$aPO~OPROQaOR!aO\$aPO~OPROQaOR!dO\$aPO~O!X!eO!Y!fO!d!gO!k!hO!q!iO!x!jO#Q!kO#V!lO#[!mO#a!nO#h!oO#m!pO#s!qO#u!rO#y!sO~O\$s!tO~OZ!wO_!yO`!zOa!{Ob!|Oc#ROd#SOg#TOh#UOl#OOp!}Ow#VO\$i!xO~OV#QO~P&xO\$h\$lP~PvOo#ZO~PvO\$m\$oP~PvO\$aPOPUXQUXRUX~OPROQaOR#dO\$aPO~O!]#eO!_#fO!a#gO~P%rOPROQaOR#jO\$aPO~O!_#fO!h#lO~P%rO\$bwOS!lX~OS#oO~O!u#qO~P%rO!}#sO~P%rO#S#uO~P%rO#X#wO~P%rO#^#yO~P%rOPROQaOR#|O\$aPO~O#c\$OO#e\$PO~P%rO#j\$RO~P%rO#o\$TO~P%rO!Z\$VO~PvO\$g\$XO~O!Z\$ZO~Op\$^O\$gfO~Om\$aO~O!Z\$eO\$g\$XO~O\$g\$XO!Z\$rP~O\$Q\$nO\$s!tO~O[\$oO~Oo\$kP~PvOekOfkOj)fOpiO!RkO!SkO!TkO!UkO\$gfO\$ihO\$njO~Ot%PO\$h\$lX~P&xO\$h%RO~Oo%TOt%PO~P&xO!P%UO~P&xOt%VO\$m\$oX~O\$m%XO~OZ!wOp!}O\$i!xOViagiahialiawiatia\$hiaoia!Pia!Zia#tia#via#zia#|ia#}iaxia!fia~O_!yO`!zOa!{Ob!|Oc#ROd#SO~P.vO!Z%^O~O!Z%_O~O!Z%bO~O!n%cO~O!Z%dO\$g\$XO~O!Z%fO~O!Z%gO~O!Z%hO~O!Z%iO~O!Z%mO~O!Z%nO~O!Z%oO~O!Z%pO~P&xO!Z%qO~P&xOc%tOt%rO~O!Z%uO!r%wO!s%vO~Op\$^O!Z%xO~O\$g\$XOo\$qP~Op!}O!Z%}O~Op!}OZ\$jX_\$jX`\$jXa\$jXb\$jXc\$jXd\$jXg\$jXh\$jXl\$jXw\$jX\$i\$jXt\$jXe\$jXf\$jX\$g\$jXx\$jX~O!Z\$jXV\$jX\$h\$jXo\$jX!P\$jX#t\$jX#v\$jX#z\$jX#|\$jX#}\$jX!f\$jX~P3[O!Z&RO~Os&UOt%rO!Z&TO~Os&VO~Os&XOt%rO~O!Z&YO~O!Z&ZO~P&xO#t&[O~P&xO#v&]O~P&xO!Z&^O#z&`O#|&_O#}&_O~P&xO\$h&aO~P&xOZ!wOp!}O\$i!xOV^i`^ia^ib^ic^id^ig^ih^il^iw^it^i\$h^io^i!P^i!Z^i#t^i#v^i#z^i#|^i#}^ie^if^i\$g^ix^i!f^i~O_^i~P6}OZ!wO_!yOp!}O\$i!xOV^ia^ib^ic^id^ig^ih^il^iw^it^i\$h^io^i!P^i!Z^i#t^i#v^i#z^i#|^i#}^ix^i!f^i~O`^i~P9OO`!zO~P9OOZ!wO_!yO`!zOa!{Op!}O\$i!xOV^ic^id^ig^ih^il^iw^it^i\$h^io^i!P^i!Z^i#t^i#v^i#z^i#|^i#}^ix^i!f^i~Ob^i~P:}Ot&bOo\$kX~P&xOZ\$fX_\$fX`\$fXa\$fXb\$fXc\$fXd\$fXg\$fXh\$fXl\$fXo\$fXp\$fXt\$fXw\$fX\$i\$fX~Os&dO~P=POt&bOo\$kX~Oo&eO~Ob!|O~P:}OZ!wO_)gO`)hOa)iOb)jOc)kOp!}O\$i!xOV^id^ig^ih^il^iw^it^i\$h^io^i!P^i!Z^i#t^i#v^i#z^i#|^i#}^ix^i!f^i~Oe&fOf&fO\$gfO~P>mOZ!wO_!yO`!zOa!{Ob!|Oc#ROd#SOp!}O\$i!xOV^ih^il^iw^it^i\$h^io^i!P^i!Z^i#t^i#v^i#z^i#|^i#}^ix^i!f^i~Og^i~P@nOg#TO~P@nOZ!wO_!yO`!zOa!{Ob!|Oc#ROd#SOg#TOh#UOp!}O\$i!xOVvilviwvitvi\$hviovi!Pvi!Zvi#tvi#vvi#zvi#|vi#}vi!fvi~Ox&gO~PBmOt%PO\$h\$la~Oo&jOt%PO~OekOfkOj(yOpiO!RkO!SkO!TkO!UkO\$gfO\$ihO\$njO~Ot%VO\$m\$oa~O!]#eO~P%rO!Z&pO~P&xO!Z&rO~O!Z&sO~O!Z&uO~P&xOc&xOt%rO~O!Z&zO~O!Z&zO!s&{O~O!Z&|O~Os&}Ot'OOo\$qX~Oo'QO~O!Z'RO~Op!}O!Z'RO~Os'TOt%rO~Os'WOt%rO~O\$g'ZO~O\$O'_O~O#{'`O~Ot&bOo\$ka~Ot\$Ua\$h\$Uao\$Ua~P&xOZ!wO_(zO`({Oa(|Ob(}Oc)POd)QOg)ROh)SOl)OOp!}Ow)TO\$i!xO~Ot!Oi\$m!Oi~PHrO!P'hO~P&xO!Z'jO!f'kO~P&xO!Z'lO~Ot'OOo\$qa~O!Z'qO~O!Z'sO~P&xOt'tO!Z'vO~P&xOt'xO!Z\$ri~P&xO!Z'zO~Ot!eX!Z!eX#tXX~O#t'{O~Ot'|O!Z'zO~O!Z(OO~O!Z(OO#|(PO#}(PO~Oo\$Tat\$Ta~P&xOs(QO~P=POoritri~P&xOZ!wOp!}O\$i!xOVvylvywvytvy\$hvyovy!Pvy!Zvy#tvy#vvy#zvy#|vy#}vyxvy!fvy~O_!yO`!zOa!{Ob!|Oc#ROd#SOg#TOh#UO~PLsOZ!wOp!}O\$i!xOgiahialiatiawia\$miaxia~O_(zO`({Oa(|Ob(}Oc)POd)QO~PNkO!Z(^O!f(_O~P&xO!Z(^O~Oo!zit!zi~P&xOs(`Oo\$Zat\$Za~O!Z(aO~P&xOt'tO!Z(dO~Ot'xO!Z\$rq~P&xOt'xO!Z\$rq~Ot'|O!Z(kO~O\$O(lO~OZ!wOp!}O\$i!xO`^ia^ib^ic^id^ig^ih^il^it^iw^i\$m^ie^if^i\$g^ix^i~O_^i~P!#iOZ!wO_(zOp!}O\$i!xOa^ib^ic^id^ig^ih^il^it^iw^i\$m^ix^i~O`^i~P!\$zO`({O~P!\$zOZ!wO_(zO`({Oa(|Op!}O\$i!xOc^id^ig^ih^il^it^iw^i\$m^ix^i~Ob^i~P!&ZO\$m\$jX~P3[Ob(}O~P!&ZOZ!wO_)zO`){Oa)|Ob)}Oc*OOp!}O\$i!xOd^ig^ih^il^it^iw^i\$m^ix^i~Oe&fOf&fO\$gfO~P!'qOZ!wO_(zO`({Oa(|Ob(}Oc)POd)QOp!}O\$i!xOh^il^it^iw^i\$m^ix^i~Og^i~P!)SOg)RO~P!)SOZ!wO_(zO`({Oa(|Ob(}Oc)POd)QOg)ROh)SOp!}O\$i!xOlvitviwvi\$mvi~Ox)WO~P!*cOt!Qi\$m!Qi~PHrO!Z(nO~Os(pO~Ot'xO!Z\$ry~Os(rOt%rO~O!Z(sO~Oouitui~P&xOo!{it!{i~P&xOs(vOt%rO~OZ!wO_(zO`({Oa(|Ob(}Oc)POd)QOg)ROh)SOp!}O\$i!xO~Olvytvywvy\$mvyxvy~P!-SOt\$[q!Z\$[q~P&xOt\$]q!Z\$]q~P&xOt\$]y!Z\$]y~P&xOm(VO~OekOfkOj)yOpiO!RkO!SkO!TkO!UkO\$gfO\$ihO\$njO~Oe^if^i\$g^i~P>mOxvi~PBmOe^if^i\$g^i~P!'qOxvi~P!*cO_)gO`)hOa)iOb)jOc)kOd)ZOeiafia\$gia~P.vOZ!wO_)gO`)hOa)iOb)jOc)kOd)ZOp!}O\$i!xOV^ie^if^ih^il^iw^i\$g^it^i\$h^io^i!P^i!Z^i#t^i#v^i#z^i#|^i#}^ix^i!f^i~Og^i~P!1_Og)lO~P!1_OZ!wO_)gO`)hOa)iOb)jOc)kOd)ZOg)lOh)mOp!}O\$i!xOVvievifvilviwvi\$gvitvi\$hviovi!Pvi!Zvi#tvi#vvi#zvi#|vi#}vi!fvi~Ox)sO~P!3gO_)gO`)hOa)iOb)jOc)kOd)ZOg)lOh)mOevyfvy\$gvy~PLsOxvi~P!3gOZ!wO_)zO`){Oa)|Ob)}Oc*OOd)bOg*POh*QOp!}O\$i!xOevifvilvitviwvi\$gvi\$mvi~Oxvi~P!6fO_)gO~P6}OZ!wO_)gO`)hOp!}O\$i!xOV^ib^ic^id^ie^if^ig^ih^il^iw^i\$g^it^i\$h^io^i!P^i!Z^i#t^i#v^i#z^i#|^i#}^ix^i!f^i~Oa^i~P!8OOa)iO~P!8OOZ!wOp!}O\$i!xOc^id^ie^if^ig^ih^il^iw^i\$g^it^ix^i~O_)gO`)hOa)iOb)jOV^i\$h^io^i!P^i!Z^i#t^i#v^i#z^i#|^i#}^i!f^i~P!:WO_)zO`){Oa)|Ob)}Oc*OOd)bOeiafia\$gia~PNkOZ!wO_)zO`){Oa)|Ob)}Oc*OOd)bOp!}O\$i!xOe^if^ih^il^it^iw^i\$g^i\$m^ix^i~Og^i~P!<xOg*PO~P!<xOx*SO~P!6fOZ!wO_)zO`){Oa)|Ob)}Op!}O\$i!xO~Oc*OOd)bOg*POh*QOevyfvylvytvywvy\$gvy\$mvyxvy~P!>iO_)zO~P!#iO_)zO`){Oa^ib^i\$m^i~P!:WO_)zO`){Oa)|Ob^i\$m^i~P!:WO_)zO`){Oa)|Ob)}O\$m^i~P!:WOfaZa~",
        goto = "Cy\$sPPPPPP\$tP\$t%j'sPP's'sPPPPPPPPPP'sP'sPP)jPP)o+nPP+q'sPP's's's's's+tP+wPPPP+z,pPPP-fP-jP-vP+z.UP.zP/zP+z0YP1O1RP+z1UPPP1zP+z2QP2v2|3P3SP+z3YP4OP+z4UP4zP+z5QP5vP+z5|P6rP6xP+z7WP7|P+z8SP8xP\$t\$t\$tPPPP9O\$tPPPPPP\$tP9U:j;f;m;w;}<T<g<m<t<z=U=[PPPPP=b>YPPPCcCjCmPPCp\$tCsCv",
        nodeNames = "\u26A0 {{ {# {% {% Template Text }} Interpolation VariableName MemberExpression . PropertyName SubscriptExpression BinaryExpression ConcatOp ArithOp ArithOp CompareOp in is StringLiteral NumberLiteral and or NotExpression not FilterExpression FilterOp FilterName FilterCall ) ( ArgumentList NamedArgument AssignOp , NamedArgument ConditionalExpression if else CallExpression ArrayExpression TupleExpression ParenthesizedExpression DictExpression Entry : Entry BooleanLiteral self super loop IfStatement Tag TagName if %} Tag elif Tag else EndTag endif ForStatement Tag for Definition recursive EndTag endfor RawStatement Tag raw RawText EndTag endraw BlockStatement Tag block scoped required EndTag endblock MacroStatement Tag macro ParamList OptionalParameter OptionalParameter EndTag endmacro CallStatement Tag call EndTag endcall FilterStatement Tag filter EndTag endfilter SetStatement Tag set EndTag endset TransStatement Tag trans Tag pluralize EndTag endtrans WithStatement Tag with EndTag endwith AutoescapeStatement Tag autoescape EndTag endautoescape Tag Tag Tag import as from import ImportItem Tag include ignore missing with without context Comment #}",
        maxTerm = 173,
        nodeProps = listOf(
            listOf("closedBy", 1, "}}", 2, "#}", -2, 3, 4, "%}", 32, ")"),
            listOf("openedBy", 7, "{{", 31, "(", 57, "{%", 140, "{#"),
            listOf(
                "group",
                -18, 9, 10, 13, 14, 21, 22, 25, 27, 38, 41, 42, 43, 44, 45, 49, 50, 51, 52,
                "Expression",
                -11, 53, 64, 71, 77, 84, 92, 97, 102, 107, 114, 119, "Statement"
            )
        ),
        skippedNodes = listOf(0),
        repeatNodeCount = 13,
        tokenData = ".|~RqXY#YYZ#Y]^#Ypq#Yqr#krs#vuv&nwx&{xy)nyz)sz{)x{|*V|}+|}!O,R!O!P,g!P!Q,o!Q![+h![!],w!^!_,|!_!`-U!`!a,|!c!}-^!}#O.U#P#Q.Z#R#S-^#T#o-^#o#p.`#p#q.e#q#r.j#r#s.w%W;'S-^;'S;:j.O<%lO-^~#_S\$d~XY#YYZ#Y]^#Ypq#Y~#nP!_!`#q~#vOb~~#yWOY#vZr#vrs\$cs#O#v#O#P\$h#P;'S#v;'S;=`%x<%lO#v~\$hOe~~\$kYOY#vYZ#vZr#vrs%Zs#O#v#O#P\$h#P;'S#v;'S;=`&O;=`<%l#v<%lO#v~%`We~OY#vZr#vrs\$cs#O#v#O#P\$h#P;'S#v;'S;=`%x<%lO#v~%{P;=`<%l#v~&RXOY#vZr#vrs\$cs#O#v#O#P\$h#P;'S#v;'S;=`%x;=`<%l#v<%lO#v~&sP`~#q#r&v~&{O!Z~~'OWOY&{Zw&{wx\$cx#O&{#O#P'h#P;'S&{;'S;=`(x<%lO&{~'kYOY&{YZ&{Zw&{wx(Zx#O&{#O#P'h#P;'S&{;'S;=`)O;=`<%l&{<%lO&{~(`We~OY&{Zw&{wx\$cx#O&{#O#P'h#P;'S&{;'S;=`(x<%lO&{~({P;=`<%l&{~)RXOY&{Zw&{wx\$cx#O&{#O#P'h#P;'S&{;'S;=`(x;=`<%l&{<%lO&{~)sOp~~)xOo~~)}P`~z{*Q~*VO`~~*[Qa~!O!P*b!Q![+h~*eP!Q![*h~*mSf~!Q![*h!g!h*y#R#S*h#X#Y*y~*|R{|+V}!O+V!Q![+]~+YP!Q![+]~+bQf~!Q![+]#R#S+]~+mTf~!O!P*b!Q![+h!g!h*y#R#S+h#X#Y*y~,ROt~~,WRa~uv,a!O!P*b!Q![+h~,dP#q#r&v~,lPZ~!Q![*h~,tP`~!P!Q*Q~,|O!P~~-RPb~!_!`#q~-ZPs~!_!`#q!`-iVm`[p!XS\$gY!Q![-^!c!}-^#R#S-^#T#o-^%W;'S-^;'S;:j.O<%lO-^!`.RP;=`<%l-^~.ZO\$i~~.`O\$h~~.eO\$n~~.jOl~^.oP\$m[#q#r.rQ.wOVQ~.|O_~",
        tokenizers = listOf(
            base,
            raw,
            1,
            2,
            3,
            4,
            5,
            LocalTokenGroup("b~RPstU~XP#q#r[~aO\$Q~~", 17, 173)
        ),
        topRules = mapOf("Template" to listOf(0, 5)),
        specialized = listOf(
            SpecializerSpec(term = 161, get = { value, _ -> specIdentifier[value] ?: -1 }),
            SpecializerSpec(term = 55, get = { value, _ -> specTagName[value] ?: -1 })
        ),
        tokenPrec = 3602
    )
)
