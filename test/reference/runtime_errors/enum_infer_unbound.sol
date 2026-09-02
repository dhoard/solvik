// expected E067: unbound enum type parameter without annotation or explicit args
package runtime_errors

enum Result<T, E> {
    Ok(T)
    Error(E)
}

func main() -> int {
    x: any = Result.Ok(5)
    return 0
}
