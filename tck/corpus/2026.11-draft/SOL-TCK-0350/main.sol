// Solvik TCK SOL-TCK-0350
// An unknown member on a Result receiver pins the specification-named SOLV-RESOL-004.
//
// Oracle quotations verified verbatim against docs/LANGUAGE_SPEC.md:
//   - `Result` operations reuse existing diagnostic codes; no new codes are introduced.
//   - | `RESOL_UNKNOWN_MEMBER` | `SOLV-RESOL-004` | a member of a `Result` receiver that is not a `Result` operation |
//
enum Result<T, E> {
    Ok(T)
    Err(E)
}
func get(): Result<Integer, String> {
    return Result.Ok(1)
}
func use(): Result<Integer, String> {
    var r: Result<Integer, String> = get()
    var b: Any = r.nope()
    return Result.Ok(1)
}
print("EXECUTED-INVALID")
