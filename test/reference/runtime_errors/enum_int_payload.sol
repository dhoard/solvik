// expected E066: payload enum values have no integer conversion
package runtime_errors

enum Result<T, E> {
    Ok(T)
    Error(E)
}

func main() -> Int {
    r: Result<Int, String> = Result.Ok(5)
    return int(r)
}
