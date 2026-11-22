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
 *
 * Originally based on CodeMirror 6 by Marijn Haverbeke, licensed under MIT.
 * See NOTICE file for details.
 */
import org.gradle.api.services.BuildService
import org.gradle.api.services.BuildServiceParameters

/**
 * A build-wide semaphore for the Kotlin/Wasm link tasks (`KotlinJsIrLink`, e.g.
 * `compileTestDevelopmentExecutableKotlinWasmJs`). It holds no state; its only job is the
 * `maxParallelUsages` it is registered with in `kodemirror.library`.
 *
 * The links run inside the one shared Kotlin daemon, so they share its heap with each other
 * and with every other Kotlin compile. Unbounded, a build that links many wasm test binaries
 * at once exhausts that heap (`OutOfMemoryError: GC overhead limit exceeded`), and on a CI
 * runner the GC thrashing shows up as a silent stall until the job times out (#423).
 */
abstract class WasmLinkLimiter : BuildService<BuildServiceParameters.None>
