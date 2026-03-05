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

import com.monkopedia.kodemirror.language.IndentContext
import com.monkopedia.kodemirror.language.StreamParser
import com.monkopedia.kodemirror.language.StringStream

private val sqlKeywordsStr =
    "alter and as asc between by count create delete " +
        "desc distinct drop from group having in " +
        "insert into is join like not on or order " +
        "select set table union update values where limit"

private val sqlDefaultBuiltin =
    "bool boolean bit blob enum long longblob longtext " +
        "medium mediumblob mediumint mediumtext " +
        "time timestamp tinyblob tinyint tinytext text " +
        "bigint int int1 int2 int3 int4 int8 integer " +
        "float float4 float8 double char varbinary " +
        "varchar varcharacter precision real date " +
        "datetime year unsigned signed decimal numeric"

private fun sqlSet(str: String): Set<String> =
    str.trim().split(" ").filter { it.isNotEmpty() }.toSet()

private fun sqlHookIdentifier(stream: StringStream): String? {
    var ch: String?
    while (true) {
        ch = stream.next() ?: break
        if (ch == "`" && stream.eat("`") == null) return "string.special"
    }
    stream.backUp(stream.current().length - 1)
    return if (stream.eatWhile(Regex("\\w"))) "string.special" else null
}

private fun sqlHookIdentifierDoublequote(stream: StringStream): String? {
    var ch: String?
    while (true) {
        ch = stream.next() ?: break
        if (ch == "\"" && stream.eat("\"") == null) return "string.special"
    }
    stream.backUp(stream.current().length - 1)
    return if (stream.eatWhile(Regex("\\w"))) "string.special" else null
}

private fun sqlHookVar(stream: StringStream): String? {
    if (stream.eat("@") != null) {
        stream.match("session.")
        stream.match("local.")
        stream.match("global.")
    }
    return when {
        stream.eat("'") != null -> {
            stream.match(Regex("^.*'"))
            "string.special"
        }
        stream.eat("\"") != null -> {
            stream.match(Regex("^.*\""))
            "string.special"
        }
        stream.eat("`") != null -> {
            stream.match(Regex("^.*`"))
            "string.special"
        }
        stream.match(Regex("^[0-9a-zA-Z\$._]+")) != null -> "string.special"
        else -> null
    }
}

private fun sqlHookClient(stream: StringStream): String? {
    if (stream.eat("N") != null) return "atom"
    return if (stream.match(Regex("^[a-zA-Z.#!?]")) != null) "string.special" else null
}

data class SqlContext(
    val prev: SqlContext?,
    val indent: Int,
    val col: Int,
    val type: String,
    var align: Boolean? = null
)

data class SqlLegacyState(
    var tokenizeTag: String = "base",
    var context: SqlContext? = null
)

internal class SqlParserConfig(
    val client: Set<String> = emptySet(),
    val atoms: Set<String> = setOf("false", "true", "null"),
    val builtin: Set<String> = sqlSet(sqlDefaultBuiltin),
    val keywords: Set<String> = sqlSet(sqlKeywordsStr),
    val operatorChars: Regex = Regex("^[*+\\-%<>!=&|~^/]"),
    val support: Set<String> = emptySet(),
    val hooks: Map<String, (StringStream) -> String?> = emptyMap(),
    val dateSQL: Set<String> = setOf("date", "time", "timestamp"),
    val backslashStringEscapes: Boolean = true,
    val brackets: Regex = Regex("^[{}()\\[\\]]"),
    val punctuation: Regex = Regex("^[;.,:]"),
    val commentTokenLine: String = "--",
    val name: String = "sql"
)

