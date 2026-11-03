// solvik::collection::Map — java.util.HashMap exposed as Map.
// Loaded from the compiler JAR; declares one native type in solvik::collection.

module solvik {
    module collection {
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

            // HashMap.keySet() and HashMap.values() are live views, not copies, and both publish
            // the iteration protocol (§7, §11.3), so a map can be enumerated by key or by value.
            // The two members need distinct declaration types because a native member binds one
            // exact Java interface: java.util.Set for keySet, java.util.Collection for values.
            pub func keys() -> Set<K> native "keySet"

            pub func values() -> Collection<V> native "values"

            pub func size() -> Integer

            pub func isEmpty() -> Boolean

            pub func clear()

            pub func toString() -> String
        }
    }
}
