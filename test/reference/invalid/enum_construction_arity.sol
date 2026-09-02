// expected C101: construction payload count must match the case
package reference_invalid

enum Result<T, E> {
    Ok(T)
    Error(E)
}

func main() -> int {
    r: Result<int, string> = Result.Ok(1, 2)
    return 0
}
