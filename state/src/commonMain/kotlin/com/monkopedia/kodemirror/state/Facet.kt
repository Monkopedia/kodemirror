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

private var nextID = 0

/**
 * Extension values can be provided when creating a state to attach
 * various kinds of configuration and behavior information.
 * Extensions can be nested in arrays arbitrarily deep.
 */
interface Extension

class ExtensionList(
    val extensions: List<Extension>
) : Extension

/** Create an [ExtensionList] from the given [extensions]. */
fun extensionListOf(vararg extensions: Extension): ExtensionList =
    ExtensionList(extensions.toList())

/** Combine two extensions into an [ExtensionList]. */
operator fun Extension.plus(other: Extension): ExtensionList = ExtensionList(listOf(this, other))

class ExtensionHolder(val extension: Extension) : Extension

/**
 * A facet is a labeled value that is associated with an editor
 * state. It takes inputs from any number of extensions, and
 * combines those into a single output value.
 */
sealed interface FacetEnabler<Input, Output> {
    data class StaticExtension<Input, Output>(
        val ext: Extension
    ) : FacetEnabler<Input, Output>
    data class DynamicExtension<Input, Output>(
        val fn: (Facet<Input, Output>) -> Extension
    ) : FacetEnabler<Input, Output>
}

class Facet<Input, Output> private constructor(
    internal val combineFn: (List<Input>) -> Output,
    internal val compareInput: (Input, Input) -> Boolean,
    internal val compareFn: (Output, Output) -> Boolean,
    private val isStatic: Boolean,
    enables: FacetEnabler<Input, Output>?
) : Extension {
    internal val id = nextID++
    internal val default: Output = combineFn(emptyList())
    internal val extensions: Extension?

    init {
        extensions = when (enables) {
            is FacetEnabler.DynamicExtension -> enables.fn(this)
            is FacetEnabler.StaticExtension -> enables.ext
            null -> null
        }
    }

    /**
     * Returns a facet reader for this facet.
     */
    val reader: FacetReader<Output>
        get() = FacetReader(id, default)

    companion object {
        /** Define a new facet. */
        fun <Input, Output> define(
            combine: ((List<Input>) -> Output)? = null,
            compare: ((Output, Output) -> Boolean)? = null,
            compareInput: ((Input, Input) -> Boolean)? = null,
            static: Boolean = false,
            enables: FacetEnabler<Input, Output>? = null
        ): Facet<Input, Output> {
            @Suppress("UNCHECKED_CAST")
            val combineFn = combine
                ?: { a: List<Input> -> a as Output }
            val compareFn = compare
                ?: if (combine == null) {
                    { a: Output, b: Output ->
                        sameArray(
                            a as List<*>, b as List<*>
                        )
                    }
                } else {
                    { a: Output, b: Output -> a == b }
                }
            val compareInputFn = compareInput
                ?: { a: Input, b: Input -> a == b }
            return Facet(
                combineFn,
                compareInputFn,
                compareFn,
                static,
                enables
            )
        }
    }

    /**
     * Returns an extension that adds the given value to this
     * facet.
     */
    fun of(value: Input): Extension = FacetProvider(
        emptyList(),
        this,
        ProviderValue.Static(value)
    )

    /**
     * Create an extension that computes a value for the facet
     * from a state.
     */
    fun compute(deps: List<Slot>, get: (EditorState) -> Input): Extension {
        if (isStatic) {
            error("Can't compute a static facet")
        }
        return FacetProvider(deps, this, ProviderValue.Single(get))
    }

    /**
     * Create an extension that computes zero or more values for
     * this facet from a state.
     */
    fun computeN(deps: List<Slot>, get: (EditorState) -> List<Input>): Extension {
        if (isStatic) {
            error("Can't compute a static facet")
        }
        return FacetProvider(deps, this, ProviderValue.Multi(get))
    }

    /**
     * Shorthand method for registering a facet source with a
     * state field as input.
     */
    fun <T> from(field: StateField<T>, get: ((T) -> Input)? = null): Extension {
        @Suppress("UNCHECKED_CAST")
        val getter = get ?: { x: T -> x as Input }
        return compute(listOf(Slot.FieldSlot(field))) { state ->
            getter(state.field(field))
        }
    }
}

