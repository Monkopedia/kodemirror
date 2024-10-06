package com.monkopedia.kodemirror.state

sealed class Either<A, B> {
    open val a: A? get() = null
    open val b: B? get() = null

    class Left<A>(override val a: A) : Either<A, Nothing>()
    class Right<B>(override val b: B) : Either<Nothing, B>()

    companion object {
        inline val <reified A> A.asLeft: Left<A>
            get() = Left(this)
        inline val <reified A> A.asRight: Right<A>
            get() = Right(this)
    }
}


inline fun <A, B, R> Either<A, B>.fold(aMap: A.() -> R, bMap: B.() -> R): R {
    return when (this) {
        is Either.Left -> a.aMap()
        is Either.Right -> b.bMap()
    }
}
