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
package com.monkopedia.kodemirror.lezer.highlight

private var nextTagID = 0

/**
 * Highlighting tags are markers that denote a highlighting category.
 * They are associated with parts of a syntax tree by a language mode,
 * and then mapped to an actual style by a [Highlighter].
 */
class Tag internal constructor(
    internal var name: String,
    val set: MutableList<Tag>,
    val base: Tag?,
    internal val modified: List<Modifier>
) {
    val id: Int = nextTagID++

    override fun toString(): String {
        var result = name
        for (mod in modified) {
            if (mod.name != null) result = "${mod.name}($result)"
        }
        return result
    }

    companion object {
        /**
         * Define a new tag. If [parent] is given, the tag is treated as a
         * sub-tag of that parent, and highlighters that don't mention
         * this tag will try to fall back to the parent tag.
         */
        fun define(name: String = "?", parent: Tag? = null): Tag {
            require(parent?.base == null) { "Cannot derive from a modified tag" }
            val tag = Tag(name, mutableListOf(), null, emptyList())
            tag.set.add(tag)
            if (parent != null) {
                for (t in parent.set) tag.set.add(t)
            }
            return tag
        }

        fun define(parent: Tag): Tag = define("?", parent)

        /**
         * Define a tag modifier, which is a function that, given a tag,
         * will return a tag that is a subtag of the original.
         */
        fun defineModifier(name: String? = null): (Tag) -> Tag {
            val mod = Modifier(name)
            return { tag ->
                if (tag.modified.contains(mod)) {
                    tag
                } else {
                    Modifier.get(
                        tag.base ?: tag,
                        (tag.modified + mod).sortedBy { it.id }
                    )
                }
            }
        }
    }
}

private var nextModifierID = 0

internal class Modifier(val name: String? = null) {
    val instances: MutableList<Tag> = mutableListOf()
    val id: Int = nextModifierID++

    companion object {
        fun get(base: Tag, mods: List<Modifier>): Tag {
            if (mods.isEmpty()) return base
            val exists = mods[0].instances.find { t ->
                t.base == base && sameArray(mods, t.modified)
            }
            if (exists != null) return exists
            val set = mutableListOf<Tag>()
            val tag = Tag(base.name, set, base, mods)
            for (m in mods) m.instances.add(tag)
            val configs = powerSet(mods)
            for (parent in base.set) {
                if (parent.modified.isEmpty()) {
                    for (config in configs) {
                        set.add(get(parent, config))
                    }
                }
            }
            return tag
        }
    }
}

private fun <T> sameArray(a: List<T>, b: List<T>): Boolean {
    return a.size == b.size && a.indices.all { a[it] == b[it] }
}

private fun <T> powerSet(array: List<T>): List<List<T>> {
    var sets = mutableListOf<List<T>>(emptyList())
    for (i in array.indices) {
        val len = sets.size
        for (j in 0 until len) {
            sets.add(sets[j] + array[i])
        }
    }
    return sets.sortedByDescending { it.size }
}
