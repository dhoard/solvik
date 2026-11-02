// solvik::collections::Stack — Stack binding.
// Loaded from the compiler JAR; declares one native type in solvik::collections.

module solvik {
    module collections {
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

            pub func toString() -> String
        }
    }
}
