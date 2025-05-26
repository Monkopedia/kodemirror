package com.monkopedia.kodemirror.state

import com.monkopedia.kodemirror.state.Either.Companion.asLeft
import com.monkopedia.kodemirror.state.Either.Companion.asRight
import com.monkopedia.kodemirror.state.SingleOrList.Companion.SingleOrList
import com.monkopedia.kodemirror.state.SingleOrList.Companion.coerceList
import com.monkopedia.kodemirror.state.SingleOrList.Companion.coerceSingle
import com.monkopedia.kodemirror.state.SingleOrList.Companion.list
import com.monkopedia.kodemirror.state.SingleOrList.Companion.single
import kotlin.jvm.JvmName
import kotlinx.atomicfu.atomic
import kotlinx.serialization.KSerializer
import kotlinx.serialization.serializer

// import {Transaction, StateEffect, StateEffectType} from "./transaction"
// import {EditorState} from "./state"

private val nextID = atomic(0)

data class FacetConfig<Input, Output>(
    // / How to combine the input values into a single output value. When
    // / not given, the array of input values becomes the output. This
    // / fun will immediately be called on creating the facet, with
    // / an empty array, to compute the facet's default value when no
    // / inputs are present.
    val combine: ((value: List<Input>) -> Output)? = null,

    // / How to compare output values to determine whether the value of
    // / the facet changed. Defaults to comparing by `===` or, if no
    // / `combine` fun was given, comparing each element of the
    // / array with `===`.
    val compare: ((a: Output, b: Output) -> Boolean)? = null,

    // / How to compare input values to avoid recomputing the output
    // / value when no inputs changed. Defaults to comparing with `===`.
    val compareInput: ((a: Input, b: Input) -> Boolean)? = null,

    // / Forbids dynamic inputs to this facet.
    val static: Boolean = false,

    // / If given, these extension(s) (or the result of calling the given
    // / fun with the facet) will be added to any state where this
    // / facet is provided. (Note that, while a facet's default value can
    // / be read from a state even if the facet wasn't present in the
    // / state at all, these extensions won't be added in that
    // / situation.)
    val enables: Either<Extension, ((self: Facet<Input, Output>) -> Extension)>? = null
)

