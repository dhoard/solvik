// expected E066: payload enum values have no integer conversion
package runtime_errors

enum Result<T, E> {
    Ok(T)
    Error(E)
}

func main() -> int {
    r: Result<int, string> = Result.Ok(5)
    return int(r)
}
