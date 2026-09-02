// expected C105: switches over closed enums must be exhaustive or have default
package reference_invalid

enum Result<T, E> {
    Ok(T)
    Error(E)
}

func f(r: Result<int, string>) -> int {
    switch r {
        case Result.Ok(v) {
            return 1
        }
    }
    return 0
}
