// solvik::collections::Map — java.util.HashMap exposed as Map.
// Loaded from the compiler JAR; declares one native type in solvik::collections.

module solvik {
    module collections {
        // java.util.HashMap<K, V> — key/value map.
        pub type Map<K, V> native "java.util.HashMap" {

            pub func Map()

            pub func put(key: K, value: V)

            // java.util.HashMap.get returns null for a missing key, so the result is V? (§11).
            pub func get(key: Any) -> V?

            // java.util.HashMap.remove returns null when the key was absent.
            pub func removeValue(key: Any) -> V? native "remove"

            pub func containsKey(key: Any) -> Boolean

            pub func containsValue(value: Any) -> Boolean

            pub func size() -> Integer

            pub func isEmpty() -> Boolean

            pub func clear()

            pub func toString() -> String
        }
    }
}
