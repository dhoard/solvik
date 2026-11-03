// solvik::collection::Collection — java.util.Collection exposed as Collection.
// Loaded from the compiler JAR; declares one native trait in solvik::collection.

module solvik {
    module collection {
        // java.util.Collection<T> — the view a Map publishes over its values. It is separate from
        // Set because a native member must name the exact Java interface it binds:
        // HashMap.values() returns a java.util.Collection, HashMap.keySet() a java.util.Set.
        pub trait Collection<T> native "java.util.Collection" {

            pub func size() -> Integer

            pub func isEmpty() -> Boolean

            pub func contains(item: Any) -> Boolean

            // The iteration protocol (§7, §11.3): `for value in map.values()` reads this view's
            // order, which for a java.util.HashMap is unspecified.
            pub func iterator() -> Iterator<T>
        }
    }
}
