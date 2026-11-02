// solvik::collections::List — java.util.ArrayList exposed as List.
// Loaded from the compiler JAR; declares one native type in solvik::collections.

module solvik {
    module collections {
        // java.util.ArrayList<T> — growable list exposed under the name `List`.
        pub type List<T> native "java.util.ArrayList" {

            pub func List()

            pub func add(item: T)

            // java.util.ArrayList.get throws for a bad index but never returns null; T? records
            // only that no universal non-null value exists for an erased element read.
            pub func get(index: Integer) -> T?

            pub func set(index: Integer, item: T)

            // java.util.ArrayList.remove returns the removed element.
            pub func remove(index: Integer) -> T?

            pub func removeValue(item: Any) -> Boolean native "remove"

            pub func size() -> Integer

            pub func isEmpty() -> Boolean

            pub func contains(item: Any) -> Boolean

            pub func indexOf(item: Any) -> Integer

            pub func clear()

            pub func toString() -> String
        }
    }
}