/**
 * A facet reader can be used to fetch the value of a facet.
 */
data class FacetReader<Output>(
    val id: Int,
    val default: Output
)

private fun sameArray(a: List<*>, b: List<*>): Boolean {
    return a === b || (
        a.size == b.size &&
            a.indices.all { a[it] == b[it] }
        )
}

sealed class Slot {
    data class FacetSlot(val reader: FacetReader<*>) : Slot() {
        val id: Int get() = reader.id
    }
    data class FieldSlot(
        val stateField: StateField<*>
    ) : Slot() {
        val id: Int get() = stateField.id
    }
    data object Doc : Slot()
    data object Selection : Slot()
}

fun <Output> Facet<*, Output>.asSlot(): Slot = Slot.FacetSlot(FacetReader(id, default))

internal sealed interface ProviderValue<Input> {
    data class Static<Input>(val value: Input) : ProviderValue<Input>
    data class Single<Input>(
        val fn: (EditorState) -> Input
    ) : ProviderValue<Input>
    data class Multi<Input>(
        val fn: (EditorState) -> List<Input>
    ) : ProviderValue<Input>
}

internal class FacetProvider<Input>(
    val dependencies: List<Slot>,
    val facet: Facet<Input, *>,
    val provider: ProviderValue<Input>
) : Extension {
    val id = nextID++

    @Suppress("UNCHECKED_CAST")
    fun dynamicSlot(addresses: Map<Int, Int>): DynamicSlot {
        val getter: (EditorState) -> Any? = when (val p = provider) {
            is ProviderValue.Single -> p.fn
            is ProviderValue.Multi -> p.fn
            is ProviderValue.Static -> error("Cannot create dynamic slot for static provider")
        }
        val compare = facet.compareInput
        val id = this.id
        val idx = addresses[id]!! shr 1
        val multi = provider is ProviderValue.Multi
        var depDoc = false
        var depSel = false
        val depAddrs = mutableListOf<Int>()
        for (dep in dependencies) {
            when (dep) {
                is Slot.Doc -> depDoc = true
                is Slot.Selection -> depSel = true
                is Slot.FacetSlot -> {
                    val addr = addresses[dep.id]
                    if (addr != null && (addr and 1) == 0) {
                        depAddrs.add(addr)
                    }
                }
                is Slot.FieldSlot -> {
                    val addr = addresses[dep.id]
                    if (addr != null && (addr and 1) == 0) {
                        depAddrs.add(addr)
                    }
                }
            }
        }

        return object : DynamicSlot {
            override fun create(state: EditorState): SlotStatus {
                state.values[idx] = getter(state)
                return SlotStatus.Changed
            }

            override fun update(state: EditorState, tr: Transaction): SlotStatus {
                if ((depDoc && tr.docChanged) ||
                    (
                        depSel && (
                            tr.docChanged ||
                                tr.selection != null
                            )
                        ) ||
                    ensureAll(state, depAddrs)
                ) {
                    val newVal = getter(state)
                    if (multi) {
                        if (!compareArray(
                                newVal as List<Input>,
                                state.values[idx] as List<Input>,
                                compare
                            )
                        ) {
                            state.values[idx] = newVal
                            return SlotStatus.Changed
                        }
                    } else {
                        if (!compare(
                                newVal as Input,
                                state.values[idx] as Input
                            )
                        ) {
                            state.values[idx] = newVal
                            return SlotStatus.Changed
                        }
                    }
                }
                return SlotStatus.None
            }

            override fun reconfigure(state: EditorState, oldState: EditorState): SlotStatus {
                var newVal: Any?
                val oldAddr = oldState.config.address[id]
                if (oldAddr != null) {
                    val oldVal = getAddr(oldState, oldAddr)
                    if (dependencies.all { dep ->
                            when (dep) {
                                is Slot.FacetSlot ->
                                    oldState.facet(dep.reader) ===
                                        state.facet(dep.reader)
                                is Slot.FieldSlot ->
                                    oldState.field(
                                        dep.stateField, false
                                    ) ==
                                        state.field(
                                            dep.stateField, false
                                        )
                                else -> true
                            }
                        } || run {
                            newVal = getter(state)
                            if (multi) {
                                compareArray(
                                    newVal as List<Input>,
                                    oldVal as List<Input>,
                                    compare
                                )
                            } else {
                                compare(
                                    newVal as Input,
                                    oldVal as Input
                                )
                            }
                        }
                    ) {
                        state.values[idx] = oldVal
                        return SlotStatus.None
                    }
                    state.values[idx] =
                        getter(state) // already computed
                } else {
                    state.values[idx] = getter(state)
                }
                return SlotStatus.Changed
            }
        }
    }
}

