// solvik::collection::Set — java.util.Set exposed as Set.
// Loaded from the compiler JAR; declares one native trait in solvik::collection.

module solvik {
    module collection {
        // java.util.Set<T> — the view a Map publishes over its keys. A set is a Java interface,
        // so only a trait can bind it; a Solvik `Boolean` result is why it is a Solvik-owned
        // interface with a generated $of conversion rather than an extension of java.util.Set.
        pub trait Set<T> native "java.util.Set" {

            pub func size() -> Integer

            pub func isEmpty() -> Boolean

            pub func contains(item: Any) -> Boolean

            // The iteration protocol (§7, §11.3): `for key in map.keys()` reads this set's order,
            // which for a java.util.HashMap is unspecified. The element is T, not T?, because the
            // packaged collections hold only values of a non-null T.
            pub func iterator() -> Iterator<T>
        }
    }
}
