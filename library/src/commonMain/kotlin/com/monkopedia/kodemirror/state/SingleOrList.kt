package com.monkopedia.kodemirror.state

import com.monkopedia.kodemirror.state.SingleOrList.Companion.coerceList
import kotlin.jvm.JvmInline

@Suppress("UNCHECKED_CAST")
@JvmInline
value class SingleOrList<T> private constructor(private val item: Any?) {
    val itemOrNull: T?
        get() = if (item is List<*>) null else item as T
    val singleOrNull: T?
        get() = if (item is List<*>) item.singleOrNull() as? T else item as T
    val listOrNull: List<T>?
        get() = if (item is List<*>) item as List<T> else null
    val list: List<T>
        get() = listOrNull ?: listOf(item as T)

    inline fun forEach(action: (T) -> Unit) {
        itemOrNull?.let(action) ?: listOrNull?.forEach(action)
    }

    override fun toString(): String = singleOrNull?.toString() ?: listOrNull?.toString() ?: "null"

    companion object {
        fun <T> SingleOrList(item: T): SingleOrList<T> {
            require(item !is List<*>) {
                "SingleOrList does not support Lists"
            }
            return com.monkopedia.kodemirror.state.SingleOrList(item)
        }

        fun <T> SingleOrList(vararg items: T): SingleOrList<T> =
            com.monkopedia.kodemirror.state.SingleOrList(items.toList())

        fun <T> SingleOrList(items: List<T>): SingleOrList<T> =
            com.monkopedia.kodemirror.state.SingleOrList(items)

        inline val <T> List<T>.singleOrList: SingleOrList<T>
            get() = singleOrNull()?.single ?: SingleOrList(this)
        inline val <T> List<T>.list: SingleOrList<T> get() = SingleOrList(this)
        inline val <T> T.single: SingleOrList<T> get() = SingleOrList(this)

        operator fun <T> SingleOrList<out T>.plus(other: T): SingleOrList<out T> =
            (list + other).list

        operator fun <T> SingleOrList<out T>.plus(other: SingleOrList<out T>): SingleOrList<out T> {
            if (listOrNull?.size == 0) return other
            if (other.listOrNull?.size == 0) return this
            return (list + other.list).list
        }

        inline fun <T> SingleOrList<T>?.orEmpty(): SingleOrList<out T> = this ?: empty

        @Suppress("UNCHECKED_CAST")
        fun <T> Any?.coerceList(): List<T> = (this as? Either.Left<Any?>)?.a?.coerceList()
            ?: (this as? Either.Right<Any?>)?.b?.coerceList()
            ?: this as? List<T>
            ?: (this as SingleOrList<T>).list

        @Suppress("UNCHECKED_CAST")
        fun <T> Any?.coerceSingle(): T = (this as? Either.Left<Any?>)?.a?.coerceSingle()
            ?: (this as? Either.Right<Any?>)?.b?.coerceSingle()
            ?: (this as? SingleOrList<T>)?.singleOrNull
            ?: this as T

        val empty = SingleOrList<Nothing>(emptyList())
    }
}