// / A facet is a labeled value that is associated with an editor
// / state. It takes inputs from any Int of extensions, and combines
// / those into a single output value.
// /
// / Examples of uses of facets are the [tab
// / size](#state.EditorState^tabSize), [editor
// / attributes](#view.EditorView^editorAttributes), and [update
// / listeners](#view.EditorView^updateListener).
// /
// / Note that `Facet` instances can be used anywhere where
// / [`FacetReader`](#state.FacetReader) is expected.
class Facet<Input, Output> private constructor(
    // / @internal
    internal val combine: (values: List<Input>) -> Output,
    // / @internal
    internal val compareInput: (a: Input, b: Input) -> Boolean,
    // / @internal
    internal val compare: (a: Output, b: Output) -> Boolean,
    private val isStatic: Boolean,
    val enables: Either<Extension, ((self: Facet<Input, Output>) -> Extension)>?
) : FacetReader<Output>,
    Extension {
    // / @internal
    override val id = nextID.getAndIncrement()

    // / @internal
    override val default: Output = combine(emptyList())

    // / @internal
    internal val extension: Extension? = enables?.fold(
        { this },
        { this(this@Facet) }
    )
    override val tag: Output
        get() = error("Unsupported")

    // / Returns a facet reader for this facet, which can be used to
    // / [read](#state.EditorState.facet) it but not to define values for it.
    val reader: FacetReader<Output>
        get() {
            return this
        }

    // / Returns an extension that adds the given value to this facet.
    fun of(value: Input): Extension =
        FacetProvider(emptyList(), this, Provider.Static, Either.Right(value))

    // / Create an extension that computes a value for the facet from a
    // / state. You must take care to declare the parts of the state that
    // / this value depends on, since your fun is only called again
    // / for a new state when one of those parts changed.
    // /
    // / In cases where your value depends only on a single field, you'll
    // / want to use the [`from`](#state.Facet.from) method instead.
    fun compute(deps: List<Slot<*>>, get: (state: EditorState) -> Input): Extension {
        if (this.isStatic) throw Error("Can't compute a static facet")
        return FacetProvider(
            deps,
            this,
            Provider.Single,
            Either.Left({ state: EditorState -> SingleOrList<Input>(item = get(state)) })
        )
    }

    // / Create an extension that computes zero or more values for this
    // / facet from a state.
    fun computeN(deps: List<Slot<*>>, get: (state: EditorState) -> List<Input>): Extension {
        if (this.isStatic) throw Error("Can't compute a static facet")
        return FacetProvider<Input>(
            deps,
            this,
            Provider.Multi,
            { state: EditorState -> get(state).list }.asLeft
        )
    }

    // / Shorthand method for registering a facet source with a state
    // / field as input. If the field's type corresponds to this facet's
    // / input type, the getter fun can be omitted. If given, it
    // / will be used to retrieve the input from the field value.
    fun <T : Input> from(field: StateField<T>, get: ((value: T) -> Input)? = null): Extension {
        val getter = get ?: { x: T -> x }
        return this.compute(listOf(field)) { state -> getter(state.field(field)) }
    }

    companion object {
        // / Define a new facet.
        @JvmName("defineConfig")
        fun <Input> define(
            config: FacetConfig<Input, List<Input>> = FacetConfig()
        ): Facet<Input, List<Input>> = Facet(
            config.combine ?: { a -> a },
            config.compareInput ?: { a, b -> a == b },
            config.compare ?: config.combine?.let { ::sameArray } ?: { a, b -> a == b },
            config.static,
            config.enables
        )

        // / Define a new facet.
        @JvmName("defineConfigOutput")
        fun <Input, Output> define(
            config: FacetConfig<Input, Output> = FacetConfig()
        ): Facet<Input, Output> = Facet(
            config.combine ?: { a: List<Input> -> a as Output },
            config.compareInput ?: { a: Input, b: Input -> a == b },
            config.compare ?: { a, b -> a == b },
            config.static,
            config.enables
        )

        fun <Input> define(
            combine: ((value: List<Input>) -> List<Input>)? = null,
            compare: ((a: List<Input>, b: List<Input>) -> Boolean)? = null,
            compareInput: ((a: Input, b: Input) -> Boolean)? = null,
            static: Boolean = false,
            enables: Either<Extension, ((self: Facet<Input, List<Input>>) -> Extension)>? = null
        ): Facet<Input, List<Input>> =
            define(FacetConfig(combine, compare, compareInput, static, enables))

        @JvmName("defineOutput")
        fun <Input, Output> define(
            combine: ((value: List<Input>) -> Output)? = null,
            compare: ((a: Output, b: Output) -> Boolean)? = null,
            compareInput: ((a: Input, b: Input) -> Boolean)? = null,
            static: Boolean = false,
            enables: Either<Extension, ((self: Facet<Input, Output>) -> Extension)>? = null
        ): Facet<Input, Output> =
            define(FacetConfig(combine, compare, compareInput, static, enables))
    }
}

// / A facet reader can be used to fetch the value of a facet, through
// / [`EditorState.facet`](#state.EditorState.facet) or as a dependency
// / in [`Facet.compute`](#state.Facet.compute), but not to define new
// / values for the facet.
interface FacetReader<Output> : IdSlot<Output> {
    // / @internal
    override val id: Int

    // / @internal
    val default: Output

    // / Dummy tag that makes sure TypeScript doesn't consider all object
    // / types as conforming to this type. Not actually present on the
    // / object.
    val tag: Output
}

internal fun <T> sameArray(a: List<T>, b: List<T>): Boolean = a == b

sealed interface Slot<T> {
    data object Doc : Slot<Nothing>
    data object Selection : Slot<Nothing>
}

sealed interface IdSlot<T> : Slot<T> {
    val id: Int
}

enum class Provider { Static, Single, Multi }