private fun makeSqlParser(config: SqlParserConfig): StreamParser<SqlLegacyState> {
    // State for current tokenize string/comment mode and depth
    // We encode tokenize state as strings: "base", "string:X:b" (quote+backslash), "comment:N"
    fun decodeToken(state: SqlLegacyState, stream: StringStream): String? {
        val tag = state.tokenizeTag
        return when {
            tag == "base" -> sqlTokenBase(stream, state, config)
            tag.startsWith("string:") -> {
                val parts = tag.split(":")
                val quote = parts[1]
                val backslash = parts[2] == "1"
                sqlTokenLiteral(stream, state, config, quote, backslash)
            }
            tag.startsWith("comment:") -> {
                val depth = tag.split(":")[1].toInt()
                sqlTokenComment(stream, state, depth)
            }
            else -> null
        }
    }

    return object : StreamParser<SqlLegacyState> {
        override val name: String get() = config.name

        override fun startState(indentUnit: Int) = SqlLegacyState()

        override fun copyState(state: SqlLegacyState) = state.copy()

        override fun token(stream: StringStream, state: SqlLegacyState): String? {
            if (stream.sol()) {
                if (state.context != null && state.context!!.align == null) {
                    state.context!!.align = false
                }
            }
            if (state.tokenizeTag == "base" && stream.eatSpace()) return null
            val style = decodeToken(state, stream)
            if (style == "comment") return style
            if (state.context != null && state.context!!.align == null) {
                state.context!!.align = true
            }
            val tok = stream.current()
            when (tok) {
                "(" -> state.context = SqlContext(
                    state.context, stream.indentation(), stream.column(), ")"
                )
                "[" -> state.context = SqlContext(
                    state.context, stream.indentation(), stream.column(), "]"
                )
                else -> if (state.context != null && state.context!!.type == tok) {
                    state.context = state.context!!.prev
                }
            }
            return style
        }

        override fun indent(
            state: SqlLegacyState,
            textAfter: String,
            context: IndentContext
        ): Int? {
            val cx = state.context ?: return null
            val closing = textAfter.isNotEmpty() && textAfter[0].toString() == cx.type
            return if (cx.align == true) {
                cx.col + (if (closing) 0 else 1)
            } else {
                cx.indent + (if (closing) 0 else context.unit)
            }
        }

        override val languageData: Map<String, Any>
            get() = mapOf(
                "commentTokens" to mapOf(
                    "line" to config.commentTokenLine,
                    "block" to mapOf("open" to "/*", "close" to "*/")
                ),
                "closeBrackets" to mapOf(
                    "brackets" to listOf("(", "[", "{", "'", "\"", "`")
                )
            )
    }
}

@Suppress("CyclomaticComplexMethod", "LongMethod", "ReturnCount")
private fun sqlTokenBase(
    stream: StringStream,
    state: SqlLegacyState,
    config: SqlParserConfig
): String? {
    val ch = stream.next() ?: return null

    val hookResult = config.hooks[ch]?.invoke(stream)
    if (hookResult != null) return hookResult

    if (config.support.contains("hexNumber") &&
        (
            (ch == "0" && stream.match(Regex("^[xX][0-9a-fA-F]+")) != null) ||
                ((ch == "x" || ch == "X") && stream.match(Regex("^'[0-9a-fA-F]*'")) != null)
            )
    ) {
        return "number"
    } else if (config.support.contains("binaryNumber") &&
        (
            ((ch == "b" || ch == "B") && stream.match(Regex("^'[01]+'")) != null) ||
                (ch == "0" && stream.match(Regex("^b[01]*")) != null)
            )
    ) {
        return "number"
    } else if (ch[0].code in 48..57) {
        stream.match(Regex("^[0-9]*(\\.[0-9]+)?([eE][-+]?[0-9]+)?"))
        if (config.support.contains("decimallessFloat")) stream.match(Regex("^\\.(?!\\.)"))
        return "number"
    } else if (ch == "?" && (stream.eatSpace() || stream.eol() || stream.eat(";") != null)) {
        return "macroName"
    } else if (ch == "'" || (ch == "\"" && config.support.contains("doubleQuote"))) {
        state.tokenizeTag = "string:$ch:${if (config.backslashStringEscapes) 1 else 0}"
        return sqlTokenLiteral(stream, state, config, ch, config.backslashStringEscapes)
    } else if (config.support.contains("nCharCast") && (ch == "n" || ch == "N") &&
        (stream.peek() == "'" || stream.peek() == "\"")
    ) {
        return "keyword"
    } else if (config.support.contains("commentSlashSlash") &&
        ch == "/" && stream.eat("/") != null
    ) {
        stream.skipToEnd()
        return "comment"
    } else if ((config.support.contains("commentHash") && ch == "#") ||
        (
            ch == "-" && stream.eat("-") != null &&
                (!config.support.contains("commentSpaceRequired") || stream.eat(" ") != null)
            )
    ) {
        stream.skipToEnd()
        return "comment"
    } else if (ch == "/" && stream.eat("*") != null) {
        state.tokenizeTag = "comment:1"
        return sqlTokenComment(stream, state, 1)
    } else if (ch == ".") {
        if (config.support.contains("zerolessFloat") &&
            stream.match(Regex("^(?:\\d+(?:e[+-]?\\d+)?)", RegexOption.IGNORE_CASE)) != null
        ) {
            return "number"
        }
        if (stream.match(Regex("^\\.+")) != null) return null
        val odbcMatch = stream.match(Regex("^[\\w\\d_\$#]+"))
        if (config.support.contains("ODBCdotTable") && odbcMatch != null) {
            return "type"
        }
    } else if (config.operatorChars.containsMatchIn(ch)) {
        stream.eatWhile(config.operatorChars)
        return "operator"
    } else if (config.brackets.containsMatchIn(ch)) {
        return "bracket"
    } else if (config.punctuation.containsMatchIn(ch)) {
        stream.eatWhile(config.punctuation)
        return "punctuation"
    } else {
        stream.eatWhile(Regex("^[_\\w\\d]"))
        val word = stream.current().lowercase()
        if (config.dateSQL.contains(word) &&
            (
                stream.match(Regex("^( )+'[^']*'")) != null ||
                    stream.match(Regex("^( )+\"[^\"]*\"")) != null
                )
        ) {
            return "number"
        }
        if (config.atoms.contains(word)) return "atom"
        if (config.builtin.contains(word)) return "type"
        if (config.keywords.contains(word)) return "keyword"
        if (config.client.contains(word)) return "builtin"
        return null
    }
    return null
}

