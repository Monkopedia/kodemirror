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

@Suppress("ktlint:standard:max-line-length")
private val nginxKeywords = (
    "break return rewrite set" +
        " accept_mutex accept_mutex_delay access_log add_after_body add_before_body add_header addition_types aio alias allow ancient_browser ancient_browser_value auth_basic auth_basic_user_file auth_http auth_http_header auth_http_timeout autoindex autoindex_exact_size autoindex_localtime charset charset_types client_body_buffer_size client_body_in_file_only client_body_in_single_buffer client_body_temp_path client_body_timeout client_header_buffer_size client_header_timeout client_max_body_size connection_pool_size create_full_put_path daemon dav_access dav_methods debug_connection debug_points default_type degradation degrade deny devpoll_changes devpoll_events directio directio_alignment empty_gif env epoll_events error_log eventport_events expires fastcgi_bind fastcgi_buffer_size fastcgi_buffers fastcgi_busy_buffers_size fastcgi_cache fastcgi_cache_key fastcgi_cache_methods fastcgi_cache_min_uses fastcgi_cache_path fastcgi_cache_use_stale fastcgi_cache_valid fastcgi_catch_stderr fastcgi_connect_timeout fastcgi_hide_header fastcgi_ignore_client_abort fastcgi_ignore_headers fastcgi_index fastcgi_intercept_errors fastcgi_max_temp_file_size fastcgi_next_upstream fastcgi_param fastcgi_pass_header fastcgi_pass_request_body fastcgi_pass_request_headers fastcgi_read_timeout fastcgi_send_lowat fastcgi_send_timeout fastcgi_split_path_info fastcgi_store fastcgi_store_access fastcgi_temp_file_write_size fastcgi_temp_path fastcgi_upstream_fail_timeout fastcgi_upstream_max_fails flv geoip_city geoip_country google_perftools_profiles gzip gzip_buffers gzip_comp_level gzip_disable gzip_hash gzip_http_version gzip_min_length gzip_no_buffer gzip_proxied gzip_static gzip_types gzip_vary gzip_window if_modified_since ignore_invalid_headers image_filter image_filter_buffer image_filter_jpeg_quality image_filter_transparency imap_auth imap_capabilities imap_client_buffer index ip_hash keepalive_requests keepalive_timeout kqueue_changes kqueue_events large_client_header_buffers limit_conn limit_conn_log_level limit_rate limit_rate_after limit_req limit_req_log_level limit_req_zone limit_zone lingering_time lingering_timeout lock_file log_format log_not_found log_subrequest map_hash_bucket_size map_hash_max_size master_process memcached_bind memcached_buffer_size memcached_connect_timeout memcached_next_upstream memcached_read_timeout memcached_send_timeout memcached_upstream_fail_timeout memcached_upstream_max_fails merge_slashes min_delete_depth modern_browser modern_browser_value msie_padding msie_refresh multi_accept open_file_cache open_file_cache_errors open_file_cache_events open_file_cache_min_uses open_file_cache_valid open_log_file_cache output_buffers override_charset perl perl_modules perl_require perl_set pid pop3_auth pop3_capabilities port_in_redirect postpone_gzipping postpone_output protocol proxy proxy_bind proxy_buffer proxy_buffer_size proxy_buffering proxy_buffers proxy_busy_buffers_size proxy_cache proxy_cache_key proxy_cache_methods proxy_cache_min_uses proxy_cache_path proxy_cache_use_stale proxy_cache_valid proxy_connect_timeout proxy_headers_hash_bucket_size proxy_headers_hash_max_size proxy_hide_header proxy_ignore_client_abort proxy_ignore_headers proxy_intercept_errors proxy_max_temp_file_size proxy_method proxy_next_upstream proxy_pass_error_message proxy_pass_header proxy_pass_request_body proxy_pass_request_headers proxy_read_timeout proxy_redirect proxy_send_lowat proxy_send_timeout proxy_set_body proxy_set_header proxy_ssl_session_reuse proxy_store proxy_store_access proxy_temp_file_write_size proxy_temp_path proxy_timeout proxy_upstream_fail_timeout proxy_upstream_max_fails random_index read_ahead real_ip_header recursive_error_pages request_pool_size reset_timedout_connection resolver resolver_timeout rewrite_log rtsig_overflow_events rtsig_overflow_test rtsig_overflow_threshold rtsig_signo satisfy secure_link_secret send_lowat send_timeout sendfile sendfile_max_chunk server_name_in_redirect server_names_hash_bucket_size server_names_hash_max_size server_tokens set_real_ip_from smtp_auth smtp_capabilities smtp_client_buffer smtp_greeting_delay so_keepalive source_charset ssi ssi_ignore_recycled_buffers ssi_min_file_chunk ssi_silent_errors ssi_types ssi_value_length ssl ssl_certificate ssl_certificate_key ssl_ciphers ssl_client_certificate ssl_crl ssl_dhparam ssl_engine ssl_prefer_server_ciphers ssl_protocols ssl_session_cache ssl_session_timeout ssl_verify_client ssl_verify_depth starttls stub_status sub_filter sub_filter_once sub_filter_types tcp_nodelay tcp_nopush thread_stack_size timeout timer_resolution types_hash_bucket_size types_hash_max_size underscores_in_headers uninitialized_variable_warn use user userid userid_domain userid_expires userid_mark userid_name userid_p3p userid_path userid_service valid_referers variables_hash_bucket_size variables_hash_max_size worker_connections worker_cpu_affinity worker_priority worker_processes worker_rlimit_core worker_rlimit_nofile worker_rlimit_sigpending worker_threads working_directory xclient xml_entities xslt_stylesheet xslt_typesdrew@li229-23"
    ).split(" ").filter { it.isNotEmpty() }.toSet()

