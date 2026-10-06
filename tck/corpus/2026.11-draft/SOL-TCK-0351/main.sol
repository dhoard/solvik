// Solvik TCK SOL-TCK-0351
// isOk takes no arguments, so the extra argument is the wrong argument count and pins SOLV-TYPE-003.
//
// Oracle quotations verified verbatim against docs/LANGUAGE_SPEC.md:
//   - `Result` operations reuse existing diagnostic codes; no new codes are introduced.
//   - | `TYPE_ARITY_MISMATCH` | `SOLV-TYPE-003` | a `Result` operation call with the wrong argument count |
//
enum Result<T, E> {
    Ok(T)
    Err(E)
}
func get(): Result<Integer, String> {
    return Result.Ok(1)
}
func use(): Result<Integer, String> {
    var r = get()
    var b = r.isOk(1)
    return Result.Ok(1)
}
print("EXECUTED-INVALID")
