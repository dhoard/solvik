// Solvik TCK SOL-TCK-0316
// The operand is a genuine `Result`, so only the missing boundary is wrong; the implicit top-level `main` does not return a `Result`, and the specification names `SOLV-SEM-050`.
//
// Oracle quotations verified verbatim against docs/LANGUAGE_SPEC.md:
//   - the operation is permitted only inside a function declared to return a `Result<T2, E2>`.
//   - | `SEM_RESULT_PROPAGATION_NO_BOUNDARY` | `SOLV-SEM-050` | no enclosing function returns a `Result` |
//
enum Result<T, E> {
    Ok(T)
    Err(E)
}
func get(): Result<Integer, String> {
    return Result.Ok(1)
}
var v: Integer = get()?

print("EXECUTED-INVALID")
