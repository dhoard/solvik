// Solvik TCK SOL-TCK-0317
// The success type `String` is not assignable to the boundary success type `Integer`; only that clause differs from an accepted program, and the specification names `SOLV-SEM-051`.
//
// Oracle quotations verified verbatim against docs/LANGUAGE_SPEC.md:
//   - Propagation is type-checked against the enclosing function's declared `Result` boundary: the unwrapped success type `T` must be assignable to `T2`, and the propagated error type `E` must be assignable to `E2`. The operand type never widens the function's declared result types; only assignability is required.
//   - | `SEM_RESULT_PROPAGATION_TYPE_MISMATCH` | `SOLV-SEM-051` | `T`/`E` is not assignable to the boundary `T2`/`E2` |
//
enum Result<T, E> {
    Ok(T)
    Err(E)
}
func get(): Result<String, String> {
    return Result.Ok("s")
}
func use(): Result<Integer, String> {
    val v = get()?
    return Result.Ok(v)
}

print("EXECUTED-INVALID")