private val nginxKeywordsBlock = (
    "http mail events server types location upstream charset_map limit_except if geo map"
    ).split(" ").toSet()

@Suppress("ktlint:standard:max-line-length")
private val nginxKeywordsImportant = (
    "include root server server_name listen internal proxy_pass memcached_pass fastcgi_pass try_files"
    ).split(" ").toSet()

data class NginxState(
    var tokenize: (StringStream, NginxState) -> NginxResult = ::nginxTokenBase,
    var baseIndent: Int = 0,
    var stack: MutableList<String> = mutableListOf(),
    var curType: String? = null
)

data class NginxResult(val style: String?, val type: String?)

@Suppress("CyclomaticComplexMethod", "ReturnCount")
private fun nginxTokenBase(stream: StringStream, state: NginxState): NginxResult {
    stream.eatWhile(Regex("[\\w\$_]"))
    val cur = stream.current()

    if (cur.isNotEmpty()) {
        if (cur in nginxKeywords) return NginxResult("keyword", null)
        if (cur in nginxKeywordsBlock) return NginxResult("controlKeyword", null)
        if (cur in nginxKeywordsImportant) return NginxResult("controlKeyword", null)
    }

    val ch = stream.next() ?: return NginxResult(null, null)

    if (ch == "@") {
        stream.eatWhile(Regex("[\\w\\\\-]"))
        return NginxResult("meta", stream.current())
    }
    if (ch == "/" && stream.eat("*") != null) {
        state.tokenize = ::nginxTokenCComment
        return nginxTokenCComment(stream, state)
    }
    if (ch == "<" && stream.eat("!") != null) {
        state.tokenize = ::nginxTokenSGMLComment
        return nginxTokenSGMLComment(stream, state)
    }
    if (ch == "=") return NginxResult(null, "compare")
    if ((ch == "~" || ch == "|") && stream.eat("=") != null) {
        return NginxResult(null, "compare")
    }
    if (ch == "\"" || ch == "'") {
        state.tokenize = nginxTokenString(ch)
        return state.tokenize(stream, state)
    }
    if (ch == "#") {
        stream.skipToEnd()
        return NginxResult("comment", "comment")
    }
    if (ch == "!") {
        stream.match(Regex("^\\s*\\w*"))
        return NginxResult("keyword", "important")
    }
    if (Regex("\\d").containsMatchIn(ch)) {
        stream.eatWhile(Regex("[\\w.%]"))
        return NginxResult("number", "unit")
    }
    if (Regex("[,.+>*/]").containsMatchIn(ch)) {
        return NginxResult(null, "select-op")
    }
    if (Regex("[;{}:\\[\\]]").containsMatchIn(ch)) {
        return NginxResult(null, ch)
    }
    stream.eatWhile(Regex("[\\w\\\\-]"))
    return NginxResult("variable", "variable")
}

