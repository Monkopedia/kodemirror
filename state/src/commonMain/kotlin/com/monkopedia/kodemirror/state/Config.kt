package com.monkopedia.kodemirror.state

/**
 * Utility function for combining behaviors to fill in a config
 * object from an array of provided configs. `defaults` should hold
 * default values for all optional fields in the config.
 *
 * The function will, by default, error
 * when a field gets two values that aren't `===`-equal, but you can
 * provide combine functions per field to do something else.
 */
fun <Config> combineConfig(
    configs: List<Map<String, Any?>>,
    defaults: Map<String, Any?>,
    combine: Map<String, (Any?, Any?) -> Any?> = emptyMap()
): Map<String, Any?> {
    val result = mutableMapOf<String, Any?>()
    for (config in configs) {
        for ((key, value) in config) {
            val current = result[key]
            if (current == null) {
                result[key] = value
            } else if (current === value || value == null) {
                // No conflict
            } else if (combine.containsKey(key)) {
                result[key] = combine[key]!!(current, value)
            } else {
                throw IllegalArgumentException(
                    "Config merge conflict for field $key"
                )
            }
        }
    }
    for ((key, defaultValue) in defaults) {
        if (!result.containsKey(key)) {
            result[key] = defaultValue
        }
    }
    return result
}