private fun sqlTokenLiteral(
    stream: StringStream,
    state: SqlLegacyState,
    config: SqlParserConfig,
    quote: String,
    backslashEscapes: Boolean
): String {
    var escaped = false
    var ch: String?
    while (true) {
        ch = stream.next() ?: break
        if (ch == quote && !escaped) {
            state.tokenizeTag = "base"
            break
        }
        escaped = (config.backslashStringEscapes || backslashEscapes) && !escaped && ch == "\\"
    }
    return "string"
}

private fun sqlTokenComment(stream: StringStream, state: SqlLegacyState, depth: Int): String {
    val m = stream.match(Regex("^.*?(/\\*|\\*/)")) as? MatchResult
    if (m == null) {
        stream.skipToEnd()
    } else {
        when {
            m.groupValues[1] == "/*" -> state.tokenizeTag = "comment:${depth + 1}"
            depth > 1 -> state.tokenizeTag = "comment:${depth - 1}"
            else -> state.tokenizeTag = "base"
        }
    }
    return "comment"
}

val standardSQL: StreamParser<SqlLegacyState> = makeSqlParser(
    SqlParserConfig(
        keywords = sqlSet("$sqlKeywordsStr begin"),
        builtin = sqlSet(sqlDefaultBuiltin),
        atoms = sqlSet("false true null unknown"),
        dateSQL = sqlSet("date time timestamp"),
        support = sqlSet("ODBCdotTable doubleQuote binaryNumber hexNumber"),
        name = "sql"
    )
)

