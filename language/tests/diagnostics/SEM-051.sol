// expected: SOLV-SEM-051
enum Result<T, E> {
    Ok(T)
    Err(E)
}
func fail(): Result<Integer, String> {
    return Result.Err("no value")
}
func retry(): Result<Integer, Integer> {
    var value: Integer = fail()?
    return Result.Ok(value)
}
