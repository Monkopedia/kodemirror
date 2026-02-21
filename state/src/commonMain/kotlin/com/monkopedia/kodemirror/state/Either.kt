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
package com.monkopedia.kodemirror.state

sealed class Either<out A, out B> {
    open val a: A? get() = null
    open val b: B? get() = null

    class Left<A>(override val a: A) : Either<A, Nothing>() {
        override fun toString(): String {
            return a.toString()
        }
    }
    class Right<B>(override val b: B) : Either<Nothing, B>() {
        override fun toString(): String {
            return b.toString()
        }
    }

    companion object {
        inline val <reified A> A.asLeft: Either<A, Nothing>
            get() = Left(this)
        inline val <reified A> A.asRight: Either<Nothing, A>
            get() = Right(this)
    }
}

inline fun <A, B, R> Either<A, B>.fold(aMap: A.() -> R, bMap: B.() -> R): R {
    return when (this) {
        is Either.Left -> a.aMap()
        is Either.Right -> b.bMap()
    }
}