val mySQL: StreamParser<SqlLegacyState> = makeSqlParser(
    SqlParserConfig(
        client = sqlSet(
            "charset clear connect edit ego exit go help nopager notee nowarning pager print " +
                "prompt quit rehash source status system tee"
        ),
        keywords = sqlSet(
            "$sqlKeywordsStr accessible action add after algorithm all analyze asensitive at " +
                "auto_increment autocommit avg avg_row_length before binary binlog both btree " +
                "cache call cascade case change character check collate column columns " +
                "comment commit committed concurrent condition connection consistent " +
                "constraint continue cross current_date current_time current_timestamp " +
                "current_user cursor data database databases day_hour day_microsecond " +
                "day_minute day_second deallocate dec declare default delay_key_write " +
                "delayed delimiter deterministic disable discard div dual each elseif enable " +
                "enclosed end ends engine engines enumerrorsescape escaped event events " +
                "every execute exists exit explain extended fastfetchfield fields first " +
                "flush for force foreign fulltext function general get global grant handler " +
                "hash help high_priority hosts hour_microsecond hour_minute hour_second if " +
                "ignore import index infile inner innodb inout insensitiveintervalkey keys " +
                "kill language last leading leave left level linear lines list " +
                "loadlocallocaltime localtimestamp lock logs low_priority master max " +
                "max_rows minute_microsecond minute_second mod mode modifies modify natural " +
                "next no no_write_to_binlog offset on open optimize option optionally out " +
                "outer outfile pack_keys parser partition partitions password plugin plugins " +
                "prepare preserve primary privileges procedure processlist profiles query " +
                "quick range read reads real rebuild recover references regexp relaylog " +
                "release remove rename repair repeatable replace require resignal restrict " +
                "return returns revoke right rlike rollback rollup row savepoint schemas " +
                "second_microsecond security sensitive separator serializable server session " +
                "share show signal slave slow snapshot soname spatial specific sql " +
                "sql_big_result sql_buffer_result sql_cache sql_no_cache sql_small_result " +
                "sqlexception sqlstate sqlwarning ssl startstartingstarts status std stddev " +
                "stddev_pop stddev_samp storage straight_join suspend table tablespace " +
                "temporary terminated then to trailing transaction trigger triggers truncate " +
                "uncommitted undo uninstall unique unlock upgrade use utc_date utc_time " +
                "utc_timestamp value variables varying view warnings whenwhilewith work " +
                "write xa xor year_month zerofill begin do else loop repeat"
        ),
        builtin = sqlSet(
            "bool boolean bit blob decimal double float long longblob longtext medium mediumblob " +
                "mediumint mediumtext time timestamp tinyblob tinyint tinytext text bigint int " +
                "int1 int2 int3 int4 int8 integer float float4 float8 double char varbinary " +
                "varchar varcharacter precision date datetime year unsigned signed numeric"
        ),
        atoms = sqlSet("false true null unknown"),
        operatorChars = Regex("^[*+\\-%<>!=&|^]"),
        dateSQL = sqlSet("date time timestamp"),
        support = sqlSet(
            "ODBCdotTable decimallessFloat zerolessFloat binaryNumber hexNumber doubleQuote " +
                "nCharCast charsetCast commentHash commentSpaceRequired"
        ),
        hooks = mapOf(
            "@" to ::sqlHookVar,
            "`" to ::sqlHookIdentifier,
            "\\" to ::sqlHookClient
        ),
        commentTokenLine = "#",
        name = "mysql"
    )
)

val mariaDB: StreamParser<SqlLegacyState> = makeSqlParser(
    SqlParserConfig(
        keywords = sqlSet(
            "$sqlKeywordsStr accessible action add after all alter analyze " +
                "asensitiveauto_incrementautocommit before binary cache call cascade case change " +
                "character check collate column columns comment commit concurrent condition " +
                "connection constraintcontinuecross current_date current_time current_timestamp " +
                "cursor data databasedatabasesdeallocate dec declare default delay_key_write " +
                "delayed delimiter deterministic disable discard div dual each elseif enable " +
                "enclosed end ends engine enginesenumerrors escape escaped event events " +
                "every execute exists exit explain extendedfastfetch fields first flush for " +
                "force foreign fulltext function general get global grant handler hash help " +
                "high_priority hosts hour_microsecond hour_minute hour_second if ignore " +
                "import index infile inner innodb inout insensitiveintervalkey keys kill " +
                "language last leading leave left level linear lines list loadlocallock logs " +
                "low_priority master max max_rows minute_microsecond minute_second mod mode " +
                "modifies modify natural next no no_write_to_binlog offset on open optimize " +
                "option optionally out outer outfile pack_keys parser partition " +
                "partitionspasswordplugin plugins prepare preserve primary privileges " +
                "procedure processlistprofilesquery quick range read reads real rebuild " +
                "recover references regexp relaylog release remove rename repair repeatable " +
                "replace require resignal restrictreturnreturns revoke right rlike rollback " +
                "rollup row savepoint schemassecond_microsecondsecurity sensitive separator " +
                "serializable server session share show signalslaveslow snapshot soname " +
                "spatial specific sql sql_big_result sql_buffer_result sql_cache " +
                "sql_no_cache sql_small_result sqlexception sqlstate sqlwarning ssl start " +
                "starting starts status std stddev stddev_pop stddev_samp storage " +
                "straight_join suspend table tablespace temporary terminated then to " +
                "trailing transaction trigger triggers truncate uncommitted undo uninstall " +
                "unique unlock upgrade use utc_date utc_time utc_timestamp value variables " +
                "varying viewwarningswhen while with work write xa xor year_month zerofill " +
                "begin do else loop repeat elsif exception raise"
        ),
        builtin = sqlSet(
            "bool boolean bit blob decimal double float long longblob longtext medium mediumblob " +
                "mediumint mediumtext time timestamp tinyblob tinyint tinytext text bigint int " +
                "integer float double char varbinary varchar precision date datetime " +
                "yearunsignedsigned numeric"
        ),
        atoms = sqlSet("false true null unknown"),
        operatorChars = Regex("^[*+\\-%<>!=&|^]"),
        dateSQL = sqlSet("date time timestamp"),
        support = sqlSet(
            "ODBCdotTable decimallessFloat zerolessFloat binaryNumber hexNumber doubleQuote " +
                "nCharCast commentHash commentSpaceRequired"
        ),
        hooks = mapOf(
            "@" to ::sqlHookVar,
            "`" to ::sqlHookIdentifier,
            "\\" to ::sqlHookClient
        ),
        commentTokenLine = "#",
        name = "mariadb"
    )
)

