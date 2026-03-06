# Troubleshooting

Common issues and solutions when working with Kodemirror.

## Build issues

### Java version errors

Kodemirror requires Java 11 or higher. If you see errors about
unsupported class file versions or missing APIs, check your Java
version:

```bash
java -version
```

For building from source, Java 21 is recommended:

```bash
export JAVA_HOME=/path/to/java-21
./gradlew build
```

### Compose compiler version mismatch

If you see errors about incompatible Compose compiler versions, make
sure your project's Kotlin and Compose plugin versions are compatible
with the versions Kodemirror was built against. Check the
`gradle/libs.versions.toml` in the Kodemirror repository for the
expected versions.

### wasmJs test failures

The wasmJs test environment requires `skiko.mjs` which may not be
available in all setups. wasmJs tests are disabled by default in
Kodemirror's build. If you're adding a new module, include this in
your `build.gradle.kts`:

```kotlin
tasks.configureEach {
    if ("wasmJs" in name || "WasmJs" in name) enabled = false
}
```

## Runtime issues

### "Field is not present in this state"

This error occurs when you try to read a `StateField` that wasn't
included in the editor's extensions. Make sure the field (or the
extension that provides it) is in your `EditorStateConfig.extensions`:

```kotlin
val myField = StateField.define(StateFieldSpec(
    create = { 0 },
    update = { v, tr -> v }
))

// Include the field in extensions
val state = EditorState.create(EditorStateConfig(
    extensions = ExtensionList(listOf(myField))
))

// Now this works
state.field(myField)
```

### Editor not rendering

If the editor composable appears blank:

1. Verify you have at least `:state` and `:view` dependencies
2. Check that your `EditorState` is created before the composable
   renders
3. Make sure the editor has non-zero size constraints in your layout

### Decorations not appearing

Decorations must be added in ascending position order. If you build
a `RangeSetBuilder` with positions out of order, the decorations
will be silently dropped:

```kotlin
val builder = RangeSetBuilder<Decoration>()
// Positions must be ascending
builder.add(from = 5, to = 10, value = deco1)
builder.add(from = 15, to = 20, value = deco2)  // OK: 15 > 10
// builder.add(from = 3, to = 8, value = deco3) // BAD: 3 < 15
```

### Changes not being applied

If `view.dispatch(TransactionSpec(...))` doesn't seem to do anything:

1. Check that your `ChangeSpec` positions are within the document
   length
2. Verify the editor is not in read-only mode
   (`readOnly.of(true)` in extensions)
3. Ensure change filters aren't rejecting the change