private fun <T> compareArray(a: List<T>, b: List<T>, compare: (T, T) -> Boolean): Boolean {
    if (a.size != b.size) return false
    for (i in a.indices) {
        if (!compare(a[i], b[i])) return false
    }
    return true
}

private fun ensureAll(state: EditorState, addrs: List<Int>): Boolean {
    var changed = false
    for (addr in addrs) {
        if (ensureAddr(state, addr).isChanged) changed = true
    }
    return changed
}

@Suppress("UNCHECKED_CAST")
internal fun <Input, Output> dynamicFacetSlot(
    addresses: Map<Int, Int>,
    facet: Facet<Input, Output>,
    providers: List<FacetProvider<Input>>
): DynamicSlot {
    val providerAddrs = providers.map { addresses[it.id]!! }
    val providerIsMulti = providers.map { it.provider is ProviderValue.Multi }
    val dynamic = providerAddrs.filter { (it and 1) == 0 }
    val idx = addresses[facet.id]!! shr 1

    fun get(state: EditorState): Output {
        val values = mutableListOf<Input>()
        for (i in providerAddrs.indices) {
            val value = getAddr(state, providerAddrs[i])
            if (providerIsMulti[i]) {
                for (v in value as List<Input>) values.add(v)
            } else {
                values.add(value as Input)
            }
        }
        return facet.combineFn(values)
    }

    return object : DynamicSlot {
        override fun create(state: EditorState): SlotStatus {
            for (addr in providerAddrs) {
                ensureAddr(state, addr)
            }
            state.values[idx] = get(state)
            return SlotStatus.Changed
        }

        override fun update(state: EditorState, tr: Transaction): SlotStatus {
            if (!ensureAll(state, dynamic)) {
                return SlotStatus.None
            }
            val value = get(state)
            if (facet.compareFn(
                    value,
                    state.values[idx] as Output
                )
            ) {
                return SlotStatus.None
            }
            state.values[idx] = value
            return SlotStatus.Changed
        }

        override fun reconfigure(state: EditorState, oldState: EditorState): SlotStatus {
            val depChanged = ensureAll(
                state,
                providerAddrs
            )
            val oldProviders =
                oldState.config.facets[facet.id]
            val oldValue = oldState.facet(facet)
            if (oldProviders != null && !depChanged &&
                sameArray(providers, oldProviders)
            ) {
                state.values[idx] = oldValue
                return SlotStatus.None
            }
            val value = get(state)
            if (facet.compareFn(value, oldValue)) {
                state.values[idx] = oldValue
                return SlotStatus.None
            }
            state.values[idx] = value
            return SlotStatus.Changed
        }
    }
}

internal val initField: Facet<FieldInit, List<FieldInit>> =
    Facet.define(static = true)

internal data class FieldInit(
    val field: StateField<*>,
    val create: (EditorState) -> Any?
)

/**
 * Fields can store additional information in an editor state, and
 * keep it in sync with the rest of the state.
 */
