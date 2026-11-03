// solvik::collection::Iterator — java.util.Iterator exposed as the iteration protocol.
// Loaded from the compiler JAR; declares one native trait in solvik::collection.

module solvik {
    module collection {
        // java.util.Iterator<T> — the cursor a `for ... in` statement reads. A type participates in
        // iteration by declaring `pub func iterator() -> Iterator<T>`; the loop name is bound to T.
        pub trait Iterator<T> native "java.util.Iterator" {

            // java.util.Iterator.hasNext returns a primitive boolean, which the generated adapter
            // boxes into the one Solvik Boolean type (§11.1).
            pub func hasNext() -> Boolean

            // For a non-null element type the result is declared non-null: the packaged collections
            // can only hold non-null elements of a non-null T, and a Solvik implementation returns
            // the declared type. A `T?` instantiation stays nullable.
            pub func next() -> T
        }
    }
}
