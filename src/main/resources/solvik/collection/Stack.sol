// solvik::collection::Stack — Stack binding.
// Loaded from the compiler JAR; declares one native type in solvik::collection.

module solvik {
    module collection {
        // java.util.Stack<T> — last-in, first-out stack.
        pub type Stack<T> native "java.util.Stack" {

            pub func Stack()

            pub func push(item: T)

            // java.util.Stack.pop and peek report an empty stack by throwing, not by null,
            // but no universal non-null T exists for the erased read, so the type is T?.
            pub func pop() -> T?

            pub func peek() -> T?

            pub func empty() -> Boolean

            pub func search(item: Any) -> Integer

            pub func size() -> Integer

            // The iteration protocol (§7, §11.3): the loop reads the stack's own order, bottom to top.
            pub func iterator() -> Iterator<T>

            pub func toString() -> String
        }
    }
}
