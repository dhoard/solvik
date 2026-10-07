// expected: SOLV-SEM-050
enum Result<T, E> {
    Ok(T)
    Err(E)
}
func run() {
    var result: Result<Integer, Integer> = Result.Ok(1)
    result?
}
