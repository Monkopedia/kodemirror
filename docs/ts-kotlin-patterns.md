# TypeScript → Kotlin Conversion Patterns

Living document of patterns used when porting CodeMirror 6 to Kotlin.

## Union Types

### `A | B` → `Either<A, B>`

```kotlin
sealed class Either<out A, out B> {
    class Left<A>(override val a: A) : Either<A, Nothing>()
    class Right<B>(override val b: B) : Either<Nothing, B>()
}
```

Use `fold(aMap, bMap)` to consume.

### `T | T[]` → `SingleOrList<T>`

```kotlin
@JvmInline
value class SingleOrList<T> private constructor(private val item: Any?) {
    val list: List<T>  // always works
    val singleOrNull: T?  // non-null if exactly one item
}
```

## Enums

### `const enum` → `object` with `const val` or `enum class`

Use `enum class` when values are used as type discriminators.
Use `object` with `const val` when values are bit flags or raw integers.

## Iterators

### Self-returning `next(): this` → generic `T : TextIterator<T>`

```kotlin
interface TextIterator<T : TextIterator<T>> : Iterator<String> {
    fun next(skip: Int?): T
    val value: String
    val done: Boolean
}
```

## Module-Level Mutable State

### Module-level `let` → `kotlinx-atomicfu`

Use `atomic()` for thread-safe mutable module-level state.

## Test Patterns

### `describe/it` → `class FooTest { @Test fun ... }`

- `describe("Foo")` → `class FooTest`
- `it("does thing")` → `@Test fun does_thing()`
- `ist(a, b)` → `assertEquals(b, a)`
- `ist(a)` → `assertTrue(a)` or `assertNotNull(a)`
- `ist.throws(...)` → `assertFailsWith<...> { ... }`

## Assertions

| TypeScript (`ist`) | Kotlin |
|---|---|
| `ist(value)` | `assertTrue(value)` / `assertNotNull(value)` |
| `ist(a, b)` | `assertEquals(b, a)` |
| `ist(a, b, "op")` | `assertEquals(b, a)` / `assertTrue(a > b)` etc. |
| `ist.throws(() => ...)` | `assertFailsWith<Exception> { ... }` |

## Null Handling

### `x!` (non-null assertion) → `x!!`

### Optional chaining `x?.y` → same in Kotlin

### `x ?? default` → `x ?: default`

## Classes

### TypeScript `class` with `readonly` → Kotlin `val` properties

### `private` → `private` or `internal`

Prefer `internal` for package-level visibility when the member is accessed
across files within the same module.

## Functions

### Arrow functions → lambdas

`(x: number) => x + 1` → `{ x: Int -> x + 1 }`

### Default parameters → same in Kotlin

### Rest parameters `...args: T[]` → `vararg args: T`
