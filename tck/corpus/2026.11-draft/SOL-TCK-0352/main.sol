// Solvik TCK SOL-TCK-0352
// A bare member read of a Result operation pins the specification-named SOLV-TYPE-014.
//
// Oracle quotations verified verbatim against docs/LANGUAGE_SPEC.md:
//   - `Result` operations reuse existing diagnostic codes; no new codes are introduced.
//   - | `TYPE_FUNCTION_AS_VALUE` | `SOLV-TYPE-014` | a bare member read of a `Result` operation (no call) |
//
enum Result<T, E> {
    Ok(T)
    Err(E)
}
func get(): Result<Integer, String> {
    return Result.Ok(1)
}
func use(): Result<Integer, String> {
    val r = get()
    val f = r.isOk
    return Result.Ok(1)
}
print("EXECUTED-INVALID")