class FacetProvider<Input>(
    val dependencies: List<Slot<*>>,
    val facet: Facet<Input, *>,
    val type: Provider,
    val value: Either<out ((state: EditorState) -> SingleOrList<Input>), out Input>
) : Extension {
    val id = nextID.getAndIncrement()

    fun dynamicSlot(addresses: Map<Int, Int>): DynamicSlot {
        val getter: (state: EditorState) -> SingleOrList<Input> = this.value.fold(
            aMap = { this },
            bMap = { { state: EditorState -> this.single } }
        )
        val compare = this.facet.compareInput
        val id = this.id
        val idx = addresses[id]!! shr 1
        val multi = this.type == Provider.Multi
        var depDoc = false
        var depSel = false
        val depAddrs = mutableListOf<Int>()
        for (dep in this.dependencies) {
            when (dep) {
                Slot.Doc -> depDoc = true
                Slot.Selection -> depSel = true
                is IdSlot<*> -> {
                    addresses.getOrElse(dep.id) { 1 }.takeIf { (it and 1) == 0 }?.let {
                        depAddrs.add(it)
                    }
                }
            }
        }

        return object : DynamicSlot {
            override fun create(state: EditorState): SlotStatus {
                state.values[idx] = getter(state)
                return SlotStatusChanged
            }

            override fun update(state: EditorState, tr: Transaction): SlotStatus {
                if ((depDoc && tr.docChanged) ||
                    (depSel && (tr.docChanged || tr.selection != null)) ||
                    ensureAll(state, depAddrs)
                ) {
                    val newVal = getter(state)

                    val stateValue = state.values[idx]

                    if (!compare(multi, newVal, stateValue, compare)) {
                        state.values[idx] = newVal
                        return SlotStatusChanged
                    }
                }
                return SlotStatusUnresolved
            }

            override fun reconfigure(state: EditorState, oldState: EditorState): SlotStatus {
                val newVal: SingleOrList<Input>?
                val oldAddr = oldState.config.address[id]
                if (oldAddr != null) {
                    val oldVal = getAddr(oldState, oldAddr)
                    val hasChanges = this@FacetProvider.dependencies.all { dep ->
                        when (dep) {
                            is Facet<*, *> -> (oldState.facet(dep) === state.facet(dep))
                            is StateField -> (oldState.field(dep, false) == state.field(dep, false))
                            else -> true
                        }
                    }
                    newVal = getter(state)
                    if (hasChanges || compare(multi, newVal, oldVal, compare)) {
                        state.values[idx] = oldVal
                        return SlotStatusUnresolved
                    }
                } else {
                    newVal = getter(state)
                }
                state.values[idx] = newVal
                return SlotStatusChanged
            }
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun compare(
        multi: Boolean,
        newVal: SingleOrList<Input>,
        stateValue: Any?,
        compare: (a: Input, b: Input) -> Boolean
    ) = if (multi) {
        compareArray(newVal.coerceList(), stateValue.coerceList(), compare)
    } else {
        compare(newVal.coerceSingle(), stateValue.coerceSingle())
    }
}

internal fun <T> compareArray(a: List<T>, b: List<T>, compare: (a: T, b: T) -> Boolean): Boolean {
    if (a.size != b.size) return false
    return a.zip(b).all { (a, b) ->
        compare(a, b)
    }
}

internal fun ensureAll(state: EditorState, addrs: List<Int>): Boolean = addrs.any { addr ->
    (ensureAddr(state, addr) and SlotStatusChanged) != 0
}

internal fun <Input, Output> dynamicFacetSlot(
    addresses: Map<Int, Int>,
    facet: Facet<Input, Output>,
    providers: List<FacetProvider<*>>
): DynamicSlot {
    val providerAddrs = providers.map { p -> addresses[p.id]!! }
    val providerTypes = providers.map { p -> p.type }
    val dynamic = providerAddrs.filter { p -> (p and 1) == 0 }
    val idx = addresses[facet.id]!! shr 1

    fun get(state: EditorState): Output {
        val values = buildList {
            for (i in providerAddrs.indices) {
                val value = getAddr(state, providerAddrs[i])
                if (providerTypes[i] == Provider.Multi) {
                    addAll(value.coerceList<Input>())
                } else {
                    add(value.coerceSingle<Input>())
                }
            }
        }
        return facet.combine(values)
    }

    return object : DynamicSlot {
        override fun create(state: EditorState): SlotStatus {
            for (addr in providerAddrs) ensureAddr(state, addr)
            state.values[idx] = get(state)
            return SlotStatusChanged
        }

        override fun update(state: EditorState, tr: Transaction): SlotStatus {
            if (!ensureAll(state, dynamic)) return 0
            val value = get(state)
            @Suppress("UNCHECKED_CAST")
            if (facet.compare(value, state.values[idx] as Output)) return SlotStatusUnresolved
            state.values[idx] = value
            return SlotStatusChanged
        }

        override fun reconfigure(state: EditorState, oldState: EditorState): SlotStatus {
            val depChanged = ensureAll(state, providerAddrs)
            val oldProviders = oldState.config.facets[facet.id]
            val oldValue = oldState.facet(facet)
            if (oldProviders != null && !depChanged && sameArray(providers, oldProviders)) {
                state.values[idx] = oldValue
                return SlotStatusUnresolved
            }
            val value = get(state)
            if (facet.compare(value, oldValue)) {
                state.values[idx] = oldValue
                return SlotStatusUnresolved
            }
            state.values[idx] = value
            return SlotStatusChanged
        }
    }
}

data class StateFieldSpec<Value>(
    // / Creates the initial value for the field when a state is created.
    val create: (state: EditorState) -> Value,

    // / Compute a new value from the field's previous value and a
    // / [transaction](#state.Transaction).
    val update: (value: Value, transaction: Transaction) -> Value,

    // / Compare two values of the field, returning `true` when they are
    // / the same. This is used to avoid recomputing facets that depend
    // / on the field when its value did not change. Defaults to using
    // / `===`.
    val compare: ((a: Value, b: Value) -> Boolean)? = null,

    // / Provide extensions based on this field. The given fun will
    // / be called once with the initialized field. It will usually want
    // / to call some facet's [`from`](#state.Facet.from) method to
    // / create facet inputs from this field, but can also return other
    // / extensions that should be enabled when the field is present in a
    // / configuration.
    val provide: ((field: StateField<Value>) -> Extension)? = null,
    // / A serializer used to serialize this field's content to JSON. Only
    // / necessary when this field is included in the argument to
    // / [`EditorState.toJSON`](#state.EditorState.toJSON).
    val serializer: KSerializer<Value>? = null
)

data class InitFieldType(
    val field: StateField<*>,
    val create: ((state: EditorState) -> Any?)? = null
)

internal val initField = Facet.define<InitFieldType>(static = true)

typealias StateGetter<Value> = (state: EditorState) -> Value

// / Fields can store additional information in an editor state, and
// / keep it in sync with the rest of the state.
class StateField<Value> private constructor(
    // / @internal
    override val id: Int = nextID.getAndIncrement(),
    private val createF: (state: EditorState) -> Value,
    private val updateF: (value: Value, tr: Transaction) -> Value,
    private val compareF: (a: Value, b: Value) -> Boolean,
    // / @internal
    internal val spec: StateFieldSpec<Value>
) : IdSlot<Value>,
    Extension {
    // / @internal
    public var provides: Extension? = null

    fun init(create: (state: EditorState) -> Value): Extension =
        listOf(this, initField.of(InitFieldType(field = this, create = create))).extension

    private fun createImpl(state: EditorState): Value {
        val init = state.facet(initField).find { it.field == this }
        @Suppress("UNCHECKED_CAST")
        return ((init?.create as? (state: EditorState) -> Value) ?: this.createF)(state)
    }

    // / @internal
    fun slot(addresses: Map<Int, Int>): DynamicSlot {
        val idx = addresses[this.id]!! shr 1
        return object : DynamicSlot {
            override fun create(state: EditorState): SlotStatus {
                state.values[idx] = createImpl(state)
                return SlotStatusChanged
            }

            override fun update(state: EditorState, tr: Transaction): SlotStatus {
                val oldVal = state.values[idx]
                val value = this@StateField.updateF(oldVal as Value, tr)
                if (this@StateField.compareF(oldVal, value)) return 0
                state.values[idx] = value
                return SlotStatusChanged
            }

            override fun reconfigure(state: EditorState, oldState: EditorState): SlotStatus {
                if (oldState.config.address[this@StateField.id] != null) {
                    state.values[idx] = oldState.field(this@StateField)
                    return 0
                }
                state.values[idx] = createImpl(state)
                return SlotStatusChanged
            }
        }
    }

    // / State field instances can be used as
    // / [`Extension`](#state.Extension) values to enable the field in a
    // / given state.
    val extension: Extension
        get() {
            return this
        }

    companion object {

        // / Define a state field.
        fun <Value> define(config: StateFieldSpec<Value>): StateField<Value> {
            val field = StateField<Value>(
                createF = config.create,
                updateF = config.update,
                compareF = config.compare ?: { a, b -> a == b },
                spec = config
            )
            config.provide?.let { field.provides = it(field) }
            return field
        }

        fun <Value> define(
            // / Creates the initial value for the field when a state is created.
            create: (state: EditorState) -> Value,

            // / Compute a new value from the field's previous value and a
            // / [transaction](#state.Transaction).
            update: (value: Value, transaction: Transaction) -> Value,

            // / Compare two values of the field, returning `true` when they are
            // / the same. This is used to avoid recomputing facets that depend
            // / on the field when its value did not change. Defaults to using
            // / `===`.
            compare: ((a: Value, b: Value) -> Boolean)? = null,

            // / Provide extensions based on this field. The given fun will
            // / be called once with the initialized field. It will usually want
            // / to call some facet's [`from`](#state.Facet.from) method to
            // / create facet inputs from this field, but can also return other
            // / extensions that should be enabled when the field is present in a
            // / configuration.
            provide: ((field: StateField<Value>) -> Extension)? = null
        ): StateField<Value> = define(StateFieldSpec(create, update, compare, provide))

        inline fun <reified Value> defineSerializable(
            // / Creates the initial value for the field when a state is created.
            noinline create: (state: EditorState) -> Value,

            // / Compute a new value from the field's previous value and a
            // / [transaction](#state.Transaction).
            noinline update: (value: Value, transaction: Transaction) -> Value,

            // / Compare two values of the field, returning `true` when they are
            // / the same. This is used to avoid recomputing facets that depend
            // / on the field when its value did not change. Defaults to using
            // / `===`.
            noinline compare: ((a: Value, b: Value) -> Boolean)? = null,

            // / Provide extensions based on this field. The given fun will
            // / be called once with the initialized field. It will usually want
            // / to call some facet's [`from`](#state.Facet.from) method to
            // / create facet inputs from this field, but can also return other
            // / extensions that should be enabled when the field is present in a
            // / configuration.
            noinline provide: ((field: StateField<Value>) -> Extension)? = null
        ): StateField<Value> =
            define(StateFieldSpec(create, update, compare, provide, serializer<Value>()))
    }
}

// / Extension values can be
// / [provided](#state.EditorStateConfig.extensions) when creating a
// / state to attach various kinds of configuration and behavior
// / information. They can either be built-in extension-providing
// / objects, such as [state fields](#state.StateField) or [facet
// / providers](#state.Facet.of), or objects with an extension in its
// / `extension` property. Extensions can be nested in arrays
// / arbitrarily deep—they will be flattened when processed.
interface Extension
data class ExtensionSet internal constructor(val extensions: List<Extension>) : Extension

val List<Extension>.extension: Extension
    get() = ExtensionSet(this.flatMap { (it as? ExtensionSet)?.extensions ?: listOf(it) })
// export type Extension = { extension: Extension } | readonly Extension[]

internal object Prec_ {
    val lowest = 4
    val low = 3
    val default = 2
    val high = 1
    val highest = 0
}

internal fun prec(value: Int): (Extension) -> Extension =
    { ext: Extension -> PrecExtension(ext, value) as Extension }

// / By default extensions are registered in the order they are found
// / in the flattened form of nested array that was provided.
// / Individual extension values can be assigned a precedence to
// / override this. Extensions that do not have a precedence set get
// / the precedence of the nearest parent with a precedence, or
// / [`default`](#state.Prec.default) if there is no such parent. The
// / final ordering of extensions is determined by first sorting by
// / precedence and then by order within each precedence.
object Prec {
    // / The highest precedence level, for extensions that should end up
    // / near the start of the precedence ordering.
    val highest = prec(Prec_.highest)

    // / A higher-than-default precedence, for extensions that should
    // / come before those with default precedence.
    val high = prec(Prec_.high)

    // / The default precedence, which is also used for extensions
    // / without an explicit precedence.
    val default = prec(Prec_.default)

    // / A lower-than-default precedence.
    val low = prec(Prec_.low)

    // / The lowest precedence level. Meant for things that should end up
    // / near the end of the extension order.
    val lowest = prec(Prec_.lowest)
}

class PrecExtension constructor(val inner: Extension, val prec: Int) : Extension

// / Extension compartments can be used to make a configuration
// / dynamic. By [wrapping](#state.Compartment.of) part of your
// / configuration in a compartment, you can later
// / [replace](#state.Compartment.reconfigure) that part through a
// / transaction.
class Compartment {
    // / Create an instance of this compartment to add to your [state
    // / configuration](#state.EditorStateConfig.extensions).
    fun of(ext: Extension): Extension = CompartmentInstance(this, ext)

    // / Create an [effect](#state.TransactionSpec.effects) that
    // / reconfigures this compartment.
    fun reconfigure(content: Extension): StateEffect<*> =
        Compartment.reconfigure.of(CompartmentType(compartment = this, extension = content))

    // / Get the current content of the compartment in the state, or
    // / `undefined` if it isn't present.
    fun get(state: EditorState): Extension? = state.config.compartments.get(this)

    data class CompartmentType(val compartment: Compartment, val extension: Extension)

    companion object {
        // / This is initialized in state.ts to avoid a cyclic dependency
        // / @internal
        val reconfigure = StateEffect.define<CompartmentType>()
//        Compartment.reconfigure = StateEffect.define<{ compartment: Compartment, extension: Extension }>()
    }
}

class CompartmentInstance constructor(val compartment: Compartment, val inner: Extension) :
    Extension

interface DynamicSlot {
    fun create(state: EditorState): SlotStatus
    fun update(state: EditorState, tr: Transaction): SlotStatus
    fun reconfigure(state: EditorState, oldState: EditorState): SlotStatus
}

class Configuration constructor(
    val base: Extension,
    val compartments: Map<Compartment, Extension>,
    val dynamicSlots: List<DynamicSlot>,
    val address: Map<Int, Int>,
    val staticValues: List<Any>,
    val facets: Map<Int, List<FacetProvider<*>>>
) {
    val statusTemplate: MutableList<SlotStatus> =
        dynamicSlots.map { SlotStatusUnresolved }.toMutableList()

    fun <Output> staticFacet(facet: Facet<*, Output>): Output {
        val addr = this.address[facet.id]
            ?: return facet.default
        @Suppress("UNCHECKED_CAST")
        return this.staticValues[addr shr 1] as Output
    }

    companion object {
        fun resolve(
            base: Extension,
            compartments: Map<Compartment, Extension>,
            oldState: EditorState? = null
        ): Configuration {
            val fields = mutableListOf<StateField<*>>()
            val facets = mutableMapOf<Int, MutableList<FacetProvider<*>>>()
            val newCompartments = mutableMapOf<Compartment, Extension>()

            for (ext in flatten(base, compartments, newCompartments)) {
                ext.fold(
                    {
                        val list = facets[facet.id] ?: mutableListOf<FacetProvider<*>>().also {
                            facets[facet.id] = it
                        }
                        list.add(this)
                    },
                    { fields.add(this) }
                )
            }

            val address = mutableMapOf<Int, Int>()
            val staticValues = mutableListOf<Any>()
            val dynamicSlots = mutableListOf<(address: Map<Int, Int>) -> DynamicSlot>()

            for (field in fields) {
                address[field.id] = dynamicSlots.size shl 1
                dynamicSlots.add({ a -> field.slot(a) })
            }

            val oldFacets = oldState?.config?.facets
            for ((id, providers) in facets) {
                @Suppress("UNCHECKED_CAST")
                val facet = providers[0].facet as Facet<Any?, Any?>
                val oldProviders = oldFacets?.get(id) ?: mutableListOf()
                if (providers.all { p -> p.type == Provider.Static }) {
                    address[facet.id] = (staticValues.size shl 1) or 1
                    if (sameArray(oldProviders, providers)) {
                        staticValues.add(oldState!!.facet(facet)!!)
                    } else {
                        val value = facet.combine(providers.map { p -> p.value.b })
                        val element = oldState?.takeIf {
                            facet.compare(value, it.facet(facet))
                        }?.facet(facet) ?: value
                        staticValues.add(element!!)
                    }
                } else {
                    for (p in providers) {
                        if (p.type == Provider.Static) {
                            address[p.id] = (staticValues.size shl 1) or 1
                            staticValues.add(p.value)
                        } else {
                            address[p.id] = dynamicSlots.size shl 1
                            dynamicSlots.add { a -> p.dynamicSlot(a) }
                        }
                    }
                    address[facet.id] = dynamicSlots.size shl 1
                    dynamicSlots.add { a ->
                        dynamicFacetSlot(
                            a,
                            facet,
                            providers
                        )
                    }
                }
            }

            val dynamic = dynamicSlots.map { f -> f(address) }
            return Configuration(base, newCompartments, dynamic, address, staticValues, facets)
        }
    }
}

internal fun flatten(
    extension: Extension,
    compartments: Map<Compartment, Extension>,
    newCompartments: MutableMap<Compartment, Extension>
): List<Either<out FacetProvider<*>, out StateField<*>>> {
    val result = List(5) { mutableListOf<Either<out FacetProvider<*>, out StateField<*>>>() }
    val seen = mutableMapOf<Extension, Int>()
    fun inner(ext: Extension, prec: Int) {
        val known = seen[ext]
        if (known != null) {
            if (known <= prec) return
            val found = result[known].indexOfFirst { it.a == ext }
            if (found > -1) result[known].subList(found, 1)
            if (ext is CompartmentInstance) newCompartments.remove(ext.compartment)
        }
        seen[ext] = prec
        when (ext) {
            is ExtensionSet -> {
                for (e in ext.extensions) inner(e, prec)
            }

            is CompartmentInstance -> {
                if (newCompartments.containsKey(ext.compartment)) {
                    throw IllegalArgumentException("Duplicate use of compartment in extensions")
                }
                val content = compartments.get(ext.compartment) ?: ext.inner
                newCompartments.set(ext.compartment, content)
                inner(content, prec)
            }

            is PrecExtension -> {
                inner(ext.inner, ext.prec)
            }

            is StateField<*> -> {
                result[prec].add(ext.asRight)
                ext.provides?.let { inner(it, prec) }
            }

            is FacetProvider<*> -> {
                result[prec].add(ext.asLeft)
                ext.facet.extension?.let { inner(it, Prec_.default) }
            }

            is Facet<*, *> -> {
                val content = ext.extension
                    ?: throw Error(
                        "Unrecognized extension value in extension set ($ext). This sometimes happens because multiple instances of @codemirror/state are loaded, breaking instanceof checks."
                    )
                inner(content, prec)
            }
        }
    }
    inner(extension, Prec_.default)
    return result.flatten()
}

typealias SlotStatus = Int

val SlotStatusUnresolved = 0
val SlotStatusChanged = 1
val SlotStatusComputed = 2
val SlotStatusComputing = 4

fun ensureAddr(state: EditorState, addr: Int): SlotStatus {
    if ((addr and 1) != 0) return SlotStatusComputed
    val idx = addr shr 1
    val status = state.status[idx]
    if (status == SlotStatusComputing) throw Error("Cyclic dependency between fields and/or facets")
    if ((status and SlotStatusComputed) != 0) return status
    state.status[idx] = SlotStatusComputing
    val changed = state.computeSlot?.invoke(state, state.config.dynamicSlots[idx]) ?: 0
    return (SlotStatusComputed or changed).also {
        state.status[idx] = it
    }
}

fun getAddr(state: EditorState, addr: Int): Any? = if ((addr and 1) !=
    0
) {
    state.config.staticValues[addr shr 1]
} else {
    state.values[addr shr 1]
}
