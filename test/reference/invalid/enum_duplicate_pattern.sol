// expected C106: the same case cannot be matched twice
package reference_invalid

enum Option<T> {
    Some(T)
    None
}

func f(o: Option<int>) -> int {
    switch o {
        case Option.Some(v) {
            return 1
        }
        case Option.Some(w) {
            return 2
        }
        case Option.None {
            return 3
        }
    }
    return 0
}
