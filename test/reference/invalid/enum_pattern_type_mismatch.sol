// expected C108: literal pattern must match the payload type
package reference_invalid

enum Result<T, E> {
    Ok(T)
    Error(E)
}

func f(r: Result<int, string>) -> int {
    switch r {
        case Result.Ok("x") {
            return 1
        }
        case Result.Error(e) {
            return 2
        }
    }
    return 0
}