class StateField<Value> private constructor(
    internal val id: Int,
    private val createF: (EditorState) -> Value,
    private val updateF: (Value, Transaction) -> Value,
    private val compareF: (Value, Value) -> Boolean,
    internal val spec: StateFieldSpec<Value>
) : Extension {
    internal var provides: Extension? = null

    companion object {
        /** Define a state field. */
        fun <Value> define(config: StateFieldSpec<Value>): StateField<Value> {
            val field = StateField(
                nextID++,
                config.create,
                config.update,
                config.compare ?: { a, b -> a == b },
                config
            )
            if (config.provide != null) {
                field.provides = config.provide.invoke(field)
            }
            return field
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun create(state: EditorState): Value {
        val init = state.facet(initField).find {
            it.field === this
        }
        return (init?.create?.let { it(state) as Value })
            ?: createF(state)
    }

    @Suppress("UNCHECKED_CAST")
    internal fun slot(addresses: Map<Int, Int>): DynamicSlot {
        val idx = addresses[id]!! shr 1
        return object : DynamicSlot {
            override fun create(state: EditorState): SlotStatus {
                state.values[idx] =
                    this@StateField.create(state)
                return SlotStatus.Changed
            }

            override fun update(state: EditorState, tr: Transaction): SlotStatus {
                val oldVal = state.values[idx] as Value
                val value = updateF(oldVal, tr)
                if (compareF(oldVal, value)) {
                    return SlotStatus.None
                }
                state.values[idx] = value
                return SlotStatus.Changed
            }

            override fun reconfigure(state: EditorState, oldState: EditorState): SlotStatus {
                val init = state.facet(initField)
                val oldInit = oldState.facet(initField)
                val reInit = init.find {
                    it.field === this@StateField
                }
                if (reInit != null &&
                    reInit != oldInit.find {
                        it.field === this@StateField
                    }
                ) {
                    state.values[idx] =
                        reInit.create(state) as Value
                    return SlotStatus.Changed
                }
                if (oldState.config.address[id] != null) {
                    state.values[idx] =
                        oldState.field(this@StateField)
                    return SlotStatus.None
                }
                state.values[idx] =
                    this@StateField.create(state)
                return SlotStatus.Changed
            }
        }
    }

    /**
     * Returns an extension that enables this field and overrides
     * the way it is initialized.
     */
    @Suppress("UNCHECKED_CAST")
    fun init(create: (EditorState) -> Value): Extension = ExtensionList(
        listOf(
            this,
            initField.of(
                FieldInit(this) { state ->
                    create(state)
                }
            )
        )
    )
}

data class StateFieldSpec<Value>(
    val create: (EditorState) -> Value,
    val update: (Value, Transaction) -> Value,
    val compare: ((Value, Value) -> Boolean)? = null,
    val provide: ((StateField<Value>) -> Extension)? = null,
    val toJSON: ((Value, EditorState) -> Any?)? = null,
    val fromJSON: ((Any?, EditorState) -> Value)? = null
)

private object PrecValue {
    const val LOWEST = 4
    const val LOW = 3
    const val DEFAULT = 2
    const val HIGH = 1
    const val HIGHEST = 0
}

/**
 * By default extensions are registered in the order they are
 * found. Individual extension values can be assigned a precedence
 * to override this.
 */
object Prec {
    fun highest(ext: Extension): Extension = PrecExtension(ext, PrecValue.HIGHEST)
    fun high(ext: Extension): Extension = PrecExtension(ext, PrecValue.HIGH)
    fun default(ext: Extension): Extension = PrecExtension(ext, PrecValue.DEFAULT)
    fun low(ext: Extension): Extension = PrecExtension(ext, PrecValue.LOW)
    fun lowest(ext: Extension): Extension = PrecExtension(ext, PrecValue.LOWEST)
}

internal class PrecExtension(
    val inner: Extension,
    val prec: Int
) : Extension

/**
 * Extension compartments can be used to make a configuration
 * dynamic.
 */
class Compartment : Extension {
    /**
     * Create an instance of this compartment to add to your state
     * configuration.
     */
    fun of(ext: Extension): Extension = CompartmentInstance(this, ext)

    /**
     * Create an effect that reconfigures this compartment.
     */
    fun reconfigure(content: Extension): StateEffect<*> = Companion.reconfigure.of(
        CompartmentReconfigure(this, content)
    )

    /**
     * Get the current content of the compartment in the state.
     */
    fun get(state: EditorState): Extension? = state.config.compartments[this]

    companion object {
        internal val reconfigure: StateEffectType<CompartmentReconfigure> =
            StateEffect.define()
    }
}

data class CompartmentReconfigure(
    val compartment: Compartment,
    val extension: Extension
)

internal class CompartmentInstance(
    val compartment: Compartment,
    val inner: Extension
) : Extension

interface DynamicSlot {
    fun create(state: EditorState): SlotStatus
    fun update(state: EditorState, tr: Transaction): SlotStatus
    fun reconfigure(state: EditorState, oldState: EditorState): SlotStatus
}

enum class SlotStatus(val value: Int) {
    None(0),
    Changed(1),
    Computed(2),
    ComputedChanged(3),
    Computing(4);

    val isChanged: Boolean get() = (value and 1) != 0
    val isComputed: Boolean get() = (value and 2) != 0

    companion object {
        fun fromInt(v: Int): SlotStatus = when (v) {
            0 -> None
            1 -> Changed
            2 -> Computed
            3 -> ComputedChanged
            4 -> Computing
            else -> None
        }

        fun computedOr(status: SlotStatus): SlotStatus = fromInt(
            Computed.value or status.value
        )
    }
}

class Configuration internal constructor(
    internal val base: Extension,
    internal val compartments: Map<Compartment, Extension>,
    internal val dynamicSlots: List<DynamicSlot>,
    internal val address: Map<Int, Int>,
    internal val staticValues: List<Any?>,
    internal val facets: Map<Int, List<FacetProvider<*>>>
) {
    internal val statusTemplate: List<SlotStatus> =
        List(dynamicSlots.size) { SlotStatus.None }

    @Suppress("UNCHECKED_CAST")
    fun <Output> staticFacet(facet: Facet<*, Output>): Output {
        val addr = address[facet.id] ?: return facet.default
        return staticValues[addr shr 1] as Output
    }

    companion object {
        @Suppress("UNCHECKED_CAST")
        fun resolve(
            base: Extension,
            compartments: Map<Compartment, Extension>,
            oldState: EditorState? = null
        ): Configuration {
            val fields = mutableListOf<StateField<*>>()
            val facets = mutableMapOf<
                Int,
                MutableList<FacetProvider<*>>
                >()
            val newCompartments =
                mutableMapOf<Compartment, Extension>()

            for (ext in flatten(
                base,
                compartments,
                newCompartments
            )) {
                if (ext is StateField<*>) {
                    fields.add(ext)
                } else if (ext is FacetProvider<*>) {
                    facets.getOrPut(ext.facet.id) {
                        mutableListOf()
                    }.add(ext)
                }
            }

            val address = mutableMapOf<Int, Int>()
            val staticValues = mutableListOf<Any?>()
            val dynamicSlotFactories =
                mutableListOf<
                    (Map<Int, Int>) -> DynamicSlot
                    >()

            for (field in fields) {
                address[field.id] =
                    dynamicSlotFactories.size shl 1
                dynamicSlotFactories.add { a ->
                    field.slot(a)
                }
            }

            val oldFacets = oldState?.config?.facets
            for ((id, providers) in facets) {
                val facet = providers[0].facet
                val oldProviders =
                    oldFacets?.get(id) ?: emptyList()
                if (providers.all {
                        it.provider is ProviderValue.Static
                    }
                ) {
                    address[facet.id] =
                        (staticValues.size shl 1) or 1
                    if (sameArray(oldProviders, providers)) {
                        staticValues.add(
                            oldState!!.facet(facet)
                        )
                    } else {
                        @Suppress("UNCHECKED_CAST")
                        val f = facet as Facet<Any?, Any?>
                        val value = f.combineFn(
                            providers.map { p ->
                                (p.provider as ProviderValue.Static).value
                            }
                        )
                        if (oldState != null &&
                            f.compareFn(
                                value,
                                oldState.facet(f)
                            )
                        ) {
                            staticValues.add(
                                oldState.facet(f)
                            )
                        } else {
                            staticValues.add(value)
                        }
                    }
                } else {
                    for (p in providers) {
                        if (p.provider is ProviderValue.Static) {
                            address[p.id] =
                                (staticValues.size shl 1) or 1
                            staticValues.add(
                                (p.provider as ProviderValue.Static).value
                            )
                        } else {
                            address[p.id] =
                                dynamicSlotFactories.size shl 1
                            dynamicSlotFactories.add { a ->
                                p.dynamicSlot(a)
                            }
                        }
                    }
                    address[facet.id] =
                        dynamicSlotFactories.size shl 1
                    val facetProviders = providers.toList()
                    dynamicSlotFactories.add { a ->
                        dynamicFacetSlot(
                            a,
                            facet as Facet<Any?, Any?>,
                            facetProviders
                                as List<FacetProvider<Any?>>
                        )
                    }
                }
            }

            val dynamic = dynamicSlotFactories.map {
                it(address)
            }
            return Configuration(
                base,
                newCompartments,
                dynamic,
                address,
                staticValues,
                facets
            )
        }
    }
}

private fun flatten(
    extension: Extension,
    compartments: Map<Compartment, Extension>,
    newCompartments: MutableMap<Compartment, Extension>
): List<Any> {
    val result = Array(5) { mutableListOf<Any>() }
    val seen = mutableMapOf<Extension, Int>()

    fun inner(ext: Extension, prec: Int) {
        val known = seen[ext]
        if (known != null) {
            if (known <= prec) return
            val found = result[known].indexOf(ext)
            if (found > -1) result[known].removeAt(found)
            if (ext is CompartmentInstance) {
                newCompartments.remove(ext.compartment)
            }
        }
        seen[ext] = prec
        when (ext) {
            is ExtensionList -> {
                for (e in ext.extensions) inner(e, prec)
            }
            is CompartmentInstance -> {
                if (newCompartments.containsKey(
                        ext.compartment
                    )
                ) {
                    throw IllegalArgumentException(
                        "Duplicate use of compartment " +
                            "in extensions"
                    )
                }
                val content = compartments[ext.compartment]
                    ?: ext.inner
                newCompartments[ext.compartment] = content
                inner(content, prec)
            }
            is PrecExtension -> inner(ext.inner, ext.prec)
            is StateField<*> -> {
                result[prec].add(ext)
                if (ext.provides != null) {
                    inner(ext.provides!!, prec)
                }
            }
            is FacetProvider<*> -> {
                result[prec].add(ext)
                if (ext.facet.extensions != null) {
                    inner(
                        ext.facet.extensions,
                        PrecValue.DEFAULT
                    )
                }
            }
            is ExtensionHolder -> inner(ext.extension, prec)
            else -> {
                throw IllegalStateException(
                    "Unrecognized extension value in " +
                        "extension set ($ext)"
                )
            }
        }
    }

    inner(extension, PrecValue.DEFAULT)
    return result.flatMap { it }
}

fun ensureAddr(state: EditorState, addr: Int): SlotStatus {
    if (addr and 1 != 0) return SlotStatus.Computed
    val idx = addr shr 1
    val status = state.status[idx]
    if (status == SlotStatus.Computing) {
        error(
            "Cyclic dependency between fields " +
                "and/or facets"
        )
    }
    if (status.isComputed) return status
    state.status[idx] = SlotStatus.Computing
    val changed = state.computeSlot!!(
        state,
        state.config.dynamicSlots[idx]
    )
    state.status[idx] = SlotStatus.computedOr(changed)
    return SlotStatus.computedOr(changed)
}

fun getAddr(state: EditorState, addr: Int): Any? {
    return if (addr and 1 != 0) {
        state.config.staticValues[addr shr 1]
    } else {
        state.values[addr shr 1]
    }
}
