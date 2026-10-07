// expected: SOLV-SEM-052
enum Result<T, E> {
    Ok(T)
    Err(E)
}
func compute(): Result<Integer, Integer> {
    return Result.Ok(1)
}
func run() {
    compute()
}
