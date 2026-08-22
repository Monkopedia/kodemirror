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
package com.monkopedia.kodemirror.view

/**
 * macOS reports itself as `"Mac"`, the name [resolveBindingKey] resolves the
 * `mac` binding overrides from.
 *
 * Lives here rather than in `nativeMain` because the shared native actual was
 * a hardcoded `"Mac"` for *every* Kotlin/Native target, so iOS reported as a
 * Mac (#217). Splitting the actual per Apple family is what makes each target
 * answer for itself; nothing else can be shared without reintroducing the lie.
 */
internal actual fun platformOsName(): String = "Mac"