val sqlite: StreamParser<SqlLegacyState> = makeSqlParser(
    SqlParserConfig(
        keywords = sqlSet(
            "$sqlKeywordsStr abort action add after all alter analyze attach autoincrement " +
                "before begin cascade case cast check cluster collate column comment commit " +
                "conflict constraint cross current_date current_time current_timestamp " +
                "database deferred deferrable detach each else end escape except exclusive " +
                "explain fail for foreign full glob if ignore immediate inner instead " +
                "intersect isnull key left like match natural no notnull null of offset on " +
                "outer plan pragma primary query raise recursive references regexp reindex " +
                "relative release rename replace restrict right rollback row savepoint temp " +
                "temporary then to transaction trigger unique using vacuum view virtual when " +
                "with without"
        ),
        builtin = sqlSet(
            "bool boolean bit blob decimal double float long longblob longtext medium mediumblob " +
                "mediumint mediumtext time timestamp tinyblob tinyint tinytext text bigint int " +
                "integer double char varbinary varchar precision date datetime year unsigned " +
                "signed numeric"
        ),
        atoms = sqlSet("false true null on off"),
        support = sqlSet(
            "decimallessFloat zerolessFloat binaryNumber " +
                "hexNumber doubleQuote commentHash"
        ),
        hooks = mapOf("`" to ::sqlHookIdentifier),
        name = "sqlite"
    )
)

val cassandra: StreamParser<SqlLegacyState> = makeSqlParser(
    SqlParserConfig(
        keywords = sqlSet(
            "add all allow alter and any apply as asc authorize batch begin by clustering " +
                "columnfamily compact consistency count create custom delete desc distinct drop " +
                "each_quorum exists filtering from full grant if in index insert into key " +
                "keyspace keyspaces level limit local_one local_quorum materialized modify " +
                "nan norecursive not of on one order password permission permissions primary " +
                "quorum rename replace revoke schema select set storage super table three " +
                "timestamp to token truncate ttl two type unlogged update use users using " +
                "values where with writetime infinity mbean mbeans"
        ),
        builtin = sqlSet(
            "ascii bigint blob boolean counter decimal double float frozen inet int list map " +
                "smallint static text timestamp timeuuid tinyint tuple uuid varchar varint"
        ),
        atoms = sqlSet("false true null"),
        operatorChars = Regex("^[<>=]"),
        name = "cassandra"
    )
)

