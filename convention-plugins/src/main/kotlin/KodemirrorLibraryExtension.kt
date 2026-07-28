/*
 * Copyright 2025 Jason Monk
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

import org.gradle.api.provider.Property

/**
 * Per-module settings for the `kodemirror.library` convention plugin.
 */
abstract class KodemirrorLibraryExtension {
    /**
     * Whether this module's `wasmJsTest` / `wasmJsNodeTest` tasks should actually run.
     *
     * Defaults to `false`. The wasm Node test environment cannot load `skiko.mjs`, so any
     * module whose wasmJs *test* binary reaches Compose — directly, or transitively through
     * `:view` / `:language` — fails at module load with `ERR_MODULE_NOT_FOUND`. Unblocking
     * those modules is tracked in #202.
     *
     * Modules that are Compose-free all the way down opt back in with:
     * ```
     * kodemirrorLibrary {
     *     wasmJsTests.set(true)
     * }
     * ```
     * Verify before flipping this on: run the task and check the executed test counts in the
     * `build/test-results/wasmJsNodeTest` XML, since a disabled task also builds green.
     */
    abstract val wasmJsTests: Property<Boolean>
}
