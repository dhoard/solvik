// expected C094: pattern enum must match the switch enum
package reference_invalid

enum Result<T, E> {
    Ok(T)
    Error(E)
}

enum Other {
    A
    B
}

func f(x: int) -> int {
    switch x {
        case Result.Ok(v) {
            return 1
        }
        default {
            return 2
        }
    }
}