val plSQL: StreamParser<SqlLegacyState> = makeSqlParser(
    SqlParserConfig(
        keywords = sqlSet(
            "$sqlKeywordsStr all alter and any array as asc at authid avg begin between " +
                "binary_integer body boolean bulk by case char check cluster collect column " +
                "comment commit compress connect constant create current currval cursor data " +
                "database date day declare default delete desc deterministic distinct drop " +
                "else elsif end exception exclusive execute exists exit extend external " +
                "false fetch float for forall from function goto grant group having heap " +
                "hour if immediate in index indicator inner insert integer intersect " +
                "interval into is isolation java join key level like limit lock log long " +
                "loop max maxextents merge min minus minute mlslabel mode month natural new " +
                "nextval no nocopy not nowait null number ocirowid of on open option or " +
                "order out package partition pctfree pls_integer positive positiven pragma " +
                "primary prior privileges procedure public raise range raw real record ref " +
                "references release rename replace resource restrict return returning " +
                "reverse revoke rollback row rowid rowlabel rownum rows rowtype savepoint " +
                "second select separate set share size smallint space sql start stddev " +
                "subtype successful sum synonyms sysdate table then time timestamp to " +
                "trigger true truncate type uid union unique update use user validate values " +
                "varchar varchar2 variance view when whenever where while with work year zone"
        ),
        builtin = sqlSet(
            "bfile blob boolean char character date float integer long mlslabel nchar nclob " +
                "number nvarchar2 pls_integer raw real rowid rowtype smallint string timestamp " +
                "urowid varchar varchar2 varray"
        ),
        atoms = sqlSet("false true null"),
        operatorChars = Regex("^[*+\\-%<>!=~]"),
        dateSQL = sqlSet("date time timestamp"),
        support = sqlSet("doubleQuote binaryNumber hexNumber"),
        name = "plsql"
    )
)

val hive: StreamParser<SqlLegacyState> = makeSqlParser(
    SqlParserConfig(
        keywords = sqlSet(
            "$sqlKeywordsStr add after all alter analyze archive array as asc authorization " +
                "before between bigint binary boolean both bucket buckets by cache cascade case " +
                "cast change cluster clustered clusterstatus collection column columns " +
                "comment compact compactions compute concatenate continue create cross cube " +
                "current current_date current_timestamp cursor data database databases " +
                "dbproperties deferred defined delete delimited dependency desc describe " +
                "directoriesdirectorydisable distinct distribute drop else enable encoded " +
                "exchange exclusive exists export extended external false fetch fields " +
                "fileformat first float followingforformat formatted from full function " +
                "functions grant group grouping having hold hour identity if ignore index " +
                "indexes inner inpath inputdriver inputformat intersect interval into is " +
                "items join keys lateral left like limit lines load local location lock " +
                "locks logical long map minus msck no_drop null of on order out outer " +
                "outputdriver outputformat over overwrite partition partitioned partitions " +
                "percent plus preceding preserve procedure purge range rcfile read readonly " +
                "reads rebuild recordreader recordwriter reduce regexp rename repair replace " +
                "restrict revoke right rlike rollup row rows schema schemas semi " +
                "sequencefile serde serdeproperties set sets shared show show_database " +
                "skewed smallint sort sorted ssl statistics stored streamtable table tables " +
                "tablesample tblproperties temporary terminated textfile then timestamp " +
                "tinyint to touch transactions transform trigger true truncate unarchive " +
                "unbounded undo union uniquejoin unlock update use using utc utc_tmestamp " +
                "view when where whilewindowwith"
        ),
        builtin = sqlSet(
            "bool boolean long bigint tinyint smallint int float double decimal char varchar " +
                "date datetime timestamp string binary array map struct uniontype named_struct"
        ),
        atoms = sqlSet("false true null"),
        operatorChars = Regex("^[*+\\-%<>!=]"),
        dateSQL = sqlSet("date timestamp"),
        support = sqlSet("ODBCdotTable doubleQuote binaryNumber hexNumber"),
        name = "hive"
    )
)

