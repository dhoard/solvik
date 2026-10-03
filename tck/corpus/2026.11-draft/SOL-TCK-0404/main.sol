// Solvik TCK SOL-TCK-0404
// unwrapErr on an Err returns the carried error payload.
//
// Oracle quotations verified verbatim against docs/LANGUAGE_SPEC.md:
//   - `unwrapErr` returns the error payload of an `Err`. On an `Ok` it raises a runtime fault (section 23.1).
//
enum Result<T, E> {
    Ok(T)
    Err(E)
}
func get(): Result<Integer, String> {
    return Result.Err("e")
}
print(get().unwrapErr())
