// expected C107: pattern positions accept bindings, wildcards, literals, or nested patterns
package reference_invalid

enum Option<T> {
    Some(T)
    None
}

func f(o: Option<int>) -> int {
    switch o {
        case Option.Some(v + 1) {
            return 1
        }
        case Option.None {
            return 2
        }
    }
    return 0
}