private fun nginxTokenCComment(stream: StringStream, state: NginxState): NginxResult {
    var maybeEnd = false
    while (true) {
        val ch = stream.next() ?: break
        if (maybeEnd && ch == "/") {
            state.tokenize = ::nginxTokenBase
            break
        }
        maybeEnd = ch == "*"
    }
    return NginxResult("comment", "comment")
}

private fun nginxTokenSGMLComment(stream: StringStream, state: NginxState): NginxResult {
    var dashes = 0
    while (true) {
        val ch = stream.next() ?: break
        if (dashes >= 2 && ch == ">") {
            state.tokenize = ::nginxTokenBase
            break
        }
        dashes = if (ch == "-") dashes + 1 else 0
    }
    return NginxResult("comment", "comment")
}

private fun nginxTokenString(quote: String): (StringStream, NginxState) -> NginxResult {
    return fn@{ stream, state ->
        var escaped = false
        while (true) {
            val ch = stream.next() ?: break
            if (ch == quote && !escaped) break
            escaped = !escaped && ch == "\\"
        }
        if (!escaped) state.tokenize = ::nginxTokenBase
        NginxResult("string", "string")
    }
}

/** Stream parser for Nginx configuration. */
val nginx: StreamParser<NginxState> = object : StreamParser<NginxState> {
    override val name: String get() = "nginx"

    override fun startState(indentUnit: Int) = NginxState()

    override fun copyState(state: NginxState) = state.copy(
        stack = state.stack.toMutableList()
    )

    @Suppress("CyclomaticComplexMethod")
    override fun token(stream: StringStream, state: NginxState): String? {
        if (stream.eatSpace()) return null
        state.curType = null
        val result = state.tokenize(stream, state)
        val type = result.type
        var style = result.style
        state.curType = type

        val context = state.stack.lastOrNull()
        if (type == "hash" && context == "rule") {
            style = "atom"
        } else if (style == "variable") {
            style = if (context == "rule") {
                "number"
            } else if (context == null || context == "@media{") {
                "tag"
            } else {
                style
            }
        }

        if (context == "rule" && type != null && Regex("^[{};]$").containsMatchIn(type)) {
            state.stack.removeLast()
        }
        if (type == "{") {
            if (context == "@media") {
                state.stack[state.stack.size - 1] = "@media{"
            } else {
                state.stack.add("{")
            }
        } else if (type == "}") {
            if (state.stack.isNotEmpty()) state.stack.removeLast()
        } else if (type == "@media") {
            state.stack.add("@media")
        } else if (context == "{" && type != "comment") {
            state.stack.add("rule")
        }
        return style
    }

    override fun indent(state: NginxState, textAfter: String, context: IndentContext): Int {
        var n = state.stack.size
        if (Regex("^\\}").containsMatchIn(textAfter)) {
            n -= if (state.stack.lastOrNull() == "rule") 2 else 1
        }
        return state.baseIndent + n * context.unit
    }

    override val languageData: Map<String, Any>
        get() = mapOf(
            "indentOnInput" to Regex("^\\s*\\}$")
        )
}
