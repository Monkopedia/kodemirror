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
 */
package com.monkopedia.kodemirror.samples.showcase

import com.monkopedia.kodemirror.basicsetup.basicSetup
import com.monkopedia.kodemirror.language.oneDarkHighlightStyle
import com.monkopedia.kodemirror.language.syntaxHighlighting
import com.monkopedia.kodemirror.state.Extension
import com.monkopedia.kodemirror.state.plus

/**
 * Showcase-specific setup: [basicSetup] plus One Dark syntax highlighting
 * to match the dark Material theme. The non-fallback [oneDarkHighlightStyle]
 * overrides basicSetup's fallback [defaultHighlightStyle][com.monkopedia.kodemirror.language.defaultHighlightStyle].
 */
val showcaseSetup: Extension =
    basicSetup + syntaxHighlighting(oneDarkHighlightStyle)
