// expected C101: construction payload must match the case type
package reference_invalid

enum Result<T, E> {
    Ok(T)
    Error(E)
}

func main() -> int {
    r: Result<int, string> = Result.Ok("str")
    return 0
}