val pgSQL: StreamParser<SqlLegacyState> = makeSqlParser(
    SqlParserConfig(
        keywords = sqlSet(
            "$sqlKeywordsStr abort access add admin after aggregate all also alter " +
                "alwaysanalyseanalyze any array assertion assignment asymmetric authorization " +
                "backward before begin between boolean cache called cascade case cast chain " +
                "check checkpointclasscluster column comment commit committed concurrently " +
                "configuration conflict connection constraints content conversion copy cost " +
                "cross cstring current current_catalog current_date current_role " +
                "current_schema current_time current_timestamp current_user cursor cycle " +
                "database day deallocate dec declare default defaults deferred definer delay " +
                "delegation delimiter delimiters depends desc dictionary disable discard " +
                "document domain drop each else enable encoding encrypted end enum escape " +
                "event every except exclude exclusive execute exists explain expression " +
                "extension external extract false family fetch filter first following for " +
                "force foreign forward freeze from full function generated global grant " +
                "greatest group grouping handler hash having hold hour identity " +
                "ifimmediateimmutable implicit include index indexes inherit inherits inline " +
                "inner inout input insensitive instead intersect interval invoker isolation " +
                "key labellanguagelarge last lateral leading leakproof left level limit " +
                "listen load locallocaltimelocaltimestamp location lock logged mapping match " +
                "materialized maxvalue method minute mode month move name national natural " +
                "nchar next no nothing notify null nullif nulls object of off offset oids " +
                "old on only open operator option options or out outer overlaps over " +
                "overriding parallel parser partial partition passing password placing plans " +
                "policy preceding prepared primary privilege procedural procedure program " +
                "publication quote range reads reassign recheck recursive ref referencing " +
                "refresh reindex relative release rename repeatable replacereplicationreset " +
                "restart restrict returning revoke right role rollback rollup row " +
                "rowsrulesavepoint schema scroll search second security sequence sequences " +
                "serializable server session session_user set sets share show simple skip " +
                "small snapshot some sql stable standalone start statement statistics stdin " +
                "stdout storage strictstripsubscription support symmetric sysid system table " +
                "tables tablespace temptemplatetemporary then ties to trailing transaction " +
                "transform trigger true truncatetrustedtype types unbounded unconditional " +
                "union unique unknown unlogged update userusingvacuum valid validate value " +
                "variable variadic view views volatile when where whitespace window with " +
                "within without work wrapper write xml year yes zone"
        ),
        builtin = sqlSet(
            "bigint int8 bigserial serial8 bit boolean bool box bytea character char varchar " +
                "cidr circle date double precision float8 inet integer int int4 interval json " +
                "jsonb line lseg macaddr macaddr8 money numeric decimal path pg_lsn " +
                "pointpolygonreal float4 smallint int2 smallserial serial2 serial serial4 " +
                "text timetimestamptsquery tsvector txid_snapshot uuid xml"
        ),
        atoms = sqlSet("false true null on off"),
        operatorChars = Regex("^[*+\\-%<>!=&|^~/#@]"),
        dateSQL = sqlSet("date time timestamp"),
        support = sqlSet("ODBCdotTable doubleQuote binaryNumber hexNumber decimallessFloat"),
        hooks = mapOf(
            "\"" to ::sqlHookIdentifierDoublequote,
            "U" to { s -> if (s.match("&\"")) sqlHookIdentifierDoublequote(s) else null }
        ),
        name = "pgsql"
    )
)

val sparkSQL: StreamParser<SqlLegacyState> = makeSqlParser(
    SqlParserConfig(
        keywords = sqlSet(
            "$sqlKeywordsStr add after all alter analyze anti any archive array as asc " +
                "authorization before between bigint binary boolean both bucket buckets by cache " +
                "cascade case cast change char check clear cluster clustered codegen collection " +
                "column columns comment commit compact compactions compute " +
                "concatenateconstraintcontains convert cost create cross cube current " +
                "current_date current_timestamp database databases day deallocate dec " +
                "decimal defined delete delimiterdelimitersdeny desc describe directories " +
                "disable distinct distribute drop else enable end equality escape every " +
                "except exchange exclusive exists explain export extended external extract " +
                "false fetch fields fileformat first float following for format formatted " +
                "from full function functions global grant group grouping having " +
                "hourifignore index indexes inner inpath input insert int intersect interval " +
                "into is items join keys lateral left level like limit lines load local " +
                "location locklongmap materialized minus mode month msck no not null or on " +
                "option out outer over overwrite partition partitioned partitions percent " +
                "pivot preceding primary principals properties purge query range rcfile read " +
                "recordreader reduce refresh rename repair replace restrict revoke right " +
                "rlike rollback rollup row rows semi sequencefile serde serdeproperties set " +
                "sets show smallint sort sorted start statistics stored struct subquery " +
                "table tables tablesample tblpropertiestemporaryterminated textfile then " +
                "tinyint to touch transform true truncate unboundedunsetupdate use view when " +
                "where window with within"
        ),
        builtin = sqlSet(
            "tinyint smallint int bigint float double decimal string date timestamp binary " +
                "boolean array map struct interval"
        ),
        atoms = sqlSet("false true null"),
        operatorChars = Regex("^[*+\\-%<>!=~]"),
        dateSQL = sqlSet("date timestamp"),
        support = sqlSet("ODBCdotTable doubleQuote binaryNumber hexNumber"),
        name = "sparksql"
    )
)
