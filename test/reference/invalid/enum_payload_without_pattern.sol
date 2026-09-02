// expected C107: payload cases need pattern arguments in a switch
package reference_invalid

enum Option<T> {
    Some(T)
    None
}

func f(o: Option<int>) -> int {
    switch o {
        case Option.Some {
            return 1
        }
        case Option.None {
            return 2
        }
    }
    return 0
}
