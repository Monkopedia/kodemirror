plugins {
    id("kodemirror.library")
}

// Compose-free all the way down, so wasmJs tests actually run here (#197).
kodemirrorLibrary {
    wasmJsTests.set(true)
}
